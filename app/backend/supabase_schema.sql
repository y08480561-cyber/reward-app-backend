-- ====================================================================
-- طرح پایگاه داده و منطق امنیتی Supabase PostgreSQL (نسخه امن و بدون رخنه)
-- برنامه مسابقه و جایزه (رقابت کن، جایزه ببر)
-- ====================================================================

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- --------------------------------------------------------------------
-- ۱. جدول کاربران (users) - متصل به auth.users
-- --------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.users (
    id UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    first_name TEXT NOT NULL,
    last_name TEXT NOT NULL,
    phone_number TEXT NOT NULL UNIQUE,
    total_score INTEGER NOT NULL DEFAULT 0 CHECK (total_score >= 0),
    status TEXT NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'suspended', 'banned')),
    device_id TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_users_phone ON public.users(phone_number);
CREATE INDEX IF NOT EXISTS idx_users_score ON public.users(total_score DESC);

-- --------------------------------------------------------------------
-- ۲. جدول مدیران مجاز سیستم (admin_users) - متصل به auth.users
-- --------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.admin_users (
    id UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    email TEXT NOT NULL UNIQUE,
    role TEXT NOT NULL DEFAULT 'super_admin' CHECK (role IN ('super_admin', 'moderator')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- --------------------------------------------------------------------
-- ۳. جدول تاریخچه تراکنش‌های امتیاز (score_transactions)
-- ضد تقلب: هر nonce فقط یک بار می‌تواند استفاده شود
-- --------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.score_transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
    points INTEGER NOT NULL DEFAULT 1 CHECK (points > 0),
    reason TEXT NOT NULL DEFAULT 'ad_reward',
    square_number INTEGER NOT NULL CHECK (square_number BETWEEN 1 AND 100),
    nonce TEXT NOT NULL UNIQUE,
    status TEXT NOT NULL DEFAULT 'verified' CHECK (status IN ('verified', 'rejected')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_score_tx_user ON public.score_transactions(user_id);
CREATE INDEX IF NOT EXISTS idx_score_tx_nonce ON public.score_transactions(nonce);

-- --------------------------------------------------------------------
-- ۴. جدول قفل روزانه ۱۰۰ مربع (user_daily_squares)
-- تضمین قطعی: هر مربع در هر روز تقویمی فقط یک بار قابل تکمیل است
-- --------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.user_daily_squares (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
    cycle_date DATE NOT NULL DEFAULT CURRENT_DATE,
    square_number INTEGER NOT NULL CHECK (square_number BETWEEN 1 AND 100),
    completed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_user_cycle_square UNIQUE (user_id, cycle_date, square_number)
);

CREATE INDEX IF NOT EXISTS idx_user_daily_squares ON public.user_daily_squares(user_id, cycle_date);

-- --------------------------------------------------------------------
-- ۵. جدول مسابقات (competitions) و جوایز (prizes) و برندگان (winners)
-- --------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.competitions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title TEXT NOT NULL,
    description TEXT,
    start_date TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    end_date TIMESTAMPTZ NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS public.prizes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    competition_id UUID REFERENCES public.competitions(id) ON DELETE CASCADE,
    title TEXT NOT NULL,
    rank_required TEXT NOT NULL,
    value_text TEXT NOT NULL,
    description TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS public.winners (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    competition_id UUID REFERENCES public.competitions(id) ON DELETE CASCADE,
    prize_id UUID REFERENCES public.prizes(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
    rank_achieved INTEGER NOT NULL,
    delivery_status TEXT NOT NULL DEFAULT 'pending' CHECK (delivery_status IN ('pending', 'contacted', 'sent', 'delivered')),
    delivery_notes TEXT,
    awarded_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ====================================================================
-- توابع کمکی بررسی مجوزها (Security Helper Functions)
-- ====================================================================

-- بررسی اینکه آیا کاربر احراز هویت‌شده فعلی مدیر سیستم است یا خیر
CREATE OR REPLACE FUNCTION public.is_admin()
RETURNS BOOLEAN
LANGUAGE sql
SECURITY DEFINER
SET search_path = public
STABLE
AS $$
    SELECT EXISTS (
        SELECT 1 FROM public.admin_users
        WHERE id = auth.uid()
    );
$$;

-- ====================================================================
-- امنیت و سیاست‌های Row Level Security (RLS) - اصلاح‌شده و بدون نشت اطلاعات
-- ====================================================================

ALTER TABLE public.users ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.admin_users ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.score_transactions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.user_daily_squares ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.competitions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.prizes ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.winners ENABLE ROW LEVEL SECURITY;

-- سیاست ۱: کاربران ناشناس هیچ دسترسی مستقیمی به جدول users ندارند!
-- هر کاربر احرازشده فقط و فقط ردیف متعلق به خودش را می‌تواند بخواند
DROP POLICY IF EXISTS "Users can read own profile" ON public.users;
CREATE POLICY "Users can only read own profile" ON public.users
FOR SELECT TO authenticated
USING (auth.uid() = id);

-- مدیران می‌توانند تمامی کاربران را مشاهده و مدیریت کنند
CREATE POLICY "Admins can view all users" ON public.users
FOR ALL TO authenticated
USING (public.is_admin());

-- سیاست جدول تراکنش‌های امتیاز: کاربر فقط تراکنش‌های خودش را می‌بیند
CREATE POLICY "Users can view own transactions" ON public.score_transactions
FOR SELECT TO authenticated
USING (auth.uid() = user_id);

CREATE POLICY "Admins can view all transactions" ON public.score_transactions
FOR ALL TO authenticated
USING (public.is_admin());

-- سیاست جدول ۵۰ مربع روزانه: کاربر فقط وضعیت مربع‌های خودش را می‌بیند
CREATE POLICY "Users can view own daily squares" ON public.user_daily_squares
FOR SELECT TO authenticated
USING (auth.uid() = user_id);

CREATE POLICY "Admins can view all daily squares" ON public.user_daily_squares
FOR ALL TO authenticated
USING (public.is_admin());

-- سیاست‌های عمومی مسابقات و جوایز: همه می‌توانند اطلاعات مسابقه را ببینند
CREATE POLICY "Public read competitions" ON public.competitions FOR SELECT USING (true);
CREATE POLICY "Public read prizes" ON public.prizes FOR SELECT USING (true);
CREATE POLICY "Public read winners" ON public.winners FOR SELECT USING (true);

CREATE POLICY "Admins manage competitions" ON public.competitions FOR ALL TO authenticated USING (public.is_admin());
CREATE POLICY "Admins manage prizes" ON public.prizes FOR ALL TO authenticated USING (public.is_admin());
CREATE POLICY "Admins manage winners" ON public.winners FOR ALL TO authenticated USING (public.is_admin());

-- سیاست جدول مدیران: فقط خود مدیر می‌تواند مشخصات خود را ببیند
CREATE POLICY "Admins can view admin_users" ON public.admin_users
FOR SELECT TO authenticated
USING (public.is_admin());

-- ====================================================================
-- توابع امن سمت سرور (Stored Procedures)
-- ====================================================================

-- تابع ۱: اعطای ۱ امتیاز مشاهده تبلیغ (اتمیک، ضد تقلب، وابسته به هویت احرازشده)
CREATE OR REPLACE FUNCTION public.rpc_award_ad_point(
    p_square_number INTEGER,
    p_nonce TEXT
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_user_id UUID;
    v_user public.users%ROWTYPE;
    v_new_score INTEGER;
    v_today DATE := CURRENT_DATE;
    v_last_ad_time TIMESTAMPTZ;
BEGIN
    -- ۱. احراز هویت: شناسه کاربر مستقیماً از توکن معتبر Supabase Auth استخراج می‌شود و از کلاینت گرفته نمی‌شود
    v_user_id := auth.uid();
    IF v_user_id IS NULL THEN
        RETURN jsonb_build_object('success', FALSE, 'message', 'خطای احراز هویت: ابتدا باید وارد حساب کاربری خود شوید.');
    END IF;

    -- ۲. اعتبارسنجی شماره مربع (۱ تا ۱۰۰)
    IF p_square_number < 1 OR p_square_number > 100 THEN
        RETURN jsonb_build_object('success', FALSE, 'message', 'شماره مربع نامعتبر است (۱ تا ۱۰۰).');
    END IF;

    -- ۳. اعتبارسنجی وضعیت کاربر
    SELECT * INTO v_user FROM public.users WHERE id = v_user_id;
    IF NOT FOUND THEN
        RETURN jsonb_build_object('success', FALSE, 'message', 'پروفایل کاربری یافت نشد.');
    END IF;

    IF v_user.status != 'active' THEN
        RETURN jsonb_build_object('success', FALSE, 'message', 'حساب کاربری شما مسدود یا غیرفعال است.');
    END IF;

    -- ۴. بررسی عدم ثبت تکراری Nonce
    IF EXISTS (SELECT 1 FROM public.score_transactions WHERE nonce = p_nonce) THEN
        RETURN jsonb_build_object('success', FALSE, 'message', 'این درخواست قبلاً ثبت شده است (توکن تکراری).');
    END IF;

    -- ۵. بررسی عدم تکمیل مجدد همین مربع در چرخه امروز
    IF EXISTS (
        SELECT 1 FROM public.user_daily_squares
        WHERE user_id = v_user_id AND cycle_date = v_today AND square_number = p_square_number
    ) THEN
        RETURN jsonb_build_object('success', FALSE, 'message', 'امتیاز این مربع برای چرخه امروز قبلاً دریافت شده است.');
    END IF;

    -- ۶. بررسی نرخ مصرف و فاصله زمانی (حداقل ۱۰ ثانیه بین دو تماشای متوالی برای جلوگیری از اسپم اسکریپتی)
    SELECT created_at INTO v_last_ad_time
    FROM public.score_transactions
    WHERE user_id = v_user_id
    ORDER BY created_at DESC
    LIMIT 1;

    IF v_last_ad_time IS NOT NULL AND (NOW() - v_last_ad_time) < INTERVAL '10 seconds' THEN
        RETURN jsonb_build_object('success', FALSE, 'message', 'فاصله زمانی بین تماشای تبلیغات رعایت نشده است. لطفاً کمی صبر کنید.');
    END IF;

    -- ۷. ثبت اتمیک در جدول روزانه مربع‌ها
    INSERT INTO public.user_daily_squares (user_id, cycle_date, square_number)
    VALUES (v_user_id, v_today, p_square_number);

    -- ۸. ثبت در تاریخچه تراکنش‌ها
    INSERT INTO public.score_transactions (user_id, points, reason, square_number, nonce, status)
    VALUES (v_user_id, 1, 'ad_reward', p_square_number, p_nonce, 'verified');

    -- ۹. افزایش اتمیک مجموع امتیاز در جدول کاربران
    UPDATE public.users
    SET total_score = total_score + 1,
        updated_at = NOW()
    WHERE id = v_user_id
    RETURNING total_score INTO v_new_score;

    RETURN jsonb_build_object(
        'success', TRUE,
        'new_total_score', v_new_score,
        'square_number', p_square_number,
        'message', '۱ امتیاز مسابقه با موفقیت ثبت شد.'
    );
END;
$$;

-- تابع ۲: دریافت رتبه‌بندی عمومی (امن، بدون افشای شماره تلفن و با نام ماسک‌شده)
CREATE OR REPLACE FUNCTION public.rpc_get_leaderboard(p_limit INTEGER DEFAULT 50)
RETURNS TABLE (
    rank BIGINT,
    display_name TEXT,
    total_score INTEGER
)
LANGUAGE sql
SECURITY DEFINER
SET search_path = public
STABLE
AS $$
    SELECT
        ROW_NUMBER() OVER (ORDER BY u.total_score DESC, u.created_at ASC) AS rank,
        u.first_name || ' ' || SUBSTRING(u.last_name FROM 1 FOR 2) || '***' AS display_name,
        u.total_score
    FROM public.users u
    WHERE u.status = 'active'
    ORDER BY u.total_score DESC, u.created_at ASC
    LIMIT LEAST(p_limit, 100);
$$;

-- تابع ۳: دریافت آمار داشبورد پنل مدیریت (فقط قابل اجرا توسط مدیران تاییدشده)
CREATE OR REPLACE FUNCTION public.rpc_get_admin_dashboard_stats()
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_total_users BIGINT;
    v_total_score BIGINT;
    v_today_ads BIGINT;
    v_active_competitions BIGINT;
BEGIN
    -- بررسی قطعی دسترسی مدیریت
    IF NOT public.is_admin() THEN
        RAISE EXCEPTION 'دسترسی غیرمجاز: این متد فقط برای مدیران سیستم قابل فراخوانی است.';
    END IF;

    SELECT COUNT(*) INTO v_total_users FROM public.users;
    SELECT COALESCE(SUM(total_score), 0) INTO v_total_score FROM public.users;
    SELECT COUNT(*) INTO v_today_ads FROM public.user_daily_squares WHERE cycle_date = CURRENT_DATE;
    SELECT COUNT(*) INTO v_active_competitions FROM public.competitions WHERE is_active = TRUE;

    RETURN jsonb_build_object(
        'total_users', v_total_users,
        'total_score', v_total_score,
        'today_ads_viewed', v_today_ads,
        'active_competitions', v_active_competitions
    );
END;
$$;

-- جدول بسته‌های فروشگاه مایکت
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

-- جدول سهمیه‌های بازگشایی سریع
CREATE TABLE IF NOT EXISTS public.user_entitlements (
    user_id UUID PRIMARY KEY REFERENCES public.users(id) ON DELETE CASCADE,
    instant_unlock_credits INTEGER NOT NULL DEFAULT 0,
    is_vip BOOLEAN NOT NULL DEFAULT false,
    total_purchased_toman BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ستون‌های اعتباری در جدول کاربران
ALTER TABLE public.users ADD COLUMN IF NOT EXISTS instant_unlock_credits INTEGER NOT NULL DEFAULT 0;
ALTER TABLE public.users ADD COLUMN IF NOT EXISTS is_vip BOOLEAN NOT NULL DEFAULT false;

-- بسته‌های پیش‌فرض
INSERT INTO public.store_products 
    (id, title, description, product_type, units_granted, price_toman, myket_sku, badge, is_active, is_ready_in_myket, sort_order)
VALUES 
    ('pkg_squares_10', 'بسته بازگشایی ۱۰ مربع', 'بازگشایی سریع و آنی ۱۰ مربع مسابقه بدون نیاز به مشاهده تبلیغ.', 'SQUARE_UNLOCK', 10, 29000, 'reqabat_unlock_10', 'سریع', true, false, 1),
    ('pkg_squares_25', 'بسته بازگشایی ۲۵ مربع', 'صرفه‌جویی عالی در زمان! ۲۵ مربع مسابقه را بلافاصله باز کرده و ۲۵ امتیاز دریافت کنید.', 'SQUARE_UNLOCK', 25, 59000, 'reqabat_unlock_25', 'محبوب‌ترین', true, false, 2),
    ('pkg_squares_50', 'بسته بازگشایی ۵۰ مربع', 'نیمی از ۱۰۰ مربع روزانه را با یک کلیک تکمیل کنید و ۵۰ امتیاز بدست آورید.', 'SQUARE_UNLOCK', 50, 99000, 'reqabat_unlock_50', 'به‌صرفه', true, false, 3),
    ('pkg_account_vip', 'بسته ارتقای حساب کاربری (VIP)', 'عضویت ویژه طلایی مسابقه: دو برابر شدن امتیازها و اولویت در قرعه‌کشی.', 'ACCOUNT_UPGRADE', 1, 149000, 'reqabat_vip_upgrade', 'ویژه مسابقه', true, false, 4)
ON CONFLICT (id) DO NOTHING;

