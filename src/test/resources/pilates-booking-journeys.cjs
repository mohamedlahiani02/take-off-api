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

  await test('PI11', 'Promoting a waitlisted member actually charges them — no free spot', async () => {
    const s = await makeSession({ maxSpots: 1, priceDt: 30 })
    const u1 = await member(100)
    const first = await ok('POST', '/classes/bookings', { sessionId: s.id, paymentMethod: 'WALLET' }, tok(u1))
    const u2 = await member(100)
    const waiting = await ok('POST', '/classes/bookings', { sessionId: s.id, paymentMethod: 'WALLET' }, tok(u2))

    // Free the spot, then promote.
    await ok('DELETE', `/classes/bookings/${first.bookingId}`, undefined, tok(u1))
    const promoted = await ok('POST', `/admin/classes/bookings/${waiting.bookingId}/promote`, undefined, admin)
    assert.equal(promoted.status, 'BOOKED')
    assert.equal(promoted.paidWith, 'SINGLE')
    assert.equal(await wallet(u2), 70, 'promotion must charge the session price')
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
    const r = await req('POST', `/admin/classes/bookings/${r2.data.bookingId}/promote`, undefined, admin)
    assert.equal(r.status, 400)
    assert.match(String(r.data?.code ?? ''), /insufficient_funds/)
    assert.equal(await wallet(u2), 5, 'a failed promotion must not leave a partial charge')

    const mine = await ok('GET', '/classes/bookings/mine', undefined, tok(u2))
    const row = mine.find((b) => b.session?.id === s.id)
    assert.ok(row, 'the waitlisted booking must still be in my bookings')
    assert.equal(row.status, 'WAITLIST', 'the failed promotion must not have flipped the booking to BOOKED')
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

  const pass = results.filter((r) => r === 'PASS').length
  console.log(JSON.stringify({ PASS: pass, FAIL: results.length - pass }))
  process.exitCode = pass === results.length ? 0 : 1
}

main().catch((e) => { console.error(e); process.exitCode = 2 })
