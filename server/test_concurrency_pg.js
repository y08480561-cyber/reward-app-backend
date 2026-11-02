const http = require('http');
const crypto = require('crypto');
const { Pool } = require('pg');

// 1. Load environment variables before requiring main application
require('dotenv').config();

async function runRealEndpointConcurrencyTest() {
  console.log("=== Production Endpoint (/api/user/feedback) PostgreSQL Concurrency Test ===");

  // 2. Strict Safety Guard: Require explicit, valid TEST_DATABASE_URL to protect production database
  const testDbUrl = process.env.TEST_DATABASE_URL;
  const prodDbUrl = process.env.DATABASE_URL;

  if (!testDbUrl || typeof testDbUrl !== 'string' || testDbUrl.trim() === '') {
    console.error("\n❌ SAFETY GUARD STOPPED EXECUTION:");
    console.error("The concurrency test CANNOT run without an explicit 'TEST_DATABASE_URL' environment variable.");
    console.error("To protect your production database, please set 'TEST_DATABASE_URL' in your .env file or environment.");
    console.error("Example:");
    console.error("  TEST_DATABASE_URL=postgres://user:pass@test-host:5432/test_db node server/test_concurrency_pg.js\n");
    process.exitCode = 1;
    return;
  }

  if (prodDbUrl && testDbUrl.trim() === prodDbUrl.trim()) {
    console.error("\n❌ SAFETY GUARD STOPPED EXECUTION:");
    console.error("'TEST_DATABASE_URL' is identical to 'DATABASE_URL' (Production/Shared DB).");
    console.error("Execution is blocked to prevent running test mutations on the production database.");
    console.error("Please specify a separate, dedicated test database URL in 'TEST_DATABASE_URL'.\n");
    process.exitCode = 1;
    return;
  }

  // 3. Import production Express app (imports /server/server.js directly without rewriting handlers)
  // Since TEST_DATABASE_URL is set in process.env prior to import, server.js initializes pool on TEST_DATABASE_URL
  const appModule = require('./server');
  const app = appModule.app || appModule;
  const pool = (appModule.getPool && appModule.getPool()) || new Pool({ connectionString: testDbUrl });

  const randomHex = crypto.randomBytes(4).toString('hex');
  const testUserId = `TEST_CONC_${Date.now()}_${randomHex}`;
  const testPhoneNumber = `0999${Math.floor(10000000 + Math.random() * 89999999)}`;
  const testAuthToken = `TOKEN_TEST_${crypto.randomUUID()}`;

  let testPassed = false;
  let serverInstance = null;

  // Individual Assertion Checklist Statuses
  const assertResults = {
    exactOneSuccess200: false,
    exactFourRejected409: false,
    zeroUnexpectedErrors: false,
    usersScoreIncreasedBy2: false,
    userScoresScoreIncreasedBy2: false,
    feedbackRecordInserted: false,
    rewardClaimedFlagTrue: false,
    subsequentDuplicateBlocked409: false,
    rejectedRequestsDidNotIncreaseScore: false
  };

  try {
    // 4. Create independent test user in PostgreSQL test database
    console.log(`\n1. Initializing test user in test database...`);
    console.log(`- Test User ID: ${testUserId}`);
    console.log(`- Test Phone Number: ${testPhoneNumber}`);
    console.log(`- Initial Score: 10`);

    await pool.query(`
      INSERT INTO public.users (
        id, first_name, last_name, phone_number, auth_token, total_score, feedback_reward_claimed, status, created_at, updated_at
      ) VALUES ($1, 'تست', 'همزمانی', $2, $3, 10, false, 'active', NOW(), NOW())
    `, [testUserId, testPhoneNumber, testAuthToken]);

    await pool.query(`
      INSERT INTO public.user_scores (user_id, total_score, updated_at)
      VALUES ($1, 10, NOW())
      ON CONFLICT (user_id) DO UPDATE SET total_score = 10, updated_at = NOW()
    `, [testUserId]);

    // Verify initial DB state using user_id for user_scores table
    const initUserRes = await pool.query(`SELECT id, total_score, feedback_reward_claimed FROM public.users WHERE id = $1`, [testUserId]);
    const initScoreRes = await pool.query(`SELECT user_id, total_score FROM public.user_scores WHERE user_id = $1`, [testUserId]);

    if (initUserRes.rows.length === 0 || initScoreRes.rows.length === 0) {
      throw new Error(`Failed to initialize test user in PostgreSQL test database.`);
    }

    console.log(`- Initial 'users' total_score: ${initUserRes.rows[0].total_score}`);
    console.log(`- Initial 'user_scores' total_score (where user_id = $1): ${initScoreRes.rows[0].total_score}`);
    console.log(`- Initial feedback_reward_claimed: ${initUserRes.rows[0].feedback_reward_claimed}`);

    // 5. Start temporary Express listener on dynamic free port (port 0) on 127.0.0.1
    console.log(`\n2. Starting test listener on dynamic free port (127.0.0.1:0)...`);
    let testPort = 0;

    await new Promise((resolve, reject) => {
      serverInstance = app.listen(0, '127.0.0.1', () => {
        testPort = serverInstance.address().port;
        console.log(`- Production Express app test listener running on http://127.0.0.1:${testPort}`);
        resolve();
      });
      serverInstance.on('error', reject);
    });

    // Helper to send HTTP POST request to imported production Express app
    const sendFeedbackApiRequest = (taskIndex) => {
      return new Promise((resolve) => {
        const postData = JSON.stringify({
          rating: 5,
          comment: `Concurrent API test comment from task ${taskIndex}`
        });

        const options = {
          hostname: '127.0.0.1',
          port: testPort,
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

    // 6. Send 5 concurrent HTTP POST requests simultaneously against production endpoint
    console.log(`\n3. Sending 5 concurrent HTTP POST requests to production /api/user/feedback...`);
    const CONCURRENT_COUNT = 5;
    const tasks = Array.from({ length: CONCURRENT_COUNT }, (_, i) => sendFeedbackApiRequest(i + 1));
    const responses = await Promise.all(tasks);

    // Exact HTTP status filtering
    const successful200List = responses.filter(r => r.statusCode === 200);
    const rejected409List = responses.filter(r => r.statusCode === 409);
    const unexpectedErrors = responses.filter(r => r.statusCode !== 200 && r.statusCode !== 409);

    console.log(`\n4. Concurrency Response Summary:`);
    console.log(`- Total Parallel Requests Sent: ${CONCURRENT_COUNT}`);
    console.log(`- HTTP 200 OK (Successful): ${successful200List.length}`);
    console.log(`- HTTP 409 Conflict (Rejected): ${rejected409List.length}`);
    console.log(`- Unexpected Errors: ${unexpectedErrors.length}`);

    responses.forEach(r => {
      console.log(`  Task #${r.taskIndex}: HTTP ${r.statusCode} -> ${JSON.stringify(r.body)}`);
    });

    // 7. Verify Database State Post-Concurrency
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

    // 8. Test subsequent duplicate request
    console.log(`\n6. Testing subsequent duplicate claim attempt...`);
    const duplicateRes = await sendFeedbackApiRequest(99);
    console.log(`- Duplicate Request Status: HTTP ${duplicateRes.statusCode} -> ${JSON.stringify(duplicateRes.body)}`);

    // 9. Evaluate Assertions
    assertResults.exactOneSuccess200 = successful200List.length === 1;
    assertResults.exactFourRejected409 = rejected409List.length === (CONCURRENT_COUNT - 1);
    assertResults.zeroUnexpectedErrors = unexpectedErrors.length === 0;
    assertResults.usersScoreIncreasedBy2 = finalUsersScore === 12;
    assertResults.userScoresScoreIncreasedBy2 = finalScoresScore === 12;
    assertResults.feedbackRecordInserted = feedbackRecordCount === 1;
    assertResults.rewardClaimedFlagTrue = feedbackClaimedStatus === true;
    assertResults.subsequentDuplicateBlocked409 = duplicateRes.statusCode === 409;
    assertResults.rejectedRequestsDidNotIncreaseScore = (finalUsersScore === 12 && feedbackRecordCount === 1);

    testPassed = Object.values(assertResults).every(res => res === true);

    console.log(`\n7. Detailed Assertion Results:`);
    console.log(`- Condition 1: Exactly 1 HTTP 200 response -> [${assertResults.exactOneSuccess200 ? 'PASS' : 'FAIL'}]`);
    console.log(`- Condition 2: Exactly 4 HTTP 409 responses -> [${assertResults.exactFourRejected409 ? 'PASS' : 'FAIL'}]`);
    console.log(`- Condition 3: Zero unexpected errors/HTTP 500 -> [${assertResults.zeroUnexpectedErrors ? 'PASS' : 'FAIL'}]`);
    console.log(`- Condition 4: 'users.total_score' increased 10 -> 12 -> [${assertResults.usersScoreIncreasedBy2 ? 'PASS' : 'FAIL'}]`);
    console.log(`- Condition 5: 'user_scores.total_score' increased 10 -> 12 -> [${assertResults.userScoresScoreIncreasedBy2 ? 'PASS' : 'FAIL'}]`);
    console.log(`- Condition 6: Exactly 1 record in 'user_feedback' -> [${assertResults.feedbackRecordInserted ? 'PASS' : 'FAIL'}]`);
    console.log(`- Condition 7: 'feedback_reward_claimed' = true -> [${assertResults.rewardClaimedFlagTrue ? 'PASS' : 'FAIL'}]`);
    console.log(`- Condition 8: Subsequent duplicate request returns HTTP 409 -> [${assertResults.subsequentDuplicateBlocked409 ? 'PASS' : 'FAIL'}]`);
    console.log(`- Condition 9: Rejected requests caused zero extra points -> [${assertResults.rejectedRequestsDidNotIncreaseScore ? 'PASS' : 'FAIL'}]`);

    if (testPassed) {
      console.log("\n✅ ALL CONCURRENCY & TRANSACTION ASSERTS PASSED PERFECTLY!");
    } else {
      console.error("\n❌ CONCURRENCY TEST FAILED!");
    }

  } catch (err) {
    console.error(`\n❌ CONCURRENCY TEST EXCEPTION:`, err.message);
    testPassed = false;
  } finally {
    // 10. Graceful resource closing & safe cleanup in all cases
    if (serverInstance) {
      try {
        await new Promise(resolve => serverInstance.close(resolve));
        console.log(`\n- Test HTTP server listener closed.`);
      } catch (srvErr) {
        console.error(`⚠️ Error closing test HTTP server:`, srvErr.message);
      }
    }

    if (testUserId && pool) {
      try {
        console.log(`\nCleanup: Safely removing test user records (${testUserId})...`);
        await pool.query(`DELETE FROM public.user_feedback WHERE user_id = $1`, [testUserId]);
        await pool.query(`DELETE FROM public.user_scores WHERE user_id = $1`, [testUserId]);
        await pool.query(`DELETE FROM public.users WHERE id = $1`, [testUserId]);
        console.log(`- Test user cleanup completed successfully.`);
      } catch (cleanupErr) {
        console.error(`⚠️ Cleanup error for test user (${testUserId}):`, cleanupErr.message);
      }
    }

    if (pool) {
      try {
        await pool.end();
        console.log(`- PostgreSQL pool closed.`);
      } catch (poolErr) {
        console.error(`⚠️ Error closing PostgreSQL pool:`, poolErr.message);
      }
    }

    process.exitCode = testPassed ? 0 : 1;
  }
}

runRealEndpointConcurrencyTest();
