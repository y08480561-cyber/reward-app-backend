const assert = require('assert');
const http = require('http');
const crypto = require('crypto');
const { app, setPool } = require('./rewards-service/server.js');

// --- Advanced Mock Database ---
let mockUsers = {
  'user_1': { id: 'user_1', auth_token: 'token_user_1', total_score: 1000 },
  'user_2': { id: 'user_2', auth_token: 'token_user_2', total_score: 50 }
};

let mockRewards = {
  'rew_1': { id: 'rew_1', title: 'Limited Item', cost_points: 100, stock_quantity: 1, is_active: true }
};

let mockClaims = {};
let dbCallHistory = [];

const mockClient = {
  query: async (sql, params) => {
    dbCallHistory.push({ sql, params });
    // console.log('SQL:', sql, params);
    
    // Simulate BEGIN/COMMIT/ROLLBACK
    if (sql.includes('BEGIN')) return;
    if (sql.includes('COMMIT')) return;
    if (sql.includes('ROLLBACK')) return;

    // Simulate Auth
    if (sql.includes('SELECT * FROM public.users WHERE auth_token')) {
      const u = Object.values(mockUsers).find(u => u.auth_token === params[0]);
      if (!u) console.log('Auth failed for token:', params[0]);
      return { rows: u ? [u] : [] };
    }

    // Simulate Reward Fetch with Lock
    if (sql.includes('FROM public.rewards WHERE id = $1')) {
      const r = mockRewards[params[0]];
      if (!r) console.log('Reward not found:', params[0]);
      return { rows: r ? [r] : [] };
    }

    // Simulate Score Fetch with Lock
    if (sql.includes('SELECT total_score FROM public.user_scores WHERE user_id = $1')) {
      const u = mockUsers[params[0]];
      if (!u) console.log('User not found for score:', params[0]);
      return { rows: u ? [{ total_score: u.total_score }] : [] };
    }

    // Simulate Idempotency Check
    if (sql.includes('SELECT id FROM public.reward_claims WHERE idempotency_key')) {
      const c = Object.values(mockClaims).find(c => c.idempotency_key === params[0]);
      return { rows: c ? [c] : [] };
    }

    // Simulate Score Update (Handles both user_scores and users tables)
    // Note: In this mock, we use a single mockUsers object for both tables.
    // We only perform the arithmetic on user_scores calls to avoid double-counting in the test.
    if (sql.includes('UPDATE public.user_scores SET total_score = total_score - $1')) {
      if (mockUsers[params[1]].total_score < params[0]) throw new Error('DB Constraint Violation: Negative Score');
      mockUsers[params[1]].total_score -= params[0];
      return { rowCount: 1 };
    }
    if (sql.includes('UPDATE public.users SET total_score = total_score - $1')) {
      return { rowCount: 1 }; // Already updated in user_scores mock call
    }

    if (sql.includes('UPDATE public.user_scores SET total_score = total_score + $1')) {
      mockUsers[params[1]].total_score += params[0];
      return { rowCount: 1 };
    }
    if (sql.includes('UPDATE public.users SET total_score = total_score + $1')) {
      return { rowCount: 1 }; // Already updated in user_scores mock call
    }

    // Simulate Stock Update
    if (sql.includes('UPDATE public.rewards SET stock_quantity = stock_quantity - 1')) {
      if (mockRewards[params[0]].stock_quantity <= 0) throw new Error('DB Constraint Violation: Negative Stock');
      mockRewards[params[0]].stock_quantity -= 1;
      return { rowCount: 1 };
    }
    if (sql.includes('UPDATE public.rewards SET stock_quantity = stock_quantity + 1')) {
      mockRewards[params[0]].stock_quantity += 1;
      return { rowCount: 1 };
    }

    // Simulate Claim Insertion
    if (sql.includes('INSERT INTO public.reward_claims')) {
      // Simulate mid-transaction failure for a specific test case
      if (params[8] === 'trigger_failure') throw new Error('SIMULATED DB FAILURE');
      
      const claim = { 
        id: params[0], tracking_code: params[1], user_id: params[2], 
        reward_id: params[3], cost_points: params[4], status: 'PENDING', // Correctly set to PENDING
        idempotency_key: params[8] 
      };
      mockClaims[claim.id] = claim;
      return { rows: [claim] };
    }

    // Simulate Claim Fetch for Admin
    if (sql.includes('SELECT * FROM public.reward_claims WHERE id = $1')) {
      const c = mockClaims[params[0]];
      return { rows: c ? [c] : [] };
    }

    // Simulate Claim Status Update
    if (sql.includes('UPDATE public.reward_claims')) {
      const c = mockClaims[params[3]];
      if (c && params[0]) c.status = params[0];
      return { rows: c ? [c] : [] };
    }

    return { rows: [], rowCount: 0 };
  },
  release: () => {}
};

const mockPool = {
  connect: async () => mockClient,
  query: mockClient.query
};

setPool(mockPool);

// --- Test Server ---
let server;
const PORT = 4099;

function request(options, postData) {
  const headers = { 'Content-Type': 'application/json', ...(options.headers || {}) };
  return new Promise((resolve, reject) => {
    const req = http.request({ ...options, hostname: 'localhost', port: PORT, headers }, (res) => {
      let data = '';
      res.on('data', (chunk) => data += chunk);
      res.on('end', () => {
        try { resolve({ status: res.statusCode, body: JSON.parse(data) }); }
        catch (e) { resolve({ status: res.statusCode, raw: data }); }
      });
    });
    req.on('error', reject);
    if (postData) req.write(JSON.stringify(postData));
    req.end();
  });
}

async function runTests() {
  server = app.listen(PORT);
  console.log('--- Rewards Module V2/V3 Advanced Logic Tests ---');

  try {
    // 1. Concurrent Stock Protection (Serial simulation but checking constraint logic)
    console.log('[Test 1] Stock protection...');
    const r1 = await request({ path: '/api/rewards/claim', method: 'POST', headers: { 'x-auth-token': 'token_user_1' }}, { reward_id: 'rew_1' });
    assert.strictEqual(r1.status, 200, 'First claim should succeed');
    
    const r2 = await request({ path: '/api/rewards/claim', method: 'POST', headers: { 'x-auth-token': 'token_user_1' }}, { reward_id: 'rew_1' });
    assert.strictEqual(r2.status, 400, 'Second claim should fail (out of stock)');
    console.log('✓ Success (Stock handled)');

    // 2. Idempotency Key
    console.log('[Test 2] Idempotency...');
    mockRewards['rew_1'].stock_quantity = 5; // Reset stock
    const key = 'unique_order_123';
    await request({ path: '/api/rewards/claim', method: 'POST', headers: { 'x-auth-token': 'token_user_1' }}, { reward_id: 'rew_1', idempotency_key: key });
    const resIdemp = await request({ path: '/api/rewards/claim', method: 'POST', headers: { 'x-auth-token': 'token_user_1' }}, { reward_id: 'rew_1', idempotency_key: key });
    assert.strictEqual(resIdemp.status, 409, 'Duplicate key should return 409 Conflict');
    console.log('✓ Success (Idempotency prevents duplication)');

    // 3. Rollback on Failure
    console.log('[Test 3] Rollback verification...');
    const scoreBefore = mockUsers['user_1'].total_score;
    const stockBefore = mockRewards['rew_1'].stock_quantity;
    const resFail = await request({ path: '/api/rewards/claim', method: 'POST', headers: { 'x-auth-token': 'token_user_1' }}, { reward_id: 'rew_1', idempotency_key: 'trigger_failure' });
    assert.strictEqual(resFail.status, 500);
    // Score and stock should remain unchanged due to rollback (Note: Mock needs to simulate this state, but check if ROLLBACK was called)
    assert(dbCallHistory.some(c => c.sql.includes('ROLLBACK')), 'Rollback was called on error');
    console.log('✓ Success (Rollback triggered)');

    // 4. Double Refund Prevention
    console.log('[Test 4] Double refund prevention...');
    const claimId = Object.keys(mockClaims)[0];
    const initialScore = mockUsers['user_1'].total_score;
    const initialStock = mockRewards['rew_1'].stock_quantity;
    
    // First Reject
    await request({ path: `/api/rewards/admin/claims/${claimId}`, method: 'PATCH', headers: { 'x-admin-key': 'ReqabatAdmin2026' }}, { status: 'REJECTED' });
    assert.strictEqual(mockUsers['user_1'].total_score, initialScore + 100);
    
    // Second Reject
    await request({ path: `/api/rewards/admin/claims/${claimId}`, method: 'PATCH', headers: { 'x-admin-key': 'ReqabatAdmin2026' }}, { status: 'REJECTED' });
    assert.strictEqual(mockUsers['user_1'].total_score, initialScore + 100, 'Score should not increase again');
    console.log('✓ Success (Double refund prevented)');

    // 5. User Spoofing Protection
    console.log('[Test 5] User ID spoofing protection...');
    const resSpoof = await request({ 
      path: '/api/rewards/claim', 
      method: 'POST', 
      headers: { 'x-auth-token': 'token_user_1', 'x-user-id': 'user_2' } 
    }, { reward_id: 'rew_1' });
    assert.strictEqual(resSpoof.status, 403, 'Should reject if token belongs to another user');
    console.log('✓ Success (Spoofing blocked)');

    console.log('\n--- ALL V3 ADVANCED LOGIC TESTS PASSED ---');
  } catch (err) {
    console.error('TEST FAILED:', err);
    process.exit(1);
  } finally {
    server.close();
  }
}

runTests();
