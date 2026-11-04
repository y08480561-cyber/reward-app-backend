-- Migration: Feedback & Reward System
-- Description: Adds user_feedback table and reward tracking column

-- 1. Create table for internal feedback storage
CREATE TABLE IF NOT EXISTS public.user_feedback (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id TEXT NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
    rating INTEGER NOT NULL CHECK (rating BETWEEN 1 AND 5),
    comment TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 2. Add flag to users to prevent double rewarding
ALTER TABLE public.users ADD COLUMN IF NOT EXISTS feedback_reward_claimed BOOLEAN NOT NULL DEFAULT false;

-- 3. Index for performance
CREATE INDEX IF NOT EXISTS idx_feedback_user ON public.user_feedback(user_id);

-- 4. Audit Log entry for the reward (optional but good for tracking)
-- We can reuse ad_sessions or just trust user_feedback table
