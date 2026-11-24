// QNow test bots: bot shops + bot customers in the LIVE Firebase project.
//
//   node bots.mjs seed     create 8 bot shops around a centre point (default: Silchar)
//   node bots.mjs run      keep bot customers joining and bot shops serving (Ctrl+C to stop)
//   node bots.mjs remove   delete every bot shop and everything under it
//
// Authentication: reuses the Firebase CLI sign-in on this PC (`firebase login`), so no
// service-account key file is needed. Requests made this way skip security rules, which is
// how bots can own shops. Every bot document carries `bot: true` so `remove` finds them all.
//
// Bots never get personal data: names are made-up first names, no phones or emails.

import { initializeApp, applicationDefault } from 'firebase-admin/app';
import { getFirestore, FieldValue, Timestamp } from 'firebase-admin/firestore';
import { readFileSync, writeFileSync, unlinkSync } from 'node:fs';
import { homedir, tmpdir } from 'node:os';
import { join } from 'node:path';

const PROJECT = process.env.QNOW_PROJECT ?? 'qnow-9e985';
const CENTER = { lat: Number(process.env.BOT_LAT ?? 24.8170), lng: Number(process.env.BOT_LNG ?? 92.7980) };
const OFFSET = 330; // India, minutes from UTC

// The Firebase CLI's public OAuth client (same values as in firebase-tools) + the saved sign-in.
const store = JSON.parse(readFileSync(join(homedir(), '.config', 'configstore', 'firebase-tools.json'), 'utf8'));
if (!store.tokens?.refresh_token) throw new Error('Not signed in. Run: firebase login');
// Firestore needs "application default credentials", so write a short-lived credential file to
// the temp folder for this run and delete it on exit. It never goes near the repo.
const credFile = join(tmpdir(), `qnow-bots-${process.pid}.json`);
writeFileSync(credFile, JSON.stringify({
  type: 'authorized_user',
  client_id: '563584335869-fgrhgmd47bqnekij5i8b5pr03ho849e6.apps.googleusercontent.com',
  client_secret: 'j9iVZfS8kkCEFUPaAeJV0sAi',
  refresh_token: store.tokens.refresh_token,
}), { mode: 0o600 });
const cleanup = () => { try { unlinkSync(credFile); } catch {} };
process.on('exit', cleanup);
process.on('SIGINT', () => { cleanup(); process.exit(0); });
process.env.GOOGLE_APPLICATION_CREDENTIALS = credFile;
initializeApp({ projectId: PROJECT, credential: applicationDefault() });
const db = getFirestore();

const BARBER = [['Haircut', 120, 20], ['Beard trim', 60, 10], ['Haircut + beard', 160, 30], ['Kids haircut', 90, 15]];
const SALON = [['Haircut & style', 300, 30], ['Hair spa', 700, 45], ['Hair colour', 900, 60], ['Blow-dry', 250, 20]];
const PARLOUR = [['Facial', 500, 40], ['Threading', 50, 10], ['Waxing', 300, 30], ['Manicure', 350, 30]];
const SHOPS = [
  ['[Bot] Fade Factory', 'BARBER', 'College Road', 2, 'OPEN', BARBER],
  ['[Bot] Urban Mane Salon', 'SALON', 'Park Road', 3, 'OPEN', SALON],
  ['[Bot] Glow Beauty Parlour', 'PARLOUR', 'Club Road', 2, 'OPEN', PARLOUR],
  ['[Bot] Kings Cut Barbershop', 'BARBER', 'Station Road', 1, 'OPEN', BARBER],
  ['[Bot] Silk Touch Salon', 'SALON', 'Hospital Road', 2, 'ON_BREAK', SALON],
  ["[Bot] The Gentlemen's Chair", 'BARBER', 'Link Road', 3, 'OPEN', BARBER],
  ['[Bot] Bella Beauty Studio', 'PARLOUR', 'Central Road', 1, 'OPEN', PARLOUR],
  ['[Bot] Sharp & Co. Barbers', 'BARBER', 'Market Lane', 2, 'CLOSED', BARBER],
];
const NAMES = ['Aarav', 'Riya', 'Kabir', 'Ananya', 'Vihaan', 'Meera', 'Arjun', 'Isha', 'Rohan', 'Sara', 'Dev', 'Nisha'];
const REVIEWS = [
  [5, 'Super quick, walked in right when my number came up.'], [4, 'Good service. Wait estimate was spot on.'],
  [5, 'Great cut and no waiting around.'], [3, 'Decent, a bit busy on weekends.'], [4, 'Friendly staff, clean place.'],
];
const pick = (a) => a[Math.floor(Math.random() * a.length)];
const dayKey = () => Math.floor((Date.now() + OFFSET * 60000) / 86400000);

// Geohash (same scheme as GeoFire) so bot shops show up in "nearby" queries.
function geohash(lat, lng, precision = 10) {
  const base32 = '0123456789bcdefghjkmnpqrstuvwxyz';
  let latR = [-90, 90], lngR = [-180, 180], hash = '', bit = 0, ch = 0, even = true;
  while (hash.length < precision) {
    const r = even ? lngR : latR, v = even ? lng : lat, mid = (r[0] + r[1]) / 2;
    if (v >= mid) { ch = (ch << 1) | 1; r[0] = mid; } else { ch <<= 1; r[1] = mid; }
    even = !even;
    if (++bit === 5) { hash += base32[ch]; bit = 0; ch = 0; }
  }
  return hash;
}

async function seed() {
  for (const [i, [name, category, street, chairs, status, services]] of SHOPS.entries()) {
    const id = `bot-shop-${i}`;
    const angle = (i / SHOPS.length) * 2 * Math.PI, km = 0.5 + Math.random() * 2.5;
    const lat = CENTER.lat + (km / 111) * Math.sin(angle);
    const lng = CENTER.lng + (km / (111 * Math.cos((CENTER.lat * Math.PI) / 180))) * Math.cos(angle);
    const count = 10 + Math.floor(Math.random() * 120);
    await db.doc(`businesses/${id}`).set({
      bot: true, ownerId: `bot-owner-${i}`, name, category, address: `${street}, Silchar`,
      lat, lng, geohash: geohash(lat, lng), photoIds: [], thumbnail: null,
      services: services.map(([n, p, m], j) => ({ id: `${id}-s${j}`, name: n, price: p, durationMinutes: m })),
      hours: [1, 2, 3, 4, 5, 6, 7].map((d) => ({ day: d, open: d !== 7, opensAt: 540, closesAt: 1260 })),
      chairs, activeChairs: chairs, status, publicPhone: null,
      ratingSum: Math.round(count * (3.8 + Math.random())), ratingCount: count,
      utcOffsetMinutes: OFFSET, graceMinutes: 3, currency: 'INR',
      queue: { dayKey: 0, lastNumber: 0 }, createdAt: FieldValue.serverTimestamp(), updatedAt: FieldValue.serverTimestamp(),
    });
    for (const [j, [rating, comment]] of REVIEWS.entries()) {
      await db.doc(`businesses/${id}/reviews/bot-reviewer-${j}`).set({ bot: true, rating, comment, tokenId: 'bot', createdAt: Timestamp.fromMillis(Date.now() - (j + 1) * 86400000) });
    }
    console.log(`created ${name}`);
  }
}

async function addBotCustomer(bizId) {
  await db.runTransaction(async (tx) => {
    const ref = db.doc(`businesses/${bizId}`);
    const biz = (await tx.get(ref)).data();
    const today = dayKey();
    const number = biz.queue?.dayKey === today ? biz.queue.lastNumber + 1 : 1;
    const id = `${today}-${number}`, s = pick(biz.services);
    tx.update(ref, { queue: { dayKey: today, lastNumber: number } });
    tx.set(db.doc(`businesses/${bizId}/tokens/${id}`), {
      dayKey: today, number, status: 'WAITING', serviceId: s.id, serviceName: s.name,
      durationMinutes: s.durationMinutes, source: Math.random() < 0.25 ? 'WALK_IN' : 'APP', createdAt: FieldValue.serverTimestamp(),
    });
    tx.set(db.doc(`businesses/${bizId}/holders/${id}`), { customerId: null, label: `${pick(NAMES)} (bot)`, dayKey: today });
  });
}

// Bot shops finish customers a bit faster than estimated so the queue visibly moves,
// then call the next person, including real customers who booked a bot shop.
async function serve(bizId, biz) {
  const live = await db.collection(`businesses/${bizId}/tokens`).where('dayKey', '==', dayKey())
    .where('status', 'in', ['WAITING', 'SERVING', 'GRACE']).orderBy('number').get();
  const now = Date.now();
  let inChair = 0;
  for (const d of live.docs) {
    const t = d.data();
    if (t.status !== 'SERVING') continue;
    const started = t.calledAt?.toMillis() ?? now;
    if (now - started > (45 + Math.random() * 60) * 1000) await d.ref.update({ status: 'DONE', completedAt: FieldValue.serverTimestamp() });
    else inChair++;
  }
  if (biz.status === 'CLOSED') return;
  let free = biz.activeChairs - inChair;
  for (const d of live.docs) {
    if (free <= 0) break;
    if (d.data().status === 'WAITING') { await d.ref.update({ status: 'SERVING', calledAt: FieldValue.serverTimestamp(), graceEndsAt: null }); free--; }
  }
}

async function run() {
  console.log('Bots running. Press Ctrl+C to stop.');
  for (;;) {
    const shops = await db.collection('businesses').where('bot', '==', true).get();
    for (const d of shops.docs) {
      const biz = d.data();
      try {
        const waiting = (await db.collection(`businesses/${d.id}/tokens`).where('dayKey', '==', dayKey()).where('status', '==', 'WAITING').get()).size;
        if (biz.status === 'OPEN' && waiting < 6 && Math.random() < 0.35) await addBotCustomer(d.id);
        await serve(d.id, biz);
      } catch (e) { console.log(`${biz.name}: ${e.message}`); }
    }
    console.log(new Date().toLocaleTimeString(), `updated ${shops.size} bot shops`);
    await new Promise((r) => setTimeout(r, 15000));
  }
}

async function remove() {
  const shops = await db.collection('businesses').where('bot', '==', true).get();
  for (const d of shops.docs) {
    await db.recursiveDelete(d.ref);
    console.log(`removed ${d.data().name}`);
  }
  console.log(`Done: ${shops.size} bot shops removed.`);
}

const cmd = process.argv[2];
if (cmd === 'seed') await seed();
else if (cmd === 'run') await run();
else if (cmd === 'remove') await remove();
else console.log('Usage: node bots.mjs seed | run | remove');
process.exit(0);
