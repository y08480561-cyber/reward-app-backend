-- ==============================================================================
-- اسکریپت جامع و کامل ساخت دیتابیس MySQL برای پروژه «رقابت کن، جایزه ببر»
-- مطابق دقیق با ساختار داده و نیازمندی‌های هاست اشتراکی cPanel
-- قابل اجرا در: cPanel -> phpMyAdmin -> SQL Tab
-- توجه: این اسکریپت ایمن بوده و در صورت وجود جداول، دیتای موجود را حفظ می‌کند (IF NOT EXISTS).
-- ==============================================================================

-- تنظیم یونیکد استاندارد برای پشتیبانی از فارسی
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ۱. جدول کاربران (users)
CREATE TABLE IF NOT EXISTS users (
    id VARCHAR(255) PRIMARY KEY,
    first_name VARCHAR(255) NOT NULL DEFAULT '',
    last_name VARCHAR(255) NOT NULL DEFAULT '',
    phone_number VARCHAR(20) NOT NULL UNIQUE,
    total_score INT NOT NULL DEFAULT 0,
    status VARCHAR(50) NOT NULL DEFAULT 'active',
    auth_method VARCHAR(100) DEFAULT 'DIRECT_CREDENTIALS',
    password_hash TEXT,
    auth_token TEXT,
    instant_unlock_credits INT NOT NULL DEFAULT 0,
    is_vip BOOLEAN NOT NULL DEFAULT false,
    account_tier VARCHAR(50) NOT NULL DEFAULT 'NORMAL',
    panel_type VARCHAR(50) NOT NULL DEFAULT 'free',
    multiplier INT NOT NULL DEFAULT 1,
    feedback_reward_claimed BOOLEAN NOT NULL DEFAULT false,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_users_score (total_score DESC),
    INDEX idx_users_phone (phone_number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_persian_ci;

-- ۲. جدول امتیازات اختصاصی کاربران (user_scores)
CREATE TABLE IF NOT EXISTS user_scores (
    user_id VARCHAR(255) PRIMARY KEY,
    total_score INT NOT NULL DEFAULT 0,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_user_scores_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_persian_ci;

-- ۳. جدول دوره‌های ۲۴ ساعته روزانه (daily_cycles)
CREATE TABLE IF NOT EXISTS daily_cycles (
    cycle_id VARCHAR(255) PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    start_time BIGINT NOT NULL,
    end_time BIGINT NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_daily_cycles_user (user_id, start_time, end_time),
    CONSTRAINT fk_daily_cycles_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_persian_ci;

-- ۴. جدول وضعیت ۱۰۰ خانه مسابقه برای هر کاربر (square_states)
CREATE TABLE IF NOT EXISTS square_states (
    state_key VARCHAR(255) PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    cycle_id VARCHAR(255) NOT NULL,
    square_number INT NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'COMPLETED',
    completed_at BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_square_states_lookup (user_id, cycle_id, square_number),
    CONSTRAINT fk_square_states_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_persian_ci;

-- ۵. جدول ثبت نشست‌های تبلیغاتی تپسل و اعتبارسنجی پاداش (ad_sessions)
CREATE TABLE IF NOT EXISTS ad_sessions (
    session_id VARCHAR(255) PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    square_number INT NOT NULL,
    cycle_id VARCHAR(255) NOT NULL,
    timestamp BIGINT NOT NULL,
    ad_duration_seconds INT NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'COMPLETED',
    token VARCHAR(255) UNIQUE,
    competition_id VARCHAR(255),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_ad_sessions_user (user_id),
    INDEX idx_ad_sessions_token (token),
    CONSTRAINT fk_ad_sessions_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_persian_ci;

-- ۶. جدول اشتراک و سطوح کاربری پنل‌ها (user_memberships)
CREATE TABLE IF NOT EXISTS user_memberships (
    user_id VARCHAR(255) NOT NULL,
    cycle_id VARCHAR(255) NOT NULL,
    panel_type VARCHAR(50) NOT NULL DEFAULT 'free',
    multiplier INT NOT NULL DEFAULT 1,
    purchased_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, cycle_id),
    CONSTRAINT fk_user_memberships_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_persian_ci;

-- ۷. جدول تراکنش‌ها و خریدهای مایکت (purchases)
CREATE TABLE IF NOT EXISTS purchases (
    purchase_id VARCHAR(255) PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    product_id VARCHAR(255) NOT NULL,
    amount INT NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'SUCCESS',
    purchase_token VARCHAR(255),
    myket_order_id VARCHAR(255),
    units_granted INT DEFAULT 0,
    error_message TEXT,
    verification_source VARCHAR(100) DEFAULT 'MYKET_IAB',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE INDEX idx_purchases_token (purchase_token),
    CONSTRAINT fk_purchases_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_persian_ci;

-- ۸. جدول مسابقات ادواری (competitions)
CREATE TABLE IF NOT EXISTS competitions (
    id VARCHAR(255) PRIMARY KEY,
    competition_number INT NOT NULL,
    title VARCHAR(255) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    started_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ended_at DATETIME,
    total_participants INT NOT NULL DEFAULT 0,
    total_score INT NOT NULL DEFAULT 0,
    created_by VARCHAR(255) NOT NULL DEFAULT 'admin',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_persian_ci;

-- مقداردهی اولیه اولین دوره مسابقه
INSERT IGNORE INTO competitions (id, competition_number, title, status)
VALUES ('COMP_DEFAULT_1', 1, 'مسابقه بزرگ مرحله اول رقابت کن جایزه ببر', 'ACTIVE');

-- ۹. جدول آرشیو مسابقات پایان‌یافته (competition_archives)
CREATE TABLE IF NOT EXISTS competition_archives (
    id VARCHAR(255) PRIMARY KEY,
    competition_id VARCHAR(255) NOT NULL,
    user_id VARCHAR(255) NOT NULL,
    final_score INT NOT NULL DEFAULT 0,
    final_rank INT NOT NULL DEFAULT 0,
    first_name VARCHAR(255) DEFAULT '',
    last_name VARCHAR(255) DEFAULT '',
    phone_number VARCHAR(255) DEFAULT '',
    archived_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_comp_archives_lookup (competition_id, final_rank),
    CONSTRAINT fk_comp_archives_comp FOREIGN KEY (competition_id) REFERENCES competitions(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_persian_ci;

-- ۱۰. لاگ‌های امنیتی و تغییرات مسابقات (competition_logs)
CREATE TABLE IF NOT EXISTS competition_logs (
    log_id VARCHAR(255) PRIMARY KEY,
    action VARCHAR(255) NOT NULL,
    admin_key_used VARCHAR(255) NOT NULL,
    prev_competition_id VARCHAR(255),
    new_competition_id VARCHAR(255),
    prev_title VARCHAR(255),
    new_title VARCHAR(255),
    participants_count INT DEFAULT 0,
    total_score_archived INT DEFAULT 0,
    details TEXT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_persian_ci;

-- ۱۱. جدول اعلانات سراسری صفحه اصلی (home_announcements)
CREATE TABLE IF NOT EXISTS home_announcements (
    id VARCHAR(255) PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    type VARCHAR(100) NOT NULL DEFAULT 'ANNOUNCEMENT',
    is_active BOOLEAN NOT NULL DEFAULT true,
    competition_id VARCHAR(255),
    starts_at DATETIME,
    ends_at DATETIME,
    created_by VARCHAR(255) NOT NULL DEFAULT 'admin',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_home_announcements_comp FOREIGN KEY (competition_id) REFERENCES competitions(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_persian_ci;

-- ۱۲. جدول تیکت‌های پشتیبانی کاربران (support_tickets)
CREATE TABLE IF NOT EXISTS support_tickets (
    id VARCHAR(255) PRIMARY KEY,
    tracking_code VARCHAR(255) NOT NULL UNIQUE,
    user_id VARCHAR(255) NOT NULL,
    subject VARCHAR(255) NOT NULL,
    category VARCHAR(100) NOT NULL DEFAULT 'OTHER',
    status VARCHAR(50) NOT NULL DEFAULT 'NEW',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    last_message_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_support_tickets_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_persian_ci;

-- ۱۳. جدول پیام‌های درون هر تیکت پشتیبانی (support_messages)
CREATE TABLE IF NOT EXISTS support_messages (
    id VARCHAR(255) PRIMARY KEY,
    ticket_id VARCHAR(255) NOT NULL,
    sender_type ENUM('USER', 'ADMIN') NOT NULL,
    sender_id VARCHAR(255) NOT NULL,
    sender_name VARCHAR(255) NOT NULL,
    message_text TEXT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_support_messages_ticket FOREIGN KEY (ticket_id) REFERENCES support_tickets(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_persian_ci;

-- ۱۴. جدول بسته‌های فروشگاهی (store_products)
CREATE TABLE IF NOT EXISTS store_products (
    id VARCHAR(255) PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    description TEXT NOT NULL,
    product_type VARCHAR(100) NOT NULL DEFAULT 'SQUARE_UNLOCK',
    units_granted INT NOT NULL DEFAULT 0,
    price_toman BIGINT NOT NULL DEFAULT 0,
    myket_sku VARCHAR(255) NOT NULL,
    badge VARCHAR(255) DEFAULT '',
    is_active BOOLEAN NOT NULL DEFAULT true,
    is_ready_in_myket BOOLEAN NOT NULL DEFAULT false,
    sort_order INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_persian_ci;

-- ۱۵. جدول مزایا و اعتبارات حساب کاربری (user_entitlements)
CREATE TABLE IF NOT EXISTS user_entitlements (
    user_id VARCHAR(255) PRIMARY KEY,
    instant_unlock_credits INT NOT NULL DEFAULT 0,
    is_vip BOOLEAN NOT NULL DEFAULT false,
    total_purchased_toman BIGINT NOT NULL DEFAULT 0,
    account_tier VARCHAR(50) NOT NULL DEFAULT 'NORMAL',
    multiplier INT NOT NULL DEFAULT 1,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_user_entitlements_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_persian_ci;

-- ۱۶. جدول دسته‌بندی جوایز (reward_categories)
CREATE TABLE IF NOT EXISTS reward_categories (
    id VARCHAR(255) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    slug VARCHAR(255) NOT NULL UNIQUE,
    icon VARCHAR(100) DEFAULT 'gift',
    sort_order INT NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_persian_ci;

-- ۱۷. جدول جوایز (rewards)
CREATE TABLE IF NOT EXISTS rewards (
    id VARCHAR(255) PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    category_id VARCHAR(255),
    cost_points INT NOT NULL DEFAULT 0,
    stock_quantity INT NOT NULL DEFAULT 0,
    image_url TEXT,
    additional_images JSON,
    is_featured BOOLEAN NOT NULL DEFAULT false,
    is_active BOOLEAN NOT NULL DEFAULT true,
    sort_order INT NOT NULL DEFAULT 0,
    deleted_at DATETIME,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_rewards_category FOREIGN KEY (category_id) REFERENCES reward_categories(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_persian_ci;

-- ۱۸. جدول تنظیمات ظاهری و بنر ویترین جوایز (reward_appearance)
CREATE TABLE IF NOT EXISTS reward_appearance (
    id VARCHAR(255) PRIMARY KEY DEFAULT 'default',
    hero_title VARCHAR(255) NOT NULL DEFAULT 'ویترین جوایز و پاداش‌های ارزشمند',
    hero_subtitle TEXT,
    hero_banner_url TEXT,
    primary_color VARCHAR(50) DEFAULT '#10B981',
    active_sections JSON,
    terms_text TEXT,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_persian_ci;

-- مقداردهی اولیه ظاهر جوایز
INSERT IGNORE INTO reward_appearance (id) VALUES ('default');

-- ۱۹. جدول درخواست‌های دریافت جایزه (reward_claims)
CREATE TABLE IF NOT EXISTS reward_claims (
    id VARCHAR(255) PRIMARY KEY,
    tracking_code VARCHAR(255) NOT NULL UNIQUE,
    user_id VARCHAR(255) NOT NULL,
    reward_id VARCHAR(255) NOT NULL,
    cost_points INT NOT NULL DEFAULT 0,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    recipient_name VARCHAR(255) DEFAULT '',
    phone VARCHAR(50) DEFAULT '',
    shipping_address TEXT,
    admin_notes TEXT,
    tracking_number VARCHAR(255) DEFAULT '',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_reward_claims_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_reward_claims_reward FOREIGN KEY (reward_id) REFERENCES rewards(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_persian_ci;

-- ۲۰. جدول تنظیمات اعلان صفحه ورود (login_notification_settings)
CREATE TABLE IF NOT EXISTS login_notification_settings (
    id VARCHAR(255) PRIMARY KEY DEFAULT 'default',
    is_visible BOOLEAN NOT NULL DEFAULT true,
    badge_text VARCHAR(255) NOT NULL DEFAULT 'سرویس اختصاصی مشترکین ایرانسل',
    badge_icon VARCHAR(100) NOT NULL DEFAULT 'verified',
    main_title VARCHAR(255) NOT NULL DEFAULT 'سامانه منحصرأ ویژه مشترکان ارجمند ایرانسل',
    main_description TEXT,
    warning_is_visible BOOLEAN NOT NULL DEFAULT true,
    warning_title VARCHAR(255) NOT NULL DEFAULT 'شرط الزامی: ورود با «اینترنت دیتای ایرانسل»',
    warning_description TEXT,
    warning_icon VARCHAR(100) NOT NULL DEFAULT 'signal_cellular_alt',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_persian_ci;

-- مقداردهی اولیه اعلان ورود
INSERT IGNORE INTO login_notification_settings (id) VALUES ('default');

-- ۲۱. نمای جدول رتبه‌بندی (leaderboard)
CREATE OR REPLACE VIEW leaderboard AS
SELECT 
    u.id AS user_id,
    u.first_name,
    u.last_name,
    COALESCE(s.total_score, u.total_score, 0) AS total_score,
    u.created_at,
    (SELECT COUNT(*) + 1 FROM users u2 LEFT JOIN user_scores s2 ON u2.id = s2.user_id 
     WHERE COALESCE(s2.total_score, u2.total_score, 0) > COALESCE(s.total_score, u.total_score, 0) 
     OR (COALESCE(s2.total_score, u2.total_score, 0) = COALESCE(s.total_score, u.total_score, 0) AND u2.created_at < u.created_at)) AS rank
FROM users u
LEFT JOIN user_scores s ON u.id = s.user_id;

-- درج مسابقه اولیه
INSERT IGNORE INTO competitions (id, competition_number, title, status, started_at, created_by)
VALUES ('COMP_1', 1, 'مسابقه شماره ۱ (جایزه آیفون و پلی‌استیشن)', 'ACTIVE', NOW(), 'system');

-- درج اطلاعیه اولیه
INSERT IGNORE INTO home_announcements (id, title, content, type, is_active, competition_id, starts_at, created_by)
VALUES ('ANN_1', 'آغاز مسابقه بزرگ دوره‌ای!', 'به مسابقه «رقابت کن، جایزه ببر» خوش آمدید! با تکمیل روزانه ۱۰۰ مربع تبلیغاتی تپسل امتیاز کسب کنید و شانس برنده شدن آیفون ۱۶ و کنسول پلی‌استیشن را افزایش دهید.', 'COMPETITION_START', 1, 'COMP_1', NOW(), 'system');

-- درج تنظیمات ظاهری پیش‌فرض
INSERT IGNORE INTO reward_appearance (id, hero_title, hero_subtitle, hero_banner_url, primary_color)
VALUES ('default', 'اینترنت ۲۰ گیگ برنده شو!', 'هدیه ویژه دوره جاری مسابقه رقابت کن، جایزه ببر', '', '#10B981');

-- درج بسته‌های اولیه فروشگاه مایکت
INSERT IGNORE INTO store_products (id, title, description, product_type, units_granted, price_toman, myket_sku, badge, sort_order)
VALUES 
  ('pkg_squares_50', 'بسته ۵۰ تایی بازگشایی سریع', 'بازگشایی فوری ۵۰ خانه از مسابقه بدون مشاهده تبلیغات', 'SQUARE_UNLOCK', 50, 45000, 'req_pkg_50', 'محبوب', 1),
  ('pkg_squares_100', 'بسته ۱۰۰ تایی بازگشایی کامل', 'بازگشایی کامل تمامی ۱۰۰ خانه مسابقه در یک روز', 'SQUARE_UNLOCK', 100, 80000, 'req_pkg_100', 'پیشنهاد ویژه', 2),
  ('pkg_upgrade_silver', 'ارتقای سطح به نقره‌ای (ضریب ۳X)', 'افزایش دائمی امتیازات دریافتی از هر تبلیغ به ۳ امتیاز', 'ACCOUNT_UPGRADE', 1, 70000, 'req_tier_silver', 'پرفروش', 3),
  ('pkg_upgrade_gold', 'ارتقای سطح به طلایی (ضریب ۶X)', 'افزایش دائمی امتیازات دریافتی از هر تبلیغ به ۶ امتیاز (حرفه‌ای)', 'ACCOUNT_UPGRADE', 1, 120000, 'req_tier_gold', 'طلایی', 4);

SET FOREIGN_KEY_CHECKS = 1;
