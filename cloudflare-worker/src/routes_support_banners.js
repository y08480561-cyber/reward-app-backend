import {
  detectImageFormatFromBytes,
  generateSecureHex,
  jsonResponse,
  normalizeLinkOpenMode,
  normalizePlacements,
  parseBannerPlacements,
  parseOptionalBannerDate,
  requireUserAuth,
  validateBannerImageUrl,
  validateBannerTargetUrl,
  verifyAdminAuth
} from './security.js';
import {
  enrichAdminBannerRow,
  generateUniqueTrackingCode,
  readJsonBody,
  serializePublicBannerRow,
  toMs
} from './helpers.js';

const VALID_SUPPORT_CATEGORIES = ['SCORES', 'ADS', 'PRIZES', 'MEMBERSHIP', 'PURCHASE', 'LOGIN', 'OTHER'];
const VALID_SUPPORT_STATUSES = ['NEW', 'IN_PROGRESS', 'ANSWERED', 'CLOSED'];
const MAX_UPLOAD_BYTES = 5 * 1024 * 1024;

export async function handleImageUploadToStorage(request, env, subFolder = 'ad-banners', fieldNames = ['image', 'banner']) {
  let formData;
  try {
    formData = await request.formData();
  } catch {
    return jsonResponse({ success: false, message: 'لطفاً فایل تصویر را به‌صورت multipart/form-data ارسال نمایید.' }, 400);
  }

  let fileEntry = null;
  for (const name of fieldNames) {
    const candidate = formData.get(name);
    if (candidate && typeof candidate === 'object' && typeof candidate.arrayBuffer === 'function') {
      fileEntry = candidate;
      break;
    }
  }
  if (!fileEntry) {
    return jsonResponse({ success: false, message: 'فایل تصویر ارسال نشده است.' }, 400);
  }

  const buffer = await fileEntry.arrayBuffer();
  if (buffer.byteLength > MAX_UPLOAD_BYTES) {
    return jsonResponse({ success: false, message: 'حجم تصویر نباید بیشتر از ۵ مگابایت باشد.' }, 400);
  }

  const bytes = new Uint8Array(buffer);
  const detected = detectImageFormatFromBytes(bytes);
  if (!detected) {
    return jsonResponse({
      success: false,
      message: 'محتوای فایل تصویر معتبر نیست. لطفاً یک تصویر استاندارد JPG، PNG یا WebP انتخاب کنید.'
    }, 400);
  }

  const fileName = `${subFolder === 'banners' ? 'banner' : 'ad_banner'}_${Date.now()}_${generateSecureHex(6)}.${detected.ext}`;
  const objectKey = `${subFolder}/${fileName}`;

  // 1. Try Cloudflare R2 if BANNERS_BUCKET binding is present
  if (env.BANNERS_BUCKET) {
    try {
      await env.BANNERS_BUCKET.put(objectKey, bytes, {
        httpMetadata: { contentType: detected.mime }
      });
      return jsonResponse({
        success: true,
        url: `/uploads/${objectKey}`,
        storage: 'r2',
        message: 'تصویر با موفقیت در فضای ابری Cloudflare R2 ذخیره شد.'
      });
    } catch (err) {
      console.warn('[R2 Upload Warning]', err?.message || err);
    }
  }

  // 2. Try Supabase Storage REST API if configured
  const supabaseUrl = String(env.SUPABASE_URL || '').trim().replace(/\/+$/, '');
  const serviceRoleKey = String(env.SUPABASE_SERVICE_ROLE_KEY || '').trim();
  if (supabaseUrl && serviceRoleKey) {
    const candidateBuckets = subFolder === 'banners'
      ? ['gift-banners', String(env.SUPABASE_AD_BANNERS_BUCKET || '').trim(), 'ad-banners']
      : [String(env.SUPABASE_AD_BANNERS_BUCKET || '').trim(), 'ad-banners', 'gift-banners'];

    for (const bucketName of candidateBuckets.filter(Boolean)) {
      try {
        const uploadUrl = `${supabaseUrl}/storage/v1/object/${bucketName}/${fileName}`;
        const uploadRes = await fetch(uploadUrl, {
          method: 'POST',
          headers: {
            'Authorization': `Bearer ${serviceRoleKey}`,
            'Content-Type': detected.mime,
            'x-upsert': 'true'
          },
          body: bytes
        });
        if (uploadRes.ok) {
          const publicUrl = `${supabaseUrl}/storage/v1/object/public/${bucketName}/${fileName}`;
          return jsonResponse({
            success: true,
            url: publicUrl,
            storage: 'supabase',
            message: 'تصویر با موفقیت در فضای ابری ذخیره شد.'
          });
        }
      } catch (err) {
        console.warn(`[Supabase Upload Warning] bucket=${bucketName}:`, err?.message || err);
      }
    }
  }

  return jsonResponse({
    success: false,
    message: 'فضای ذخیره‌سازی ابری (Cloudflare R2 یا Supabase Storage) روی Worker پیکربندی نشده است.'
  }, 501);
}

export async function handleSupportAndBannerRoutes(request, env, url, pathname) {
  const method = request.method.toUpperCase();

  // ====================================================================
  // Support Ticketing (User)
  // ====================================================================
  if (method === 'POST' && pathname === '/api/support/tickets') {
    const auth = await requireUserAuth(request, env, url);
    if (!auth.ok) return jsonResponse({ success: false, error: auth.error }, auth.status);

    const body = await readJsonBody(request);
    if (!body) return jsonResponse({ error: 'فرمت JSON نامعتبر است.' }, 400);

    const { subject, category, message } = body;
    if (!subject || typeof subject !== 'string' || subject.trim().length < 3 || subject.trim().length > 150) {
      return jsonResponse({ error: 'عنوان درخواست باید بین ۳ تا ۱۵۰ کاراکتر باشد.' }, 400);
    }
    if (!message || typeof message !== 'string' || message.trim().length < 5 || message.trim().length > 3000) {
      return jsonResponse({ error: 'متن پیام پشتیبانی باید بین ۵ تا ۳۰۰۰ کاراکتر باشد.' }, 400);
    }

    const cleanCategory = category && VALID_SUPPORT_CATEGORIES.includes(String(category).toUpperCase())
      ? String(category).toUpperCase()
      : 'OTHER';

    const ticketId = 'TCK_' + crypto.randomUUID();
    const trackingCode = await generateUniqueTrackingCode(env.DB);
    const messageId = 'MSG_' + crypto.randomUUID();
    const nowMs = Date.now();
    const nowIso = new Date(nowMs).toISOString();

    await env.DB.batch([
      env.DB.prepare(
        `INSERT INTO support_tickets (id, tracking_code, user_id, subject, category, status, created_at, updated_at, last_message_at)
         VALUES (?1, ?2, ?3, ?4, ?5, 'NEW', ?6, ?6, ?6)`
      ).bind(ticketId, trackingCode, auth.user.id, subject.trim(), cleanCategory, nowIso),
      env.DB.prepare(
        `INSERT INTO support_messages (id, ticket_id, sender_type, sender_id, sender_name, message_text, created_at)
         VALUES (?1, ?2, 'USER', ?3, ?4, ?5, ?6)`
      ).bind(messageId, ticketId, auth.user.id, auth.user.fullName, message.trim(), nowIso)
    ]);

    return jsonResponse({
      success: true,
      message: 'درخواست پشتیبانی شما با موفقیت ثبت شد.',
      ticket: {
        id: ticketId,
        trackingCode,
        subject: subject.trim(),
        category: cleanCategory,
        status: 'NEW',
        createdAt: nowMs,
        updatedAt: nowMs,
        lastMessage: message.trim(),
        lastSenderType: 'USER'
      }
    }, 201);
  }

  if (method === 'GET' && pathname === '/api/support/tickets') {
    const auth = await requireUserAuth(request, env, url);
    if (!auth.ok) return jsonResponse({ success: false, error: auth.error }, auth.status);

    const { results } = await env.DB.prepare(
      `SELECT t.id, t.tracking_code, t.subject, t.category, t.status, t.created_at, t.updated_at, t.last_message_at,
              (SELECT m.message_text FROM support_messages m WHERE m.ticket_id = t.id ORDER BY m.created_at DESC LIMIT 1) AS last_message,
              (SELECT m.sender_type FROM support_messages m WHERE m.ticket_id = t.id ORDER BY m.created_at DESC LIMIT 1) AS last_sender_type
       FROM support_tickets t
       WHERE t.user_id = ?1
       ORDER BY t.updated_at DESC`
    ).bind(auth.user.id).all();

    const tickets = (results || []).map((row) => ({
      id: row.id,
      trackingCode: row.tracking_code,
      subject: row.subject,
      category: row.category,
      status: row.status,
      createdAtMs: toMs(row.created_at, Date.now()),
      updatedAtMs: toMs(row.updated_at, Date.now()),
      lastMessageAtMs: toMs(row.last_message_at, Date.now()),
      lastMessage: row.last_message || '',
      lastSenderType: row.last_sender_type || 'USER'
    }));

    return jsonResponse({ success: true, tickets });
  }

  const userTicketDetailMatch = pathname.match(/^\/api\/support\/tickets\/([^/]+)$/);
  if (method === 'GET' && userTicketDetailMatch) {
    const auth = await requireUserAuth(request, env, url);
    if (!auth.ok) return jsonResponse({ success: false, error: auth.error }, auth.status);

    const ticketId = decodeURIComponent(userTicketDetailMatch[1]);
    const t = await env.DB.prepare(
      `SELECT id, tracking_code, user_id, subject, category, status, created_at, updated_at, last_message_at
       FROM support_tickets WHERE id = ?1 AND user_id = ?2 LIMIT 1`
    ).bind(ticketId, auth.user.id).first();

    if (!t) {
      return jsonResponse({ error: 'درخواست پشتیبانی یافت نشد یا دسترسی به آن برای شما مجاز نمی‌باشد.' }, 404);
    }

    const { results: msgs } = await env.DB.prepare(
      `SELECT id, ticket_id, sender_type, sender_id, sender_name, message_text, created_at
       FROM support_messages WHERE ticket_id = ?1 ORDER BY created_at ASC`
    ).bind(ticketId).all();

    return jsonResponse({
      success: true,
      ticket: {
        id: t.id,
        trackingCode: t.tracking_code,
        subject: t.subject,
        category: t.category,
        status: t.status,
        createdAtMs: toMs(t.created_at, Date.now()),
        updatedAtMs: toMs(t.updated_at, Date.now()),
        lastMessageAtMs: toMs(t.last_message_at, Date.now())
      },
      messages: (msgs || []).map((m) => ({
        id: m.id,
        ticketId: m.ticket_id,
        senderType: m.sender_type,
        senderId: m.sender_id,
        senderName: m.sender_name,
        messageText: m.message_text,
        createdAtMs: toMs(m.created_at, Date.now())
      }))
    });
  }

  const userTicketMsgMatch = pathname.match(/^\/api\/support\/tickets\/([^/]+)\/messages$/);
  if (method === 'POST' && userTicketMsgMatch) {
    const auth = await requireUserAuth(request, env, url);
    if (!auth.ok) return jsonResponse({ success: false, error: auth.error }, auth.status);

    const ticketId = decodeURIComponent(userTicketMsgMatch[1]);
    const body = await readJsonBody(request);
    const message = body?.message;
    if (!message || typeof message !== 'string' || message.trim().length < 2 || message.trim().length > 3000) {
      return jsonResponse({ error: 'متن پیام باید بین ۲ تا ۳۰۰۰ کاراکتر باشد.' }, 400);
    }

    const currentTicket = await env.DB.prepare(
      `SELECT id, status, tracking_code FROM support_tickets WHERE id = ?1 AND user_id = ?2 LIMIT 1`
    ).bind(ticketId, auth.user.id).first();

    if (!currentTicket) {
      return jsonResponse({ error: 'درخواست پشتیبانی یافت نشد یا دسترسی به آن برای شما مجاز نمی‌باشد.' }, 404);
    }
    if (currentTicket.status === 'CLOSED') {
      return jsonResponse({
        error: 'این درخواست توسط مدیریت بسته شده است. در صورت نیاز به راهنمایی جدید، لطفاً یک درخواست جدید ثبت نمایید.'
      }, 400);
    }

    const messageId = 'MSG_' + crypto.randomUUID();
    const nowMs = Date.now();
    const nowIso = new Date(nowMs).toISOString();
    const newStatus = currentTicket.status === 'ANSWERED' ? 'IN_PROGRESS' : currentTicket.status;

    await env.DB.batch([
      env.DB.prepare(
        `INSERT INTO support_messages (id, ticket_id, sender_type, sender_id, sender_name, message_text, created_at)
         VALUES (?1, ?2, 'USER', ?3, ?4, ?5, ?6)`
      ).bind(messageId, ticketId, auth.user.id, auth.user.fullName, message.trim(), nowIso),
      env.DB.prepare(
        `UPDATE support_tickets SET status = ?1, updated_at = ?2, last_message_at = ?2 WHERE id = ?3`
      ).bind(newStatus, nowIso, ticketId)
    ]);

    return jsonResponse({
      success: true,
      message: {
        id: messageId,
        ticketId,
        senderType: 'USER',
        senderId: auth.user.id,
        senderName: auth.user.fullName,
        messageText: message.trim(),
        createdAtMs: nowMs
      },
      ticketStatus: newStatus
    }, 201);
  }

  // ====================================================================
  // Support Ticketing (Admin)
  // ====================================================================
  if (method === 'GET' && pathname === '/api/admin/support/tickets') {
    const auth = verifyAdminAuth(request, env, url);
    if (!auth.ok) return jsonResponse({ error: auth.message }, auth.status);

    const status = url.searchParams.get('status');
    const category = url.searchParams.get('category');
    const search = url.searchParams.get('search');

    let sql = `
      SELECT t.id, t.tracking_code, t.user_id, t.subject, t.category, t.status,
             t.created_at, t.updated_at, t.last_message_at,
             u.first_name, u.last_name, u.phone_number,
             (SELECT m.message_text FROM support_messages m WHERE m.ticket_id = t.id ORDER BY m.created_at DESC LIMIT 1) AS last_message,
             (SELECT m.sender_type FROM support_messages m WHERE m.ticket_id = t.id ORDER BY m.created_at DESC LIMIT 1) AS last_sender_type,
             (SELECT COUNT(*) FROM support_messages m WHERE m.ticket_id = t.id) AS total_messages
      FROM support_tickets t
      JOIN users u ON t.user_id = u.id
      WHERE 1=1
    `;
    const params = [];

    if (status && status !== 'ALL' && VALID_SUPPORT_STATUSES.includes(status.toUpperCase())) {
      params.push(status.toUpperCase());
      sql += ` AND t.status = ?${params.length}`;
    }
    if (category && category !== 'ALL' && VALID_SUPPORT_CATEGORIES.includes(category.toUpperCase())) {
      params.push(category.toUpperCase());
      sql += ` AND t.category = ?${params.length}`;
    }
    if (search && search.trim()) {
      params.push(`%${search.trim().toLowerCase()}%`);
      const pIdx = `?${params.length}`;
      sql += ` AND (LOWER(t.tracking_code) LIKE ${pIdx} OR LOWER(u.phone_number) LIKE ${pIdx} OR LOWER(t.subject) LIKE ${pIdx} OR LOWER(u.first_name) LIKE ${pIdx} OR LOWER(u.last_name) LIKE ${pIdx})`;
    }
    sql += ` ORDER BY t.updated_at DESC LIMIT 200`;

    const [{ results }, counts] = await Promise.all([
      env.DB.prepare(sql).bind(...params).all(),
      env.DB.prepare(
        `SELECT COUNT(*) AS total,
                SUM(CASE WHEN status = 'NEW' THEN 1 ELSE 0 END) AS new_count,
                SUM(CASE WHEN status = 'IN_PROGRESS' THEN 1 ELSE 0 END) AS in_progress_count,
                SUM(CASE WHEN status = 'ANSWERED' THEN 1 ELSE 0 END) AS answered_count,
                SUM(CASE WHEN status = 'CLOSED' THEN 1 ELSE 0 END) AS closed_count
         FROM support_tickets`
      ).first()
    ]);

    return jsonResponse({
      success: true,
      stats: {
        total: Number(counts?.total || 0),
        newCount: Number(counts?.new_count || 0),
        inProgressCount: Number(counts?.in_progress_count || 0),
        answeredCount: Number(counts?.answered_count || 0),
        closedCount: Number(counts?.closed_count || 0)
      },
      tickets: (results || []).map((row) => ({
        id: row.id,
        trackingCode: row.tracking_code,
        userId: row.user_id,
        userName: `${row.first_name || ''} ${row.last_name || ''}`.trim() || 'کاربر',
        phoneNumber: row.phone_number || '',
        subject: row.subject,
        category: row.category,
        status: row.status,
        createdAtMs: toMs(row.created_at, Date.now()),
        updatedAtMs: toMs(row.updated_at, Date.now()),
        lastMessageAtMs: toMs(row.last_message_at, Date.now()),
        lastMessage: row.last_message || '',
        lastSenderType: row.last_sender_type || 'USER',
        totalMessages: Number(row.total_messages || 0)
      }))
    });
  }

  const adminTicketDetailMatch = pathname.match(/^\/api\/admin\/support\/tickets\/([^/]+)$/);
  if (method === 'GET' && adminTicketDetailMatch) {
    const auth = verifyAdminAuth(request, env, url);
    if (!auth.ok) return jsonResponse({ error: auth.message }, auth.status);

    const ticketId = decodeURIComponent(adminTicketDetailMatch[1]);
    const t = await env.DB.prepare(
      `SELECT t.id, t.tracking_code, t.user_id, t.subject, t.category, t.status,
              t.created_at, t.updated_at, t.last_message_at,
              u.first_name, u.last_name, u.phone_number, u.total_score
       FROM support_tickets t JOIN users u ON t.user_id = u.id
       WHERE t.id = ?1 LIMIT 1`
    ).bind(ticketId).first();

    if (!t) return jsonResponse({ error: 'درخواست پشتیبانی یافت نشد.' }, 404);

    const { results: msgs } = await env.DB.prepare(
      `SELECT id, ticket_id, sender_type, sender_id, sender_name, message_text, created_at
       FROM support_messages WHERE ticket_id = ?1 ORDER BY created_at ASC`
    ).bind(ticketId).all();

    return jsonResponse({
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
        createdAtMs: toMs(t.created_at, Date.now()),
        updatedAtMs: toMs(t.updated_at, Date.now()),
        lastMessageAtMs: toMs(t.last_message_at, Date.now())
      },
      messages: (msgs || []).map((m) => ({
        id: m.id,
        ticketId: m.ticket_id,
        senderType: m.sender_type,
        senderId: m.sender_id,
        senderName: m.sender_name,
        messageText: m.message_text,
        createdAtMs: toMs(m.created_at, Date.now())
      }))
    });
  }

  const adminTicketReplyMatch = pathname.match(/^\/api\/admin\/support\/tickets\/([^/]+)\/messages$/);
  if (method === 'POST' && adminTicketReplyMatch) {
    const auth = verifyAdminAuth(request, env, url);
    if (!auth.ok) return jsonResponse({ error: auth.message }, auth.status);

    const ticketId = decodeURIComponent(adminTicketReplyMatch[1]);
    const body = await readJsonBody(request);
    const { message, newStatus } = body || {};

    if (!message || typeof message !== 'string' || message.trim().length < 2 || message.trim().length > 3000) {
      return jsonResponse({ error: 'متن پاسخ مدیر باید بین ۲ تا ۳۰۰۰ کاراکتر باشد.' }, 400);
    }

    const existing = await env.DB.prepare(`SELECT id FROM support_tickets WHERE id = ?1 LIMIT 1`).bind(ticketId).first();
    if (!existing) return jsonResponse({ error: 'درخواست پشتیبانی یافت نشد.' }, 404);

    const updatedStatus = newStatus && VALID_SUPPORT_STATUSES.includes(String(newStatus).toUpperCase())
      ? String(newStatus).toUpperCase()
      : 'ANSWERED';
    const messageId = 'MSG_' + crypto.randomUUID();
    const nowMs = Date.now();
    const nowIso = new Date(nowMs).toISOString();

    await env.DB.batch([
      env.DB.prepare(
        `INSERT INTO support_messages (id, ticket_id, sender_type, sender_id, sender_name, message_text, created_at)
         VALUES (?1, ?2, 'ADMIN', 'admin', 'پشتیبانی رقابت', ?3, ?4)`
      ).bind(messageId, ticketId, message.trim(), nowIso),
      env.DB.prepare(
        `UPDATE support_tickets SET status = ?1, updated_at = ?2, last_message_at = ?2 WHERE id = ?3`
      ).bind(updatedStatus, nowIso, ticketId)
    ]);

    return jsonResponse({
      success: true,
      message: {
        id: messageId,
        ticketId,
        senderType: 'ADMIN',
        senderId: 'admin',
        senderName: 'پشتیبانی رقابت',
        messageText: message.trim(),
        createdAtMs: nowMs
      },
      ticketStatus: updatedStatus
    }, 201);
  }

  const adminTicketStatusMatch = pathname.match(/^\/api\/admin\/support\/tickets\/([^/]+)\/status$/);
  if (method === 'PATCH' && adminTicketStatusMatch) {
    const auth = verifyAdminAuth(request, env, url);
    if (!auth.ok) return jsonResponse({ error: auth.message }, auth.status);

    const ticketId = decodeURIComponent(adminTicketStatusMatch[1]);
    const body = await readJsonBody(request);
    const status = body?.status;

    if (!status || !VALID_SUPPORT_STATUSES.includes(String(status).toUpperCase())) {
      return jsonResponse({ error: 'وضعیت انتخاب شده نامعتبر است (NEW, IN_PROGRESS, ANSWERED, CLOSED).' }, 400);
    }

    const cleanStatus = String(status).toUpperCase();
    const nowIso = new Date().toISOString();
    const res = await env.DB.prepare(
      `UPDATE support_tickets SET status = ?1, updated_at = ?2 WHERE id = ?3`
    ).bind(cleanStatus, nowIso, ticketId).run();

    if (Number(res?.meta?.changes ?? 0) === 0) {
      return jsonResponse({ error: 'درخواست پشتیبانی یافت نشد.' }, 404);
    }

    const updated = await env.DB.prepare(`SELECT id, tracking_code, status FROM support_tickets WHERE id = ?1 LIMIT 1`).bind(ticketId).first();
    return jsonResponse({
      success: true,
      message: `وضعیت درخواست با موفقیت به «${cleanStatus}» تغییر یافت.`,
      ticket: updated
    });
  }

  // ====================================================================
  // Ad Banners Management & Analytics
  // ====================================================================
  if (method === 'POST' && pathname === '/api/admin/ad-banners/upload-image') {
    const auth = verifyAdminAuth(request, env, url);
    if (!auth.ok) return jsonResponse({ success: false, message: auth.message }, auth.status);
    return handleImageUploadToStorage(request, env, 'ad-banners', ['image', 'banner']);
  }

  if (method === 'GET' && pathname === '/api/admin/ad-banners') {
    const auth = verifyAdminAuth(request, env, url);
    if (!auth.ok) return jsonResponse({ success: false, message: auth.message }, auth.status);

    const search = String(url.searchParams.get('search') || '').trim().toLowerCase();
    const statusFilter = String(url.searchParams.get('status') || 'all').trim().toLowerCase();
    const placementFilter = String(url.searchParams.get('placement') || 'all').trim().toLowerCase();

    const { results } = await env.DB.prepare(
      `SELECT id, title, image_url, target_url, link_open_mode, is_active, start_at, end_at,
              display_order, click_count, impression_count, placements, placement_stats, created_at, updated_at
       FROM ad_banners ORDER BY display_order ASC, created_at DESC`
    ).all();

    const nowMs = Date.now();
    const allBanners = (results || []).map((r) => enrichAdminBannerRow(r, nowMs));

    const summary = {
      total: allBanners.length,
      activeCount: allBanners.filter((b) => b.computed_status === 'active').length,
      inactiveCount: allBanners.filter((b) => b.computed_status === 'inactive').length,
      scheduledCount: allBanners.filter((b) => b.computed_status === 'scheduled').length,
      expiredCount: allBanners.filter((b) => b.computed_status === 'expired').length,
      totalImpressions: allBanners.reduce((acc, b) => acc + b.impression_count, 0),
      totalClicks: allBanners.reduce((acc, b) => acc + b.click_count, 0)
    };
    summary.avgCtr = summary.totalImpressions > 0
      ? Number(((summary.totalClicks / summary.totalImpressions) * 100).toFixed(2))
      : 0;

    const filtered = allBanners.filter((b) => {
      if (statusFilter !== 'all' && b.computed_status !== statusFilter) return false;
      if (placementFilter !== 'all' && !b.placements.includes(placementFilter)) return false;
      if (search && !`${b.title} ${b.target_url}`.toLowerCase().includes(search)) return false;
      return true;
    });

    return jsonResponse({ success: true, banners: filtered, summary });
  }

  if (method === 'POST' && pathname === '/api/admin/ad-banners') {
    const auth = verifyAdminAuth(request, env, url);
    if (!auth.ok) return jsonResponse({ success: false, message: auth.message }, auth.status);

    const body = await readJsonBody(request);
    if (!body) return jsonResponse({ success: false, message: 'فرمت JSON نامعتبر است.' }, 400);

    const title = String(body.title || '').trim();
    if (!title || title.length > 120) {
      return jsonResponse({ success: false, message: 'نام داخلی تبلیغ الزامی است و حداکثر ۱۲۰ کاراکتر می‌تواند باشد.' }, 400);
    }

    const imageCheck = validateBannerImageUrl(body.image_url);
    if (!imageCheck.valid) return jsonResponse({ success: false, message: imageCheck.message }, 400);

    const urlCheck = validateBannerTargetUrl(body.target_url);
    if (!urlCheck.valid) return jsonResponse({ success: false, message: urlCheck.message }, 400);

    const linkOpenMode = normalizeLinkOpenMode(body.link_open_mode);
    if (!linkOpenMode) return jsonResponse({ success: false, message: 'نوع باز شدن لینک باید external یا internal باشد.' }, 400);

    const placementsCheck = normalizePlacements(body.placements);
    if (!placementsCheck.valid) return jsonResponse({ success: false, message: placementsCheck.message }, 400);

    const startCheck = parseOptionalBannerDate(body.start_at);
    if (!startCheck.valid) return jsonResponse({ success: false, message: 'تاریخ شروع نمایش نامعتبر است.' }, 400);

    const endCheck = parseOptionalBannerDate(body.end_at);
    if (!endCheck.valid) return jsonResponse({ success: false, message: 'تاریخ پایان نمایش نامعتبر است.' }, 400);

    if (startCheck.date && endCheck.date && endCheck.date.getTime() <= startCheck.date.getTime()) {
      return jsonResponse({ success: false, message: 'تاریخ پایان نمایش باید بعد از تاریخ شروع باشد.' }, 400);
    }

    const displayOrder = Number.isFinite(Number(body.display_order)) ? Math.max(0, Math.floor(Number(body.display_order))) : 0;
    const isActiveInt = body.is_active === undefined || Boolean(body.is_active) ? 1 : 0;
    const id = `ad_${crypto.randomUUID()}`;
    const placementsJson = JSON.stringify(placementsCheck.placements);
    const nowIso = new Date().toISOString();

    await env.DB.prepare(
      `INSERT INTO ad_banners
         (id, title, image_url, target_url, link_open_mode, is_active, start_at, end_at, display_order, click_count, impression_count, placements, placement_stats, created_at, updated_at)
       VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, 0, 0, ?10, '{}', ?11, ?11)`
    ).bind(
      id,
      title,
      imageCheck.url,
      urlCheck.url,
      linkOpenMode,
      isActiveInt,
      startCheck.date ? startCheck.date.toISOString() : null,
      endCheck.date ? endCheck.date.toISOString() : null,
      displayOrder,
      placementsJson,
      nowIso
    ).run();

    const row = await env.DB.prepare(`SELECT * FROM ad_banners WHERE id = ?1 LIMIT 1`).bind(id).first();
    return jsonResponse({
      success: true,
      banner: enrichAdminBannerRow(row),
      message: 'بنر تبلیغاتی جدید با موفقیت ثبت شد.'
    }, 201);
  }

  if (method === 'PATCH' && pathname === '/api/admin/ad-banners/reorder') {
    const auth = verifyAdminAuth(request, env, url);
    if (!auth.ok) return jsonResponse({ success: false, message: auth.message }, auth.status);

    const body = await readJsonBody(request);
    const items = Array.isArray(body?.items) ? body.items : (Array.isArray(body?.orders) ? body.orders : null);
    if (!items || items.length === 0) {
      return jsonResponse({ success: false, message: 'لیست ترتیب نمایش ارسال نشده است.' }, 400);
    }

    const nowIso = new Date().toISOString();
    const stmts = items
      .filter((item) => item && item.id)
      .map((item) => {
        const orderVal = Number.isFinite(Number(item.display_order)) ? Math.max(0, Math.floor(Number(item.display_order))) : 0;
        return env.DB.prepare(`UPDATE ad_banners SET display_order = ?1, updated_at = ?2 WHERE id = ?3`)
          .bind(orderVal, nowIso, String(item.id));
      });
    if (stmts.length > 0) await env.DB.batch(stmts);

    return jsonResponse({ success: true, message: 'ترتیب نمایش بنرها به‌روزرسانی شد.' });
  }

  const bannerUpdateMatch = pathname.match(/^\/api\/admin\/ad-banners\/([^/]+)$/);
  if (method === 'PUT' && bannerUpdateMatch) {
    const auth = verifyAdminAuth(request, env, url);
    if (!auth.ok) return jsonResponse({ success: false, message: auth.message }, auth.status);

    const id = decodeURIComponent(bannerUpdateMatch[1]).trim();
    const existing = await env.DB.prepare(`SELECT * FROM ad_banners WHERE id = ?1 LIMIT 1`).bind(id).first();
    if (!existing) return jsonResponse({ success: false, message: 'بنر تبلیغاتی مورد نظر یافت نشد.' }, 404);

    const body = await readJsonBody(request) || {};
    const title = body.title !== undefined ? String(body.title).trim() : existing.title;
    if (!title || title.length > 120) {
      return jsonResponse({ success: false, message: 'نام داخلی تبلیغ الزامی است و حداکثر ۱۲۰ کاراکتر می‌تواند باشد.' }, 400);
    }

    const imageCheck = body.image_url !== undefined ? validateBannerImageUrl(body.image_url) : { valid: true, url: existing.image_url };
    if (!imageCheck.valid) return jsonResponse({ success: false, message: imageCheck.message }, 400);

    const urlCheck = body.target_url !== undefined ? validateBannerTargetUrl(body.target_url) : { valid: true, url: existing.target_url };
    if (!urlCheck.valid) return jsonResponse({ success: false, message: urlCheck.message }, 400);

    const linkOpenMode = body.link_open_mode !== undefined ? normalizeLinkOpenMode(body.link_open_mode) : (existing.link_open_mode || 'external');
    if (!linkOpenMode) return jsonResponse({ success: false, message: 'نوع باز شدن لینک باید external یا internal باشد.' }, 400);

    const placementsCheck = body.placements !== undefined
      ? normalizePlacements(body.placements)
      : { valid: true, placements: parseBannerPlacements(existing.placements) };
    if (!placementsCheck.valid) return jsonResponse({ success: false, message: placementsCheck.message }, 400);

    const startCheck = body.start_at !== undefined
      ? parseOptionalBannerDate(body.start_at)
      : { valid: true, date: existing.start_at ? new Date(existing.start_at) : null };
    if (!startCheck.valid) return jsonResponse({ success: false, message: 'تاریخ شروع نمایش نامعتبر است.' }, 400);

    const endCheck = body.end_at !== undefined
      ? parseOptionalBannerDate(body.end_at)
      : { valid: true, date: existing.end_at ? new Date(existing.end_at) : null };
    if (!endCheck.valid) return jsonResponse({ success: false, message: 'تاریخ پایان نمایش نامعتبر است.' }, 400);

    if (startCheck.date && endCheck.date && endCheck.date.getTime() <= startCheck.date.getTime()) {
      return jsonResponse({ success: false, message: 'تاریخ پایان نمایش باید بعد از تاریخ شروع باشد.' }, 400);
    }

    const isActiveInt = (body.is_active !== undefined ? Boolean(body.is_active) : Boolean(existing.is_active)) ? 1 : 0;
    const displayOrder = body.display_order !== undefined
      ? (Number.isFinite(Number(body.display_order)) ? Math.max(0, Math.floor(Number(body.display_order))) : 0)
      : Number(existing.display_order || 0);
    const placementsJson = JSON.stringify(placementsCheck.placements);
    const nowIso = new Date().toISOString();

    await env.DB.prepare(
      `UPDATE ad_banners
       SET title = ?1, image_url = ?2, target_url = ?3, link_open_mode = ?4, is_active = ?5,
           start_at = ?6, end_at = ?7, display_order = ?8, placements = ?9, updated_at = ?10
       WHERE id = ?11`
    ).bind(
      title,
      imageCheck.url,
      urlCheck.url,
      linkOpenMode,
      isActiveInt,
      startCheck.date ? startCheck.date.toISOString() : null,
      endCheck.date ? endCheck.date.toISOString() : null,
      displayOrder,
      placementsJson,
      nowIso,
      id
    ).run();

    const updated = await env.DB.prepare(`SELECT * FROM ad_banners WHERE id = ?1 LIMIT 1`).bind(id).first();
    return jsonResponse({
      success: true,
      banner: enrichAdminBannerRow(updated),
      message: 'تغییرات بنر تبلیغاتی با موفقیت ذخیره شد.'
    });
  }

  const bannerToggleMatch = pathname.match(/^\/api\/admin\/ad-banners\/([^/]+)\/toggle$/);
  if (method === 'PATCH' && bannerToggleMatch) {
    const auth = verifyAdminAuth(request, env, url);
    if (!auth.ok) return jsonResponse({ success: false, message: auth.message }, auth.status);

    const id = decodeURIComponent(bannerToggleMatch[1]).trim();
    const existing = await env.DB.prepare(`SELECT is_active FROM ad_banners WHERE id = ?1 LIMIT 1`).bind(id).first();
    if (!existing) return jsonResponse({ success: false, message: 'بنر تبلیغاتی مورد نظر یافت نشد.' }, 404);

    const nextActive = existing.is_active ? 0 : 1;
    await env.DB.prepare(
      `UPDATE ad_banners SET is_active = ?1, updated_at = ?2 WHERE id = ?3`
    ).bind(nextActive, new Date().toISOString(), id).run();

    const updated = await env.DB.prepare(`SELECT * FROM ad_banners WHERE id = ?1 LIMIT 1`).bind(id).first();
    return jsonResponse({
      success: true,
      banner: enrichAdminBannerRow(updated),
      message: nextActive ? 'تبلیغ فعال شد.' : 'تبلیغ غیرفعال شد.'
    });
  }

  if (method === 'DELETE' && bannerUpdateMatch) {
    const auth = verifyAdminAuth(request, env, url);
    if (!auth.ok) return jsonResponse({ success: false, message: auth.message }, auth.status);

    const id = decodeURIComponent(bannerUpdateMatch[1]).trim();
    const existing = await env.DB.prepare(`SELECT id, image_url FROM ad_banners WHERE id = ?1 LIMIT 1`).bind(id).first();
    if (!existing) return jsonResponse({ success: false, message: 'بنر تبلیغاتی مورد نظر یافت نشد.' }, 404);

    await env.DB.prepare(`DELETE FROM ad_banners WHERE id = ?1`).bind(id).run();

    const oldImg = existing.image_url || '';
    if (oldImg.startsWith('/uploads/') && env.BANNERS_BUCKET) {
      const refCheck = await env.DB.prepare(`SELECT id FROM ad_banners WHERE image_url = ?1 LIMIT 1`).bind(oldImg).first();
      if (!refCheck) {
        const key = oldImg.replace(/^\/uploads\//, '');
        await env.BANNERS_BUCKET.delete(key).catch(() => {});
      }
    }

    return jsonResponse({ success: true, message: 'بنر تبلیغاتی با موفقیت حذف شد.' });
  }

  // Public: Active Ad Banners
  if (method === 'GET' && pathname === '/api/ad-banners/active') {
    const serverNow = Date.now();
    const nowIso = new Date(serverNow).toISOString();
    const requestedPlacement = String(url.searchParams.get('placement') || '').trim().toLowerCase();

    const { results } = await env.DB.prepare(
      `SELECT id, title, image_url, target_url, link_open_mode, is_active, start_at, end_at, display_order, placements, updated_at
       FROM ad_banners
       WHERE is_active = 1
       ORDER BY display_order ASC, created_at DESC`
    ).all();

    const banners = (results || [])
      .filter((row) => {
        const startMs = toMs(row.start_at, null);
        const endMs = toMs(row.end_at, null);
        if (startMs !== null && startMs > serverNow) return false;
        if (endMs !== null && endMs <= serverNow) return false;
        if (!validateBannerTargetUrl(row.target_url).valid) return false;
        if (requestedPlacement) {
          const placements = parseBannerPlacements(row.placements);
          if (!placements.includes(requestedPlacement)) return false;
        }
        return true;
      })
      .map(serializePublicBannerRow);

    const latestTs = banners.reduce((max, b) => Math.max(max, b.updatedAt || 0), 0);
    const version = `${banners.length}:${latestTs}`;
    return jsonResponse({ success: true, banners, version, serverNow, checkedAt: nowIso }, 200, {
      'Cache-Control': 'no-store, no-cache, must-revalidate, proxy-revalidate',
      'Pragma': 'no-cache',
      'Expires': '0'
    });
  }

  // Public: Record Banner Impression (D1 persistent deduplication)
  const impMatch = pathname.match(/^\/api\/ad-banners\/([^/]+)\/impression$/);
  if (method === 'POST' && impMatch) {
    const id = decodeURIComponent(impMatch[1]).trim();
    const body = await readJsonBody(request) || {};
    const nowMs = Date.now();
    const viewerKey = String(body.userId || request.headers.get('x-user-id') || request.headers.get('cf-connecting-ip') || 'anon').slice(0, 64);
    const rawPlacement = String(body.placement || '').trim().toLowerCase();
    const validPlacements = ['home', 'leaderboard', 'profile', 'squares'];
    const placement = validPlacements.includes(rawPlacement) ? rawPlacement : null;
    const dedupKey = placement ? `imp:${id}:${placement}:${viewerKey}` : `imp:${id}:${viewerKey}`;

    const existingDedup = await env.DB.prepare(
      `SELECT expires_at FROM banner_event_dedup WHERE dedup_key = ?1 LIMIT 1`
    ).bind(dedupKey).first();
    if (existingDedup && Number(existingDedup.expires_at) > nowMs) {
      return jsonResponse({ success: true, recorded: false, deduplicated: true });
    }

    const row = await env.DB.prepare(
      `SELECT id, is_active, start_at, end_at, placement_stats FROM ad_banners WHERE id = ?1 LIMIT 1`
    ).bind(id).first();
    if (!row || !row.is_active) {
      return jsonResponse({ success: false, recorded: false, message: 'بنر فعال یافت نشد.' }, 404);
    }
    const startMs = toMs(row.start_at, null);
    const endMs = toMs(row.end_at, null);
    if ((startMs !== null && startMs > nowMs) || (endMs !== null && endMs <= nowMs)) {
      return jsonResponse({ success: false, recorded: false, message: 'بنر فعال یافت نشد.' }, 404);
    }

    let stats = {};
    try { stats = row.placement_stats ? JSON.parse(row.placement_stats) : {}; } catch { stats = {}; }
    if (placement) {
      if (!stats[placement]) stats[placement] = { impressions: 0, clicks: 0 };
      stats[placement].impressions = Number(stats[placement].impressions || 0) + 1;
    }

    await env.DB.batch([
      env.DB.prepare(
        `UPDATE ad_banners SET impression_count = impression_count + 1, placement_stats = ?1 WHERE id = ?2`
      ).bind(JSON.stringify(stats), id),
      env.DB.prepare(
        `INSERT INTO banner_event_dedup (dedup_key, expires_at) VALUES (?1, ?2)
         ON CONFLICT(dedup_key) DO UPDATE SET expires_at = ?2`
      ).bind(dedupKey, nowMs + 10000)
    ]);

    return jsonResponse({ success: true, recorded: true });
  }

  // Public: Record Banner Click (D1 persistent deduplication)
  const clickMatch = pathname.match(/^\/api\/ad-banners\/([^/]+)\/click$/);
  if (method === 'POST' && clickMatch) {
    const id = decodeURIComponent(clickMatch[1]).trim();
    const body = await readJsonBody(request) || {};
    const nowMs = Date.now();
    const tapNonce = String(body.tapNonce || '').trim();
    const viewerKey = String(body.userId || request.headers.get('x-user-id') || request.headers.get('cf-connecting-ip') || 'anon').slice(0, 64);
    const rawPlacement = String(body.placement || '').trim().toLowerCase();
    const validPlacements = ['home', 'leaderboard', 'profile', 'squares'];
    const placement = validPlacements.includes(rawPlacement) ? rawPlacement : null;
    const dedupKey = tapNonce ? `clk:${id}:nonce:${tapNonce}` : (placement ? `clk:${id}:${placement}:${viewerKey}` : `clk:${id}:viewer:${viewerKey}`);

    const existingDedup = await env.DB.prepare(
      `SELECT expires_at FROM banner_event_dedup WHERE dedup_key = ?1 LIMIT 1`
    ).bind(dedupKey).first();
    if (existingDedup && Number(existingDedup.expires_at) > nowMs) {
      return jsonResponse({ success: true, recorded: false, deduplicated: true });
    }

    const row = await env.DB.prepare(
      `SELECT id, is_active, start_at, end_at, placement_stats FROM ad_banners WHERE id = ?1 LIMIT 1`
    ).bind(id).first();
    if (!row || !row.is_active) {
      return jsonResponse({ success: false, recorded: false, message: 'بنر فعال یافت نشد.' }, 404);
    }
    const startMs = toMs(row.start_at, null);
    const endMs = toMs(row.end_at, null);
    if ((startMs !== null && startMs > nowMs) || (endMs !== null && endMs <= nowMs)) {
      return jsonResponse({ success: false, recorded: false, message: 'بنر فعال یافت نشد.' }, 404);
    }

    let stats = {};
    try { stats = row.placement_stats ? JSON.parse(row.placement_stats) : {}; } catch { stats = {}; }
    if (placement) {
      if (!stats[placement]) stats[placement] = { impressions: 0, clicks: 0 };
      stats[placement].clicks = Number(stats[placement].clicks || 0) + 1;
    }

    await env.DB.batch([
      env.DB.prepare(
        `UPDATE ad_banners SET click_count = click_count + 1, placement_stats = ?1 WHERE id = ?2`
      ).bind(JSON.stringify(stats), id),
      env.DB.prepare(
        `INSERT INTO banner_event_dedup (dedup_key, expires_at) VALUES (?1, ?2)
         ON CONFLICT(dedup_key) DO UPDATE SET expires_at = ?2`
      ).bind(dedupKey, nowMs + 2000)
    ]);

    return jsonResponse({ success: true, recorded: true });
  }

  return null;
}
