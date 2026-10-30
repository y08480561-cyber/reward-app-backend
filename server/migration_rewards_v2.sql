-- ====================================================================
-- مهارجرت امن و غیرمخرب برای ماژول جوایز (Rewards Module v2)
-- افزودن قابلیت‌های امنیتی و تراکنشی
-- ====================================================================

-- ۱. افزودن ستون کلید یکتای تکرار (Idempotency Key) برای جلوگیری از ثبت درخواست تکراری
ALTER TABLE public.reward_claims ADD COLUMN IF NOT EXISTS idempotency_key TEXT;
CREATE UNIQUE INDEX IF NOT EXISTS idx_reward_claims_idempotency ON public.reward_claims(idempotency_key) WHERE idempotency_key IS NOT NULL;

-- ۲. افزودن قید برای جلوگیری از منفی شدن موجودی جوایز (در صورت نبود)
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.constraint_column_usage WHERE table_name = 'rewards' AND constraint_name = 'rewards_stock_quantity_check') THEN
        ALTER TABLE public.rewards ADD CONSTRAINT rewards_stock_quantity_check CHECK (stock_quantity >= 0);
    END IF;
EXCEPTION
    WHEN OTHERS THEN
        NULL;
END $$;

-- ۳. افزودن قید برای جلوگیری از منفی شدن امتیاز کاربر (در صورت نبود)
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.constraint_column_usage WHERE table_name = 'user_scores' AND constraint_name = 'user_scores_total_score_check') THEN
        ALTER TABLE public.user_scores ADD CONSTRAINT user_scores_total_score_check CHECK (total_score >= 0);
    END IF;
EXCEPTION
    WHEN OTHERS THEN
        NULL;
END $$;
