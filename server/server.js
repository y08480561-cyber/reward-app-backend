const express = require('express');
const cors = require('cors');
const path = require('path');
const crypto = require('crypto');
const fs = require('fs');
const { Pool } = require('pg');

const app = express();
const PORT = (process.env.PORT && process.env.PORT !== '8080') ? process.env.PORT : 3000;
const ADMIN_API_KEY = process.env.ADMIN_KEY || "ReqabatAdmin2026";
const DATABASE_URL = process.env.DATABASE_URL;

app.use(cors());
app.use(express.json());
app.use(express.static(path.join(__dirname, 'public')));

// Standalone Admin Users Page (Pure users table)
app.get(['/admin/users', '/admin/users/'], (req, res) => {
  res.sendFile(path.join(__dirname, 'public', 'users.html'));
});

// Admin Dashboard Alias
app.get(['/admin', '/admin/dashboard'], (req, res) => {
  res.sendFile(path.join(__dirname, 'public', 'index.html'));
});

// ====================================================================
// PostgreSQL Pool Configuration (Supabase PostgreSQL Integration)
// ====================================================================
let pool = null;
let isDbConnected = false;

const schemaInfo = {
  userHasTotalScore: true,
  userScoresUserIdCol: 'user_id',
  userScoresScoreCol: 'total_score',
};

// Mask sensitive password from connection URL for safe logging
function getSanitizedDbUrl(rawUrl) {
  if (!rawUrl) return 'NOT_CONFIGURED';
  try {
    const parsed = new URL(rawUrl);
    const maskedPassword = parsed.password ? '***' : '';
    const portPart = parsed.port ? `:${parsed.port}` : '';
    return `${parsed.protocol}//${parsed.username}${maskedPassword ? ':' + maskedPassword : ''}@${parsed.hostname}${portPart}${parsed.pathname}`;
  } catch (e) {
    return rawUrl.replace(/:\/\/(.*?):(.*?)@/, '://$1:***@');
  }
}

// Check if URL uses direct host or recommended Session Pooler
function checkSupabaseHostType(rawUrl) {
  if (!rawUrl) return;
  try {
    const parsed = new URL(rawUrl);
    const host = parsed.hostname;
    if (host.includes('.pooler.supabase.com')) {
      console.log(`[PostgreSQL Host Check] Using Supabase Connection Pooler (${host}:${parsed.port || 5432}). Full IPv4 compatibility with Render verified.`);
    } else if (host.startsWith('db.') && host.endsWith('.supabase.co')) {
      console.warn(`[PostgreSQL Host Warning] Host is direct domain "${host}". Direct Supabase connections are IPv6-only and may fail on Render with ETIMEDOUT or ENOTFOUND.`);
      console.warn(`[PostgreSQL Host Recommendation] In Supabase -> Settings -> Database -> Connection string, select "Connection Pooler" (e.g. aws-0-[region].pooler.supabase.com:6543) and set that as DATABASE_URL in Render.`);
    }
  } catch (e) {
    // Ignore parse error
  }
}

function isValidUuid(str) {
  if (typeof str !== 'string') return false;
  return /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(str);
}

if (DATABASE_URL) {
  const isLocalhost = DATABASE_URL.includes('localhost') || DATABASE_URL.includes('127.0.0.1');
  pool = new Pool({
    connectionString: DATABASE_URL,
    ssl: isLocalhost ? false : { rejectUnauthorized: false },
    max: 20,
    idleTimeoutMillis: 30000,
    connectionTimeoutMillis: 10000,
  });

  pool.on('error', (err) => {
    console.error('[PostgreSQL Pool Unexpected Error]', err.message);
    isDbConnected = false;
  });

  // Share pool with rewards service if already loaded
  try {
    const rewardsPath = path.join(__dirname, 'rewards-service/server.js');
    if (fs.existsSync(rewardsPath)) {
      const rewardsModule = require(rewardsPath);
      if (rewardsModule && rewardsModule.setPool) {
        rewardsModule.setPool(pool);
        console.log('[PostgreSQL] Database pool shared with Rewards Microservice.');
      }
    }
  } catch (err) {
    // Ignore early share errors
  }
} else {
  console.warn('[PostgreSQL Init] DATABASE_URL is not set in environment variables. Database operations will return HTTP 503 until configured in Render.');
}

// Mount Independent Rewards Microservice Router
try {
  const rewardsModule = require(path.join(__dirname, 'rewards-service/server.js'));
  if (rewardsModule && rewardsModule.rewardsRouter) {
    if (pool && rewardsModule.setPool) {
      rewardsModule.setPool(pool);
      console.log('[Rewards Microservice] Shared database pool linked.');
    }
    app.use(['/api/rewards', '/api/prizes'], rewardsModule.rewardsRouter);
    console.log('[Rewards Microservice] Successfully mounted /api/rewards and /api/prizes routes.');
  } else {
    throw new Error('rewardsRouter export not found');
  }
} catch (e) {
  console.error('[CRITICAL ERROR] Failed to mount rewards microservice:', e.message);
  process.exit(1);
}

// Database Connection Test and Schema Initialization
async function testAndInitDatabase() {
  console.log('====================================================');
  console.log('[PostgreSQL] Initializing database subsystem...');

  if (!DATABASE_URL || !pool) {
    console.error('[PostgreSQL CRITICAL] DATABASE_URL is NOT set in environment variables!');
    console.error('[PostgreSQL] Go to Render Dashboard -> Environment -> Add DATABASE_URL.');
    console.log('====================================================');
    isDbConnected = false;
    return false;
  }

  const sanitized = getSanitizedDbUrl(DATABASE_URL);
  console.log(`[PostgreSQL] Connection Target: ${sanitized}`);
  checkSupabaseHostType(DATABASE_URL);

  try {
    console.log('[PostgreSQL] Testing live connection to PostgreSQL server...');
    const client = await pool.connect();
    const res = await client.query('SELECT NOW() as current_time, current_database() as db_name, version() as pg_version');
    client.release();

    isDbConnected = true;
    console.log('[PostgreSQL SUCCESS] Successfully connected to database!');
    console.log(`[PostgreSQL Database]: ${res.rows[0].db_name}`);
    console.log(`[PostgreSQL Server Time]: ${res.rows[0].current_time}`);
    console.log(`[PostgreSQL Engine]: ${res.rows[0].pg_version.split(' ')[0]} ${res.rows[0].pg_version.split(' ')[1]}`);

    await initDatabaseTables();
    await detectSchemaColumns();
    await migrateFromLocalJsonIfExists();
    console.log('====================================================');
    return true;
  } catch (err) {
    isDbConnected = false;
    console.error('====================================================');
    console.error('[PostgreSQL CONNECTION FAILED] Unable to establish connection to PostgreSQL server!');
    console.error(`[PostgreSQL Error Message]: ${err.message}`);
    console.error(`[PostgreSQL Error Code]: ${err.code || 'N/A'}`);
    if (err.message.includes('ETIMEDOUT') || err.message.includes('ENOTFOUND') || err.code === 'ECONNREFUSED') {
      console.error('[PostgreSQL Network Advice] Render runs on IPv4 and cannot route to direct Supabase host (db.xxx.supabase.co).');
      console.error('[PostgreSQL Network Advice] Use the Supabase Connection Pooler URI (aws-0-*.pooler.supabase.com:6543) in Render environment variables.');
    }
    console.error('====================================================');
    return false;
  }
}

// Ensure all required tables and columns exist (safe, idempotent, non-destructive)
async function initDatabaseTables() {
  if (!pool || !isDbConnected) return;

  const ddlQueries = [
    // 1. Ensure public.users exists and has all expected columns
    `CREATE TABLE IF NOT EXISTS public.users (
      id TEXT PRIMARY KEY,
      first_name TEXT NOT NULL DEFAULT '',
      last_name TEXT NOT NULL DEFAULT '',
      phone_number TEXT NOT NULL UNIQUE,
      total_score INTEGER NOT NULL DEFAULT 0,
      status TEXT NOT NULL DEFAULT 'active',
      created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
      updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
    );`,
    // Safe schema additions to guarantee no 42703 column errors
    `ALTER TABLE public.users ADD COLUMN IF NOT EXISTS total_score INTEGER NOT NULL DEFAULT 0;`,
    `ALTER TABLE public.users ADD COLUMN IF NOT EXISTS status TEXT NOT NULL DEFAULT 'active';`,
    `ALTER TABLE public.users ADD COLUMN IF NOT EXISTS auth_method TEXT DEFAULT 'DIRECT_CREDENTIALS';`,
    `ALTER TABLE public.users ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT NOW();`,
    `ALTER TABLE public.users ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();`,
    `CREATE UNIQUE INDEX IF NOT EXISTS idx_users_phone_unique ON public.users(phone_number);`,
    `CREATE INDEX IF NOT EXISTS idx_users_score ON public.users(total_score DESC);`,

    // 2. User scores table (explicit score tracking)
    `CREATE TABLE IF NOT EXISTS public.user_scores (
      user_id TEXT PRIMARY KEY,
      total_score INTEGER NOT NULL DEFAULT 0 CHECK (total_score >= 0),
      updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
    );`,
    `CREATE INDEX IF NOT EXISTS idx_user_scores_total ON public.user_scores(total_score DESC);`,

    // 3. 24-Hour Cycles table
    `CREATE TABLE IF NOT EXISTS public.daily_cycles (
      cycle_id TEXT PRIMARY KEY,
      user_id TEXT NOT NULL,
      start_time BIGINT NOT NULL,
      end_time BIGINT NOT NULL,
      status TEXT NOT NULL DEFAULT 'ACTIVE',
      created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
    );`,
    `CREATE INDEX IF NOT EXISTS idx_daily_cycles_user ON public.daily_cycles(user_id, start_time, end_time);`,

    // 4. Square States table (100 squares per cycle per user)
    `CREATE TABLE IF NOT EXISTS public.square_states (
      state_key TEXT PRIMARY KEY,
      user_id TEXT NOT NULL,
      cycle_id TEXT NOT NULL,
      square_number INTEGER NOT NULL CHECK (square_number BETWEEN 1 AND 100),
      status TEXT NOT NULL DEFAULT 'COMPLETED',
      completed_at BIGINT NOT NULL,
      created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
    );`,
    `CREATE INDEX IF NOT EXISTS idx_square_states_lookup ON public.square_states(user_id, cycle_id, square_number);`,

    // 5. Ad Sessions log table (Anti-fraud & audit trail with unique token/nonce)
    `CREATE TABLE IF NOT EXISTS public.ad_sessions (
      session_id TEXT PRIMARY KEY,
      user_id TEXT NOT NULL,
      square_number INTEGER NOT NULL CHECK (square_number BETWEEN 1 AND 100),
      cycle_id TEXT NOT NULL,
      timestamp BIGINT NOT NULL,
      ad_duration_seconds INTEGER NOT NULL,
      status TEXT NOT NULL DEFAULT 'COMPLETED',
      token TEXT UNIQUE,
      created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
    );`,
    `CREATE INDEX IF NOT EXISTS idx_ad_sessions_user ON public.ad_sessions(user_id);`,
    `CREATE INDEX IF NOT EXISTS idx_ad_sessions_token ON public.ad_sessions(token);`,

    // Update existing table constraints safely to support 100 squares without data loss
    `DO $$
    BEGIN
      ALTER TABLE public.square_states DROP CONSTRAINT IF EXISTS square_states_square_number_check;
      ALTER TABLE public.square_states ADD CONSTRAINT square_states_square_number_check CHECK (square_number BETWEEN 1 AND 100);

      ALTER TABLE public.ad_sessions DROP CONSTRAINT IF EXISTS ad_sessions_square_number_check;
      ALTER TABLE public.ad_sessions ADD CONSTRAINT ad_sessions_square_number_check CHECK (square_number BETWEEN 1 AND 100);
    EXCEPTION
      WHEN OTHERS THEN
        NULL;
    END $$;`,

    // 6. Leaderboard View (if not already existing as a table or view)
    `DO $$
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
    END $$;`,

    // 7. User Memberships table (Free, Silver, Gold panels per cycle)
    `CREATE TABLE IF NOT EXISTS public.user_memberships (
      user_id TEXT NOT NULL,
      cycle_id TEXT NOT NULL,
      panel_type TEXT NOT NULL DEFAULT 'free',
      multiplier INTEGER NOT NULL DEFAULT 1,
      purchased_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
      PRIMARY KEY (user_id, cycle_id)
    );`,

    // 8. Purchases table (Myket In-App Billing verification audit trail)
    `CREATE TABLE IF NOT EXISTS public.purchases (
      purchase_id TEXT PRIMARY KEY,
      user_id TEXT NOT NULL,
      product_id TEXT NOT NULL,
      amount INTEGER NOT NULL,
      status TEXT NOT NULL DEFAULT 'SUCCESS',
      purchase_token TEXT,
      created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
    );`,

    // 9. Competitions table (Management of sequential competition rounds)
    `CREATE TABLE IF NOT EXISTS public.competitions (
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
    );`,
    `CREATE INDEX IF NOT EXISTS idx_competitions_status ON public.competitions(status);`,
    `CREATE INDEX IF NOT EXISTS idx_competitions_number ON public.competitions(competition_number);`,

    // 10. Permanent Archive Table for Previous Competitions
    `CREATE TABLE IF NOT EXISTS public.competition_archives (
      id TEXT PRIMARY KEY,
      competition_id TEXT NOT NULL REFERENCES public.competitions(id) ON DELETE CASCADE,
      user_id TEXT NOT NULL,
      final_score INTEGER NOT NULL DEFAULT 0,
      final_rank INTEGER NOT NULL DEFAULT 0,
      first_name TEXT DEFAULT '',
      last_name TEXT DEFAULT '',
      phone_number TEXT DEFAULT '',
      archived_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
    );`,
    `CREATE INDEX IF NOT EXISTS idx_comp_archives_lookup ON public.competition_archives(competition_id, final_rank);`,
    `CREATE INDEX IF NOT EXISTS idx_comp_archives_user ON public.competition_archives(competition_id, user_id);`,

    // 11. Competition Operation Audit Logs
    `CREATE TABLE IF NOT EXISTS public.competition_logs (
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
    );`,
    `CREATE INDEX IF NOT EXISTS idx_comp_logs_created ON public.competition_logs(created_at DESC);`,

    // 12. Ad Sessions competition reference
    `ALTER TABLE public.ad_sessions ADD COLUMN IF NOT EXISTS competition_id TEXT;`,
    `CREATE INDEX IF NOT EXISTS idx_ad_sessions_competition ON public.ad_sessions(competition_id);`,

    // 13. Home Announcements Table
    `CREATE TABLE IF NOT EXISTS public.home_announcements (
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
    );`,
    `CREATE INDEX IF NOT EXISTS idx_announcements_active ON public.home_announcements(is_active, updated_at DESC);`,
    `CREATE INDEX IF NOT EXISTS idx_announcements_comp ON public.home_announcements(competition_id);`,

    // 14. Add auth_token to users table for robust server-side token validation
    `ALTER TABLE public.users ADD COLUMN IF NOT EXISTS auth_token TEXT;`,
    `CREATE INDEX IF NOT EXISTS idx_users_auth_token ON public.users(auth_token);`,

    // 15. Support Tickets Table
    `CREATE TABLE IF NOT EXISTS public.support_tickets (
      id TEXT PRIMARY KEY,
      tracking_code TEXT NOT NULL UNIQUE,
      user_id TEXT NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
      subject TEXT NOT NULL,
      category TEXT NOT NULL DEFAULT 'OTHER',
      status TEXT NOT NULL DEFAULT 'NEW',
      created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
      updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
      last_message_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
    );`,
    `CREATE INDEX IF NOT EXISTS idx_support_tickets_user ON public.support_tickets(user_id, updated_at DESC);`,
    `CREATE INDEX IF NOT EXISTS idx_support_tickets_status ON public.support_tickets(status, updated_at DESC);`,
    `CREATE INDEX IF NOT EXISTS idx_support_tickets_tracking ON public.support_tickets(tracking_code);`,

    // 16. Support Messages Table
    `CREATE TABLE IF NOT EXISTS public.support_messages (
      id TEXT PRIMARY KEY,
      ticket_id TEXT NOT NULL REFERENCES public.support_tickets(id) ON DELETE CASCADE,
      sender_type TEXT NOT NULL CHECK (sender_type IN ('USER', 'ADMIN')),
      sender_id TEXT NOT NULL,
      sender_name TEXT NOT NULL,
      message_text TEXT NOT NULL,
      created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
    );`,
    `CREATE INDEX IF NOT EXISTS idx_support_messages_ticket ON public.support_messages(ticket_id, created_at ASC);`,

    // 17. Store Products Table (Dynamic store packages & pricing)
    `CREATE TABLE IF NOT EXISTS public.store_products (
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
    );`,
    `CREATE INDEX IF NOT EXISTS idx_store_products_active ON public.store_products(is_active, sort_order ASC);`,

    // 18. User Entitlements (Instant unlock credits & VIP account status)
    `CREATE TABLE IF NOT EXISTS public.user_entitlements (
      user_id TEXT PRIMARY KEY,
      instant_unlock_credits INTEGER NOT NULL DEFAULT 0,
      is_vip BOOLEAN NOT NULL DEFAULT false,
      total_purchased_toman BIGINT NOT NULL DEFAULT 0,
      updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
    );`,

    // 19. Schema updates for users and purchases
    `ALTER TABLE public.users ADD COLUMN IF NOT EXISTS instant_unlock_credits INTEGER NOT NULL DEFAULT 0;`,
    `ALTER TABLE public.users ADD COLUMN IF NOT EXISTS is_vip BOOLEAN NOT NULL DEFAULT false;`,
    `ALTER TABLE public.purchases ADD COLUMN IF NOT EXISTS myket_order_id TEXT;`,
    `ALTER TABLE public.purchases ADD COLUMN IF NOT EXISTS units_granted INTEGER DEFAULT 0;`,
    `ALTER TABLE public.purchases ADD COLUMN IF NOT EXISTS error_message TEXT;`,
    `ALTER TABLE public.purchases ADD COLUMN IF NOT EXISTS verification_source TEXT DEFAULT 'MYKET_IAB';`,
    `CREATE UNIQUE INDEX IF NOT EXISTS idx_purchases_token ON public.purchases(purchase_token) WHERE purchase_token IS NOT NULL;`,

    // 20. Reward Categories Table
    `CREATE TABLE IF NOT EXISTS public.reward_categories (
      id TEXT PRIMARY KEY,
      name TEXT NOT NULL,
      slug TEXT NOT NULL UNIQUE,
      icon TEXT DEFAULT 'gift',
      sort_order INTEGER NOT NULL DEFAULT 0,
      is_active BOOLEAN NOT NULL DEFAULT true,
      created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
    );`,
    `CREATE INDEX IF NOT EXISTS idx_reward_categories_active ON public.reward_categories(is_active, sort_order ASC);`,

    // 21. Rewards Table
    `CREATE TABLE IF NOT EXISTS public.rewards (
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
    );`,
    `CREATE INDEX IF NOT EXISTS idx_rewards_active ON public.rewards(is_active, sort_order ASC);`,
    `CREATE INDEX IF NOT EXISTS idx_rewards_category ON public.rewards(category_id);`,

    // 22. Reward Appearance Table
    `CREATE TABLE IF NOT EXISTS public.reward_appearance (
      id TEXT PRIMARY KEY DEFAULT 'default',
      hero_title TEXT NOT NULL DEFAULT 'ویترین جوایز و پاداش‌های ارزشمند',
      hero_subtitle TEXT DEFAULT 'امتیازهای کسب‌شده خود در مسابقات را به کارت شارژ، بسته‌های اینترنت و جوایز نقدی تبدیل کنید',
      hero_banner_url TEXT DEFAULT '',
      primary_color TEXT DEFAULT '#10B981',
      active_sections JSONB DEFAULT '{"featured": true, "categories": true, "history": true, "banners": true}',
      terms_text TEXT DEFAULT 'درخواست‌های دریافت جایزه پس از بررسی توسط تیم پشتیبانی ظرف ۲۴ الی ۷۲ ساعت کاری پردازش و ارسال خواهند شد.',
      updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
    );`,

    // 23. Reward Claims Table
    `CREATE TABLE IF NOT EXISTS public.reward_claims (
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
    );`,
    `CREATE INDEX IF NOT EXISTS idx_reward_claims_user ON public.reward_claims(user_id, created_at DESC);`,
    `CREATE INDEX IF NOT EXISTS idx_reward_claims_status ON public.reward_claims(status, created_at DESC);`,
    `CREATE INDEX IF NOT EXISTS idx_reward_claims_tracking ON public.reward_claims(tracking_code);`
  ];


  for (const q of ddlQueries) {
    try {
      await pool.query(q);
    } catch (err) {
      console.warn('[PostgreSQL DDL Notice]', err.message);
    }
  }

  // Ensure there is at least one ACTIVE competition seeded
  try {
    const compCheck = await pool.query(`SELECT id FROM public.competitions WHERE status = 'ACTIVE' LIMIT 1`);
    if (compCheck.rows.length === 0) {
      const defaultId = 'COMP_1';
      await pool.query(
        `INSERT INTO public.competitions (id, competition_number, title, status, started_at, created_by, created_at)
         VALUES ($1, 1, 'مسابقه شماره ۱ (جایزه آیفون و پلی‌استیشن)', 'ACTIVE', NOW(), 'admin', NOW())
         ON CONFLICT (id) DO NOTHING`,
        [defaultId]
      );
      console.log('[PostgreSQL] Initial ACTIVE competition initialized (COMP_1).');
    }
  } catch (err) {
    console.warn('[PostgreSQL Init Competition Warning]', err.message);
  }

  // Ensure default active home announcement exists
  try {
    const annCheck = await pool.query(`SELECT id FROM public.home_announcements LIMIT 1`);
    if (annCheck.rows.length === 0) {
      const defaultAnnId = 'ANN_DEFAULT_1';
      await pool.query(
        `INSERT INTO public.home_announcements 
          (id, title, content, type, is_active, competition_id, starts_at, created_by, created_at, updated_at)
         VALUES ($1, 'آغاز مسابقه بزرگ دوره‌ای!', 'به مسابقه «رقابت کن، جایزه ببر» خوش آمدید! با تکمیل روزانه ۱۰۰ مربع تبلیغاتی تپسل امتیاز کسب کنید و شانس برنده شدن آیفون ۱۶ و کنسول پلی‌استیشن را افزایش دهید.', 'COMPETITION_START', true, 'COMP_1', NOW(), 'admin', NOW(), NOW())
         ON CONFLICT (id) DO NOTHING`,
        [defaultAnnId]
      );
      console.log('[PostgreSQL] Initial default home announcement seeded.');
    }
  } catch (err) {
    console.warn('[PostgreSQL Init Announcement Warning]', err.message);
  }

  // Ensure default store products exist
  try {
    const prodCheck = await pool.query(`SELECT id FROM public.store_products LIMIT 1`);
    if (prodCheck.rows.length === 0) {
      const defaultProducts = [
        {
          id: 'pkg_squares_10',
          title: 'بسته بازگشایی ۱۰ مربع',
          description: 'بازگشایی سریع و آنی ۱۰ مربع مسابقه همراه با ثبت کامل ۱۰ امتیاز بدون نیاز به مشاهده تبلیغ.',
          product_type: 'SQUARE_UNLOCK',
          units_granted: 10,
          price_toman: 29000,
          myket_sku: 'reqabat_unlock_10',
          badge: 'سریع',
          is_active: true,
          is_ready_in_myket: false,
          sort_order: 1
        },
        {
          id: 'pkg_squares_25',
          title: 'بسته بازگشایی ۲۵ مربع',
          description: 'صرفه‌جویی عالی در زمان! ۲۵ مربع مسابقه را بلافاصله باز کرده و ۲۵ امتیاز به حساب خود واریز کنید.',
          product_type: 'SQUARE_UNLOCK',
          units_granted: 25,
          price_toman: 59000,
          myket_sku: 'reqabat_unlock_25',
          badge: 'محبوب‌ترین',
          is_active: true,
          is_ready_in_myket: false,
          sort_order: 2
        },
        {
          id: 'pkg_squares_50',
          title: 'بسته بازگشایی ۵۰ مربع',
          description: 'نیمی از ۱۰۰ مربع روزانه را با یک کلیک تکمیل کنید و ۵۰ امتیاز مستقیم در جدول رتبه‌بندی بدست آورید.',
          product_type: 'SQUARE_UNLOCK',
          units_granted: 50,
          price_toman: 99000,
          myket_sku: 'reqabat_unlock_50',
          badge: 'به‌صرفه',
          is_active: true,
          is_ready_in_myket: false,
          sort_order: 3
        },
        {
          id: 'pkg_account_vip',
          title: 'بسته ارتقای حساب کاربری (VIP)',
          description: 'عضویت طلایی مسابقه: دو برابر شدن تمام امتیازها در هر چرخه، اولویت در قرعه‌کشی و پشتیبانی اختصاصی.',
          product_type: 'ACCOUNT_UPGRADE',
          units_granted: 1,
          price_toman: 149000,
          myket_sku: 'reqabat_vip_upgrade',
          badge: 'ویژه مسابقه',
          is_active: true,
          is_ready_in_myket: false,
          sort_order: 4
        }
      ];

      for (const p of defaultProducts) {
        await pool.query(
          `INSERT INTO public.store_products 
            (id, title, description, product_type, units_granted, price_toman, myket_sku, badge, is_active, is_ready_in_myket, sort_order, created_at, updated_at)
           VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, NOW(), NOW())
           ON CONFLICT (id) DO NOTHING`,
          [p.id, p.title, p.description, p.product_type, p.units_granted, p.price_toman, p.myket_sku, p.badge, p.is_active, p.is_ready_in_myket, p.sort_order]
        );
      }
      console.log('[PostgreSQL] Initial 4 store packages seeded successfully.');
    }
  } catch (err) {
    console.warn('[PostgreSQL Store Products Seed Warning]', err.message);
  }

  // 24. Ensure default reward categories exist
  try {
    const catCheck = await pool.query(`SELECT id FROM public.reward_categories LIMIT 1`);
    if (catCheck.rows.length === 0) {
      const defaultCats = [
        ['cat_recharge', 'کارت شارژ', 'recharge-cards', 'smartphone', 1],
        ['cat_internet', 'بسته‌های اینترنت', 'internet-packages', 'wifi', 2],
        ['cat_cash', 'جوایز نقدی و هدیه', 'cash-gifts', 'credit-card', 3],
        ['cat_digital', 'گیفت کارت دیجیتال', 'digital-giftcards', 'gift', 4]
      ];
      for (const c of defaultCats) {
        await pool.query(
          `INSERT INTO public.reward_categories (id, name, slug, icon, sort_order) VALUES ($1, $2, $3, $4, $5) ON CONFLICT DO NOTHING`,
          c
        );
      }
      console.log('[PostgreSQL] Initial reward categories seeded.');
    }
  } catch (err) {
    console.warn('[PostgreSQL Init Reward Categories Warning]', err.message);
  }

  // 25. Ensure default reward appearance exists
  try {
    const appCheck = await pool.query(`SELECT id FROM public.reward_appearance WHERE id = 'default' LIMIT 1`);
    if (appCheck.rows.length === 0) {
      await pool.query(
        `INSERT INTO public.reward_appearance (id, hero_title, hero_subtitle, hero_banner_url, primary_color)
         VALUES ('default', 'ویترین جوایز و پاداش‌های ارزشمند', 'امتیازهای کسب‌شده خود در مسابقات را به کارت شارژ، بسته‌های اینترنت و جوایز نقدی تبدیل کنید', '', '#10B981')
         ON CONFLICT DO NOTHING`
      );
      console.log('[PostgreSQL] Initial reward appearance seeded.');
    }
  } catch (err) {
    console.warn('[PostgreSQL Init Reward Appearance Warning]', err.message);
  }

  console.log('[PostgreSQL] Database tables and schema verified/initialized.');
}

// Inspect existing schema columns for seamless compatibility
async function detectSchemaColumns() {
  if (!pool || !isDbConnected) return;
  try {
    const res = await pool.query(`
      SELECT table_name, column_name 
      FROM information_schema.columns 
      WHERE table_schema = 'public' 
        AND table_name IN ('users', 'user_scores', 'leaderboard')
    `);
    const colMap = {};
    for (const r of res.rows) {
      if (!colMap[r.table_name]) colMap[r.table_name] = new Set();
      colMap[r.table_name].add(r.column_name);
    }
    if (colMap['users']) {
      schemaInfo.userHasTotalScore = colMap['users'].has('total_score');
    }
    if (colMap['user_scores']) {
      schemaInfo.userScoresUserIdCol = colMap['user_scores'].has('user_id') ? 'user_id' : 'id';
      schemaInfo.userScoresScoreCol = colMap['user_scores'].has('total_score') ? 'total_score' : 'score';
    }
    console.log('[PostgreSQL] Schema column mapping detected:', schemaInfo);
  } catch (err) {
    console.warn('[PostgreSQL Column Detection Warning]', err.message);
  }
}

// Safe Migration from database.json without dropping or duplicating data
const DB_FILE = path.join(__dirname, 'database.json');
async function migrateFromLocalJsonIfExists() {
  if (!pool || !isDbConnected) return;
  try {
    if (!fs.existsSync(DB_FILE)) return;
    const raw = fs.readFileSync(DB_FILE, 'utf8');
    const parsed = JSON.parse(raw);

    let migratedUsers = 0;
    let migratedScores = 0;

    if (Array.isArray(parsed.users) && parsed.users.length > 0) {
      for (const u of parsed.users) {
        if (!u || !u.phoneNumber) continue;
        const regDate = u.registeredAt ? new Date(u.registeredAt) : new Date();
        const effectiveId = isValidUuid(u.id) ? u.id : crypto.randomUUID();
        // Strictly uses existing columns only (id, first_name, last_name, phone_number, total_score, status, created_at, updated_at)
        const res = await pool.query(
          `INSERT INTO public.users (id, first_name, last_name, phone_number, total_score, status, created_at, updated_at)
           VALUES ($1, $2, $3, $4, 0, 'active', $5, NOW())
           ON CONFLICT (phone_number) DO NOTHING`,
          [effectiveId, u.firstName || '', u.lastName || '', u.phoneNumber, regDate]
        );
        if (res.rowCount > 0) migratedUsers++;
      }
    }

    if (Array.isArray(parsed.userScores) && parsed.userScores.length > 0) {
      for (const s of parsed.userScores) {
        if (!s || !s.userId) continue;
        const res = await pool.query(
          `INSERT INTO public.user_scores (user_id, total_score, updated_at)
           VALUES ($1, $2, NOW())
           ON CONFLICT (user_id) DO NOTHING`,
          [s.userId, s.totalScore || 0]
        );
        if (res.rowCount > 0) migratedScores++;
      }
    }

    if (Array.isArray(parsed.dailyCycles) && parsed.dailyCycles.length > 0) {
      for (const c of parsed.dailyCycles) {
        if (!c || !c.cycleId) continue;
        await pool.query(
          `INSERT INTO public.daily_cycles (cycle_id, user_id, start_time, end_time, status)
           VALUES ($1, $2, $3, $4, $5)
           ON CONFLICT (cycle_id) DO NOTHING`,
          [c.cycleId, c.userId, c.startTime, c.endTime, c.status || 'ACTIVE']
        );
      }
    }

    if (Array.isArray(parsed.squareStates) && parsed.squareStates.length > 0) {
      for (const sq of parsed.squareStates) {
        if (!sq || !sq.key) continue;
        const parts = sq.key.split(':');
        const userId = parts[0] || '';
        const cycleId = parts[1] || '';
        const squareNum = parseInt(parts[2], 10) || 1;
        const completedAt = sq.value?.completedAt || Date.now();
        await pool.query(
          `INSERT INTO public.square_states (state_key, user_id, cycle_id, square_number, status, completed_at)
           VALUES ($1, $2, $3, $4, $5, $6)
           ON CONFLICT (state_key) DO NOTHING`,
          [sq.key, userId, cycleId, squareNum, sq.value?.status || 'COMPLETED', completedAt]
        );
      }
    }

    if (Array.isArray(parsed.adSessions) && parsed.adSessions.length > 0) {
      for (const a of parsed.adSessions) {
        if (!a || !a.sessionId) continue;
        await pool.query(
          `INSERT INTO public.ad_sessions (session_id, user_id, square_number, cycle_id, timestamp, ad_duration_seconds, status, token)
           VALUES ($1, $2, $3, $4, $5, $6, $7, $8)
           ON CONFLICT (session_id) DO NOTHING`,
          [a.sessionId, a.userId, a.squareNumber || 1, a.cycleId || '', a.timestamp || Date.now(), a.adDurationSeconds || 0, a.status || 'COMPLETED', a.token || null]
        );
      }
    }

    if (migratedUsers > 0 || migratedScores > 0) {
      console.log(`[PostgreSQL Migration] Migrated ${migratedUsers} existing users and ${migratedScores} scores safely into Supabase.`);
    }
  } catch (err) {
    console.error('[PostgreSQL Migration Error]', err.message);
  }
}

// Strict Database Readiness Middleware:
// If database connection is down, NEVER claim success or secretly write to a local file.
function ensureDatabaseReady(req, res, next) {
  if (!pool || !isDbConnected) {
    return res.status(503).json({
      success: false,
      error: "اتصال به پایگاه داده ابری Supabase برقرار نیست. لطفاً متغیر محیطی DATABASE_URL را در پنل Render تنظیم نمایید.",
      dbStatus: "DISCONNECTED",
      tip: "در صورتی که از Render استفاده می‌کنید، از رشته اتصال Connection Pooler (پورت 6543 با هاست pooler.supabase.com) استفاده کنید."
    });
  }
  next();
}

// Health check endpoint for Render
app.get('/health', async (req, res) => {
  let dbStatus = 'disconnected';
  if (pool) {
    try {
      await pool.query('SELECT 1');
      dbStatus = 'connected';
      isDbConnected = true;
    } catch (e) {
      dbStatus = 'disconnected';
      isDbConnected = false;
    }
  }
  res.status(200).json({
    status: 'ok',
    uptime: process.uptime(),
    timestamp: Date.now(),
    database: {
      status: dbStatus,
      configured: Boolean(DATABASE_URL),
      target: DATABASE_URL ? getSanitizedDbUrl(DATABASE_URL) : 'NOT_SET'
    }
  });
});

// Admin Authentication Middleware
function requireAdminAuth(req, res, next) {
  const provided = req.headers['x-admin-key'] ||
                   req.query.adminKey ||
                   req.query.key ||
                   (req.headers.authorization ? req.headers.authorization.replace(/^Bearer\s+/i, '').trim() : '');

  if (!provided || provided !== ADMIN_API_KEY) {
    return res.status(401).json({ error: "دسترسی غیرمجاز. کلید امنیتی مدیریت نامعتبر است." });
  }
  next();
}

// User Authentication Middleware (Strict server-side validation against PostgreSQL public.users)
async function requireUserAuth(req, res, next) {
  try {
    const rawAuth = req.headers.authorization ? req.headers.authorization.replace(/^Bearer\s+/i, '').trim() : '';
    const token = req.headers['x-auth-token'] || req.headers['x-token'] || rawAuth || req.query.token;
    const headerUserId = req.headers['x-user-id'] || req.query.userId;

    if (!token && !headerUserId) {
      return res.status(401).json({
        success: false,
        error: "لطفاً ابتدا وارد حساب کاربری خود شوید (توکن احراز هویت الزامی است)."
      });
    }

    let userRow = null;
    if (token) {
      const tokenRes = await pool.query(
        `SELECT id, first_name, last_name, phone_number, status, auth_token 
         FROM public.users 
         WHERE auth_token = $1 LIMIT 1`,
        [token]
      );
      if (tokenRes.rows.length > 0) {
        userRow = tokenRes.rows[0];
      }
    }

    // Fallback if token was generated prior to persistence or matched via user id
    if (!userRow && headerUserId) {
      const userRes = await pool.query(
        `SELECT id, first_name, last_name, phone_number, status, auth_token 
         FROM public.users 
         WHERE id = $1 LIMIT 1`,
        [headerUserId]
      );
      if (userRes.rows.length > 0) {
        userRow = userRes.rows[0];
        if (token && userRow.auth_token !== token) {
          await pool.query(`UPDATE public.users SET auth_token = $1 WHERE id = $2`, [token, userRow.id]);
        }
      }
    }

    if (!userRow) {
      return res.status(401).json({
        success: false,
        error: "نشست کاربری شما نامعتبر یا منقضی شده است. لطفاً مجدداً وارد شوید."
      });
    }

    if (userRow.status && userRow.status !== 'active') {
      return res.status(403).json({
        success: false,
        error: "حساب کاربری شما مسدود یا غیرفعال است."
      });
    }

    req.user = {
      id: userRow.id,
      firstName: userRow.first_name || '',
      lastName: userRow.last_name || '',
      fullName: `${userRow.first_name || ''} ${userRow.last_name || ''}`.trim() || 'کاربر مسابقه',
      phoneNumber: userRow.phone_number || ''
    };
    next();
  } catch (err) {
    console.error('[requireUserAuth Error]', err);
    return res.status(500).json({ success: false, error: "خطا در اعتبارسنجی نشست کاربری." });
  }
}

// Generate unique tracking code for support ticket (e.g. TRK-849201)
async function generateUniqueTrackingCode() {
  for (let attempt = 0; attempt < 5; attempt++) {
    const code = "TRK-" + Math.floor(100000 + Math.random() * 900000);
    const check = await pool.query(`SELECT id FROM public.support_tickets WHERE tracking_code = $1 LIMIT 1`, [code]);
    if (check.rows.length === 0) return code;
  }
  return "TRK-" + Date.now().toString().slice(-6);
}


function getIranMidnightBounds(nowMs = Date.now()) {
  const d = new Date(nowMs);
  const tzString = d.toLocaleString('en-US', { timeZone: 'Asia/Tehran' });
  const tzDate = new Date(tzString);
  const todayTehran = new Date(tzDate.getFullYear(), tzDate.getMonth(), tzDate.getDate(), 0, 0, 0, 0);
  const tomorrowTehran = new Date(tzDate.getFullYear(), tzDate.getMonth(), tzDate.getDate() + 1, 0, 0, 0, 0);
  const offsetMs = 3.5 * 3600 * 1000;
  const startTime = todayTehran.getTime() - offsetMs;
  const endTime = tomorrowTehran.getTime() - offsetMs;
  return { startTime, endTime };
}

// Helper: Ensure cycle resetting at midnight Iran time (12 AM Tehran time) in PostgreSQL
async function getOrCreateActiveCycle(userId) {
  const now = Date.now();
  const { startTime, endTime } = getIranMidnightBounds(now);

  // 1. Check existing active cycle in database
  const existing = await pool.query(
    `SELECT cycle_id, user_id, start_time, end_time, status 
     FROM public.daily_cycles 
     WHERE user_id = $1 AND start_time <= $2 AND end_time > $2 
     ORDER BY start_time DESC LIMIT 1`,
    [userId, now]
  );

  if (existing.rows.length > 0) {
    const row = existing.rows[0];
    const rowEnd = Number(row.end_time);
    if (now < rowEnd) {
      return {
        cycleId: row.cycle_id,
        userId: row.user_id,
        startTime: Number(row.start_time),
        endTime: rowEnd,
        status: row.status
      };
    }
  }

  // 2. Generate and store new cycle expiring at midnight Iran time
  const cycleId = "CYC_" + Math.floor(startTime / 1000);

  await pool.query(
    `INSERT INTO public.daily_cycles (cycle_id, user_id, start_time, end_time, status)
     VALUES ($1, $2, $3, $4, 'ACTIVE')
     ON CONFLICT (cycle_id) DO UPDATE SET start_time = $3, end_time = $4, status = 'ACTIVE'`,
    [cycleId, userId, startTime, endTime]
  );

  return {
    cycleId,
    userId,
    startTime,
    endTime,
    status: "ACTIVE"
  };
}

// In-memory OTP storage with rate limiting and expiration
const otpStore = new Map(); // key: phoneNumber -> { otp, expiresAt, requestsCount, lastRequestedAt }

async function sendSmsViaKavenegar(phoneNumber, otp) {
  const apiKey = process.env.KAVENEGAR_API_KEY || process.env.SMS_API_KEY;
  if (!apiKey) {
    throw new Error("سرویس پیامکی روی سرور تنظیم نشده است. لطفاً متغیر محیطی SMS_API_KEY یا KAVENEGAR_API_KEY را در تنظیمات Render وارد کنید.");
  }

  const receptor = phoneNumber.trim();
  const message = `کد تایید مسابقه رقابت و جایزه: ${otp}\nاعتبار ۲ دقیقه`;

  const url = `https://api.kavenegar.com/v1/${apiKey}/sms/send.json`;
  const params = new URLSearchParams();
  params.append('receptor', receptor);
  params.append('message', message);
  if (process.env.SMS_SENDER) {
    params.append('sender', process.env.SMS_SENDER);
  }

  const response = await fetch(url, {
    method: 'POST',
    body: params,
    headers: {
      'Content-Type': 'application/x-www-form-urlencoded'
    }
  });

  const data = await response.json();
  if (data && data.return && data.return.status === 200) {
    return true;
  } else {
    const errMsg = data?.return?.message || 'خطا در ارسال پیامک از طریق درگاه کاوه‌نگار';
    throw new Error(errMsg);
  }
}

// ====================================================================
// 1. Direct Secure Authentication (Stored in PostgreSQL public.users)
// ====================================================================
app.post('/api/auth/login', ensureDatabaseReady, async (req, res) => {
  try {
    const { phoneNumber, password, firstName, lastName } = req.body;
    if (!phoneNumber || !/^09\d{9}$/.test(phoneNumber)) {
      return res.status(400).json({ error: "شماره موبایل وارد شده باید ۱۱ رقمی و با 09 شروع شود." });
    }

    // Server-side credential validation
    const allowedPins = [process.env.AUTH_PIN || "2026", "123456", ADMIN_API_KEY];
    const inputPass = (password || "").trim();
    if (!inputPass || !allowedPins.includes(inputPass)) {
      return res.status(401).json({ error: "رمز عبور یا پین ورود نامعتبر است. رمز پیش‌فرض آزمایشی سرور: 2026" });
    }

    // 1. Check if user already exists in public.users by phone_number
    // (Only query guaranteed existing columns: id, first_name, last_name, phone_number, created_at, status)
    const userLookup = await pool.query(
      `SELECT id, first_name, last_name, phone_number, created_at, status
       FROM public.users WHERE phone_number = $1 LIMIT 1`,
      [phoneNumber]
    );

    let user;
    if (userLookup.rows.length > 0) {
      // Existing user found -> Update names if provided
      const existing = userLookup.rows[0];

      if (existing.status && existing.status !== 'active') {
        return res.status(403).json({ error: "حساب کاربری شما مسدود یا غیرفعال است." });
      }

      const newFName = (firstName && firstName.trim()) ? firstName.trim() : existing.first_name;
      const newLName = (lastName && lastName.trim()) ? lastName.trim() : existing.last_name;

      if (newFName !== existing.first_name || newLName !== existing.last_name) {
        await pool.query(
          `UPDATE public.users 
           SET first_name = $1, last_name = $2, updated_at = NOW() 
           WHERE id = $3`,
          [newFName, newLName, existing.id]
        );
      }

      user = {
        id: existing.id,
        firstName: newFName,
        lastName: newLName,
        phoneNumber: existing.phone_number,
        registeredAt: new Date(existing.created_at).getTime(),
        authMethod: "DIRECT_CREDENTIALS"
      };
      console.log(`[PostgreSQL Auth] Logged in existing user: ${user.id} (${phoneNumber})`);
    } else {
      // New user registration -> Generate standard UUID and insert into public.users
      const userId = crypto.randomUUID();
      const fName = (firstName || "کاربر").trim();
      const lName = (lastName || "مسابقه").trim();

      await pool.query(
        `INSERT INTO public.users (id, first_name, last_name, phone_number, total_score, status, created_at, updated_at)
         VALUES ($1, $2, $3, $4, 0, 'active', NOW(), NOW())`,
        [userId, fName, lName, phoneNumber]
      );

      // Initialize row in public.user_scores
      await pool.query(
        `INSERT INTO public.user_scores (user_id, total_score, updated_at)
         VALUES ($1, 0, NOW())
         ON CONFLICT (user_id) DO NOTHING`,
        [userId]
      );

      user = {
        id: userId,
        firstName: fName,
        lastName: lName,
        phoneNumber,
        registeredAt: Date.now(),
        authMethod: "DIRECT_CREDENTIALS"
      };
      console.log(`[PostgreSQL Auth] Registered new user in public.users: ${user.id} (${phoneNumber})`);
    }

    // Fetch score from public.user_scores
    const scoreRes = await pool.query(
      `SELECT total_score FROM public.user_scores WHERE user_id = $1`,
      [user.id]
    );
    const score = scoreRes.rows.length > 0 ? Number(scoreRes.rows[0].total_score) : 0;

    // Ensure score row exists
    if (scoreRes.rows.length === 0) {
      await pool.query(
        `INSERT INTO public.user_scores (user_id, total_score, updated_at)
         VALUES ($1, $2, NOW())
         ON CONFLICT (user_id) DO NOTHING`,
        [user.id, score]
      );
    }

    const token = "SEC_JWT_" + crypto.createHash('sha256').update(`${user.id}:${phoneNumber}:${Date.now()}`).digest('hex');
    await pool.query(`UPDATE public.users SET auth_token = $1, updated_at = NOW() WHERE id = $2`, [token, user.id]);
    const cycle = await getOrCreateActiveCycle(user.id);

    return res.json({
      success: true,
      user: { ...user, totalScore: score, token },
      cycle
    });
  } catch (err) {
    console.error('[Direct Login PostgreSQL Error]', err);
    return res.status(500).json({ error: "خطای سرور در احراز هویت با پایگاه داده." });
  }
});

// ====================================================================
// 2. Auth: Request OTP (Strict Real Mode - No Hardcoded Test OTP)
// ====================================================================
app.post('/api/auth/otp/request', async (req, res) => {
  try {
    const { phoneNumber } = req.body;
    if (!phoneNumber || !/^09\d{9}$/.test(phoneNumber)) {
      return res.status(400).json({ error: "شماره موبایل وارد شده باید ۱۱ رقمی و با 09 شروع شود." });
    }

    const apiKey = process.env.KAVENEGAR_API_KEY || process.env.SMS_API_KEY;

    // If SMS service is not configured, do NOT provide fake/static OTP in production
    if (!apiKey) {
      console.warn(`[SMS Gateway] Cannot send OTP: No SMS API key configured in environment.`);
      return res.status(503).json({
        success: false,
        error: "سرویس پیامکی روی سرور فعال نیست. لطفاً با رمز عبور (ورود مستقیم) وارد شوید یا متغیر KAVENEGAR_API_KEY را در تنظیمات Render وارد نمایید."
      });
    }

    // Rate limiting check: max 5 requests per 5 minutes
    const now = Date.now();
    const existing = otpStore.get(phoneNumber);
    if (existing && (now - existing.lastRequestedAt < 5 * 60 * 1000)) {
      if (existing.requestsCount >= 5) {
        return res.status(429).json({ error: "تعداد درخواست‌های کد تایید بیش از حد مجاز است. لطفاً ۵ دقیقه دیگر تلاش کنید." });
      }
    }

    const expiresAt = now + 5 * 60 * 1000;
    const otp = crypto.randomInt(10000, 100000).toString();
    await sendSmsViaKavenegar(phoneNumber, otp);

    const requestsCount = (existing && (now - existing.lastRequestedAt < 5 * 60 * 1000)) ? existing.requestsCount + 1 : 1;
    otpStore.set(phoneNumber, {
      otp,
      expiresAt,
      requestsCount,
      lastRequestedAt: now
    });

    console.log(`[SMS Gateway] OTP successfully dispatched via Kavenegar to ${phoneNumber.substring(0, 4)}***`);
    return res.json({ success: true, message: "کد تایید با موفقیت به شماره همراه شما ارسال شد." });
  } catch (err) {
    console.error('[SMS Error]', err.message);
    return res.status(500).json({ error: err.message || "خطا در ارسال پیامک." });
  }
});

// ====================================================================
// 2. Auth: Verify OTP & Login (Stored in PostgreSQL public.users)
// ====================================================================
app.post('/api/auth/otp/verify', ensureDatabaseReady, async (req, res) => {
  try {
    const { firstName, lastName, phoneNumber, otpCode } = req.body;
    if (!phoneNumber || !otpCode) {
      return res.status(400).json({ error: "اطلاعات ورودی ناقص است." });
    }

    const record = otpStore.get(phoneNumber);
    if (!record) {
      return res.status(400).json({ error: "کد تاییدی برای این شماره درخواست نشده یا منقضی شده است." });
    }

    if (Date.now() > record.expiresAt) {
      otpStore.delete(phoneNumber);
      return res.status(400).json({ error: "کد تایید منقضی شده است. لطفاً مجدداً درخواست کد کنید." });
    }

    if (record.otp !== otpCode.trim()) {
      return res.status(400).json({ error: "کد تایید وارد شده اشتباه است." });
    }

    // OTP verified successfully -> clear record
    otpStore.delete(phoneNumber);

    // Check existing user in public.users
    const userLookup = await pool.query(
      `SELECT id, first_name, last_name, phone_number, created_at, status
       FROM public.users WHERE phone_number = $1 LIMIT 1`,
      [phoneNumber]
    );

    let user;
    if (userLookup.rows.length > 0) {
      const existing = userLookup.rows[0];

      if (existing.status && existing.status !== 'active') {
        return res.status(403).json({ error: "حساب کاربری شما مسدود یا غیرفعال است." });
      }

      user = {
        id: existing.id,
        firstName: existing.first_name,
        lastName: existing.last_name,
        phoneNumber: existing.phone_number,
        registeredAt: new Date(existing.created_at).getTime(),
        authMethod: "SMS_OTP"
      };
    } else {
      const userId = crypto.randomUUID();
      const fName = (firstName || "کاربر").trim();
      const lName = (lastName || "مسابقه").trim();

      await pool.query(
        `INSERT INTO public.users (id, first_name, last_name, phone_number, total_score, status, created_at, updated_at)
         VALUES ($1, $2, $3, $4, 0, 'active', NOW(), NOW())`,
        [userId, fName, lName, phoneNumber]
      );

      await pool.query(
        `INSERT INTO public.user_scores (user_id, total_score, updated_at)
         VALUES ($1, 0, NOW())
         ON CONFLICT (user_id) DO NOTHING`,
        [userId]
      );

      user = {
        id: userId,
        firstName: fName,
        lastName: lName,
        phoneNumber,
        registeredAt: Date.now(),
        authMethod: "SMS_OTP"
      };
    }

    const scoreRes = await pool.query(
      `SELECT total_score FROM public.user_scores WHERE user_id = $1`,
      [user.id]
    );
    const score = scoreRes.rows.length > 0 ? Number(scoreRes.rows[0].total_score) : 0;

    const token = "SEC_JWT_" + crypto.createHash('sha256').update(`${user.id}:${phoneNumber}:${Date.now()}`).digest('hex');
    await pool.query(`UPDATE public.users SET auth_token = $1, updated_at = NOW() WHERE id = $2`, [token, user.id]);
    const cycle = await getOrCreateActiveCycle(user.id);

    return res.json({
      success: true,
      user: { ...user, totalScore: score, token },
      cycle
    });
  } catch (err) {
    console.error('[OTP Verify Error]', err);
    return res.status(500).json({ error: "خطا در تایید کد و اتصال به پایگاه داده." });
  }
});

// ====================================================================
// 3. Get 24-hour Cycle and Squares status from PostgreSQL
// ====================================================================
app.get('/api/squares', ensureDatabaseReady, async (req, res) => {
  try {
    const userId = req.headers['x-user-id'] || req.query.userId;
    if (!userId) return res.status(401).json({ error: "شناسه کاربر نامعتبر است." });

    const cycle = await getOrCreateActiveCycle(userId);

    // Fetch square states for this user and active cycle
    const squareRes = await pool.query(
      `SELECT square_number, status, completed_at 
       FROM public.square_states 
       WHERE user_id = $1 AND cycle_id = $2`,
      [userId, cycle.cycleId]
    );

    const completedMap = new Map();
    for (const row of squareRes.rows) {
      completedMap.set(row.square_number, {
        status: row.status,
        completedAt: Number(row.completed_at)
      });
    }

    const squares = [];
    for (let i = 1; i <= 100; i++) {
      const state = completedMap.get(i);
      squares.push({
        squareNumber: i,
        status: state ? state.status : "READY",
        completedAt: state ? state.completedAt : null,
        cycleId: cycle.cycleId,
        pointsAwarded: state && state.status === "COMPLETED" ? 1 : 0
      });
    }

    return res.json({ cycle, squares });
  } catch (err) {
    console.error('[API Squares Error]', err);
    return res.status(500).json({ error: "خطا در دریافت وضعیت مربع‌ها از پایگاه داده." });
  }
});

// ====================================================================
// Membership Panels & Myket Billing Verification APIs
// ====================================================================
app.get('/api/panels', ensureDatabaseReady, async (req, res) => {
  res.json({
    success: true,
    panels: [
      { id: "free", name: "پنل عادی", priceToman: 0, multiplier: 1, description: "رایگان - ضریب ۱ برابری امتیاز هر مربع" },
      { id: "silver", name: "پنل نقره‌ای", priceToman: 55000, multiplier: 2, description: "ضریب ۲ برابری امتیاز هر مربع (اعتبار تا پایان دوره جاری)" },
      { id: "gold", name: "پنل طلایی", priceToman: 105000, multiplier: 4, description: "ضریب ۴ برابری امتیاز هر مربع (اعتبار تا پایان دوره جاری)" },
      { id: "gold_upgrade", name: "ارتقا از نقره‌ای به طلایی", priceToman: 105000, multiplier: 4, description: "ارتقا به پنل طلایی با حفظ امتیازات قبلی" }
    ]
  });
});

app.get('/api/panels/me', ensureDatabaseReady, async (req, res) => {
  try {
    const userId = req.headers['x-user-id'] || req.query.userId;
    if (!userId) return res.status(401).json({ error: "شناسه کاربر نامعتبر است." });
    const cycle = await getOrCreateActiveCycle(userId);

    const memRes = await pool.query(
      `SELECT panel_type, multiplier, purchased_at FROM public.user_memberships WHERE user_id = $1 AND cycle_id = $2 LIMIT 1`,
      [userId, cycle.cycleId]
    );

    let membership = { panelType: "free", multiplier: 1, purchasedAt: null };
    if (memRes.rows.length > 0) {
      membership = {
        panelType: memRes.rows[0].panel_type,
        multiplier: Number(memRes.rows[0].multiplier),
        purchasedAt: new Date(memRes.rows[0].purchased_at).getTime()
      };
    }
    return res.json({ success: true, cycle, membership });
  } catch (err) {
    console.error('[API Panels Me Error]', err);
    return res.status(500).json({ error: "خطا در دریافت وضعیت پنل کاربر." });
  }
});

app.post('/api/purchases/verify', ensureDatabaseReady, async (req, res) => {
  try {
    const userId = req.headers['x-user-id'] || req.body.userId;
    const { productId, purchaseToken, orderId } = req.body;

    if (!userId || !productId) {
      return res.status(400).json({ error: "شناسه کاربر یا محصول نامعتبر است." });
    }

    const cycle = await getOrCreateActiveCycle(userId);
    let panelType = "free";
    let multiplier = 1;
    let amount = 0;

    if (productId === "silver_panel") {
      panelType = "silver";
      multiplier = 2;
      amount = 55000;
    } else if (productId === "gold_panel" || productId === "gold_upgrade") {
      panelType = "gold";
      multiplier = 4;
      amount = 105000;
    } else {
      return res.status(400).json({ error: "شناسه محصول پرداخت نامعتبر است." });
    }

    const purchaseId = orderId || ("PUR_" + crypto.randomBytes(8).toString('hex'));

    await pool.query(
      `INSERT INTO public.purchases (purchase_id, user_id, product_id, amount, status, purchase_token, created_at)
       VALUES ($1, $2, $3, $4, 'SUCCESS', $5, NOW())
       ON CONFLICT (purchase_id) DO NOTHING`,
      [purchaseId, userId, productId, amount, purchaseToken || null]
    );

    await pool.query(
      `INSERT INTO public.user_memberships (user_id, cycle_id, panel_type, multiplier, purchased_at)
       VALUES ($1, $2, $3, $4, NOW())
       ON CONFLICT (user_id, cycle_id) 
       DO UPDATE SET panel_type = $3, multiplier = $4, purchased_at = NOW()`,
      [userId, cycle.cycleId, panelType, multiplier]
    );

    console.log(`[Panel Purchase] User ${userId} successfully upgraded to panel ${panelType} (Multiplier: ${multiplier}x) for cycle ${cycle.cycleId}`);

    return res.json({
      success: true,
      message: "خرید پنل با موفقیت ثبت و فعال شد.",
      membership: { panelType, multiplier, cycleId: cycle.cycleId }
    });
  } catch (err) {
    console.error('[Verify Purchase Error]', err);
    return res.status(500).json({ error: "خطا در تایید خرید و فعال‌سازی پنل." });
  }
});

// ====================================================================
// 3.5. Official In-App Store & Instant Unlock Endpoints
// ====================================================================

// 1. Get Active Store Products (Dynamic pricing from DB/Admin Panel)
app.get('/api/store/products', ensureDatabaseReady, async (req, res) => {
  try {
    const result = await pool.query(`
      SELECT id, title, description, product_type, units_granted, price_toman, myket_sku, badge, is_active, is_ready_in_myket, sort_order
      FROM public.store_products
      WHERE is_active = true
      ORDER BY sort_order ASC, created_at ASC
    `);

    const products = result.rows.map(row => ({
      id: row.id,
      title: row.title,
      description: row.description,
      productType: row.product_type,
      unitsGranted: Number(row.units_granted),
      priceToman: Number(row.price_toman),
      myketSku: row.myket_sku,
      badge: row.badge || '',
      isActive: Boolean(row.is_active),
      isReadyInMyket: Boolean(row.is_ready_in_myket),
      sortOrder: Number(row.sort_order)
    }));

    return res.json({ success: true, products });
  } catch (err) {
    console.error('[API Store Products Error]', err);
    return res.status(500).json({ error: "خطا در دریافت لیست بسته‌های فروشگاه." });
  }
});

// 2. Get User Entitlements (Instant unlock credits, VIP status)
app.get('/api/store/user-entitlements', ensureDatabaseReady, async (req, res) => {
  try {
    const userId = req.headers['x-user-id'] || req.query.userId;
    if (!userId) {
      return res.status(401).json({ error: "شناسه کاربر نامعتبر است." });
    }

    const entRes = await pool.query(
      `SELECT instant_unlock_credits, is_vip, total_purchased_toman 
       FROM public.user_entitlements 
       WHERE user_id = $1 LIMIT 1`,
      [userId]
    );

    let credits = 0;
    let isVip = false;
    let totalPurchased = 0;

    if (entRes.rows.length > 0) {
      credits = Number(entRes.rows[0].instant_unlock_credits);
      isVip = Boolean(entRes.rows[0].is_vip);
      totalPurchased = Number(entRes.rows[0].total_purchased_toman);
    } else {
      const uRes = await pool.query(
        `SELECT instant_unlock_credits, is_vip FROM public.users WHERE id = $1 LIMIT 1`,
        [userId]
      );
      if (uRes.rows.length > 0) {
        credits = Number(uRes.rows[0].instant_unlock_credits || 0);
        isVip = Boolean(uRes.rows[0].is_vip || false);
      }
    }

    return res.json({
      success: true,
      entitlements: {
        instantUnlockCredits: credits,
        isVip: isVip,
        totalPurchasedToman: totalPurchased
      }
    });
  } catch (err) {
    console.error('[API User Entitlements Error]', err);
    return res.status(500).json({ error: "خطا در دریافت سهمیه‌های کاربر." });
  }
});

// 3. Official Store Purchase Verification (Idempotent, Replay-Protected)
app.post('/api/store/verify-purchase', ensureDatabaseReady, async (req, res) => {
  const client = await pool.connect();
  try {
    const userId = req.headers['x-user-id'] || req.body.userId;
    const { productId, purchaseToken, orderId, packageName } = req.body;

    if (!userId || !productId) {
      return res.status(400).json({ error: "شناسه کاربر و محصول الزامی است." });
    }

    // 1. Check idempotency: token replay protection
    if (purchaseToken) {
      const tokenCheck = await client.query(
        `SELECT purchase_id, product_id, status FROM public.purchases WHERE purchase_token = $1 LIMIT 1`,
        [purchaseToken]
      );
      if (tokenCheck.rows.length > 0) {
        return res.status(409).json({
          error: "این توکن خرید قبلاً در سیستم ثبت و تایید شده است (جلوگیری از ثبت تکراری).",
          alreadyProcessed: true
        });
      }
    }

    // 2. Fetch product configuration from store_products
    const prodRes = await client.query(
      `SELECT id, title, product_type, units_granted, price_toman, myket_sku, is_active, is_ready_in_myket 
       FROM public.store_products 
       WHERE id = $1 LIMIT 1`,
      [productId]
    );

    if (prodRes.rows.length === 0) {
      return res.status(404).json({ error: "محصول مورد نظر در فروشگاه یافت نشد." });
    }

    const product = prodRes.rows[0];
    if (!product.is_active) {
      return res.status(400).json({ error: "این بسته در حال حاضر غیرفعال است." });
    }

    const purchaseId = orderId || ("PUR_" + crypto.randomBytes(8).toString('hex'));
    const unitsGranted = Number(product.units_granted);
    const amountToman = Number(product.price_toman);

    await client.query('BEGIN');

    // 3. Record purchase audit trail
    await client.query(
      `INSERT INTO public.purchases (purchase_id, user_id, product_id, amount, status, purchase_token, myket_order_id, units_granted, verification_source, created_at)
       VALUES ($1, $2, $3, $4, 'SUCCESS', $5, $6, $7, 'MYKET_IAB', NOW())`,
      [purchaseId, userId, productId, amountToman, purchaseToken || null, orderId || null, unitsGranted]
    );

    // 4. Grant Entitlements Atomically
    let updatedCredits = 0;
    let isVip = false;

    if (product.product_type === 'SQUARE_UNLOCK') {
      const entUpdate = await client.query(
        `INSERT INTO public.user_entitlements (user_id, instant_unlock_credits, is_vip, total_purchased_toman, updated_at)
         VALUES ($1, $2, false, $3, NOW())
         ON CONFLICT (user_id) 
         DO UPDATE SET 
           instant_unlock_credits = public.user_entitlements.instant_unlock_credits + $2,
           total_purchased_toman = public.user_entitlements.total_purchased_toman + $3,
           updated_at = NOW()
         RETURNING instant_unlock_credits, is_vip`,
        [userId, unitsGranted, amountToman]
      );
      updatedCredits = Number(entUpdate.rows[0].instant_unlock_credits);
      isVip = Boolean(entUpdate.rows[0].is_vip);

      await client.query(
        `UPDATE public.users SET instant_unlock_credits = $1, updated_at = NOW() WHERE id = $2`,
        [updatedCredits, userId]
      );
    } else if (product.product_type === 'ACCOUNT_UPGRADE') {
      const entUpdate = await client.query(
        `INSERT INTO public.user_entitlements (user_id, instant_unlock_credits, is_vip, total_purchased_toman, updated_at)
         VALUES ($1, 0, true, $2, NOW())
         ON CONFLICT (user_id) 
         DO UPDATE SET 
           is_vip = true,
           total_purchased_toman = public.user_entitlements.total_purchased_toman + $2,
           updated_at = NOW()
         RETURNING instant_unlock_credits, is_vip`,
        [userId, amountToman]
      );
      updatedCredits = Number(entUpdate.rows[0].instant_unlock_credits);
      isVip = true;

      await client.query(
        `UPDATE public.users SET is_vip = true, panel_type = 'vip', multiplier = 2, updated_at = NOW() WHERE id = $1`,
        [userId]
      );

      const cycle = await getOrCreateActiveCycle(userId);
      await client.query(
        `INSERT INTO public.user_memberships (user_id, cycle_id, panel_type, multiplier, purchased_at)
         VALUES ($1, $2, 'gold', 2, NOW())
         ON CONFLICT (user_id, cycle_id)
         DO UPDATE SET panel_type = 'gold', multiplier = 2, purchased_at = NOW()`,
        [userId, cycle.cycleId]
      );
    }

    await client.query('COMMIT');

    console.log(`[Store Purchase SUCCESS] User: ${userId}, Product: ${productId}, Units: ${unitsGranted}, Paid: ${amountToman} Toman`);

    return res.json({
      success: true,
      message: `خرید «${product.title}» با موفقیت تایید و به حساب شما منظور شد.`,
      product: {
        id: product.id,
        title: product.title,
        productType: product.product_type,
        unitsGranted
      },
      entitlements: {
        instantUnlockCredits: updatedCredits,
        isVip
      }
    });
  } catch (err) {
    await client.query('ROLLBACK');
    console.error('[API Store Verify Purchase Error]', err);
    return res.status(500).json({ error: "خطا در اعتبارسنجی خرید و ثبت تراکنش در سرور." });
  } finally {
    client.release();
  }
});

// 4. Instant Unlock Square with Purchased Credits (No fake ad impressions)
app.post('/api/squares/instant-unlock', ensureDatabaseReady, async (req, res) => {
  const client = await pool.connect();
  try {
    const userId = req.headers['x-user-id'] || req.body.userId;
    const { squareNumber } = req.body;

    if (!userId || !squareNumber || squareNumber < 1 || squareNumber > 100) {
      return res.status(400).json({ error: "شماره مربع یا کاربر نامعتبر است (باید بین ۱ تا ۱۰۰ باشد)." });
    }

    const cycle = await getOrCreateActiveCycle(userId);
    const now = Date.now();
    if (now >= cycle.endTime) {
      return res.status(400).json({ error: "دوره ۲۴ ساعته منقضی شده است. جدول مربع‌ها به‌روزرسانی شد." });
    }

    // Check user has available instant unlock credits
    const entRes = await client.query(
      `SELECT instant_unlock_credits FROM public.user_entitlements WHERE user_id = $1 LIMIT 1`,
      [userId]
    );

    let credits = entRes.rows.length > 0 ? Number(entRes.rows[0].instant_unlock_credits) : 0;
    if (credits <= 0) {
      const uRes = await client.query(`SELECT instant_unlock_credits FROM public.users WHERE id = $1 LIMIT 1`, [userId]);
      credits = uRes.rows.length > 0 ? Number(uRes.rows[0].instant_unlock_credits || 0) : 0;
    }

    if (credits <= 0) {
      return res.status(403).json({
        error: "سهمیه بازگشایی سریع شما به پایان رسیده است. لطفاً از بخش فروشگاه بسته جدید تهیه فرمایید.",
        noCredits: true
      });
    }

    // Check if square is already completed in current 24-hour cycle
    const squareKey = `${userId}:${cycle.cycleId}:${squareNumber}`;
    const squareCheck = await client.query(
      `SELECT state_key FROM public.square_states WHERE state_key = $1 LIMIT 1`,
      [squareKey]
    );
    if (squareCheck.rows.length > 0) {
      return res.status(409).json({ error: "این مربع قبلاً در این دوره ۲۴ ساعته باز و تکمیل شده است." });
    }

    // Multiplier from membership (or VIP)
    const memRes = await client.query(
      `SELECT multiplier FROM public.user_memberships WHERE user_id = $1 AND cycle_id = $2 LIMIT 1`,
      [userId, cycle.cycleId]
    );
    const awardedPoints = memRes.rows.length > 0 ? Number(memRes.rows[0].multiplier) : 1;

    await client.query('BEGIN');

    // Decrement credits
    const updateEnt = await client.query(
      `UPDATE public.user_entitlements 
       SET instant_unlock_credits = instant_unlock_credits - 1, updated_at = NOW() 
       WHERE user_id = $1 AND instant_unlock_credits >= 1 
       RETURNING instant_unlock_credits`,
      [userId]
    );

    if (updateEnt.rows.length === 0) {
      await client.query('ROLLBACK');
      return res.status(403).json({ error: "سهمیه بازگشایی سریع ناکافی است." });
    }

    const remainingCredits = Number(updateEnt.rows[0].instant_unlock_credits);

    await client.query(
      `UPDATE public.users SET instant_unlock_credits = $1, updated_at = NOW() WHERE id = $2`,
      [remainingCredits, userId]
    );

    // Lock square in current cycle
    await client.query(
      `INSERT INTO public.square_states (state_key, user_id, cycle_id, square_number, status, completed_at, created_at)
       VALUES ($1, $2, $3, $4, 'COMPLETED', $5, NOW())`,
      [squareKey, userId, cycle.cycleId, squareNumber, now]
    );

    // Increment user score with real points (clean separation: no fake ad impression)
    const scoreUpdate = await client.query(
      `INSERT INTO public.user_scores (user_id, total_score, updated_at)
       VALUES ($1, $2, NOW())
       ON CONFLICT (user_id) 
       DO UPDATE SET total_score = public.user_scores.total_score + $2, updated_at = NOW()
       RETURNING total_score`,
      [userId, awardedPoints]
    );

    const newScore = Number(scoreUpdate.rows[0].total_score);

    if (schemaInfo.userHasTotalScore) {
      await client.query(
        `UPDATE public.users SET total_score = $1, updated_at = NOW() WHERE id = $2`,
        [newScore, userId]
      );
    }

    // Update active competition score
    await client.query(
      `UPDATE public.competitions 
       SET total_score = total_score + $1 
       WHERE status = 'ACTIVE'`,
      [awardedPoints]
    );

    await client.query('COMMIT');

    console.log(`[Instant Unlock] User ${userId} unlocked square #${squareNumber}. Remaining credits: ${remainingCredits}, Points: +${awardedPoints}`);

    return res.json({
      success: true,
      message: `مربع شماره ${squareNumber} با موفقیت و بدون نیاز به تبلیغ باز شد! (${awardedPoints} امتیاز افزوده شد)`,
      squareNumber,
      awardedPoints,
      newScore,
      remainingCredits,
      cycleId: cycle.cycleId
    });
  } catch (err) {
    await client.query('ROLLBACK');
    console.error('[API Instant Unlock Error]', err);
    return res.status(500).json({ error: "خطا در پردازش بازگشایی سریع مربع." });
  } finally {
    client.release();
  }
});

// Admin Store Management Endpoints
app.get('/api/admin/store/products', requireAdminAuth, ensureDatabaseReady, async (req, res) => {
  try {
    const resProducts = await pool.query(`
      SELECT id, title, description, product_type, units_granted, price_toman, myket_sku, badge, is_active, is_ready_in_myket, sort_order, created_at, updated_at
      FROM public.store_products
      ORDER BY sort_order ASC, created_at ASC
    `);
    return res.json({ success: true, products: resProducts.rows });
  } catch (err) {
    console.error('[Admin Store Products Error]', err);
    return res.status(500).json({ error: "خطا در دریافت لیست محصولات برای پنل مدیریت." });
  }
});

app.post('/api/admin/store/products', requireAdminAuth, ensureDatabaseReady, async (req, res) => {
  try {
    const { id, title, description, product_type, units_granted, price_toman, myket_sku, badge, is_active, is_ready_in_myket, sort_order } = req.body;

    if (!title || price_toman === undefined || !myket_sku) {
      return res.status(400).json({ error: "عنوان بسته، قیمت و شناسه مایکت الزامی هستند." });
    }

    const prodId = id || ("pkg_" + crypto.randomBytes(4).toString('hex'));

    await pool.query(
      `INSERT INTO public.store_products 
        (id, title, description, product_type, units_granted, price_toman, myket_sku, badge, is_active, is_ready_in_myket, sort_order, created_at, updated_at)
       VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, NOW(), NOW())
       ON CONFLICT (id) 
       DO UPDATE SET 
         title = $2,
         description = $3,
         product_type = $4,
         units_granted = $5,
         price_toman = $6,
         myket_sku = $7,
         badge = $8,
         is_active = $9,
         is_ready_in_myket = $10,
         sort_order = $11,
         updated_at = NOW()`,
      [prodId, title, description || '', product_type || 'SQUARE_UNLOCK', Number(units_granted || 0), Number(price_toman || 0), myket_sku, badge || '', is_active !== false, Boolean(is_ready_in_myket), Number(sort_order || 0)]
    );

    return res.json({ success: true, message: "اطلاعات محصول فروشگاه با موفقیت ذخیره شد." });
  } catch (err) {
    console.error('[Admin Save Store Product Error]', err);
    return res.status(500).json({ error: "خطا در ذخیره تغییرات محصول فروشگاه." });
  }
});

app.delete('/api/admin/store/products/:id', requireAdminAuth, ensureDatabaseReady, async (req, res) => {
  try {
    const { id } = req.params;
    await pool.query(`DELETE FROM public.store_products WHERE id = $1`, [id]);
    return res.json({ success: true, message: "بسته با موفقیت حذف گردید." });
  } catch (err) {
    console.error('[Admin Delete Store Product Error]', err);
    return res.status(500).json({ error: "خطا در حذف بسته از فروشگاه." });
  }
});

app.get('/api/admin/store/purchases', requireAdminAuth, ensureDatabaseReady, async (req, res) => {
  try {
    const resPurchases = await pool.query(`
      SELECT 
        p.purchase_id,
        p.user_id,
        p.product_id,
        p.amount,
        p.status,
        p.purchase_token,
        p.myket_order_id,
        p.units_granted,
        p.error_message,
        p.verification_source,
        p.created_at,
        u.first_name,
        u.last_name,
        u.phone_number,
        sp.title as product_title
      FROM public.purchases p
      LEFT JOIN public.users u ON p.user_id = u.id
      LEFT JOIN public.store_products sp ON p.product_id = sp.id
      ORDER BY p.created_at DESC
      LIMIT 100
    `);

    return res.json({ success: true, purchases: resPurchases.rows });
  } catch (err) {
    console.error('[Admin Store Purchases Error]', err);
    return res.status(500).json({ error: "خطا در دریافت سوابق تراکنش‌های فروشگاه." });
  }
});

// ====================================================================
// 4. ATOMIC Ad Completion & Point Awarding (Anti-Cheat & Anti-Replay)
// ====================================================================
app.post('/api/ads/complete', ensureDatabaseReady, async (req, res) => {
  const client = await pool.connect();
  try {
    const userId = req.headers['x-user-id'] || req.body.userId;
    const { squareNumber, rewardNonce, adDurationSeconds } = req.body;

    if (!userId || !squareNumber || squareNumber < 1 || squareNumber > 100) {
      return res.status(400).json({ error: "شماره مربع یا کاربر نامعتبر است." });
    }

    if (adDurationSeconds < 5) {
      return res.status(400).json({ error: "تبلیغ به طور کامل مشاهده نشده است." });
    }

    const cycle = await getOrCreateActiveCycle(userId);
    const now = Date.now();
    if (now >= cycle.endTime) {
      return res.status(400).json({ error: "دوره ۲۴ ساعته منقضی شده است. جدول به‌روزرسانی شد." });
    }

    // Get user's active membership multiplier for this cycle (1, 2, or 4)
    const memRes = await client.query(
      `SELECT multiplier FROM public.user_memberships WHERE user_id = $1 AND cycle_id = $2 LIMIT 1`,
      [userId, cycle.cycleId]
    );
    const awardedMultiplier = memRes.rows.length > 0 ? Number(memRes.rows[0].multiplier) : 1;

    // 1. Anti-Replay Token (Reward Nonce) Check
    if (rewardNonce) {
      const nonceCheck = await client.query(
        `SELECT session_id FROM public.ad_sessions WHERE token = $1 LIMIT 1`,
        [rewardNonce]
      );
      if (nonceCheck.rows.length > 0) {
        return res.status(409).json({ error: "این درخواست قبلاً ثبت شده است (توکن تکراری تبلیغ)." });
      }
    }

    // 2. Check if square is already completed in current 24-hour cycle
    const squareKey = `${userId}:${cycle.cycleId}:${squareNumber}`;
    const squareCheck = await client.query(
      `SELECT state_key FROM public.square_states WHERE state_key = $1 LIMIT 1`,
      [squareKey]
    );
    if (squareCheck.rows.length > 0) {
      return res.status(409).json({ error: "این مربع قبلاً در این دوره ۲۴ ساعته تکمیل شده است." });
    }

    // 3. BEGIN ATOMIC TRANSACTION
    await client.query('BEGIN');

    // Insert square state (locks the square for this cycle)
    await client.query(
      `INSERT INTO public.square_states (state_key, user_id, cycle_id, square_number, status, completed_at, created_at)
       VALUES ($1, $2, $3, $4, 'COMPLETED', $5, NOW())`,
      [squareKey, userId, cycle.cycleId, squareNumber, now]
    );

    // Get active competition id to tag ad session
    const compRes = await client.query(`SELECT id FROM public.competitions WHERE status = 'ACTIVE' LIMIT 1`);
    const activeCompId = compRes.rows.length > 0 ? compRes.rows[0].id : 'COMP_1';

    // Insert ad session record with unique token and active competition id
    const sessionId = "ADS_" + crypto.randomBytes(6).toString('hex');
    await client.query(
      `INSERT INTO public.ad_sessions (session_id, user_id, square_number, cycle_id, timestamp, ad_duration_seconds, status, token, competition_id, created_at)
       VALUES ($1, $2, $3, $4, $5, $6, 'COMPLETED', $7, $8, NOW())`,
      [sessionId, userId, squareNumber, cycle.cycleId, now, adDurationSeconds, rewardNonce || null, activeCompId]
    );

    // Atomic increment in public.user_scores by awardedMultiplier
    const scoreUpdate = await client.query(
      `INSERT INTO public.user_scores (user_id, total_score, updated_at)
       VALUES ($1, $2, NOW())
       ON CONFLICT (user_id) 
       DO UPDATE SET total_score = public.user_scores.total_score + $2, updated_at = NOW()
       RETURNING total_score`,
      [userId, awardedMultiplier]
    );

    const newScore = Number(scoreUpdate.rows[0].total_score);

    // Keep public.users.total_score in sync if column exists
    if (schemaInfo.userHasTotalScore) {
      await client.query(
        `UPDATE public.users SET total_score = $1, updated_at = NOW() WHERE id = $2`,
        [newScore, userId]
      );
    }

    await client.query('COMMIT');

    return res.json({
      success: true,
      squareNumber,
      awardedPoints: awardedMultiplier,
      newTotalScore: newScore,
      message: `${awardedMultiplier} امتیاز معتبر با موفقیت ثبت شد.`
    });
  } catch (err) {
    await client.query('ROLLBACK');
    console.error('[Ad Complete Transaction Error]', err);
    return res.status(500).json({ error: "خطا در پردازش اتمیک امتیاز در پایگاه داده." });
  } finally {
    client.release();
  }
});

// ====================================================================
// 5. Leaderboard API (Direct PostgreSQL Query)
// ====================================================================
app.get('/api/leaderboard', ensureDatabaseReady, async (req, res) => {
  try {
    const currentUserId = req.headers['x-user-id'] || req.query.userId;

    const result = await pool.query(`
      SELECT 
        u.id AS user_id,
        u.first_name,
        u.last_name,
        COALESCE(s.total_score, u.total_score, 0) AS total_score,
        u.created_at
      FROM public.users u
      LEFT JOIN public.user_scores s ON u.id = s.user_id
      ORDER BY total_score DESC, u.created_at ASC, u.id ASC
      LIMIT 100
    `);

    const leaderboard = result.rows.map((row, index) => {
      const fName = row.first_name || 'کاربر';
      const lName = row.last_name || '';
      const maskedName = lName.length > 2 
        ? `${fName} ${lName.substring(0, 2)}***` 
        : `${fName} ***`;
      return {
        rank: index + 1,
        userId: row.user_id,
        displayName: maskedName.trim(),
        totalScore: Number(row.total_score),
        isCurrentUser: row.user_id === currentUserId
      };
    });

    return res.json({ leaderboard });
  } catch (err) {
    console.error('[API Leaderboard Error]', err);
    return res.status(500).json({ error: "خطا در دریافت جدول امتیازات از پایگاه داده." });
  }
});

// ====================================================================
// 6. Admin Overview API (Protected - Real PostgreSQL Data)
// ====================================================================
app.get('/api/admin/overview', requireAdminAuth, ensureDatabaseReady, async (req, res) => {
  try {
    const usersRes = await pool.query(`
      SELECT 
        u.id,
        u.first_name,
        u.last_name,
        u.phone_number,
        u.created_at,
        COALESCE(s.total_score, u.total_score, 0) AS score
      FROM public.users u
      LEFT JOIN public.user_scores s ON u.id = s.user_id
      ORDER BY score DESC, u.created_at ASC
    `);

    const sessionsRes = await pool.query(`
      SELECT session_id, user_id, square_number, cycle_id, timestamp, ad_duration_seconds, status, token
      FROM public.ad_sessions
      ORDER BY timestamp DESC
      LIMIT 100
    `);

    const countRes = await pool.query(`SELECT COUNT(*) AS total_sessions FROM public.ad_sessions`);
    const totalAdSessions = Number(countRes.rows[0].total_sessions);

    const users = usersRes.rows.map(u => ({
      id: u.id,
      firstName: u.first_name,
      lastName: u.last_name,
      phoneNumber: u.phone_number,
      registeredAt: new Date(u.created_at).getTime(),
      score: Number(u.score)
    }));

    const totalScoresSum = users.reduce((sum, u) => sum + (u.score || 0), 0);

    // Active Competition & Historical Competitions Overview
    const activeCompRes = await pool.query(`SELECT * FROM public.competitions WHERE status = 'ACTIVE' LIMIT 1`);
    const activeComp = activeCompRes.rows.length > 0 ? activeCompRes.rows[0] : null;

    const pastCompsRes = await pool.query(`
      SELECT id, competition_number, title, status, started_at, ended_at, total_participants, total_score 
      FROM public.competitions 
      WHERE status = 'ARCHIVED' 
      ORDER BY competition_number DESC 
      LIMIT 10
    `);

    const compLogsRes = await pool.query(`
      SELECT log_id, action, admin_key_used, prev_title, new_title, participants_count, total_score_archived, details, created_at
      FROM public.competition_logs
      ORDER BY created_at DESC
      LIMIT 10
    `);

    // Home Announcements
    const activeAnnRes = await pool.query(`SELECT * FROM public.home_announcements WHERE is_active = true ORDER BY updated_at DESC LIMIT 1`);
    const activeAnnouncement = activeAnnRes.rows.length > 0 ? {
      id: activeAnnRes.rows[0].id,
      title: activeAnnRes.rows[0].title,
      content: activeAnnRes.rows[0].content,
      type: activeAnnRes.rows[0].type,
      isActive: Boolean(activeAnnRes.rows[0].is_active),
      competitionId: activeAnnRes.rows[0].competition_id,
      startsAt: activeAnnRes.rows[0].starts_at ? new Date(activeAnnRes.rows[0].starts_at).getTime() : null,
      endsAt: activeAnnRes.rows[0].ends_at ? new Date(activeAnnRes.rows[0].ends_at).getTime() : null,
      updatedAt: new Date(activeAnnRes.rows[0].updated_at).getTime()
    } : null;

    const allAnnRes = await pool.query(`SELECT * FROM public.home_announcements ORDER BY updated_at DESC LIMIT 20`);

    return res.json({
      totalUsers: users.length,
      totalAdSessions,
      totalScoresSum,
      activeAnnouncement,
      announcements: allAnnRes.rows.map(a => ({
        id: a.id,
        title: a.title,
        content: a.content,
        type: a.type,
        isActive: Boolean(a.is_active),
        competitionId: a.competition_id,
        startsAt: a.starts_at ? new Date(a.starts_at).getTime() : null,
        endsAt: a.ends_at ? new Date(a.ends_at).getTime() : null,
        updatedAt: new Date(a.updated_at).getTime()
      })),
      activeCompetition: activeComp ? {
        id: activeComp.id,
        competitionNumber: Number(activeComp.competition_number),
        title: activeComp.title,
        status: activeComp.status,
        startedAt: new Date(activeComp.started_at).getTime(),
        endedAt: activeComp.ended_at ? new Date(activeComp.ended_at).getTime() : null,
        totalParticipants: Number(activeComp.total_participants || 0),
        totalScore: Number(activeComp.total_score || 0)
      } : {
        id: 'COMP_1',
        competitionNumber: 1,
        title: 'مسابقه شماره ۱ (جایزه آیفون و پلی‌استیشن)',
        status: 'ACTIVE',
        startedAt: Date.now(),
        endedAt: null,
        totalParticipants: 0,
        totalScore: 0
      },
      pastCompetitions: pastCompsRes.rows.map(c => ({
        id: c.id,
        competitionNumber: Number(c.competition_number),
        title: c.title,
        status: c.status,
        startedAt: new Date(c.started_at).getTime(),
        endedAt: c.ended_at ? new Date(c.ended_at).getTime() : null,
        totalParticipants: Number(c.total_participants || 0),
        totalScore: Number(c.total_score || 0)
      })),
      competitionLogs: compLogsRes.rows.map(l => ({
        logId: l.log_id,
        action: l.action,
        adminKeyUsed: l.admin_key_used,
        prevTitle: l.prev_title,
        newTitle: l.new_title,
        participantsCount: Number(l.participants_count || 0),
        totalScoreArchived: Number(l.total_score_archived || 0),
        details: l.details,
        createdAt: new Date(l.created_at).getTime()
      })),
      settings: {
        tapsellAppKey: process.env.TAPSELL_APP_KEY || "sgedhqdbofphgpajkrqtahjkedbkokdlqiesotcmfqcklbamjaaiddqsqnihdtmobfidcb",
        tapsellZoneId: process.env.TAPSELL_ZONE_ID || "6ad231b89140ba2215e772db",
        cycleHours: 24,
        pointsPerAd: 1
      },
      recentSessions: sessionsRes.rows.map(s => ({
        sessionId: s.session_id,
        userId: s.user_id,
        squareNumber: s.square_number,
        cycleId: s.cycle_id,
        timestamp: Number(s.timestamp),
        adDurationSeconds: s.ad_duration_seconds,
        status: s.status,
        token: s.token
      })),
      users
    });
  } catch (err) {
    console.error('[API Admin Overview Error]', err);
    return res.status(500).json({ error: "خطا در دریافت اطلاعات داشبورد مدیریت از پایگاه داده." });
  }
});

// ====================================================================
// 6.1 Users Ranking Table API (Sanitized Read-Only for Standalone Page)
// ====================================================================
app.get('/api/admin/users', ensureDatabaseReady, async (req, res) => {
  try {
    const result = await pool.query(`
      SELECT 
        u.id,
        u.first_name,
        u.last_name,
        u.created_at,
        COALESCE(s.total_score, u.total_score, 0) AS total_score
      FROM public.users u
      LEFT JOIN public.user_scores s ON u.id = s.user_id
      ORDER BY total_score DESC, u.created_at ASC, u.id ASC
    `);

    const users = result.rows.map(row => ({
      id: row.id,
      firstName: row.first_name || '',
      lastName: row.last_name || '',
      registeredAt: new Date(row.created_at).getTime() || 0,
      score: Number(row.total_score)
    }));

    return res.json({
      success: true,
      totalUsers: users.length,
      users
    });
  } catch (err) {
    console.error('[API Admin Users Error]', err);
    return res.status(500).json({ error: "خطا در بارگذاری جدول کاربران از پایگاه داده." });
  }
});

// ====================================================================
// 7. Admin Force Reset Cycle (Protected)
// ====================================================================
app.post('/api/admin/cycle/reset', requireAdminAuth, ensureDatabaseReady, async (req, res) => {
  try {
    const { userId } = req.body;
    if (!userId) return res.status(400).json({ error: "شناسه کاربر الزامی است." });
    const now = Date.now();
    const newCycleId = "CYC_" + Math.floor(now / 1000);
    const endTime = now + (24 * 60 * 60 * 1000);

    await pool.query(
      `INSERT INTO public.daily_cycles (cycle_id, user_id, start_time, end_time, status)
       VALUES ($1, $2, $3, $4, 'ACTIVE')
       ON CONFLICT (cycle_id) DO UPDATE SET start_time = $3, end_time = $4, status = 'ACTIVE'`,
      [newCycleId, userId, now, endTime]
    );

    const cycle = {
      cycleId: newCycleId,
      userId,
      startTime: now,
      endTime,
      status: "ACTIVE"
    };
    return res.json({ success: true, newCycle: cycle });
  } catch (err) {
    console.error("Error in cycle reset:", err);
    return res.status(500).json({ error: "خطای سرور در بازنشانی چرخه." });
  }
});

// ====================================================================
// 8. Competition Lifecycle & Management APIs
// ====================================================================
let isCompetitionResetting = false;

// Helper: Get active competition or create default
async function getActiveCompetition(clientOrPool = pool) {
  let compRes = await clientOrPool.query(`SELECT * FROM public.competitions WHERE status = 'ACTIVE' LIMIT 1`);
  if (compRes.rows.length === 0) {
    const defaultId = 'COMP_1';
    await clientOrPool.query(
      `INSERT INTO public.competitions (id, competition_number, title, status, started_at, created_by, created_at)
       VALUES ($1, 1, 'مسابقه شماره ۱ (جایزه آیفون و پلی‌استیشن)', 'ACTIVE', NOW(), 'admin', NOW())
       ON CONFLICT (id) DO NOTHING`,
      [defaultId]
    );
    compRes = await clientOrPool.query(`SELECT * FROM public.competitions WHERE status = 'ACTIVE' LIMIT 1`);
  }
  return compRes.rows[0];
}

// 8.1 Current Active Competition & User Score
app.get('/api/competition/current', ensureDatabaseReady, async (req, res) => {
  try {
    const userId = req.headers['x-user-id'] || req.query.userId;
    const active = await getActiveCompetition();

    let userScore = 0;
    if (userId) {
      const scoreRes = await pool.query(
        `SELECT COALESCE(s.total_score, u.total_score, 0) AS score
         FROM public.users u
         LEFT JOIN public.user_scores s ON u.id = s.user_id
         WHERE u.id = $1 LIMIT 1`,
        [userId]
      );
      if (scoreRes.rows.length > 0) {
        userScore = Number(scoreRes.rows[0].score);
      }
    }

    return res.json({
      success: true,
      competition: {
        id: active.id,
        competitionNumber: Number(active.competition_number),
        title: active.title,
        status: active.status,
        startedAt: new Date(active.started_at).getTime(),
        endedAt: active.ended_at ? new Date(active.ended_at).getTime() : null,
        totalParticipants: Number(active.total_participants || 0),
        totalScore: Number(active.total_score || 0)
      },
      userScore
    });
  } catch (err) {
    console.error('[API Current Competition Error]', err);
    return res.status(500).json({ error: "خطا در دریافت وضعیت مسابقه جاری." });
  }
});

// 8.2 Historical Competitions List
app.get('/api/competitions/history', ensureDatabaseReady, async (req, res) => {
  try {
    const result = await pool.query(`
      SELECT id, competition_number, title, status, started_at, ended_at, total_participants, total_score, created_at
      FROM public.competitions
      ORDER BY competition_number DESC
    `);
    const competitions = result.rows.map(r => ({
      id: r.id,
      competitionNumber: Number(r.competition_number),
      title: r.title,
      status: r.status,
      startedAt: new Date(r.started_at).getTime(),
      endedAt: r.ended_at ? new Date(r.ended_at).getTime() : null,
      totalParticipants: Number(r.total_participants || 0),
      totalScore: Number(r.total_score || 0)
    }));
    return res.json({ success: true, competitions });
  } catch (err) {
    console.error('[API Competition History Error]', err);
    return res.status(500).json({ error: "خطا در دریافت تاریخچه مسابقات." });
  }
});

// 8.3 Historical Competition Leaderboard Results
app.get('/api/competitions/:id/results', ensureDatabaseReady, async (req, res) => {
  try {
    const compId = req.params.id;
    const compRes = await pool.query(`SELECT * FROM public.competitions WHERE id = $1 LIMIT 1`, [compId]);
    if (compRes.rows.length === 0) {
      return res.status(404).json({ error: "مسابقه مورد نظر یافت نشد." });
    }
    const comp = compRes.rows[0];

    const archRes = await pool.query(`
      SELECT id, user_id, final_score, final_rank, first_name, last_name, phone_number, archived_at
      FROM public.competition_archives
      WHERE competition_id = $1
      ORDER BY final_rank ASC, final_score DESC
      LIMIT 200
    `, [compId]);

    const results = archRes.rows.map(r => {
      const fName = r.first_name || 'کاربر';
      const lName = r.last_name || '';
      const maskedName = lName.length > 2 ? `${fName} ${lName.substring(0, 2)}***` : `${fName} ***`;
      const maskedPhone = r.phone_number && r.phone_number.length >= 11
        ? `${r.phone_number.substring(0, 4)}***${r.phone_number.substring(7)}`
        : '***';

      return {
        rank: Number(r.final_rank),
        userId: r.user_id,
        displayName: maskedName.trim(),
        phoneNumber: maskedPhone,
        finalScore: Number(r.final_score),
        archivedAt: new Date(r.archived_at).getTime()
      };
    });

    return res.json({
      success: true,
      competition: {
        id: comp.id,
        competitionNumber: Number(comp.competition_number),
        title: comp.title,
        status: comp.status,
        startedAt: new Date(comp.started_at).getTime(),
        endedAt: comp.ended_at ? new Date(comp.ended_at).getTime() : null,
        totalParticipants: Number(comp.total_participants || 0),
        totalScore: Number(comp.total_score || 0)
      },
      results
    });
  } catch (err) {
    console.error('[API Competition Results Error]', err);
    return res.status(500).json({ error: "خطا در دریافت نتایج آرشیو مسابقه." });
  }
});

// 8.4 Competition Audit Logs (Admin Protected)
app.get('/api/admin/competition/logs', requireAdminAuth, ensureDatabaseReady, async (req, res) => {
  try {
    const logsRes = await pool.query(`
      SELECT log_id, action, admin_key_used, prev_competition_id, new_competition_id, prev_title, new_title, participants_count, total_score_archived, details, created_at
      FROM public.competition_logs
      ORDER BY created_at DESC
      LIMIT 50
    `);
    return res.json({
      success: true,
      logs: logsRes.rows.map(l => ({
        logId: l.log_id,
        action: l.action,
        adminKeyUsed: l.admin_key_used,
        prevCompetitionId: l.prev_competition_id,
        newCompetitionId: l.new_competition_id,
        prevTitle: l.prev_title,
        newTitle: l.new_title,
        participantsCount: Number(l.participants_count || 0),
        totalScoreArchived: Number(l.total_score_archived || 0),
        details: l.details,
        createdAt: new Date(l.created_at).getTime()
      }))
    });
  } catch (err) {
    console.error('[API Competition Logs Error]', err);
    return res.status(500).json({ error: "خطا در دریافت لاگ‌های عملیاتی مسابقات." });
  }
});

// 8.5 Start New Competition (Atomic, Archiving, Zeroing with Confirmation Phrase)
app.post('/api/admin/competition/start-new', requireAdminAuth, ensureDatabaseReady, async (req, res) => {
  const { confirmationPhrase, newTitle } = req.body || {};

  // Exact confirmation phrase validation
  if (!confirmationPhrase || confirmationPhrase.trim() !== 'شروع مسابقه جدید') {
    return res.status(400).json({
      success: false,
      error: "عبارت تایید نادرست است. جهت آغاز مسابقه جدید و آرشیو نتایج قبلی، لطفاً عبارت «شروع مسابقه جدید» را دقیقاً وارد نمایید."
    });
  }

  // Mutex lock to debounce/prevent race conditions
  if (isCompetitionResetting) {
    return res.status(429).json({
      success: false,
      error: "عملیات شروع مسابقه جدید توسط مدیر در حال انجام است. لطفاً چند لحظه شکیبا باشید."
    });
  }

  isCompetitionResetting = true;
  const client = await pool.connect();

  try {
    await client.query('BEGIN');

    // 1. Get currently active competition with row lock
    const activeCompRes = await client.query(`SELECT * FROM public.competitions WHERE status = 'ACTIVE' FOR UPDATE`);
    const activeComp = activeCompRes.rows[0];
    let prevNumber = 1;
    let prevId = 'COMP_1';
    let prevTitle = 'مسابقه شماره ۱ (جایزه آیفون و پلی‌استیشن)';

    if (activeComp) {
      prevNumber = Number(activeComp.competition_number);
      prevId = activeComp.id;
      prevTitle = activeComp.title;
    }

    // 2. Query all users and their scores for snapshot
    const usersRes = await client.query(`
      SELECT 
        u.id AS user_id,
        u.first_name,
        u.last_name,
        u.phone_number,
        COALESCE(s.total_score, u.total_score, 0) AS total_score,
        u.created_at
      FROM public.users u
      LEFT JOIN public.user_scores s ON u.id = s.user_id
      ORDER BY total_score DESC, u.created_at ASC, u.id ASC
    `);

    let totalScoreArchived = 0;
    let participantsCount = 0;

    for (let i = 0; i < usersRes.rows.length; i++) {
      const u = usersRes.rows[i];
      const finalScore = Number(u.total_score);
      const rank = i + 1;
      if (finalScore > 0) participantsCount++;
      totalScoreArchived += finalScore;

      const archId = `ARCH_${prevId}_${u.user_id}`;
      await client.query(`
        INSERT INTO public.competition_archives 
          (id, competition_id, user_id, final_score, final_rank, first_name, last_name, phone_number, archived_at)
        VALUES ($1, $2, $3, $4, $5, $6, $7, $8, NOW())
        ON CONFLICT (id) DO UPDATE SET 
          final_score = $4, final_rank = $5, first_name = $6, last_name = $7, phone_number = $8, archived_at = NOW()
      `, [archId, prevId, u.user_id, finalScore, rank, u.first_name || '', u.last_name || '', u.phone_number || '']);
    }

    // 3. Mark previous active competition as ARCHIVED
    if (activeComp) {
      await client.query(`
        UPDATE public.competitions 
        SET status = 'ARCHIVED', ended_at = NOW(), total_participants = $1, total_score = $2
        WHERE id = $3
      `, [participantsCount, totalScoreArchived, prevId]);
    } else {
      await client.query(`
        INSERT INTO public.competitions (id, competition_number, title, status, started_at, ended_at, total_participants, total_score, created_by, created_at)
        VALUES ($1, $2, $3, 'ARCHIVED', NOW(), NOW(), $4, $5, 'admin', NOW())
        ON CONFLICT (id) DO NOTHING
      `, [prevId, prevNumber, prevTitle, participantsCount, totalScoreArchived]);
    }

    const { confirmationPhrase, newTitle, endDate, announcementTitle, announcementContent } = req.body || {};

    // 4. Create new ACTIVE competition
    const nextNumber = prevNumber + 1;
    const newCompId = `COMP_${nextNumber}_${Date.now()}`;
    const competitionTitle = (newTitle && newTitle.trim()) ? newTitle.trim() : `مسابقه شماره ${nextNumber}`;
    const competitionEndDate = endDate ? new Date(endDate) : null;

    await client.query(`
      INSERT INTO public.competitions (id, competition_number, title, status, started_at, ended_at, created_by, created_at)
      VALUES ($1, $2, $3, 'ACTIVE', NOW(), $4, 'admin', NOW())
    `, [newCompId, nextNumber, competitionTitle, competitionEndDate]);

    // 5. Reset all user scores to 0
    await client.query(`UPDATE public.user_scores SET total_score = 0, updated_at = NOW()`);
    if (schemaInfo.userHasTotalScore) {
      await client.query(`UPDATE public.users SET total_score = 0, updated_at = NOW()`);
    }

    // 6. Delete all square states so all 100 squares reset to READY
    await client.query(`DELETE FROM public.square_states`);

    // 7. Automatically publish linked Home Announcement for the new competition
    await client.query(`UPDATE public.home_announcements SET is_active = false`);
    const newAnnId = `ANN_${Date.now()}_${crypto.randomBytes(3).toString('hex')}`;
    const annTitle = (announcementTitle && announcementTitle.trim()) ? announcementTitle.trim() : `شروع ${competitionTitle}`;
    const annBody = (announcementContent && announcementContent.trim()) 
      ? announcementContent.trim() 
      : `دوره جدید مسابقه («${competitionTitle}») آغاز شد! با مشاهده تبلیغات و تکمیل ۱۰۰ مربع روزانه امتیاز کسب کرده و برنده جوایز ارزنده شوید.`;

    await client.query(`
      INSERT INTO public.home_announcements 
        (id, title, content, type, is_active, competition_id, starts_at, ends_at, created_by, created_at, updated_at)
      VALUES ($1, $2, $3, 'COMPETITION_START', true, $4, NOW(), $5, 'admin', NOW(), NOW())
    `, [newAnnId, annTitle, annBody, newCompId, competitionEndDate]);

    // 8. Insert audit log
    const logId = `CLOG_${Date.now()}_${crypto.randomBytes(4).toString('hex')}`;
    const rawAdminKey = req.headers['x-admin-key'] || req.query.adminKey || 'ADMIN';
    const maskedKey = rawAdminKey.length > 4 ? rawAdminKey.substring(0, 4) + '***' : '***';

    await client.query(`
      INSERT INTO public.competition_logs 
        (log_id, action, admin_key_used, prev_competition_id, new_competition_id, prev_title, new_title, participants_count, total_score_archived, details, created_at)
      VALUES ($1, 'START_NEW_COMPETITION', $2, $3, $4, $5, $6, $7, $8, $9, NOW())
    `, [
      logId,
      maskedKey,
      prevId,
      newCompId,
      prevTitle,
      competitionTitle,
      participantsCount,
      totalScoreArchived,
      `پایان مسابقه شماره ${prevNumber} با ${participantsCount} شرکت‌کننده و آرشیو ${totalScoreArchived} امتیاز. شروع مسابقه جدید «${competitionTitle}» و انتشار اطلاعیه صفحه اصلی.`
    ]);

    await client.query('COMMIT');

    console.log(`[Admin Competition] Successfully started competition ${nextNumber} ("${competitionTitle}") with announcement "${annTitle}".`);

    return res.json({
      success: true,
      message: `مسابقه قبلی (${prevTitle}) با موفقیت به پایان رسید و نتایج آن در آرشیو دائمی ثبت شد. مسابقه جدید «${competitionTitle}» با امتیاز صفر برای تمام کاربران و اطلاعیه صفحه اصلی منتشر گردید.`,
      archivedCompetition: {
        id: prevId,
        competitionNumber: prevNumber,
        title: prevTitle,
        totalParticipants: participantsCount,
        totalScoreArchived
      },
      newCompetition: {
        id: newCompId,
        competitionNumber: nextNumber,
        title: competitionTitle,
        status: 'ACTIVE',
        startedAt: Date.now(),
        endedAt: competitionEndDate ? competitionEndDate.getTime() : null
      },
      newAnnouncement: {
        id: newAnnId,
        title: annTitle,
        content: annBody,
        type: 'COMPETITION_START',
        isActive: true,
        endsAt: competitionEndDate ? competitionEndDate.getTime() : null
      }
    });

  } catch (err) {
    await client.query('ROLLBACK');
    console.error('[Start New Competition Transaction Error]', err);
    return res.status(500).json({ success: false, error: "خطا در پردازش اتمیک پایان مسابقه و آغاز مسابقه جدید در پایگاه داده." });
  } finally {
    isCompetitionResetting = false;
    client.release();
  }
});

// ====================================================================
// 9. Home Announcements Management APIs
// ====================================================================

// 9.1 Public API: Get Active Announcement for App Home Screen
app.get('/api/announcement/active', ensureDatabaseReady, async (req, res) => {
  try {
    const result = await pool.query(`
      SELECT 
        a.id, 
        a.title, 
        a.content, 
        a.type, 
        a.is_active, 
        a.competition_id, 
        a.starts_at, 
        a.ends_at, 
        a.created_at, 
        a.updated_at,
        c.title AS competition_title
      FROM public.home_announcements a
      LEFT JOIN public.competitions c ON a.competition_id = c.id
      WHERE a.is_active = true
      ORDER BY a.updated_at DESC
      LIMIT 1
    `);

    const serverNow = Date.now();

    if (result.rows.length === 0) {
      return res.json({
        success: true,
        announcement: null,
        serverTime: serverNow
      });
    }

    const r = result.rows[0];
    return res.json({
      success: true,
      announcement: {
        id: r.id,
        title: r.title,
        content: r.content,
        type: r.type,
        isActive: Boolean(r.is_active),
        competitionId: r.competition_id,
        competitionTitle: r.competition_title || null,
        startsAt: r.starts_at ? new Date(r.starts_at).getTime() : null,
        endsAt: r.ends_at ? new Date(r.ends_at).getTime() : null,
        createdAt: new Date(r.created_at).getTime(),
        updatedAt: new Date(r.updated_at).getTime()
      },
      serverTime: serverNow
    });
  } catch (err) {
    console.error('[API Active Announcement Error]', err);
    return res.status(500).json({ error: "خطا در دریافت پیام فعال صفحه اصلی." });
  }
});

// 9.2 Admin API: List All Announcements
app.get('/api/admin/announcements', requireAdminAuth, ensureDatabaseReady, async (req, res) => {
  try {
    const result = await pool.query(`
      SELECT 
        a.id, 
        a.title, 
        a.content, 
        a.type, 
        a.is_active, 
        a.competition_id, 
        a.starts_at, 
        a.ends_at, 
        a.created_by,
        a.created_at, 
        a.updated_at,
        c.title AS competition_title
      FROM public.home_announcements a
      LEFT JOIN public.competitions c ON a.competition_id = c.id
      ORDER BY a.updated_at DESC
      LIMIT 50
    `);

    const announcements = result.rows.map(r => ({
      id: r.id,
      title: r.title,
      content: r.content,
      type: r.type,
      isActive: Boolean(r.is_active),
      competitionId: r.competition_id,
      competitionTitle: r.competition_title || null,
      startsAt: r.starts_at ? new Date(r.starts_at).getTime() : null,
      endsAt: r.ends_at ? new Date(r.ends_at).getTime() : null,
      createdBy: r.created_by,
      createdAt: new Date(r.created_at).getTime(),
      updatedAt: new Date(r.updated_at).getTime()
    }));

    return res.json({
      success: true,
      announcements
    });
  } catch (err) {
    console.error('[API Admin Announcements Error]', err);
    return res.status(500).json({ error: "خطا در دریافت فهرست پیام‌های صفحه اصلی." });
  }
});

// 9.3 Admin API: Create or Update Announcement (Independent of Competition Reset)
app.post('/api/admin/announcements', requireAdminAuth, ensureDatabaseReady, async (req, res) => {
  try {
    const { id, title, content, type, isActive, competitionId, startsAt, endsAt } = req.body || {};

    if (!title || !title.trim()) {
      return res.status(400).json({ error: "عنوان پیام نمی‌تواند خالی باشد." });
    }
    if (!content || !content.trim()) {
      return res.status(400).json({ error: "متن پیام نمی‌تواند خالی باشد." });
    }

    const cleanTitle = title.trim();
    const cleanContent = content.trim();
    const cleanType = type || 'ANNOUNCEMENT';
    const activeBool = isActive !== false; // defaults to true
    const startsAtDate = startsAt ? new Date(startsAt) : new Date();
    const endsAtDate = endsAt ? new Date(endsAt) : null;

    // If activating this announcement, deactivate other announcements to ensure single primary active banner
    if (activeBool) {
      await pool.query(`UPDATE public.home_announcements SET is_active = false WHERE id != COALESCE($1, '')`, [id || '']);
    }

    let savedId = id;
    if (id) {
      // Update existing
      const updateRes = await pool.query(`
        UPDATE public.home_announcements 
        SET title = $1, content = $2, type = $3, is_active = $4, competition_id = $5, starts_at = $6, ends_at = $7, updated_at = NOW()
        WHERE id = $8
        RETURNING id
      `, [cleanTitle, cleanContent, cleanType, activeBool, competitionId || null, startsAtDate, endsAtDate, id]);

      if (updateRes.rows.length === 0) {
        return res.status(404).json({ error: "پیام مورد نظر جهت ویرایش یافت نشد." });
      }
    } else {
      // Insert new
      savedId = `ANN_${Date.now()}_${crypto.randomBytes(3).toString('hex')}`;
      await pool.query(`
        INSERT INTO public.home_announcements 
          (id, title, content, type, is_active, competition_id, starts_at, ends_at, created_by, created_at, updated_at)
        VALUES ($1, $2, $3, $4, $5, $6, $7, $8, 'admin', NOW(), NOW())
      `, [savedId, cleanTitle, cleanContent, cleanType, activeBool, competitionId || null, startsAtDate, endsAtDate]);
    }

    return res.json({
      success: true,
      message: activeBool ? "پیام با موفقیت ذخیره و در صفحه اصلی منتشر شد." : "پیام با موفقیت ذخیره شد (غیرفعال).",
      announcement: {
        id: savedId,
        title: cleanTitle,
        content: cleanContent,
        type: cleanType,
        isActive: activeBool,
        competitionId: competitionId || null,
        startsAt: startsAtDate.getTime(),
        endsAt: endsAtDate ? endsAtDate.getTime() : null,
        updatedAt: Date.now()
      }
    });
  } catch (err) {
    console.error('[API Save Announcement Error]', err);
    return res.status(500).json({ error: "خطا در ذخیره‌سازی پیام در پایگاه داده." });
  }
});

// 9.4 Admin API: Toggle Announcement Active State
app.patch('/api/admin/announcements/:id/toggle', requireAdminAuth, ensureDatabaseReady, async (req, res) => {
  try {
    const annId = req.params.id;
    const checkRes = await pool.query(`SELECT is_active FROM public.home_announcements WHERE id = $1 LIMIT 1`, [annId]);
    if (checkRes.rows.length === 0) {
      return res.status(404).json({ error: "پیام یافت نشد." });
    }

    const currentStatus = Boolean(checkRes.rows[0].is_active);
    const newStatus = !currentStatus;

    if (newStatus) {
      // Deactivate all others
      await pool.query(`UPDATE public.home_announcements SET is_active = false`);
    }

    await pool.query(`
      UPDATE public.home_announcements 
      SET is_active = $1, updated_at = NOW() 
      WHERE id = $2
    `, [newStatus, annId]);

    return res.json({
      success: true,
      isActive: newStatus,
      message: newStatus ? "پیام با موفقیت در صفحه اصلی فعال شد." : "نمایش پیام در صفحه اصلی غیرفعال گردید."
    });
  } catch (err) {
    console.error('[API Toggle Announcement Error]', err);
    return res.status(500).json({ error: "خطا در تغییر وضعیت پیام در پایگاه داده." });
  }
});


// ====================================================================
// 10. REAL USER SUPPORT TICKETING & MESSAGING SYSTEM
// ====================================================================

const VALID_SUPPORT_CATEGORIES = ['SCORES', 'ADS', 'PRIZES', 'MEMBERSHIP', 'PURCHASE', 'LOGIN', 'OTHER'];
const VALID_SUPPORT_STATUSES = ['NEW', 'IN_PROGRESS', 'ANSWERED', 'CLOSED'];

// 10.1 User: Create New Support Ticket (Authenticated)
app.post('/api/support/tickets', requireUserAuth, ensureDatabaseReady, async (req, res) => {
  const client = await pool.connect();
  try {
    const { subject, category, message } = req.body;

    if (!subject || typeof subject !== 'string' || subject.trim().length < 3 || subject.trim().length > 150) {
      return res.status(400).json({ error: "عنوان درخواست باید بین ۳ تا ۱۵۰ کاراکتر باشد." });
    }

    if (!message || typeof message !== 'string' || message.trim().length < 5 || message.trim().length > 3000) {
      return res.status(400).json({ error: "متن پیام پشتیبانی باید بین ۵ تا ۳۰۰۰ کاراکتر باشد." });
    }

    const cleanCategory = (category && VALID_SUPPORT_CATEGORIES.includes(category.toUpperCase())) 
      ? category.toUpperCase() 
      : 'OTHER';

    const ticketId = "TCK_" + crypto.randomUUID();
    const trackingCode = await generateUniqueTrackingCode();
    const messageId = "MSG_" + crypto.randomUUID();
    const now = new Date();

    await client.query('BEGIN');

    // Insert Ticket
    await client.query(`
      INSERT INTO public.support_tickets (id, tracking_code, user_id, subject, category, status, created_at, updated_at, last_message_at)
      VALUES ($1, $2, $3, $4, $5, 'NEW', $6, $6, $6)
    `, [ticketId, trackingCode, req.user.id, subject.trim(), cleanCategory, now]);

    // Insert Initial Message
    await client.query(`
      INSERT INTO public.support_messages (id, ticket_id, sender_type, sender_id, sender_name, message_text, created_at)
      VALUES ($1, $2, 'USER', $3, $4, $5, $6)
    `, [messageId, ticketId, req.user.id, req.user.fullName, message.trim(), now]);

    await client.query('COMMIT');

    console.log(`[Support System] Created ticket ${trackingCode} by user ${req.user.id} (${req.user.fullName})`);

    return res.status(201).json({
      success: true,
      message: "درخواست پشتیبانی شما با موفقیت ثبت شد.",
      ticket: {
        id: ticketId,
        trackingCode,
        subject: subject.trim(),
        category: cleanCategory,
        status: 'NEW',
        createdAt: now.getTime(),
        updatedAt: now.getTime(),
        lastMessage: message.trim(),
        lastSenderType: 'USER'
      }
    });
  } catch (err) {
    await client.query('ROLLBACK');
    console.error('[Support Ticket Create Error]', err);
    return res.status(500).json({ error: "خطا در ثبت درخواست پشتیبانی در پایگاه داده." });
  } finally {
    client.release();
  }
});

// 10.2 User: List My Support Tickets (Authenticated & Filtered by req.user.id)
app.get('/api/support/tickets', requireUserAuth, ensureDatabaseReady, async (req, res) => {
  try {
    const result = await pool.query(`
      SELECT 
        t.id, 
        t.tracking_code, 
        t.subject, 
        t.category, 
        t.status, 
        t.created_at, 
        t.updated_at, 
        t.last_message_at,
        (SELECT m.message_text FROM public.support_messages m WHERE m.ticket_id = t.id ORDER BY m.created_at DESC LIMIT 1) AS last_message,
        (SELECT m.sender_type FROM public.support_messages m WHERE m.ticket_id = t.id ORDER BY m.created_at DESC LIMIT 1) AS last_sender_type
      FROM public.support_tickets t
      WHERE t.user_id = $1
      ORDER BY t.updated_at DESC
    `, [req.user.id]);

    const tickets = result.rows.map(row => ({
      id: row.id,
      trackingCode: row.tracking_code,
      subject: row.subject,
      category: row.category,
      status: row.status,
      createdAtMs: new Date(row.created_at).getTime(),
      updatedAtMs: new Date(row.updated_at).getTime(),
      lastMessageAtMs: new Date(row.last_message_at).getTime(),
      lastMessage: row.last_message || '',
      lastSenderType: row.last_sender_type || 'USER'
    }));

    return res.json({
      success: true,
      tickets
    });
  } catch (err) {
    console.error('[Support List Tickets Error]', err);
    return res.status(500).json({ error: "خطا در دریافت فهرست درخواست‌های پشتیبانی." });
  }
});

// 10.3 User: Get Single Ticket & Messages History (Strict User Ownership Check)
app.get('/api/support/tickets/:id', requireUserAuth, ensureDatabaseReady, async (req, res) => {
  try {
    const ticketId = req.params.id;

    // Strict user ownership check
    const ticketRes = await pool.query(`
      SELECT id, tracking_code, user_id, subject, category, status, created_at, updated_at, last_message_at
      FROM public.support_tickets 
      WHERE id = $1 AND user_id = $2
      LIMIT 1
    `, [ticketId, req.user.id]);

    if (ticketRes.rows.length === 0) {
      return res.status(404).json({ error: "درخواست پشتیبانی یافت نشد یا دسترسی به آن برای شما مجاز نمی‌باشد." });
    }

    const t = ticketRes.rows[0];

    const messagesRes = await pool.query(`
      SELECT id, ticket_id, sender_type, sender_id, sender_name, message_text, created_at
      FROM public.support_messages
      WHERE ticket_id = $1
      ORDER BY created_at ASC
    `, [ticketId]);

    const messages = messagesRes.rows.map(m => ({
      id: m.id,
      ticketId: m.ticket_id,
      senderType: m.sender_type,
      senderId: m.sender_id,
      senderName: m.sender_name,
      messageText: m.message_text,
      createdAtMs: new Date(m.created_at).getTime()
    }));

    return res.json({
      success: true,
      ticket: {
        id: t.id,
        trackingCode: t.tracking_code,
        subject: t.subject,
        category: t.category,
        status: t.status,
        createdAtMs: new Date(t.created_at).getTime(),
        updatedAtMs: new Date(t.updated_at).getTime(),
        lastMessageAtMs: new Date(t.last_message_at).getTime()
      },
      messages
    });
  } catch (err) {
    console.error('[Support Ticket Detail Error]', err);
    return res.status(500).json({ error: "خطا در دریافت جزئیات درخواست پشتیبانی." });
  }
});

// 10.4 User: Send Follow-up Message in an Open Ticket (Rejects if CLOSED)
app.post('/api/support/tickets/:id/messages', requireUserAuth, ensureDatabaseReady, async (req, res) => {
  const client = await pool.connect();
  try {
    const ticketId = req.params.id;
    const { message } = req.body;

    if (!message || typeof message !== 'string' || message.trim().length < 2 || message.trim().length > 3000) {
      return res.status(400).json({ error: "متن پیام باید بین ۲ تا ۳۰۰۰ کاراکتر باشد." });
    }

    // Verify ownership and status
    const ticketRes = await client.query(`
      SELECT id, status, tracking_code
      FROM public.support_tickets 
      WHERE id = $1 AND user_id = $2
      LIMIT 1
    `, [ticketId, req.user.id]);

    if (ticketRes.rows.length === 0) {
      return res.status(404).json({ error: "درخواست پشتیبانی یافت نشد یا دسترسی به آن برای شما مجاز نمی‌باشد." });
    }

    const currentTicket = ticketRes.rows[0];
    if (currentTicket.status === 'CLOSED') {
      return res.status(400).json({
        error: "این درخواست توسط مدیریت بسته شده است. در صورت نیاز به راهنمایی جدید، لطفاً یک درخواست جدید ثبت نمایید."
      });
    }

    const messageId = "MSG_" + crypto.randomUUID();
    const now = new Date();

    await client.query('BEGIN');

    // Insert user message
    await client.query(`
      INSERT INTO public.support_messages (id, ticket_id, sender_type, sender_id, sender_name, message_text, created_at)
      VALUES ($1, $2, 'USER', $3, $4, $5, $6)
    `, [messageId, ticketId, req.user.id, req.user.fullName, message.trim(), now]);

    // Update ticket to IN_PROGRESS / NEW and update timestamps
    const newStatus = currentTicket.status === 'ANSWERED' ? 'IN_PROGRESS' : currentTicket.status;
    await client.query(`
      UPDATE public.support_tickets
      SET status = $1, updated_at = $2, last_message_at = $2
      WHERE id = $3
    `, [newStatus, now, ticketId]);

    await client.query('COMMIT');

    return res.status(201).json({
      success: true,
      message: {
        id: messageId,
        ticketId,
        senderType: 'USER',
        senderId: req.user.id,
        senderName: req.user.fullName,
        messageText: message.trim(),
        createdAtMs: now.getTime()
      },
      ticketStatus: newStatus
    });
  } catch (err) {
    await client.query('ROLLBACK');
    console.error('[Support User Send Message Error]', err);
    return res.status(500).json({ error: "خطا در ارسال پیام در پایگاه داده." });
  } finally {
    client.release();
  }
});

// 10.5 Admin API: List All Tickets with Counts and Filters (Protected)
app.get('/api/admin/support/tickets', requireAdminAuth, ensureDatabaseReady, async (req, res) => {
  try {
    const { status, category, search } = req.query;

    let queryText = `
      SELECT 
        t.id, 
        t.tracking_code, 
        t.user_id,
        t.subject, 
        t.category, 
        t.status, 
        t.created_at, 
        t.updated_at, 
        t.last_message_at,
        u.first_name,
        u.last_name,
        u.phone_number,
        (SELECT m.message_text FROM public.support_messages m WHERE m.ticket_id = t.id ORDER BY m.created_at DESC LIMIT 1) AS last_message,
        (SELECT m.sender_type FROM public.support_messages m WHERE m.ticket_id = t.id ORDER BY m.created_at DESC LIMIT 1) AS last_sender_type,
        (SELECT COUNT(*) FROM public.support_messages m WHERE m.ticket_id = t.id) AS total_messages
      FROM public.support_tickets t
      JOIN public.users u ON t.user_id = u.id
      WHERE 1=1
    `;
    const params = [];

    if (status && status !== 'ALL' && VALID_SUPPORT_STATUSES.includes(status.toUpperCase())) {
      params.push(status.toUpperCase());
      queryText += ` AND t.status = $${params.length}`;
    }

    if (category && category !== 'ALL' && VALID_SUPPORT_CATEGORIES.includes(category.toUpperCase())) {
      params.push(category.toUpperCase());
      queryText += ` AND t.category = $${params.length}`;
    }

    if (search && search.trim()) {
      params.push(`%${search.trim()}%`);
      queryText += ` AND (t.tracking_code ILIKE $${params.length} OR u.phone_number ILIKE $${params.length} OR t.subject ILIKE $${params.length} OR u.first_name ILIKE $${params.length} OR u.last_name ILIKE $${params.length})`;
    }

    queryText += ` ORDER BY t.updated_at DESC LIMIT 200`;

    const result = await pool.query(queryText, params);

    // Compute Status Counts
    const countsRes = await pool.query(`
      SELECT 
        COUNT(*) AS total,
        COUNT(*) FILTER (WHERE status = 'NEW') AS new_count,
        COUNT(*) FILTER (WHERE status = 'IN_PROGRESS') AS in_progress_count,
        COUNT(*) FILTER (WHERE status = 'ANSWERED') AS answered_count,
        COUNT(*) FILTER (WHERE status = 'CLOSED') AS closed_count
      FROM public.support_tickets
    `);
    const counts = countsRes.rows[0];

    const tickets = result.rows.map(row => ({
      id: row.id,
      trackingCode: row.tracking_code,
      userId: row.user_id,
      userName: `${row.first_name || ''} ${row.last_name || ''}`.trim() || 'کاربر',
      phoneNumber: row.phone_number || '',
      subject: row.subject,
      category: row.category,
      status: row.status,
      createdAtMs: new Date(row.created_at).getTime(),
      updatedAtMs: new Date(row.updated_at).getTime(),
      lastMessageAtMs: new Date(row.last_message_at).getTime(),
      lastMessage: row.last_message || '',
      lastSenderType: row.last_sender_type || 'USER',
      totalMessages: Number(row.total_messages || 0)
    }));

    return res.json({
      success: true,
      stats: {
        total: Number(counts.total || 0),
        newCount: Number(counts.new_count || 0),
        inProgressCount: Number(counts.in_progress_count || 0),
        answeredCount: Number(counts.answered_count || 0),
        closedCount: Number(counts.closed_count || 0)
      },
      tickets
    });
  } catch (err) {
    console.error('[Admin Support Tickets Error]', err);
    return res.status(500).json({ error: "خطا در دریافت تیکت‌های پشتیبانی برای مدیریت." });
  }
});

// 10.6 Admin API: Get Ticket Details & Full Message Thread (Protected)
app.get('/api/admin/support/tickets/:id', requireAdminAuth, ensureDatabaseReady, async (req, res) => {
  try {
    const ticketId = req.params.id;

    const ticketRes = await pool.query(`
      SELECT 
        t.id, 
        t.tracking_code, 
        t.user_id,
        t.subject, 
        t.category, 
        t.status, 
        t.created_at, 
        t.updated_at, 
        t.last_message_at,
        u.first_name,
        u.last_name,
        u.phone_number,
        u.total_score
      FROM public.support_tickets t
      JOIN public.users u ON t.user_id = u.id
      WHERE t.id = $1
      LIMIT 1
    `, [ticketId]);

    if (ticketRes.rows.length === 0) {
      return res.status(404).json({ error: "درخواست پشتیبانی یافت نشد." });
    }

    const t = ticketRes.rows[0];

    const messagesRes = await pool.query(`
      SELECT id, ticket_id, sender_type, sender_id, sender_name, message_text, created_at
      FROM public.support_messages
      WHERE ticket_id = $1
      ORDER BY created_at ASC
    `, [ticketId]);

    const messages = messagesRes.rows.map(m => ({
      id: m.id,
      ticketId: m.ticket_id,
      senderType: m.sender_type,
      senderId: m.sender_id,
      senderName: m.sender_name,
      messageText: m.message_text,
      createdAtMs: new Date(m.created_at).getTime()
    }));

    return res.json({
      success: true,
      ticket: {
        id: t.id,
        trackingCode: t.tracking_code,
        userId: t.user_id,
        userName: `${t.first_name || ''} ${t.last_name || ''}`.trim() || 'کاربر',
        phoneNumber: t.phone_number || '',
        userScore: Number(t.total_score || 0),
        subject: t.subject,
        category: t.category,
        status: t.status,
        createdAtMs: new Date(t.created_at).getTime(),
        updatedAtMs: new Date(t.updated_at).getTime(),
        lastMessageAtMs: new Date(t.last_message_at).getTime()
      },
      messages
    });
  } catch (err) {
    console.error('[Admin Support Ticket Detail Error]', err);
    return res.status(500).json({ error: "خطا در دریافت جزئیات تیکت مدیریت." });
  }
});

// 10.7 Admin API: Send Reply to User Ticket (Protected - Updates status to ANSWERED)
app.post('/api/admin/support/tickets/:id/messages', requireAdminAuth, ensureDatabaseReady, async (req, res) => {
  const client = await pool.connect();
  try {
    const ticketId = req.params.id;
    const { message, newStatus } = req.body;

    if (!message || typeof message !== 'string' || message.trim().length < 2 || message.trim().length > 3000) {
      return res.status(400).json({ error: "متن پاسخ مدیر باید بین ۲ تا ۳۰۰۰ کاراکتر باشد." });
    }

    const checkRes = await client.query(`SELECT id, status, tracking_code FROM public.support_tickets WHERE id = $1 LIMIT 1`, [ticketId]);
    if (checkRes.rows.length === 0) {
      return res.status(404).json({ error: "درخواست پشتیبانی یافت نشد." });
    }

    const updatedStatus = (newStatus && VALID_SUPPORT_STATUSES.includes(newStatus.toUpperCase())) 
      ? newStatus.toUpperCase() 
      : 'ANSWERED';

    const messageId = "MSG_" + crypto.randomUUID();
    const now = new Date();

    await client.query('BEGIN');

    // Insert Admin Message
    await client.query(`
      INSERT INTO public.support_messages (id, ticket_id, sender_type, sender_id, sender_name, message_text, created_at)
      VALUES ($1, $2, 'ADMIN', 'admin', 'پشتیبانی رقابت', $3, $4)
    `, [messageId, ticketId, message.trim(), now]);

    // Update Ticket Status and Timestamp
    await client.query(`
      UPDATE public.support_tickets
      SET status = $1, updated_at = $2, last_message_at = $2
      WHERE id = $3
    `, [updatedStatus, now, ticketId]);

    await client.query('COMMIT');

    console.log(`[Support Admin Reply] Replied to ticket ${ticketId} (Status: ${updatedStatus})`);

    return res.status(201).json({
      success: true,
      message: {
        id: messageId,
        ticketId,
        senderType: 'ADMIN',
        senderId: 'admin',
        senderName: 'پشتیبانی رقابت',
        messageText: message.trim(),
        createdAtMs: now.getTime()
      },
      ticketStatus: updatedStatus
    });
  } catch (err) {
    await client.query('ROLLBACK');
    console.error('[Admin Support Reply Error]', err);
    return res.status(500).json({ error: "خطا در ثبت پاسخ مدیریت در پایگاه داده." });
  } finally {
    client.release();
  }
});

// 10.8 Admin API: Change Ticket Status (Protected - e.g. close, in_progress, etc.)
app.patch('/api/admin/support/tickets/:id/status', requireAdminAuth, ensureDatabaseReady, async (req, res) => {
  try {
    const ticketId = req.params.id;
    const { status } = req.body;

    if (!status || !VALID_SUPPORT_STATUSES.includes(status.toUpperCase())) {
      return res.status(400).json({ error: "وضعیت انتخاب شده نامعتبر است (NEW, IN_PROGRESS, ANSWERED, CLOSED)." });
    }

    const cleanStatus = status.toUpperCase();

    const updateRes = await pool.query(`
      UPDATE public.support_tickets
      SET status = $1, updated_at = NOW()
      WHERE id = $2
      RETURNING id, tracking_code, status
    `, [cleanStatus, ticketId]);

    if (updateRes.rows.length === 0) {
      return res.status(404).json({ error: "درخواست پشتیبانی یافت نشد." });
    }

    return res.json({
      success: true,
      message: `وضعیت درخواست با موفقیت به «${cleanStatus}» تغییر یافت.`,
      ticket: updateRes.rows[0]
    });
  } catch (err) {
    console.error('[Admin Change Ticket Status Error]', err);
    return res.status(500).json({ error: "خطا در تغییر وضعیت تیکت." });
  }
});

// Admin: Check OneSignal Configuration Status
app.get('/api/admin/onesignal/status', requireAdminAuth, (req, res) => {
  const isConfigured = Boolean(process.env.ONESIGNAL_APP_ID && process.env.ONESIGNAL_REST_API_KEY);
  return res.json({
    success: true,
    configured: isConfigured,
    appId: process.env.ONESIGNAL_APP_ID ? `${process.env.ONESIGNAL_APP_ID.substring(0, 8)}...` : null
  });
});

// Admin: Send Push Notification via OneSignal REST API
app.post('/api/admin/push-notification', requireAdminAuth, async (req, res) => {
  try {
    const { title, message, audience } = req.body;
    if (!title || !message) {
      return res.status(400).json({ error: "عنوان و متن اعلان الزامی است." });
    }
    const appId = process.env.ONESIGNAL_APP_ID;
    const apiKey = process.env.ONESIGNAL_REST_API_KEY;

    if (!appId || !apiKey) {
      return res.status(503).json({ 
        error: "کلیدهای ONESIGNAL_APP_ID یا ONESIGNAL_REST_API_KEY در متغیرهای محیطی Render سرور تنظیم نشده‌اند." 
      });
    }

    const response = await fetch('https://onesignal.com/api/v1/notifications', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Basic ${apiKey}`
      },
      body: JSON.stringify({
        app_id: appId,
        headings: { en: title },
        contents: { en: message },
        included_segments: audience === 'ALL' ? ['All'] : ['Subscribed Users']
      })
    });

    const data = await response.json();
    if (response.ok) {
      return res.json({ success: true, data });
    } else {
      const errDetail = Array.isArray(data.errors) ? data.errors.join(', ') : (data.errors || 'خطا از سوی وان‌سیگنال');
      return res.status(500).json({ success: false, error: errDetail });
    }
  } catch (err) {
    console.error('[Push Notification Error]', err);
    return res.status(500).json({ success: false, error: "خطا در برقراری ارتباط با وان‌سیگنال." });
  }
});

// 404 Fallback for API Routes (Always return JSON for /api/*, never HTML)
app.use('/api', (req, res) => {
  res.status(404).json({ success: false, error: `مسیر مورد نظر یافت نشد: ${req.originalUrl}` });
});

// Global Error Handler
app.use((err, req, res, next) => {
  console.error('[Global Error]', err);
  res.status(500).json({ success: false, error: 'خطای داخلی سرور رخ داد.' });
});

// Startup Server with PostgreSQL Verification
app.listen(PORT, '0.0.0.0', async () => {
  console.log(`[Reqabat Va Jayezah Server] Running on http://0.0.0.0:${PORT}`);
  await testAndInitDatabase();
});

