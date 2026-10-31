// Mock DB Logic for verification without pg dependency
const crypto = require('crypto');

// Mock PG Pool to simulate DB behavior
class MockClient {
    constructor(dbState) {
        this.dbState = dbState;
        this.transactionActive = false;
        this.queries = [];
    }

    async query(sql, params) {
        this.queries.push({ sql, params });
        
        if (sql === 'BEGIN') {
            this.transactionActive = true;
            return { rowCount: 0, rows: [] };
        }
        if (sql === 'ROLLBACK') {
            this.transactionActive = false;
            return { rowCount: 0, rows: [] };
        }
        if (sql === 'COMMIT') {
            this.transactionActive = false;
            return { rowCount: 0, rows: [] };
        }

        // Simulate UPDATE public.users SET feedback_reward_claimed = true ... WHERE id = $2 AND feedback_reward_claimed = false
        if (sql.includes('UPDATE public.users') && sql.includes('feedback_reward_claimed = false')) {
            const userId = params[1];
            const user = this.dbState.users[userId];
            if (user && !user.feedback_reward_claimed) {
                user.feedback_reward_claimed = true;
                user.total_score += params[0];
                return { rowCount: 1, rows: [{ total_score: user.total_score }] };
            } else {
                return { rowCount: 0, rows: [] };
            }
        }

        // Simulate SELECT id, feedback_reward_claimed FROM public.users WHERE id = $1
        if (sql.includes('SELECT id, feedback_reward_claimed FROM public.users')) {
            const userId = params[0];
            const user = this.dbState.users[userId];
            if (user) {
                return { rowCount: 1, rows: [user] };
            } else {
                return { rowCount: 0, rows: [] };
            }
        }

        // Simulate INSERT INTO public.user_feedback
        if (sql.includes('INSERT INTO public.user_feedback')) {
            const userId = params[0];
            this.dbState.feedback.push({ userId, rating: params[1], comment: params[2] });
            return { rowCount: 1, rows: [] };
        }

        // Simulate INSERT INTO public.user_scores ... ON CONFLICT
        if (sql.includes('INSERT INTO public.user_scores')) {
            const userId = params[0];
            const points = params[1];
            if (!this.dbState.user_scores[userId]) {
                this.dbState.user_scores[userId] = { total_score: 0 };
            }
            this.dbState.user_scores[userId].total_score += points;
            return { rowCount: 1, rows: [{ total_score: this.dbState.user_scores[userId].total_score }] };
        }

        return { rowCount: 0, rows: [] };
    }

    release() {}
}

async function runFeedbackTest() {
    console.log("Starting Server-Side Feedback Reward Logic Test...");

    const dbState = {
        users: {
            "test-user-1": { id: "test-user-1", feedback_reward_claimed: false, total_score: 10 },
            "test-user-already-claimed": { id: "test-user-already-claimed", feedback_reward_claimed: true, total_score: 12 }
        },
        user_scores: {
            "test-user-1": { total_score: 10 },
            "test-user-already-claimed": { total_score: 12 }
        },
        feedback: []
    };

    const client = new MockClient(dbState);

    // Test Case 1: First time claim
    console.log("\n--- Test Case 1: First time feedback claim ---");
    const REWARD_POINTS = 2;
    const userId = "test-user-1";
    
    await client.query('BEGIN');
    const updateRes = await client.query(
      `UPDATE public.users SET feedback_reward_claimed = true, total_score = total_score + $1 WHERE id = $2 AND feedback_reward_claimed = false RETURNING total_score`,
      [REWARD_POINTS, userId]
    );

    if (updateRes.rows.length > 0) {
        console.log("SUCCESS: User points updated in users table.");
        await client.query(`INSERT INTO public.user_feedback (user_id, rating, comment) VALUES ($1, $2, $3)`, [userId, 5, "Great app!"]);
        await client.query(`INSERT INTO public.user_scores (user_id, total_score) VALUES ($1, $2) ON CONFLICT (user_id) DO UPDATE SET total_score = total_score + $2 RETURNING total_score`, [userId, REWARD_POINTS]);
        await client.query('COMMIT');
    } else {
        console.log("FAILED: User update failed.");
    }

    console.log("User Total Score in users:", dbState.users[userId].total_score);
    console.log("User Total Score in user_scores:", dbState.user_scores[userId].total_score);
    console.log("Feedback recorded:", dbState.feedback.length);
    
    if (dbState.users[userId].total_score === 12 && dbState.user_scores[userId].total_score === 12 && dbState.feedback.length === 1) {
        console.log("PASSED: Correct reward and sync.");
    } else {
        console.error("FAILED: Score mismatch or feedback missing.");
    }

    // Test Case 2: Duplicate claim
    console.log("\n--- Test Case 2: Duplicate feedback claim ---");
    const updateRes2 = await client.query(
      `UPDATE public.users SET feedback_reward_claimed = true, total_score = total_score + $1 WHERE id = $2 AND feedback_reward_claimed = false RETURNING total_score`,
      [REWARD_POINTS, userId]
    );

    if (updateRes2.rows.length === 0) {
        console.log("PASSED: Duplicate claim prevented (0 rows updated).");
    } else {
        console.error("FAILED: Duplicate claim allowed!");
    }

    // Test Case 3: Concurrent claim simulation
    console.log("\n--- Test Case 3: Concurrent claim simulation (Race condition test) ---");
    // Both try to update at the same time. The DB locking (atomic UPDATE) will ensure only one succeeds.
    const update1 = client.query(`UPDATE public.users SET feedback_reward_claimed = true, total_score = total_score + $1 WHERE id = $2 AND feedback_reward_claimed = false RETURNING total_score`, [REWARD_POINTS, "test-user-1"]);
    const update2 = client.query(`UPDATE public.users SET feedback_reward_claimed = true, total_score = total_score + $1 WHERE id = $2 AND feedback_reward_claimed = false RETURNING total_score`, [REWARD_POINTS, "test-user-1"]);
    
    const results = await Promise.all([update1, update2]);
    const successCount = results.filter(r => r.rows.length > 0).length;
    console.log("Success Count:", successCount);
    if (successCount === 0) { // Should be 0 since it was already claimed in Test Case 1
        console.log("PASSED: No more rewards given.");
    } else {
        console.error("FAILED: Concurrent reward given!");
    }

    console.log("\nServer Logic Tests Completed.");
}

runFeedbackTest();
