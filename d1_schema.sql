-- ==============================================================================
-- Cloudflare D1 (SQLite) Complete Database Schema
-- Project: رقابت کن، جایزه ببر (reward-app-backend)
-- Database: reward-app-db (binding: DB)
-- Pure DDL only (NO INSERT / UPDATE / DELETE / SEED data statements)
-- Ordered strictly by foreign-key dependencies (parent tables before children)
-- ==============================================================================

PRAGMA foreign_keys = ON;

-- ------------------------------------------------------------------------------
-- 1. جدول کاربران (Users)
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS users (
    id TEXT PRIMARY KEY,
    first_name TEXT NOT NULL DEFAULT '',
    last_name TEXT NOT NULL DEFAULT '',
    phone_number TEXT NOT NULL UNIQUE,
    total_score INTEGER NOT NULL DEFAULT 0,
    status TEXT NOT NULL DEFAULT 'active',
    auth_method TEXT DEFAULT 'DIRECT_CREDENTIALS',
    password_hash TEXT,
    auth_token TEXT,
    instant_unlock_credits INTEGER NOT NULL DEFAULT 0,
    is_vip INTEGER NOT NULL DEFAULT 0 CHECK (is_vip IN (0, 1)),
    account_tier TEXT NOT NULL DEFAULT 'NORMAL',
    panel_type TEXT NOT NULL DEFAULT 'free',
    multiplier INTEGER NOT NULL DEFAULT 1,
    feedback_reward_claimed INTEGER NOT NULL DEFAULT 0 CHECK (feedback_reward_claimed IN (0, 1)),
    created_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
    updated_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now'))
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_users_phone_unique ON users(phone_number);
CREATE INDEX IF NOT EXISTS idx_users_score ON users(total_score DESC);
CREATE INDEX IF NOT EXISTS idx_users_auth_token ON users(auth_token);

-- ------------------------------------------------------------------------------
-- 2. جدول امتیازات اختصاصی کاربران (User Scores)
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS user_scores (
    user_id TEXT PRIMARY KEY,
    total_score INTEGER NOT NULL DEFAULT 0 CHECK (total_score >= 0),
    updated_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now'))
);

CREATE INDEX IF NOT EXISTS idx_user_scores_total ON user_scores(total_score DESC);

-- ------------------------------------------------------------------------------
-- 3. جدول دوره‌های ۲۴ ساعته روزانه (Daily Cycles)
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS daily_cycles (
    cycle_id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL,
    start_time INTEGER NOT NULL,
    end_time INTEGER NOT NULL,
    status TEXT NOT NULL DEFAULT 'ACTIVE',
    created_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now'))
);

CREATE INDEX IF NOT EXISTS idx_daily_cycles_user ON daily_cycles(user_id, start_time, end_time);

-- ------------------------------------------------------------------------------
-- 4. جدول وضعیت ۱۰۰ خانه مسابقه برای هر کاربر (Square States)
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS square_states (
    state_key TEXT PRIMARY KEY,
    user_id TEXT NOT NULL,
    cycle_id TEXT NOT NULL,
    square_number INTEGER NOT NULL CHECK (square_number BETWEEN 1 AND 100),
    status TEXT NOT NULL DEFAULT 'COMPLETED',
    completed_at INTEGER NOT NULL,
    created_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now'))
);

CREATE INDEX IF NOT EXISTS idx_square_states_lookup ON square_states(user_id, cycle_id, square_number);

-- ------------------------------------------------------------------------------
-- 5. جدول مسابقات ادواری (Competitions)
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS competitions (
    id TEXT PRIMARY KEY,
    competition_number INTEGER NOT NULL,
    title TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'ACTIVE',
    started_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
    ended_at TEXT,
    total_participants INTEGER NOT NULL DEFAULT 0,
    total_score INTEGER NOT NULL DEFAULT 0,
    created_by TEXT NOT NULL DEFAULT 'admin',
    created_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now'))
);

CREATE INDEX IF NOT EXISTS idx_competitions_status ON competitions(status);
CREATE INDEX IF NOT EXISTS idx_competitions_number ON competitions(competition_number);

-- ------------------------------------------------------------------------------
-- 6. جدول نشست‌های تبلیغاتی تپسل و اعتبارسنجی پاداش (Ad Sessions)
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ad_sessions (
    session_id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL,
    square_number INTEGER NOT NULL CHECK (square_number BETWEEN 1 AND 100),
    cycle_id TEXT NOT NULL,
    timestamp INTEGER NOT NULL,
    ad_duration_seconds INTEGER NOT NULL,
    status TEXT NOT NULL DEFAULT 'COMPLETED',
    token TEXT UNIQUE,
    competition_id TEXT,
    created_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now'))
);

CREATE INDEX IF NOT EXISTS idx_ad_sessions_user ON ad_sessions(user_id);
CREATE INDEX IF NOT EXISTS idx_ad_sessions_token ON ad_sessions(token);
CREATE INDEX IF NOT EXISTS idx_ad_sessions_competition ON ad_sessions(competition_id);

-- ------------------------------------------------------------------------------
-- 7. جدول اشتراک و سطوح کاربری پنل‌ها (User Memberships)
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS user_memberships (
    user_id TEXT NOT NULL,
    cycle_id TEXT NOT NULL,
    panel_type TEXT NOT NULL DEFAULT 'free',
    multiplier INTEGER NOT NULL DEFAULT 1,
    purchased_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
    PRIMARY KEY (user_id, cycle_id)
);

-- ------------------------------------------------------------------------------
-- 8. جدول بسته‌های فروشگاهی (Store Products)
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS store_products (
    id TEXT PRIMARY KEY,
    title TEXT NOT NULL,
    description TEXT NOT NULL,
    product_type TEXT NOT NULL DEFAULT 'SQUARE_UNLOCK',
    units_granted INTEGER NOT NULL DEFAULT 0,
    price_toman INTEGER NOT NULL DEFAULT 0,
    myket_sku TEXT NOT NULL,
    badge TEXT DEFAULT '',
    is_active INTEGER NOT NULL DEFAULT 1 CHECK (is_active IN (0, 1)),
    is_ready_in_myket INTEGER NOT NULL DEFAULT 0 CHECK (is_ready_in_myket IN (0, 1)),
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
    updated_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now'))
);

CREATE INDEX IF NOT EXISTS idx_store_products_active ON store_products(is_active, sort_order ASC);

-- ------------------------------------------------------------------------------
-- 9. جدول تراکنش‌ها و خریدهای مایکت (Purchases)
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS purchases (
    purchase_id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL,
    product_id TEXT NOT NULL,
    amount INTEGER NOT NULL,
    status TEXT NOT NULL DEFAULT 'SUCCESS',
    purchase_token TEXT,
    myket_order_id TEXT,
    units_granted INTEGER DEFAULT 0,
    error_message TEXT,
    verification_source TEXT DEFAULT 'MYKET_IAB',
    created_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now'))
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_purchases_token
    ON purchases(purchase_token)
    WHERE purchase_token IS NOT NULL;

-- ------------------------------------------------------------------------------
-- 10. جدول مزایا و اعتبارات حساب کاربری (User Entitlements)
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS user_entitlements (
    user_id TEXT PRIMARY KEY,
    instant_unlock_credits INTEGER NOT NULL DEFAULT 0,
    is_vip INTEGER NOT NULL DEFAULT 0 CHECK (is_vip IN (0, 1)),
    total_purchased_toman INTEGER NOT NULL DEFAULT 0,
    account_tier TEXT NOT NULL DEFAULT 'NORMAL',
    multiplier INTEGER NOT NULL DEFAULT 1,
    updated_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now'))
);

-- ------------------------------------------------------------------------------
-- 11. جدول آرشیو مسابقات پایان‌یافته (Competition Archives)
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS competition_archives (
    id TEXT PRIMARY KEY,
    competition_id TEXT NOT NULL REFERENCES competitions(id) ON DELETE CASCADE,
    user_id TEXT NOT NULL,
    final_score INTEGER NOT NULL DEFAULT 0,
    final_rank INTEGER NOT NULL DEFAULT 0,
    first_name TEXT DEFAULT '',
    last_name TEXT DEFAULT '',
    phone_number TEXT DEFAULT '',
    archived_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now'))
);

CREATE INDEX IF NOT EXISTS idx_comp_archives_lookup ON competition_archives(competition_id, final_rank);
CREATE INDEX IF NOT EXISTS idx_comp_archives_user ON competition_archives(competition_id, user_id);

-- ------------------------------------------------------------------------------
-- 12. جدول لاگ‌های امنیتی و تغییرات مسابقات (Competition Logs)
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS competition_logs (
    log_id TEXT PRIMARY KEY,
    action TEXT NOT NULL,
    admin_key_used TEXT NOT NULL,
    prev_competition_id TEXT,
    new_competition_id TEXT,
    prev_title TEXT,
    new_title TEXT,
    participants_count INTEGER DEFAULT 0,
    total_score_archived INTEGER DEFAULT 0,
    details TEXT,
    created_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now'))
);

CREATE INDEX IF NOT EXISTS idx_comp_logs_created ON competition_logs(created_at DESC);

-- ------------------------------------------------------------------------------
-- 13. جدول اعلانات سراسری صفحه اصلی (Home Announcements)
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS home_announcements (
    id TEXT PRIMARY KEY,
    title TEXT NOT NULL,
    content TEXT NOT NULL,
    type TEXT NOT NULL DEFAULT 'ANNOUNCEMENT',
    is_active INTEGER NOT NULL DEFAULT 1 CHECK (is_active IN (0, 1)),
    competition_id TEXT REFERENCES competitions(id) ON DELETE SET NULL,
    starts_at TEXT,
    ends_at TEXT,
    created_by TEXT NOT NULL DEFAULT 'admin',
    created_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
    updated_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now'))
);

CREATE INDEX IF NOT EXISTS idx_announcements_active ON home_announcements(is_active, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_announcements_comp ON home_announcements(competition_id);

-- ------------------------------------------------------------------------------
-- 14. جدول تیکت‌های پشتیبانی کاربران (Support Tickets)
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS support_tickets (
    id TEXT PRIMARY KEY,
    tracking_code TEXT NOT NULL UNIQUE,
    user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    subject TEXT NOT NULL,
    category TEXT NOT NULL DEFAULT 'OTHER',
    status TEXT NOT NULL DEFAULT 'NEW',
    created_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
    updated_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
    last_message_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now'))
);

CREATE INDEX IF NOT EXISTS idx_support_tickets_user ON support_tickets(user_id, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_support_tickets_status ON support_tickets(status, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_support_tickets_tracking ON support_tickets(tracking_code);

-- ------------------------------------------------------------------------------
-- 15. جدول پیام‌های درون هر تیکت پشتیبانی (Support Messages)
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS support_messages (
    id TEXT PRIMARY KEY,
    ticket_id TEXT NOT NULL REFERENCES support_tickets(id) ON DELETE CASCADE,
    sender_type TEXT NOT NULL CHECK (sender_type IN ('USER', 'ADMIN')),
    sender_id TEXT NOT NULL,
    sender_name TEXT NOT NULL,
    message_text TEXT NOT NULL,
    created_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now'))
);

CREATE INDEX IF NOT EXISTS idx_support_messages_ticket ON support_messages(ticket_id, created_at ASC);

-- ------------------------------------------------------------------------------
-- 16. جدول دسته‌بندی جوایز (Reward Categories)
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS reward_categories (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    slug TEXT NOT NULL UNIQUE,
    icon TEXT DEFAULT 'gift',
    sort_order INTEGER NOT NULL DEFAULT 0,
    is_active INTEGER NOT NULL DEFAULT 1 CHECK (is_active IN (0, 1)),
    created_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now'))
);

CREATE INDEX IF NOT EXISTS idx_reward_categories_active ON reward_categories(is_active, sort_order ASC);

-- ------------------------------------------------------------------------------
-- 17. جدول جوایز (Rewards)
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS rewards (
    id TEXT PRIMARY KEY,
    title TEXT NOT NULL,
    description TEXT DEFAULT '',
    category_id TEXT REFERENCES reward_categories(id) ON DELETE SET NULL,
    cost_points INTEGER NOT NULL CHECK (cost_points >= 0),
    stock_quantity INTEGER NOT NULL DEFAULT 0 CHECK (stock_quantity >= 0),
    image_url TEXT DEFAULT '',
    additional_images TEXT DEFAULT '[]' CHECK (additional_images IS NULL OR json_valid(additional_images)),
    is_featured INTEGER NOT NULL DEFAULT 0 CHECK (is_featured IN (0, 1)),
    is_active INTEGER NOT NULL DEFAULT 1 CHECK (is_active IN (0, 1)),
    sort_order INTEGER NOT NULL DEFAULT 0,
    deleted_at TEXT,
    created_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
    updated_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now'))
);

CREATE INDEX IF NOT EXISTS idx_rewards_active ON rewards(is_active, sort_order ASC);
CREATE INDEX IF NOT EXISTS idx_rewards_category ON rewards(category_id);
CREATE INDEX IF NOT EXISTS idx_rewards_featured ON rewards(is_featured);

-- ------------------------------------------------------------------------------
-- 18. جدول تنظیمات ظاهری، متن‌ها و بنر ویترین جوایز (Reward Appearance)
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS reward_appearance (
    id TEXT PRIMARY KEY DEFAULT 'default',
    hero_title TEXT NOT NULL DEFAULT 'ویترین جوایز و پاداش‌های ارزشمند',
    hero_subtitle TEXT DEFAULT 'امتیازهای کسب‌شده خود در مسابقات را به کارت شارژ، بسته‌های اینترنت و جوایز نقدی تبدیل کنید',
    hero_banner_url TEXT DEFAULT '',
    primary_color TEXT DEFAULT '#10B981',
    active_sections TEXT DEFAULT '{"featured": true, "categories": true, "history": true, "banners": true}' CHECK (active_sections IS NULL OR json_valid(active_sections)),
    terms_text TEXT DEFAULT 'درخواست‌های دریافت جایزه پس از بررسی توسط تیم پشتیبانی ظرف ۲۴ الی ۷۲ ساعت کاری پردازش و ارسال خواهند شد.',
    updated_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now'))
);

-- ------------------------------------------------------------------------------
-- 19. جدول درخواست‌های دریافت جایزه توسط کاربران (Reward Claims)
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS reward_claims (
    id TEXT PRIMARY KEY,
    tracking_code TEXT NOT NULL UNIQUE,
    user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    reward_id TEXT NOT NULL REFERENCES rewards(id) ON DELETE RESTRICT,
    cost_points INTEGER NOT NULL CHECK (cost_points >= 0),
    status TEXT NOT NULL DEFAULT 'PENDING',
    recipient_name TEXT DEFAULT '',
    phone TEXT DEFAULT '',
    shipping_address TEXT DEFAULT '',
    admin_notes TEXT DEFAULT '',
    tracking_number TEXT DEFAULT '',
    idempotency_key TEXT,
    created_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
    updated_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now'))
);

CREATE INDEX IF NOT EXISTS idx_reward_claims_user ON reward_claims(user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_reward_claims_status ON reward_claims(status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_reward_claims_tracking ON reward_claims(tracking_code);
CREATE UNIQUE INDEX IF NOT EXISTS idx_reward_claims_idempotency
    ON reward_claims(idempotency_key)
    WHERE idempotency_key IS NOT NULL;

-- ------------------------------------------------------------------------------
-- 20. جدول تنظیمات اعلان صفحه ورود ویژه مشترکین ایرانسل (Login Notification Settings)
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS login_notification_settings (
    id TEXT PRIMARY KEY DEFAULT 'default',
    is_visible INTEGER NOT NULL DEFAULT 1 CHECK (is_visible IN (0, 1)),
    badge_text TEXT NOT NULL DEFAULT 'سرویس اختصاصی مشترکین ایرانسل',
    badge_icon TEXT NOT NULL DEFAULT 'verified',
    main_title TEXT NOT NULL DEFAULT 'سامانه منحصرأ ویژه مشترکان ارجمند ایرانسل',
    main_description TEXT NOT NULL DEFAULT 'با کمال احترام و افتخار، این سامانه منحصراً جهت حضور، رقابت و بهره‌مندی مشترکین گرامی و همراهان وفادار اپراتور ایرانسل طراحی گردیده است.',
    warning_is_visible INTEGER NOT NULL DEFAULT 1 CHECK (warning_is_visible IN (0, 1)),
    warning_title TEXT NOT NULL DEFAULT 'شرط الزامی: ورود با «اینترنت دیتای ایرانسل»',
    warning_description TEXT NOT NULL DEFAULT 'لطفاً پیش از ورود، وای‌فای و فیلترشکن را خاموش نموده و از طریق اینترنت همراه سیم‌کارت ایرانسل متصل گردید.',
    warning_icon TEXT NOT NULL DEFAULT 'signal_cellular_alt',
    updated_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now'))
);

-- ------------------------------------------------------------------------------
-- 21. جدول بنرهای تبلیغاتی تصویری و لینک‌دار (Ad Banners)
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ad_banners (
    id TEXT PRIMARY KEY,
    title TEXT NOT NULL,
    image_url TEXT NOT NULL,
    target_url TEXT NOT NULL,
    link_open_mode TEXT NOT NULL DEFAULT 'external' CHECK (link_open_mode IN ('external', 'internal')),
    is_active INTEGER NOT NULL DEFAULT 1 CHECK (is_active IN (0, 1)),
    start_at TEXT,
    end_at TEXT,
    display_order INTEGER NOT NULL DEFAULT 0,
    click_count INTEGER NOT NULL DEFAULT 0,
    impression_count INTEGER NOT NULL DEFAULT 0,
    placements TEXT DEFAULT '["home"]' CHECK (placements IS NULL OR json_valid(placements)),
    placement_stats TEXT DEFAULT '{}' CHECK (placement_stats IS NULL OR json_valid(placement_stats)),
    created_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
    updated_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now'))
);

CREATE INDEX IF NOT EXISTS idx_ad_banners_active_order ON ad_banners(is_active, display_order ASC, created_at DESC);

-- ------------------------------------------------------------------------------
-- 22. جدول بازخورد و امتیازدهی کاربران (User Feedback)
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS user_feedback (
    id TEXT PRIMARY KEY DEFAULT (
        lower(hex(randomblob(4))) || '-' ||
        lower(hex(randomblob(2))) || '-4' ||
        substr(lower(hex(randomblob(2))), 2, 3) || '-' ||
        substr('89ab', abs(random()) % 4 + 1, 1) ||
        substr(lower(hex(randomblob(2))), 2, 3) || '-' ||
        lower(hex(randomblob(6)))
    ),
    user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    rating INTEGER NOT NULL CHECK (rating BETWEEN 1 AND 5),
    comment TEXT,
    created_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now'))
);

CREATE INDEX IF NOT EXISTS idx_feedback_user ON user_feedback(user_id);

-- ==============================================================================
-- نماهای سازگاری (Read-Only Compatibility Views - معادل Viewهای PostgreSQL)
-- ==============================================================================

CREATE VIEW IF NOT EXISTS leaderboard AS
SELECT
    u.id AS user_id,
    u.first_name,
    u.last_name,
    COALESCE(s.total_score, u.total_score, 0) AS total_score,
    u.created_at,
    ROW_NUMBER() OVER (
        ORDER BY COALESCE(s.total_score, u.total_score, 0) DESC, u.created_at ASC
    ) AS rank
FROM users u
LEFT JOIN user_scores s ON u.id = s.user_id;

CREATE VIEW IF NOT EXISTS store_purchases AS
SELECT * FROM purchases;

CREATE VIEW IF NOT EXISTS competition_archive AS
SELECT * FROM competition_archives;

CREATE VIEW IF NOT EXISTS announcements AS
SELECT * FROM home_announcements;

CREATE VIEW IF NOT EXISTS instant_unlock_credits AS
SELECT user_id, instant_unlock_credits, updated_at FROM user_entitlements;

CREATE VIEW IF NOT EXISTS user_store_tiers AS
SELECT user_id, account_tier, is_vip, multiplier, updated_at FROM user_entitlements;

CREATE VIEW IF NOT EXISTS user_feedback_rewards AS
SELECT * FROM user_feedback;
