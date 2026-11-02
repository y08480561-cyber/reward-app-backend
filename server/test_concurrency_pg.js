const { Pool } = require('pg');
const crypto = require('crypto');
require('dotenv').config();

// PostgreSQL Connection configuration
const connectionString = process.env.DATABASE_URL || 'postgres://localhost:5432/reqabat';

async function runRealPgConcurrencyTest() {
  console.log("=== Real PostgreSQL Concurrency & Transaction Test ===");
  const pool = new Pool({ connectionString, max: 10 });
  const randomSuffix = crypto.randomBytes(4).toString('hex');
  const testUserId = `TEST_CONC_${Date.now()}_${randomSuffix}`;
  let testPassed = false;

  try {
    // 1. Create independent test user with feedback_reward_claimed = false
    console.log(`\n1. Creating test user: ${testUserId} (initial score: 10)...`);
    await pool.query(`
      INSERT INTO public.users (id, first_name, last_name, phone_number, total_score, feedback_reward_claimed, status, created_at, updated_at)
      VALUES ($1, 'تست', 'همزمانی', '09990000000', 10, false, 'active', NOW(), NOW())
    `, [testUserId]);

    await pool.query(`
      INSERT INTO public.user_scores (user_id, total_score, updated_at)
      VALUES ($1, 10, NOW())
      ON CONFLICT (user_id) DO UPDATE SET total_score = 10
    `, [testUserId]);

    // Check initial scores
    const initialUsersRes = await pool.query(`SELECT total_score, feedback_reward_claimed FROM public.users WHERE id = $1`, [testUserId]);
    const initialScoresRes = await pool.query(`SELECT total_score FROM public.user_scores WHERE id = $1`, [testUserId]);

    console.log(`Initial Score in 'users': ${initialUsersRes.rows[0].total_score}`);
    console.log(`Initial Score in 'user_scores': ${initialScoresRes.rows[0].total_score}`);
    console.log(`Initial feedback_reward_claimed: ${initialUsersRes.rows[0].feedback_reward_claimed}`);

    // 2. Execute 5 concurrent feedback reward claims in parallel using separate pool connections
    console.log("\n2. Launching 5 concurrent feedback reward requests simultaneously...");
    const CONCURRENT_COUNT = 5;

    const executeClaimTask = async (taskIndex) => {
      const client = await pool.connect();
      try {
        await client.query('BEGIN');
        const updateRes = await client.query(`
          UPDATE public.users 
          SET feedback_reward_claimed = true, 
              total_score = total_score + 2, 
              updated_at = NOW() 
          WHERE id = $1 AND feedback_reward_claimed = false
          RETURNING total_score
        `, [testUserId]);

        if (updateRes.rows.length === 0) {
          await client.query('ROLLBACK');
          return { success: false, taskIndex, reason: "Already claimed or blocked" };
        }

        await client.query(`
          INSERT INTO public.user_feedback (user_id, rating, comment, created_at)
          VALUES ($1, 5, $2, NOW())
        `, [testUserId, `Concurrent test comment from task ${taskIndex}`]);

        const scoreUpdate = await client.query(`
          INSERT INTO public.user_scores (user_id, total_score, updated_at)
          VALUES ($1, 2, NOW())
          ON CONFLICT (user_id) 
          DO UPDATE SET total_score = public.user_scores.total_score + 2, updated_at = NOW()
          RETURNING total_score
        `, [testUserId]);

        await client.query('COMMIT');
        return { success: true, taskIndex, newScore: Number(scoreUpdate.rows[0].total_score) };
      } catch (err) {
        await client.query('ROLLBACK');
        return { success: false, taskIndex, reason: err.message };
      } finally {
        client.release();
      }
    };

    const tasks = Array.from({ length: CONCURRENT_COUNT }, (_, i) => executeClaimTask(i + 1));
    const results = await Promise.all(tasks);

    const successList = results.filter(r => r.success);
    const rejectedList = results.filter(r => !r.success);

    console.log(`\n3. Concurrency Execution Results:`);
    console.log(`- Total Requests Sent: ${CONCURRENT_COUNT}`);
    console.log(`- Successful Requests: ${successList.length}`);
    console.log(`- Rejected Requests: ${rejectedList.length}`);

    // 4. Check post-concurrency scores & feedback records
    const finalUsersRes = await pool.query(`SELECT total_score, feedback_reward_claimed FROM public.users WHERE id = $1`, [testUserId]);
    const finalScoresRes = await pool.query(`SELECT total_score FROM public.user_scores WHERE id = $1`, [testUserId]);
    const feedbackCountRes = await pool.query(`SELECT COUNT(*) AS count FROM public.user_feedback WHERE user_id = $1`, [testUserId]);

    const finalUsersScore = Number(finalUsersRes.rows[0].total_score);
    const finalScoresScore = Number(finalScoresRes.rows[0].total_score);
    const totalFeedbacks = Number(feedbackCountRes.rows[0].count);

    console.log(`\n4. Score & Database Verification:`);
    console.log(`- Score in 'users' (Before: 10 -> After: ${finalUsersScore})`);
    console.log(`- Score in 'user_scores' (Before: 10 -> After: ${finalScoresScore})`);
    console.log(`- Total Feedback Records in 'user_feedback': ${totalFeedbacks}`);
    console.log(`- 'feedback_reward_claimed' status: ${finalUsersRes.rows[0].feedback_reward_claimed}`);

    // 5. Duplicate Claim Test (Subsequent request)
    console.log(`\n5. Subsequent Duplicate Claim Test...`);
    const duplicateRes = await executeClaimTask(99);
    console.log(`- Duplicate Request Success: ${duplicateRes.success} (Reason: ${duplicateRes.reason})`);

    // Verification asserts
    testPassed = successList.length === 1 &&
                 rejectedList.length === (CONCURRENT_COUNT - 1) &&
                 finalUsersScore === 12 &&
                 finalScoresScore === 12 &&
                 totalFeedbacks === 1 &&
                 !duplicateRes.success;

    if (testPassed) {
      console.log("\n✅ ALL CONCURRENCY & TRANSACTION ASSERTS PASSED PERFECTLY!");
    } else {
      console.error("\n❌ CONCURRENCY ASSERTS FAILED!");
    }

  } finally {
    if (testPassed) {
      // Clean up test user completely on success
      console.log(`\n6. Cleaning up test user (${testUserId})...`);
      await pool.query(`DELETE FROM public.user_feedback WHERE user_id = $1`, [testUserId]);
      await pool.query(`DELETE FROM public.user_scores WHERE user_id = $1`, [testUserId]);
      await pool.query(`DELETE FROM public.users WHERE id = $1`, [testUserId]);
    } else {
      console.log(`\n⚠️ Test failed or incomplete — preserving test user data (${testUserId}) for manual inspection.`);
    }
    await pool.end();
  }
}

runRealPgConcurrencyTest().catch(console.error);
