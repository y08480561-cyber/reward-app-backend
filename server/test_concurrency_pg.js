const http = require('http');
const crypto = require('crypto');
const { Pool } = require('pg');
require('dotenv').config();

// PostgreSQL Connection configuration
const connectionString = process.env.DATABASE_URL || 'postgres://localhost:5432/reqabat';

async function runRealEndpointConcurrencyTest() {
  console.log("=== Real Endpoint (/api/user/feedback) PostgreSQL Concurrency Test ===");
  const pool = new Pool({ connectionString, max: 15 });

  // 1. Generate unique test user identity matching database schema
  const randomHex = crypto.randomBytes(4).toString('hex');
  const testUserId = `TEST_CONC_${Date.now()}_${randomHex}`;
  const testPhoneNumber = `0999${Math.floor(10000000 + Math.random() * 89999999)}`;
  const testAuthToken = `TOKEN_TEST_${crypto.randomUUID()}`;
  let testPassed = false;
  let serverInstance = null;

  try {
    // 2. Create independent test user in public.users
    console.log(`\n1. Creating test user in PostgreSQL...`);
    console.log(`- User ID: ${testUserId}`);
    console.log(`- Phone Number: ${testPhoneNumber}`);
    console.log(`- Initial Score: 10`);

    await pool.query(`
      INSERT INTO public.users (
        id, first_name, last_name, phone_number, auth_token, total_score, feedback_reward_claimed, status, created_at, updated_at
      ) VALUES ($1, 'تست', 'همزمانی', $2, $3, 10, false, 'active', NOW(), NOW())
    `, [testUserId, testPhoneNumber, testAuthToken]);

    // Insert initial score in user_scores table (key is user_id)
    await pool.query(`
      INSERT INTO public.user_scores (user_id, total_score, updated_at)
      VALUES ($1, 10, NOW())
      ON CONFLICT (user_id) DO UPDATE SET total_score = 10, updated_at = NOW()
    `, [testUserId]);

    // Verify initial state using correct column names
    const initUserRes = await pool.query(`SELECT id, total_score, feedback_reward_claimed FROM public.users WHERE id = $1`, [testUserId]);
    const initScoreRes = await pool.query(`SELECT user_id, total_score FROM public.user_scores WHERE user_id = $1`, [testUserId]);

    if (initUserRes.rows.length === 0 || initScoreRes.rows.length === 0) {
      throw new Error(`Failed to initialize test user in database.`);
    }

    console.log(`- Initial 'users' total_score: ${initUserRes.rows[0].total_score}`);
    console.log(`- Initial 'user_scores' total_score (where user_id = $1): ${initScoreRes.rows[0].total_score}`);
    console.log(`- Initial feedback_reward_claimed: ${initUserRes.rows[0].feedback_reward_claimed}`);

    // 3. Spin up Express app instance for testing the actual production endpoint
    console.log(`\n2. Starting local Express test server for production API testing...`);
    const app = require('./server'); // Import Express app
    const TEST_PORT = 3099;

    await new Promise((resolve) => {
      serverInstance = app.listen(TEST_PORT, '127.0.0.1', () => {
        console.log(`- Test HTTP server listening on http://127.0.0.1:${TEST_PORT}`);
        resolve();
      });
    });

    // Helper to send HTTP POST request to the actual production /api/user/feedback endpoint
    const sendFeedbackApiRequest = (taskIndex) => {
      return new Promise((resolve) => {
        const postData = JSON.stringify({
          rating: 5,
          comment: `Concurrent API test comment from task ${taskIndex}`
        });

        const options = {
          hostname: '127.0.0.1',
          port: TEST_PORT,
          path: '/api/user/feedback',
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            'Content-Length': Buffer.byteLength(postData),
            'x-user-id': testUserId,
            'x-auth-token': testAuthToken
          }
        };

        const req = http.request(options, (res) => {
          let body = '';
          res.on('data', (chunk) => body += chunk);
          res.on('end', () => {
            let json = {};
            try { json = JSON.parse(body); } catch (e) {}
            resolve({
              taskIndex,
              statusCode: res.statusCode,
              body: json,
              rawBody: body
            });
          });
        });

        req.on('error', (err) => {
          resolve({
            taskIndex,
            statusCode: 500,
            error: err.message
          });
        });

        req.write(postData);
        req.end();
      });
    };

    // 4. Launch 5 concurrent HTTP POST requests simultaneously against /api/user/feedback
    console.log(`\n3. Sending 5 concurrent HTTP POST requests to /api/user/feedback endpoint...`);
    const CONCURRENT_COUNT = 5;
    const tasks = Array.from({ length: CONCURRENT_COUNT }, (_, i) => sendFeedbackApiRequest(i + 1));
    const responses = await Promise.all(tasks);

    const successfulResponses = responses.filter(r => r.statusCode === 200);
    const rejectedResponses = responses.filter(r => r.statusCode !== 200);

    console.log(`\n4. Concurrency Response Summary:`);
    console.log(`- Total Parallel HTTP Requests: ${CONCURRENT_COUNT}`);
    console.log(`- Successful HTTP 200 Responses: ${successfulResponses.length}`);
    console.log(`- Rejected HTTP Responses (409/Error): ${rejectedResponses.length}`);

    responses.forEach(r => {
      console.log(`  Task #${r.taskIndex}: HTTP ${r.statusCode} -> ${JSON.stringify(r.body)}`);
    });

    // 5. Database State Verification after concurrent execution
    console.log(`\n5. Verifying Database State Post-Concurrency...`);
    const finalUserRes = await pool.query(`SELECT total_score, feedback_reward_claimed FROM public.users WHERE id = $1`, [testUserId]);
    const finalScoreRes = await pool.query(`SELECT total_score FROM public.user_scores WHERE user_id = $1`, [testUserId]);
    const feedbackRecordsRes = await pool.query(`SELECT id, rating, comment FROM public.user_feedback WHERE user_id = $1`, [testUserId]);

    const finalUsersScore = Number(finalUserRes.rows[0].total_score);
    const finalScoresScore = Number(finalScoreRes.rows[0].total_score);
    const feedbackRecordCount = feedbackRecordsRes.rows.length;
    const feedbackClaimedStatus = finalUserRes.rows[0].feedback_reward_claimed;

    console.log(`- Score in 'users' table (Before: 10 -> After: ${finalUsersScore})`);
    console.log(`- Score in 'user_scores' table (where user_id = $1) (Before: 10 -> After: ${finalScoresScore})`);
    console.log(`- Total records inserted in 'user_feedback': ${feedbackRecordCount}`);
    console.log(`- 'feedback_reward_claimed' status: ${feedbackClaimedStatus}`);

    // 6. Test subsequent duplicate request
    console.log(`\n6. Testing subsequent duplicate claim attempt...`);
    const duplicateRes = await sendFeedbackApiRequest(99);
    console.log(`- Duplicate Request HTTP Status: ${duplicateRes.statusCode} -> ${JSON.stringify(duplicateRes.body)}`);

    // 7. Evaluate exact test assertions
    const isSuccessCountValid = successfulResponses.length === 1;
    const isRejectedCountValid = rejectedResponses.length === (CONCURRENT_COUNT - 1);
    const isUsersScoreValid = finalUsersScore === 12; // Initial 10 + 2 reward
    const isScoresScoreValid = finalScoresScore === 12; // Initial 10 + 2 reward
    const isFeedbackCountValid = feedbackRecordCount === 1;
    const isClaimedFlagValid = feedbackClaimedStatus === true;
    const isDuplicateBlocked = duplicateRes.statusCode === 409;

    testPassed = isSuccessCountValid &&
                 isRejectedCountValid &&
                 isUsersScoreValid &&
                 isScoresScoreValid &&
                 isFeedbackCountValid &&
                 isClaimedFlagValid &&
                 isDuplicateBlocked;

    console.log(`\n7. Assertion Checklist:`);
    console.log(`  [${isSuccessCountValid ? 'PASS' : 'FAIL'}] Exactly 1 HTTP 200 Success`);
    console.log(`  [${isRejectedCountValid ? 'PASS' : 'FAIL'}] Exactly ${CONCURRENT_COUNT - 1} HTTP Rejected (409)`);
    console.log(`  [${isUsersScoreValid ? 'PASS' : 'FAIL'}] 'users.total_score' increased by exactly +2 (10 -> ${finalUsersScore})`);
    console.log(`  [${isScoresScoreValid ? 'PASS' : 'FAIL'}] 'user_scores.total_score' increased by exactly +2 (10 -> ${finalScoresScore})`);
    console.log(`  [${isFeedbackCountValid ? 'PASS' : 'FAIL'}] Exactly 1 feedback record inserted in 'user_feedback'`);
    console.log(`  [${isClaimedFlagValid ? 'PASS' : 'FAIL'}] 'feedback_reward_claimed' set to true`);
    console.log(`  [${isDuplicateBlocked ? 'PASS' : 'FAIL'}] Subsequent duplicate request rejected with HTTP 409`);

    if (testPassed) {
      console.log("\n✅ PRODUCTION ENDPOINT CONCURRENCY & TRANSACTION TEST PASSED PERFECTLY!");
    } else {
      console.error("\n❌ CONCURRENCY TEST FAILED! Assertions were not met.");
    }

  } catch (err) {
    console.error(`\n❌ CONCURRENCY TEST ERROR:`, err.message);
  } finally {
    // Close test HTTP server if open
    if (serverInstance) {
      serverInstance.close();
    }

    if (testPassed) {
      // Cleanup ONLY test user records upon complete verification success
      console.log(`\n8. Test Passed: Cleaning up test user records (${testUserId})...`);
      await pool.query(`DELETE FROM public.user_feedback WHERE user_id = $1`, [testUserId]);
      await pool.query(`DELETE FROM public.user_scores WHERE user_id = $1`, [testUserId]);
      await pool.query(`DELETE FROM public.users WHERE id = $1`, [testUserId]);
      console.log(`- Test user cleanup completed successfully.`);
    } else {
      console.log(`\n⚠️ Test Failed or Incomplete: PRESERVING test user records for manual inspection.`);
      console.log(`- Preserved User ID: ${testUserId}`);
      console.log(`- You can inspect records for this user in PostgreSQL tables: public.users, public.user_scores, public.user_feedback.`);
    }

    await pool.end();
  }
}

runRealEndpointConcurrencyTest().catch(console.error);
