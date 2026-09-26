/**
 * Padel booking business rules, exercised against the real Spring backend and a
 * synthetic database. No fixtures, no stubs: every assertion below is about what
 * the API actually persisted.
 *
 *   node padel-booking-journeys.cjs <baseUrl> <adminEmail> <adminPassword>
 *
 * Nothing here sends SMS, charges a card or touches production data: users are
 * created per run with generated phone numbers, and wallets are credited through
 * the admin endpoint.
 */
const assert = require('node:assert/strict')
const crypto = require('node:crypto')

const BASE = (process.argv[2] || 'http://127.0.0.1:18081') + '/api/v1'
const ADMIN_EMAIL = process.argv[3] || 'audit-admin@example.test'
const ADMIN_PASSWORD = process.argv[4] || 'Audit-Only-Password-2026!'

const results = []
let admin
let stamp = Date.now().toString(36)
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
  if (r.status >= 400) {
    throw new Error(`${method} ${path} -> ${r.status} ${JSON.stringify(r.data).slice(0, 300)}`)
  }
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

/** A fresh member with an optional wallet balance. */
async function member(balance = 0) {
  const phone = '+216' + String(90000000 + (Date.now() % 1000000) + ++seq).slice(0, 8)
  const u = await ok('POST', '/auth/register', {
    phone,
    email: `padel-${stamp}-${seq}@example.test`,
    password: 'Padel-Journey-Password!',
    name: 'Padel Tester ' + seq,
  })
  if (balance) {
    await ok('POST', `/admin/users/${u.user.id}/wallet/credit`, { amountDt: balance, reason: 'journey' }, admin)
  }
  return { ...u, phone }
}

const tok = (u) => u.tokens.accessToken
const wallet = async (u) => Number((await ok('GET', '/auth/me', undefined, tok(u))).walletDt)

/**
 * A future slot start that is actually on the club's grid.
 *
 * Bookings run in 90-minute slots from 07:00 to 22:00 club time, so the only
 * valid starts are 07:00, 08:30, 10:00 ... 20:30. Picking a round hour like
 * 09:00 is rejected — correctly — by the server, so the grid is computed here
 * rather than guessed.
 */
const GRID_MINUTES = (() => {
  const out = []
  for (let m = 7 * 60; m + 90 <= 22 * 60; m += 90) out.push(m)
  return out
})()

/* Each run books days no previous run touched: the database persists between
   runs, so a fixed offset makes the second run collide with the first and
   report slot_taken as if the code had regressed. */
let dayOffset = 40 + Math.floor(Math.random() * 4000) * 3
function nextSlot(gridIndex = 2) {
  const minutes = GRID_MINUTES[gridIndex % GRID_MINUTES.length]
  const d = new Date()
  d.setUTCDate(d.getUTCDate() + dayOffset++)
  // Club time is UTC+1 all year, so subtract the hour to express it in UTC.
  d.setUTCHours(Math.floor(minutes / 60) - 1, minutes % 60, 0, 0)
  return d.toISOString()
}

async function main() {
  admin = (await ok('POST', '/admin/auth/login', { email: ADMIN_EMAIL, password: ADMIN_PASSWORD })).token

  const courts = await ok('GET', '/courts')
  const court = courts.find((c) => !c.activity || c.activity === 'PADEL')
  assert.ok(court, 'no padel court configured')
  const book = (u, body) => req('POST', `/courts/${court.id}/bookings`, body, tok(u))

  // ── pricing comes from the server ───────────────────────────────────────
  let pricing
  await test('B01', 'Pricing is served by the API, not assumed by the client', async () => {
    pricing = await ok('GET', '/courts/pricing')
    assert.equal(Number(pricing.fullPriceDt), 80, 'full court must be 80 DT')
    assert.equal(Number(pricing.seatPriceDt), 20, 'a seat must be 20 DT (80 / 4)')
    assert.equal(pricing.seatsPerCourt, 4)
    assert.equal(pricing.currency, 'TND')
  })

  // ── FULL: the organiser covers the whole court ─────────────────────────
  await test('B02', 'FULL charges 80 DT once, not four seat payments', async () => {
    const u = await member(200)
    const r = await book(u, { startsAt: nextSlot(), mode: 'FULL', paymentMethod: 'WALLET' })
    assert.equal(r.status, 201, JSON.stringify(r.data))
    assert.equal(Number(r.data.priceDt), 80)
    assert.equal(await wallet(u), 120, 'exactly 80 DT should leave the wallet')
    const seats = await ok('GET', `/courts/bookings/${r.data.bookingId}/participants`, undefined, tok(u))
    assert.equal(seats.length, 1, 'a solo FULL booking seats only the organiser')
  })

  // ── SHARE: the organiser pays only their seat ──────────────────────────
  await test('B03', 'SHARE charges 20 DT and leaves three seats open', async () => {
    const u = await member(200)
    const r = await book(u, { startsAt: nextSlot(), mode: 'SHARE', paymentMethod: 'WALLET' })
    assert.equal(r.status, 201, JSON.stringify(r.data))
    assert.equal(Number(r.data.priceDt), 20)
    assert.equal(await wallet(u), 180, 'only the seat should be charged')
    const seats = await ok('GET', `/courts/bookings/${r.data.bookingId}/participants`, undefined, tok(u))
    assert.equal(seats.length, 1, 'three seats must remain free')
  })

  // ── booking alone is valid ─────────────────────────────────────────────
  await test('B04', 'A member can book without naming anyone', async () => {
    const u = await member(100)
    const r = await book(u, { startsAt: nextSlot(), mode: 'SHARE', paymentMethod: 'PAY_AT_CLUB' })
    assert.equal(r.status, 201)
    assert.equal(await wallet(u), 100, 'pay-at-club must not debit the wallet')
  })

  // ── naming a partner is not a payment ──────────────────────────────────
  await test('B05', 'Naming a partner charges nobody and marks nobody paid', async () => {
    const organiser = await member(200)
    const friend = await member(100)
    const r = await ok('POST', `/courts/${court.id}/bookings`, {
      startsAt: nextSlot(), mode: 'SHARE', paymentMethod: 'WALLET',
    }, tok(organiser))

    const added = await ok('POST', `/courts/bookings/${r.bookingId}/participants`,
      { phone: friend.phone }, tok(organiser))
    assert.equal(added.paymentStatus, 'PENDING', 'a named partner owes their seat')
    assert.equal(await wallet(friend), 100, "the partner's wallet must be untouched")
    assert.equal(await wallet(organiser), 180, 'the organiser still paid only their own seat')
  })

  // ── FULL partners owe nothing ──────────────────────────────────────────
  await test('B06', 'On a FULL court, named partners owe nothing', async () => {
    const organiser = await member(200)
    const r = await ok('POST', `/courts/${court.id}/bookings`, {
      startsAt: nextSlot(), mode: 'FULL', paymentMethod: 'WALLET',
      participants: [{ guestName: 'Invite Un' }, { guestName: 'Invite Deux' }],
    }, tok(organiser))
    assert.equal(await wallet(organiser), 120, 'still a single 80 DT charge')
    const seats = await ok('GET', `/courts/bookings/${r.bookingId}/participants`, undefined, tok(organiser))
    assert.equal(seats.length, 3)
    const guests = seats.filter((s) => s.isGuest)
    assert.equal(guests.length, 2)
    assert.ok(guests.every((g) => g.paymentStatus === 'COVERED'), 'the court is already paid for')
    assert.ok(guests.every((g) => Number(g.shareDt) === 0), 'no invented per-head charge')
  })

  // ── guests never become accounts ───────────────────────────────────────
  await test('B07', 'A guest is seated without creating an account', async () => {
    const organiser = await member(100)
    const r = await ok('POST', `/courts/${court.id}/bookings`, {
      startsAt: nextSlot(), mode: 'SHARE', paymentMethod: 'PAY_AT_CLUB',
    }, tok(organiser))
    const guest = await ok('POST', `/courts/bookings/${r.bookingId}/participants`,
      { guestName: 'Visiteur Sans Compte' }, tok(organiser))
    assert.equal(guest.isGuest, true)
    assert.equal(guest.userId, null, 'no user row may be manufactured for a guest')
  })

  // ── four seats maximum, no duplicates ──────────────────────────────────
  await test('B08', 'A match holds at most four players', async () => {
    const organiser = await member(100)
    const r = await ok('POST', `/courts/${court.id}/bookings`, {
      startsAt: nextSlot(), mode: 'SHARE', paymentMethod: 'PAY_AT_CLUB',
    }, tok(organiser))
    for (const name of ['A', 'B', 'C']) {
      await ok('POST', `/courts/bookings/${r.bookingId}/participants`, { guestName: 'Joueur ' + name }, tok(organiser))
    }
    rejects(
      await req('POST', `/courts/bookings/${r.bookingId}/participants`, { guestName: 'Joueur D' }, tok(organiser)),
      'a fifth player',
    )
  })

  await test('B09', 'The same person cannot take two seats', async () => {
    const organiser = await member(100)
    const friend = await member()
    const r = await ok('POST', `/courts/${court.id}/bookings`, {
      startsAt: nextSlot(), mode: 'SHARE', paymentMethod: 'PAY_AT_CLUB',
    }, tok(organiser))
    await ok('POST', `/courts/bookings/${r.bookingId}/participants`, { phone: friend.phone }, tok(organiser))
    rejects(
      await req('POST', `/courts/bookings/${r.bookingId}/participants`, { phone: friend.phone }, tok(organiser)),
      'duplicate member',
    )
    await ok('POST', `/courts/bookings/${r.bookingId}/participants`, { guestName: 'Repete' }, tok(organiser))
    rejects(
      await req('POST', `/courts/bookings/${r.bookingId}/participants`, { guestName: 'repete' }, tok(organiser)),
      'duplicate guest name',
    )
  })

  await test('B10', 'Only the organiser may seat players', async () => {
    const organiser = await member(100)
    const stranger = await member()
    const r = await ok('POST', `/courts/${court.id}/bookings`, {
      startsAt: nextSlot(), mode: 'SHARE', paymentMethod: 'PAY_AT_CLUB',
    }, tok(organiser))
    rejects(
      await req('POST', `/courts/bookings/${r.bookingId}/participants`, { guestName: 'Intrus' }, tok(stranger)),
      'a stranger seating players',
    )
  })

  // ── joining an existing SHARE ──────────────────────────────────────────
  await test('B11', 'Joining a shared match does not create a second booking', async () => {
    const a = await member(200)
    const b = await member(200)
    const slot = nextSlot()
    const first = await ok('POST', `/courts/${court.id}/bookings`, {
      startsAt: slot, mode: 'SHARE', paymentMethod: 'WALLET',
    }, tok(a))
    const second = await ok('POST', `/courts/${court.id}/bookings`, {
      startsAt: slot, mode: 'SHARE', paymentMethod: 'WALLET',
    }, tok(b))
    assert.equal(second.bookingId, first.bookingId, 'both players belong to one match')
    assert.equal(await wallet(b), 180, 'the joiner pays one seat')
    const seats = await ok('GET', `/courts/bookings/${first.bookingId}/participants`, undefined, tok(a))
    assert.equal(seats.length, 2)
  })

  // ── a stale price cannot be charged ────────────────────────────────────
  await test('B12', 'A stale quoted price is refused', async () => {
    const u = await member(200)
    rejects(
      await book(u, { startsAt: nextSlot(), mode: 'FULL', paymentMethod: 'WALLET', quotedPriceDt: 10 }),
      'a client-chosen price',
    )
    assert.equal(await wallet(u), 200, 'a refused booking charges nothing')
  })

  // ── history ────────────────────────────────────────────────────────────
  await test('B13', 'Each participant finds the match in their own history', async () => {
    const organiser = await member(200)
    const friend = await member(200)
    const slot = nextSlot()
    const r = await ok('POST', `/courts/${court.id}/bookings`, {
      startsAt: slot, mode: 'SHARE', paymentMethod: 'WALLET',
    }, tok(organiser))
    await ok('POST', `/courts/bookings/${r.bookingId}/participants`, { phone: friend.phone }, tok(organiser))

    const mine = await ok('GET', '/courts/bookings/mine', undefined, tok(organiser))
    const theirs = await ok('GET', '/courts/bookings/mine', undefined, tok(friend))
    assert.ok(mine.some((m) => m.bookingId === r.bookingId && m.isOrganizer === true))
    assert.ok(
      theirs.some((m) => m.bookingId === r.bookingId && m.isOrganizer === false),
      'a seated member sees the match too',
    )
  })

  // ── cancellation refunds once ──────────────────────────────────────────
  await test('B14', 'Cancelling refunds the wallet exactly once', async () => {
    const u = await member(200)
    const r = await ok('POST', `/courts/${court.id}/bookings`, {
      startsAt: nextSlot(), mode: 'FULL', paymentMethod: 'WALLET',
    }, tok(u))
    assert.equal(await wallet(u), 120)
    await ok('DELETE', `/courts/bookings/${r.bookingId}`, undefined, tok(u))
    assert.equal(await wallet(u), 200, 'the 80 DT comes back')
    rejects(await req('DELETE', `/courts/bookings/${r.bookingId}`, undefined, tok(u)), 'a second cancellation')
    assert.equal(await wallet(u), 200, 'and never pays out twice')
  })

  // ── double click ───────────────────────────────────────────────────────
  await test('B15', 'A double click cannot book the same slot twice', async () => {
    const u = await member(300)
    const slot = nextSlot()
    const [a, b] = await Promise.all([
      book(u, { startsAt: slot, mode: 'FULL', paymentMethod: 'WALLET' }),
      book(u, { startsAt: slot, mode: 'FULL', paymentMethod: 'WALLET' }),
    ])
    const created = [a, b].filter((r) => r.status === 201)
    assert.equal(created.length, 1, `exactly one booking, got ${created.length}`)
    assert.equal(await wallet(u), 220, 'charged once')
  })

  // ── concurrency on the last seat ───────────────────────────────────────
  await test('B16', 'Concurrent joins cannot oversell the last seat', async () => {
    const host = await member(200)
    const slot = nextSlot()
    const r = await ok('POST', `/courts/${court.id}/bookings`, {
      startsAt: slot, mode: 'SHARE', paymentMethod: 'WALLET',
    }, tok(host))
    // Fill to three, leaving exactly one seat.
    await ok('POST', `/courts/bookings/${r.bookingId}/participants`, { guestName: 'Siege Deux' }, tok(host))
    await ok('POST', `/courts/bookings/${r.bookingId}/participants`, { guestName: 'Siege Trois' }, tok(host))

    const rivals = await Promise.all([member(200), member(200), member(200)])
    await Promise.all(
      rivals.map((u) =>
        req('POST', `/courts/${court.id}/bookings`, { startsAt: slot, mode: 'SHARE', paymentMethod: 'WALLET' }, tok(u)),
      ),
    )
    const seats = await ok('GET', `/courts/bookings/${r.bookingId}/participants`, undefined, tok(host))
    assert.equal(seats.length, 4, `match holds 4 seats, found ${seats.length}`)
  })

  // ── admin completes the roster ─────────────────────────────────────────
  await test('B17', 'An admin can complete the roster without charging anyone', async () => {
    const organiser = await member(200)
    const r = await ok('POST', `/courts/${court.id}/bookings`, {
      startsAt: nextSlot(), mode: 'SHARE', paymentMethod: 'WALLET',
    }, tok(organiser))
    const friend = await member(150)
    await ok('POST', `/admin/courts/bookings/${r.bookingId}/participants`, { userId: friend.user.id }, admin)
    assert.equal(await wallet(friend), 150, 'an admin adding a player charges nobody')
    const seats = await ok('GET', `/courts/bookings/${r.bookingId}/participants`, undefined, tok(organiser))
    assert.equal(seats.length, 2)
  })

  const counts = ['PASS', 'FAIL'].map((s) => [s, results.filter((r) => r.status === s).length])
  console.log(JSON.stringify(Object.fromEntries(counts)))
  process.exitCode = results.some((r) => r.status !== 'PASS') ? 1 : 0
}

main().catch((e) => {
  console.error(e)
  process.exitCode = 2
})
