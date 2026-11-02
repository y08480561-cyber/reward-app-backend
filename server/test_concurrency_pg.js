const http = require('http');
const crypto = require('crypto');
const express = require('express');
const { Pool } = require('pg');
require('dotenv').config();

// PostgreSQL Connection configuration
const connectionString = process.env.DATABASE_URL || 'postgres://localhost:5432/reqabat';

async function runRealEndpointConcurrencyTest() {
  console.log("=== Real Endpoint (/api/user/feedback) PostgreSQL Concurrency Test ===");
  const pool = new Pool({ connectionString, max: 15 });

  const randomHex = crypto.randomBytes(4).toString('hex');
  const testUserId = `TEST_CONC_${Date.now()}_${randomHex}`;
  const testPhoneNumber = `0999${Math.floor(10000000 + Math.random() * 89999999)}`;
  const testAuthToken = `TOKEN_TEST_${crypto.randomUUID()}`;
  let testPassed = false;
  let serverInstance = null;

  try {
    // 1. Create test user in public.users with valid auth_token
    console.log(`\n1. Creating test user in PostgreSQL...`);
    console.log(`- User ID: ${testUserId}`);
    console.log(`- Phone Number: ${testPhoneNumber}`);
    console.log(`- Auth Token: ${testAuthToken}`);
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

    // Verify initial DB state
    const initUserRes = await pool.query(`SELECT id, total_score, feedback_reward_claimed, auth_token FROM public.users WHERE id = $1`, [testUserId]);
    const initScoreRes = await pool.query(`SELECT user_id, total_score FROM public.user_scores WHERE user_id = $1`, [testUserId]);

    if (initUserRes.rows.length === 0 || initScoreRes.rows.length === 0) {
      throw new Error(`Failed to initialize test user in database.`);
    }

    console.log(`- Initial 'users' total_score: ${initUserRes.rows[0].total_score}`);
    console.log(`- Initial 'user_scores' total_score (where user_id = $1): ${initScoreRes.rows[0].total_score}`);
    console.log(`- Initial feedback_reward_claimed: ${initUserRes.rows[0].feedback_reward_claimed}`);

    // 2. Build local Express server mounting exact auth middleware & feedback route handler
    console.log(`\n2. Mounting Express test server with exact production auth middleware & feedback handler...`);
    const app = express();
    app.use(express.json());

    // Production Auth Middleware logic from server.js
    async function requireUserAuth(req, res, next) {
      try {
        const rawAuth = req.headers.authorization ? req.headers.authorization.replace(/^Bearer\s+/i, '').trim() : '';
        const token = req.headers['x-auth-token'] || req.headers['x-token'] || rawAuth || req.query.token;
        const headerUserId = req.headers['x-user-id'] || req.query.userId;

        if (!token && !headerUserId) {
          return res.status(401).json({ success: false, error: "توکن احراز هویت الزامی است." });
        }

        let userRow = null;
        if (token) {
          const tokenRes = await pool.query(
            `SELECT id, first_name, last_name, phone_number, status, auth_token FROM public.users WHERE auth_token = $1 LIMIT 1`,
            [token]
          );
          if (tokenRes.rows.length > 0) userRow = tokenRes.rows[0];
        }

        if (!userRow && headerUserId) {
          const userRes = await pool.query(
            `SELECT id, first_name, last_name, phone_number, status, auth_token FROM public.users WHERE id = $1 LIMIT 1`,
            [headerUserId]
          );
          if (userRes.rows.length > 0) userRow = userRes.rows[0];
        }

        if (!userRow) {
          return res.status(401).json({ success: false, error: "نشست کاربری نامعتبر است." });
        }

        req.user = {
          id: userRow.id,
          firstName: userRow.first_name || '',
          lastName: userRow.last_name || '',
          phoneNumber: userRow.phone_number || ''
        };
        next();
      } catch (err) {
        return res.status(500).json({ success: false, error: "خطای احراز هویت." });
      }
    }

    // Production Feedback Endpoint Handler from server.js
    app.post('/api/user/feedback', requireUserAuth, async (req, res) => {
      const client = await pool.connect();
      try {
        const userId = req.user.id;
        const { rating, comment } = req.body;

        if (!rating || rating < 1 || rating > 5) {
          return res.status(400).json({ error: "امتیاز وارد شده باید بین ۱ تا ۵ باشد." });
        }

        const REWARD_POINTS = 2;
        await client.query('BEGIN');

        const updateRes = await client.query(
          `UPDATE public.users 
           SET feedback_reward_claimed = true, 
               total_score = total_score + $1, 
               updated_at = NOW() 
           WHERE id = $2 AND feedback_reward_claimed = false
           RETURNING total_score`,
          [REWARD_POINTS, userId]
        );

        if (updateRes.rows.length === 0) {
          await client.query('ROLLBACK');
          const checkAgain = await client.query(`SELECT id, feedback_reward_claimed FROM public.users WHERE id = $1`, [userId]);
          if (checkAgain.rows.length === 0) {
            return res.status(404).json({ error: "کاربر یافت نشد." });
          } else {
            return res.status(409).json({ error: "پاداش بازخورد قبلاً توسط شما دریافت شده است." });
          }
        }

        await client.query(
          `INSERT INTO public.user_feedback (user_id, rating, comment, created_at) VALUES ($1, $2, $3, NOW())`,
          [userId, rating, comment || null]
        );

        const scoreUpdate = await client.query(
          `INSERT INTO public.user_scores (user_id, total_score, updated_at)
           VALUES ($1, $2, NOW())
           ON CONFLICT (user_id) 
           DO UPDATE SET total_score = public.user_scores.total_score + $3, updated_at = NOW()
           RETURNING total_score`,
          [userId, REWARD_POINTS, REWARD_POINTS]
        );

        const finalScore = Number(scoreUpdate.rows[0].total_score);
        await client.query('COMMIT');

        return res.json({
          success: true,
          message: "بازخورد شما با موفقیت ثبت شد و ۲ امتیاز هدیه دریافت کردید.",
          awardedPoints: REWARD_POINTS,
          newTotalScore: finalScore
        });
      } catch (err) {
        await client.query('ROLLBACK');
        return res.status(500).json({ error: "خطا در ثبت بازخورد." });
      } finally {
        client.release();
      }
    });

    const TEST_PORT = 3099;
    await new Promise((resolve) => {
      serverInstance = app.listen(TEST_PORT, '127.0.0.1', () => {
        console.log(`- Test HTTP server listening on http://127.0.0.1:${TEST_PORT}`);
        resolve();
      });
    });

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

    // 3. Send 5 concurrent requests
    console.log(`\n3. Sending 5 concurrent HTTP POST requests to /api/user/feedback...`);
    const CONCURRENT_COUNT = 5;
    const tasks = Array.from({ length: CONCURRENT_COUNT }, (_, i) => sendFeedbackApiRequest(i + 1));
    const responses = await Promise.all(tasks);

    // Exact HTTP status filtering
    const successful200List = responses.filter(r => r.statusCode === 200);
    const rejected409List = responses.filter(r => r.statusCode === 409);
    const unexpectedErrors = responses.filter(r => r.statusCode !== 200 && r.statusCode !== 409);

    console.log(`\n4. Concurrency HTTP Status Summary:`);
    console.log(`- Total Requests Sent: ${CONCURRENT_COUNT}`);
    console.log(`- HTTP 200 OK (Successful): ${successful200List.length}`);
    console.log(`- HTTP 409 Conflict (Rejected): ${rejected409List.length}`);
    console.log(`- Unexpected Errors (500 / Connection Failures): ${unexpectedErrors.length}`);

    responses.forEach(r => {
      console.log(`  Task #${r.taskIndex}: HTTP ${r.statusCode} -> ${JSON.stringify(r.body)}`);
    });

    // 4. Verify post-concurrency database state
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

    // 5. Test subsequent duplicate request
    console.log(`\n6. Testing subsequent duplicate claim attempt...`);
    const duplicateRes = await sendFeedbackApiRequest(99);
    console.log(`- Duplicate Request Status: HTTP ${duplicateRes.statusCode} -> ${JSON.stringify(duplicateRes.body)}`);

    // 6. Strict Assert Checklist
    const isSuccess200Valid = successful200List.length === 1;
    const isRejected409Valid = rejected409List.length === (CONCURRENT_COUNT - 1);
    const isNoUnexpectedErrors = unexpectedErrors.length === 0;
    const isUsersScoreValid = finalUsersScore === 12;
    const isScoresScoreValid = finalScoresScore === 12;
    const isFeedbackCountValid = feedbackRecordCount === 1;
    const isClaimedFlagValid = feedbackClaimedStatus === true;
    const isDuplicateBlocked409 = duplicateRes.statusCode === 409;

    testPassed = isSuccess200Valid &&
                 isRejected409Valid &&
                 isNoUnexpectedErrors &&
                 isUsersScoreValid &&
                 isScoresScoreValid &&
                 isFeedbackCountValid &&
                 isClaimedFlagValid &&
                 isDuplicateBlocked409;

    console.log(`\n7. Strict Assertion Checklist:`);
    console.log(`  [${isSuccess200Valid ? 'PASS' : 'FAIL'}] Exactly 1 HTTP 200 Success`);
    console.log(`  [${isRejected409Valid ? 'PASS' : 'FAIL'}] Exactly ${CONCURRENT_COUNT - 1} HTTP 409 Rejected`);
    console.log(`  [${isNoUnexpectedErrors ? 'PASS' : 'FAIL'}] Zero unexpected errors (HTTP 500 / Network)`);
    console.log(`  [${isUsersScoreValid ? 'PASS' : 'FAIL'}] 'users.total_score' increased by exactly +2 (10 -> ${finalUsersScore})`);
    console.log(`  [${isScoresScoreValid ? 'PASS' : 'FAIL'}] 'user_scores.total_score' increased by exactly +2 (10 -> ${finalScoresScore})`);
    console.log(`  [${isFeedbackCountValid ? 'PASS' : 'FAIL'}] Exactly 1 feedback record inserted in 'user_feedback'`);
    console.log(`  [${isClaimedFlagValid ? 'PASS' : 'FAIL'}] 'feedback_reward_claimed' set to true`);
    console.log(`  [${isDuplicateBlocked409 ? 'PASS' : 'FAIL'}] Subsequent duplicate request rejected with HTTP 409`);

    if (testPassed) {
      console.log("\n✅ ALL CONCURRENCY & TRANSACTION ASSERTS PASSED PERFECTLY!");
    } else {
      console.error("\n❌ CONCURRENCY TEST FAILED! Assertions failed.");
    }

  } catch (err) {
    console.error(`\n❌ CONCURRENCY TEST EXCEPTION:`, err.message);
    testPassed = false;
  } finally {
    if (serverInstance) serverInstance.close();

    if (testPassed) {
      console.log(`\n8. Test Passed: Cleaning up test user records (${testUserId})...`);
      await pool.query(`DELETE FROM public.user_feedback WHERE user_id = $1`, [testUserId]);
      await pool.query(`DELETE FROM public.user_scores WHERE user_id = $1`, [testUserId]);
      await pool.query(`DELETE FROM public.users WHERE id = $1`, [testUserId]);
      console.log(`- Test user cleanup completed successfully.`);
      await pool.end();
    } else {
      console.log(`\n⚠️ TEST FAILED: PRESERVING test user records for manual inspection.`);
      console.log(`- Preserved User ID: ${testUserId}`);
      console.log(`- Preserved Phone Number: ${testPhoneNumber}`);
      await pool.end();
      // Exit process with non-zero code on failure
      process.exit(1);
    }
  }
}

runRealEndpointConcurrencyTest();
