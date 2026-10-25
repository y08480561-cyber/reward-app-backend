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

-- ۳. جدول چرخه‌های ۲۴ ساعته ۵۰ مربع
CREATE TABLE IF NOT EXISTS public.daily_cycles (
    cycle_id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL,
    start_time BIGINT NOT NULL,
    end_time BIGINT NOT NULL,
    status TEXT NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_daily_cycles_user ON public.daily_cycles(user_id, start_time, end_time);

-- ۴. جدول قفل وضعیت ۵۰ مربع روزانه به ازای هر کاربر
CREATE TABLE IF NOT EXISTS public.square_states (
    state_key TEXT PRIMARY KEY,
    user_id TEXT NOT NULL,
    cycle_id TEXT NOT NULL,
    square_number INTEGER NOT NULL CHECK (square_number BETWEEN 1 AND 50),
    status TEXT NOT NULL DEFAULT 'COMPLETED',
    completed_at BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_square_states_lookup ON public.square_states(user_id, cycle_id, square_number);

-- ۵. جدول نشست‌های تماشای تبلیغ و توکن ضد تقلب (Anti-Replay Nonce)
CREATE TABLE IF NOT EXISTS public.ad_sessions (
    session_id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL,
    square_number INTEGER NOT NULL CHECK (square_number BETWEEN 1 AND 50),
    cycle_id TEXT NOT NULL,
    timestamp BIGINT NOT NULL,
    ad_duration_seconds INTEGER NOT NULL,
    status TEXT NOT NULL DEFAULT 'COMPLETED',
    token TEXT UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_ad_sessions_user ON public.ad_sessions(user_id);
CREATE INDEX IF NOT EXISTS idx_ad_sessions_token ON public.ad_sessions(token);

-- ۶. ویو رتبه‌بندی عمومی کاربران
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
