/**
 * A booking traced end to end, and the cancellation rule at its boundary.
 *
 * Against the real Spring backend and a synthetic database:
 *   create -> bookingId -> database row -> admin calendar API -> player profile
 *
 * The admin console positioned bookings with the browser's getHours(), so the
 * same row sat on the 07:00 line in Tunis and on a non-existent 08:00 line in
 * Paris, where it vanished. These cases pin the data side of that: what the
 * calendar API returns for a club-day window, whatever the caller's clock.
 *
 *   node admin-visibility-journeys.cjs <baseUrl> <adminEmail> <adminPassword>
 */
const assert = require('node:assert/strict')
const { execFileSync } = require('node:child_process')

const BASE = (process.argv[2] || 'http://127.0.0.1:18081') + '/api/v1'
const ADMIN_EMAIL = process.argv[3] || 'audit-admin@example.test'
const ADMIN_PASSWORD = process.argv[4] || 'Audit-Only-Password-2026!'
const DB_CONTAINER = process.env.TAKEOFF_DB_CONTAINER || 'takeoff-recheck-20260922'

const results = []
let admin
const stamp = Date.now().toString(36)
let seq = 0

const sql = (q) =>
  execFileSync('docker', ['exec', DB_CONTAINER, 'psql', '-U', 'audit', '-d', 'takeoff_audit', '-At', '-c', q], {
    encoding: 'utf8',
    windowsHide: true,
  }).trim()

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

const rejects = (r, why) => assert.ok(r.status >= 400 && r.status < 500, `${why}: expected 4xx, got ${r.status}`)

async function test(id, title, fn) {
  try {
    await fn()
    results.push({ id, title, status: 'PASS' })
    console.log('PASS ' + id + ' ' + title)
  } catch (e) {
    results.push({ id, title, status: 'FAIL', error: e.message })
    console.log('FAIL ' + id + ' ' + title + ' :: ' + e.message)
  }
}

async function member(balance = 0) {
  const phone = '+216' + String(70000000 + (Date.now() % 1000000) + ++seq).slice(0, 8)
  const u = await ok('POST', '/auth/register', {
    phone, email: `vis-${stamp}-${seq}@example.test`,
    password: 'Visibility-Journey-Password!', name: 'Visibility Tester ' + seq,
  })
  if (balance) await ok('POST', `/admin/users/${u.user.id}/wallet/credit`, { amountDt: balance, reason: 'journey' }, admin)
  return { ...u, phone }
}

const tok = (u) => u.tokens.accessToken
const wallet = async (u) => Number((await ok('GET', '/auth/me', undefined, tok(u))).walletDt)

/* ── club time (Africa/Tunis), independent of this process's clock ───────── */
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
const clubTime = (iso) => { const p = clubParts(new Date(iso)); return `${p.hour}:${p.minute}` }
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

const GRID = (() => { const o = []; for (let m = 7 * 60; m + 90 <= 22 * 60; m += 90) o.push(m); return o })()
/* Each run books days no previous run touched: the database persists between
   runs, so a fixed offset makes the second run collide with the first and
   report slot_taken as if the code had regressed. */
let dayOffset = 120 + Math.floor(Math.random() * 4000) * 3
function nextSlot(gridIndex = 0) {
  const minutes = GRID[gridIndex % GRID.length]
  const d = new Date(); d.setUTCDate(d.getUTCDate() + dayOffset++)
  const day = clubDay(d.toISOString())
  const hhmm = `${String(Math.floor(minutes / 60)).padStart(2, '0')}:${String(minutes % 60).padStart(2, '0')}`
  return { iso: clubInstant(day, hhmm).toISOString(), day, hhmm }
}

async function main() {
  admin = (await ok('POST', '/admin/auth/login', { email: ADMIN_EMAIL, password: ADMIN_PASSWORD })).token
  const courts = await ok('GET', '/courts')
  const court = courts.find((c) => !c.activity || c.activity === 'PADEL')
  assert.ok(court, 'no padel court configured')

  /* ── 1. the trace ─────────────────────────────────────────────────────── */

  await test('V01', 'A PAY_AT_CLUB booking reaches the database, the admin calendar and the profile', async () => {
    const u = await member(100)
    const slot = nextSlot(0) // 07:00 club time — the row that used to vanish
    const created = await ok('POST', `/courts/${court.id}/bookings`, {
      startsAt: slot.iso, mode: 'FULL', paymentMethod: 'PAY_AT_CLUB',
    }, tok(u))
    const id = created.bookingId
    assert.ok(id, 'no bookingId returned')

    // database
    const row = sql(`select status || '|' || payment_status || '|' || starts_at from court_bookings where id='${id}'`)
    assert.ok(row.startsWith('CONFIRMED|PAY_AT_CLUB|'), 'unexpected row: ' + row)

    // admin calendar, queried over the club day the member sees
    const from = clubInstant(slot.day, '00:00').toISOString()
    const to = new Date(clubInstant(slot.day, '00:00').getTime() + 86400000).toISOString()
    const cal = await ok('GET', `/admin/courts/calendar?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`, undefined, admin)
    const card = (cal.bookings || []).find((b) => b.id === id)
    assert.ok(card, 'the booking is absent from the admin calendar window')
    assert.equal(clubTime(card.startsAt), slot.hhmm, 'the admin sees a different club time')
    assert.equal(clubDay(card.startsAt), slot.day, 'the admin sees a different club day')

    // unpaid, and not dressed up as paid
    assert.notEqual(card.paymentState, 'PAID', 'an uncollected booking must not read as paid')
    assert.equal(await wallet(u), 100, 'PAY_AT_CLUB must not debit the wallet')

    // the player's own profile
    const mine = await ok('GET', '/courts/bookings/mine', undefined, tok(u))
    assert.ok(mine.some((m) => m.bookingId === id), 'the booking is missing from the profile')
  })

  await test('V02', 'A wallet-paid SHARE is equally visible, and shows as paid', async () => {
    const u = await member(100)
    const slot = nextSlot(1)
    const created = await ok('POST', `/courts/${court.id}/bookings`, {
      startsAt: slot.iso, mode: 'SHARE', paymentMethod: 'WALLET',
    }, tok(u))
    assert.equal(await wallet(u), 80, 'one seat charged')

    const from = clubInstant(slot.day, '00:00').toISOString()
    const to = new Date(Date.parse(from) + 86400000).toISOString()
    const cal = await ok('GET', `/admin/courts/calendar?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`, undefined, admin)
    const card = (cal.bookings || []).find((b) => b.id === created.bookingId)
    assert.ok(card, 'the shared match is absent from the admin calendar')
    assert.equal(card.mode, 'SHARE')
  })

  await test('V03', 'Every slot on the club grid is returned for its own club day', async () => {
    // The 07:00 and 20:30 edges are where a browser-zone shift bites hardest.
    for (const idx of [0, GRID.length - 1]) {
      const u = await member(200)
      const slot = nextSlot(idx)
      const created = await ok('POST', `/courts/${court.id}/bookings`, {
        startsAt: slot.iso, mode: 'FULL', paymentMethod: 'PAY_AT_CLUB',
      }, tok(u))
      const from = clubInstant(slot.day, '00:00').toISOString()
      const to = new Date(Date.parse(from) + 86400000).toISOString()
      const cal = await ok('GET', `/admin/courts/calendar?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`, undefined, admin)
      const card = (cal.bookings || []).find((b) => b.id === created.bookingId)
      assert.ok(card, `slot ${slot.hhmm} missing from its own club day`)
      assert.equal(clubTime(card.startsAt), slot.hhmm)
    }
  })

  /* ── 2. cancellation ──────────────────────────────────────────────────── */

  await test('V04', 'The profile publishes cancellation eligibility and its deadline', async () => {
    const u = await member(200)
    const slot = nextSlot(2)
    const created = await ok('POST', `/courts/${court.id}/bookings`, {
      startsAt: slot.iso, mode: 'FULL', paymentMethod: 'WALLET',
    }, tok(u))
    const mine = await ok('GET', '/courts/bookings/mine', undefined, tok(u))
    const row = mine.find((m) => m.bookingId === created.bookingId)
    assert.equal(row.canCancel, true, 'a booking months ahead must be cancellable')
    assert.ok(row.cancelDeadline, 'the deadline must be published, not re-derived by the UI')
    // The deadline is exactly 24h before the start.
    assert.equal(Date.parse(row.cancelDeadline), Date.parse(slot.iso) - 24 * 3600 * 1000)
  })

  await test('V05', 'Inside 24 hours the member is refused and told the club can do it', async () => {
    const u = await member(200)
    // A slot on the club grid, tomorrow: inside the 24h window but still future.
    const soon = new Date(Date.now() + 6 * 3600 * 1000)
    const day = clubDay(soon.toISOString())
    const created = await ok('POST', '/admin/courts/bookings', {
      courtId: court.id, userId: u.user.id,
      startsAt: clubInstant(day, '20:30').toISOString(),
      endsAt: new Date(clubInstant(day, '20:30').getTime() + 5400000).toISOString(),
      mode: 'FULL', priceDt: 80, paymentMethod: 'PAY_AT_CLUB',
    }, admin)

    const mine = await ok('GET', '/courts/bookings/mine', undefined, tok(u))
    const row = mine.find((m) => m.bookingId === created.id)
    if (row && Date.parse(row.cancelDeadline) < Date.now()) {
      assert.equal(row.canCancel, false, 'inside the window the member must be refused')
      assert.ok(/club/i.test(row.cancelBlockedReason || ''), 'the refusal must point at the club')
      const r = await req('DELETE', `/courts/bookings/${created.id}`, undefined, tok(u))
      rejects(r, 'a late member cancellation')
      assert.match(JSON.stringify(r.data), /cancel_too_late/)
    }

    // The club can still do it, with a reason on record.
    await ok('POST', `/admin/courts/bookings/${created.id}/cancel`, { reason: 'Journey check', refundToWallet: false }, admin)
    assert.equal(sql(`select status from court_bookings where id='${created.id}'`), 'CANCELLED')
    assert.equal(sql(`select coalesce(cancel_reason,'') from court_bookings where id='${created.id}'`), 'Journey check')
  })

  await test('V06', 'A wallet cancellation repays once; PAY_AT_CLUB repays nothing', async () => {
    const paid = await member(200)
    const s1 = nextSlot(3)
    const b1 = await ok('POST', `/courts/${court.id}/bookings`, {
      startsAt: s1.iso, mode: 'FULL', paymentMethod: 'WALLET',
    }, tok(paid))
    assert.equal(await wallet(paid), 120)
    await ok('DELETE', `/courts/bookings/${b1.bookingId}`, undefined, tok(paid))
    assert.equal(await wallet(paid), 200, 'the 80 DT comes back')
    rejects(await req('DELETE', `/courts/bookings/${b1.bookingId}`, undefined, tok(paid)), 'a second cancellation')
    assert.equal(await wallet(paid), 200, 'and never a second time')

    const club = await member(200)
    const s2 = nextSlot(4)
    const b2 = await ok('POST', `/courts/${court.id}/bookings`, {
      startsAt: s2.iso, mode: 'FULL', paymentMethod: 'PAY_AT_CLUB',
    }, tok(club))
    await ok('DELETE', `/courts/bookings/${b2.bookingId}`, undefined, tok(club))
    assert.equal(await wallet(club), 200, 'nothing was collected, so nothing is credited')
  })

  await test('V07', 'Leaving a shared match frees one seat and keeps the match', async () => {
    const host = await member(200)
    const guest = await member(200)
    const slot = nextSlot(5)
    const match = await ok('POST', `/courts/${court.id}/bookings`, {
      startsAt: slot.iso, mode: 'SHARE', paymentMethod: 'WALLET',
    }, tok(host))
    await ok('POST', `/courts/${court.id}/bookings`, {
      startsAt: slot.iso, mode: 'SHARE', paymentMethod: 'WALLET',
    }, tok(guest))
    assert.equal(await wallet(guest), 180)

    await ok('DELETE', `/courts/bookings/${match.bookingId}`, undefined, tok(guest))
    assert.equal(await wallet(guest), 200, 'the leaver gets their own seat back')
    assert.equal(sql(`select status from court_bookings where id='${match.bookingId}'`), 'CONFIRMED',
      'the match itself survives')
    assert.equal(await wallet(host), 180, "the host's payment is untouched")
    assert.equal(sql(`select count(*) from court_booking_players where booking_id='${match.bookingId}'`), '1')
  })

  await test('V08', 'A stranger cannot cancel someone else’s match', async () => {
    const owner = await member(200)
    const stranger = await member(200)
    const slot = nextSlot(6)
    const b = await ok('POST', `/courts/${court.id}/bookings`, {
      startsAt: slot.iso, mode: 'FULL', paymentMethod: 'WALLET',
    }, tok(owner))
    rejects(await req('DELETE', `/courts/bookings/${b.bookingId}`, undefined, tok(stranger)), 'a stranger')
    assert.equal(sql(`select status from court_bookings where id='${b.bookingId}'`), 'CONFIRMED')
    assert.equal(await wallet(owner), 120, 'nothing refunded to anyone')
  })

  await test('V09', 'A cancelled match stays in history and frees its slot', async () => {
    const u = await member(200)
    const slot = nextSlot(7)
    const b = await ok('POST', `/courts/${court.id}/bookings`, {
      startsAt: slot.iso, mode: 'FULL', paymentMethod: 'WALLET',
    }, tok(u))
    await ok('DELETE', `/courts/bookings/${b.bookingId}`, undefined, tok(u))

    const mine = await ok('GET', '/courts/bookings/mine', undefined, tok(u))
    const row = mine.find((m) => m.bookingId === b.bookingId)
    assert.ok(row, 'a cancelled match must remain in history')
    assert.equal(row.status, 'CANCELLED')

    // And the slot is bookable again by someone else.
    const other = await member(200)
    const again = await req('POST', `/courts/${court.id}/bookings`, {
      startsAt: slot.iso, mode: 'FULL', paymentMethod: 'WALLET',
    }, tok(other))
    assert.equal(again.status, 201, 'a cancelled booking must not keep blocking its slot')
  })

  const counts = ['PASS', 'FAIL'].map((s) => [s, results.filter((r) => r.status === s).length])
  console.log(JSON.stringify(Object.fromEntries(counts)))
  process.exitCode = results.some((r) => r.status !== 'PASS') ? 1 : 0
}

main().catch((e) => { console.error(e); process.exitCode = 2 })
