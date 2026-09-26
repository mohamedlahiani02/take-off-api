/**
 * What the club edits in the admin console must reach the public site.
 *
 * Every migrated page reads the backend rather than a static prototype file, so
 * these cases assert the round trip for each one: an admin writes, the public
 * endpoint the page uses serves it, and a retired item stops being offered.
 *
 *   node admin-propagation-journeys.cjs <baseUrl> <adminEmail> <adminPassword>
 */
const assert = require('node:assert/strict')

const BASE = (process.argv[2] || 'http://127.0.0.1:18081') + '/api/v1'
const ADMIN_EMAIL = process.argv[3] || 'audit-admin@example.test'
const ADMIN_PASSWORD = process.argv[4] || 'Audit-Only-Password-2026!'

const results = []
const stamp = Date.now().toString(36)
let admin

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
  if (r.status >= 400) throw new Error(`${method} ${path} -> ${r.status} ${JSON.stringify(r.data).slice(0, 220)}`)
  return r.data
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

/** Admin creates reply in a few shapes; the id is what matters. */
const idOf = (r) => r.id ?? r.tournament?.id ?? r.product?.id ?? r.coach?.id ?? r.session?.id ?? r.court?.id

async function main() {
  admin = (await ok('POST', '/admin/auth/login', { email: ADMIN_EMAIL, password: ADMIN_PASSWORD })).token

  await test('P01', 'padel/tournaments: a published tournament reaches the public list', async () => {
    const title = `Coupe Propagation ${stamp}`
    const created = await ok('POST', '/admin/tournaments', {
      title, description: 'Tournoi de propagation.', format: 'AMERICANO', category: 'MIXED',
      startsAt: '2027-03-14T09:00:00Z', entryFeeDt: 60, prize: '1000 DT',
      maxParticipants: 16, registrationMode: 'OPEN', paymentRule: 'BOTH', status: 'PUBLISHED',
    }, admin)
    const id = idOf(created)
    assert.ok(id, 'no id returned: ' + JSON.stringify(created).slice(0, 150))

    // The body's status is discarded: create() never passes it, so a new
    // tournament is always a draft. Publishing is a separate, deliberate act.
    assert.equal(created.status, 'DRAFT',
      'create() appears to honour a body status now — the publish step below may be redundant')
    assert.ok(!(await ok('GET', '/tournaments')).some((t) => t.id === id),
      'an unpublished tournament must not be public')

    await ok('POST', `/admin/tournaments/${id}/status`, { status: 'PUBLISHED' }, admin)

    const list = await ok('GET', '/tournaments')
    const found = list.find((t) => t.id === id)
    assert.ok(found, 'the published tournament is absent from the public list')

    // Every field the page renders must actually arrive.
    for (const f of ['title', 'format', 'prize', 'startsAt', 'maxParticipants', 'status', 'currentRegistrations']) {
      assert.ok(found[f] !== undefined, `the public DTO omits ${f}, which the page displays`)
    }
    assert.equal(found.title, title)

    // And the detail page the list links to resolves.
    const detail = await ok('GET', `/tournaments/${id}`)
    assert.equal(detail.title, title)
  })

  await test('P02', 'A draft tournament stays off the public site', async () => {
    const created = await ok('POST', '/admin/tournaments', {
      title: `Brouillon ${stamp}`, format: 'AMERICANO', category: 'MIXED',
      startsAt: '2027-04-01T09:00:00Z', entryFeeDt: 0,
      registrationMode: 'OPEN', paymentRule: 'BOTH', status: 'DRAFT',
    }, admin)
    const id = idOf(created)
    const list = await ok('GET', '/tournaments')
    assert.ok(!list.some((t) => t.id === id), 'a draft must not be public')
    const r = await req('GET', `/tournaments/${id}`)
    assert.equal(r.status, 404, 'a draft detail page must 404, not render')
  })

  await test('P03', 'Editing a tournament in the console changes the public page', async () => {
    const created = await ok('POST', '/admin/tournaments', {
      title: `Avant ${stamp}`, format: 'AMERICANO', category: 'MIXED',
      startsAt: '2027-05-01T09:00:00Z', entryFeeDt: 0, prize: '500 DT',
      registrationMode: 'OPEN', paymentRule: 'BOTH', status: 'PUBLISHED',
    }, admin)
    const id = idOf(created)
    await ok('PUT', `/admin/tournaments/${id}`, {
      title: `Apres ${stamp}`, format: 'AMERICANO', category: 'MIXED',
      startsAt: '2027-05-01T09:00:00Z', entryFeeDt: 0, prize: '2000 DT',
      registrationMode: 'OPEN', paymentRule: 'BOTH', status: 'PUBLISHED',
    }, admin)
    await ok('POST', `/admin/tournaments/${id}/status`, { status: 'PUBLISHED' }, admin)
    const detail = await ok('GET', `/tournaments/${id}`)
    assert.equal(detail.title, `Apres ${stamp}`, 'the edit did not propagate')
    assert.equal(String(detail.prize), '2000 DT')
  })

  await test('P04', 'A finished tournament stays reachable, marked finished', async () => {
    const body = {
      title: `Termine ${stamp}`, format: 'AMERICANO', category: 'MIXED',
      startsAt: '2027-06-01T09:00:00Z', entryFeeDt: 0,
      registrationMode: 'OPEN', paymentRule: 'BOTH', status: 'PUBLISHED',
    }
    const created = await ok('POST', '/admin/tournaments', body, admin)
    const id = idOf(created)
    // TournamentStatus has no cancelled state; FINISHED is the terminal one, and
    // only the status endpoint applies it.
    await ok('POST', `/admin/tournaments/${id}/status`, { status: 'FINISHED' }, admin)
    const detail = await ok('GET', `/tournaments/${id}`)
    // A member must still be able to find the tournament they played.
    assert.equal(detail.status, 'FINISHED')
  })

  await test('P05', 'pilates/classes: a scheduled session reaches the public schedule', async () => {
    const types = await ok('GET', '/admin/classes/types', undefined, admin)
    const type = (Array.isArray(types) ? types : types.content ?? [])[0]
    assert.ok(type, 'no class type configured')
    const coaches = await ok('GET', '/coaches')
    const starts = new Date(Date.now() + 5 * 86400000)
    starts.setUTCHours(8, 0, 0, 0)

    const created = await ok('POST', '/admin/classes/sessions', {
      classTypeId: type.id,
      instructorId: coaches[0]?.id ?? null,
      startsAt: starts.toISOString(),
      durationMin: 55,
      maxSpots: 10,
      priceDt: 35,
    }, admin)
    const id = idOf(created)

    const from = new Date(starts.getTime() - 86400000).toISOString()
    const to = new Date(starts.getTime() + 86400000).toISOString()
    const sched = await ok('GET', `/classes/schedule?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`)
    const rows = Array.isArray(sched) ? sched : sched.sessions ?? []
    const found = rows.find((s) => (s.sessionId ?? s.id) === id)
    assert.ok(found, 'the session is absent from the public schedule')
    // The fields the migrated calendar depends on.
    for (const f of ['maxSpots', 'bookedSpots', 'status', 'waitlistCount']) {
      assert.ok(found[f] !== undefined, `the public session DTO omits ${f}`)
    }
    assert.notEqual(found.status, 'CANCELLED')
  })

  await test('P06', 'A cancelled session is never offered as bookable', async () => {
    const types = await ok('GET', '/admin/classes/types', undefined, admin)
    const type = (Array.isArray(types) ? types : types.content ?? [])[0]
    const starts = new Date(Date.now() + 6 * 86400000)
    starts.setUTCHours(9, 30, 0, 0)
    const created = await ok('POST', '/admin/classes/sessions', {
      classTypeId: type.id, startsAt: starts.toISOString(),
      durationMin: 55, maxSpots: 8, priceDt: 35,
    }, admin)
    const id = idOf(created)
    await ok('POST', `/admin/classes/sessions/${id}/cancel`, { reason: 'Propagation check' }, admin)

    const from = new Date(starts.getTime() - 86400000).toISOString()
    const to = new Date(starts.getTime() + 86400000).toISOString()
    const sched = await ok('GET', `/classes/schedule?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`)
    const rows = Array.isArray(sched) ? sched : sched.sessions ?? []
    const found = rows.find((s) => (s.sessionId ?? s.id) === id)
    // Either withheld, or served with a status the page can refuse. Silently
    // bookable is the one outcome that is wrong.
    if (found) assert.equal(found.status, 'CANCELLED', 'a cancelled session must say so')
  })

  await test('P07', 'store: a product reaches the catalogue, a retired one stops being active', async () => {
    const created = await ok('POST', '/admin/products', {
      name: `Raquette Propagation ${stamp}`, category: 'RACKETS', priceDt: 299.9,
      description: 'Produit de propagation.', stock: 5, isActive: true,
    }, admin)
    const id = idOf(created)

    const page = await ok('GET', '/products?page=0&size=100')
    const rows = page.content ?? page
    const found = rows.find((p) => p.id === id)
    assert.ok(found, 'the new product is absent from the public catalogue')
    assert.equal(Number(found.priceDt), 299.9, 'the catalogue price must be the one the club set')

    await ok('PUT', `/admin/products/${id}`, {
      name: `Raquette Propagation ${stamp}`, category: 'RACKETS', priceDt: 299.9,
      stock: 5, isActive: false,
    }, admin)
    const after = await ok('GET', '/products?page=0&size=100')
    const rows2 = after.content ?? after
    const still = rows2.find((p) => p.id === id)
    assert.ok(!still || still.isActive === false,
      'a retired product must not be offered for sale as active')
  })

  await test('P08', 'coaches: a new coach reaches the public page', async () => {
    const created = await ok('POST', '/admin/coaches', {
      firstName: 'Propa', lastName: `Gation ${stamp}`, roleTitle: 'Coach',
      bio: 'Coach de propagation.', activity: 'PADEL',
    }, admin)
    const id = idOf(created)
    const list = await ok('GET', '/coaches')
    assert.ok(list.some((c) => c.id === id), 'the new coach is absent from the public page')
  })

  await test('P09', 'CMS copy edited in the console reaches the page that reads it', async () => {
    const heading = `CMS Propagation ${stamp}`
    await ok('POST', '/admin/content/section', {
      page: 'padel', sectionKey: 'tournaments', visible: true, displayOrder: 0,
      content: { kicker: '04 — COMPETE', heading },
    }, admin)
    const sections = await ok('GET', '/content/padel')
    const rows = Array.isArray(sections) ? sections : sections.sections ?? []
    const sec = rows.find((s) => s.sectionKey === 'tournaments')
    assert.ok(sec, 'the edited section is absent from the public content')
    assert.equal(sec.content?.heading, heading, 'the CMS edit did not propagate')
  })

  await test('P10', 'padel/reserve: courts and the club price reach the calendar', async () => {
    // The console has no create-court endpoint (only calendar, bookings and
    // blocks), so courts are seeded or inserted directly. What the calendar
    // depends on is that the public list and the price agree with the backend.
    const courts = await ok('GET', '/courts')
    assert.ok(courts.length > 0, 'no court served to the public calendar')
    for (const f of ['id', 'name']) {
      assert.ok(courts[0][f] !== undefined, `the public court DTO omits ${f}`)
    }

    // The price the calendar quotes comes from configuration, not the page.
    const pricing = await ok('GET', '/courts/pricing')
    assert.ok(Number(pricing.fullPriceDt) > 0, 'no full price served')
    assert.ok(Number(pricing.seatPriceDt) > 0, 'no seat price served')
    assert.equal(
      Number(pricing.seatPriceDt) * Number(pricing.seatsPerCourt ?? 4),
      Number(pricing.fullPriceDt),
      'a seat times the seat count must equal a whole court',
    )
  })

  const pass = results.filter((r) => r === 'PASS').length
  console.log(JSON.stringify({ PASS: pass, FAIL: results.length - pass }))
  process.exitCode = pass === results.length ? 0 : 1
}

main().catch((e) => { console.error(e); process.exitCode = 2 })
