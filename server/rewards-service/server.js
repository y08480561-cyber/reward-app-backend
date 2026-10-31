const express = require('express');
const cors = require('cors');
const { Pool } = require('pg');
const crypto = require('crypto');
require('dotenv').config();

const rewardsRouter = express.Router();
rewardsRouter.use(cors());
rewardsRouter.use(express.json());

const ADMIN_KEY = process.env.ADMIN_KEY || 'ReqabatAdmin2026';
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
}

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

function requireAdminAuth(req, res, next) {
  const adminKey = req.headers['x-admin-key'] || req.query.admin_key || req.query.adminKey || req.query.key || (req.headers.authorization ? req.headers.authorization.replace(/^Bearer\s+/i, '').trim() : '');
  const expectedKey = process.env.ADMIN_KEY || 'ReqabatAdmin2026';
  if (!adminKey || adminKey !== expectedKey) {
    return res.status(401).json({ success: false, message: 'دسترسی غیرمجاز. کلید مدیریت نامعتبر است.' });
  }
  next();
}

async function requireUserAuth(req, res, next) {
  try {
    const rawAuth = req.headers.authorization ? req.headers.authorization.replace(/^Bearer\s+/i, '').trim() : '';
    const token = req.headers['x-auth-token'] || req.headers['x-token'] || rawAuth || req.query.token;
    const headerUserId = req.headers['x-user-id'] || req.body?.user_id || req.query?.userId;

    if (!token && !headerUserId) {
      return res.status(401).json({ success: false, message: 'لطفاً ابتدا وارد حساب کاربری خود شوید.' });
    }

    let userRow = null;
    if (pool) {
      if (token) {
        // Secure verification using token
        const uRes = await pool.query('SELECT * FROM public.users WHERE auth_token = $1 LIMIT 1', [token]);
        if (uRes.rows.length > 0) {
          userRow = uRes.rows[0];
          // Security Check: Token must belong to the requested userId if provided
          if (headerUserId && userRow.id !== headerUserId) {
            return res.status(403).json({ success: false, message: 'توکن امنیتی با شناسه کاربر مطابقت ندارد.' });
          }
        } else {
          return res.status(401).json({ success: false, message: 'نشست کاربری نامعتبر یا منقضی شده است.' });
        }
      } else if (headerUserId) {
        // Fallback for development/legacy (should be disabled in production)
        const uRes = await pool.query('SELECT * FROM public.users WHERE id = $1', [headerUserId]);
        if (uRes.rows.length > 0) userRow = uRes.rows[0];
      }
    }

    if (!userRow && headerUserId) {
      userRow = { id: headerUserId };
    }

    if (!userRow) {
      return res.status(401).json({ success: false, message: 'کاربر یافت نشد.' });
    }

    req.user = userRow;
    next();
  } catch (err) {
    console.error('[Auth Error]', err.message);
    return res.status(401).json({ success: false, message: 'خطا در احراز هویت کاربر.' });
  }
}

rewardsRouter.get('/categories', async (req, res) => {
  try {
    if (pool) {
      const result = await pool.query('SELECT * FROM public.reward_categories WHERE is_active = true ORDER BY sort_order ASC');
      return res.json({ success: true, categories: result.rows });
    }
    return res.json({ success: true, categories: memoryDb.categories });
  } catch (err) {
    return res.status(500).json({ success: false, message: 'خطا در دریافت دسته‌بندی‌ها' });
  }
});

rewardsRouter.get('/appearance', async (req, res) => {
  try {
    if (pool) {
      const result = await pool.query('SELECT * FROM public.reward_appearance WHERE id = $1', ['default']);
      if (result.rows.length > 0) return res.json({ success: true, appearance: result.rows[0] });
    }
    return res.json({ success: true, appearance: memoryDb.appearance });
  } catch (err) {
    return res.status(500).json({ success: false, message: 'خطا در دریافت تنظیمات ظاهر' });
  }
});

rewardsRouter.get('/', async (req, res) => {
  try {
    const { category, featured, search } = req.query;
    if (pool) {
      let query = `SELECT r.*, c.name as category_name, c.slug as category_slug FROM public.rewards r LEFT JOIN public.reward_categories c ON r.category_id = c.id WHERE r.is_active = true AND r.deleted_at IS NULL`;
      const queryParams = [];
      if (category) {
        queryParams.push(category);
        query += ` AND (r.category_id = $${queryParams.length} OR c.slug = $${queryParams.length})`;
      }
      if (featured === 'true' || featured === '1') query += ` AND r.is_featured = true`;
      if (search) {
        queryParams.push(`%${search}%`);
        query += ` AND (r.title ILIKE $${queryParams.length} OR r.description ILIKE $${queryParams.length})`;
      }
      query += ` ORDER BY r.sort_order ASC, r.created_at DESC`;
      const result = await pool.query(query, queryParams);
      return res.json({ success: true, rewards: result.rows });
    }
    return res.json({ success: true, rewards: memoryDb.rewards });
  } catch (err) {
    console.error('[Rewards API Error] GET /api/rewards failed:', err.message);
    return res.status(500).json({ success: false, message: 'خطا در دریافت لیست جوایز' });
  }
});

rewardsRouter.post('/claim', requireUserAuth, async (req, res) => {
  const client = await (pool ? pool.connect() : null);
  try {
    const userId = req.user.id;
    const { reward_id, recipient_name, phone, shipping_address, idempotency_key } = req.body;
    
    if (!userId || !reward_id) {
      return res.status(400).json({ success: false, message: 'اطلاعات ناقص است.' });
    }

    if (!client) {
      // Memory Fallback (Limited Logic)
      const claimId = 'clm_' + Date.now();
      const trackingCode = 'CLM-' + crypto.randomBytes(4).toString('hex').toUpperCase();
      const newClaim = {
        id: claimId,
        tracking_code: trackingCode,
        user_id: userId,
        reward_id,
        cost_points: 100,
        status: 'PENDING',
        recipient_name: recipient_name || '',
        phone: phone || '',
        shipping_address: shipping_address || '',
        created_at: new Date().toISOString()
      };
      memoryDb.claims.push(newClaim);
      return res.json({ success: true, claim: newClaim, tracking_code: trackingCode });
    }

    await client.query('BEGIN');

    // 1. Fetch Reward with lock
    const rewRes = await client.query('SELECT * FROM public.rewards WHERE id = $1 FOR UPDATE', [reward_id]);
    if (rewRes.rows.length === 0) {
      await client.query('ROLLBACK');
      return res.status(404).json({ success: false, message: 'جایزه یافت نشد.' });
    }
    const reward = rewRes.rows[0];

    if (!reward.is_active || reward.deleted_at) {
      await client.query('ROLLBACK');
      return res.status(400).json({ success: false, message: 'این جایزه در حال حاضر غیرفعال است.' });
    }

    // 2. Check Stock
    if (reward.stock_quantity <= 0) {
      await client.query('ROLLBACK');
      return res.status(400).json({ success: false, message: 'موجودی این جایزه به پایان رسیده است.' });
    }

    // 3. Check User Score
    const scoreRes = await client.query('SELECT total_score FROM public.user_scores WHERE user_id = $1 FOR UPDATE', [userId]);
    const currentScore = scoreRes.rows.length > 0 ? Number(scoreRes.rows[0].total_score) : 0;

    if (currentScore < reward.cost_points) {
      await client.query('ROLLBACK');
      return res.status(400).json({ success: false, message: `امتیاز شما کافی نیست. (نیاز: ${reward.cost_points}، موجودی: ${currentScore})` });
    }

    // 4. Check Idempotency (Optional but recommended)
    if (idempotency_key) {
      const idempCheck = await client.query('SELECT id FROM public.reward_claims WHERE idempotency_key = $1 LIMIT 1', [idempotency_key]);
      if (idempCheck.rows.length > 0) {
        await client.query('ROLLBACK');
        return res.status(409).json({ success: false, message: 'این درخواست قبلاً ثبت شده است.' });
      }
    }

    // 5. Deduct Score
    await client.query('UPDATE public.user_scores SET total_score = total_score - $1, updated_at = NOW() WHERE user_id = $2', [reward.cost_points, userId]);
    await client.query('UPDATE public.users SET total_score = total_score - $1, updated_at = NOW() WHERE id = $2', [reward.cost_points, userId]);

    // 6. Deduct Stock
    await client.query('UPDATE public.rewards SET stock_quantity = stock_quantity - 1, updated_at = NOW() WHERE id = $1', [reward_id]);

    // 7. Create Claim
    const claimId = 'clm_' + crypto.randomBytes(8).toString('hex');
    const trackingCode = 'CLM-' + crypto.randomBytes(4).toString('hex').toUpperCase();
    
    const claimRes = await client.query(`
      INSERT INTO public.reward_claims 
      (id, tracking_code, user_id, reward_id, cost_points, status, recipient_name, phone, shipping_address, idempotency_key, created_at, updated_at)
      VALUES ($1, $2, $3, $4, $5, 'PENDING', $6, $7, $8, $9, NOW(), NOW())
      RETURNING *
    `, [claimId, trackingCode, userId, reward_id, reward.cost_points, recipient_name || '', phone || '', shipping_address || '', idempotency_key || null]);

    await client.query('COMMIT');
    
    console.log(`[Reward Claim] User ${userId} claimed ${reward.title}. Score Deducted: ${reward.cost_points}`);

    return res.json({ 
      success: true, 
      message: 'درخواست شما با موفقیت ثبت شد و پس از بررسی نهایی پردازش خواهد شد.',
      claim: claimRes.rows[0], 
      tracking_code: trackingCode 
    });

  } catch (err) {
    if (client) await client.query('ROLLBACK');
    console.error('[Rewards Claim Error]', err);
    return res.status(500).json({ success: false, message: 'خطا در فرآیند ثبت درخواست جایزه' });
  } finally {
    if (client) client.release();
  }
});

rewardsRouter.get('/claims/user/:userId', requireUserAuth, async (req, res) => {
  try {
    const { userId } = req.params;
    if (pool) {
      const result = await pool.query('SELECT c.*, r.title as reward_title FROM public.reward_claims c LEFT JOIN public.rewards r ON c.reward_id = r.id WHERE c.user_id = $1 ORDER BY c.created_at DESC', [userId]);
      return res.json({ success: true, claims: result.rows });
    }
    const userClaims = memoryDb.claims.filter(c => c.user_id === userId);
    return res.json({ success: true, claims: userClaims });
  } catch (err) {
    console.error('[Rewards API Error] GET /api/rewards/claims/user/:userId failed:', { message: err.message, userId: req.params.userId });
    return res.status(500).json({ success: false, message: 'خطا در دریافت سوابق' });
  }
});

rewardsRouter.get('/admin/rewards', requireAdminAuth, async (req, res) => {
  try {
    if (pool) {
      const result = await pool.query('SELECT r.*, c.name as category_name FROM public.rewards r LEFT JOIN public.reward_categories c ON r.category_id = c.id WHERE r.deleted_at IS NULL ORDER BY r.sort_order ASC');
      return res.json({ success: true, rewards: result.rows });
    }
    return res.json({ success: true, rewards: memoryDb.rewards });
  } catch (err) {
    console.error('[Rewards Admin Error] GET /admin/rewards failed:', err.message);
    return res.status(500).json({ success: false, message: 'خطا در لیست جوایز' });
  }
});

rewardsRouter.post('/admin/rewards', requireAdminAuth, async (req, res) => {
  try {
    const { title, description, category_id, cost_points, stock_quantity, image_url, is_featured, is_active, sort_order } = req.body;
    const id = 'rew_' + Date.now();
    if (pool) {
      const result = await pool.query(`
        INSERT INTO public.rewards (id, title, description, category_id, cost_points, stock_quantity, image_url, is_featured, is_active, sort_order, created_at, updated_at)
        VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, NOW(), NOW())
        RETURNING *
      `, [id, title, description || '', category_id || null, cost_points || 0, stock_quantity || 0, image_url || '', !!is_featured, is_active !== false, sort_order || 0]);
      return res.json({ success: true, reward: result.rows[0] });
    }
    const newReward = {
      id,
      title,
      description: description || '',
      category_id: category_id || null,
      cost_points: cost_points || 0,
      stock_quantity: stock_quantity || 0,
      image_url: image_url || '',
      is_featured: !!is_featured,
      is_active: is_active !== false,
      sort_order: sort_order || 0
    };
    memoryDb.rewards.push(newReward);
    return res.json({ success: true, reward: newReward });
  } catch (err) {
    console.error('[Rewards Admin Error] POST /admin/rewards failed:', err.message);
    return res.status(500).json({ success: false, message: 'خطا در ایجاد جایزه' });
  }
});

rewardsRouter.put('/admin/rewards/:id', requireAdminAuth, async (req, res) => {
  try {
    const { id } = req.params;
    const { title, description, category_id, cost_points, stock_quantity, image_url, is_featured, is_active, sort_order } = req.body;
    if (pool) {
      const result = await pool.query(`
        UPDATE public.rewards
        SET title = COALESCE($1, title), description = COALESCE($2, description), category_id = COALESCE($3, category_id), cost_points = COALESCE($4, cost_points), stock_quantity = COALESCE($5, stock_quantity), image_url = COALESCE($6, image_url), is_featured = COALESCE($7, is_featured), is_active = COALESCE($8, is_active), sort_order = COALESCE($9, sort_order), updated_at = NOW()
        WHERE id = $10 RETURNING *
      `, [title, description, category_id, cost_points, stock_quantity, image_url, is_featured, is_active, sort_order, id]);
      return res.json({ success: true, reward: result.rows[0] });
    }
    return res.json({ success: true });
  } catch (err) {
    console.error('[Rewards Admin Error] PUT /admin/rewards/:id failed:', err.message);
    return res.status(500).json({ success: false, message: 'خطا در ویرایش جایزه' });
  }
});

rewardsRouter.delete('/admin/rewards/:id', requireAdminAuth, async (req, res) => {
  try {
    const { id } = req.params;
    if (pool) {
      await pool.query('UPDATE public.rewards SET deleted_at = NOW(), is_active = false WHERE id = $1', [id]);
      return res.json({ success: true });
    }
    return res.json({ success: true });
  } catch (err) {
    console.error('[Rewards Admin Error] DELETE /admin/rewards/:id failed:', err.message);
    return res.status(500).json({ success: false, message: 'خطا در حذف' });
  }
});

rewardsRouter.get('/admin/claims', requireAdminAuth, async (req, res) => {
  try {
    if (pool) {
      const result = await pool.query('SELECT c.*, r.title as reward_title, u.first_name, u.last_name, u.phone_number as phone FROM public.reward_claims c LEFT JOIN public.rewards r ON c.reward_id = r.id LEFT JOIN public.users u ON c.user_id = u.id ORDER BY c.created_at DESC');
      return res.json({ success: true, claims: result.rows });
    }
    return res.json({ success: true, claims: [] });
  } catch (err) {
    console.error('[Rewards Admin Error] GET /admin/claims failed:', err.message);
    return res.status(500).json({ success: false, message: 'خطا در دریافت درخواست‌ها' });
  }
});

rewardsRouter.patch('/admin/claims/:id', requireAdminAuth, async (req, res) => {
  const client = await (pool ? pool.connect() : null);
  try {
    const { id } = req.params;
    const { status, admin_notes, tracking_number } = req.body;

    if (!client) {
      const c = memoryDb.claims.find(item => item.id === id || item.tracking_code === id);
      if (!c) return res.status(404).json({ success: false, message: 'درخواست جایزه یافت نشد' });
      if (status) c.status = status;
      if (admin_notes !== undefined) c.admin_notes = admin_notes;
      if (tracking_number !== undefined) c.tracking_number = tracking_number;
      c.updated_at = new Date().toISOString();
      return res.json({ success: true, message: 'وضعیت با موفقیت به‌روزرسانی شد', claim: c });
    }

    await client.query('BEGIN');

    // 1. Fetch existing claim
    const claimRes = await client.query('SELECT * FROM public.reward_claims WHERE id = $1 FOR UPDATE', [id]);
    if (claimRes.rows.length === 0) {
      await client.query('ROLLBACK');
      return res.status(404).json({ success: false, message: 'درخواست یافت نشد.' });
    }
    const oldClaim = claimRes.rows[0];

    // 2. Handle Rejection Refund (if transitioning TO rejected from a non-rejected state)
    const normalizedOldStatus = (oldClaim.status || '').toUpperCase();
    const normalizedNewStatus = (status || '').toUpperCase();

    if (normalizedNewStatus === 'REJECTED' && normalizedOldStatus !== 'REJECTED') {
      // Return points to user
      await client.query('UPDATE public.user_scores SET total_score = total_score + $1, updated_at = NOW() WHERE user_id = $2', [oldClaim.cost_points, oldClaim.user_id]);
      await client.query('UPDATE public.users SET total_score = total_score + $1, updated_at = NOW() WHERE id = $2', [oldClaim.cost_points, oldClaim.user_id]);
      
      // Return stock to reward
      await client.query('UPDATE public.rewards SET stock_quantity = stock_quantity + 1, updated_at = NOW() WHERE id = $1', [oldClaim.reward_id]);
      
      console.log(`[Refund] Refunded ${oldClaim.cost_points} points to user ${oldClaim.user_id} for claim ${id}`);
    } 
    // 3. Handle Reversal of Rejection (if transitioning FROM rejected to something else - rare but possible)
    else if (oldClaim.status === 'REJECTED' && status && status !== 'REJECTED') {
      // Re-deduct points (ensure user has enough)
      const scoreRes = await client.query('SELECT total_score FROM public.user_scores WHERE user_id = $1 FOR UPDATE', [oldClaim.user_id]);
      const currentScore = scoreRes.rows.length > 0 ? Number(scoreRes.rows[0].total_score) : 0;
      
      if (currentScore < oldClaim.cost_points) {
        await client.query('ROLLBACK');
        return res.status(400).json({ success: false, message: 'امتیاز کاربر برای بازگرداندن از حالت رد شده کافی نیست.' });
      }
      
      await client.query('UPDATE public.user_scores SET total_score = total_score - $1, updated_at = NOW() WHERE user_id = $2', [oldClaim.cost_points, oldClaim.user_id]);
      await client.query('UPDATE public.users SET total_score = total_score - $1, updated_at = NOW() WHERE id = $2', [oldClaim.cost_points, oldClaim.user_id]);
      
      // Re-deduct stock
      await client.query('UPDATE public.rewards SET stock_quantity = stock_quantity - 1, updated_at = NOW() WHERE id = $1', [oldClaim.reward_id]);
    }

    // 4. Update Claim Status
    const result = await client.query(`
      UPDATE public.reward_claims 
      SET status = COALESCE($1, status), 
          admin_notes = COALESCE($2, admin_notes), 
          tracking_number = COALESCE($3, tracking_number), 
          updated_at = NOW() 
      WHERE id = $4 
      RETURNING *
    `, [status, admin_notes, tracking_number, id]);

    await client.query('COMMIT');
    return res.json({ success: true, message: 'درخواست با موفقیت بروزرسانی شد.', claim: result.rows[0] });

  } catch (err) {
    if (client) await client.query('ROLLBACK');
    console.error('[Admin Claim Update Error]', err);
    return res.status(500).json({ success: false, message: 'خطا در بروزرسانی درخواست.' });
  } finally {
    if (client) client.release();
  }
});

const saveAppearanceHandler = async (req, res) => {
  try {
    const { hero_title, hero_subtitle, hero_banner_url, primary_color, active_sections, is_active, terms_text } = req.body;
    
    let activeSecObj = active_sections;
    if (!activeSecObj) {
      activeSecObj = { isActive: is_active !== false };
    } else if (typeof activeSecObj === 'string') {
      try { activeSecObj = JSON.parse(activeSecObj); } catch(e) { activeSecObj = { isActive: is_active !== false }; }
    }
    const activeSecStr = JSON.stringify(activeSecObj);

    if (pool) {
      const result = await pool.query(`
        INSERT INTO public.reward_appearance (id, hero_title, hero_subtitle, hero_banner_url, primary_color, active_sections, terms_text, updated_at)
        VALUES ('default', $1, $2, $3, $4, $5, $6, NOW())
        ON CONFLICT (id) DO UPDATE SET hero_title = EXCLUDED.hero_title, hero_subtitle = EXCLUDED.hero_subtitle, hero_banner_url = EXCLUDED.hero_banner_url, primary_color = EXCLUDED.primary_color, active_sections = EXCLUDED.active_sections, terms_text = EXCLUDED.terms_text, updated_at = NOW()
        RETURNING *
      `, [hero_title || 'ویترین هدیه و پاداش ویژه', hero_subtitle || '', hero_banner_url || '', primary_color || '#10B981', activeSecStr, terms_text || '']);
      return res.json({ success: true, appearance: result.rows[0] });
    }

    memoryDb.appearance = {
      ...memoryDb.appearance,
      hero_title: hero_title || memoryDb.appearance.hero_title,
      hero_subtitle: hero_subtitle || memoryDb.appearance.hero_subtitle,
      hero_banner_url: hero_banner_url !== undefined ? hero_banner_url : memoryDb.appearance.hero_banner_url,
      primary_color: primary_color || memoryDb.appearance.primary_color,
      active_sections: activeSecObj,
      terms_text: terms_text !== undefined ? terms_text : memoryDb.appearance.terms_text
    };

    return res.json({ success: true, appearance: memoryDb.appearance });
  } catch (err) {
    console.error('[Rewards Admin Error] Save appearance failed:', err.message);
    return res.status(500).json({ success: false, message: 'خطا در ذخیره ظاهر و بنر هدیه' });
  }
};

rewardsRouter.post('/admin/appearance', saveAppearanceHandler);
rewardsRouter.post('/appearance', saveAppearanceHandler);

function setPool(newPool) {
  pool = newPool;
}

const app = express();
app.use(cors());
app.use(express.json());
app.use('/api/rewards', rewardsRouter);

module.exports = { app, rewardsRouter, pool, setPool };
