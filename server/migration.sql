-- ====================================================================
-- اسکریپت ایمن و غیرمخرب همگام‌سازی پایگاه داده Supabase PostgreSQL
-- پروژه: رقابت کن، جایزه ببر
-- هیچ جدولی حذف نمی‌شود و هیچ داده‌ای از بین نمی‌رود
-- ====================================================================

-- ۱. اطمینان از وجود ستون‌های موردنیاز در جدول public.users (بدون خطای ۴۲۷۰۳)
ALTER TABLE public.users ADD COLUMN IF NOT EXISTS total_score INTEGER NOT NULL DEFAULT 0;
ALTER TABLE public.users ADD COLUMN IF NOT EXISTS status TEXT NOT NULL DEFAULT 'active';
ALTER TABLE public.users ADD COLUMN IF NOT EXISTS auth_method TEXT DEFAULT 'DIRECT_CREDENTIALS';
ALTER TABLE public.users ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT NOW();
ALTER TABLE public.users ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

CREATE UNIQUE INDEX IF NOT EXISTS idx_users_phone_unique ON public.users(phone_number);
CREATE INDEX IF NOT EXISTS idx_users_score ON public.users(total_score DESC);

-- ۲. جدول پیگیری مجزای امتیاز کاربران
CREATE TABLE IF NOT EXISTS public.user_scores (
    user_id TEXT PRIMARY KEY,
    total_score INTEGER NOT NULL DEFAULT 0 CHECK (total_score >= 0),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_user_scores_total ON public.user_scores(total_score DESC);

-- ۳. جدول چرخه‌های ۲۴ ساعته ۱۰۰ مربع
CREATE TABLE IF NOT EXISTS public.daily_cycles (
    cycle_id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL,
    start_time BIGINT NOT NULL,
    end_time BIGINT NOT NULL,
    status TEXT NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_daily_cycles_user ON public.daily_cycles(user_id, start_time, end_time);

-- ۴. جدول قفل وضعیت ۱۰۰ مربع روزانه به ازای هر کاربر
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

-- ۵. جدول نشست‌های تماشای تبلیغ و توکن ضد تقلب (Anti-Replay Nonce)
CREATE TABLE IF NOT EXISTS public.ad_sessions (
    session_id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL,
    square_number INTEGER NOT NULL CHECK (square_number BETWEEN 1 AND 100),
    cycle_id TEXT NOT NULL,
    timestamp BIGINT NOT NULL,
    ad_duration_seconds INTEGER NOT NULL,
    status TEXT NOT NULL DEFAULT 'COMPLETED',
    token TEXT UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_ad_sessions_user ON public.ad_sessions(user_id);
CREATE INDEX IF NOT EXISTS idx_ad_sessions_token ON public.ad_sessions(token);

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_class c
    JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE n.nspname = 'public' AND c.relname = 'leaderboard'
  ) THEN
    CREATE VIEW public.leaderboard AS
    SELECT 
      u.id AS user_id,
      u.first_name,
      u.last_name,
      COALESCE(s.total_score, u.total_score, 0) AS total_score,
      u.created_at,
      ROW_NUMBER() OVER (ORDER BY COALESCE(s.total_score, u.total_score, 0) DESC, u.created_at ASC) AS rank
    FROM public.users u
    LEFT JOIN public.user_scores s ON u.id = s.user_id;
  END IF;
END $$;

-- ۷. جدول مسابقات ادواری (مدیریت دوره‌های مسابقه)
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

-- ۸. جدول آرشیو دائمی نتایج مسابقات پایان‌یافته
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

-- ۹. جدول لاگ‌های نظارتی و عملیاتی مسابقات (Audit Trail)
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

-- ۱۰. افزودن شناسه مسابقه به نشست‌های تماشای تبلیغ (ارتباط دقیق با دوره فعال)
ALTER TABLE public.ad_sessions ADD COLUMN IF NOT EXISTS competition_id TEXT;
CREATE INDEX IF NOT EXISTS idx_ad_sessions_competition ON public.ad_sessions(competition_id);

-- ۱۱. جدول پیام‌ها و اطلاعیه‌های صفحه اصلی اپلیکیشن
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

-- ۱۲. توکن احراز هویت نشست کاربری امن در جدول کاربران
ALTER TABLE public.users ADD COLUMN IF NOT EXISTS auth_token TEXT;
CREATE INDEX IF NOT EXISTS idx_users_auth_token ON public.users(auth_token);

-- ۱۳. جدول تیکت‌های پشتیبانی کاربران (Support Tickets)
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

-- ۱۴. جدول پیام‌ها و مکالمات درون هر تیکت (Support Messages)
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

-- ۱۵. جدول بسته‌ها و محصولات فروشگاه درون‌برنامه‌ای (Store Products)
CREATE TABLE IF NOT EXISTS public.store_products (
    id TEXT PRIMARY KEY,
    title TEXT NOT NULL,
    description TEXT NOT NULL,
    product_type TEXT NOT NULL DEFAULT 'SQUARE_UNLOCK', -- 'SQUARE_UNLOCK' or 'ACCOUNT_UPGRADE'
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

-- ۱۶. جدول سهمیه‌های کاربری (User Entitlements & Instant Unlock Credits)
CREATE TABLE IF NOT EXISTS public.user_entitlements (
    user_id TEXT PRIMARY KEY,
    instant_unlock_credits INTEGER NOT NULL DEFAULT 0,
    is_vip BOOLEAN NOT NULL DEFAULT false,
    total_purchased_toman BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ۱۷. ارتقای ستون‌های جدول کاربران و پرداخت‌ها
ALTER TABLE public.users ADD COLUMN IF NOT EXISTS instant_unlock_credits INTEGER NOT NULL DEFAULT 0;
ALTER TABLE public.users ADD COLUMN IF NOT EXISTS is_vip BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE public.purchases ADD COLUMN IF NOT EXISTS myket_order_id TEXT;
ALTER TABLE public.purchases ADD COLUMN IF NOT EXISTS units_granted INTEGER DEFAULT 0;
ALTER TABLE public.purchases ADD COLUMN IF NOT EXISTS error_message TEXT;
ALTER TABLE public.purchases ADD COLUMN IF NOT EXISTS verification_source TEXT DEFAULT 'MYKET_IAB';
CREATE UNIQUE INDEX IF NOT EXISTS idx_purchases_token ON public.purchases(purchase_token) WHERE purchase_token IS NOT NULL;

-- ۱۸. ثبت بسته‌های پیش‌فرض فروشگاه
INSERT INTO public.store_products 
    (id, title, description, product_type, units_granted, price_toman, myket_sku, badge, is_active, is_ready_in_myket, sort_order)
VALUES 
    ('pkg_squares_10', 'بسته بازگشایی ۱۰ مربع', 'بازگشایی سریع و آنی ۱۰ مربع مسابقه همراه با ثبت کامل ۱۰ امتیاز بدون نیاز به مشاهده تبلیغ.', 'SQUARE_UNLOCK', 10, 29000, 'reqabat_unlock_10', 'سریع', true, false, 1),
    ('pkg_squares_25', 'بسته بازگشایی ۲۵ مربع', 'صرفه‌جویی عالی در زمان! ۲۵ مربع مسابقه را بلافاصله باز کرده و ۲۵ امتیاز به حساب خود واریز کنید.', 'SQUARE_UNLOCK', 25, 59000, 'reqabat_unlock_25', 'محبوب‌ترین', true, false, 2),
    ('pkg_squares_50', 'بسته بازگشایی ۵۰ مربع', 'نیمی از ۱۰۰ مربع روزانه را با یک کلیک تکمیل کنید و ۵۰ امتیاز مستقیم در جدول رتبه‌بندی بدست آورید.', 'SQUARE_UNLOCK', 50, 99000, 'reqabat_unlock_50', 'به‌صرفه', true, false, 3),
    ('pkg_account_vip', 'بسته ارتقای حساب کاربری (VIP)', 'عضویت طلایی مسابقه: دو برابر شدن تمام امتیازها در هر چرخه، اولویت در قرعه‌کشی و پشتیبانی اختصاصی.', 'ACCOUNT_UPGRADE', 1, 149000, 'reqabat_vip_upgrade', 'ویژه مسابقه', true, false, 4)
ON CONFLICT (id) DO NOTHING;

-- ۱۹. جدول دسته‌بندی جوایز (Reward Categories)
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

-- ۲۰. جدول جوایز و هدایا (Rewards)
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
CREATE INDEX IF NOT EXISTS idx_rewards_featured ON public.rewards(is_featured);

-- ۲۱. جدول تنظیمات ظاهری و بنرهای ویترین جوایز (Reward Appearance)
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

-- ۲۲. جدول درخواست‌های ثبت‌شده برای دریافت جایزه (Reward Claims)
CREATE TABLE IF NOT EXISTS public.reward_claims (
    id TEXT PRIMARY KEY,
    tracking_code TEXT NOT NULL UNIQUE,
    user_id TEXT NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
    reward_id TEXT NOT NULL REFERENCES public.rewards(id) ON DELETE RESTRICT,
    cost_points INTEGER NOT NULL CHECK (cost_points >= 0),
    status TEXT NOT NULL DEFAULT 'PENDING', -- PENDING, APPROVED, PROCESSING, SHIPPED, COMPLETED, REJECTED
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

-- ۲۳. درج داده‌های اولیه نمونه برای دسته‌بندی‌ها، جوایز و تنظیمات ظاهر
INSERT INTO public.reward_categories (id, name, slug, icon, sort_order, is_active)
VALUES 
    ('cat_recharge', 'کارت شارژ', 'recharge-cards', 'smartphone', 1, true),
    ('cat_internet', 'بسته‌های اینترنت', 'internet-packages', 'wifi', 2, true),
    ('cat_cash', 'جوایز نقدی و هدیه', 'cash-gifts', 'credit-card', 3, true),
    ('cat_digital', 'گیفت کارت دیجیتال', 'digital-giftcards', 'gift', 4, true)
ON CONFLICT (id) DO NOTHING;

INSERT INTO public.reward_appearance (id, hero_title, hero_subtitle, hero_banner_url, primary_color, active_sections, terms_text)
VALUES (
    'default',
    'ویترین جوایز و پاداش‌های ارزشمند',
    'امتیازهای کسب‌شده خود در مسابقات را به کارت شارژ، بسته‌های اینترنت و جوایز نقدی تبدیل کنید',
    'https://images.unsplash.com/photo-1513151233558-d860c5398176?w=800&q=80',
    '#10B981',
    '{"featured": true, "categories": true, "history": true, "banners": true}',
    'درخواست‌های دریافت جایزه پس از بررسی توسط تیم پشتیبانی ظرف ۲۴ الی ۷۲ ساعت کاری پردازش و ارسال خواهند شد.'
) ON CONFLICT (id) DO NOTHING;

INSERT INTO public.rewards 
    (id, title, description, category_id, cost_points, stock_quantity, image_url, is_featured, is_active, sort_order)
VALUES 
    ('rew_irancell_10k', 'کارت شارژ ۱۰ هزار تومانی ایرانسل', 'کد شارژ مستقیم ۱۰,۰۰۰ تومانی برای سیم‌کارت‌های اعتباری ایرانسل ارسال از طریق پیامک.', 'cat_recharge', 100, 50, 'https://images.unsplash.com/photo-1556742049-0a67568d0d9f?w=600&q=80', true, true, 1),
    ('rew_mci_10k', 'کارت شارژ ۱۰ هزار تومانی همراه اول', 'کد شارژ مستقیم ۱۰,۰۰۰ تومانی برای سیم‌کارت‌های اعتباری همراه اول.', 'cat_recharge', 100, 50, 'https://images.unsplash.com/photo-1556742049-0a67568d0d9f?w=600&q=80', false, true, 2),
    ('rew_irancell_net_5gb', 'بسته اینترنت ۵ گیگابایت یک‌ماهه', 'فعالسازی مستقیم بسته اینترنت ۵ گیگابایت یک‌ماهه بر روی شماره ایرانسل ثبت‌شده شما.', 'cat_internet', 250, 30, 'https://images.unsplash.com/photo-1544717297-fa95b6ee9643?w=600&q=80', true, true, 3),
    ('rew_cash_50k', 'جایزه نقدی ۵۰,۰۰۰ تومانی', 'واریز پایا مستقیم به کارت بانکی متصل به شماره همراه شما.', 'cat_cash', 500, 20, 'https://images.unsplash.com/photo-1580519542036-c47de6196ba5?w=600&q=80', true, true, 4)
ON CONFLICT (id) DO NOTHING;





