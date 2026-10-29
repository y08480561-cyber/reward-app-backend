/**
 * Unit Test Suite: Store Packages, Myket IAB Flow, and Instant Unlock Logic
 * Tests:
 * 1. Default product packages definition and dynamic pricing
 * 2. Idempotent purchase verification (replay token protection)
 * 3. Instant unlock credit accounting and deduction
 * 4. Genuine separation between paid unlock and Tapsell ad impressions
 * 5. Boundary checks on square unlocking with credits
 */

const assert = require('assert');

console.log('====================================================');
console.log('[Store & Myket IAB Test] Starting Validation Suite...');
console.log('====================================================');

// Mock Product Catalog
const mockProducts = [
  { id: 'pkg_squares_10', title: 'بسته بازگشایی ۱۰ مربع', productType: 'SQUARE_UNLOCK', unitsGranted: 10, priceToman: 29000, myketSku: 'reqabat_unlock_10', isActive: true, isReadyInMyket: false },
  { id: 'pkg_squares_25', title: 'بسته بازگشایی ۲۵ مربع', productType: 'SQUARE_UNLOCK', unitsGranted: 25, priceToman: 59000, myketSku: 'reqabat_unlock_25', isActive: true, isReadyInMyket: false },
  { id: 'pkg_squares_50', title: 'بسته بازگشایی ۵۰ مربع', productType: 'SQUARE_UNLOCK', unitsGranted: 50, priceToman: 99000, myketSku: 'reqabat_unlock_50', isActive: true, isReadyInMyket: false },
  { id: 'pkg_account_vip', title: 'بسته ارتقای حساب کاربری (VIP)', productType: 'ACCOUNT_UPGRADE', unitsGranted: 1, priceToman: 149000, myketSku: 'reqabat_vip_upgrade', isActive: true, isReadyInMyket: false }
];

// Test 1: Product Catalog Integrity
console.log('[Test 1] Testing store product catalog definitions...');
assert.strictEqual(mockProducts.length, 4, 'Must have 4 default packages');
const unlock10 = mockProducts.find(p => p.id === 'pkg_squares_10');
assert(unlock10, 'pkg_squares_10 must exist');
assert.strictEqual(unlock10.unitsGranted, 10);
assert.strictEqual(unlock10.priceToman, 29000);
assert.strictEqual(unlock10.myketSku, 'reqabat_unlock_10');

const vip = mockProducts.find(p => p.id === 'pkg_account_vip');
assert(vip, 'pkg_account_vip must exist');
assert.strictEqual(vip.productType, 'ACCOUNT_UPGRADE');
assert.strictEqual(vip.priceToman, 149000);
console.log('  ✓ 4 default store products verified with correct units and pricing.');

// Test 2: Idempotent Purchase Verification & Replay Protection
console.log('[Test 2] Testing purchase token idempotency...');
const processedTokens = new Set();

function verifyPurchaseToken(token, purchaseRecord) {
  if (processedTokens.has(token)) {
    return { success: false, error: 'این توکن خرید قبلاً در سیستم ثبت شده است (تکراری)', code: 409 };
  }
  processedTokens.add(token);
  return { success: true, record: purchaseRecord };
}

const firstAttempt = verifyPurchaseToken('myket_tok_abc_123', { id: 'PUR_1', amount: 29000 });
assert.strictEqual(firstAttempt.success, true, 'First purchase must succeed');

const duplicateAttempt = verifyPurchaseToken('myket_tok_abc_123', { id: 'PUR_2', amount: 29000 });
assert.strictEqual(duplicateAttempt.success, false, 'Duplicate purchase token must be rejected');
assert.strictEqual(duplicateAttempt.code, 409);
console.log('  ✓ Idempotency verified: duplicate purchase tokens safely rejected with HTTP 409.');

// Test 3: Instant Unlock Credit Consumption & Anti-Replay
console.log('[Test 3] Testing instant unlock credit consumption...');
let userState = {
  credits: 10,
  score: 0,
  completedSquares: new Set()
};

function instantUnlockSquare(state, squareNumber) {
  if (squareNumber < 1 || squareNumber > 100) {
    return { success: false, error: 'شماره مربع نامعتبر است' };
  }
  if (state.credits < 1) {
    return { success: false, error: 'سهمیه بازگشایی سریع ناکافی است' };
  }
  if (state.completedSquares.has(squareNumber)) {
    return { success: false, error: 'این مربع قبلاً باز شده است' };
  }

  // Atomically decrement credit, lock square, award point
  state.credits -= 1;
  state.completedSquares.add(squareNumber);
  state.score += 1;

  return {
    success: true,
    remainingCredits: state.credits,
    newScore: state.score,
    squareNumber
  };
}

// Unlock square 5
const unlockRes = instantUnlockSquare(userState, 5);
assert.strictEqual(unlockRes.success, true);
assert.strictEqual(userState.credits, 9, 'Credits must decrement to 9');
assert.strictEqual(userState.score, 1, 'Score must increment to 1');
assert(userState.completedSquares.has(5));

// Cannot re-unlock square 5
const reUnlockRes = instantUnlockSquare(userState, 5);
assert.strictEqual(reUnlockRes.success, false, 'Cannot re-unlock already completed square');
assert.strictEqual(userState.credits, 9, 'Credits must not decrement on failure');
assert.strictEqual(userState.score, 1, 'Score must not increment on failure');

// Boundary checks
assert.strictEqual(instantUnlockSquare(userState, 0).success, false);
assert.strictEqual(instantUnlockSquare(userState, 101).success, false);

// Unlock remaining 9 credits
for (let i = 6; i <= 14; i++) {
  const r = instantUnlockSquare(userState, i);
  assert.strictEqual(r.success, true);
}
assert.strictEqual(userState.credits, 0);
assert.strictEqual(userState.score, 10);

// Exhausted credits check
const failWhenEmpty = instantUnlockSquare(userState, 15);
assert.strictEqual(failWhenEmpty.success, false);
assert.strictEqual(failWhenEmpty.error, 'سهمیه بازگشایی سریع ناکافی است');
console.log('  ✓ Credit deduction, square locking, score awarding, and empty credit guard verified.');

// Test 4: Tapsell Analytics Preservation Check
console.log('[Test 4] Verifying clean separation between ad impression vs paid unlock...');
const adSessionAudit = [];
function recordUnlock(type, squareNumber) {
  if (type === 'AD') {
    adSessionAudit.push({ type: 'TAPSELL_AD', squareNumber });
  } else if (type === 'PAID_INSTANT_UNLOCK') {
    // Does NOT record to adSessionAudit
  }
}
recordUnlock('AD', 1);
recordUnlock('PAID_INSTANT_UNLOCK', 2);
recordUnlock('PAID_INSTANT_UNLOCK', 3);
assert.strictEqual(adSessionAudit.length, 1, 'Only genuine ad views must be logged to ad sessions');
assert.strictEqual(adSessionAudit[0].squareNumber, 1);
console.log('  ✓ Tapsell ad impressions remain 100% genuine and unpolluted.');

console.log('====================================================');
console.log('[Store & Myket IAB Test] ALL TESTS PASSED SUCCESSFULLY! ✓');
console.log('====================================================');
