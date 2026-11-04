-- ==============================================================================
-- اسکریپت جامع ساخت و آماده‌سازی جداول Supabase PostgreSQL برای پروژه «رقابت کن، جایزه ببر»
-- برای اجرا در: Supabase Dashboard > SQL Editor > New Query
-- توجه: این اسکریپت در صورت وجود جداول، خطایی ایجاد نمی‌کند (IF NOT EXISTS).
-- ==============================================================================

-- ۱. جدول کاربران
CREATE TABLE IF NOT EXISTS public.users (
    id SERIAL PRIMARY KEY,
    name VARCHAR(255),
    phone VARCHAR(20) UNIQUE NOT NULL,
    password_hash VARCHAR(255),
    score INT DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    last_login TIMESTAMP WITH TIME ZONE
);

-- ۲. جدول نشست‌های ورود (Sessions)
CREATE TABLE IF NOT EXISTS public.user_sessions (
    id SERIAL PRIMARY KEY,
    user_id INT REFERENCES public.users(id) ON DELETE CASCADE,
    token VARCHAR(255) UNIQUE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP WITH TIME ZONE
);

-- ۳. جدول تنظیمات ظاهری، اطلاعیه‌ها و بنر جوایز
CREATE TABLE IF NOT EXISTS public.reward_appearance (
    id SERIAL PRIMARY KEY,
    hero_banner_url TEXT,
    announcement_text TEXT,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- مقداردهی اولیه تنظیمات ظاهری در صورت خالی بودن
INSERT INTO public.reward_appearance (id, hero_banner_url, announcement_text)
SELECT 1, '', 'به سامانه جوایز و رقابت خوش آمدید'
WHERE NOT EXISTS (SELECT 1 FROM public.reward_appearance WHERE id = 1);

-- ۴. جدول جوایز
CREATE TABLE IF NOT EXISTS public.rewards (
    id SERIAL PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    score_cost INT NOT NULL,
    image_url TEXT,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- ۵. جدول درخواست‌های دریافت جایزه
CREATE TABLE IF NOT EXISTS public.reward_claims (
    id SERIAL PRIMARY KEY,
    user_id INT REFERENCES public.users(id),
    reward_id INT REFERENCES public.rewards(id),
    status VARCHAR(50) DEFAULT 'pending',
    tracking_code VARCHAR(100),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- ۶. جدول بسته‌های سکه فروشگاه
CREATE TABLE IF NOT EXISTS public.store_packages (
    id SERIAL PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    coins INT DEFAULT 0,
    price_toman INT NOT NULL,
    is_active BOOLEAN DEFAULT TRUE
);

-- ۷. جدول تراکنش‌های مالی و امتیازی
CREATE TABLE IF NOT EXISTS public.transactions (
    id SERIAL PRIMARY KEY,
    user_id INT REFERENCES public.users(id),
    amount INT NOT NULL,
    type VARCHAR(50),
    description TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- ۸. جدول خانه‌های رقابت (۱ تا ۱۰۰)
CREATE TABLE IF NOT EXISTS public.competition_squares (
    square_id INT PRIMARY KEY,
    owner_id INT REFERENCES public.users(id),
    score_value INT DEFAULT 10,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- مقداردهی اولیه ۱۰۰ خانه رقابت در صورت عدم وجود
INSERT INTO public.competition_squares (square_id, score_value)
SELECT s, 10
FROM generate_series(1, 100) AS s
WHERE NOT EXISTS (SELECT 1 FROM public.competition_squares WHERE square_id = s);

-- ۹. جدول تیکت‌های پشتیبانی
CREATE TABLE IF NOT EXISTS public.support_tickets (
    id SERIAL PRIMARY KEY,
    user_id INT REFERENCES public.users(id),
    subject VARCHAR(255),
    message TEXT NOT NULL,
    status VARCHAR(50) DEFAULT 'open',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- ۱۰. جدول اعلانات سراسری
CREATE TABLE IF NOT EXISTS public.announcements (
    id SERIAL PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- ایندکس‌های پیشنهادی برای افزایش سرعت کوئری‌ها
CREATE INDEX IF NOT EXISTS idx_users_phone ON public.users(phone);
CREATE INDEX IF NOT EXISTS idx_user_sessions_token ON public.user_sessions(token);
CREATE INDEX IF NOT EXISTS idx_reward_claims_user ON public.reward_claims(user_id);
CREATE INDEX IF NOT EXISTS idx_transactions_user ON public.transactions(user_id);
CREATE INDEX IF NOT EXISTS idx_support_tickets_user ON public.support_tickets(user_id);
