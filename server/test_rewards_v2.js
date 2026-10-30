const assert = require('assert');
const http = require('http');
const crypto = require('crypto');
const { app, setPool } = require('./rewards-service/server.js');

// --- Mock Database ---
let mockUsers = {
  'user_1': { id: 'user_1', auth_token: 'valid_token_1', total_score: 1000 },
  'user_2': { id: 'user_2', auth_token: 'valid_token_2', total_score: 50 } // Low score
};

let mockRewards = {
  'rew_1': { id: 'rew_1', title: 'Gift Card 100', cost_points: 100, stock_quantity: 10, is_active: true },
  'rew_sold_out': { id: 'rew_sold_out', title: 'Sold Out', cost_points: 100, stock_quantity: 0, is_active: true }
};

let mockClaims = {};

const mockClient = {
  query: async (sql, params) => {
    // Basic query router
    if (sql.includes('BEGIN') || sql.includes('COMMIT') || sql.includes('ROLLBACK')) return;
    
    if (sql.includes('SELECT * FROM public.users WHERE auth_token')) {
      const u = Object.values(mockUsers).find(u => u.auth_token === params[0]);
      return { rows: u ? [u] : [] };
    }
    
    if (sql.includes('SELECT * FROM public.rewards WHERE id = $1')) {
      const r = mockRewards[params[0]];
      return { rows: r ? [r] : [] };
    }
    
    if (sql.includes('SELECT total_score FROM public.user_scores WHERE user_id = $1')) {
      const u = mockUsers[params[0]];
      return { rows: u ? [{ total_score: u.total_score }] : [] };
    }

    if (sql.includes('INSERT INTO public.reward_claims')) {
      const claim = { id: params[0], tracking_code: params[1], user_id: params[2], reward_id: params[3], cost_points: params[4], status: params[5] };
      mockClaims[claim.id] = claim;
      return { rows: [claim] };
    }

    if (sql.includes('UPDATE public.user_scores SET total_score = total_score - $1')) {
      mockUsers[params[1]].total_score -= params[0];
      return { rowCount: 1 };
    }

    if (sql.includes('UPDATE public.rewards SET stock_quantity = stock_quantity - 1')) {
      mockRewards[params[0]].stock_quantity -= 1;
      return { rowCount: 1 };
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
  return new Promise((resolve, reject) => {
    const req = http.request({ ...options, hostname: 'localhost', port: PORT }, (res) => {
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
  console.log('--- Rewards Module V2 Security & Logic Tests ---');

  try {
    // Case 1: Valid Claim
    console.log('[Test 1] Valid Claim...');
    const res1 = await request({
      path: '/api/rewards/claim',
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'x-auth-token': 'valid_token_1' }
    }, { reward_id: 'rew_1' });
    assert.strictEqual(res1.status, 200);
    assert.strictEqual(res1.body.success, true);
    assert.strictEqual(mockUsers['user_1'].total_score, 900);
    assert.strictEqual(mockRewards['rew_1'].stock_quantity, 9);
    console.log('✓ Success');

    // Case 2: Insufficient Points
    console.log('[Test 2] Insufficient Points...');
    const res2 = await request({
      path: '/api/rewards/claim',
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'x-auth-token': 'valid_token_2' }
    }, { reward_id: 'rew_1' });
    assert.strictEqual(res2.status, 400);
    assert.strictEqual(res2.body.success, false);
    assert(res2.body.message.includes('امتیاز شما کافی نیست'));
    console.log('✓ Rejected correctly');

    // Case 3: Out of Stock
    console.log('[Test 3] Out of Stock...');
    const res3 = await request({
      path: '/api/rewards/claim',
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'x-auth-token': 'valid_token_1' }
    }, { reward_id: 'rew_sold_out' });
    assert.strictEqual(res3.status, 400);
    assert(res3.body.message.includes('موجودی'));
    console.log('✓ Rejected correctly');

    // Case 4: Invalid Token
    console.log('[Test 4] Invalid Token...');
    const res4 = await request({
      path: '/api/rewards/claim',
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'x-auth-token': 'wrong' }
    }, { reward_id: 'rew_1' });
    assert.strictEqual(res4.status, 401);
    console.log('✓ Unauthorized correctly');

    console.log('\n--- ALL V2 LOGIC TESTS PASSED ---');
  } catch (err) {
    console.error('TEST FAILED:', err);
    process.exit(1);
  } finally {
    server.close();
  }
}

runTests();
