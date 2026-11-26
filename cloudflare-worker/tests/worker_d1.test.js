import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import { DatabaseSync } from 'node:sqlite';
import worker from '../src/index.js';

function createD1Shim(sqliteDb) {
  class D1Statement {
    constructor(sql, bound = []) {
      this.sql = sql;
      this.bound = bound;
    }
    bind(...args) {
      return new D1Statement(this.sql, args.map((v) => (v === undefined ? null : v)));
    }
    async first() {
      const stmt = sqliteDb.prepare(this.sql);
      const row = stmt.get(...this.bound);
      return row === undefined ? null : row;
    }
    async all() {
      const stmt = sqliteDb.prepare(this.sql);
      const rows = stmt.all(...this.bound);
      return { results: rows || [], success: true };
    }
    async run() {
      const stmt = sqliteDb.prepare(this.sql);
      const info = stmt.run(...this.bound);
      return { success: true, meta: { changes: Number(info.changes || 0), last_row_id: Number(info.lastInsertRowid || 0) } };
    }
  }

  return {
    prepare(sql) {
      return new D1Statement(sql);
    },
    async batch(statements) {
      sqliteDb.exec('BEGIN IMMEDIATE');
      try {
        const out = [];
        for (const s of statements) {
          const stmt = sqliteDb.prepare(s.sql);
          const info = stmt.run(...s.bound);
          out.push({ success: true, meta: { changes: Number(info.changes || 0) } });
        }
        sqliteDb.exec('COMMIT');
        return out;
      } catch (err) {
        try { sqliteDb.exec('ROLLBACK'); } catch {}
        throw err;
      }
    }
  };
}

function createR2Mock() {
  const store = new Map();
  return {
    async put(key, value, options) {
      store.set(key, {
        body: new Uint8Array(value),
        httpMetadata: options?.httpMetadata || {}
      });
    },
    async get(key) {
      return store.get(key) || null;
    },
    async delete(key) {
      store.delete(key);
    }
  };
}

async function runTests() {
  const sqliteDb = new DatabaseSync(':memory:');
  const migrationSql = fs.readFileSync(
    path.resolve(process.cwd(), 'cloudflare-worker/migrations/0001_initial_d1_schema.sql'),
    'utf8'
  );
  sqliteDb.exec(migrationSql);

  const r2Mock = createR2Mock();
  const { publicKey, privateKey } = crypto.generateKeyPairSync('rsa', {
    modulusLength: 1024,
    publicKeyEncoding: { type: 'spki', format: 'der' },
    privateKeyEncoding: { type: 'pkcs8', format: 'pem' }
  });
  const testMyketPubKey = publicKey.toString('base64');

  const ADMIN_KEY = 'TEST_SECURE_ADMIN_KEY_2026';
  const env = {
    ENVIRONMENT: 'test',
    ADMIN_KEY,
    MYKET_PUBLIC_KEY: testMyketPubKey,
    DB: createD1Shim(sqliteDb),
    BANNERS_BUCKET: r2Mock,
    ASSETS: {
      async fetch(req) {
        const u = new URL(req.url);
        return new Response(`STATIC:${u.pathname}`, { status: 200 });
      }
    }
  };

  async function req(pathAndQuery, options = {}) {
    const headers = new Headers(options.headers || {});
    let body = options.body;
    if (body && typeof body === 'object' && !(body instanceof FormData) && !(body instanceof Uint8Array)) {
      headers.set('Content-Type', 'application/json');
      body = JSON.stringify(body);
    }
    const request = new Request(`https://api-test.kalametgame.ir${pathAndQuery}`, {
      method: options.method || 'GET',
      headers,
      body
    });
    const response = await worker.fetch(request, env);
    const contentType = response.headers.get('content-type') || '';
    let data = null;
    if (contentType.includes('application/json')) {
      data = await response.json();
    } else {
      data = new Uint8Array(await response.arrayBuffer());
    }
    return { status: response.status, headers: response.headers, data };
  }

  console.log('1. Testing /api/health and /api/health/d1...');
  const healthRes = await req('/api/health');
  assert.equal(healthRes.status, 200);
  assert.equal(healthRes.data.status, 'ok');
  assert.equal(healthRes.data.database.status, 'connected');

  const d1HealthRes = await req('/api/health/d1');
  assert.equal(d1HealthRes.status, 200);
  assert.equal(d1HealthRes.data.success, true);
  assert.deepEqual(d1HealthRes.data.missingTables, []);

  console.log('2. Testing Admin auth security (header-only, no query leak, no hardcoded fallback)...');
  const queryLeakRes = await req(`/api/admin/overview?adminKey=${ADMIN_KEY}`);
  assert.equal(queryLeakRes.status, 401);

  const hardcodedRes = await req('/api/admin/overview', {
    headers: { 'x-admin-key': '09876poiuyAA' }
  });
  assert.equal(hardcodedRes.status, 401);

  const validAdminRes = await req('/api/admin/auth/verify', {
    headers: { 'x-admin-key': ADMIN_KEY }
  });
  assert.equal(validAdminRes.status, 200);
  assert.equal(validAdminRes.data.success, true);

  console.log('3. Testing User Auth (Register, Password Verification, PBKDF2, Wrong Pass Rejection)...');
  const loginRes = await req('/api/auth/login', {
    method: 'POST',
    body: { firstName: 'علی', lastName: 'رضایی', phoneNumber: '09123456789', password: 'MyStrongPassword123' }
  });
  assert.equal(loginRes.status, 200);
  assert.equal(loginRes.data.success, true);
  assert.ok(loginRes.data.user.token.startsWith('SEC_JWT_'));
  const userId = loginRes.data.user.id;
  const userToken = loginRes.data.user.token;

  const wrongPassRes = await req('/api/auth/login', {
    method: 'POST',
    body: { phoneNumber: '09123456789', password: 'WrongPassword' }
  });
  assert.equal(wrongPassRes.status, 401);

  console.log('4. Testing Token Bypass Rejection & Notification Settings...');
  const bypassRes = await req('/api/user/settings/notifications', {
    method: 'POST',
    headers: { 'x-user-id': userId },
    body: { enabled: false }
  });
  assert.equal(bypassRes.status, 401);

  const notifRes = await req('/api/user/settings/notifications', {
    method: 'POST',
    headers: { 'x-user-id': userId, 'Authorization': `Bearer ${userToken}` },
    body: { enabled: false }
  });
  assert.equal(notifRes.status, 200);
  assert.equal(notifRes.data.enabled, false);

  const notifGetRes = await req('/api/user/settings/notifications', {
    headers: { 'x-user-id': userId, 'x-auth-token': userToken }
  });
  assert.equal(notifGetRes.status, 200);
  assert.equal(notifGetRes.data.enabled, false);

  console.log('5. Testing 100-Squares, Ad Completion, Duplicate Prevention & Leaderboard...');
  const sqRes = await req('/api/squares', { headers: { 'x-user-id': userId } });
  assert.equal(sqRes.status, 200);
  assert.equal(sqRes.data.squares.length, 100);

  const adRes = await req('/api/ads/complete', {
    method: 'POST',
    headers: { 'x-user-id': userId, 'Authorization': `Bearer ${userToken}` },
    body: { squareNumber: 5, rewardNonce: 'nonce_sq_5_a', adDurationSeconds: 15 }
  });
  assert.equal(adRes.status, 200);
  assert.equal(adRes.data.awardedPoints, 1);
  assert.equal(adRes.data.newTotalScore, 1);

  const dupSquareRes = await req('/api/ads/complete', {
    method: 'POST',
    headers: { 'x-user-id': userId, 'Authorization': `Bearer ${userToken}` },
    body: { squareNumber: 5, rewardNonce: 'nonce_sq_5_b', adDurationSeconds: 15 }
  });
  assert.equal(dupSquareRes.status, 409);

  const dupNonceRes = await req('/api/ads/complete', {
    method: 'POST',
    headers: { 'x-user-id': userId, 'Authorization': `Bearer ${userToken}` },
    body: { squareNumber: 6, rewardNonce: 'nonce_sq_5_a', adDurationSeconds: 15 }
  });
  assert.equal(dupNonceRes.status, 409);

  const lbRes = await req('/api/leaderboard', { headers: { 'x-user-id': userId } });
  assert.equal(lbRes.status, 200);
  assert.equal(lbRes.data.leaderboard[0].totalScore, 1);

  console.log('6. Testing Feedback Reward Idempotency...');
  const fbRes1 = await req('/api/user/feedback', {
    method: 'POST',
    headers: { 'x-user-id': userId, 'Authorization': `Bearer ${userToken}` },
    body: { rating: 5, comment: 'عالی' }
  });
  assert.equal(fbRes1.status, 200);
  assert.equal(fbRes1.data.newTotalScore, 3);

  const fbRes2 = await req('/api/user/feedback', {
    method: 'POST',
    headers: { 'x-user-id': userId, 'Authorization': `Bearer ${userToken}` },
    body: { rating: 5, comment: 'تکراری' }
  });
  assert.equal(fbRes2.status, 409);

  console.log('7. Testing Store, Myket RSA Signature Verification & Instant Unlock...');
  const productsRes = await req('/api/store/products');
  assert.equal(productsRes.status, 200);
  assert.equal(productsRes.data.products.length, 4);

  const purchasePayload = JSON.stringify({
    orderId: 'ORD_RSA_1',
    productId: 'pkg_squares_50',
    purchaseToken: 'tok_rsa_50_1',
    purchaseState: 0
  });
  const signer = crypto.createSign('RSA-SHA1');
  signer.update(purchasePayload);
  const validSig = signer.sign(privateKey, 'base64');

  const badSigRes = await req('/api/store/verify-purchase', {
    method: 'POST',
    headers: { 'x-user-id': userId, 'Authorization': `Bearer ${userToken}` },
    body: {
      productId: 'pkg_squares_50',
      purchaseToken: 'tok_rsa_50_1',
      purchaseData: purchasePayload,
      signature: Buffer.from('tampered').toString('base64')
    }
  });
  assert.equal(badSigRes.status, 401);

  const goodPurchaseRes = await req('/api/store/verify-purchase', {
    method: 'POST',
    headers: { 'x-user-id': userId, 'Authorization': `Bearer ${userToken}` },
    body: {
      productId: 'pkg_squares_50',
      purchaseToken: 'tok_rsa_50_1',
      purchaseData: purchasePayload,
      signature: validSig
    }
  });
  assert.equal(goodPurchaseRes.status, 200);
  assert.equal(goodPurchaseRes.data.entitlements.instantUnlockCredits, 50);

  const replayPurchaseRes = await req('/api/store/verify-purchase', {
    method: 'POST',
    headers: { 'x-user-id': userId, 'Authorization': `Bearer ${userToken}` },
    body: {
      productId: 'pkg_squares_50',
      purchaseToken: 'tok_rsa_50_1',
      purchaseData: purchasePayload,
      signature: validSig
    }
  });
  assert.equal(replayPurchaseRes.status, 409);

  const instantRes = await req('/api/squares/instant-unlock', {
    method: 'POST',
    headers: { 'x-user-id': userId, 'Authorization': `Bearer ${userToken}` },
    body: { squareNumber: 10 }
  });
  assert.equal(instantRes.status, 200);
  assert.equal(instantRes.data.remainingCredits, 49);
  assert.equal(instantRes.data.newScore, 4);

  console.log('8. Testing Rewards CRUD, Atomic Claim, Double-Refund Prevention...');
  const createRewRes = await req('/api/rewards/admin/rewards', {
    method: 'POST',
    headers: { 'x-admin-key': ADMIN_KEY },
    body: { title: 'بسته تست', category_id: 'cat_internet', cost_points: 2, stock_quantity: 5, is_featured: true }
  });
  assert.equal(createRewRes.status, 200);
  const rewardId = createRewRes.data.reward.id;

  const claimRes = await req('/api/rewards/claim', {
    method: 'POST',
    headers: { 'x-user-id': userId, 'Authorization': `Bearer ${userToken}` },
    body: { reward_id: rewardId, recipient_name: 'علی رضایی', phone: '09123456789', idempotency_key: 'idemp_claim_1' }
  });
  assert.equal(claimRes.status, 200);
  const claimId = claimRes.data.claim.id;

  const dupClaimRes = await req('/api/rewards/claim', {
    method: 'POST',
    headers: { 'x-user-id': userId, 'Authorization': `Bearer ${userToken}` },
    body: { reward_id: rewardId, recipient_name: 'علی رضایی', phone: '09123456789', idempotency_key: 'idemp_claim_1' }
  });
  assert.equal(dupClaimRes.status, 409);

  // Reject once -> refunds 2 points (score returns 2 -> 4)
  const reject1 = await req(`/api/rewards/admin/claims/${claimId}`, {
    method: 'PATCH',
    headers: { 'x-admin-key': ADMIN_KEY },
    body: { status: 'REJECTED', admin_notes: 'تست بازگشت امتیاز' }
  });
  assert.equal(reject1.status, 200);

  // Reject second time -> must NOT double refund
  const reject2 = await req(`/api/rewards/admin/claims/${claimId}`, {
    method: 'PATCH',
    headers: { 'x-admin-key': ADMIN_KEY },
    body: { status: 'REJECTED', admin_notes: 'دوباره' }
  });
  assert.equal(reject2.status, 200);
  const scoreAfterReject = sqliteDb.prepare('SELECT total_score FROM user_scores WHERE user_id = ?').get(userId);
  assert.equal(Number(scoreAfterReject.total_score), 4);

  console.log('9. Testing Support Tickets & Cross-User Isolation...');
  const createTicketRes = await req('/api/support/tickets', {
    method: 'POST',
    headers: { 'x-user-id': userId, 'Authorization': `Bearer ${userToken}` },
    body: { subject: 'سوال درباره امتیازات', category: 'SCORES', message: 'سلام امتیاز مربع ثبت شد یا خیر؟' }
  });
  assert.equal(createTicketRes.status, 201);
  const ticketId = createTicketRes.data.ticket.id;

  // Register user 2 and try reading user 1 ticket
  const user2Login = await req('/api/auth/login', {
    method: 'POST',
    body: { firstName: 'مریم', lastName: 'احمدی', phoneNumber: '09129998877', password: 'PassUser2' }
  });
  assert.equal(user2Login.status, 200);
  const crossAccess = await req(`/api/support/tickets/${ticketId}`, {
    headers: { 'x-user-id': user2Login.data.user.id, 'Authorization': `Bearer ${user2Login.data.user.token}` }
  });
  assert.equal(crossAccess.status, 404);

  console.log('10. Testing Ad Banners Upload (R2), Placements & Deduplicated Analytics...');
  // Valid 1x1 PNG header bytes + minimal data
  const pngBytes = new Uint8Array([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0x00, 0x00, 0x00, 0x0d, 0x49, 0x48, 0x44, 0x52]);
  const fd = new FormData();
  fd.append('image', new Blob([pngBytes], { type: 'image/png' }), 'banner.png');
  const uploadRes = await req('/api/admin/ad-banners/upload-image', {
    method: 'POST',
    headers: { 'x-admin-key': ADMIN_KEY },
    body: fd
  });
  assert.equal(uploadRes.status, 200);
  assert.ok(uploadRes.data.url.startsWith('/uploads/ad-banners/'));

  const fetchedImg = await req(uploadRes.data.url);
  assert.equal(fetchedImg.status, 200);

  const createBannerRes = await req('/api/admin/ad-banners', {
    method: 'POST',
    headers: { 'x-admin-key': ADMIN_KEY },
    body: {
      title: 'بنر تست کلادفلر',
      image_url: uploadRes.data.url,
      target_url: 'https://kalametgame.ir/promo',
      link_open_mode: 'external',
      placements: ['home', 'leaderboard']
    }
  });
  assert.equal(createBannerRes.status, 201);
  const bannerId = createBannerRes.data.banner.id;

  const activeBannersLb = await req('/api/ad-banners/active?placement=leaderboard');
  assert.equal(activeBannersLb.status, 200);
  assert.equal(activeBannersLb.data.banners.length, 1);

  const activeBannersSquares = await req('/api/ad-banners/active?placement=squares');
  assert.equal(activeBannersSquares.data.banners.length, 0);

  const clk1 = await req(`/api/ad-banners/${bannerId}/click`, {
    method: 'POST',
    body: { userId, tapNonce: 'tap_1', placement: 'leaderboard' }
  });
  assert.equal(clk1.data.recorded, true);
  const clkDup = await req(`/api/ad-banners/${bannerId}/click`, {
    method: 'POST',
    body: { userId, tapNonce: 'tap_1', placement: 'leaderboard' }
  });
  assert.equal(clkDup.data.deduplicated, true);

  console.log('11. Testing Excel Export (.xlsx) & Competition Start-New Archive...');
  const xlsxRes = await req('/api/admin/users/export-excel', {
    headers: { 'x-admin-key': ADMIN_KEY }
  });
  assert.equal(xlsxRes.status, 200);
  assert.equal(xlsxRes.data[0], 0x50); // 'P'
  assert.equal(xlsxRes.data[1], 0x4b); // 'K'

  const startNewRes = await req('/api/admin/competition/start-new', {
    method: 'POST',
    headers: { 'x-admin-key': ADMIN_KEY },
    body: { confirmationPhrase: 'شروع مسابقه جدید', newTitle: 'مسابقه دوم کلادفلر' }
  });
  assert.equal(startNewRes.status, 200);
  assert.equal(startNewRes.data.archivedCompetition.totalScoreArchived, 4);
  assert.equal(startNewRes.data.newCompetition.competitionNumber, 2);

  const afterResetScore = sqliteDb.prepare('SELECT total_score FROM user_scores WHERE user_id = ?').get(userId);
  assert.equal(Number(afterResetScore.total_score), 0);

  const historyRes = await req('/api/competitions/history');
  assert.equal(historyRes.status, 200);
  assert.equal(historyRes.data.competitions.length, 2);

  console.log('✅ ALL CLOUDFLARE WORKER + D1 SQLITE TESTS PASSED SUCCESSFULLY!');
}

runTests().catch((err) => {
  console.error('❌ Worker D1 Test Failed:', err);
  process.exit(1);
});
