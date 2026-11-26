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
      return {
        success: true,
        meta: { changes: Number(info.changes || 0), last_row_id: Number(info.lastInsertRowid || 0) }
      };
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
  const migration1Sql = fs.readFileSync(
    path.resolve(process.cwd(), 'cloudflare-worker/migrations/0001_initial_d1_schema.sql'),
    'utf8'
  );
  const migration2Sql = fs.readFileSync(
    path.resolve(process.cwd(), 'cloudflare-worker/migrations/0002_hardening_and_constraints.sql'),
    'utf8'
  );
  sqliteDb.exec(migration1Sql);
  sqliteDb.exec(migration2Sql);

  const r2Mock = createR2Mock();
  const { publicKey, privateKey } = crypto.generateKeyPairSync('rsa', {
    modulusLength: 1024,
    publicKeyEncoding: { type: 'spki', format: 'der' },
    privateKeyEncoding: { type: 'pkcs8', format: 'pem' }
  });
  const testMyketPubKey = publicKey.toString('base64');

  function signMyketPayload(payloadObj) {
    const purchaseData = JSON.stringify(payloadObj);
    const signer = crypto.createSign('RSA-SHA1');
    signer.update(purchaseData);
    const signature = signer.sign(privateKey, 'base64');
    return { purchaseData, signature };
  }

  const ADMIN_KEY = 'TEST_SECURE_ADMIN_KEY_2026';
  const baseDb = createD1Shim(sqliteDb);
  const env = {
    ENVIRONMENT: 'test',
    ADMIN_KEY,
    MYKET_PUBLIC_KEY: testMyketPubKey,
    DB: baseDb,
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
    const request = new Request(`https://api.kalametgame.ir${pathAndQuery}`, {
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

  console.log('5. Testing /api/squares Auth Hardening, Ad Completion, Leaderboard & Comp Privacy...');
  const sqUnauthNoToken = await req('/api/squares', { headers: { 'x-user-id': userId } });
  assert.equal(sqUnauthNoToken.status, 401);

  const sqUnauthQueryOnly = await req(`/api/squares?userId=${userId}`);
  assert.equal(sqUnauthQueryOnly.status, 401);

  const sqMismatch = await req('/api/squares?userId=00000000-0000-0000-0000-000000000000', {
    headers: { 'x-user-id': userId, 'Authorization': `Bearer ${userToken}` }
  });
  assert.equal(sqMismatch.status, 403);

  const sqRes = await req('/api/squares', {
    headers: { 'x-user-id': userId, 'Authorization': `Bearer ${userToken}` }
  });
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

  // Leaderboard must omit userId UUIDs and ignore raw x-user-id without token
  const lbUnauthRes = await req('/api/leaderboard', { headers: { 'x-user-id': userId } });
  assert.equal(lbUnauthRes.status, 200);
  assert.equal(lbUnauthRes.data.leaderboard[0].totalScore, 1);
  assert.equal('userId' in lbUnauthRes.data.leaderboard[0], false);
  assert.equal(lbUnauthRes.data.leaderboard[0].isCurrentUser, false);

  const lbAuthRes = await req('/api/leaderboard', { headers: { 'Authorization': `Bearer ${userToken}` } });
  assert.equal(lbAuthRes.status, 200);
  assert.equal(lbAuthRes.data.leaderboard[0].isCurrentUser, true);

  // GET /api/competition/current privacy hardening
  const compAnon = await req('/api/competition/current');
  assert.equal(compAnon.status, 200);
  assert.equal(compAnon.data.userScore, 0);

  const compUnauthHint = await req('/api/competition/current', { headers: { 'x-user-id': userId } });
  assert.equal(compUnauthHint.status, 401);

  const compAuth = await req('/api/competition/current', { headers: { 'Authorization': `Bearer ${userToken}` } });
  assert.equal(compAuth.status, 200);
  assert.equal(compAuth.data.userScore, 1);

  console.log('6. Testing Feedback Reward Atomic DB.batch & Concurrency Idempotency...');
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

  // Register user 2 early for multi-user isolation & concurrent feedback test
  const user2Login = await req('/api/auth/login', {
    method: 'POST',
    body: { firstName: 'مریم', lastName: 'احمدی', phoneNumber: '09129998877', password: 'PassUser2' }
  });
  assert.equal(user2Login.status, 200);
  const user2Id = user2Login.data.user.id;
  const user2Token = user2Login.data.user.token;

  const [fbConc1, fbConc2] = await Promise.all([
    req('/api/user/feedback', {
      method: 'POST',
      headers: { 'Authorization': `Bearer ${user2Token}` },
      body: { rating: 5, comment: 'همزمان ۱' }
    }),
    req('/api/user/feedback', {
      method: 'POST',
      headers: { 'Authorization': `Bearer ${user2Token}` },
      body: { rating: 5, comment: 'همزمان ۲' }
    })
  ]);
  const fbStatuses = [fbConc1.status, fbConc2.status].sort((a, b) => a - b);
  assert.deepEqual(fbStatuses, [200, 409]);
  const u2FeedbackCount = sqliteDb.prepare('SELECT COUNT(*) AS cnt FROM user_feedback WHERE user_id = ?').get(user2Id);
  const u2Score = sqliteDb.prepare('SELECT total_score FROM user_scores WHERE user_id = ?').get(user2Id);
  assert.equal(Number(u2FeedbackCount.cnt), 1);
  assert.equal(Number(u2Score.total_score), 2);

  console.log('7. Testing Panels Sync, Mandatory Myket RSA Verification & Instant Unlock Concurrency...');
  const panelsRes = await req('/api/panels');
  assert.equal(panelsRes.status, 200);
  const silverPanel = panelsRes.data.panels.find((p) => p.id === 'silver');
  const goldPanel = panelsRes.data.panels.find((p) => p.id === 'gold');
  assert.ok(silverPanel && goldPanel);
  assert.equal(silverPanel.priceToman, 70000);
  assert.equal(silverPanel.multiplier, 3);
  assert.equal(goldPanel.priceToman, 120000);
  assert.equal(goldPanel.multiplier, 6);

  // Reject missing purchaseData/signature on both store & panel purchase endpoints
  const missingSigStore = await req('/api/store/verify-purchase', {
    method: 'POST',
    headers: { 'Authorization': `Bearer ${userToken}` },
    body: { productId: 'pkg_squares_50', purchaseToken: 'MYKET_FAKE_TOKEN' }
  });
  assert.equal(missingSigStore.status, 400);

  const missingSigPanel = await req('/api/purchases/verify', {
    method: 'POST',
    headers: { 'Authorization': `Bearer ${userToken}` },
    body: { productId: 'silver_panel', purchaseToken: 'MYKET_SECURE_TOKEN_123' }
  });
  assert.equal(missingSigPanel.status, 400);

  // Valid RSA-signed panel upgrade test on /api/purchases/verify
  const { purchaseData: silverPayload, signature: silverSig } = signMyketPayload({
    orderId: 'ORD_SILVER_1',
    productId: 'pkg_upgrade_silver',
    purchaseToken: 'tok_rsa_silver_1',
    purchaseState: 0
  });
  const validPanelRes = await req('/api/purchases/verify', {
    method: 'POST',
    headers: { 'Authorization': `Bearer ${user2Token}` },
    body: {
      productId: 'pkg_upgrade_silver',
      purchaseToken: 'tok_rsa_silver_1',
      purchaseData: silverPayload,
      signature: silverSig
    }
  });
  assert.equal(validPanelRes.status, 200);
  assert.equal(validPanelRes.data.membership.multiplier, 3);
  assert.equal(validPanelRes.data.membership.accountTier, 'SILVER');

  const { purchaseData: purchasePayload50, signature: validSig50 } = signMyketPayload({
    orderId: 'ORD_RSA_1',
    productId: 'pkg_squares_50',
    purchaseToken: 'tok_rsa_50_1',
    purchaseState: 0
  });

  const badSigRes = await req('/api/store/verify-purchase', {
    method: 'POST',
    headers: { 'x-user-id': userId, 'Authorization': `Bearer ${userToken}` },
    body: {
      productId: 'pkg_squares_50',
      purchaseToken: 'tok_rsa_50_1',
      purchaseData: purchasePayload50,
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
      purchaseData: purchasePayload50,
      signature: validSig50
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
      purchaseData: purchasePayload50,
      signature: validSig50
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

  // Instant-unlock concurrency race protection & negative credit non-regression
  sqliteDb.prepare(`INSERT OR REPLACE INTO user_entitlements (user_id, instant_unlock_credits, is_vip, account_tier, multiplier) VALUES (?, 1, 0, 'NORMAL', 1)`).run(user2Id);
  sqliteDb.prepare(`UPDATE users SET instant_unlock_credits = 1 WHERE id = ?`).run(user2Id);

  const [unlockRace1, unlockRace2] = await Promise.all([
    req('/api/squares/instant-unlock', {
      method: 'POST',
      headers: { 'Authorization': `Bearer ${user2Token}` },
      body: { squareNumber: 21 }
    }),
    req('/api/squares/instant-unlock', {
      method: 'POST',
      headers: { 'Authorization': `Bearer ${user2Token}` },
      body: { squareNumber: 22 }
    })
  ]);
  const raceStatuses = [unlockRace1.status, unlockRace2.status].sort((a, b) => a - b);
  assert.deepEqual(raceStatuses, [200, 403]);

  const u2AfterRaceEnt = sqliteDb.prepare('SELECT instant_unlock_credits FROM user_entitlements WHERE user_id = ?').get(user2Id);
  const u2AfterRaceUsr = sqliteDb.prepare('SELECT instant_unlock_credits FROM users WHERE id = ?').get(user2Id);
  assert.equal(Number(u2AfterRaceEnt.instant_unlock_credits), 0);
  assert.equal(Number(u2AfterRaceUsr.instant_unlock_credits), 0);

  // Direct DB constraint trigger must block decrementing credits below 0
  assert.throws(
    () => sqliteDb.prepare('UPDATE user_entitlements SET instant_unlock_credits = -1 WHERE user_id = ?').run(user2Id),
    /CHECK constraint failed/
  );

  // Non-destructive verification for pre-existing negative credit accounts
  const legacyCheckDb = new DatabaseSync(':memory:');
  legacyCheckDb.exec(migration1Sql);
  legacyCheckDb.prepare(
    `INSERT INTO users (id, first_name, last_name, phone_number, instant_unlock_credits) VALUES ('legacy_neg_1', 'کاربر', 'قدیمی', '09120001111', -3)`
  ).run();
  legacyCheckDb.prepare(
    `INSERT INTO user_entitlements (user_id, instant_unlock_credits) VALUES ('legacy_neg_1', -3)`
  ).run();
  legacyCheckDb.exec(migration2Sql); // Must NOT fail or overwrite existing negative credit rows
  const negViewRows = legacyCheckDb.prepare('SELECT * FROM v_negative_credit_users').all();
  assert.equal(negViewRows.length, 1);
  assert.equal(negViewRows[0].user_id, 'legacy_neg_1');
  assert.equal(Number(negViewRows[0].users_credits), -3);
  assert.equal(Number(negViewRows[0].entitlement_credits), -3);
  legacyCheckDb.close();

  console.log('8. Testing Rewards CRUD, Atomic Claim, Double-Refund Prevention & Claims Pagination...');
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

  // Create a 2nd claim by user 2 to test pagination
  const claim2Res = await req('/api/rewards/claim', {
    method: 'POST',
    headers: { 'x-user-id': user2Id, 'Authorization': `Bearer ${user2Token}` },
    body: { reward_id: rewardId, recipient_name: 'مریم احمدی', phone: '09129998877', idempotency_key: 'idemp_claim_2' }
  });
  assert.equal(claim2Res.status, 200);

  const pagedClaimsRes = await req('/api/rewards/admin/claims?limit=1&page=1', {
    headers: { 'x-admin-key': ADMIN_KEY }
  });
  assert.equal(pagedClaimsRes.status, 200);
  assert.equal(pagedClaimsRes.data.claims.length, 1);
  assert.equal(pagedClaimsRes.data.pagination.total, 2);
  assert.equal(pagedClaimsRes.data.pagination.totalPages, 2);
  assert.equal(pagedClaimsRes.data.pagination.hasMore, true);

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

  console.log('9. Testing Support Tickets, Cross-User Isolation & Concurrent daily_cycles Creation...');
  const createTicketRes = await req('/api/support/tickets', {
    method: 'POST',
    headers: { 'x-user-id': userId, 'Authorization': `Bearer ${userToken}` },
    body: { subject: 'سوال درباره امتیازات', category: 'SCORES', message: 'سلام امتیاز مربع ثبت شد یا خیر؟' }
  });
  assert.equal(createTicketRes.status, 201);
  const ticketId = createTicketRes.data.ticket.id;

  const crossAccess = await req(`/api/support/tickets/${ticketId}`, {
    headers: { 'x-user-id': user2Id, 'Authorization': `Bearer ${user2Token}` }
  });
  assert.equal(crossAccess.status, 404);

  // Concurrent daily_cycles creation for two fresh users in the exact same millisecond/second
  const u3Login = await req('/api/auth/login', {
    method: 'POST',
    body: { firstName: 'سارا', lastName: 'محمدی', phoneNumber: '09120001122', password: 'PassUser3' }
  });
  const u4Login = await req('/api/auth/login', {
    method: 'POST',
    body: { firstName: 'رضا', lastName: 'کریمی', phoneNumber: '09120001123', password: 'PassUser4' }
  });
  const [sqU3, sqU4] = await Promise.all([
    req('/api/squares', { headers: { 'Authorization': `Bearer ${u3Login.data.user.token}` } }),
    req('/api/squares', { headers: { 'Authorization': `Bearer ${u4Login.data.user.token}` } })
  ]);
  assert.equal(sqU3.status, 200);
  assert.equal(sqU4.status, 200);
  assert.notEqual(sqU3.data.cycle.cycleId, sqU4.data.cycle.cycleId);

  console.log('10. Testing Ad Banners Upload (R2), Placements, Deduplicated Analytics & Announcements Pre-Validation...');
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

  // Announcement 404 must NOT deactivate currently active announcement
  const badAnnRes = await req('/api/admin/announcements', {
    method: 'POST',
    headers: { 'x-admin-key': ADMIN_KEY },
    body: { id: 'NON_EXISTENT_ANN_ID', title: 'تست', content: 'متن تست', isActive: true }
  });
  assert.equal(badAnnRes.status, 404);
  const activeAnnStillAlive = sqliteDb.prepare('SELECT COUNT(*) AS cnt FROM home_announcements WHERE is_active = 1').get();
  assert.equal(Number(activeAnnStillAlive.cnt), 1);

  console.log('11. Testing Excel Export (.xlsx), Chunked Competition Start-New, Chunk Failure Safety & Results Privacy...');
  const xlsxRes = await req('/api/admin/users/export-excel', {
    headers: { 'x-admin-key': ADMIN_KEY }
  });
  assert.equal(xlsxRes.status, 200);
  assert.equal(xlsxRes.data[0], 0x50); // 'P'
  assert.equal(xlsxRes.data[1], 0x4b); // 'K'

  // Seed 120 extra users to force > 2 chunks (ARCHIVE_CHUNK_SIZE = 50 -> 3 chunks total)
  const bulkNow = new Date().toISOString();
  sqliteDb.exec('BEGIN');
  const insUser = sqliteDb.prepare(
    `INSERT INTO users (id, first_name, last_name, phone_number, total_score, created_at, updated_at) VALUES (?, ?, ?, ?, 1, ?, ?)`
  );
  const insScore = sqliteDb.prepare(
    `INSERT INTO user_scores (user_id, total_score, updated_at) VALUES (?, 1, ?)`
  );
  for (let i = 0; i < 120; i++) {
    const uid = `bulk_u_${i}`;
    insUser.run(uid, 'کاربر', `شماره ${i}`, `0990000${String(i).padStart(4, '0')}`, bulkNow, bulkNow);
    insScore.run(uid, bulkNow);
  }
  sqliteDb.exec('COMMIT');

  // Inject one-time simulated failure on 2nd archive chunk in DB.batch
  let batchCalls = 0;
  env.DB = {
    prepare: (sql) => baseDb.prepare(sql),
    async batch(statements) {
      batchCalls += 1;
      if (batchCalls === 2) {
        throw new Error('SIMULATED_D1_CHUNK_FAILURE');
      }
      return baseDb.batch(statements);
    }
  };

  const failedStartRes = await req('/api/admin/competition/start-new', {
    method: 'POST',
    headers: { 'x-admin-key': ADMIN_KEY },
    body: { confirmationPhrase: 'شروع مسابقه جدید', newTitle: 'مسابقه دوم کلادفلر' }
  });
  assert.equal(failedStartRes.status, 500);

  // Restore DB & verify COMP_1 stayed ACTIVE, scores were NOT zeroed, lock was released
  env.DB = baseDb;
  const comp1StillActive = sqliteDb.prepare(`SELECT status FROM competitions WHERE id = 'COMP_1'`).get();
  assert.equal(comp1StillActive.status, 'ACTIVE');
  const user1ScorePreserved = sqliteDb.prepare('SELECT total_score FROM user_scores WHERE user_id = ?').get(userId);
  assert.equal(Number(user1ScorePreserved.total_score), 4);
  const lockReleased = sqliteDb.prepare(`SELECT is_locked FROM competition_locks WHERE id = 'active'`).get();
  assert.equal(Number(lockReleased.is_locked), 0);

  // Idempotent retry after chunk failure succeeds across all chunks
  const startNewRes = await req('/api/admin/competition/start-new', {
    method: 'POST',
    headers: { 'x-admin-key': ADMIN_KEY },
    body: { confirmationPhrase: 'شروع مسابقه جدید', newTitle: 'مسابقه دوم کلادفلر' }
  });
  assert.equal(startNewRes.status, 200);
  assert.equal(startNewRes.data.archivedCompetition.totalScoreArchived, 127); // 4 (u1) + 3 (u2: 2+3-2=3) + 120 (bulk) = 127
  assert.equal(startNewRes.data.newCompetition.competitionNumber, 2);

  const afterResetScore = sqliteDb.prepare('SELECT total_score FROM user_scores WHERE user_id = ?').get(userId);
  assert.equal(Number(afterResetScore.total_score), 0);

  const archiveCount = sqliteDb.prepare(`SELECT COUNT(*) AS cnt FROM competition_archives WHERE competition_id = 'COMP_1'`).get();
  assert.equal(Number(archiveCount.cnt), 124); // u1, u2, u3, u4 + 120 bulk = 124

  // Results endpoint must hide phoneNumber and userId publicly, and return masked phoneNumber only to admins
  const publicResults = await req('/api/competitions/COMP_1/results');
  assert.equal(publicResults.status, 200);
  assert.equal('userId' in publicResults.data.results[0], false);
  assert.equal('phoneNumber' in publicResults.data.results[0], false);

  const adminResults = await req('/api/competitions/COMP_1/results', {
    headers: { 'x-admin-key': ADMIN_KEY }
  });
  assert.equal(adminResults.status, 200);
  assert.equal('userId' in adminResults.data.results[0], false);
  assert.ok(adminResults.data.results[0].phoneNumber.includes('***'));

  const historyRes = await req('/api/competitions/history');
  assert.equal(historyRes.status, 200);
  assert.equal(historyRes.data.competitions.length, 2);

  console.log('✅ ALL CLOUDFLARE WORKER + D1 SQLITE TESTS PASSED SUCCESSFULLY!');
}

runTests().catch((err) => {
  console.error('❌ Worker D1 Test Failed:', err);
  process.exit(1);
});
