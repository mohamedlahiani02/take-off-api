/**
 * Member tournament registration, end to end, against the real backend.
 *
 * MemberTournamentController used to have GET only — "S'inscrire" was a dead
 * link. These cases exercise the write path added for it: detail -> fields ->
 * pricing -> register, with every rule (deadline, capacity, duplicates,
 * required fields, price, payment rule, waitlist) enforced server-side.
 *
 *   node tournament-registration-journeys.cjs <baseUrl> <adminEmail> <adminPassword>
 */
const assert = require('node:assert/strict')

const BASE = (process.argv[2] || 'http://127.0.0.1:18081') + '/api/v1'
const ADMIN_EMAIL = process.argv[3] || 'audit-admin@example.test'
const ADMIN_PASSWORD = process.argv[4] || 'Audit-Only-Password-2026!'

const results = []
let admin
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
  const phone = '+216' + String(20000000 + (Date.now() % 1000000) + seq).slice(0, 8)
  const u = await ok('POST', '/auth/register', {
    phone, email: `treg-${stamp}-${seq}@example.test`,
    password: 'Tournament-Journey-Password!', name: 'Tournament Tester ' + seq,
  })
  if (balance) await ok('POST', `/admin/users/${u.user.id}/wallet/credit`, { amountDt: balance, reason: 'journey' }, admin)
  return u
}
const tok = (u) => u.tokens.accessToken
const wallet = async (u) => Number((await ok('GET', '/auth/me', undefined, tok(u))).walletDt)

const BASE_TOURNAMENT = {
  format: 'AMERICANO', category: 'MIXED', entryFeeDt: 40,
  registrationMode: 'OPEN', paymentRule: 'BOTH', autoWaitlist: false, manualValidation: false,
}
let dayOffset = 300 + Math.floor(Math.random() * 4000) * 3
function futureDate() {
  const d = new Date(); d.setUTCDate(d.getUTCDate() + dayOffset++); return d.toISOString()
}

async function makeTournament(overrides = {}) {
  const body = { ...BASE_TOURNAMENT, title: `Journey Cup ${stamp}-${++seq}`, startsAt: futureDate(), ...overrides }
  const t = await ok('POST', '/admin/tournaments', body, admin)
  await ok('POST', `/admin/tournaments/${t.id}/status`, { status: 'REGISTRATION_OPEN' }, admin)
  return t
}

async function addAgreementField(tournamentId) {
  return ok('POST', `/admin/tournaments/${tournamentId}/fields`, {
    fieldType: 'AGREEMENT', label: 'I accept the rules', required: true, displayOrder: 0,
  }, admin)
}
async function addPartnerField(tournamentId) {
  return ok('POST', `/admin/tournaments/${tournamentId}/fields`, {
    fieldType: 'PARTNER', label: 'Partner name', required: false, displayOrder: 1,
  }, admin)
}

async function main() {
  admin = (await ok('POST', '/admin/auth/login', { email: ADMIN_EMAIL, password: ADMIN_PASSWORD })).token

  await test('T01', 'Full registration: fields -> pricing -> register -> visible admin + profile', async () => {
    const t = await makeTournament()
    const agreement = await addAgreementField(t.id)

    const fields = await ok('GET', `/tournaments/${t.id}/fields`)
    assert.equal(fields.length, 1)
    const pricing = await ok('GET', `/tournaments/${t.id}/pricing`)
    assert.equal(pricing.length, 0) // no tiers configured -> flat entryFeeDt

    const u = await member(100)
    const reg = await ok('POST', `/tournaments/${t.id}/register`, {
      answers: { [agreement.id]: true }, paymentMethod: 'WALLET', quotedPriceDt: 40,
    }, tok(u))
    assert.equal(reg.status, 'CONFIRMED')
    assert.equal(reg.paymentStatus, 'PAID')
    assert.equal(Number(reg.amountPaidDt), 40)
    assert.equal(await wallet(u), 60)

    const adminRegs = await ok('GET', `/admin/tournaments/${t.id}/registrations`, undefined, admin)
    assert.ok(adminRegs.some((r) => r.id === reg.id), 'missing from admin registrations')

    const mine = await ok('GET', '/tournaments/registrations/mine', undefined, tok(u))
    assert.ok(mine.some((r) => r.id === reg.id), 'missing from member profile')
    assert.equal(mine.find((r) => r.id === reg.id).tournamentTitle, t.title)
  })

  await test('T02', 'A PARTNER field takes a name, no account forced, nobody charged for it', async () => {
    const t = await makeTournament()
    const partner = await addPartnerField(t.id)
    const u = await member(100)
    const reg = await ok('POST', `/tournaments/${t.id}/register`, {
      answers: { [partner.id]: 'Guest Partner Name' }, paymentMethod: 'AT_CLUB',
    }, tok(u))
    assert.equal(reg.answers[partner.id], 'Guest Partner Name')
    assert.equal(await wallet(u), 100, 'naming a partner must not touch the wallet')
    assert.equal(reg.paymentStatus, 'PAY_AT_CLUB')
  })

  await test('T03', 'A second registration by the same member is refused', async () => {
    const t = await makeTournament()
    const u = await member(100)
    await ok('POST', `/tournaments/${t.id}/register`, { paymentMethod: 'AT_CLUB' }, tok(u))
    const r = await req('POST', `/tournaments/${t.id}/register`, { paymentMethod: 'AT_CLUB' }, tok(u))
    rejects(r, 'duplicate registration', /already_registered/)
    assert.equal(await wallet(u), 100)
  })

  await test('T04', 'Registration is refused when the tournament is not REGISTRATION_OPEN', async () => {
    const t = await ok('POST', '/admin/tournaments', { ...BASE_TOURNAMENT, title: `Draft ${stamp}-${++seq}`, startsAt: futureDate() }, admin)
    const u = await member(100)
    const r = await req('POST', `/tournaments/${t.id}/register`, { paymentMethod: 'AT_CLUB' }, tok(u))
    assert.equal(r.status, 404, 'a draft tournament should not even be visible enough to register')
  })

  await test('T04b', 'REGISTRATION_CLOSED is refused with a clear reason, not a 404', async () => {
    const t = await makeTournament()
    await ok('POST', `/admin/tournaments/${t.id}/status`, { status: 'REGISTRATION_CLOSED' }, admin)
    const u = await member(100)
    const r = await req('POST', `/tournaments/${t.id}/register`, { paymentMethod: 'AT_CLUB' }, tok(u))
    rejects(r, 'closed registration', /registration_closed/)
  })

  await test('T05', 'A passed registration deadline is refused', async () => {
    const past = new Date(Date.now() - 3600_000).toISOString()
    const t = await makeTournament({ registrationDeadline: past })
    const u = await member(100)
    const r = await req('POST', `/tournaments/${t.id}/register`, { paymentMethod: 'AT_CLUB' }, tok(u))
    rejects(r, 'passed deadline', /deadline_passed/)
  })

  await test('T06', 'A required field left blank is refused before anything is charged', async () => {
    const t = await makeTournament()
    const agreement = await addAgreementField(t.id)
    const u = await member(100)
    const r = await req('POST', `/tournaments/${t.id}/register`, { answers: {}, paymentMethod: 'WALLET' }, tok(u))
    rejects(r, 'missing required field', /field_required/)
    assert.equal(await wallet(u), 100, 'nothing must be charged when the form is incomplete')
    void agreement
  })

  await test('T07', 'A stale quoted price is refused', async () => {
    const t = await makeTournament({ entryFeeDt: 50 })
    const u = await member(100)
    const r = await req('POST', `/tournaments/${t.id}/register`, { paymentMethod: 'WALLET', quotedPriceDt: 40 }, tok(u))
    rejects(r, 'stale price', /price_mismatch/)
    assert.equal(await wallet(u), 100)
  })

  await test('T08', 'A payment method the tournament does not accept is refused', async () => {
    const t = await makeTournament({ paymentRule: 'AT_CLUB' })
    const u = await member(100)
    const r = await req('POST', `/tournaments/${t.id}/register`, { paymentMethod: 'WALLET' }, tok(u))
    rejects(r, 'wallet on an at-club-only tournament', /payment_method_not_allowed/)
    assert.equal(await wallet(u), 100)
  })

  await test('T09', 'A full tournament without autoWaitlist refuses new entries', async () => {
    const t = await makeTournament({ maxParticipants: 1, autoWaitlist: false })
    const u1 = await member(100)
    await ok('POST', `/tournaments/${t.id}/register`, { paymentMethod: 'AT_CLUB' }, tok(u1))
    const u2 = await member(100)
    const r = await req('POST', `/tournaments/${t.id}/register`, { paymentMethod: 'AT_CLUB' }, tok(u2))
    rejects(r, 'a full tournament', /takeoff\.tournament\.full/)
  })

  await test('T10', 'A full tournament with autoWaitlist waitlists instead, and never charges the wallet', async () => {
    const t = await makeTournament({ maxParticipants: 1, autoWaitlist: true, entryFeeDt: 30 })
    const u1 = await member(100)
    await ok('POST', `/tournaments/${t.id}/register`, { paymentMethod: 'AT_CLUB' }, tok(u1))

    const u2 = await member(100)
    const walletR = await req('POST', `/tournaments/${t.id}/register`, { paymentMethod: 'WALLET' }, tok(u2))
    rejects(walletR, 'wallet on a waitlisted spot', /wallet_on_waitlist_not_allowed/)

    const reg2 = await ok('POST', `/tournaments/${t.id}/register`, { paymentMethod: 'AT_CLUB' }, tok(u2))
    assert.equal(reg2.status, 'WAITLIST')
    assert.equal(await wallet(u2), 100)

    const list = await ok('GET', '/tournaments')
    const found = list.find((x) => x.id === t.id)
    // Waitlisted members must not count toward "currentRegistrations" the way a
    // confirmed entry does — a public list showing 2/1 would be dishonest.
    assert.equal(found.currentRegistrations, 1)
  })

  await test('T11', 'The last spot under concurrent registration is granted exactly once', async () => {
    const t = await makeTournament({ maxParticipants: 1, autoWaitlist: true, entryFeeDt: 20 })
    const u1 = await member(100)
    const u2 = await member(100)
    const [r1, r2] = await Promise.all([
      req('POST', `/tournaments/${t.id}/register`, { paymentMethod: 'AT_CLUB' }, tok(u1)),
      req('POST', `/tournaments/${t.id}/register`, { paymentMethod: 'AT_CLUB' }, tok(u2)),
    ])
    const statuses = [r1.data?.status, r2.data?.status].sort()
    assert.deepEqual(statuses, ['CONFIRMED', 'WAITLIST'], 'exactly one of the two must win the spot')
  })

  await test('T12', 'manualValidation gates CONFIRMED even when payment is settled', async () => {
    const t = await makeTournament({ manualValidation: true, entryFeeDt: 25 })
    const u = await member(100)
    const reg = await ok('POST', `/tournaments/${t.id}/register`, { paymentMethod: 'WALLET' }, tok(u))
    assert.equal(reg.status, 'PENDING', 'paid but not admin-approved must not read as confirmed')
    assert.equal(reg.paymentStatus, 'PAID')
    assert.equal(await wallet(u), 75)
  })

  await test('T13', 'An invitation-only tournament refuses self-service registration', async () => {
    const t = await makeTournament({ registrationMode: 'INVITATION_ONLY' })
    const u = await member(100)
    const r = await req('POST', `/tournaments/${t.id}/register`, { paymentMethod: 'AT_CLUB' }, tok(u))
    rejects(r, 'invitation-only', /invitation_only/)
  })

  await test('T14', 'A price tier from /pricing is honoured over the flat entry fee', async () => {
    const t = await makeTournament({ entryFeeDt: 40 })
    const tier = await ok('PUT', `/admin/tournaments/${t.id}/pricing`, [
      { label: 'Early bird', priceDt: 25, displayOrder: 0 },
    ], admin)
    const u = await member(100)
    const reg = await ok('POST', `/tournaments/${t.id}/register`, {
      pricingId: tier[0].id, paymentMethod: 'WALLET', quotedPriceDt: 25,
    }, tok(u))
    assert.equal(Number(reg.amountPaidDt), 25)
    assert.equal(await wallet(u), 75)
  })

  const pass = results.filter((r) => r === 'PASS').length
  console.log(JSON.stringify({ PASS: pass, FAIL: results.length - pass }))
  process.exitCode = pass === results.length ? 0 : 1
}

main().catch((e) => { console.error(e); process.exitCode = 2 })
