-- ==============================================================================
-- اسکریپت جامع و کامل ساخت دیتابیس Supabase PostgreSQL برای «رقابت کن، جایزه ببر»
-- مطابق دقیق با ساختار داده و نیازمندی‌های نسخه ۲۲ نودجی‌اس و هاست cPanel
-- قابل اجرا در: Supabase Dashboard -> SQL Editor -> New Query
-- توجه: این اسکریپت ایمن بوده و در صورت وجود جداول، دیتای موجود را حفظ می‌کند (IF NOT EXISTS).
-- ==============================================================================

-- ۱. جدول کاربران (Users)
CREATE TABLE IF NOT EXISTS public.users (
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
    is_vip BOOLEAN NOT NULL DEFAULT false,
    account_tier TEXT NOT NULL DEFAULT 'NORMAL',
    panel_type TEXT NOT NULL DEFAULT 'free',
    multiplier INTEGER NOT NULL DEFAULT 1,
    feedback_reward_claimed BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ارتقای امن ستون‌های جدول کاربران در صورت ساخت قبلی
ALTER TABLE public.users ADD COLUMN IF NOT EXISTS total_score INTEGER NOT NULL DEFAULT 0;
ALTER TABLE public.users ADD COLUMN IF NOT EXISTS status TEXT NOT NULL DEFAULT 'active';
ALTER TABLE public.users ADD COLUMN IF NOT EXISTS auth_method TEXT DEFAULT 'DIRECT_CREDENTIALS';
ALTER TABLE public.users ADD COLUMN IF NOT EXISTS password_hash TEXT;
ALTER TABLE public.users ADD COLUMN IF NOT EXISTS auth_token TEXT;
ALTER TABLE public.users ADD COLUMN IF NOT EXISTS instant_unlock_credits INTEGER NOT NULL DEFAULT 0;
ALTER TABLE public.users ADD COLUMN IF NOT EXISTS is_vip BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE public.users ADD COLUMN IF NOT EXISTS account_tier TEXT NOT NULL DEFAULT 'NORMAL';
ALTER TABLE public.users ADD COLUMN IF NOT EXISTS panel_type TEXT NOT NULL DEFAULT 'free';
ALTER TABLE public.users ADD COLUMN IF NOT EXISTS multiplier INTEGER NOT NULL DEFAULT 1;
ALTER TABLE public.users ADD COLUMN IF NOT EXISTS feedback_reward_claimed BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE public.users ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT NOW();
ALTER TABLE public.users ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

CREATE UNIQUE INDEX IF NOT EXISTS idx_users_phone_unique ON public.users(phone_number);
CREATE INDEX IF NOT EXISTS idx_users_score ON public.users(total_score DESC);
CREATE INDEX IF NOT EXISTS idx_users_auth_token ON public.users(auth_token);

-- ۲. جدول امتیازات اختصاصی کاربران (User Scores)
CREATE TABLE IF NOT EXISTS public.user_scores (
    user_id TEXT PRIMARY KEY,
    total_score INTEGER NOT NULL DEFAULT 0 CHECK (total_score >= 0),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_user_scores_total ON public.user_scores(total_score DESC);

-- ۳. جدول دوره‌های ۲۴ ساعته روزانه (Daily Cycles)
CREATE TABLE IF NOT EXISTS public.daily_cycles (
    cycle_id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL,
    start_time BIGINT NOT NULL,
    end_time BIGINT NOT NULL,
    status TEXT NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_daily_cycles_user ON public.daily_cycles(user_id, start_time, end_time);

-- ۴. جدول وضعیت ۱۰۰ خانه مسابقه برای هر کاربر (Square States)
CREATE TABLE IF NOT EXISTS public.square_states (
    state_key TEXT PRIMARY KEY,
    user_id TEXT NOT NULL,
    cycle_id TEXT NOT NULL,
    square_number INTEGER NOT NULL CHECK (square_number BETWEEN 1 AND 100),
    status TEXT NOT NULL DEFAULT 'COMPLETED',
    completed_at BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_square_states_lookup ON public.square_states(user_id, cycle_id, square_number);

-- ۵. جدول ثبت نشست‌های تبلیغاتی تپسل و اعتبارسنجی پاداش (Ad Sessions)
CREATE TABLE IF NOT EXISTS public.ad_sessions (
    session_id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL,
    square_number INTEGER NOT NULL CHECK (square_number BETWEEN 1 AND 100),
    cycle_id TEXT NOT NULL,
    timestamp BIGINT NOT NULL,
    ad_duration_seconds INTEGER NOT NULL,
    status TEXT NOT NULL DEFAULT 'COMPLETED',
    token TEXT UNIQUE,
    competition_id TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_ad_sessions_user ON public.ad_sessions(user_id);
CREATE INDEX IF NOT EXISTS idx_ad_sessions_token ON public.ad_sessions(token);
CREATE INDEX IF NOT EXISTS idx_ad_sessions_competition ON public.ad_sessions(competition_id);

-- ۶. جدول اشتراک و سطوح کاربری پنل‌ها (User Memberships)
CREATE TABLE IF NOT EXISTS public.user_memberships (
    user_id TEXT NOT NULL,
    cycle_id TEXT NOT NULL,
    panel_type TEXT NOT NULL DEFAULT 'free',
    multiplier INTEGER NOT NULL DEFAULT 1,
    purchased_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, cycle_id)
);

-- ۷. جدول تراکنش‌ها و خریدهای مایکت (Purchases)
CREATE TABLE IF NOT EXISTS public.purchases (
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
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_purchases_token ON public.purchases(purchase_token) WHERE purchase_token IS NOT NULL;

-- ایجاد ویو جهت تطابق store_purchases با purchases
CREATE OR REPLACE VIEW public.store_purchases AS SELECT * FROM public.purchases;

-- ۸. جدول مسابقات ادواری (Competitions)
CREATE TABLE IF NOT EXISTS public.competitions (
    id TEXT PRIMARY KEY,
    competition_number INTEGER NOT NULL,
    title TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'ACTIVE',
    started_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ended_at TIMESTAMPTZ,
    total_participants INTEGER NOT NULL DEFAULT 0,
    total_score INTEGER NOT NULL DEFAULT 0,
    created_by TEXT NOT NULL DEFAULT 'admin',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_competitions_status ON public.competitions(status);
CREATE INDEX IF NOT EXISTS idx_competitions_number ON public.competitions(competition_number);

-- مقداردهی اولیه اولین دوره مسابقه در صورت عدم وجود
INSERT INTO public.competitions (id, competition_number, title, status)
SELECT 'COMP_DEFAULT_1', 1, 'مسابقه بزرگ مرحله اول رقابت کن جایزه ببر', 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM public.competitions WHERE id = 'COMP_DEFAULT_1');

-- ۹. جدول آرشیو مسابقات پایان‌یافته (Competition Archives)
CREATE TABLE IF NOT EXISTS public.competition_archives (
    id TEXT PRIMARY KEY,
    competition_id TEXT NOT NULL REFERENCES public.competitions(id) ON DELETE CASCADE,
    user_id TEXT NOT NULL,
    final_score INTEGER NOT NULL DEFAULT 0,
    final_rank INTEGER NOT NULL DEFAULT 0,
    first_name TEXT DEFAULT '',
    last_name TEXT DEFAULT '',
    phone_number TEXT DEFAULT '',
    archived_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_comp_archives_lookup ON public.competition_archives(competition_id, final_rank);
CREATE INDEX IF NOT EXISTS idx_comp_archives_user ON public.competition_archives(competition_id, user_id);

CREATE OR REPLACE VIEW public.competition_archive AS SELECT * FROM public.competition_archives;

-- ۱۰. لاگ‌های امنیتی و تغییرات مسابقات (Competition Logs)
CREATE TABLE IF NOT EXISTS public.competition_logs (
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
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_comp_logs_created ON public.competition_logs(created_at DESC);

-- ۱۱. جدول اعلانات سراسری صفحه اصلی (Home Announcements)
CREATE TABLE IF NOT EXISTS public.home_announcements (
    id TEXT PRIMARY KEY,
    title TEXT NOT NULL,
    content TEXT NOT NULL,
    type TEXT NOT NULL DEFAULT 'ANNOUNCEMENT',
    is_active BOOLEAN NOT NULL DEFAULT true,
    competition_id TEXT REFERENCES public.competitions(id) ON DELETE SET NULL,
    starts_at TIMESTAMPTZ,
    ends_at TIMESTAMPTZ,
    created_by TEXT NOT NULL DEFAULT 'admin',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_announcements_active ON public.home_announcements(is_active, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_announcements_comp ON public.home_announcements(competition_id);

CREATE OR REPLACE VIEW public.announcements AS SELECT * FROM public.home_announcements;

-- ۱۲. جدول تیکت‌های پشتیبانی کاربران (Support Tickets)
CREATE TABLE IF NOT EXISTS public.support_tickets (
    id TEXT PRIMARY KEY,
    tracking_code TEXT NOT NULL UNIQUE,
    user_id TEXT NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
    subject TEXT NOT NULL,
    category TEXT NOT NULL DEFAULT 'OTHER',
    status TEXT NOT NULL DEFAULT 'NEW',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_message_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_support_tickets_user ON public.support_tickets(user_id, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_support_tickets_status ON public.support_tickets(status, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_support_tickets_tracking ON public.support_tickets(tracking_code);

-- ۱۳. جدول پیام‌های درون هر تیکت پشتیبانی (Support Messages)
CREATE TABLE IF NOT EXISTS public.support_messages (
    id TEXT PRIMARY KEY,
    ticket_id TEXT NOT NULL REFERENCES public.support_tickets(id) ON DELETE CASCADE,
    sender_type TEXT NOT NULL CHECK (sender_type IN ('USER', 'ADMIN')),
    sender_id TEXT NOT NULL,
    sender_name TEXT NOT NULL,
    message_text TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_support_messages_ticket ON public.support_messages(ticket_id, created_at ASC);

-- ۱۴. جدول بسته‌های فروشگاهی (Store Products)
CREATE TABLE IF NOT EXISTS public.store_products (
    id TEXT PRIMARY KEY,
    title TEXT NOT NULL,
    description TEXT NOT NULL,
    product_type TEXT NOT NULL DEFAULT 'SQUARE_UNLOCK',
    units_granted INTEGER NOT NULL DEFAULT 0,
    price_toman BIGINT NOT NULL DEFAULT 0,
    myket_sku TEXT NOT NULL,
    badge TEXT DEFAULT '',
    is_active BOOLEAN NOT NULL DEFAULT true,
    is_ready_in_myket BOOLEAN NOT NULL DEFAULT false,
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_store_products_active ON public.store_products(is_active, sort_order ASC);

-- ۱۵. جدول مزایا و اعتبارات حساب کاربری (User Entitlements)
CREATE TABLE IF NOT EXISTS public.user_entitlements (
    user_id TEXT PRIMARY KEY,
    instant_unlock_credits INTEGER NOT NULL DEFAULT 0,
    is_vip BOOLEAN NOT NULL DEFAULT false,
    total_purchased_toman BIGINT NOT NULL DEFAULT 0,
    account_tier TEXT NOT NULL DEFAULT 'NORMAL',
    multiplier INTEGER NOT NULL DEFAULT 1,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ۱۶. جدول دسته‌بندی جوایز (Reward Categories)
CREATE TABLE IF NOT EXISTS public.reward_categories (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    slug TEXT NOT NULL UNIQUE,
    icon TEXT DEFAULT 'gift',
    sort_order INTEGER NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_reward_categories_active ON public.reward_categories(is_active, sort_order ASC);

-- ۱۷. جدول جوایز (Rewards)
CREATE TABLE IF NOT EXISTS public.rewards (
    id TEXT PRIMARY KEY,
    title TEXT NOT NULL,
    description TEXT DEFAULT '',
    category_id TEXT REFERENCES public.reward_categories(id) ON DELETE SET NULL,
    cost_points INTEGER NOT NULL CHECK (cost_points >= 0),
    stock_quantity INTEGER NOT NULL DEFAULT 0 CHECK (stock_quantity >= 0),
    image_url TEXT DEFAULT '',
    additional_images JSONB DEFAULT '[]',
    is_featured BOOLEAN NOT NULL DEFAULT false,
    is_active BOOLEAN NOT NULL DEFAULT true,
    sort_order INTEGER NOT NULL DEFAULT 0,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_rewards_active ON public.rewards(is_active, sort_order ASC);
CREATE INDEX IF NOT EXISTS idx_rewards_category ON public.rewards(category_id);

-- ۱۸. جدول تنظیمات ظاهری، متن‌ها و بنر ویترین جوایز (Reward Appearance)
CREATE TABLE IF NOT EXISTS public.reward_appearance (
    id TEXT PRIMARY KEY DEFAULT 'default',
    hero_title TEXT NOT NULL DEFAULT 'ویترین جوایز و پاداش‌های ارزشمند',
    hero_subtitle TEXT DEFAULT 'امتیازهای کسب‌شده خود در مسابقات را به کارت شارژ، بسته‌های اینترنت و جوایز نقدی تبدیل کنید',
    hero_banner_url TEXT DEFAULT '',
    primary_color TEXT DEFAULT '#10B981',
    active_sections JSONB DEFAULT '{"featured": true, "categories": true, "history": true, "banners": true}',
    terms_text TEXT DEFAULT 'درخواست‌های دریافت جایزه پس از بررسی توسط تیم پشتیبانی ظرف ۲۴ الی ۷۲ ساعت کاری پردازش و ارسال خواهند شد.',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- درج رکورد پیش‌فرض ظاهر جوایز
INSERT INTO public.reward_appearance (id, hero_banner_url)
VALUES ('default', '')
ON CONFLICT (id) DO NOTHING;

-- ۱۹. جدول درخواست‌های دریافت جایزه توسط کاربران (Reward Claims)
CREATE TABLE IF NOT EXISTS public.reward_claims (
    id TEXT PRIMARY KEY,
    tracking_code TEXT NOT NULL UNIQUE,
    user_id TEXT NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
    reward_id TEXT NOT NULL REFERENCES public.rewards(id) ON DELETE RESTRICT,
    cost_points INTEGER NOT NULL CHECK (cost_points >= 0),
    status TEXT NOT NULL DEFAULT 'PENDING',
    recipient_name TEXT DEFAULT '',
    phone TEXT DEFAULT '',
    shipping_address TEXT DEFAULT '',
    admin_notes TEXT DEFAULT '',
    tracking_number TEXT DEFAULT '',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_reward_claims_user ON public.reward_claims(user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_reward_claims_status ON public.reward_claims(status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_reward_claims_tracking ON public.reward_claims(tracking_code);

-- ۲۰. جدول تنظیمات اعلان صفحه ورود ویژه مشترکین ایرانسل (Login Notification Settings)
CREATE TABLE IF NOT EXISTS public.login_notification_settings (
    id TEXT PRIMARY KEY DEFAULT 'default',
    is_visible BOOLEAN NOT NULL DEFAULT true,
    badge_text TEXT NOT NULL DEFAULT 'سرویس اختصاصی مشترکین ایرانسل',
    badge_icon TEXT NOT NULL DEFAULT 'verified',
    main_title TEXT NOT NULL DEFAULT 'سامانه منحصرأ ویژه مشترکان ارجمند ایرانسل',
    main_description TEXT NOT NULL DEFAULT 'با کمال احترام و افتخار، این سامانه منحصراً جهت حضور، رقابت و بهره‌مندی مشترکین گرامی و همراهان وفادار اپراتور ایرانسل طراحی گردیده است.',
    warning_is_visible BOOLEAN NOT NULL DEFAULT true,
    warning_title TEXT NOT NULL DEFAULT 'شرط الزامی: ورود با «اینترنت دیتای ایرانسل»',
    warning_description TEXT NOT NULL DEFAULT 'لطفاً پیش از ورود، وای‌فای و فیلترشکن را خاموش نموده و از طریق اینترنت همراه سیم‌کارت ایرانسل متصل گردید.',
    warning_icon TEXT NOT NULL DEFAULT 'signal_cellular_alt',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- درج رکورد پیش‌فرض اعلان ورود
INSERT INTO public.login_notification_settings (id)
VALUES ('default')
ON CONFLICT (id) DO NOTHING;

-- ۲۱. نمای جدول امتیازات و رتبه‌بندی زنده (Leaderboard View)
CREATE OR REPLACE VIEW public.leaderboard AS
SELECT 
    u.id AS user_id,
    u.first_name,
    u.last_name,
    COALESCE(s.total_score, u.total_score, 0) AS total_score,
    u.created_at,
    ROW_NUMBER() OVER (ORDER BY COALESCE(s.total_score, u.total_score, 0) DESC, u.created_at ASC) AS rank
FROM public.users u
LEFT JOIN public.user_scores s ON u.id = s.user_id;

-- درج بسته‌های اولیه فروشگاه مایکت در صورت عدم وجود
INSERT INTO public.store_products (id, title, description, product_type, units_granted, price_toman, myket_sku, badge, sort_order)
VALUES 
  ('PKG_1', 'بسته برنزی (۱۰ خانه)', 'بازگشایی فوری ۱۰ خانه از مسابقه بدون مشاهده تبلیغات', 'SQUARE_UNLOCK', 10, 10000, 'reqabat_pkg_10', 'محبوب', 1),
  ('PKG_2', 'بسته نقره‌ای (۳۰ خانه)', 'بازگشایی فوری ۳۰ خانه از مسابقه با ضریب امتیاز ۲ برابری', 'SQUARE_UNLOCK', 30, 25000, 'reqabat_pkg_30', 'ویژه', 2),
  ('PKG_3', 'بسته طلایی (۱۰۰ خانه کامل)', 'بازگشایی کامل تمامی ۱۰۰ خانه مسابقه با ضریب ۳ برابری', 'SQUARE_UNLOCK', 100, 70000, 'reqabat_pkg_100', 'پیشنهاد طلایی', 3)
ON CONFLICT (id) DO NOTHING;
