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


