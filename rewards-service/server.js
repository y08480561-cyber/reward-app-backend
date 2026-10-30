const express = require('express');
const cors = require('cors');
const { Pool } = require('pg');
const crypto = require('crypto');
require('dotenv').config();

const app = express();
app.use(cors());
app.use(express.json());

// Administrative Auth Key
const ADMIN_KEY = process.env.ADMIN_KEY || 'ReqabatAdmin2026';
const PORT = process.env.PORT || 3001;

// PostgreSQL Connection Pool
const connectionString = process.env.DATABASE_URL || process.env.SUPABASE_DB_URL;
let pool = null;

if (connectionString) {
  const isLocalhost = connectionString.includes('localhost') || connectionString.includes('127.0.0.1');
  pool = new Pool({
    connectionString,
    ssl: isLocalhost ? false : { rejectUnauthorized: false },
    max: 10,
    idleTimeoutMillis: 30000,
    connectionTimeoutMillis: 10000
  });

  pool.on('error', (err) => {
    console.error('[Rewards PostgreSQL Pool Error]', err.message);
  });
}

// Memory fallback store for local development inspection (read-only fallbacks)
const memoryDb = {
  categories: [
    { id: 'cat_recharge', name: 'کارت شارژ', slug: 'recharge-cards', icon: 'smartphone', sort_order: 1, is_active: true },
    { id: 'cat_internet', name: 'بسته‌های اینترنت', slug: 'internet-packages', icon: 'wifi', sort_order: 2, is_active: true },
    { id: 'cat_cash', name: 'جوایز نقدی و هدیه', slug: 'cash-gifts', icon: 'credit-card', sort_order: 3, is_active: true },
    { id: 'cat_digital', name: 'گیفت کارت دیجیتال', slug: 'digital-giftcards', icon: 'gift', sort_order: 4, is_active: true }
  ],
  appearance: {
    id: 'default',
    hero_title: 'ویترین جوایز و پاداش‌های ارزشمند',
    hero_subtitle: 'امتیازهای کسب‌شده خود در مسابقات را به کارت شارژ، بسته‌های اینترنت و جوایز نقدی تبدیل کنید',
    hero_banner_url: 'https://images.unsplash.com/photo-1513151233558-d860c5398176?w=800&q=80',
    primary_color: '#10B981',
    active_sections: { featured: true, categories: true, history: true, banners: true },
    terms_text: 'درخواست‌های دریافت جایزه پس از بررسی توسط تیم پشتیبانی ظرف ۲۴ الی ۷۲ ساعت کاری پردازش و ارسال خواهند شد.'
  },
  rewards: [
    { id: 'rew_irancell_10k', title: 'کارت شارژ ۱۰ هزار تومانی ایرانسل', description: 'کد شارژ مستقیم ۱۰,۰۰۰ تومانی برای سیم‌کارت‌های اعتباری ایرانسل ارسال از طریق پیامک.', category_id: 'cat_recharge', cost_points: 100, stock_quantity: 50, image_url: 'https://images.unsplash.com/photo-1556742049-0a67568d0d9f?w=600&q=80', is_featured: true, is_active: true, sort_order: 1 },
    { id: 'rew_mci_10k', title: 'کارت شارژ ۱۰ هزار تومانی همراه اول', description: 'کد شارژ مستقیم ۱۰,۰۰۰ تومانی برای سیم‌کارت‌های اعتباری همراه اول.', category_id: 'cat_recharge', cost_points: 100, stock_quantity: 50, image_url: 'https://images.unsplash.com/photo-1556742049-0a67568d0d9f?w=600&q=80', is_featured: false, is_active: true, sort_order: 2 },
    { id: 'rew_irancell_net_5gb', title: 'بسته اینترنت ۵ گیگابایت یک‌ماهه', description: 'فعالسازی مستقیم بسته اینترنت ۵ گیگابایت یک‌ماهه بر روی شماره ایرانسل ثبت‌شده شما.', category_id: 'cat_internet', cost_points: 250, stock_quantity: 30, image_url: 'https://images.unsplash.com/photo-1544717297-fa95b6ee9643?w=600&q=80', is_featured: true, is_active: true, sort_order: 3 },
    { id: 'rew_cash_50k', title: 'جایزه نقدی ۵۰,۰۰۰ تومانی', description: 'واریز پایا مستقیم به کارت بانکی متصل به شماره همراه شما.', category_id: 'cat_cash', cost_points: 500, stock_quantity: 20, image_url: 'https://images.unsplash.com/photo-1580519542036-c47de6196ba5?w=600&q=80', is_featured: true, is_active: true, sort_order: 4 }
  ],
  claims: []
};

// Middleware: Admin Security Check
function requireAdminAuth(req, res, next) {
  const adminKey = req.headers['x-admin-key'] ||
                   req.query.admin_key ||
                   req.query.adminKey ||
                   req.query.key ||
                   (req.headers.authorization ? req.headers.authorization.replace(/^Bearer\s+/i, '').trim() : '');

  const expectedKey = process.env.ADMIN_KEY || 'ReqabatAdmin2026';
  if (!adminKey || adminKey !== expectedKey) {
    return res.status(401).json({ success: false, message: 'دسترسی غیرمجاز. کلید مدیریت نامعتبر است.' });
  }
  next();
}

// Middleware: Strict User Authentication against PostgreSQL public.users
async function requireUserAuth(req, res, next) {
  try {
    const rawAuth = req.headers.authorization ? req.headers.authorization.replace(/^Bearer\s+/i, '').trim() : '';
    const token = req.headers['x-auth-token'] || req.headers['x-token'] || rawAuth || req.query.token;
    const headerUserId = req.headers['x-user-id'] || req.body?.user_id || req.query?.userId;

    if (!token && !headerUserId) {
      return res.status(401).json({
        success: false,
        message: 'لطفاً ابتدا وارد حساب کاربری خود شوید (توکن احراز هویت الزامی است).'
      });
    }

    let userRow = null;
    if (pool) {
      if (token) {
        const tokenRes = await pool.query(
          `SELECT id, first_name, last_name, phone_number, status, total_score 
           FROM public.users 
           WHERE auth_token = $1 LIMIT 1`,
          [token]
        );
        if (tokenRes.rows.length > 0) userRow = tokenRes.rows[0];
      }

      if (!userRow && headerUserId) {
        const userRes = await pool.query(
          `SELECT id, first_name, last_name, phone_number, status, total_score, auth_token 
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
    }

    if (!userRow) {
      if (pool || process.env.NODE_ENV === 'production') {
        return res.status(401).json({
          success: false,
          message: 'نشست کاربری شما نامعتبر یا منقضی شده است. لطفاً مجدداً وارد شوید.'
        });
      }
      // Local dev test fallback only
      req.user = { id: headerUserId || 'user_test_999', firstName: 'کاربر', lastName: 'تست' };
      return next();
    }

    if (userRow.status && userRow.status !== 'active') {
      return res.status(403).json({
        success: false,
        message: 'حساب کاربری شما مسدود یا غیرفعال است.'
      });
    }

    req.user = {
      id: userRow.id,
      firstName: userRow.first_name || '',
      lastName: userRow.last_name || '',
      phoneNumber: userRow.phone_number || '',
      totalScore: userRow.total_score || 0
    };
    next();
  } catch (err) {
    console.error('[Rewards requireUserAuth Error]', err);
    return res.status(500).json({ success: false, message: 'خطا در اعتبارسنجی نشست کاربری.' });
  }
}

// Router for rewards service
const rewardsRouter = express.Router();

// 1. PUBLIC: Get Categories
rewardsRouter.get('/categories', async (req, res) => {
  try {
    if (pool) {
      const result = await pool.query(
        'SELECT * FROM public.reward_categories WHERE is_active = true ORDER BY sort_order ASC, name ASC'
      );
      return res.json({ success: true, categories: result.rows });
    }
    const activeCats = memoryDb.categories.filter(c => c.is_active).sort((a,b) => a.sort_order - b.sort_order);
    return res.json({ success: true, categories: activeCats });
  } catch (err) {
    console.error('Error fetching reward categories:', err);
    return res.status(500).json({ success: false, message: 'خطا در دریافت دسته‌بندی‌های جوایز' });
  }
});

// 2. PUBLIC: Get Visual Appearance Settings
rewardsRouter.get('/appearance', async (req, res) => {
  try {
    if (pool) {
      const result = await pool.query('SELECT * FROM public.reward_appearance WHERE id = $1', ['default']);
      if (result.rows.length > 0) {
        return res.json({ success: true, appearance: result.rows[0] });
      }
    }
    return res.json({ success: true, appearance: memoryDb.appearance });
  } catch (err) {
    console.error('Error fetching reward appearance:', err);
    return res.status(500).json({ success: false, message: 'خطا در دریافت تنظیمات ظاهری جوایز' });
  }
});

// 3. PUBLIC: Get Rewards List with filtering
rewardsRouter.get('/', async (req, res) => {
  try {
    const { category, featured, search } = req.query;

    if (pool) {
      let query = `
        SELECT r.*, c.name as category_name, c.slug as category_slug
        FROM public.rewards r
        LEFT JOIN public.reward_categories c ON r.category_id = c.id
        WHERE r.is_active = true AND r.deleted_at IS NULL
      `;
      const queryParams = [];

      if (category) {
        queryParams.push(category);
        query += ` AND (r.category_id = $${queryParams.length} OR c.slug = $${queryParams.length})`;
      }

      if (featured === 'true' || featured === '1') {
        query += ` AND r.is_featured = true`;
      }

      if (search) {
        queryParams.push(`%${search}%`);
        query += ` AND (r.title ILIKE $${queryParams.length} OR r.description ILIKE $${queryParams.length})`;
      }

      query += ` ORDER BY r.sort_order ASC, r.created_at DESC`;

      const result = await pool.query(query, queryParams);
      return res.json({ success: true, rewards: result.rows });
    }

    // Memory Fallback for preview/offline mode
    let items = memoryDb.rewards.filter(r => r.is_active && !r.deleted_at);
    if (category) {
      const catObj = memoryDb.categories.find(c => c.id === category || c.slug === category);
      const catId = catObj ? catObj.id : category;
      items = items.filter(r => r.category_id === catId);
    }
    if (featured === 'true' || featured === '1') {
      items = items.filter(r => r.is_featured);
    }
    if (search) {
      const q = search.toLowerCase();
      items = items.filter(r => r.title.toLowerCase().includes(q) || r.description.toLowerCase().includes(q));
    }
    items.sort((a,b) => a.sort_order - b.sort_order);

    return res.json({ success: true, rewards: items });
  } catch (err) {
    console.error('Error fetching rewards list:', err);
    return res.status(500).json({ success: false, message: 'خطا در دریافت فهرست جوایز' });
  }
});

// 4. PUBLIC: Get Reward Details
rewardsRouter.get('/:id', async (req, res) => {
  try {
    const { id } = req.params;
    if (pool) {
      const result = await pool.query(`
        SELECT r.*, c.name as category_name, c.slug as category_slug
        FROM public.rewards r
        LEFT JOIN public.reward_categories c ON r.category_id = c.id
        WHERE r.id = $1 AND r.deleted_at IS NULL
      `, [id]);
      if (result.rows.length === 0) {
        return res.status(404).json({ success: false, message: 'جایزه موردنظر یافت نشد' });
      }
      return res.json({ success: true, reward: result.rows[0] });
    }

    const item = memoryDb.rewards.find(r => r.id === id && !r.deleted_at);
    if (!item) {
      return res.status(404).json({ success: false, message: 'جایزه موردنظر یافت نشد' });
    }
    return res.json({ success: true, reward: item });
  } catch (err) {
    console.error('Error fetching reward details:', err);
    return res.status(500).json({ success: false, message: 'خطا در دریافت جزئیات جایزه' });
  }
});

// 5. USER: Submit Reward Claim Request (Strict Auth & PostgreSQL Transaction)
rewardsRouter.post('/claim', requireUserAuth, async (req, res) => {
  const userId = req.user.id; // Strictly taken from authenticated user session
  const { reward_id, recipient_name, phone, shipping_address } = req.body;

  if (!reward_id) {
    return res.status(400).json({ success: false, message: 'شناسه جایزه الزامی است' });
  }

  // Strict DB Guard: Do NOT fallback to fake memory database for real user claim transactions
  if (!pool) {
    if (process.env.NODE_ENV === 'production') {
      return res.status(503).json({
        success: false,
        message: 'اتصال به پایگاه داده ابری برقرار نیست. امکان ثبت درخواست جایزه در این لحظه وجود ندارد.'
      });
    }
  }

  if (pool) {
    const client = await pool.connect();
    try {
      await client.query('BEGIN');

      // 1. Lock User Row & Verify Score
      const userRes = await client.query('SELECT total_score FROM public.users WHERE id = $1 FOR UPDATE', [userId]);
      if (userRes.rows.length === 0) {
        await client.query('ROLLBACK');
        return res.status(404).json({ success: false, message: 'کاربر در سیستم یافت نشد' });
      }
      const userScore = userRes.rows[0].total_score || 0;

      // 2. Lock Reward Row & Verify Stock
      const rewardRes = await client.query(
        'SELECT * FROM public.rewards WHERE id = $1 AND is_active = true AND deleted_at IS NULL FOR UPDATE',
        [reward_id]
      );
      if (rewardRes.rows.length === 0) {
        await client.query('ROLLBACK');
        return res.status(404).json({ success: false, message: 'جایزه فعال یافت نشد' });
      }
      const reward = rewardRes.rows[0];

      if (reward.stock_quantity <= 0) {
        await client.query('ROLLBACK');
        return res.status(400).json({ success: false, message: 'موجودی این جایزه به پایان رسیده است' });
      }

      if (userScore < reward.cost_points) {
        await client.query('ROLLBACK');
        return res.status(400).json({
          success: false,
          message: `امتیاز شما (${userScore}) برای دریافت این جایزه (${reward.cost_points}) کافی نیست`
        });
      }

      // 3. Deduct User Points atomically in public.users & public.user_scores
      const newScore = userScore - reward.cost_points;
      await client.query('UPDATE public.users SET total_score = $1, updated_at = NOW() WHERE id = $2', [newScore, userId]);
      await client.query(
        'INSERT INTO public.user_scores (user_id, total_score, updated_at) VALUES ($1, $2, NOW()) ON CONFLICT (user_id) DO UPDATE SET total_score = $2, updated_at = NOW()',
        [userId, newScore]
      );

      // 4. Decrement Stock
      await client.query(
        'UPDATE public.rewards SET stock_quantity = stock_quantity - 1, updated_at = NOW() WHERE id = $1',
        [reward_id]
      );

      // 5. Create Claim Record
      const trackingCode = 'CLM-' + crypto.randomBytes(4).toString('hex').toUpperCase();
      const claimId = 'clm_' + Date.now() + '_' + Math.floor(Math.random()*1000);
      const claimRes = await client.query(`
        INSERT INTO public.reward_claims 
          (id, tracking_code, user_id, reward_id, cost_points, status, recipient_name, phone, shipping_address, created_at, updated_at)
        VALUES ($1, $2, $3, $4, $5, 'PENDING', $6, $7, $8, NOW(), NOW())
        RETURNING *
      `, [claimId, trackingCode, userId, reward_id, reward.cost_points, recipient_name || '', phone || '', shipping_address || '']);

      await client.query('COMMIT');

      return res.json({
        success: true,
        message: 'درخواست دریافت جایزه با موفقیت ثبت گردید',
        tracking_code: trackingCode,
        remaining_score: newScore,
        claim: claimRes.rows[0]
      });
    } catch (err) {
      await client.query('ROLLBACK');
      console.error('Transaction error submitting claim:', err);
      return res.status(500).json({ success: false, message: 'خطای سرور هنگام ثبت درخواست جایزه' });
    } finally {
      client.release();
    }
  }

  // Memory Fallback (Dev/Preview only)
  const reward = memoryDb.rewards.find(r => r.id === reward_id && r.is_active && !r.deleted_at);
  if (!reward) return res.status(404).json({ success: false, message: 'جایزه موردنظر یافت نشد' });
  if (reward.stock_quantity <= 0) return res.status(400).json({ success: false, message: 'موجودی جایزه تمام شده است' });
  reward.stock_quantity -= 1;

  const trackingCode = 'CLM-' + crypto.randomBytes(4).toString('hex').toUpperCase();
  const claim = {
    id: 'clm_' + Date.now(),
    tracking_code: trackingCode,
    user_id: userId,
    reward_id: reward_id,
    cost_points: reward.cost_points,
    status: 'PENDING',
    recipient_name: recipient_name || '',
    phone: phone || '',
    shipping_address: shipping_address || '',
    created_at: new Date().toISOString()
  };
  memoryDb.claims.unshift(claim);

  return res.json({
    success: true,
    message: 'درخواست دریافت جایزه با موفقیت ثبت گردید (حالت تست محلی)',
    tracking_code: trackingCode,
    claim
  });
});

// 6. USER: View User Claims History (Strict Auth)
rewardsRouter.get('/claims/user/:userId', requireUserAuth, async (req, res) => {
  try {
    const authenticatedUserId = req.user.id;
    const requestedUserId = req.params.userId === 'me' ? authenticatedUserId : req.params.userId;

    // Security check: User can only view their own claims history
    if (requestedUserId !== authenticatedUserId && req.user.role !== 'admin') {
      return res.status(403).json({ success: false, message: 'دسترسی غیرمجاز به سوابق سایر کاربران.' });
    }

    if (pool) {
      const result = await pool.query(`
        SELECT c.*, r.title as reward_title, r.image_url as reward_image
        FROM public.reward_claims c
        LEFT JOIN public.rewards r ON c.reward_id = r.id
        WHERE c.user_id = $1
        ORDER BY c.created_at DESC
      `, [requestedUserId]);
      return res.json({ success: true, claims: result.rows });
    }

    const userClaims = memoryDb.claims.filter(c => c.user_id === requestedUserId);
    return res.json({ success: true, claims: userClaims });
  } catch (err) {
    console.error('Error fetching user claims:', err);
    return res.status(500).json({ success: false, message: 'خطا در دریافت سوابق جوایز کاربر' });
  }
});

// ==========================================
// ADMIN ENDPOINTS
// ==========================================

// 7. ADMIN: Get All Rewards
rewardsRouter.get('/admin/rewards', requireAdminAuth, async (req, res) => {
  try {
    if (pool) {
      const result = await pool.query(`
        SELECT r.*, c.name as category_name
        FROM public.rewards r
        LEFT JOIN public.reward_categories c ON r.category_id = c.id
        WHERE r.deleted_at IS NULL
        ORDER BY r.sort_order ASC, r.created_at DESC
      `);
      return res.json({ success: true, rewards: result.rows });
    }
    return res.json({ success: true, rewards: memoryDb.rewards.filter(r => !r.deleted_at) });
  } catch (err) {
    console.error('Admin rewards error:', err);
    return res.status(500).json({ success: false, message: 'خطای سرور در لیست جوایز' });
  }
});

// 8. ADMIN: Create Reward
rewardsRouter.post('/admin/rewards', requireAdminAuth, async (req, res) => {
  try {
    const { title, description, category_id, cost_points, stock_quantity, image_url, is_featured, is_active, sort_order } = req.body;
    if (!title || cost_points === undefined) {
      return res.status(400).json({ success: false, message: 'عنوان جایزه و مقدار امتیاز الزامی هستند' });
    }

    if (!pool && process.env.NODE_ENV === 'production') {
      return res.status(503).json({ success: false, message: 'اتصال به دیتابیس برقرار نیست.' });
    }

    const id = 'rew_' + Date.now() + '_' + Math.floor(Math.random()*1000);

    if (pool) {
      const result = await pool.query(`
        INSERT INTO public.rewards
          (id, title, description, category_id, cost_points, stock_quantity, image_url, is_featured, is_active, sort_order, created_at, updated_at)
        VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, NOW(), NOW())
        RETURNING *
      `, [
        id,
        title,
        description || '',
        category_id || null,
        parseInt(cost_points, 10) || 0,
        parseInt(stock_quantity, 10) || 0,
        image_url || '',
        is_featured === true || is_featured === 'true',
        is_active !== false && is_active !== 'false',
        parseInt(sort_order, 10) || 0
      ]);
      return res.json({ success: true, message: 'جایزه جدید با موفقیت ایجاد شد', reward: result.rows[0] });
    }

    const newReward = {
      id,
      title,
      description: description || '',
      category_id: category_id || null,
      cost_points: parseInt(cost_points, 10) || 0,
      stock_quantity: parseInt(stock_quantity, 10) || 0,
      image_url: image_url || '',
      is_featured: is_featured === true,
      is_active: is_active !== false,
      sort_order: parseInt(sort_order, 10) || 0,
      created_at: new Date().toISOString()
    };
    memoryDb.rewards.push(newReward);
    return res.json({ success: true, message: 'جایزه با موفقیت ایجاد شد (تست محلی)', reward: newReward });
  } catch (err) {
    console.error('Error creating reward:', err);
    return res.status(500).json({ success: false, message: 'خطا در ساخت جایزه جدید' });
  }
});

// 9. ADMIN: Update Reward
rewardsRouter.put('/admin/rewards/:id', requireAdminAuth, async (req, res) => {
  try {
    const { id } = req.params;
    const { title, description, category_id, cost_points, stock_quantity, image_url, is_featured, is_active, sort_order } = req.body;

    if (!pool && process.env.NODE_ENV === 'production') {
      return res.status(503).json({ success: false, message: 'اتصال به دیتابیس برقرار نیست.' });
    }

    if (pool) {
      const result = await pool.query(`
        UPDATE public.rewards
        SET title = COALESCE($1, title),
            description = COALESCE($2, description),
            category_id = COALESCE($3, category_id),
            cost_points = COALESCE($4, cost_points),
            stock_quantity = COALESCE($5, stock_quantity),
            image_url = COALESCE($6, image_url),
            is_featured = COALESCE($7, is_featured),
            is_active = COALESCE($8, is_active),
            sort_order = COALESCE($9, sort_order),
            updated_at = NOW()
        WHERE id = $10 AND deleted_at IS NULL
        RETURNING *
      `, [
        title,
        description,
        category_id,
        cost_points !== undefined ? parseInt(cost_points, 10) : null,
        stock_quantity !== undefined ? parseInt(stock_quantity, 10) : null,
        image_url,
        is_featured,
        is_active,
        sort_order !== undefined ? parseInt(sort_order, 10) : null,
        id
      ]);

      if (result.rows.length === 0) {
        return res.status(404).json({ success: false, message: 'جایزه یافت نشد' });
      }
      return res.json({ success: true, message: 'جایزه با موفقیت به‌روزرسانی شد', reward: result.rows[0] });
    }

    const item = memoryDb.rewards.find(r => r.id === id);
    if (!item) return res.status(404).json({ success: false, message: 'جایزه یافت نشد' });

    if (title !== undefined) item.title = title;
    if (description !== undefined) item.description = description;
    if (category_id !== undefined) item.category_id = category_id;
    if (cost_points !== undefined) item.cost_points = parseInt(cost_points, 10);
    if (stock_quantity !== undefined) item.stock_quantity = parseInt(stock_quantity, 10);
    if (image_url !== undefined) item.image_url = image_url;
    if (is_featured !== undefined) item.is_featured = is_featured;
    if (is_active !== undefined) item.is_active = is_active;
    if (sort_order !== undefined) item.sort_order = parseInt(sort_order, 10);

    return res.json({ success: true, message: 'جایزه به‌روزرسانی شد', reward: item });
  } catch (err) {
    console.error('Error updating reward:', err);
    return res.status(500).json({ success: false, message: 'خطا در بروزرسانی جایزه' });
  }
});

// 10. ADMIN: Delete Reward (Soft Delete)
rewardsRouter.delete('/admin/rewards/:id', requireAdminAuth, async (req, res) => {
  try {
    const { id } = req.params;
    if (!pool && process.env.NODE_ENV === 'production') {
      return res.status(503).json({ success: false, message: 'اتصال به دیتابیس برقرار نیست.' });
    }

    if (pool) {
      await pool.query('UPDATE public.rewards SET deleted_at = NOW(), is_active = false WHERE id = $1', [id]);
      return res.json({ success: true, message: 'جایزه با موفقیت حذف گردید' });
    }
    const idx = memoryDb.rewards.findIndex(r => r.id === id);
    if (idx !== -1) memoryDb.rewards.splice(idx, 1);
    return res.json({ success: true, message: 'جایزه با موفقیت حذف شد' });
  } catch (err) {
    console.error('Error deleting reward:', err);
    return res.status(500).json({ success: false, message: 'خطا در حذف جایزه' });
  }
});

// 11. ADMIN: Manage Categories
rewardsRouter.get('/admin/categories', requireAdminAuth, async (req, res) => {
  try {
    if (pool) {
      const result = await pool.query('SELECT * FROM public.reward_categories ORDER BY sort_order ASC');
      return res.json({ success: true, categories: result.rows });
    }
    return res.json({ success: true, categories: memoryDb.categories });
  } catch (err) {
    return res.status(500).json({ success: false, message: 'خطا در دریافت دسته‌بندی‌ها' });
  }
});

rewardsRouter.post('/admin/categories', requireAdminAuth, async (req, res) => {
  try {
    const { name, slug, icon, sort_order, is_active } = req.body;
    if (!name) return res.status(400).json({ success: false, message: 'نام دسته‌بندی الزامی است' });

    if (!pool && process.env.NODE_ENV === 'production') {
      return res.status(503).json({ success: false, message: 'اتصال به دیتابیس برقرار نیست.' });
    }

    const id = 'cat_' + Date.now();
    const finalSlug = slug || name.toLowerCase().replace(/\s+/g, '-');

    if (pool) {
      const result = await pool.query(`
        INSERT INTO public.reward_categories (id, name, slug, icon, sort_order, is_active)
        VALUES ($1, $2, $3, $4, $5, $6)
        ON CONFLICT (slug) DO UPDATE SET name = $2, icon = $4, sort_order = $5, is_active = $6
        RETURNING *
      `, [id, name, finalSlug, icon || 'gift', parseInt(sort_order, 10) || 0, is_active !== false]);
      return res.json({ success: true, category: result.rows[0] });
    }

    const cat = { id, name, slug: finalSlug, icon: icon || 'gift', sort_order: parseInt(sort_order, 10) || 0, is_active: is_active !== false };
    memoryDb.categories.push(cat);
    return res.json({ success: true, category: cat });
  } catch (err) {
    return res.status(500).json({ success: false, message: 'خطا در ثبت دسته‌بندی' });
  }
});

// 12. ADMIN: Manage Appearance
rewardsRouter.post('/admin/appearance', requireAdminAuth, async (req, res) => {
  try {
    const { hero_title, hero_subtitle, hero_banner_url, primary_color, active_sections, terms_text } = req.body;

    if (!pool && process.env.NODE_ENV === 'production') {
      return res.status(503).json({ success: false, message: 'اتصال به دیتابیس برقرار نیست.' });
    }

    if (pool) {
      const result = await pool.query(`
        INSERT INTO public.reward_appearance (id, hero_title, hero_subtitle, hero_banner_url, primary_color, active_sections, terms_text, updated_at)
        VALUES ('default', $1, $2, $3, $4, $5, $6, NOW())
        ON CONFLICT (id) DO UPDATE SET
          hero_title = EXCLUDED.hero_title,
          hero_subtitle = EXCLUDED.hero_subtitle,
          hero_banner_url = EXCLUDED.hero_banner_url,
          primary_color = EXCLUDED.primary_color,
          active_sections = EXCLUDED.active_sections,
          terms_text = EXCLUDED.terms_text,
          updated_at = NOW()
        RETURNING *
      `, [
        hero_title || 'ویترین جوایز و پاداش‌های ارزشمند',
        hero_subtitle || '',
        hero_banner_url || '',
        primary_color || '#10B981',
        typeof active_sections === 'object' ? JSON.stringify(active_sections) : active_sections,
        terms_text || ''
      ]);
      return res.json({ success: true, message: 'تنظیمات ظاهر با موفقیت بروزرسانی شد', appearance: result.rows[0] });
    }

    memoryDb.appearance = {
      id: 'default',
      hero_title: hero_title || memoryDb.appearance.hero_title,
      hero_subtitle: hero_subtitle || memoryDb.appearance.hero_subtitle,
      hero_banner_url: hero_banner_url || memoryDb.appearance.hero_banner_url,
      primary_color: primary_color || memoryDb.appearance.primary_color,
      active_sections: active_sections || memoryDb.appearance.active_sections,
      terms_text: terms_text || memoryDb.appearance.terms_text
    };
    return res.json({ success: true, message: 'تنظیمات ظاهر بروزرسانی گردید', appearance: memoryDb.appearance });
  } catch (err) {
    console.error('Error saving appearance:', err);
    return res.status(500).json({ success: false, message: 'خطا در به‌روزرسانی تنظیمات ظاهری' });
  }
});

// 13. ADMIN: List & Manage Claims
rewardsRouter.get('/admin/claims', requireAdminAuth, async (req, res) => {
  try {
    const { status } = req.query;
    if (pool) {
      let q = `
        SELECT c.*, r.title as reward_title, r.cost_points as reward_cost, u.first_name, u.last_name, u.phone_number
        FROM public.reward_claims c
        LEFT JOIN public.rewards r ON c.reward_id = r.id
        LEFT JOIN public.users u ON c.user_id = u.id
      `;
      const params = [];
      if (status) {
        params.push(status);
        q += ` WHERE c.status = $1`;
      }
      q += ` ORDER BY c.created_at DESC`;

      const result = await pool.query(q, params);
      return res.json({ success: true, claims: result.rows });
    }

    let items = memoryDb.claims;
    if (status) items = items.filter(c => c.status === status);
    return res.json({ success: true, claims: items });
  } catch (err) {
    console.error('Error fetching claims list:', err);
    return res.status(500).json({ success: false, message: 'خطا در دریافت لیست درخواست‌های جایزه' });
  }
});

// 14. ADMIN: Update Claim Status & Tracking Info
rewardsRouter.patch('/admin/claims/:id', requireAdminAuth, async (req, res) => {
  try {
    const { id } = req.params;
    const { status, admin_notes, tracking_number } = req.body;

    if (!pool && process.env.NODE_ENV === 'production') {
      return res.status(503).json({ success: false, message: 'اتصال به دیتابیس برقرار نیست.' });
    }

    if (pool) {
      const result = await pool.query(`
        UPDATE public.reward_claims
        SET status = COALESCE($1, status),
            admin_notes = COALESCE($2, admin_notes),
            tracking_number = COALESCE($3, tracking_number),
            updated_at = NOW()
        WHERE id = $4
        RETURNING *
      `, [status, admin_notes, tracking_number, id]);

      if (result.rows.length === 0) {
        return res.status(404).json({ success: false, message: 'درخواست جایزه پیدا نشد' });
      }
      return res.json({ success: true, message: 'وضعیت درخواست با موفقیت بروز شد', claim: result.rows[0] });
    }

    const c = memoryDb.claims.find(item => item.id === id || item.tracking_code === id);
    if (!c) return res.status(404).json({ success: false, message: 'درخواست جایزه یافت نشد' });

    if (status) c.status = status;
    if (admin_notes !== undefined) c.admin_notes = admin_notes;
    if (tracking_number !== undefined) c.tracking_number = tracking_number;
    c.updated_at = new Date().toISOString();

    return res.json({ success: true, message: 'وضعیت با موفقیت به‌روزرسانی شد', claim: c });
  } catch (err) {
    console.error('Error updating claim:', err);
    return res.status(500).json({ success: false, message: 'خطا در بروزرسانی وضعیت درخواست' });
  }
});

// Mount router under /api/rewards
app.use('/api/rewards', rewardsRouter);

// Standalone Server listening
if (require.main === module) {
  app.listen(PORT, () => {
    console.log(`Rewards Service is running on port ${PORT}`);
  });
}

module.exports = { app, rewardsRouter, pool };
