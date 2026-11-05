const express = require('express');
const cors = require('cors');
const mysql = require('mysql2/promise');
const crypto = require('crypto');
const path = require('path');
require('dotenv').config();

const rewardsRouter = express.Router();
rewardsRouter.use(cors());
rewardsRouter.use(express.json());

const ADMIN_KEY = process.env.ADMIN_KEY;
let pool = null;

// Unified Admin Authentication Middleware
function requireAdminAuth(req, res, next) {
  const adminKey = req.headers['x-admin-key'] ||
                   (req.headers.authorization ? req.headers.authorization.replace(/^Bearer\s+/i, '').trim() : '');
  const expectedKey = process.env.ADMIN_KEY;
  if (!expectedKey || !adminKey || adminKey !== expectedKey) {
    return res.status(401).json({ success: false, message: 'دسترسی غیرمجاز. کلید مدیریت تنظیم نشده یا نامعتبر است.' });
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
        const [rows] = await pool.query('SELECT * FROM users WHERE auth_token = ? LIMIT 1', [token]);
        if (rows.length > 0) {
          userRow = rows[0];
          if (headerUserId && String(userRow.id) !== String(headerUserId)) {
            return res.status(403).json({ success: false, message: 'توکن امنیتی با شناسه کاربر مطابقت ندارد.' });
          }
        } else {
          return res.status(401).json({ success: false, message: 'نشست کاربری نامعتبر یا منقضی شده است.' });
        }
      } else if (headerUserId) {
        const [rows] = await pool.query('SELECT * FROM users WHERE id = ?', [headerUserId]);
        if (rows.length > 0) userRow = rows[0];
      }
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
    if (!pool) return res.status(503).json({ success: false, message: 'پایگاه داده در دسترس نیست.' });
    const [rows] = await pool.query('SELECT * FROM reward_categories WHERE is_active = true ORDER BY sort_order ASC');
    return res.json({ success: true, categories: rows });
  } catch (err) {
    return res.status(500).json({ success: false, message: 'خطا در دریافت دسته‌بندی‌ها' });
  }
});

rewardsRouter.get('/appearance', async (req, res) => {
  if (!pool) return res.status(503).json({ success: false, message: 'اتصال به پایگاه داده برقرار نیست.' });
  try {
    const [rows] = await pool.query('SELECT * FROM reward_appearance WHERE id = ? LIMIT 1', ['default']);
    if (rows.length === 0) {
      return res.status(404).json({ success: false, message: 'تنظیمات ظاهری یافت نشد.' });
    }
    return res.json({ success: true, appearance: rows[0] });
  } catch (err) {
    console.error('[Rewards Error] Failed to fetch appearance:', err.message);
    return res.status(500).json({ success: false, message: 'خطا در دریافت تنظیمات ظاهر.' });
  }
});

rewardsRouter.get('/', async (req, res) => {
  try {
    const { category, featured, search } = req.query;
    if (!pool) return res.status(503).json({ success: false, message: 'پایگاه داده در دسترس نیست.' });
    
    let query = `SELECT r.*, c.name as category_name, c.slug as category_slug FROM rewards r LEFT JOIN reward_categories c ON r.category_id = c.id WHERE r.is_active = true AND r.deleted_at IS NULL`;
    const queryParams = [];
    
    if (category) {
      queryParams.push(category);
      query += ` AND (r.category_id = ? OR c.slug = ?)`;
      queryParams.push(category);
    }
    if (featured === 'true' || featured === '1') query += ` AND r.is_featured = true`;
    if (search) {
      queryParams.push(`%${search}%`);
      query += ` AND (r.title LIKE ? OR r.description LIKE ?)`;
      queryParams.push(`%${search}%`);
    }
    query += ` ORDER BY r.sort_order ASC, r.created_at DESC`;
    
    const [rows] = await pool.query(query, queryParams);
    return res.json({ success: true, rewards: rows });
  } catch (err) {
    console.error('[Rewards API Error] GET /api/rewards failed:', err.message);
    return res.status(500).json({ success: false, message: 'خطا در دریافت لیست جوایز' });
  }
});

rewardsRouter.post('/claim', requireUserAuth, async (req, res) => {
  if (!pool) return res.status(503).json({ success: false, message: 'پایگاه داده در دسترس نیست.' });
  const connection = await pool.getConnection();
  try {
    const userId = req.user.id;
    const { reward_id, recipient_name, phone, shipping_address, idempotency_key } = req.body;
    
    if (!userId || !reward_id) return res.status(400).json({ success: false, message: 'اطلاعات ناقص است.' });

    await connection.beginTransaction();

    // 1. Fetch Reward with lock
    const [rewards] = await connection.query('SELECT * FROM rewards WHERE id = ? FOR UPDATE', [reward_id]);
    if (rewards.length === 0) {
      await connection.rollback();
      return res.status(404).json({ success: false, message: 'جایزه یافت نشد.' });
    }
    const reward = rewards[0];

    if (!reward.is_active || reward.deleted_at) {
      await connection.rollback();
      return res.status(400).json({ success: false, message: 'این جایزه غیرفعال است.' });
    }

    if (reward.stock_quantity <= 0) {
      await connection.rollback();
      return res.status(400).json({ success: false, message: 'موجودی این جایزه به پایان رسیده است.' });
    }

    // 2. Check User Score
    const [scores] = await connection.query('SELECT total_score FROM user_scores WHERE user_id = ? FOR UPDATE', [userId]);
    const currentScore = scores.length > 0 ? Number(scores[0].total_score) : 0;

    if (currentScore < reward.cost_points) {
      await connection.rollback();
      return res.status(400).json({ success: false, message: `امتیاز شما کافی نیست. (نیاز: ${reward.cost_points}، موجودی: ${currentScore})` });
    }

    // 3. Create Claim
    const claimId = 'clm_' + crypto.randomBytes(8).toString('hex');
    const trackingCode = 'CLM-' + crypto.randomBytes(4).toString('hex').toUpperCase();

    // Deduct Score
    await connection.query('UPDATE user_scores SET total_score = total_score - ?, updated_at = NOW() WHERE user_id = ?', [reward.cost_points, userId]);
    await connection.query('UPDATE users SET total_score = total_score - ?, updated_at = NOW() WHERE id = ?', [reward.cost_points, userId]);

    // Deduct Stock
    await connection.query('UPDATE rewards SET stock_quantity = stock_quantity - 1, updated_at = NOW() WHERE id = ?', [reward_id]);

    // Insert Claim record
    await connection.query(`
      INSERT INTO reward_claims 
      (id, tracking_code, user_id, reward_id, cost_points, status, recipient_name, phone, shipping_address, created_at, updated_at)
      VALUES (?, ?, ?, ?, ?, 'PENDING', ?, ?, ?, NOW(), NOW())
    `, [claimId, trackingCode, userId, reward_id, reward.cost_points, recipient_name || '', phone || '', shipping_address || '']);

    await connection.commit();
    return res.json({ success: true, message: 'درخواست با موفقیت ثبت شد.', claim_id: claimId, tracking_code: trackingCode });
  } catch (err) {
    await connection.rollback();
    console.error('[Rewards Claim Error]', err);
    return res.status(500).json({ success: false, message: 'خطا در فرآیند ثبت درخواست جایزه' });
  } finally {
    connection.release();
  }
});

rewardsRouter.get('/claims/user/:userId', requireUserAuth, async (req, res) => {
  try {
    const { userId } = req.params;
    const [rows] = await pool.query('SELECT c.*, r.title as reward_title FROM reward_claims c LEFT JOIN rewards r ON c.reward_id = r.id WHERE c.user_id = ? ORDER BY c.created_at DESC', [userId]);
    return res.json({ success: true, claims: rows });
  } catch (err) {
    return res.status(500).json({ success: false, message: 'خطا در دریافت سوابق' });
  }
});

rewardsRouter.get('/admin/rewards', requireAdminAuth, async (req, res) => {
  try {
    const [rows] = await pool.query('SELECT r.*, c.name as category_name FROM rewards r LEFT JOIN reward_categories c ON r.category_id = c.id WHERE r.deleted_at IS NULL ORDER BY r.sort_order ASC');
    return res.json({ success: true, rewards: rows });
  } catch (err) {
    return res.status(500).json({ success: false, message: 'خطا در لیست جوایز' });
  }
});

rewardsRouter.post('/admin/rewards', requireAdminAuth, async (req, res) => {
  try {
    const { title, description, category_id, cost_points, stock_quantity, image_url, is_featured, is_active, sort_order } = req.body;
    const id = 'rew_' + Date.now();
    await pool.query(`
      INSERT INTO rewards (id, title, description, category_id, cost_points, stock_quantity, image_url, is_featured, is_active, sort_order, created_at, updated_at)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())
    `, [id, title, description || '', category_id || null, cost_points || 0, stock_quantity || 0, image_url || '', !!is_featured, is_active !== false, sort_order || 0]);
    
    const [rows] = await pool.query('SELECT * FROM rewards WHERE id = ?', [id]);
    return res.json({ success: true, reward: rows[0] });
  } catch (err) {
    return res.status(500).json({ success: false, message: 'خطا در ایجاد جایزه' });
  }
});

rewardsRouter.put('/admin/rewards/:id', requireAdminAuth, async (req, res) => {
  try {
    const { id } = req.params;
    const { title, description, category_id, cost_points, stock_quantity, image_url, is_featured, is_active, sort_order } = req.body;
    await pool.query(`
      UPDATE rewards
      SET title = COALESCE(?, title), description = COALESCE(?, description), category_id = COALESCE(?, category_id), 
          cost_points = COALESCE(?, cost_points), stock_quantity = COALESCE(?, stock_quantity), image_url = COALESCE(?, image_url), 
          is_featured = COALESCE(?, is_featured), is_active = COALESCE(?, is_active), sort_order = COALESCE(?, sort_order), updated_at = NOW()
      WHERE id = ?
    `, [title, description, category_id, cost_points, stock_quantity, image_url, is_featured, is_active, sort_order, id]);
    
    const [rows] = await pool.query('SELECT * FROM rewards WHERE id = ?', [id]);
    return res.json({ success: true, reward: rows[0] });
  } catch (err) {
    return res.status(500).json({ success: false, message: 'خطا در ویرایش جایزه' });
  }
});

rewardsRouter.delete('/admin/rewards/:id', requireAdminAuth, async (req, res) => {
  try {
    const { id } = req.params;
    await pool.query('UPDATE rewards SET deleted_at = NOW(), is_active = false WHERE id = ?', [id]);
    return res.json({ success: true });
  } catch (err) {
    return res.status(500).json({ success: false, message: 'خطا در حذف' });
  }
});

rewardsRouter.get('/admin/claims', requireAdminAuth, async (req, res) => {
  try {
    const [rows] = await pool.query('SELECT c.*, r.title as reward_title, u.first_name, u.last_name, u.phone_number as phone FROM reward_claims c LEFT JOIN rewards r ON c.reward_id = r.id LEFT JOIN users u ON c.user_id = u.id ORDER BY c.created_at DESC');
    return res.json({ success: true, claims: rows });
  } catch (err) {
    return res.status(500).json({ success: false, message: 'خطا در دریافت درخواست‌ها' });
  }
});

rewardsRouter.patch('/admin/claims/:id', requireAdminAuth, async (req, res) => {
  const connection = await pool.getConnection();
  try {
    const { id } = req.params;
    const { status, admin_notes, tracking_number } = req.body;

    await connection.beginTransaction();

    const [claims] = await connection.query('SELECT * FROM reward_claims WHERE id = ? FOR UPDATE', [id]);
    if (claims.length === 0) {
      await connection.rollback();
      return res.status(404).json({ success: false, message: 'درخواست یافت نشد.' });
    }
    const oldClaim = claims[0];

    const normalizedOldStatus = (oldClaim.status || '').toUpperCase();
    const normalizedNewStatus = (status || '').toUpperCase();

    if (normalizedNewStatus === 'REJECTED' && normalizedOldStatus !== 'REJECTED') {
      await connection.query('UPDATE user_scores SET total_score = total_score + ?, updated_at = NOW() WHERE user_id = ?', [oldClaim.cost_points, oldClaim.user_id]);
      await connection.query('UPDATE users SET total_score = total_score + ?, updated_at = NOW() WHERE id = ?', [oldClaim.cost_points, oldClaim.user_id]);
      await connection.query('UPDATE rewards SET stock_quantity = stock_quantity + 1, updated_at = NOW() WHERE id = ?', [oldClaim.reward_id]);
    } 

    await connection.query(`
      UPDATE reward_claims 
      SET status = COALESCE(?, status), 
          admin_notes = COALESCE(?, admin_notes), 
          tracking_number = COALESCE(?, tracking_number), 
          updated_at = NOW() 
      WHERE id = ?
    `, [status, admin_notes, tracking_number, id]);

    await connection.commit();
    const [updated] = await connection.query('SELECT * FROM reward_claims WHERE id = ?', [id]);
    return res.json({ success: true, message: 'بروزرسانی شد.', claim: updated[0] });
  } catch (err) {
    await connection.rollback();
    return res.status(500).json({ success: false, message: 'خطا در بروزرسانی.' });
  } finally {
    connection.release();
  }
});

const saveAppearanceHandler = async (req, res) => {
  if (!pool) return res.status(503).json({ success: false, message: 'پایگاه داده در دسترس نیست.' });
  try {
    const { hero_title, hero_subtitle, hero_banner_url, primary_color, active_sections, terms_text } = req.body;
    let activeSecStr = active_sections;
    if (active_sections && typeof active_sections !== 'string') activeSecStr = JSON.stringify(active_sections);

    await pool.query(`
      INSERT INTO reward_appearance (id, hero_title, hero_subtitle, hero_banner_url, primary_color, active_sections, terms_text, updated_at)
      VALUES ('default', ?, ?, ?, ?, ?, ?, NOW())
      ON DUPLICATE KEY UPDATE 
        hero_title = COALESCE(?, hero_title),
        hero_subtitle = COALESCE(?, hero_subtitle),
        hero_banner_url = COALESCE(?, hero_banner_url),
        primary_color = COALESCE(?, primary_color),
        active_sections = COALESCE(?, active_sections),
        terms_text = COALESCE(?, terms_text),
        updated_at = NOW()
    `, [
      hero_title, hero_subtitle, hero_banner_url, primary_color, activeSecStr, terms_text,
      hero_title, hero_subtitle, hero_banner_url, primary_color, activeSecStr, terms_text
    ]);

    const [rows] = await pool.query('SELECT * FROM reward_appearance WHERE id = ?', ['default']);
    return res.json({ success: true, appearance: rows[0] });
  } catch (err) {
    return res.status(500).json({ success: false, message: 'خطا در ذخیره تنظیمات: ' + err.message });
  }
};

rewardsRouter.post('/admin/appearance', requireAdminAuth, saveAppearanceHandler);

function setPool(newPool) {
  pool = newPool;
}

module.exports = { rewardsRouter, setPool };
