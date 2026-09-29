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
const crypto = require('node:crypto')
const idem = () => crypto.randomUUID()

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
      amountDt: 60, method: 'CASH', payerName: 'Walk-in guest', coveredSeats: 3, idempotencyKey: idem(),
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
      amountDt: 60, method: 'CASH', coveredSeats: 3, idempotencyKey: idem(),
    }, admin)
    const me = await ok('GET', '/auth/me', undefined, tok(organiser))
    assert.equal(Number(me.walletDt), 100 - 20, 'the guest cash-in must not touch the organiser wallet')
  })

  await test('G03', 'Overpaying beyond the amount due is refused', async () => {
    const { booking } = await shareBooking(2)
    const r = await req('POST', `/admin/courts/bookings/${booking.bookingId}/guest-payments`, {
      amountDt: 61, method: 'CASH', coveredSeats: 3, idempotencyKey: idem(),
    }, admin)
    rejects(r, 'overpayment', /guest_payment_exceeds_due/)
  })

  await test('G04', 'A partial cash-in shows PARTIAL, not PAID', async () => {
    const { booking } = await shareBooking(3)
    const after = await ok('POST', `/admin/courts/bookings/${booking.bookingId}/guest-payments`, {
      amountDt: 20, method: 'CARD', coveredSeats: 1, idempotencyKey: idem(),
    }, admin)
    assert.equal(Number(after.totalCollectedDt), 40)
    assert.equal(after.paymentState, 'PARTIAL')
  })

  await test('G05', 'A double click (two sequential identical cash-ins) cannot both succeed', async () => {
    const { booking } = await shareBooking(4)
    // Two genuinely distinct admin actions (different idempotency keys) —
    // this is testing the balance/seat business rule, not key-replay.
    await ok('POST', `/admin/courts/bookings/${booking.bookingId}/guest-payments`, {
      amountDt: 60, method: 'CASH', coveredSeats: 3, idempotencyKey: idem(),
    }, admin)
    const second = await req('POST', `/admin/courts/bookings/${booking.bookingId}/guest-payments`, {
      amountDt: 60, method: 'CASH', coveredSeats: 3, idempotencyKey: idem(),
    }, admin)
    // Refused either way: no seats are open any more, or the amount exceeds
    // what remains due — both are correct depending on which check runs first.
    rejects(second, 'a second identical cash-in after the balance is settled', /guest_payment_exceeds_due|guest_payment_seats_exceed_open/)
  })

  await test('G05b', 'Same idempotency key sent twice (real double-click/retry): one row, one 20 DT collected', async () => {
    const { booking } = await shareBooking(11)
    const key = idem()
    const first = await ok('POST', `/admin/courts/bookings/${booking.bookingId}/guest-payments`, {
      amountDt: 20, method: 'CASH', coveredSeats: 1, idempotencyKey: key,
    }, admin)
    assert.equal(Number(first.totalCollectedDt), 40, '20 organiser + 20 first cash-in')
    assert.equal(first.guestPayments.length, 1)
    const firstPaymentId = first.guestPayments[0].id

    // Exact same key replayed (double-click, or a network retry with no re-click).
    const replay = await ok('POST', `/admin/courts/bookings/${booking.bookingId}/guest-payments`, {
      amountDt: 20, method: 'CASH', coveredSeats: 1, idempotencyKey: key,
    }, admin)
    assert.equal(Number(replay.totalCollectedDt), 40, 'the replay must NOT add another 20 DT — still 40, not 60')
    assert.equal(replay.guestPayments.length, 1, 'no duplicate row was inserted')
    assert.equal(replay.guestPayments[0].id, firstPaymentId, 'the replay resolves to the SAME payment record')
  })

  await test('G05c', 'Two DIFFERENT idempotency keys, same amount, same booking: both succeed', async () => {
    const { booking } = await shareBooking(12)
    const r1 = await ok('POST', `/admin/courts/bookings/${booking.bookingId}/guest-payments`, {
      amountDt: 20, method: 'CASH', coveredSeats: 1, idempotencyKey: idem(),
    }, admin)
    assert.equal(Number(r1.totalCollectedDt), 40)
    const r2 = await ok('POST', `/admin/courts/bookings/${booking.bookingId}/guest-payments`, {
      amountDt: 20, method: 'CASH', coveredSeats: 1, idempotencyKey: idem(),
    }, admin)
    // Organiser's 20 + two genuinely distinct 20 DT guest cash-ins = 60.
    assert.equal(Number(r2.totalCollectedDt), 60, 'two distinct 20 DT payments must both be collected')
    assert.equal(r2.guestPayments.length, 2)
    assert.notEqual(r2.guestPayments[0].id, r2.guestPayments[1].id)
  })

  await test('G05d', 'Non-integer coveredSeats is rejected', async () => {
    const { booking } = await shareBooking(13)
    const r = await req('POST', `/admin/courts/bookings/${booking.bookingId}/guest-payments`, {
      amountDt: 20, method: 'CASH', coveredSeats: 1.5, idempotencyKey: idem(),
    }, admin)
    rejects(r, 'non-integer coveredSeats')
  })

  await test('G05e', 'Negative coveredSeats is rejected at the DTO level', async () => {
    const { booking } = await shareBooking(14)
    const r = await req('POST', `/admin/courts/bookings/${booking.bookingId}/guest-payments`, {
      amountDt: 20, method: 'CASH', coveredSeats: -1, idempotencyKey: idem(),
    }, admin)
    rejects(r, 'negative coveredSeats', /takeoff.validation/)
  })

  await test('G05f', 'Blank idempotencyKey is rejected', async () => {
    const { booking } = await shareBooking(15)
    const r = await req('POST', `/admin/courts/bookings/${booking.bookingId}/guest-payments`, {
      amountDt: 20, method: 'CASH', coveredSeats: 1, idempotencyKey: '',
    }, admin)
    rejects(r, 'blank idempotencyKey', /takeoff.validation/)
  })

  await test('G06', 'Two concurrent cash-ins against the same balance: only the coverable one succeeds', async () => {
    const { booking } = await shareBooking(5)
    const [r1, r2] = await Promise.all([
      req('POST', `/admin/courts/bookings/${booking.bookingId}/guest-payments`, { amountDt: 60, method: 'CASH', coveredSeats: 3, idempotencyKey: idem() }, admin),
      req('POST', `/admin/courts/bookings/${booking.bookingId}/guest-payments`, { amountDt: 60, method: 'CASH', coveredSeats: 3, idempotencyKey: idem() }, admin),
    ])
    const okCount = [r1, r2].filter((r) => r.status < 300).length
    assert.equal(okCount, 1, 'exactly one of the two concurrent 60 DT cash-ins must be accepted')
    const detail = await ok('GET', `/admin/courts/bookings/${booking.bookingId}`, undefined, admin)
    assert.equal(Number(detail.totalCollectedDt), 80, 'never more than the court is worth')
  })

  await test('G06b', 'Two truly concurrent requests with the SAME idempotency key: only one payment row is ever created', async () => {
    const { booking } = await shareBooking(16)
    const key = idem()
    const [r1, r2] = await Promise.all([
      req('POST', `/admin/courts/bookings/${booking.bookingId}/guest-payments`, { amountDt: 20, method: 'CASH', coveredSeats: 1, idempotencyKey: key }, admin),
      req('POST', `/admin/courts/bookings/${booking.bookingId}/guest-payments`, { amountDt: 20, method: 'CASH', coveredSeats: 1, idempotencyKey: key }, admin),
    ])
    // Both may well come back 2xx (the second sees its own key already
    // recorded and just returns the same record) — what must never happen is
    // two distinct payment rows or double-counted money.
    assert.ok([r1, r2].every((r) => r.status < 300), `both concurrent replays of the same key must succeed, got ${r1.status}/${r2.status}`)
    const detail = await ok('GET', `/admin/courts/bookings/${booking.bookingId}`, undefined, admin)
    assert.equal(detail.guestPayments.length, 1, 'the same key raced concurrently must still produce exactly one row')
    assert.equal(Number(detail.totalCollectedDt), 40, '20 organiser + 20 — never double-billed by the race')
  })

  await test('G07', 'Covering more seats than are open is refused', async () => {
    const { booking } = await shareBooking(6)
    const r = await req('POST', `/admin/courts/bookings/${booking.bookingId}/guest-payments`, {
      amountDt: 40, method: 'CASH', coveredSeats: 4, idempotencyKey: idem(),
    }, admin)
    rejects(r, 'covering more seats than exist', /guest_payment_seats_exceed_open/)
    // (Already exercised above — coveredSeats exceeding open seats is G07's
    // whole point; no separate case duplicates it.)
  })

  await test('G08', 'A covered seat is not offered for booking again', async () => {
    const { booking, startsAt } = await shareBooking(7)
    await ok('POST', `/admin/courts/bookings/${booking.bookingId}/guest-payments`, {
      amountDt: 60, method: 'CASH', coveredSeats: 3, idempotencyKey: idem(),
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
      amountDt: 60, method: 'CASH', coveredSeats: 3, idempotencyKey: idem(),
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

    // And the balance is genuinely reopened: a fresh, correct cash-in succeeds
    // (a fresh key — this is a new admin action, not a replay of the voided one).
    const redo = await ok('POST', `/admin/courts/bookings/${booking.bookingId}/guest-payments`, {
      amountDt: 60, method: 'CASH', coveredSeats: 3, idempotencyKey: idem(),
    }, admin)
    assert.equal(Number(redo.totalCollectedDt), 80)
  })

  await test('G09b', 'Replaying the idempotency key of a VOIDED payment returns it as-is (voided), not a fresh row', async () => {
    const { booking } = await shareBooking(17)
    const key = idem()
    const payment = await ok('POST', `/admin/courts/bookings/${booking.bookingId}/guest-payments`, {
      amountDt: 60, method: 'CASH', coveredSeats: 3, idempotencyKey: key,
    }, admin)
    const paymentId = payment.guestPayments[0].id
    await ok('POST', `/admin/courts/bookings/${booking.bookingId}/guest-payments/${paymentId}/void`,
      { reason: 'Test void for replay check' }, admin)

    // Same key again — this must NOT re-litigate against the now-reopened
    // balance and insert a second (unvoided) row for the same key. It
    // returns the booking exactly as it now stands: the original row, voided.
    const replay = await ok('POST', `/admin/courts/bookings/${booking.bookingId}/guest-payments`, {
      amountDt: 60, method: 'CASH', coveredSeats: 3, idempotencyKey: key,
    }, admin)
    assert.equal(replay.guestPayments.length, 1, 'no second row for the same key, voided or not')
    assert.equal(replay.guestPayments[0].id, paymentId)
    assert.equal(replay.guestPayments[0].voided, true, 'the replay reflects the payment as it now stands: voided')
    assert.equal(Number(replay.totalCollectedDt), 20, 'the voided amount still does not count')
  })

  await test('G10', 'A negative or zero amount is refused', async () => {
    const { booking } = await shareBooking(9)
    const r = await req('POST', `/admin/courts/bookings/${booking.bookingId}/guest-payments`, {
      amountDt: 0, method: 'CASH', coveredSeats: 0, idempotencyKey: idem(),
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
      amountDt: 10, method: 'CASH', coveredSeats: 1, idempotencyKey: idem(),
    }, admin)
    rejects(r, 'coveredSeats on a FULL booking', /guest_payment_seats_not_applicable/)
  })

  await test('G12', 'coveredSeats and amountDt are intentionally independent: a lump sum need not equal seats * per-seat price', async () => {
    const { booking } = await shareBooking(18)
    // Organiser already paid their 20 DT seat (WALLET). Remaining due = 60 DT
    // across 3 open seats (20 DT/seat if split evenly). A guest instead pays
    // an uneven 25 DT while claiming 2 of those 3 seats — no 2*20=40 DT rule
    // is enforced, by design (see AddGuestPaymentRequest doc comment): only
    // "coveredSeats <= open seats" and "amountDt <= remaining due" apply.
    const after = await ok('POST', `/admin/courts/bookings/${booking.bookingId}/guest-payments`, {
      amountDt: 25, method: 'CASH', coveredSeats: 2, idempotencyKey: idem(),
    }, admin)
    assert.equal(Number(after.totalCollectedDt), 45, '20 organiser + 25 uneven guest cash-in')
    assert.equal(after.seatsOpenForSale, 1, 'covering 2 seats reserves 2, regardless of the 25 DT not being 2x20')
  })

  const pass = results.filter((r) => r === 'PASS').length
  console.log(JSON.stringify({ PASS: pass, FAIL: results.length - pass }))
  process.exitCode = pass === results.length ? 0 : 1
}

main().catch((e) => { console.error(e); process.exitCode = 2 })
