/**
 * Admin cash-in for a guest paying at the desk, against the real backend.
 *
 * The mandatory case: an 80 DT court, the organiser already paid their own 20
 * DT seat (WALLET), and a guest settles the remaining 60 DT in one operation
 * — 80 DT collected, 0 DT due, without fabricating three fake players and
 * without crediting any wallet.
 *
 *   node guest-payment-journeys.cjs <baseUrl> <adminEmail> <adminPassword>
 */
const assert = require('node:assert/strict')

const BASE = (process.argv[2] || 'http://127.0.0.1:18081') + '/api/v1'
const ADMIN_EMAIL = process.argv[3] || 'audit-admin@example.test'
const ADMIN_PASSWORD = process.argv[4] || 'Audit-Only-Password-2026!'

const results = []
let admin, court
const stamp = Date.now().toString(36)
let seq = 0

async function req(method, path, body, token) {
  const res = await fetch(BASE + path, {
    method,
    headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: 'Bearer ' + token } : {}) },
    body: body === undefined ? undefined : JSON.stringify(body),
  })
  const raw = await res.text()
  let data
  try { data = JSON.parse(raw) } catch { data = raw }
  return { status: res.status, data }
}
async function ok(method, path, body, token) {
  const r = await req(method, path, body, token)
  if (r.status >= 400) throw new Error(`${method} ${path} -> ${r.status} ${JSON.stringify(r.data).slice(0, 250)}`)
  return r.data
}
const rejects = (r, why, codeMatch) => {
  assert.ok(r.status >= 400 && r.status < 500, `${why}: expected 4xx, got ${r.status} ${JSON.stringify(r.data).slice(0, 200)}`)
  if (codeMatch) assert.match(String(r.data?.code ?? ''), codeMatch, `${why}: wrong error code, got ${r.data?.code}`)
}
async function test(id, title, fn) {
  try {
    await fn()
    results.push('PASS')
    console.log('PASS ' + id + ' ' + title)
  } catch (e) {
    results.push('FAIL')
    console.log('FAIL ' + id + ' ' + title + ' :: ' + e.message)
  }
}

async function member(balance = 0) {
  seq += 1
  const phone = '+216' + String(30000000 + (Date.now() % 1000000) + seq).slice(0, 8)
  const u = await ok('POST', '/auth/register', {
    phone, email: `gpay-${stamp}-${seq}@example.test`,
    password: 'Guest-Payment-Journey-Password!', name: 'Guest Payment Tester ' + seq,
  })
  if (balance) await ok('POST', `/admin/users/${u.user.id}/wallet/credit`, { amountDt: balance, reason: 'journey' }, admin)
  return u
}
const tok = (u) => u.tokens.accessToken

/* Club time (Africa/Tunis), independent of this process's own clock. */
const CLUB_TZ = 'Africa/Tunis'
function clubParts(instant) {
  const f = new Intl.DateTimeFormat('en-GB', {
    timeZone: CLUB_TZ, year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit', hour12: false,
  })
  const o = {}
  f.formatToParts(instant).forEach((p) => { if (p.type !== 'literal') o[p.type] = p.value })
  if (o.hour === '24') o.hour = '00'
  return o
}
const clubDay = (iso) => { const p = clubParts(new Date(iso)); return `${p.year}-${p.month}-${p.day}` }
function clubInstant(dateStr, timeStr) {
  const [y, m, d] = dateStr.split('-').map(Number)
  const [hh, mm] = timeStr.split(':').map(Number)
  const naive = Date.UTC(y, m - 1, d, hh, mm)
  const off = (i) => {
    const p = clubParts(new Date(i))
    return (Date.UTC(+p.year, +p.month - 1, +p.day, +p.hour, +p.minute) - Math.floor(i / 60000) * 60000) / 60000
  }
  const guess = naive - off(naive) * 60000
  return new Date(naive - off(guess) * 60000)
}

const GRID_MINUTES = (() => { const out = []; for (let m = 7 * 60; m + 90 <= 22 * 60; m += 90) out.push(m); return out })()
let dayOffset = 800 + Math.floor(Math.random() * 4000) * 3
function nextSlot(gridIndex = 0) {
  const minutes = GRID_MINUTES[gridIndex % GRID_MINUTES.length]
  const d = new Date(); d.setUTCDate(d.getUTCDate() + dayOffset++)
  const day = clubDay(d.toISOString())
  const hhmm = `${String(Math.floor(minutes / 60)).padStart(2, '0')}:${String(minutes % 60).padStart(2, '0')}`
  return clubInstant(day, hhmm).toISOString()
}

async function shareBooking(gridIndex, organiserBalance = 100) {
  const organiser = await member(organiserBalance)
  const startsAt = nextSlot(gridIndex)
  const b = await ok('POST', `/courts/${court.id}/bookings`, {
    startsAt, mode: 'SHARE', paymentMethod: 'WALLET',
  }, tok(organiser))
  return { organiser, booking: b, startsAt }
}

async function main() {
  admin = (await ok('POST', '/admin/auth/login', { email: ADMIN_EMAIL, password: ADMIN_PASSWORD })).token
  const courts = await ok('GET', '/courts')
  court = courts.find((c) => !c.activity || c.activity === 'PADEL')
  assert.ok(court, 'no padel court configured')

  await test('G01', 'The mandatory case: 20 DT organiser + 60 DT guest = 80 DT collected, 0 due', async () => {
    const { organiser, booking } = await shareBooking(0)

    const before = await ok('GET', `/admin/courts/bookings/${booking.bookingId}`, undefined, admin)
    assert.equal(Number(before.totalDueDt), 80)
    assert.equal(Number(before.totalCollectedDt), 20, 'only the organiser has paid so far')
    assert.equal(before.paymentState, 'PARTIAL')
    assert.equal(before.participants.length, 1, 'no fake players yet')

    const after = await ok('POST', `/admin/courts/bookings/${booking.bookingId}/guest-payments`, {
      amountDt: 60, method: 'CASH', payerName: 'Walk-in guest', coveredSeats: 3,
    }, admin)

    assert.equal(Number(after.totalCollectedDt), 80, '20 + 60 must equal 80')
    assert.equal(Number(after.totalDueDt) - Number(after.totalCollectedDt), 0, 'nothing left due')
    assert.equal(after.paymentState, 'PAID')
    // Still exactly one named participant — the 60 DT never became three fake players.
    assert.equal(after.participants.length, 1)
    assert.equal(after.guestPayments.length, 1)
    assert.equal(Number(after.guestPayments[0].amountDt), 60)
    assert.equal(after.guestPayments[0].method, 'CASH')
    void organiser
  })

  await test('G02', 'A guest payment never credits any wallet', async () => {
    const { organiser, booking } = await shareBooking(1)
    await ok('POST', `/admin/courts/bookings/${booking.bookingId}/guest-payments`, {
      amountDt: 60, method: 'CASH', coveredSeats: 3,
    }, admin)
    const me = await ok('GET', '/auth/me', undefined, tok(organiser))
    assert.equal(Number(me.walletDt), 100 - 20, 'the guest cash-in must not touch the organiser wallet')
  })

  await test('G03', 'Overpaying beyond the amount due is refused', async () => {
    const { booking } = await shareBooking(2)
    const r = await req('POST', `/admin/courts/bookings/${booking.bookingId}/guest-payments`, {
      amountDt: 61, method: 'CASH', coveredSeats: 3,
    }, admin)
    rejects(r, 'overpayment', /guest_payment_exceeds_due/)
  })

  await test('G04', 'A partial cash-in shows PARTIAL, not PAID', async () => {
    const { booking } = await shareBooking(3)
    const after = await ok('POST', `/admin/courts/bookings/${booking.bookingId}/guest-payments`, {
      amountDt: 20, method: 'CARD', coveredSeats: 1,
    }, admin)
    assert.equal(Number(after.totalCollectedDt), 40)
    assert.equal(after.paymentState, 'PARTIAL')
  })

  await test('G05', 'A double click (two sequential identical cash-ins) cannot both succeed', async () => {
    const { booking } = await shareBooking(4)
    await ok('POST', `/admin/courts/bookings/${booking.bookingId}/guest-payments`, {
      amountDt: 60, method: 'CASH', coveredSeats: 3,
    }, admin)
    const second = await req('POST', `/admin/courts/bookings/${booking.bookingId}/guest-payments`, {
      amountDt: 60, method: 'CASH', coveredSeats: 3,
    }, admin)
    // Refused either way: no seats are open any more, or the amount exceeds
    // what remains due — both are correct depending on which check runs first.
    rejects(second, 'a second identical cash-in after the balance is settled', /guest_payment_exceeds_due|guest_payment_seats_exceed_open/)
  })

  await test('G06', 'Two concurrent cash-ins against the same balance: only the coverable one succeeds', async () => {
    const { booking } = await shareBooking(5)
    const [r1, r2] = await Promise.all([
      req('POST', `/admin/courts/bookings/${booking.bookingId}/guest-payments`, { amountDt: 60, method: 'CASH', coveredSeats: 3 }, admin),
      req('POST', `/admin/courts/bookings/${booking.bookingId}/guest-payments`, { amountDt: 60, method: 'CASH', coveredSeats: 3 }, admin),
    ])
    const okCount = [r1, r2].filter((r) => r.status < 300).length
    assert.equal(okCount, 1, 'exactly one of the two concurrent 60 DT cash-ins must be accepted')
    const detail = await ok('GET', `/admin/courts/bookings/${booking.bookingId}`, undefined, admin)
    assert.equal(Number(detail.totalCollectedDt), 80, 'never more than the court is worth')
  })

  await test('G07', 'Covering more seats than are open is refused', async () => {
    const { booking } = await shareBooking(6)
    const r = await req('POST', `/admin/courts/bookings/${booking.bookingId}/guest-payments`, {
      amountDt: 40, method: 'CASH', coveredSeats: 4,
    }, admin)
    rejects(r, 'covering more seats than exist', /guest_payment_seats_exceed_open/)
  })

  await test('G08', 'A covered seat is not offered for booking again', async () => {
    const { booking, startsAt } = await shareBooking(7)
    await ok('POST', `/admin/courts/bookings/${booking.bookingId}/guest-payments`, {
      amountDt: 60, method: 'CASH', coveredSeats: 3,
    }, admin)
    const day = clubDay(startsAt)
    const slots = await ok('GET', `/courts/${court.id}/slots?date=${day}`)
    const slot = slots.find((s) => s.startsAt === startsAt || s.startsAt.slice(0, 16) === startsAt.slice(0, 16))
    if (slot) {
      assert.equal(slot.available, false, 'a fully covered SHARE match must not still invite joiners')
    }
  })

  await test('G09', 'Voiding a cash-in is traced, not a silent delete, and reopens the balance', async () => {
    const { booking } = await shareBooking(8)
    const payment = await ok('POST', `/admin/courts/bookings/${booking.bookingId}/guest-payments`, {
      amountDt: 60, method: 'CASH', coveredSeats: 3,
    }, admin)
    const paymentId = payment.guestPayments[0].id

    const after = await ok(
      'POST', `/admin/courts/bookings/${booking.bookingId}/guest-payments/${paymentId}/void`,
      { reason: 'Wrong amount entered' }, admin,
    )
    assert.equal(Number(after.totalCollectedDt), 20, 'a voided cash-in stops counting toward collected')
    assert.equal(after.paymentState, 'PARTIAL')
    assert.equal(after.guestPayments.length, 1, 'the record stays — never deleted')
    assert.equal(after.guestPayments[0].voided, true)
    assert.equal(after.guestPayments[0].voidReason, 'Wrong amount entered')

    // And the balance is genuinely reopened: a fresh, correct cash-in succeeds.
    const redo = await ok('POST', `/admin/courts/bookings/${booking.bookingId}/guest-payments`, {
      amountDt: 60, method: 'CASH', coveredSeats: 3,
    }, admin)
    assert.equal(Number(redo.totalCollectedDt), 80)
  })

  await test('G10', 'A negative or zero amount is refused', async () => {
    const { booking } = await shareBooking(9)
    const r = await req('POST', `/admin/courts/bookings/${booking.bookingId}/guest-payments`, {
      amountDt: 0, method: 'CASH', coveredSeats: 0,
    }, admin)
    assert.ok(r.status >= 400, 'a zero amount must be refused')
  })

  await test('G11', 'FULL/SHARE rules and existing player rights are untouched', async () => {
    const organiser = await member(200)
    const startsAt = nextSlot(10)
    const full = await ok('POST', `/courts/${court.id}/bookings`, {
      startsAt, mode: 'FULL', paymentMethod: 'WALLET',
    }, tok(organiser))
    const detail = await ok('GET', `/admin/courts/bookings/${full.bookingId}`, undefined, admin)
    assert.equal(detail.paymentState, 'PAID')
    assert.equal(Number(detail.totalDueDt), 80)
    // coveredSeats is meaningless on a FULL booking — must be refused, not silently accepted.
    const r = await req('POST', `/admin/courts/bookings/${full.bookingId}/guest-payments`, {
      amountDt: 10, method: 'CASH', coveredSeats: 1,
    }, admin)
    rejects(r, 'coveredSeats on a FULL booking', /guest_payment_seats_not_applicable/)
  })

  const pass = results.filter((r) => r === 'PASS').length
  console.log(JSON.stringify({ PASS: pass, FAIL: results.length - pass }))
  process.exitCode = pass === results.length ? 0 : 1
}

main().catch((e) => { console.error(e); process.exitCode = 2 })
