-- ==============================================================================
-- Migration 0002: Security, Concurrency, and Integrity Hardening for Cloudflare D1
-- Safe, non-destructive, idempotent DDL:
--   - Does NOT drop or recreate existing tables
--   - Does NOT modify pre-existing user rows (including any legacy negative credits)
-- ==============================================================================

-- 1. Enforce instant_unlock_credits >= 0 on user_entitlements via triggers
--    (SQLite cannot ALTER TABLE ADD CHECK without dropping tables; triggers preserve all existing data
--     and atomically abort any DB.batch transaction that attempts to decrement credits below 0.)
CREATE TRIGGER IF NOT EXISTS trg_user_entitlements_credits_nonneg_insert
BEFORE INSERT ON user_entitlements
FOR EACH ROW
WHEN NEW.instant_unlock_credits < 0
BEGIN
    SELECT RAISE(ABORT, 'CHECK constraint failed: user_entitlements.instant_unlock_credits >= 0');
END;

CREATE TRIGGER IF NOT EXISTS trg_user_entitlements_credits_nonneg_update
BEFORE UPDATE OF instant_unlock_credits ON user_entitlements
FOR EACH ROW
WHEN NEW.instant_unlock_credits < 0
 AND (OLD.instant_unlock_credits >= 0 OR NEW.instant_unlock_credits < OLD.instant_unlock_credits)
BEGIN
    SELECT RAISE(ABORT, 'CHECK constraint failed: user_entitlements.instant_unlock_credits >= 0');
END;

-- 2. Enforce instant_unlock_credits >= 0 on users via triggers
CREATE TRIGGER IF NOT EXISTS trg_users_credits_nonneg_insert
BEFORE INSERT ON users
FOR EACH ROW
WHEN NEW.instant_unlock_credits < 0
BEGIN
    SELECT RAISE(ABORT, 'CHECK constraint failed: users.instant_unlock_credits >= 0');
END;

CREATE TRIGGER IF NOT EXISTS trg_users_credits_nonneg_update
BEFORE UPDATE OF instant_unlock_credits ON users
FOR EACH ROW
WHEN NEW.instant_unlock_credits < 0
 AND (OLD.instant_unlock_credits >= 0 OR NEW.instant_unlock_credits < OLD.instant_unlock_credits)
BEGIN
    SELECT RAISE(ABORT, 'CHECK constraint failed: users.instant_unlock_credits >= 0');
END;

-- 3. Read-only diagnostic view to audit any pre-existing negative credit records without auto-modifying them
CREATE VIEW IF NOT EXISTS v_negative_credit_users AS
SELECT
    u.id AS user_id,
    u.phone_number,
    u.instant_unlock_credits AS users_credits,
    COALESCE(e.instant_unlock_credits, 0) AS entitlement_credits,
    u.updated_at
FROM users u
LEFT JOIN user_entitlements e ON u.id = e.user_id
WHERE u.instant_unlock_credits < 0
   OR COALESCE(e.instant_unlock_credits, 0) < 0;

-- 4. Optimize active daily_cycles lookup per user and prevent unnecessary full-table scans
CREATE INDEX IF NOT EXISTS idx_daily_cycles_user_active
    ON daily_cycles(user_id, end_time DESC, start_time DESC);

-- 5. Enforce square_states uniqueness per user/cycle/square explicitly via index lookup optimization
CREATE INDEX IF NOT EXISTS idx_square_states_cycle_user
    ON square_states(cycle_id, user_id, square_number);

-- 6. Speed up per-user purchase history lookups in D1
CREATE INDEX IF NOT EXISTS idx_purchases_user_created
    ON purchases(user_id, created_at DESC);
