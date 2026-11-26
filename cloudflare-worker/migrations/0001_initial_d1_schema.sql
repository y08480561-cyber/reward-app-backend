-- ==============================================================================
-- Cloudflare D1 (SQLite) Schema for «رقابت کن، جایزه ببر»
-- Safe, non-destructive, idempotent DDL (no DROP/TRUNCATE)
-- Target Database: reward-app-db (Binding: DB)
-- ==============================================================================

-- 1. Users
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
    is_vip INTEGER NOT NULL DEFAULT 0,
    account_tier TEXT NOT NULL DEFAULT 'NORMAL',
    panel_type TEXT NOT NULL DEFAULT 'free',
    multiplier INTEGER NOT NULL DEFAULT 1,
    feedback_reward_claimed INTEGER NOT NULL DEFAULT 0,
    notifications_enabled INTEGER NOT NULL DEFAULT 1,
    created_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
    updated_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now'))
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_users_phone_unique ON users(phone_number);
CREATE INDEX IF NOT EXISTS idx_users_score ON users(total_score DESC);
CREATE INDEX IF NOT EXISTS idx_users_auth_token ON users(auth_token);

-- 2. User Scores
CREATE TABLE IF NOT EXISTS user_scores (
    user_id TEXT PRIMARY KEY,
    total_score INTEGER NOT NULL DEFAULT 0 CHECK (total_score >= 0),
    updated_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now'))
);
CREATE INDEX IF NOT EXISTS idx_user_scores_total ON user_scores(total_score DESC);

-- 3. Daily Cycles (24-hour cycles resetting at Tehran midnight)
CREATE TABLE IF NOT EXISTS daily_cycles (
    cycle_id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL,
    start_time INTEGER NOT NULL,
    end_time INTEGER NOT NULL,
    status TEXT NOT NULL DEFAULT 'ACTIVE',
    created_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now'))
);
CREATE INDEX IF NOT EXISTS idx_daily_cycles_user ON daily_cycles(user_id, start_time, end_time);

-- 4. Square States (1..100 squares per user per cycle)
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

-- 5. Ad Sessions (Anti-replay & audit log)
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

-- 6. User Memberships
CREATE TABLE IF NOT EXISTS user_memberships (
    user_id TEXT NOT NULL,
    cycle_id TEXT NOT NULL,
    panel_type TEXT NOT NULL DEFAULT 'free',
    multiplier INTEGER NOT NULL DEFAULT 1,
    purchased_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
    PRIMARY KEY (user_id, cycle_id)
);

-- 7. Purchases (Myket In-App Billing)
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
CREATE UNIQUE INDEX IF NOT EXISTS idx_purchases_token ON purchases(purchase_token) WHERE purchase_token IS NOT NULL;

CREATE VIEW IF NOT EXISTS store_purchases AS SELECT * FROM purchases;

-- 8. Competitions
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

INSERT OR IGNORE INTO competitions (id, competition_number, title, status)
VALUES ('COMP_1', 1, 'مسابقه شماره ۱ (جایزه آیفون و پلی‌استیشن)', 'ACTIVE');

-- 9. Competition Archives
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

CREATE VIEW IF NOT EXISTS competition_archive AS SELECT * FROM competition_archives;

-- 10. Competition Logs
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

-- 11. Home Announcements
CREATE TABLE IF NOT EXISTS home_announcements (
    id TEXT PRIMARY KEY,
    title TEXT NOT NULL,
    content TEXT NOT NULL,
    type TEXT NOT NULL DEFAULT 'ANNOUNCEMENT',
    is_active INTEGER NOT NULL DEFAULT 1,
    competition_id TEXT REFERENCES competitions(id) ON DELETE SET NULL,
    starts_at TEXT,
    ends_at TEXT,
    created_by TEXT NOT NULL DEFAULT 'admin',
    created_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
    updated_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now'))
);
CREATE INDEX IF NOT EXISTS idx_announcements_active ON home_announcements(is_active, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_announcements_comp ON home_announcements(competition_id);

CREATE VIEW IF NOT EXISTS announcements AS SELECT * FROM home_announcements;

INSERT OR IGNORE INTO home_announcements (id, title, content, type, is_active, competition_id, created_by)
VALUES (
    'ANN_DEFAULT_1',
    'آغاز مسابقه بزرگ دوره‌ای!',
    'به مسابقه «رقابت کن، جایزه ببر» خوش آمدید! با تکمیل روزانه ۱۰۰ مربع تبلیغاتی تپسل امتیاز کسب کنید و شانس برنده شدن آیفون ۱۶ و کنسول پلی‌استیشن را افزایش دهید.',
    'COMPETITION_START',
    1,
    'COMP_1',
    'admin'
);

-- 12. Support Tickets
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

-- 13. Support Messages
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

-- 14. Store Products (4 Canonical Packages)
CREATE TABLE IF NOT EXISTS store_products (
    id TEXT PRIMARY KEY,
    title TEXT NOT NULL,
    description TEXT NOT NULL,
    product_type TEXT NOT NULL DEFAULT 'SQUARE_UNLOCK',
    units_granted INTEGER NOT NULL DEFAULT 0,
    price_toman INTEGER NOT NULL DEFAULT 0,
    myket_sku TEXT NOT NULL,
    badge TEXT DEFAULT '',
    is_active INTEGER NOT NULL DEFAULT 1,
    is_ready_in_myket INTEGER NOT NULL DEFAULT 0,
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
    updated_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now'))
);
CREATE INDEX IF NOT EXISTS idx_store_products_active ON store_products(is_active, sort_order ASC);

INSERT OR IGNORE INTO store_products (id, title, description, product_type, units_granted, price_toman, myket_sku, badge, is_active, is_ready_in_myket, sort_order)
VALUES
  ('pkg_squares_50', 'بازگشایی ۵۰ مربع', 'بازگشایی آنی ۵۰ مربع مسابقه بدون نیاز به تماشای تبلیغات، همراه با ثبت مستقیم امتیازات در جدول رده‌بندی.', 'SQUARE_UNLOCK', 50, 55000, 'reqabat_unlock_50', 'محبوب', 1, 1, 1),
  ('pkg_squares_100', 'بازگشایی ۱۰۰ مربع', 'تکمیل سریع ۱۰۰ مربع کامل روزانه با یک لمس و دریافت کامل امتیازات روز برای صعود به صدر جدول برندگان.', 'SQUARE_UNLOCK', 100, 100000, 'reqabat_unlock_100', 'به‌صرفه', 1, 1, 2),
  ('pkg_upgrade_silver', 'ارتقای نقره‌ای', 'ارتقای سطح به نقره‌ای (Silver): امتیاز هر مربع از ۱ به ۳ امتیاز افزایش می‌یابد (ضریب ۳ برابری).', 'ACCOUNT_UPGRADE', 3, 70000, 'reqabat_upgrade_silver', 'نقره‌ای (۳X)', 1, 1, 3),
  ('pkg_upgrade_gold', 'ارتقای طلایی', 'بالاترین سطح عضویت طلایی (Gold): امتیاز هر مربع به ۶ امتیاز افزایش می‌یابد (ضریب ۶ برابری ویژه).', 'ACCOUNT_UPGRADE', 6, 120000, 'reqabat_upgrade_gold', 'طلایی (۶X)', 1, 1, 4);

-- 15. User Entitlements
CREATE TABLE IF NOT EXISTS user_entitlements (
    user_id TEXT PRIMARY KEY,
    instant_unlock_credits INTEGER NOT NULL DEFAULT 0,
    is_vip INTEGER NOT NULL DEFAULT 0,
    total_purchased_toman INTEGER NOT NULL DEFAULT 0,
    account_tier TEXT NOT NULL DEFAULT 'NORMAL',
    multiplier INTEGER NOT NULL DEFAULT 1,
    updated_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now'))
);

-- 16. Reward Categories
CREATE TABLE IF NOT EXISTS reward_categories (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    slug TEXT NOT NULL UNIQUE,
    icon TEXT DEFAULT 'gift',
    sort_order INTEGER NOT NULL DEFAULT 0,
    is_active INTEGER NOT NULL DEFAULT 1,
    created_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now'))
);
CREATE INDEX IF NOT EXISTS idx_reward_categories_active ON reward_categories(is_active, sort_order ASC);

INSERT OR IGNORE INTO reward_categories (id, name, slug, icon, sort_order, is_active)
VALUES
  ('cat_recharge', 'کارت شارژ', 'recharge-cards', 'smartphone', 1, 1),
  ('cat_internet', 'بسته‌های اینترنت', 'internet-packages', 'wifi', 2, 1),
  ('cat_cash', 'جوایز نقدی و هدیه', 'cash-gifts', 'credit-card', 3, 1),
  ('cat_digital', 'گیفت کارت دیجیتال', 'digital-giftcards', 'gift', 4, 1);

-- 17. Rewards
CREATE TABLE IF NOT EXISTS rewards (
    id TEXT PRIMARY KEY,
    title TEXT NOT NULL,
    description TEXT DEFAULT '',
    category_id TEXT REFERENCES reward_categories(id) ON DELETE SET NULL,
    cost_points INTEGER NOT NULL CHECK (cost_points >= 0),
    stock_quantity INTEGER NOT NULL DEFAULT 0 CHECK (stock_quantity >= 0),
    image_url TEXT DEFAULT '',
    additional_images TEXT DEFAULT '[]',
    is_featured INTEGER NOT NULL DEFAULT 0,
    is_active INTEGER NOT NULL DEFAULT 1,
    sort_order INTEGER NOT NULL DEFAULT 0,
    deleted_at TEXT,
    created_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
    updated_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now'))
);
CREATE INDEX IF NOT EXISTS idx_rewards_active ON rewards(is_active, sort_order ASC);
CREATE INDEX IF NOT EXISTS idx_rewards_category ON rewards(category_id);

-- 18. Reward Appearance
CREATE TABLE IF NOT EXISTS reward_appearance (
    id TEXT PRIMARY KEY DEFAULT 'default',
    hero_title TEXT NOT NULL DEFAULT 'ویترین جوایز و پاداش‌های ارزشمند',
    hero_subtitle TEXT DEFAULT 'امتیازهای کسب‌شده خود در مسابقات را به کارت شارژ، بسته‌های اینترنت و جوایز نقدی تبدیل کنید',
    hero_banner_url TEXT DEFAULT '',
    primary_color TEXT DEFAULT '#10B981',
    active_sections TEXT DEFAULT '{"featured": true, "categories": true, "history": true, "banners": true}',
    terms_text TEXT DEFAULT 'درخواست‌های دریافت جایزه پس از بررسی توسط تیم پشتیبانی ظرف ۲۴ الی ۷۲ ساعت کاری پردازش و ارسال خواهند شد.',
    updated_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now'))
);

INSERT OR IGNORE INTO reward_appearance (id, hero_title, hero_subtitle, hero_banner_url, primary_color)
VALUES ('default', 'اینترنت ۲۰ گیگ برنده شو!', 'هدیه ویژه دوره جاری مسابقه رقابت کن، جایزه ببر', '', '#10B981');

-- 19. Reward Claims
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
CREATE UNIQUE INDEX IF NOT EXISTS idx_reward_claims_idempotency ON reward_claims(idempotency_key) WHERE idempotency_key IS NOT NULL;

-- 20. Login Notification Settings
CREATE TABLE IF NOT EXISTS login_notification_settings (
    id TEXT PRIMARY KEY DEFAULT 'default',
    is_visible INTEGER NOT NULL DEFAULT 1,
    badge_text TEXT NOT NULL DEFAULT 'سرویس اختصاصی مشترکین ایرانسل',
    badge_icon TEXT NOT NULL DEFAULT 'verified',
    main_title TEXT NOT NULL DEFAULT 'سامانه منحصرأ ویژه مشترکان ارجمند ایرانسل',
    main_description TEXT NOT NULL DEFAULT 'با کمال احترام و افتخار، این سامانه منحصراً جهت حضور، رقابت و بهره‌مندی مشترکین گرامی و همراهان وفادار اپراتور ایرانسل طراحی گردیده است.',
    warning_is_visible INTEGER NOT NULL DEFAULT 1,
    warning_title TEXT NOT NULL DEFAULT 'شرط الزامی: ورود با «اینترنت دیتای ایرانسل»',
    warning_description TEXT NOT NULL DEFAULT 'لطفاً پیش از ورود، وای‌فای و فیلترشکن را خاموش نموده و از طریق اینترنت همراه سیم‌کارت ایرانسل متصل گردید.',
    warning_icon TEXT NOT NULL DEFAULT 'signal_cellular_alt',
    updated_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now'))
);

INSERT OR IGNORE INTO login_notification_settings (id) VALUES ('default');

-- 21. Leaderboard View
CREATE VIEW IF NOT EXISTS leaderboard AS
SELECT
    u.id AS user_id,
    u.first_name,
    u.last_name,
    COALESCE(s.total_score, u.total_score, 0) AS total_score,
    u.created_at,
    ROW_NUMBER() OVER (ORDER BY COALESCE(s.total_score, u.total_score, 0) DESC, u.created_at ASC) AS rank
FROM users u
LEFT JOIN user_scores s ON u.id = s.user_id;

-- 22. Ad Banners
CREATE TABLE IF NOT EXISTS ad_banners (
    id TEXT PRIMARY KEY,
    title TEXT NOT NULL,
    image_url TEXT NOT NULL,
    target_url TEXT NOT NULL,
    link_open_mode TEXT NOT NULL DEFAULT 'external' CHECK (link_open_mode IN ('external', 'internal')),
    is_active INTEGER NOT NULL DEFAULT 1,
    start_at TEXT,
    end_at TEXT,
    display_order INTEGER NOT NULL DEFAULT 0,
    click_count INTEGER NOT NULL DEFAULT 0,
    impression_count INTEGER NOT NULL DEFAULT 0,
    placements TEXT DEFAULT '["home"]',
    placement_stats TEXT DEFAULT '{}',
    created_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
    updated_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now'))
);
CREATE INDEX IF NOT EXISTS idx_ad_banners_active_order ON ad_banners(is_active, display_order ASC, created_at DESC);

-- 23. User Feedback
CREATE TABLE IF NOT EXISTS user_feedback (
    id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    rating INTEGER NOT NULL CHECK (rating BETWEEN 1 AND 5),
    comment TEXT,
    created_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now'))
);
CREATE INDEX IF NOT EXISTS idx_feedback_user ON user_feedback(user_id);

-- 24. Worker-Persistent OTP Codes (replaces in-memory Map across Worker isolates)
CREATE TABLE IF NOT EXISTS otp_codes (
    phone_number TEXT PRIMARY KEY,
    otp_code TEXT NOT NULL,
    expires_at INTEGER NOT NULL,
    requests_count INTEGER NOT NULL DEFAULT 1,
    last_requested_at INTEGER NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0
);

-- 25. Worker-Persistent Rate Limits
CREATE TABLE IF NOT EXISTS rate_limits (
    rate_key TEXT PRIMARY KEY,
    window_start INTEGER NOT NULL,
    count INTEGER NOT NULL DEFAULT 1,
    updated_at INTEGER NOT NULL
);

-- 26. Worker-Persistent Competition Reset Lock
CREATE TABLE IF NOT EXISTS competition_locks (
    id TEXT PRIMARY KEY DEFAULT 'active',
    is_locked INTEGER NOT NULL DEFAULT 0,
    locked_at INTEGER
);
INSERT OR IGNORE INTO competition_locks (id, is_locked, locked_at) VALUES ('active', 0, NULL);

-- 27. Worker-Persistent Ad Banner Event Deduplication
CREATE TABLE IF NOT EXISTS banner_event_dedup (
    dedup_key TEXT PRIMARY KEY,
    expires_at INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_banner_event_dedup_exp ON banner_event_dedup(expires_at);
