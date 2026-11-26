import { generateSecureHex, jsonResponse, requireUserAuth, verifyAdminAuth } from './security.js';
import { readJsonBody } from './helpers.js';
import { handleImageUploadToStorage } from './routes_support_banners.js';

function parseRewardAppearanceRow(row) {
  let activeSections = row.active_sections;
  if (typeof activeSections === 'string') {
    try { activeSections = JSON.parse(activeSections); } catch { activeSections = { isActive: true }; }
  }
  return {
    ...row,
    hero_banner_url: row.hero_banner_url || '',
    active_sections: activeSections
  };
}

function normalizeRewardRow(row) {
  return {
    ...row,
    is_featured: Boolean(row.is_featured),
    is_active: Boolean(row.is_active),
    cost_points: Number(row.cost_points || 0),
    stock_quantity: Number(row.stock_quantity || 0),
    sort_order: Number(row.sort_order || 0)
  };
}

export async function handleRewardsRoutes(request, env, url, pathname) {
  let subPath = null;
  if (pathname.startsWith('/api/rewards')) {
    subPath = pathname.slice('/api/rewards'.length) || '/';
  } else if (pathname.startsWith('/api/prizes')) {
    subPath = pathname.slice('/api/prizes'.length) || '/';
  } else {
    return null;
  }

  const method = request.method.toUpperCase();

  // 1. GET /categories
  if (method === 'GET' && subPath === '/categories') {
    const { results } = await env.DB.prepare(
      `SELECT * FROM reward_categories WHERE is_active = 1 ORDER BY sort_order ASC`
    ).all();
    return jsonResponse({
      success: true,
      categories: (results || []).map((c) => ({ ...c, is_active: Boolean(c.is_active), sort_order: Number(c.sort_order || 0) }))
    });
  }

  // 2. GET /appearance
  if (method === 'GET' && subPath === '/appearance') {
    const row = await env.DB.prepare(`SELECT * FROM reward_appearance WHERE id = 'default' LIMIT 1`).first();
    if (!row) return jsonResponse({ success: false, message: 'تنظیمات ظاهری در پایگاه داده یافت نشد.' }, 404);
    return jsonResponse({ success: true, appearance: parseRewardAppearanceRow(row) });
  }

  // 3. GET / (Public Active Rewards Catalog)
  if (method === 'GET' && subPath === '/') {
    const category = url.searchParams.get('category');
    const featured = url.searchParams.get('featured');
    const search = url.searchParams.get('search');

    let sql = `
      SELECT r.*, c.name AS category_name, c.slug AS category_slug
      FROM rewards r
      LEFT JOIN reward_categories c ON r.category_id = c.id
      WHERE r.is_active = 1 AND r.deleted_at IS NULL
    `;
    const params = [];

    if (category) {
      params.push(category);
      sql += ` AND (r.category_id = ?${params.length} OR c.slug = ?${params.length})`;
    }
    if (featured === 'true' || featured === '1') {
      sql += ` AND r.is_featured = 1`;
    }
    if (search && search.trim()) {
      params.push(`%${search.trim().toLowerCase()}%`);
      const pIdx = `?${params.length}`;
      sql += ` AND (LOWER(r.title) LIKE ${pIdx} OR LOWER(r.description) LIKE ${pIdx})`;
    }
    sql += ` ORDER BY r.sort_order ASC, r.created_at DESC`;

    const { results } = await env.DB.prepare(sql).bind(...params).all();
    return jsonResponse({ success: true, rewards: (results || []).map(normalizeRewardRow) });
  }

  // 4. POST /claim
  if (method === 'POST' && subPath === '/claim') {
    const auth = await requireUserAuth(request, env, url);
    if (!auth.ok) return jsonResponse({ success: false, message: auth.error }, auth.status);

    const body = await readJsonBody(request);
    if (!body) return jsonResponse({ success: false, message: 'فرمت JSON نامعتبر است.' }, 400);

    const userId = auth.user.id;
    const { reward_id, recipient_name, phone, shipping_address, idempotency_key } = body;
    if (!reward_id) return jsonResponse({ success: false, message: 'اطلاعات ناقص است.' }, 400);

    const reward = await env.DB.prepare(`SELECT * FROM rewards WHERE id = ?1 LIMIT 1`).bind(reward_id).first();
    if (!reward) return jsonResponse({ success: false, message: 'جایزه یافت نشد.' }, 404);
    if (!reward.is_active || reward.deleted_at) {
      return jsonResponse({ success: false, message: 'این جایزه در حال حاضر غیرفعال است.' }, 400);
    }
    if (Number(reward.stock_quantity || 0) <= 0) {
      return jsonResponse({ success: false, message: 'موجودی این جایزه به پایان رسیده است.' }, 400);
    }

    const costPoints = Number(reward.cost_points || 0);
    const scoreRow = await env.DB.prepare(`SELECT total_score FROM user_scores WHERE user_id = ?1 LIMIT 1`).bind(userId).first();
    const currentScore = scoreRow ? Number(scoreRow.total_score || 0) : 0;
    if (currentScore < costPoints) {
      return jsonResponse({
        success: false,
        message: `امتیاز شما کافی نیست. (نیاز: ${costPoints}، موجودی: ${currentScore})`
      }, 400);
    }

    if (idempotency_key) {
      const idempCheck = await env.DB.prepare(
        `SELECT id FROM reward_claims WHERE idempotency_key = ?1 LIMIT 1`
      ).bind(String(idempotency_key)).first();
      if (idempCheck) {
        return jsonResponse({ success: false, message: 'این درخواست قبلاً ثبت شده است.' }, 409);
      }
    }

    const claimId = 'clm_' + generateSecureHex(8);
    const trackingCode = 'CLM-' + generateSecureHex(4).toUpperCase();
    const nowIso = new Date().toISOString();

    // Atomic batch protected by CHECK (total_score >= 0), CHECK (stock_quantity >= 0), and UNIQUE(idempotency_key)
    try {
      await env.DB.batch([
        env.DB.prepare(
          `UPDATE user_scores SET total_score = total_score - ?1, updated_at = ?2 WHERE user_id = ?3`
        ).bind(costPoints, nowIso, userId),
        env.DB.prepare(
          `UPDATE users SET total_score = CASE WHEN total_score >= ?1 THEN total_score - ?1 ELSE 0 END, updated_at = ?2 WHERE id = ?3`
        ).bind(costPoints, nowIso, userId),
        env.DB.prepare(
          `UPDATE rewards SET stock_quantity = stock_quantity - 1, updated_at = ?1 WHERE id = ?2`
        ).bind(nowIso, reward_id),
        env.DB.prepare(
          `INSERT INTO reward_claims
             (id, tracking_code, user_id, reward_id, cost_points, status, recipient_name, phone, shipping_address, idempotency_key, created_at, updated_at)
           VALUES (?1, ?2, ?3, ?4, ?5, 'PENDING', ?6, ?7, ?8, ?9, ?10, ?10)`
        ).bind(
          claimId,
          trackingCode,
          userId,
          reward_id,
          costPoints,
          recipient_name || '',
          phone || '',
          shipping_address || '',
          idempotency_key ? String(idempotency_key) : null,
          nowIso
        )
      ]);
    } catch (err) {
      const msg = String(err?.message || '');
      if (msg.includes('UNIQUE')) {
        return jsonResponse({ success: false, message: 'این درخواست قبلاً ثبت شده است.' }, 409);
      }
      if (msg.includes('CHECK')) {
        return jsonResponse({ success: false, message: 'امتیاز یا موجودی جایزه برای ثبت درخواست کافی نیست.' }, 400);
      }
      throw err;
    }

    const claim = await env.DB.prepare(`SELECT * FROM reward_claims WHERE id = ?1 LIMIT 1`).bind(claimId).first();
    return jsonResponse({
      success: true,
      message: 'درخواست شما با موفقیت ثبت شد و پس از بررسی نهایی پردازش خواهد شد.',
      claim,
      tracking_code: trackingCode
    });
  }

  // 5. GET /claims/user/:userId
  const userClaimsMatch = subPath.match(/^\/claims\/user\/([^/]+)$/);
  if (method === 'GET' && userClaimsMatch) {
    const auth = await requireUserAuth(request, env, url);
    if (!auth.ok) return jsonResponse({ success: false, message: auth.error }, auth.status);

    const requestedUserId = decodeURIComponent(userClaimsMatch[1]);
    if (requestedUserId !== auth.user.id) {
      return jsonResponse({ success: false, message: 'دسترسی به سوابق جوایز کاربر دیگر مجاز نیست.' }, 403);
    }

    const { results } = await env.DB.prepare(
      `SELECT c.*, r.title AS reward_title, r.image_url AS reward_image
       FROM reward_claims c
       LEFT JOIN rewards r ON c.reward_id = r.id
       WHERE c.user_id = ?1
       ORDER BY c.created_at DESC`
    ).bind(auth.user.id).all();
    return jsonResponse({ success: true, claims: results || [] });
  }

  // 6. Admin Rewards CRUD
  if (method === 'GET' && subPath === '/admin/rewards') {
    const auth = verifyAdminAuth(request, env, url);
    if (!auth.ok) return jsonResponse({ success: false, message: auth.message }, auth.status);

    const { results } = await env.DB.prepare(
      `SELECT r.*, c.name AS category_name
       FROM rewards r LEFT JOIN reward_categories c ON r.category_id = c.id
       WHERE r.deleted_at IS NULL ORDER BY r.sort_order ASC`
    ).all();
    return jsonResponse({ success: true, rewards: (results || []).map(normalizeRewardRow) });
  }

  if (method === 'POST' && subPath === '/admin/rewards') {
    const auth = verifyAdminAuth(request, env, url);
    if (!auth.ok) return jsonResponse({ success: false, message: auth.message }, auth.status);

    const body = await readJsonBody(request);
    if (!body || !body.title) return jsonResponse({ success: false, message: 'عنوان جایزه الزامی است.' }, 400);

    const id = 'rew_' + Date.now() + '_' + generateSecureHex(3);
    const nowIso = new Date().toISOString();
    await env.DB.prepare(
      `INSERT INTO rewards (id, title, description, category_id, cost_points, stock_quantity, image_url, is_featured, is_active, sort_order, created_at, updated_at)
       VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11, ?11)`
    ).bind(
      id,
      String(body.title),
      String(body.description || ''),
      body.category_id || null,
      Number(body.cost_points || 0),
      Number(body.stock_quantity || 0),
      String(body.image_url || ''),
      body.is_featured ? 1 : 0,
      body.is_active !== false ? 1 : 0,
      Number(body.sort_order || 0),
      nowIso
    ).run();

    const saved = await env.DB.prepare(`SELECT * FROM rewards WHERE id = ?1 LIMIT 1`).bind(id).first();
    return jsonResponse({ success: true, reward: normalizeRewardRow(saved) });
  }

  const adminRewardIdMatch = subPath.match(/^\/admin\/rewards\/([^/]+)$/);
  if (method === 'PUT' && adminRewardIdMatch) {
    const auth = verifyAdminAuth(request, env, url);
    if (!auth.ok) return jsonResponse({ success: false, message: auth.message }, auth.status);

    const id = decodeURIComponent(adminRewardIdMatch[1]);
    const existing = await env.DB.prepare(`SELECT * FROM rewards WHERE id = ?1 LIMIT 1`).bind(id).first();
    if (!existing) return jsonResponse({ success: false, message: 'جایزه یافت نشد.' }, 404);

    const body = await readJsonBody(request) || {};
    const nowIso = new Date().toISOString();

    await env.DB.prepare(
      `UPDATE rewards
       SET title = ?1, description = ?2, category_id = ?3, cost_points = ?4, stock_quantity = ?5,
           image_url = ?6, is_featured = ?7, is_active = ?8, sort_order = ?9, updated_at = ?10
       WHERE id = ?11`
    ).bind(
      body.title !== undefined ? body.title : existing.title,
      body.description !== undefined ? body.description : existing.description,
      body.category_id !== undefined ? body.category_id : existing.category_id,
      body.cost_points !== undefined ? Number(body.cost_points) : existing.cost_points,
      body.stock_quantity !== undefined ? Number(body.stock_quantity) : existing.stock_quantity,
      body.image_url !== undefined ? body.image_url : existing.image_url,
      body.is_featured !== undefined ? (body.is_featured ? 1 : 0) : existing.is_featured,
      body.is_active !== undefined ? (body.is_active ? 1 : 0) : existing.is_active,
      body.sort_order !== undefined ? Number(body.sort_order) : existing.sort_order,
      nowIso,
      id
    ).run();

    const saved = await env.DB.prepare(`SELECT * FROM rewards WHERE id = ?1 LIMIT 1`).bind(id).first();
    return jsonResponse({ success: true, reward: normalizeRewardRow(saved) });
  }

  if (method === 'DELETE' && adminRewardIdMatch) {
    const auth = verifyAdminAuth(request, env, url);
    if (!auth.ok) return jsonResponse({ success: false, message: auth.message }, auth.status);

    const id = decodeURIComponent(adminRewardIdMatch[1]);
    await env.DB.prepare(
      `UPDATE rewards SET deleted_at = ?1, is_active = 0 WHERE id = ?2`
    ).bind(new Date().toISOString(), id).run();
    return jsonResponse({ success: true });
  }

  // 7. Admin Claims & Atomic Status Transition / Refund
  if (method === 'GET' && subPath === '/admin/claims') {
    const auth = verifyAdminAuth(request, env, url);
    if (!auth.ok) return jsonResponse({ success: false, message: auth.message }, auth.status);

    const rawLimit = Number(url.searchParams.get('limit') || 100);
    const limit = Number.isFinite(rawLimit) ? Math.min(Math.max(Math.floor(rawLimit), 1), 200) : 100;
    const rawPage = Number(url.searchParams.get('page') || 1);
    const page = Number.isFinite(rawPage) && rawPage >= 1 ? Math.floor(rawPage) : 1;
    const rawOffset = url.searchParams.get('offset');
    const offset = rawOffset !== null && Number.isFinite(Number(rawOffset)) && Number(rawOffset) >= 0
      ? Math.floor(Number(rawOffset))
      : (page - 1) * limit;
    const statusFilter = String(url.searchParams.get('status') || '').trim().toUpperCase();

    let whereSql = '';
    const filterParams = [];
    if (statusFilter && statusFilter !== 'ALL') {
      filterParams.push(statusFilter);
      whereSql = `WHERE UPPER(c.status) = ?1`;
    }

    const limitParamIdx = filterParams.length + 1;
    const offsetParamIdx = filterParams.length + 2;

    const [{ results }, countRow, pendingRow] = await Promise.all([
      env.DB.prepare(
        `SELECT c.*, r.title AS reward_title, u.first_name, u.last_name, u.phone_number AS phone
         FROM reward_claims c
         LEFT JOIN rewards r ON c.reward_id = r.id
         LEFT JOIN users u ON c.user_id = u.id
         ${whereSql}
         ORDER BY c.created_at DESC
         LIMIT ?${limitParamIdx} OFFSET ?${offsetParamIdx}`
      ).bind(...filterParams, limit, offset).all(),
      env.DB.prepare(
        `SELECT COUNT(*) AS total FROM reward_claims c ${whereSql}`
      ).bind(...filterParams).first(),
      env.DB.prepare(
        `SELECT COUNT(*) AS pending_count FROM reward_claims WHERE UPPER(status) = 'PENDING'`
      ).first()
    ]);

    const claims = results || [];
    const total = Number(countRow?.total || 0);
    const pendingCount = Number(pendingRow?.pending_count || 0);

    return jsonResponse({
      success: true,
      claims,
      pendingCount,
      pagination: {
        total,
        limit,
        offset,
        page: Math.floor(offset / limit) + 1,
        totalPages: Math.max(1, Math.ceil(total / limit)),
        hasMore: offset + claims.length < total
      }
    });
  }

  const adminClaimMatch = subPath.match(/^\/admin\/claims\/([^/]+)$/);
  if (method === 'PATCH' && adminClaimMatch) {
    const auth = verifyAdminAuth(request, env, url);
    if (!auth.ok) return jsonResponse({ success: false, message: auth.message }, auth.status);

    const id = decodeURIComponent(adminClaimMatch[1]);
    const body = await readJsonBody(request) || {};
    const { status, admin_notes, tracking_number } = body;

    const oldClaim = await env.DB.prepare(`SELECT * FROM reward_claims WHERE id = ?1 LIMIT 1`).bind(id).first();
    if (!oldClaim) return jsonResponse({ success: false, message: 'درخواست یافت نشد.' }, 404);

    const normalizedOldStatus = String(oldClaim.status || '').toUpperCase();
    const normalizedNewStatus = String(status || '').toUpperCase();
    const nowIso = new Date().toISOString();
    const batch = [];

    if (normalizedNewStatus === 'REJECTED' && normalizedOldStatus !== 'REJECTED') {
      // Refund guarded by oldClaim.status != 'REJECTED' in WHERE clause
      const batchRes = await env.DB.batch([
        env.DB.prepare(
          `UPDATE reward_claims
           SET status = ?1, admin_notes = ?2, tracking_number = ?3, updated_at = ?4
           WHERE id = ?5 AND status != 'REJECTED'`
        ).bind(
          status || oldClaim.status,
          admin_notes !== undefined ? admin_notes : oldClaim.admin_notes,
          tracking_number !== undefined ? tracking_number : oldClaim.tracking_number,
          nowIso,
          id
        ),
        env.DB.prepare(`UPDATE user_scores SET total_score = total_score + ?1, updated_at = ?2 WHERE user_id = ?3`)
          .bind(oldClaim.cost_points, nowIso, oldClaim.user_id),
        env.DB.prepare(`UPDATE users SET total_score = total_score + ?1, updated_at = ?2 WHERE id = ?3`)
          .bind(oldClaim.cost_points, nowIso, oldClaim.user_id),
        env.DB.prepare(`UPDATE rewards SET stock_quantity = stock_quantity + 1, updated_at = ?1 WHERE id = ?2`)
          .bind(nowIso, oldClaim.reward_id)
      ]);
      if (Number(batchRes?.[0]?.meta?.changes || 0) === 0) {
        return jsonResponse({ success: false, message: 'این درخواست قبلاً رد شده یا امتیاز آن بازگردانده شده است.' }, 409);
      }
    } else if (normalizedOldStatus === 'REJECTED' && status && normalizedNewStatus !== 'REJECTED') {
      const scoreRow = await env.DB.prepare(`SELECT total_score FROM user_scores WHERE user_id = ?1 LIMIT 1`).bind(oldClaim.user_id).first();
      const currentScore = scoreRow ? Number(scoreRow.total_score || 0) : 0;
      if (currentScore < Number(oldClaim.cost_points || 0)) {
        return jsonResponse({ success: false, message: 'امتیاز کاربر برای بازگرداندن از حالت رد شده کافی نیست.' }, 400);
      }
      try {
        await env.DB.batch([
          env.DB.prepare(`UPDATE user_scores SET total_score = total_score - ?1, updated_at = ?2 WHERE user_id = ?3`)
            .bind(oldClaim.cost_points, nowIso, oldClaim.user_id),
          env.DB.prepare(`UPDATE users SET total_score = CASE WHEN total_score >= ?1 THEN total_score - ?1 ELSE 0 END, updated_at = ?2 WHERE id = ?3`)
            .bind(oldClaim.cost_points, nowIso, oldClaim.user_id),
          env.DB.prepare(`UPDATE rewards SET stock_quantity = stock_quantity - 1, updated_at = ?1 WHERE id = ?2`)
            .bind(nowIso, oldClaim.reward_id),
          env.DB.prepare(
            `UPDATE reward_claims SET status = ?1, admin_notes = ?2, tracking_number = ?3, updated_at = ?4 WHERE id = ?5`
          ).bind(
            status,
            admin_notes !== undefined ? admin_notes : oldClaim.admin_notes,
            tracking_number !== undefined ? tracking_number : oldClaim.tracking_number,
            nowIso,
            id
          )
        ]);
      } catch {
        return jsonResponse({ success: false, message: 'موجودی امتیاز یا کالا برای تغییر وضعیت کافی نیست.' }, 400);
      }
    } else {
      await env.DB.prepare(
        `UPDATE reward_claims SET status = ?1, admin_notes = ?2, tracking_number = ?3, updated_at = ?4 WHERE id = ?5`
      ).bind(
        status || oldClaim.status,
        admin_notes !== undefined ? admin_notes : oldClaim.admin_notes,
        tracking_number !== undefined ? tracking_number : oldClaim.tracking_number,
        nowIso,
        id
      ).run();
    }

    const updatedClaim = await env.DB.prepare(`SELECT * FROM reward_claims WHERE id = ?1 LIMIT 1`).bind(id).first();
    return jsonResponse({ success: true, message: 'درخواست با موفقیت بروزرسانی شد.', claim: updatedClaim });
  }

  // 8. Admin Appearance
  if (method === 'POST' && (subPath === '/admin/appearance' || subPath === '/appearance')) {
    const auth = verifyAdminAuth(request, env, url);
    if (!auth.ok) return jsonResponse({ success: false, message: auth.message }, auth.status);

    const body = await readJsonBody(request) || {};
    const { hero_title, hero_subtitle, hero_banner_url, primary_color, active_sections, is_active, terms_text } = body;

    let activeSecObj = active_sections;
    if (!activeSecObj) {
      activeSecObj = { isActive: is_active !== false };
    } else if (typeof activeSecObj === 'string') {
      try { activeSecObj = JSON.parse(activeSecObj); } catch { activeSecObj = { isActive: is_active !== false }; }
    }
    const activeSecStr = JSON.stringify(activeSecObj);
    const nowIso = new Date().toISOString();

    await env.DB.prepare(
      `INSERT INTO reward_appearance (id, hero_title, hero_subtitle, hero_banner_url, primary_color, active_sections, terms_text, updated_at)
       VALUES ('default', ?1, ?2, ?3, ?4, ?5, ?6, ?7)
       ON CONFLICT(id) DO UPDATE SET
         hero_title = ?1, hero_subtitle = ?2, hero_banner_url = ?3,
         primary_color = ?4, active_sections = ?5, terms_text = ?6, updated_at = ?7`
    ).bind(
      hero_title || 'ویترین جوایز و پاداش‌های ارزشمند',
      hero_subtitle || '',
      hero_banner_url || '',
      primary_color || '#10B981',
      activeSecStr,
      terms_text || '',
      nowIso
    ).run();

    const saved = await env.DB.prepare(`SELECT * FROM reward_appearance WHERE id = 'default' LIMIT 1`).first();
    return jsonResponse({
      success: true,
      appearance: saved ? parseRewardAppearanceRow(saved) : { hero_title, hero_subtitle, hero_banner_url, primary_color }
    });
  }

  // 9. Admin Upload Gift Banner
  if (method === 'POST' && subPath === '/admin/upload-banner') {
    const auth = verifyAdminAuth(request, env, url);
    if (!auth.ok) return jsonResponse({ success: false, message: auth.message }, auth.status);
    return handleImageUploadToStorage(request, env, 'banners', ['banner', 'image']);
  }

  return null;
}
