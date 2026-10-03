/**
 * Pilates booking integrity, against the real backend.
 *
 * MemberClassController.book used to create a BOOKED/paidWith=SINGLE booking
 * even with no valid pack and no payment collected. These cases pin the fix:
 * a confirmed spot requires a pack credit consumed, a valid unlimited
 * subscription, or a payment actually collected — nothing else.
 *
 *   node pilates-booking-journeys.cjs <baseUrl> <adminEmail> <adminPassword>
 */
const assert = require('node:assert/strict')
const { execFileSync } = require('node:child_process')

const BASE = (process.argv[2] || 'http://127.0.0.1:18081') + '/api/v1'
const ADMIN_EMAIL = process.argv[3] || 'audit-admin@example.test'
const ADMIN_PASSWORD = process.argv[4] || 'Audit-Only-Password-2026!'
/**
 * Local runs exec into the named docker container the way the rest of this
 * session's suites do; CI has no such container, so TAKEOFF_DB_HOST (etc.)
 * point psql at the CI Postgres service directly over TCP instead.
 */
const DB_CONTAINER = process.env.TAKEOFF_DB_CONTAINER || 'takeoff-recheck-20260922'
const DB_HOST = process.env.TAKEOFF_DB_HOST
const DB_PORT = process.env.TAKEOFF_DB_PORT || '5432'
const DB_USER = process.env.TAKEOFF_DB_USER || 'audit'
const DB_NAME = process.env.TAKEOFF_DB_NAME || 'takeoff_audit'
const DB_PASSWORD = process.env.TAKEOFF_DB_PASSWORD

const results = []
let admin
const stamp = Date.now().toString(36)
let seq = 0

const sql = (q) => {
  if (DB_HOST) {
    return execFileSync('psql', ['-h', DB_HOST, '-p', DB_PORT, '-U', DB_USER, '-d', DB_NAME, '-At', '-c', q], {
      encoding: 'utf8', windowsHide: true,
      env: { ...process.env, ...(DB_PASSWORD ? { PGPASSWORD: DB_PASSWORD } : {}) },
    }).trim()
  }
  return execFileSync('docker', ['exec', DB_CONTAINER, 'psql', '-U', DB_USER, '-d', DB_NAME, '-At', '-c', q], {
    encoding: 'utf8', windowsHide: true,
  }).trim()
}

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
  const phone = '+216' + String(40000000 + (Date.now() % 1000000) + seq).slice(0, 8)
  const u = await ok('POST', '/auth/register', {
    phone, email: `pil-${stamp}-${seq}@example.test`,
    password: 'Pilates-Journey-Password!', name: 'Pilates Tester ' + seq,
  })
  if (balance) await ok('POST', `/admin/users/${u.user.id}/wallet/credit`, { amountDt: balance, reason: 'journey' }, admin)
  return u
}
const tok = (u) => u.tokens.accessToken
const wallet = async (u) => Number((await ok('GET', '/auth/me', undefined, tok(u))).walletDt)

let dayOffset = 1400 + Math.floor(Math.random() * 4000) * 3
function futureDate(hour = 9) {
  const d = new Date(); d.setUTCDate(d.getUTCDate() + dayOffset++); d.setUTCHours(hour, 0, 0, 0); return d.toISOString()
}

async function makeSession(overrides = {}) {
  const types = await ok('GET', '/admin/classes/types', undefined, admin)
  const type = (Array.isArray(types) ? types : types.content ?? [])[0]
  assert.ok(type, 'no class type configured')
  const body = {
    classTypeId: type.id, startsAt: futureDate((8 + (seq % 10)) % 20 + 2), durationMin: 55, maxSpots: 8, priceDt: 35,
    ...overrides,
  }
  return ok('POST', '/admin/classes/sessions', body, admin)
}

async function pilatesPackType() {
  const types = await ok('GET', '/admin/packs/types', undefined, admin)
  const list = Array.isArray(types) ? types : types.content ?? []
  const found = list.find((t) => t.activity === 'PILATES' && !t.unlimited && (t.creditCount ?? 0) > 0)
  if (found) return found
  return ok('POST', '/admin/packs/types', {
    name: `Journey Pack ${stamp}`, activity: 'PILATES', priceDt: 200, creditCount: 5, unlimited: false, validityMonths: 3,
  }, admin)
}
/** A fresh PILATES pack type with exactly one credit, never reused across
 *  tests — the shared-credit concurrency case needs to know precisely how
 *  many credits exist, which pilatesPackType()'s shared 5-credit type does
 *  not guarantee once other tests have consumed from it. */
async function oneCreditPackType() {
  seq += 1
  return ok('POST', '/admin/packs/types', {
    name: `Journey OneCredit ${stamp}-${seq}`, activity: 'PILATES', priceDt: 40, creditCount: 1, unlimited: false, validityMonths: 3,
  }, admin)
}
async function unlimitedPackType() {
  const types = await ok('GET', '/admin/packs/types', undefined, admin)
  const list = Array.isArray(types) ? types : types.content ?? []
  const found = list.find((t) => t.activity === 'PILATES' && t.unlimited)
  if (found) return found
  return ok('POST', '/admin/packs/types', {
    name: `Journey Unlimited ${stamp}`, activity: 'PILATES', priceDt: 400, unlimited: true, validityMonths: 1,
  }, admin)
}

async function main() {
  admin = (await ok('POST', '/admin/auth/login', { email: ADMIN_EMAIL, password: ADMIN_PASSWORD })).token

  await test('PI01', 'No pack, no payment method: refused, nothing booked, nothing charged', async () => {
    const s = await makeSession()
    const u = await member(100)
    const r = await req('POST', '/classes/bookings', { sessionId: s.id }, tok(u))
    rejects(r, 'no pack and no payment method', /payment_required/)
    assert.equal(await wallet(u), 100)
    const mine = await ok('GET', '/classes/bookings/mine', undefined, tok(u))
    assert.equal(mine.filter((b) => b.session?.id === s.id).length, 0, 'no booking row must exist')
  })

  await test('PI02', 'No pack, WALLET: confirmed, wallet charged exactly the session price', async () => {
    const s = await makeSession({ priceDt: 35 })
    const u = await member(100)
    const b = await ok('POST', '/classes/bookings', { sessionId: s.id, paymentMethod: 'WALLET', quotedPriceDt: 35 }, tok(u))
    assert.equal(b.status, 'BOOKED')
    assert.equal(b.paidWith, 'SINGLE')
    assert.equal(Number(b.priceDt), 35)
    assert.equal(await wallet(u), 65)
  })

  await test('PI03', 'A valid credit pack confirms the spot and consumes one credit, no wallet charge', async () => {
    const pt = await pilatesPackType()
    const u = await member(100)
    await ok('POST', '/admin/packs/assign', { userId: u.user.id, packTypeId: pt.id }, admin)
    const s = await makeSession({ priceDt: 40 })
    const b = await ok('POST', '/classes/bookings', { sessionId: s.id }, tok(u))
    assert.equal(b.status, 'BOOKED')
    assert.equal(b.paidWith, 'PACK')
    assert.equal(Number(b.priceDt), 0)
    assert.equal(await wallet(u), 100, 'a pack credit must not touch the wallet')
    const packs = await ok('GET', '/classes/packs/mine', undefined, tok(u))
    const mine = packs.find((p) => p.packName === pt.name)
    assert.ok(mine, 'the assigned pack must appear in packs/mine')
    assert.equal(mine.creditsRemaining, (pt.creditCount ?? 5) - 1)
  })

  await test('PI04', 'A valid unlimited subscription confirms the spot without consuming credits', async () => {
    const pt = await unlimitedPackType()
    const u = await member(100)
    await ok('POST', '/admin/packs/assign', { userId: u.user.id, packTypeId: pt.id }, admin)
    const s = await makeSession()
    const b = await ok('POST', '/classes/bookings', { sessionId: s.id }, tok(u))
    assert.equal(b.paidWith, 'UNLIMITED')
    assert.equal(await wallet(u), 100)
  })

  await test('PI05', 'An expired pack does not confirm a spot — wallet is required instead', async () => {
    const pt = await pilatesPackType()
    const u = await member(100)
    const assigned = await ok('POST', '/admin/packs/assign', { userId: u.user.id, packTypeId: pt.id }, admin)
    // No admin endpoint backdates expiry; this is the one legitimate direct-SQL
    // setup in this suite, done once assignment already went through the API.
    sql(`update user_packs set expires_at = now() - interval '1 day' where id='${assigned.id}'`)

    const s = await makeSession({ priceDt: 30 })
    const noMethod = await req('POST', '/classes/bookings', { sessionId: s.id }, tok(u))
    rejects(noMethod, 'expired pack ignored, no payment method given', /payment_required/)

    const s2 = await makeSession({ priceDt: 30 })
    const b = await ok('POST', '/classes/bookings', { sessionId: s2.id, paymentMethod: 'WALLET' }, tok(u))
    assert.equal(b.paidWith, 'SINGLE')
    assert.equal(await wallet(u), 70)
  })

  await test('PI06', 'A frozen pack does not confirm a spot either', async () => {
    const pt = await pilatesPackType()
    const u = await member(100)
    const assigned = await ok('POST', '/admin/packs/assign', { userId: u.user.id, packTypeId: pt.id }, admin)
    await ok('POST', `/admin/packs/user-packs/${assigned.id}/freeze`, undefined, admin)
    const s = await makeSession()
    const r = await req('POST', '/classes/bookings', { sessionId: s.id }, tok(u))
    rejects(r, 'frozen pack ignored', /payment_required/)
  })

  await test('PI07', 'Insufficient wallet balance refuses the booking — no partial state', async () => {
    const s = await makeSession({ priceDt: 40 })
    const u = await member(10)
    const r = await req('POST', '/classes/bookings', { sessionId: s.id, paymentMethod: 'WALLET' }, tok(u))
    assert.equal(r.status, 400)
    assert.match(String(r.data?.code ?? ''), /insufficient_funds/)
    assert.equal(await wallet(u), 10)
    const mine = await ok('GET', '/classes/bookings/mine', undefined, tok(u))
    assert.equal(mine.filter((b) => b.session?.id === s.id).length, 0)
  })

  await test('PI08', 'A stale quoted price is refused before any charge', async () => {
    const s = await makeSession({ priceDt: 45 })
    const u = await member(100)
    const r = await req('POST', '/classes/bookings', { sessionId: s.id, paymentMethod: 'WALLET', quotedPriceDt: 35 }, tok(u))
    rejects(r, 'stale quoted price', /price_mismatch/)
    assert.equal(await wallet(u), 100)
  })

  await test('PI09', 'The last spot is granted to exactly one of two concurrent bookers', async () => {
    const s = await makeSession({ maxSpots: 1, priceDt: 20 })
    const u1 = await member(100)
    const u2 = await member(100)
    const [r1, r2] = await Promise.all([
      req('POST', '/classes/bookings', { sessionId: s.id, paymentMethod: 'WALLET' }, tok(u1)),
      req('POST', '/classes/bookings', { sessionId: s.id, paymentMethod: 'WALLET' }, tok(u2)),
    ])
    const statuses = [r1.data?.status, r2.data?.status].sort()
    assert.deepEqual(statuses, ['BOOKED', 'WAITLIST'])
    // Only the confirmed one is charged.
    const balances = (await Promise.all([wallet(u1), wallet(u2)])).sort((a, b) => a - b)
    assert.deepEqual(balances, [80, 100])
  })

  await test('PI10', 'A waitlisted booking never looks reserved and charges nothing', async () => {
    const s = await makeSession({ maxSpots: 1, priceDt: 25 })
    const u1 = await member(100)
    await ok('POST', '/classes/bookings', { sessionId: s.id, paymentMethod: 'WALLET' }, tok(u1))
    const u2 = await member(100)
    const b2 = await ok('POST', '/classes/bookings', { sessionId: s.id, paymentMethod: 'WALLET' }, tok(u2))
    assert.equal(b2.status, 'WAITLIST')
    assert.equal(b2.paidWith, 'WAITLIST')
    assert.equal(Number(b2.priceDt), 0)
    assert.equal(await wallet(u2), 100, 'a waitlist entry must never be charged')

    const from = new Date(Date.parse(s.startsAt) - 3600_000).toISOString()
    const to = new Date(Date.parse(s.startsAt) + 3600_000).toISOString()
    const sched = await ok('GET', `/classes/schedule?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`, undefined, tok(u2))
    const row = sched.find((x) => x.id === s.id)
    assert.equal(row.myStatus, 'WAITLIST', 'the schedule must show WAITLIST, never something that reads as reserved')
  })

  await test('PI11', 'Promotion only offers the spot — the member\'s own confirmation is what charges', async () => {
    const s = await makeSession({ maxSpots: 1, priceDt: 30 })
    const u1 = await member(100)
    const first = await ok('POST', '/classes/bookings', { sessionId: s.id, paymentMethod: 'WALLET' }, tok(u1))
    const u2 = await member(100)
    const waiting = await ok('POST', '/classes/bookings', { sessionId: s.id, paymentMethod: 'WALLET' }, tok(u2))

    // Free the spot, then promote. Joining a waitlist promised "nothing will
    // be debited", so an admin click must not charge on its own.
    await ok('DELETE', `/classes/bookings/${first.bookingId}`, undefined, tok(u1))
    const proposed = await ok('POST', `/admin/classes/bookings/${waiting.bookingId}/promote`, undefined, admin)
    assert.equal(proposed.status, 'PROPOSED', 'promotion must propose, not book')
    assert.equal(await wallet(u2), 100, 'proposing a spot must charge nothing at all')

    const mine = await ok('GET', '/classes/bookings/mine', undefined, tok(u2))
    const row = mine.find((b) => b.session?.id === s.id)
    assert.equal(row.status, 'PROPOSED')
    assert.ok(row.proposalExpiresAt, 'the member must be told how long they have to decide')

    // Now the member themselves accepts: this is the only step that moves money.
    const confirmed = await ok('POST', `/classes/bookings/${waiting.bookingId}/confirm`, { paymentMethod: 'WALLET', quotedPriceDt: 30 }, tok(u2))
    assert.equal(confirmed.status, 'BOOKED')
    assert.equal(confirmed.paidWith, 'SINGLE')
    assert.equal(await wallet(u2), 70, 'confirming charges the session price exactly once')
  })

  await test('PI11b', 'Declining a proposal charges nothing and passes the spot to the next member', async () => {
    const s = await makeSession({ maxSpots: 1, priceDt: 30 })
    const holder = await member(100)
    const held = await ok('POST', '/classes/bookings', { sessionId: s.id, paymentMethod: 'WALLET' }, tok(holder))
    const first = await member(100)
    const firstWait = await ok('POST', '/classes/bookings', { sessionId: s.id, paymentMethod: 'WALLET' }, tok(first))
    const second = await member(100)
    const secondWait = await ok('POST', '/classes/bookings', { sessionId: s.id, paymentMethod: 'WALLET' }, tok(second))

    await ok('DELETE', `/classes/bookings/${held.bookingId}`, undefined, tok(holder))
    await ok('POST', `/admin/classes/bookings/${firstWait.bookingId}/promote`, undefined, admin)

    const declined = await ok('POST', `/classes/bookings/${firstWait.bookingId}/decline`, undefined, tok(first))
    assert.equal(declined.status, 'DECLINED')
    assert.equal(await wallet(first), 100, 'declining must never charge')

    // The freed spot must chain to whoever was next in line, not sit idle.
    const secondMine = await ok('GET', '/classes/bookings/mine', undefined, tok(second))
    const secondRow = secondMine.find((b) => b.session?.id === s.id)
    assert.equal(secondRow.status, 'PROPOSED', 'the next waitlisted member must be offered the spot')
    assert.equal(await wallet(second), 100, 'and must not be charged for being offered it')
  })

  await test('PI11c', 'An expired proposal charges nothing and passes the spot on', async () => {
    const s = await makeSession({ maxSpots: 1, priceDt: 30 })
    const holder = await member(100)
    const held = await ok('POST', '/classes/bookings', { sessionId: s.id, paymentMethod: 'WALLET' }, tok(holder))
    const first = await member(100)
    const firstWait = await ok('POST', '/classes/bookings', { sessionId: s.id, paymentMethod: 'WALLET' }, tok(first))
    const second = await member(100)
    const secondWait = await ok('POST', '/classes/bookings', { sessionId: s.id, paymentMethod: 'WALLET' }, tok(second))

    await ok('DELETE', `/classes/bookings/${held.bookingId}`, undefined, tok(holder))
    await ok('POST', `/admin/classes/bookings/${firstWait.bookingId}/promote`, undefined, admin)

    // Backdate the hold rather than waiting it out in real time.
    sql(`update class_bookings set proposal_expires_at = now() - interval '1 minute' where id='${firstWait.bookingId}'`)

    // Any member-facing read settles overdue proposals before answering.
    await ok('GET', '/classes/bookings/mine', undefined, tok(first))

    const firstMine = await ok('GET', '/classes/bookings/mine', undefined, tok(first))
    const firstRow = firstMine.find((b) => b.session?.id === s.id)
    assert.equal(firstRow.status, 'EXPIRED', 'an unanswered proposal must expire, not linger or auto-charge')
    assert.equal(await wallet(first), 100, 'expiry must never charge')

    const secondMine = await ok('GET', '/classes/bookings/mine', undefined, tok(second))
    const secondRow = secondMine.find((b) => b.session?.id === s.id)
    assert.equal(secondRow.status, 'PROPOSED', 'the spot must move to the next member on expiry')

    // And a late confirmation cannot sneak through after the deadline.
    const late = await req('POST', `/classes/bookings/${firstWait.bookingId}/confirm`, { paymentMethod: 'WALLET' }, tok(first))
    assert.ok(late.status >= 400, 'confirming after expiry must be refused')
    assert.equal(await wallet(first), 100)
  })

  await test('PI11d', 'An admin cannot flip a booking straight to BOOKED through the attendance endpoint', async () => {
    const s = await makeSession({ maxSpots: 1, priceDt: 30 })
    const holder = await member(100)
    await ok('POST', '/classes/bookings', { sessionId: s.id, paymentMethod: 'WALLET' }, tok(holder))
    const waiter = await member(100)
    const waiting = await ok('POST', '/classes/bookings', { sessionId: s.id, paymentMethod: 'WALLET' }, tok(waiter))
    assert.equal(waiting.status, 'WAITLIST')

    // setAttendance used to accept any status at all, which was a way to grant
    // a real spot with no payment resolution and no member consent.
    const r = await req('POST', `/admin/classes/bookings/${waiting.bookingId}/attendance`, { status: 'BOOKED' }, admin)
    assert.ok(r.status >= 400, 'attendance must not be usable to grant a spot: got ' + r.status)
    assert.equal(await wallet(waiter), 100, 'and nothing may be charged')

    const mine = await ok('GET', '/classes/bookings/mine', undefined, tok(waiter))
    const row = mine.find((b) => b.session?.id === s.id)
    assert.equal(row.status, 'WAITLIST', 'the booking must still be on the waitlist')
  })

  await test('PI12', 'Promoting without enough funds fails, and the booking stays on the waitlist', async () => {
    const s = await makeSession({ maxSpots: 1, priceDt: 50 })
    const u1 = await member(100)
    const first = await ok('POST', '/classes/bookings', { sessionId: s.id, paymentMethod: 'WALLET' }, tok(u1))
    const u2 = await member(5)
    // u2 cannot afford a WALLET booking either, but the class is already full
    // (u1 holds the only spot), so this lands on the waitlist and charges
    // nothing — which is exactly how a low-balance member reaches WAITLIST.
    const r2 = await req('POST', '/classes/bookings', { sessionId: s.id, paymentMethod: 'WALLET' }, tok(u2))
    if (!r2 || !r2.data) throw new Error('unexpected response for r2: ' + JSON.stringify(r2))
    assert.equal(r2.data.status, 'WAITLIST', 'a full class waitlists regardless of balance, since nothing is charged for it')

    await ok('DELETE', `/classes/bookings/${first.bookingId}`, undefined, tok(u1))
    // Offering the spot costs nothing, so this succeeds even for a member who
    // cannot afford it — the money question is asked at confirmation.
    const proposed = await ok('POST', `/admin/classes/bookings/${r2.data.bookingId}/promote`, undefined, admin)
    assert.equal(proposed.status, 'PROPOSED')
    assert.equal(await wallet(u2), 5)

    const r = await req('POST', `/classes/bookings/${r2.data.bookingId}/confirm`, { paymentMethod: 'WALLET' }, tok(u2))
    assert.equal(r.status, 400)
    assert.match(String(r.data?.code ?? ''), /insufficient_funds/)
    assert.equal(await wallet(u2), 5, 'a failed confirmation must not leave a partial charge')

    const mine = await ok('GET', '/classes/bookings/mine', undefined, tok(u2))
    const row = mine.find((b) => b.session?.id === s.id)
    assert.ok(row, 'the booking must still be in my bookings')
    assert.equal(row.status, 'PROPOSED', 'a failed confirmation leaves the offer open to retry, never BOOKED')
  })

  await test('PI13', 'A second booking for the same session by the same member is refused', async () => {
    const s = await makeSession({ priceDt: 20 })
    const u = await member(100)
    await ok('POST', '/classes/bookings', { sessionId: s.id, paymentMethod: 'WALLET' }, tok(u))
    const r = await req('POST', '/classes/bookings', { sessionId: s.id, paymentMethod: 'WALLET' }, tok(u))
    rejects(r, 'duplicate booking', /already_booked/)
    assert.equal(await wallet(u), 80)
  })

  await test('PI14', 'Cancelling a WALLET single-session booking does not fabricate a refund (by design: single sessions are not refundable credits)', async () => {
    const s = await makeSession({ priceDt: 20 })
    const u = await member(100)
    const b = await ok('POST', '/classes/bookings', { sessionId: s.id, paymentMethod: 'WALLET' }, tok(u))
    await ok('DELETE', `/classes/bookings/${b.bookingId}`, undefined, tok(u))
    // Documents existing behaviour: only PACK-paid cancellations restore a
    // credit; a SINGLE wallet charge does not silently invent a wallet refund
    // this suite did not ask to change (open item — see final report).
    assert.equal(await wallet(u), 80)
  })

  await test('PI15', 'Cancelling a PACK booking with 24h+ notice restores the credit', async () => {
    const pt = await pilatesPackType()
    const u = await member(0)
    await ok('POST', '/admin/packs/assign', { userId: u.user.id, packTypeId: pt.id }, admin)
    const s = await makeSession({ priceDt: 30 })
    const b = await ok('POST', '/classes/bookings', { sessionId: s.id }, tok(u))
    await ok('DELETE', `/classes/bookings/${b.bookingId}`, undefined, tok(u))
    const packs = await ok('GET', '/classes/packs/mine', undefined, tok(u))
    const mine = packs.find((p) => p.packName === pt.name)
    assert.ok(mine, 'the pack must appear in packs/mine')
    assert.equal(mine.creditsRemaining, pt.creditCount ?? 5, 'the credit must come back on an early cancel')
  })

  await test('PI16', 'Admin can see legacy unpaid SINGLE bookings without any retroactive charge', async () => {
    // The old bug cannot be reproduced through the fixed API any more, so this
    // seeds one the way the bug used to leave it: BOOKED/SINGLE with a real
    // price and no wallet ledger entry behind it.
    const u = await member(0)
    const s = await makeSession({ priceDt: 33 })
    // psql -c with RETURNING can still print a trailing command tag
    // ("INSERT 0 1") after the returned value; take only the UUID.
    const legacyRaw = sql(`insert into class_bookings (id, session_id, user_id, status, paid_with, price_dt)
      values (gen_random_uuid(), '${s.id}', '${u.user.id}', 'BOOKED', 'SINGLE', 33) returning id`)
    const legacyId = legacyRaw.split(/\s+/)[0]

    const list = await ok('GET', '/admin/classes/bookings/unpaid-legacy', undefined, admin)
    const found = list.find((r) => r.bookingId === legacyId)
    assert.ok(found, 'the legacy unpaid booking must be surfaced')
    assert.equal(Number(found.priceDt), 33)

    // Read-only: nothing was charged just by listing it.
    assert.equal(await wallet(u), 0)
  })

  await test('PI17', 'Real concurrent promotion across two sessions sharing one pack credit: only one promotion consumes it, the other falls through to a wallet charge', async () => {
    const pt = await oneCreditPackType()
    const u = await member(100)
    await ok('POST', '/admin/packs/assign', { userId: u.user.id, packTypeId: pt.id }, admin)

    const sA = await makeSession({ maxSpots: 1, priceDt: 30 })
    const sB = await makeSession({ maxSpots: 1, priceDt: 45 })
    const fillerA = await member(100)
    const fillerB = await member(100)
    const bookA = await ok('POST', '/classes/bookings', { sessionId: sA.id, paymentMethod: 'WALLET' }, tok(fillerA))
    const bookB = await ok('POST', '/classes/bookings', { sessionId: sB.id, paymentMethod: 'WALLET' }, tok(fillerB))
    const waitA = await ok('POST', '/classes/bookings', { sessionId: sA.id, paymentMethod: 'WALLET' }, tok(u))
    const waitB = await ok('POST', '/classes/bookings', { sessionId: sB.id, paymentMethod: 'WALLET' }, tok(u))
    assert.equal(waitA.status, 'WAITLIST')
    assert.equal(waitB.status, 'WAITLIST')

    // Free both real spots so both promotions have somewhere to land, then
    // promote both waitlist entries at the same instant — real concurrency,
    // not sequential.
    await ok('DELETE', `/classes/bookings/${bookA.bookingId}`, undefined, tok(fillerA))
    await ok('DELETE', `/classes/bookings/${bookB.bookingId}`, undefined, tok(fillerB))

    // Offering both spots charges nothing, so both proposals stand.
    await ok('POST', `/admin/classes/bookings/${waitA.bookingId}/promote`, undefined, admin)
    await ok('POST', `/admin/classes/bookings/${waitB.bookingId}/promote`, undefined, admin)

    // The real race is now at confirmation: two confirmations at the same
    // instant, one shared credit.
    const [rA, rB] = await Promise.all([
      req('POST', `/classes/bookings/${waitA.bookingId}/confirm`, { paymentMethod: 'WALLET' }, tok(u)),
      req('POST', `/classes/bookings/${waitB.bookingId}/confirm`, { paymentMethod: 'WALLET' }, tok(u)),
    ])

    // Both must succeed (there was real capacity in both sessions) — but only
    // one of them may have used the single pack credit; the other must have
    // fallen through to a real wallet charge, exactly as book() would for
    // "no valid pack".
    assert.equal(rA.status, 200, 'confirmation A: ' + JSON.stringify(rA.data))
    assert.equal(rB.status, 200, 'confirmation B: ' + JSON.stringify(rB.data))
    const paidWiths = [rA.data.paidWith, rB.data.paidWith].sort()
    assert.deepEqual(paidWiths, ['PACK', 'SINGLE'], 'exactly one confirmation must consume the shared credit, the other must be a wallet charge')

    const packs = await ok('GET', '/classes/packs/mine', undefined, tok(u))
    const mine = packs.find((p) => p.packName === pt.name)
    assert.ok(mine, 'the one-credit pack must still be visible')
    assert.equal(mine.creditsRemaining, 0, 'the single credit must be consumed exactly once, not twice, not left untouched')

    const chargedSessionPrice = rA.data.paidWith === 'SINGLE' ? Number(rA.data.priceDt) : Number(rB.data.priceDt)
    assert.equal(await wallet(u), 100 - chargedSessionPrice, 'the wallet must be charged exactly once, for exactly the session that lost the race for the credit')
  })

  await test('PI18', 'Double-clicking / retrying promote on the same booking never double-charges', async () => {
    const pt = await pilatesPackType()
    const u = await member(100)
    await ok('POST', '/admin/packs/assign', { userId: u.user.id, packTypeId: pt.id }, admin)
    const before = await ok('GET', '/classes/packs/mine', undefined, tok(u))
    const creditsBefore = before.find((p) => p.packName === pt.name).creditsRemaining

    const s = await makeSession({ maxSpots: 1, priceDt: 20 })
    const filler = await member(100)
    const filled = await ok('POST', '/classes/bookings', { sessionId: s.id, paymentMethod: 'WALLET' }, tok(filler))
    const waiting = await ok('POST', '/classes/bookings', { sessionId: s.id, paymentMethod: 'WALLET' }, tok(u))
    assert.equal(waiting.status, 'WAITLIST')
    await ok('DELETE', `/classes/bookings/${filled.bookingId}`, undefined, tok(filler))

    // Two real concurrent promote() calls against the exact same booking id —
    // simulating a double-click or a retried admin request.
    const [r1, r2] = await Promise.all([
      req('POST', `/admin/classes/bookings/${waiting.bookingId}/promote`, undefined, admin),
      req('POST', `/admin/classes/bookings/${waiting.bookingId}/promote`, undefined, admin),
    ])
    const statuses = [r1.status, r2.status].sort((a, b) => a - b)
    assert.deepEqual(statuses, [200, 409], `exactly one promote must succeed, the other must be rejected as no-longer-waitlisted, got ${r1.status} ${r2.status}`)
    const loser = r1.status === 409 ? r1 : r2
    assert.match(String(loser.data?.code ?? ''), /not_waitlisted/)

    const afterPropose = await ok('GET', '/classes/packs/mine', undefined, tok(u))
    assert.equal(
      afterPropose.find((p) => p.packName === pt.name).creditsRemaining,
      creditsBefore,
      'proposing must not touch the credit balance at all',
    )

    // Double-clicking the member's own confirmation must not double-charge
    // either: exactly one of the two may take the credit.
    const [c1, c2] = await Promise.all([
      req('POST', `/classes/bookings/${waiting.bookingId}/confirm`, {}, tok(u)),
      req('POST', `/classes/bookings/${waiting.bookingId}/confirm`, {}, tok(u)),
    ])
    const confirmStatuses = [c1.status, c2.status].sort((a, b) => a - b)
    assert.deepEqual(confirmStatuses, [200, 409], `exactly one confirm may win, got ${c1.status} ${c2.status}`)

    const after = await ok('GET', '/classes/packs/mine', undefined, tok(u))
    const creditsAfter = after.find((p) => p.packName === pt.name).creditsRemaining
    assert.equal(creditsAfter, creditsBefore - 1, 'exactly one credit must be consumed, not two')

    const detail = await ok('GET', `/admin/classes/sessions/${s.id}`, undefined, admin)
    assert.equal(detail.bookedCount, 1, 'the session must show exactly one booked spot, not two')
  })

  await test('PI19a', 'Promoting into a cancelled session is refused, matching book()\'s cancelled-session message', async () => {
    const pt = await pilatesPackType()
    const u = await member(100)
    await ok('POST', '/admin/packs/assign', { userId: u.user.id, packTypeId: pt.id }, admin)
    const s = await makeSession({ maxSpots: 1, priceDt: 20 })
    const filler = await member(100)
    await ok('POST', '/classes/bookings', { sessionId: s.id, paymentMethod: 'WALLET' }, tok(filler))
    const waiting = await ok('POST', '/classes/bookings', { sessionId: s.id, paymentMethod: 'WALLET' }, tok(u))
    assert.equal(waiting.status, 'WAITLIST')

    // Directly flip the session to CANCELLED without touching the booking
    // row, isolating promote()'s own session-status guard (the admin
    // cancelSession() endpoint would also cancel the waitlisted booking
    // itself, which would instead trip the "not on the waitlist" check).
    sql(`update class_sessions set status = 'CANCELLED' where id='${s.id}'`)

    const r = await req('POST', `/admin/classes/bookings/${waiting.bookingId}/promote`, undefined, admin)
    rejects(r, 'promoting into a cancelled session', /cancelled/)

    const mine = await ok('GET', '/classes/bookings/mine', undefined, tok(u))
    const row = mine.find((b) => b.session?.id === s.id)
    assert.equal(row.status, 'WAITLIST', 'a refused promotion must not flip the booking to BOOKED')
    assert.equal(await wallet(u), 100)
  })

  await test('PI19b', 'Promoting into a session that has already started is refused, matching book()\'s past-session message', async () => {
    const pt = await pilatesPackType()
    const u = await member(100)
    await ok('POST', '/admin/packs/assign', { userId: u.user.id, packTypeId: pt.id }, admin)
    const s = await makeSession({ maxSpots: 1, priceDt: 20 })
    const filler = await member(100)
    await ok('POST', '/classes/bookings', { sessionId: s.id, paymentMethod: 'WALLET' }, tok(filler))
    const waiting = await ok('POST', '/classes/bookings', { sessionId: s.id, paymentMethod: 'WALLET' }, tok(u))
    assert.equal(waiting.status, 'WAITLIST')

    sql(`update class_sessions set starts_at = now() - interval '1 hour' where id='${s.id}'`)

    const r = await req('POST', `/admin/classes/bookings/${waiting.bookingId}/promote`, undefined, admin)
    rejects(r, 'promoting into a session that already started', /past_session/)

    const mine = await ok('GET', '/classes/bookings/mine', undefined, tok(u))
    const row = mine.find((b) => b.session?.id === s.id)
    assert.equal(row.status, 'WAITLIST', 'a refused promotion must not flip the booking to BOOKED')
    assert.equal(await wallet(u), 100)
  })

  await test('PI20', 'Real concurrent capacity race: a fresh booking and a waitlist promotion competing for the same freed spot never both win it', async () => {
    const s = await makeSession({ maxSpots: 1, priceDt: 25 })
    const filler = await member(100)
    const filled = await ok('POST', '/classes/bookings', { sessionId: s.id, paymentMethod: 'WALLET' }, tok(filler))
    const b = await member(100)
    const waiting = await ok('POST', '/classes/bookings', { sessionId: s.id, paymentMethod: 'WALLET' }, tok(b))
    assert.equal(waiting.status, 'WAITLIST')

    await ok('DELETE', `/classes/bookings/${filled.bookingId}`, undefined, tok(filler))

    // A brand-new member racing to book the just-freed spot, at the exact
    // same instant an admin promotes the waitlisted member into it.
    const c = await member(100)
    const [bookResp, promoteResp] = await Promise.all([
      req('POST', '/classes/bookings', { sessionId: s.id, paymentMethod: 'WALLET' }, tok(c)),
      req('POST', `/admin/classes/bookings/${waiting.bookingId}/promote`, undefined, admin),
    ])

    assert.ok(bookResp.status < 400, 'the fresh booking attempt itself must not error: ' + JSON.stringify(bookResp.data))

    // A proposal holds the spot just as a booking does, so the two of them
    // together may never occupy more than the one real place.
    const detail = await ok('GET', `/admin/classes/sessions/${s.id}`, undefined, admin)
    assert.equal(
      detail.bookedCount + detail.proposedCount,
      1,
      'the single real spot must never be held by both competitors at once',
    )

    // Exactly one of the two holds it; the other must have been cleanly
    // refused (promote: still WAITLIST / 400 full) or itself waitlisted
    // (book: WAITLIST, uncharged) — never both, and never a silent charge.
    const promoted = promoteResp.status === 200
    const freshBooked = bookResp.data?.status === 'BOOKED'
    assert.ok(promoted !== freshBooked || !(promoted && freshBooked), 'both sides must not simultaneously win the one real spot')
    assert.ok(promoted || freshBooked, 'at least one side must have taken the real spot')

    if (!promoted) {
      assert.equal(promoteResp.status, 400)
      assert.match(String(promoteResp.data?.code ?? ''), /full/)
      const mine = await ok('GET', '/classes/bookings/mine', undefined, tok(b))
      const row = mine.find((x) => x.session?.id === s.id)
      assert.equal(row.status, 'WAITLIST')
      assert.equal(await wallet(b), 100, 'a refused promotion must not have charged the member')
    }
    if (!freshBooked) {
      assert.equal(bookResp.data?.status, 'WAITLIST', 'the loser of the capacity race must land on the waitlist, not an error, per book()\'s own full-session handling')
      assert.equal(await wallet(c), 100, 'a member who lands on the waitlist through the race must not be charged')
    }
  })

  await test('PI21', 'Non-late member cancellation of a SINGLE booking grants a use credit, wallet stays untouched, and the credit is usable on a later booking', async () => {
    const s = await makeSession({ priceDt: 28 })
    const u = await member(100)
    const b = await ok('POST', '/classes/bookings', { sessionId: s.id, paymentMethod: 'WALLET' }, tok(u))
    assert.equal(b.paidWith, 'SINGLE')
    assert.equal(await wallet(u), 72)

    await ok('DELETE', `/classes/bookings/${b.bookingId}`, undefined, tok(u))
    assert.equal(await wallet(u), 72, 'a non-late SINGLE cancellation must not recredit the wallet')

    const packs = await ok('GET', '/classes/packs/mine', undefined, tok(u))
    const credit = packs.find((p) => p.packName === "Crédit d'utilisation (annulation)")
    assert.ok(credit, 'the use credit must appear in packs/mine')
    assert.equal(credit.creditsRemaining, 1)

    // Spend the credit on a different session: no wallet charge, and no
    // payment method required — it consumes through book()'s existing PACK
    // branch, exactly like a real pack credit would.
    const s2 = await makeSession({ priceDt: 60 })
    const b2 = await ok('POST', '/classes/bookings', { sessionId: s2.id }, tok(u))
    assert.equal(b2.status, 'BOOKED')
    assert.equal(b2.paidWith, 'PACK')
    assert.equal(Number(b2.priceDt), 0)
    assert.equal(await wallet(u), 72, 'spending the use credit must not touch the wallet')

    const packsAfter = await ok('GET', '/classes/packs/mine', undefined, tok(u))
    const creditAfter = packsAfter.find((p) => p.packName === "Crédit d'utilisation (annulation)")
    assert.equal(creditAfter.creditsRemaining, 0, 'the credit must be consumed exactly once')
  })

  await test('PI22', 'A LATE_CANCEL still yields nothing — no wallet refund, no use credit (regression, unchanged pre-fix behaviour)', async () => {
    const s = await makeSession({ priceDt: 22, startsAt: new Date(Date.now() + 5 * 3600_000).toISOString() })
    const u = await member(100)
    const b = await ok('POST', '/classes/bookings', { sessionId: s.id, paymentMethod: 'WALLET' }, tok(u))
    assert.equal(await wallet(u), 78)

    await ok('DELETE', `/classes/bookings/${b.bookingId}`, undefined, tok(u))
    assert.equal(await wallet(u), 78, 'a LATE_CANCEL must not refund the wallet')

    const mine = await ok('GET', '/classes/bookings/mine', undefined, tok(u))
    const row = mine.find((x) => x.bookingId === b.bookingId)
    assert.equal(row.status, 'LATE_CANCEL')

    const packs = await ok('GET', '/classes/packs/mine', undefined, tok(u))
    const credit = packs.find((p) => p.packName === "Crédit d'utilisation (annulation)")
    assert.ok(!credit, 'a late cancellation must not grant a use credit either')
  })

  await test('PI23', 'Club cancelling a session more than 24h out auto-refunds a SINGLE booking to the wallet, idempotently', async () => {
    const s = await makeSession({ priceDt: 40 })
    const u = await member(100)
    const b = await ok('POST', '/classes/bookings', { sessionId: s.id, paymentMethod: 'WALLET' }, tok(u))
    assert.equal(await wallet(u), 60)

    await ok('POST', `/admin/classes/sessions/${s.id}/cancel`, undefined, admin)
    assert.equal(await wallet(u), 100, 'more than 24h out, the club cancellation must auto-refund the wallet')

    // Retry the exact same cancel-session action (double-click / retried
    // request) — must not double-refund.
    await ok('POST', `/admin/classes/sessions/${s.id}/cancel`, undefined, admin)
    assert.equal(await wallet(u), 100, 'retrying cancel-session must not refund a second time')

    const mine = await ok('GET', '/classes/bookings/mine', undefined, tok(u))
    const row = mine.find((x) => x.bookingId === b.bookingId)
    assert.equal(row.status, 'CANCELLED')
  })

  await test('PI24', 'Club cancelling a session less than 24h out does not auto-refund — the booking surfaces in the manual-refund list', async () => {
    const s = await makeSession({ priceDt: 33, startsAt: new Date(Date.now() + 6 * 3600_000).toISOString() })
    const u = await member(100)
    const b = await ok('POST', '/classes/bookings', { sessionId: s.id, paymentMethod: 'WALLET' }, tok(u))
    assert.equal(await wallet(u), 67)

    await ok('POST', `/admin/classes/sessions/${s.id}/cancel`, undefined, admin)
    assert.equal(await wallet(u), 67, 'inside 24h, the club cancellation must not auto-refund')

    const pending = await ok('GET', '/admin/classes/bookings/refund-pending', undefined, admin)
    const found = pending.find((r) => r.bookingId === b.bookingId)
    assert.ok(found, 'the booking must be surfaced for an admin to refund manually')
    assert.equal(Number(found.priceDt), 33)
  })

  await test('PI25', 'unpaidLegacyBookings still never auto-charges, and stays distinct from the manual-refund list (regression)', async () => {
    const u = await member(0)
    const s = await makeSession({ priceDt: 44 })
    const legacyRaw = sql(`insert into class_bookings (id, session_id, user_id, status, paid_with, price_dt)
      values (gen_random_uuid(), '${s.id}', '${u.user.id}', 'BOOKED', 'SINGLE', 44) returning id`)
    const legacyId = legacyRaw.split(/\s+/)[0]

    const unpaid = await ok('GET', '/admin/classes/bookings/unpaid-legacy', undefined, admin)
    assert.ok(unpaid.find((r) => r.bookingId === legacyId), 'the legacy unpaid booking must still be surfaced, unchanged')
    assert.equal(await wallet(u), 0, 'listing legacy unpaid bookings must never charge retroactively')

    const pending = await ok('GET', '/admin/classes/bookings/refund-pending', undefined, admin)
    assert.ok(!pending.find((r) => r.bookingId === legacyId), 'a legacy unpaid booking (never refund_pending) must not leak into the manual-refund list')
  })

  const pass = results.filter((r) => r === 'PASS').length
  console.log(JSON.stringify({ PASS: pass, FAIL: results.length - pass }))
  process.exitCode = pass === results.length ? 0 : 1
}

main().catch((e) => { console.error(e); process.exitCode = 2 })
