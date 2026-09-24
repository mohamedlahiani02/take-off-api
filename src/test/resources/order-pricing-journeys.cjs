/**
 * Order price authority, against the real Spring backend and a synthetic
 * database.
 *
 * Replaces the fixture shape that the older suite used, where a line carried
 * `productName` and a price but nothing the server could look up. That made the
 * browser the authority on what an order costs. These cases pin the rule that
 * replaced it: a line must reference something the club sells, and the
 * catalogue decides the amount.
 *
 *   node order-pricing-journeys.cjs <baseUrl> <adminEmail> <adminPassword>
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
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: 'Bearer ' + token } : {}),
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  })
  const raw = await res.text()
  let data
  try {
    data = JSON.parse(raw)
  } catch {
    data = raw
  }
  return { status: res.status, data }
}

async function ok(method, path, body, token) {
  const r = await req(method, path, body, token)
  if (r.status >= 400) throw new Error(`${method} ${path} -> ${r.status} ${JSON.stringify(r.data).slice(0, 250)}`)
  return r.data
}

const rejects = (r, why) =>
  assert.ok(r.status >= 400 && r.status < 500, `${why}: expected 4xx, got ${r.status}`)

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
  const phone = '+216' + String(80000000 + (Date.now() % 1000000) + ++seq).slice(0, 8)
  const u = await ok('POST', '/auth/register', {
    phone,
    email: `pricing-${stamp}-${seq}@example.test`,
    password: 'Pricing-Journey-Password!',
    name: 'Pricing Tester ' + seq,
  })
  if (balance) {
    await ok('POST', `/admin/users/${u.user.id}/wallet/credit`, { amountDt: balance, reason: 'journey' }, admin)
  }
  return u
}

const tok = (u) => u.tokens.accessToken
const wallet = async (u) => Number((await ok('GET', '/auth/me', undefined, tok(u))).walletDt)

const order = (items, paymentMethod = 'COD') => ({
  deliveryMethod: 'PICKUP',
  paymentMethod,
  contact: { name: 'Pricing Tester', phone: '+21622000000' },
  items,
})

async function main() {
  admin = (await ok('POST', '/admin/auth/login', { email: ADMIN_EMAIL, password: ADMIN_PASSWORD })).token

  // A real product to price against. The admin endpoint wraps its reply as
  // { product, variants }, so unwrap rather than assuming a bare entity.
  const created = await ok('POST', '/admin/products', {
    name: 'Pricing Fixture Racket ' + stamp,
    category: 'RACKETS',
    priceDt: 120,
    stock: 50,
    isActive: true,
  }, admin)
  const product = created.product ?? created

  const pack = await ok('POST', '/admin/packs/types', {
    name: 'Pricing Fixture Pack ' + stamp,
    activity: 'PILATES',
    priceDt: 40,
    creditCount: 4,
    validityMonths: 12,
  }, admin)

  await test('Q01', 'A line naming nothing the club sells is refused', async () => {
    const u = await member(500)
    const r = await req('POST', '/orders', order([
      { productName: 'Invented service', qty: 1, unitPriceDt: 10 },
    ]), tok(u))
    rejects(r, 'an unpriceable line')
    assert.match(JSON.stringify(r.data), /unpriced_item/)
  })

  await test('Q02', 'A product line is charged the catalogue price, not the sent one', async () => {
    const u = await member(500)
    // The browser claims 1 DT for a 120 DT racket.
    const o = await ok('POST', '/orders', order([
      { productId: product.id, productName: product.name, qty: 1, unitPriceDt: 1 },
    ], 'WALLET'), tok(u))
    // 120 catalogue + 1 DT timbre fiscal (physical goods over 10 DT).
    assert.equal(Number(o.totalDt), 121, 'the catalogue price decides')
    assert.equal(await wallet(u), 379, '121 DT leaves the wallet, not the 1 DT claimed')
  })

  await test('Q03', 'A pack line is priced from the pack catalogue', async () => {
    const u = await member(500)
    const o = await ok('POST', '/orders', order([
      { packTypeId: pack.id, productName: pack.name, qty: 1, unitPriceDt: 1 },
    ], 'WALLET'), tok(u))
    assert.equal(Number(o.totalDt), 40, 'the pack catalogue price decides')
    assert.equal(await wallet(u), 460)
  })

  await test('Q04', 'Quantity multiplies the catalogue price, not a sent total', async () => {
    const u = await member(500)
    const o = await ok('POST', '/orders', order([
      { productId: product.id, productName: product.name, qty: 3, unitPriceDt: 1 },
    ], 'WALLET'), tok(u))
    assert.equal(Number(o.totalDt), 361, '3 x 120 DT plus the 1 DT timbre')
    assert.equal(await wallet(u), 139)
  })

  await test('Q05', 'An unknown product reference is refused, nothing is charged', async () => {
    const u = await member(500)
    rejects(await req('POST', '/orders', order([
      { productId: '00000000-0000-4000-8000-000000000000', productName: 'Ghost', qty: 1, unitPriceDt: 10 },
    ], 'WALLET'), tok(u)), 'an unknown product')
    assert.equal(await wallet(u), 500)
  })

  await test('Q06', 'An inactive pack cannot be ordered', async () => {
    const u = await member(500)
    const dead = await ok('POST', '/admin/packs/types', {
      name: 'Retired Pack ' + stamp, activity: 'PILATES',
      priceDt: 40, creditCount: 4, validityMonths: 12, active: false,
    }, admin)
    rejects(await req('POST', '/orders', order([
      { packTypeId: dead.id, productName: dead.name, qty: 1, unitPriceDt: 40 },
    ], 'WALLET'), tok(u)), 'a retired pack')
    assert.equal(await wallet(u), 500)
  })

  const counts = ['PASS', 'FAIL'].map((s) => [s, results.filter((r) => r.status === s).length])
  console.log(JSON.stringify(Object.fromEntries(counts)))
  process.exitCode = results.some((r) => r.status !== 'PASS') ? 1 : 0
}

main().catch((e) => {
  console.error(e)
  process.exitCode = 2
})
