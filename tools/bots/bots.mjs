// QNow test bots: bot shops + bot customers in the LIVE Firebase project.
//
//   node bots.mjs seed     create the bot shops around a centre point (default: Silchar); skips ones that exist
//   node bots.mjs run      keep bot customers joining (bot shops AND real shops) and bot shops serving (Ctrl+C to stop)
//   node bots.mjs remove   delete every bot shop, plus bot customers left in real shops
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

const BARBER = [['Haircut', 120, 20], ['Beard trim', 60, 10], ['Clean shave', 50, 10], ['Kids haircut', 90, 15], ['Head massage', 100, 15]];
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
  ['[Bot] Blade Runner Barbers', 'BARBER', 'Ambicapatty', 2, 'OPEN', BARBER],
  ['[Bot] Mirror Mirror Salon', 'SALON', 'Tarapur', 3, 'OPEN', SALON],
  ['[Bot] Rose Petal Parlour', 'PARLOUR', 'Rangirkhari', 2, 'OPEN', PARLOUR],
  ['[Bot] Clipper Kings', 'BARBER', 'Malugram', 1, 'OPEN', BARBER],
  ['[Bot] Style Street Unisex', 'SALON', 'Premtala', 2, 'OPEN', SALON],
  ['[Bot] Moustache & More', 'BARBER', 'Janiganj', 2, 'OPEN', BARBER],
  ['[Bot] Lotus Beauty Lounge', 'PARLOUR', 'Shillongpatty', 1, 'OPEN', PARLOUR],
  ['[Bot] Fresh Fade Studio', 'BARBER', 'Meherpur', 3, 'OPEN', BARBER],
  ['[Bot] Crown & Comb', 'SALON', 'Itkhola', 2, 'ON_BREAK', SALON],
  ['[Bot] Little Cuts Kids Salon', 'BARBER', 'Sonai Road', 1, 'OPEN', BARBER],
  ['[Bot] Velvet Glow Parlour', 'PARLOUR', 'Bilpar', 2, 'OPEN', PARLOUR],
  ['[Bot] Old Town Barber', 'BARBER', 'Chandmari', 2, 'OPEN', BARBER],
];
const NAMES = ['Aarav', 'Riya', 'Kabir', 'Ananya', 'Vihaan', 'Meera', 'Arjun', 'Isha', 'Rohan', 'Sara', 'Dev', 'Nisha',
  'Kunal', 'Pooja', 'Rahul', 'Sneha', 'Aditya', 'Priya', 'Imran', 'Ayesha', 'Bikash', 'Rupa', 'Sanjay', 'Tania'];
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
    if ((await db.doc(`businesses/${id}`).get()).exists) { console.log(`exists ${name}`); continue; }
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
    const id = `${today}-${number}`;
    // Like real customers, bots often tick more than one service (e.g. Haircut + Beard trim).
    const services = (biz.services ?? []).length ? biz.services : [{ id: 'any', name: 'Haircut', durationMinutes: 20 }];
    const picks = [...services].sort(() => Math.random() - 0.5).slice(0, Math.random() < 0.4 ? 2 : 1);
    const ids = picks.map((p) => p.id).join('+');
    tx.update(ref, { queue: { dayKey: today, lastNumber: number } });
    tx.set(db.doc(`businesses/${bizId}/tokens/${id}`), {
      bot: true, dayKey: today, number, status: 'WAITING',
      serviceId: ids.length <= 64 ? ids : picks[0].id, serviceName: picks.map((p) => p.name).join(' + ').slice(0, 80),
      durationMinutes: picks.reduce((m, p) => m + p.durationMinutes, 0), source: Math.random() < 0.25 ? 'WALK_IN' : 'APP', createdAt: FieldValue.serverTimestamp(),
    });
    tx.set(db.doc(`businesses/${bizId}/holders/${id}`), { bot: true, customerId: null, label: `${pick(NAMES)} (bot)`, dayKey: today });
  });
}

// Bot shops finish customers a bit faster than estimated so the queue visibly moves,
// then call the next person, including real customers who booked a bot shop.
// One query per shop per round (the free plan allows 50,000 reads a day). Returns how many wait.
async function serve(bizId, biz) {
  const live = await db.collection(`businesses/${bizId}/tokens`).where('dayKey', '==', dayKey())
    .where('status', 'in', ['WAITING', 'SERVING', 'GRACE']).orderBy('number').get();
  const now = Date.now();
  let inChair = 0, waiting = 0;
  for (const d of live.docs) {
    const t = d.data();
    if (t.status === 'WAITING') waiting++;
    if (t.status !== 'SERVING') continue;
    const started = t.calledAt?.toMillis() ?? now;
    if (now - started > (60 + Math.random() * 90) * 1000) await d.ref.update({ status: 'DONE', completedAt: FieldValue.serverTimestamp() });
    else inChair++;
  }
  if (biz.status === 'CLOSED') return waiting;
  let free = biz.activeChairs - inChair;
  for (const d of live.docs) {
    if (free <= 0) break;
    if (d.data().status === 'WAITING') { await d.ref.update({ status: 'SERVING', calledAt: FieldValue.serverTimestamp(), graceEndsAt: null }); free--; waiting--; }
  }
  return waiting;
}

// Kept light on purpose: a round every BOT_INTERVAL seconds (default 90) and it stops by itself
// after BOT_HOURS hours (default 2), so the bots can't use up the free plan's daily reads.
async function run() {
  const intervalMs = Number(process.env.BOT_INTERVAL ?? 90) * 1000;
  const stopAt = Date.now() + Number(process.env.BOT_HOURS ?? 2) * 3600000;
  console.log(`Bots running (a round every ${intervalMs / 1000}s, auto-stop at ${new Date(stopAt).toLocaleTimeString()}). Press Ctrl+C to stop.`);
  let all = null, round = 0;
  while (Date.now() < stopAt) {
    if (!all || round % 20 === 0) all = (await db.collection('businesses').get()).docs.map((d) => [d.id, d.data()]);
    round++;
    let botShops = 0, realShops = 0;
    for (const [id, biz] of all) {
      try {
        if (biz.bot) {
          botShops++;
          const waiting = await serve(id, biz);
          if (biz.status === 'OPEN') {
            // First round: fill each open shop to a realistic line (3-7 waiting); then keep it busy.
            const target = round === 1 ? 3 + Math.floor(Math.random() * 5) : waiting < 8 && Math.random() < 0.6 ? waiting + 1 : waiting;
            for (let k = waiting; k < target; k++) await addBotCustomer(id);
          }
        } else {
          // Real shops: bot customers book so the owner's dashboard has a queue to work.
          // The real owner serves them; bots never press Done/Next in a real shop.
          realShops++;
          const waiting = (await db.collection(`businesses/${id}/tokens`).where('dayKey', '==', dayKey())
            .where('status', '==', 'WAITING').count().get()).data().count;
          if (biz.status === 'OPEN') {
            const target = round === 1 ? Math.max(waiting, 3) : waiting < 6 && Math.random() < 0.4 ? waiting + 1 : waiting;
            for (let k = waiting; k < target; k++) await addBotCustomer(id);
          }
        }
      } catch (e) { console.log(`${biz.name}: ${e.message}`); }
    }
    console.log(new Date().toLocaleTimeString(), `updated ${botShops} bot shops, booked into ${realShops} real shops`);
    await new Promise((r) => setTimeout(r, intervalMs));
  }
  console.log('Bots stopped (time limit reached). Run again to restart.');
}

async function remove() {
  const shops = await db.collection('businesses').where('bot', '==', true).get();
  for (const d of shops.docs) {
    await db.recursiveDelete(d.ref);
    console.log(`removed ${d.data().name}`);
  }
  console.log(`Done: ${shops.size} bot shops removed.`);
  // Bot customers who joined real shops.
  const real = await db.collection('businesses').get();
  let n = 0;
  for (const b of real.docs) {
    for (const sub of ['tokens', 'holders']) {
      const bots = await db.collection(`businesses/${b.id}/${sub}`).where('bot', '==', true).get();
      for (const d of bots.docs) { await d.ref.delete(); n++; }
    }
  }
  console.log(`Removed ${n} bot bookings from real shops.`);
}

// Free pictures from Wikimedia Commons (openly licensed) that match the shop type. Stored the
// same way the app stores owner photos: small JPEG bytes in businesses/{id}/photos + a thumbnail.
const UA = { 'User-Agent': 'QNowBotSeeder/1.0 (test data for a demo app)' };
const PHOTO_SEARCH = {
  BARBER: ['barbershop interior', 'barber shop chair', 'barber haircut'],
  SALON: ['hair salon interior', 'hairdresser salon', 'hair salon'],
  PARLOUR: ['beauty salon', 'beauty parlour', 'nail salon'],
};
async function commonsPool(queries) {
  const urls = new Set();
  for (const q of queries) {
    const u = 'https://commons.wikimedia.org/w/api.php?action=query&format=json&generator=search&gsrnamespace=6&gsrlimit=30'
      + `&gsrsearch=${encodeURIComponent(q + ' filetype:bitmap')}&prop=imageinfo&iiprop=url|mime&iiurlwidth=800`;
    const r = await (await fetch(u, { headers: UA })).json();
    for (const p of Object.values(r.query?.pages ?? {})) {
      const info = p.imageinfo?.[0];
      if (info?.mime === 'image/jpeg' && info.thumburl) urls.add(info.thumburl);
    }
  }
  return [...urls].sort(() => Math.random() - 0.5);
}
async function fetchJpeg(url) {
  const res = await fetch(url, { headers: UA, redirect: 'follow' });
  if (!res.ok) throw new Error(`photo download failed (${res.status})`);
  const buf = Buffer.from(await res.arrayBuffer());
  if (buf.length > 700_000) throw new Error('photo too big');
  return buf;
}
async function photos() {
  const shops = await db.collection('businesses').where('bot', '==', true).get();
  const pools = {};
  for (const [cat, qs] of Object.entries(PHOTO_SEARCH)) { pools[cat] = await commonsPool(qs); console.log(`${cat}: ${pools[cat].length} photos found`); }
  for (const d of shops.docs) {
    const biz = d.data(), pool = pools[biz.category] ?? pools.BARBER;
    const ids = [];
    let thumb = null;
    while (ids.length < 3 && pool.length) {
      const url = pool.pop();
      try {
        const bytes = await fetchJpeg(url);
        const id = `bot-photo-${ids.length}`;
        await d.ref.collection('photos').doc(id).set({ bot: true, data: bytes, createdAt: FieldValue.serverTimestamp() });
        if (!thumb) thumb = await fetchJpeg(url.replace(/\/\d+px-/, '/330px-')).catch(() => null);
        ids.push(id);
      } catch { /* skip that picture, try the next */ }
    }
    await d.ref.update({ photoIds: ids, thumbnail: thumb });
    console.log(`${biz.name}: ${ids.length} photos`);
  }
}
// Refresh the service list of every bot shop (e.g. after removing the old combo services).
async function services() {
  const shops = await db.collection('businesses').where('bot', '==', true).get();
  for (const d of shops.docs) {
    const biz = d.data(), list = { BARBER, SALON, PARLOUR }[biz.category] ?? BARBER;
    await d.ref.update({ services: list.map(([n, p, m], j) => ({ id: `${d.id}-s${j}`, name: n, price: p, durationMinutes: m })) });
    console.log(`services updated: ${biz.name}`);
  }
}

const cmd = process.argv[2];
if (cmd === 'services') await services();
else if (cmd === 'photos') await photos();
else if (cmd === 'seed') await seed();
else if (cmd === 'run') await run();
else if (cmd === 'remove') await remove();
else console.log('Usage: node bots.mjs seed | run | remove');
process.exit(0);