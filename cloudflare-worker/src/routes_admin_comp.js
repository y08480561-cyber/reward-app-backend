import { jsonResponse, generateSecureHex, requireUserAuth, verifyAdminAuth } from './security.js';
import { getActiveCompetition, readJsonBody, toMs } from './helpers.js';
import { buildSelectedUsersExcelBytes, buildUsersExcelBytes, getIranJalaliDateString } from './xlsx.js';

export async function handleAdminAndCompetitionRoutes(request, env, url, pathname) {
  const method = request.method.toUpperCase();

  // Admin auth verification
  if (method === 'GET' && pathname === '/api/admin/auth/verify') {
    const auth = verifyAdminAuth(request, env, url);
    if (!auth.ok) return jsonResponse({ error: auth.message }, auth.status);
    return jsonResponse({
      success: true,
      message: 'احراز هویت مدیریت با موفقیت تایید شد.',
      dbConnected: Boolean(env.DB)
    });
  }

  // Admin Store Management
  if (method === 'GET' && pathname === '/api/admin/store/products') {
    const auth = verifyAdminAuth(request, env, url);
    if (!auth.ok) return jsonResponse({ error: auth.message }, auth.status);

    const { results } = await env.DB.prepare(
      `SELECT id, title, description, product_type, units_granted, price_toman, myket_sku, badge, is_active, is_ready_in_myket, sort_order, created_at, updated_at
       FROM store_products ORDER BY sort_order ASC, created_at ASC`
    ).all();
    return jsonResponse({ success: true, products: results || [] });
  }

  if (method === 'POST' && pathname === '/api/admin/store/products') {
    const auth = verifyAdminAuth(request, env, url);
    if (!auth.ok) return jsonResponse({ error: auth.message }, auth.status);

    const body = await readJsonBody(request);
    if (!body) return jsonResponse({ error: 'فرمت JSON نامعتبر است.' }, 400);

    const { id, price_toman } = body;
    const allowedIds = ['pkg_squares_50', 'pkg_squares_100', 'pkg_upgrade_silver', 'pkg_upgrade_gold'];
    if (!id || !allowedIds.includes(id)) {
      return jsonResponse({ error: 'فقط ویرایش قیمت ۴ بسته ثابت فروشگاه مجاز است و افزودن یا تغییر شناسه بسته پذیرفته نیست.' }, 400);
    }
    if (price_toman === undefined || Number.isNaN(Number(price_toman)) || Number(price_toman) < 0) {
      return jsonResponse({ error: 'قیمت وارد شده نامعتبر است.' }, 400);
    }

    await env.DB.prepare(
      `UPDATE store_products SET price_toman = ?1, updated_at = ?2 WHERE id = ?3`
    ).bind(Number(price_toman), new Date().toISOString(), id).run();
    return jsonResponse({ success: true, message: 'قیمت بسته با موفقیت به‌روزرسانی شد.' });
  }

  if (method === 'DELETE' && pathname.startsWith('/api/admin/store/products/')) {
    const auth = verifyAdminAuth(request, env, url);
    if (!auth.ok) return jsonResponse({ error: auth.message }, auth.status);
    return jsonResponse({ error: 'حذف ۴ بسته ثابت فروشگاه مجاز نیست. این بسته‌ها ساختار اصلی فروشگاه هستند.' }, 400);
  }

  if (method === 'GET' && pathname === '/api/admin/store/purchases') {
    const auth = verifyAdminAuth(request, env, url);
    if (!auth.ok) return jsonResponse({ error: auth.message }, auth.status);

    const { results } = await env.DB.prepare(
      `SELECT p.purchase_id, p.user_id, p.product_id, p.amount, p.status, p.purchase_token,
              p.myket_order_id, p.units_granted, p.error_message, p.verification_source, p.created_at,
              u.first_name, u.last_name, u.phone_number, sp.title AS product_title
       FROM purchases p
       LEFT JOIN users u ON p.user_id = u.id
       LEFT JOIN store_products sp ON p.product_id = sp.id
       ORDER BY p.created_at DESC
       LIMIT 100`
    ).all();
    return jsonResponse({ success: true, purchases: results || [] });
  }

  // Admin Overview
  if (method === 'GET' && pathname === '/api/admin/overview') {
    const auth = verifyAdminAuth(request, env, url);
    if (!auth.ok) return jsonResponse({ error: auth.message }, auth.status);

    const [usersRes, sessionsRes, countRes, activeCompRes, pastCompsRes, compLogsRes, activeAnnRes, allAnnRes] = await Promise.all([
      env.DB.prepare(
        `SELECT u.id, u.first_name, u.last_name, u.phone_number, u.created_at,
                COALESCE(s.total_score, u.total_score, 0) AS score
         FROM users u
         LEFT JOIN user_scores s ON u.id = s.user_id
         ORDER BY score DESC, u.created_at ASC`
      ).all(),
      env.DB.prepare(
        `SELECT session_id, user_id, square_number, cycle_id, timestamp, ad_duration_seconds, status, token
         FROM ad_sessions ORDER BY timestamp DESC LIMIT 100`
      ).all(),
      env.DB.prepare(`SELECT COUNT(*) AS total_sessions FROM ad_sessions`).first(),
      env.DB.prepare(`SELECT * FROM competitions WHERE status = 'ACTIVE' LIMIT 1`).first(),
      env.DB.prepare(
        `SELECT id, competition_number, title, status, started_at, ended_at, total_participants, total_score
         FROM competitions WHERE status = 'ARCHIVED' ORDER BY competition_number DESC LIMIT 10`
      ).all(),
      env.DB.prepare(
        `SELECT log_id, action, admin_key_used, prev_title, new_title, participants_count, total_score_archived, details, created_at
         FROM competition_logs ORDER BY created_at DESC LIMIT 10`
      ).all(),
      env.DB.prepare(`SELECT * FROM home_announcements WHERE is_active = 1 ORDER BY updated_at DESC LIMIT 1`).first(),
      env.DB.prepare(`SELECT * FROM home_announcements ORDER BY updated_at DESC LIMIT 20`).all()
    ]);

    const serverNow = Date.now();
    const NEW_USER_WINDOW_MS = 24 * 60 * 60 * 1000;
    const users = (usersRes.results || []).map((u) => {
      const regMs = toMs(u.created_at, null);
      const isNew = regMs !== null && (serverNow - regMs) >= 0 && (serverNow - regMs) <= NEW_USER_WINDOW_MS;
      return {
        id: u.id,
        firstName: u.first_name,
        lastName: u.last_name,
        phoneNumber: u.phone_number,
        registeredAt: regMs,
        registeredAtIso: regMs !== null ? new Date(regMs).toISOString() : null,
        isNew,
        score: Number(u.score || 0)
      };
    });

    const totalScoresSum = users.reduce((sum, u) => sum + (u.score || 0), 0);
    const activeAnnouncement = activeAnnRes ? {
      id: activeAnnRes.id,
      title: activeAnnRes.title,
      content: activeAnnRes.content,
      type: activeAnnRes.type,
      isActive: Boolean(activeAnnRes.is_active),
      competitionId: activeAnnRes.competition_id,
      startsAt: toMs(activeAnnRes.starts_at, null),
      endsAt: toMs(activeAnnRes.ends_at, null),
      updatedAt: toMs(activeAnnRes.updated_at, serverNow)
    } : null;

    return jsonResponse({
      serverNow,
      totalUsers: users.length,
      totalAdSessions: Number(countRes?.total_sessions || 0),
      totalScoresSum,
      activeAnnouncement,
      announcements: (allAnnRes.results || []).map((a) => ({
        id: a.id,
        title: a.title,
        content: a.content,
        type: a.type,
        isActive: Boolean(a.is_active),
        competitionId: a.competition_id,
        startsAt: toMs(a.starts_at, null),
        endsAt: toMs(a.ends_at, null),
        updatedAt: toMs(a.updated_at, serverNow)
      })),
      activeCompetition: activeCompRes ? {
        id: activeCompRes.id,
        competitionNumber: Number(activeCompRes.competition_number),
        title: activeCompRes.title,
        status: activeCompRes.status,
        startedAt: toMs(activeCompRes.started_at, serverNow),
        endedAt: toMs(activeCompRes.ended_at, null),
        totalParticipants: Number(activeCompRes.total_participants || 0),
        totalScore: Number(activeCompRes.total_score || 0)
      } : {
        id: 'COMP_1',
        competitionNumber: 1,
        title: 'مسابقه شماره ۱ (جایزه آیفون و پلی‌استیشن)',
        status: 'ACTIVE',
        startedAt: serverNow,
        endedAt: null,
        totalParticipants: 0,
        totalScore: 0
      },
      pastCompetitions: (pastCompsRes.results || []).map((c) => ({
        id: c.id,
        competitionNumber: Number(c.competition_number),
        title: c.title,
        status: c.status,
        startedAt: toMs(c.started_at, serverNow),
        endedAt: toMs(c.ended_at, null),
        totalParticipants: Number(c.total_participants || 0),
        totalScore: Number(c.total_score || 0)
      })),
      competitionLogs: (compLogsRes.results || []).map((l) => ({
        logId: l.log_id,
        action: l.action,
        adminKeyUsed: l.admin_key_used,
        prevTitle: l.prev_title,
        newTitle: l.new_title,
        participantsCount: Number(l.participants_count || 0),
        totalScoreArchived: Number(l.total_score_archived || 0),
        details: l.details,
        createdAt: toMs(l.created_at, serverNow)
      })),
      settings: {
        tapsellAppKey: env.TAPSELL_APP_KEY || '',
        tapsellZoneId: env.TAPSELL_ZONE_ID || '',
        cycleHours: 24,
        pointsPerAd: 1
      },
      recentSessions: (sessionsRes.results || []).map((s) => ({
        sessionId: s.session_id,
        userId: s.user_id,
        squareNumber: Number(s.square_number),
        cycleId: s.cycle_id,
        timestamp: Number(s.timestamp),
        adDurationSeconds: Number(s.ad_duration_seconds),
        status: s.status,
        token: s.token
      })),
      users
    });
  }

  // Admin Users Ranking Table
  if (method === 'GET' && pathname === '/api/admin/users') {
    const auth = verifyAdminAuth(request, env, url);
    if (!auth.ok) return jsonResponse({ error: auth.message }, auth.status);

    const { results } = await env.DB.prepare(
      `SELECT u.id, u.first_name, u.last_name, u.created_at,
              COALESCE(s.total_score, u.total_score, 0) AS total_score
       FROM users u
       LEFT JOIN user_scores s ON u.id = s.user_id
       ORDER BY total_score DESC, u.created_at ASC, u.id ASC`
    ).all();

    const users = (results || []).map((row) => ({
      firstName: row.first_name || '',
      lastName: row.last_name || '',
      registeredAt: toMs(row.created_at, 0),
      score: Number(row.total_score || 0)
    }));

    return jsonResponse({ success: true, totalUsers: users.length, users });
  }

  // Export All Users Excel
  if (method === 'GET' && pathname === '/api/admin/users/export-excel') {
    const auth = verifyAdminAuth(request, env, url);
    if (!auth.ok) return jsonResponse({ error: auth.message }, auth.status);

    const { results } = await env.DB.prepare(
      `SELECT first_name, last_name, phone_number FROM users ORDER BY created_at ASC, id ASC`
    ).all();

    const rows = (results || []).map((row) => ({
      fullName: `${row.first_name || ''} ${row.last_name || ''}`.trim() || 'بدون نام',
      phoneNumber: row.phone_number != null ? String(row.phone_number).trim() : ''
    }));

    const xlsxBytes = buildUsersExcelBytes(rows);
    const jalaliDate = getIranJalaliDateString(new Date());
    const fileName = `users-${jalaliDate}.xlsx`;

    return new Response(xlsxBytes, {
      status: 200,
      headers: {
        'Content-Type': 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
        'Content-Disposition': `attachment; filename="${fileName}"; filename*=UTF-8''${encodeURIComponent(fileName)}`,
        'Content-Length': String(xlsxBytes.byteLength),
        'Cache-Control': 'no-store, no-cache, must-revalidate, private',
        'X-Content-Type-Options': 'nosniff'
      }
    });
  }

  // Export Selected Users Excel
  if (method === 'POST' && pathname === '/api/admin/users/export-selected-excel') {
    const auth = verifyAdminAuth(request, env, url);
    if (!auth.ok) return jsonResponse({ error: auth.message }, auth.status);

    const body = await readJsonBody(request);
    const rawUserIds = body && Array.isArray(body.userIds) ? body.userIds : [];
    const cleanUserIds = Array.from(
      new Set(rawUserIds.map((id) => (id == null ? '' : String(id).trim())).filter((id) => id.length > 0))
    );

    if (cleanUserIds.length === 0) {
      return jsonResponse({ success: false, error: 'لطفاً ابتدا حداقل یک کاربر را انتخاب کنید.' }, 400);
    }

    const placeholders = cleanUserIds.map((_, idx) => `?${idx + 1}`).join(', ');
    const { results } = await env.DB.prepare(
      `SELECT id, first_name, last_name, phone_number, created_at
       FROM users WHERE id IN (${placeholders})
       ORDER BY created_at ASC, id ASC`
    ).bind(...cleanUserIds).all();

    if (!results || results.length === 0) {
      return jsonResponse({ success: false, error: 'هیچ کاربر معتبری مطابق انتخاب شما در پایگاه داده یافت نشد.' }, 404);
    }

    const rows = results.map((row) => ({
      fullName: `${row.first_name || ''} ${row.last_name || ''}`.trim() || 'بدون نام',
      phoneNumber: row.phone_number != null ? String(row.phone_number).trim() : '',
      registeredAt: row.created_at || null
    }));

    const xlsxBytes = buildSelectedUsersExcelBytes(rows);
    const jalaliDate = getIranJalaliDateString(new Date());
    const fileName = `selected-users-${jalaliDate}.xlsx`;

    return new Response(xlsxBytes, {
      status: 200,
      headers: {
        'Content-Type': 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
        'Content-Disposition': `attachment; filename="${fileName}"; filename*=UTF-8''${encodeURIComponent(fileName)}`,
        'Content-Length': String(xlsxBytes.byteLength),
        'Cache-Control': 'no-store, no-cache, must-revalidate, private',
        'X-Content-Type-Options': 'nosniff'
      }
    });
  }

  // Admin Force Cycle Reset
  if (method === 'POST' && pathname === '/api/admin/cycle/reset') {
    const auth = verifyAdminAuth(request, env, url);
    if (!auth.ok) return jsonResponse({ error: auth.message }, auth.status);

    const body = await readJsonBody(request);
    const userId = body?.userId;
    if (!userId) return jsonResponse({ error: 'شناسه کاربر الزامی است.' }, 400);

    const now = Date.now();
    const newCycleId = 'CYC_' + Math.floor(now / 1000) + '_' + String(userId);
    const endTime = now + 24 * 60 * 60 * 1000;

    await env.DB.prepare(
      `INSERT INTO daily_cycles (cycle_id, user_id, start_time, end_time, status)
       VALUES (?1, ?2, ?3, ?4, 'ACTIVE')
       ON CONFLICT(cycle_id) DO UPDATE SET start_time = ?3, end_time = ?4, status = 'ACTIVE'`
    ).bind(newCycleId, userId, now, endTime).run();

    return jsonResponse({
      success: true,
      newCycle: { cycleId: newCycleId, userId, startTime: now, endTime, status: 'ACTIVE' }
    });
  }

  // Public Competition APIs (Personal score gated by valid token; no raw userId UUID exposure)
  if (method === 'GET' && pathname === '/api/competition/current') {
    const hasUserToken = Boolean(
      request.headers.get('authorization') ||
      request.headers.get('x-auth-token') ||
      request.headers.get('x-token')
    );
    const hasRawUserHint = Boolean(
      request.headers.get('x-user-id') ||
      url.searchParams.get('userId')
    );

    let authenticatedUserId = '';
    if (hasUserToken || hasRawUserHint) {
      const auth = await requireUserAuth(request, env, url);
      if (!auth.ok) return jsonResponse({ success: false, error: auth.error }, auth.status);
      const queryUserId = String(url.searchParams.get('userId') || '').trim();
      if (queryUserId && queryUserId !== auth.user.id) {
        return jsonResponse({ success: false, error: 'توکن امنیتی با شناسه کاربر مطابقت ندارد.' }, 403);
      }
      authenticatedUserId = auth.user.id;
    }

    const active = await getActiveCompetition(env.DB);
    let userScore = 0;

    if (authenticatedUserId) {
      const scoreRow = await env.DB.prepare(
        `SELECT COALESCE(s.total_score, u.total_score, 0) AS score
         FROM users u LEFT JOIN user_scores s ON u.id = s.user_id
         WHERE u.id = ?1 LIMIT 1`
      ).bind(authenticatedUserId).first();
      if (scoreRow) userScore = Number(scoreRow.score || 0);
    }

    return jsonResponse({
      success: true,
      competition: {
        id: active.id,
        competitionNumber: Number(active.competition_number),
        title: active.title,
        status: active.status,
        startedAt: toMs(active.started_at, Date.now()),
        endedAt: toMs(active.ended_at, null),
        totalParticipants: Number(active.total_participants || 0),
        totalScore: Number(active.total_score || 0)
      },
      userScore
    });
  }

  if (method === 'GET' && pathname === '/api/competitions/history') {
    const { results } = await env.DB.prepare(
      `SELECT id, competition_number, title, status, started_at, ended_at, total_participants, total_score
       FROM competitions ORDER BY competition_number DESC`
    ).all();

    return jsonResponse({
      success: true,
      competitions: (results || []).map((r) => ({
        id: r.id,
        competitionNumber: Number(r.competition_number),
        title: r.title,
        status: r.status,
        startedAt: toMs(r.started_at, Date.now()),
        endedAt: toMs(r.ended_at, null),
        totalParticipants: Number(r.total_participants || 0),
        totalScore: Number(r.total_score || 0)
      }))
    });
  }

  const compResultsMatch = pathname.match(/^\/api\/competitions\/([^/]+)\/results$/);
  if (method === 'GET' && compResultsMatch) {
    const compId = decodeURIComponent(compResultsMatch[1]);
    const comp = await env.DB.prepare(`SELECT * FROM competitions WHERE id = ?1 LIMIT 1`).bind(compId).first();
    if (!comp) return jsonResponse({ error: 'مسابقه مورد نظر یافت نشد.' }, 404);

    const adminAuth = verifyAdminAuth(request, env, url);
    const isAdmin = adminAuth.ok;

    const { results } = await env.DB.prepare(
      `SELECT id, final_score, final_rank, first_name, last_name, phone_number, archived_at
       FROM competition_archives
       WHERE competition_id = ?1
       ORDER BY final_rank ASC, final_score DESC LIMIT 200`
    ).bind(compId).all();

    const mapped = (results || []).map((r) => {
      const fullName = `${r.first_name || 'کاربر'} ${r.last_name || ''}`.trim() || 'کاربر مسابقه';
      const item = {
        rank: Number(r.final_rank),
        displayName: fullName,
        finalScore: Number(r.final_score),
        archivedAt: toMs(r.archived_at, Date.now())
      };
      if (isAdmin) {
        item.phoneNumber = r.phone_number && r.phone_number.length >= 11
          ? `${r.phone_number.substring(0, 4)}***${r.phone_number.substring(7)}`
          : '***';
      }
      return item;
    });

    return jsonResponse({
      success: true,
      competition: {
        id: comp.id,
        competitionNumber: Number(comp.competition_number),
        title: comp.title,
        status: comp.status,
        startedAt: toMs(comp.started_at, Date.now()),
        endedAt: toMs(comp.ended_at, null),
        totalParticipants: Number(comp.total_participants || 0),
        totalScore: Number(comp.total_score || 0)
      },
      results: mapped
    });
  }

  if (method === 'GET' && pathname === '/api/admin/competition/logs') {
    const auth = verifyAdminAuth(request, env, url);
    if (!auth.ok) return jsonResponse({ error: auth.message }, auth.status);

    const { results } = await env.DB.prepare(
      `SELECT log_id, action, admin_key_used, prev_competition_id, new_competition_id,
              prev_title, new_title, participants_count, total_score_archived, details, created_at
       FROM competition_logs ORDER BY created_at DESC LIMIT 50`
    ).all();

    return jsonResponse({
      success: true,
      logs: (results || []).map((l) => ({
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
        createdAt: toMs(l.created_at, Date.now())
      }))
    });
  }

  // Start New Competition (D1 lock + idempotent chunked archive + atomic cutover batch)
  if (method === 'POST' && pathname === '/api/admin/competition/start-new') {
    const auth = verifyAdminAuth(request, env, url);
    if (!auth.ok) return jsonResponse({ error: auth.message }, auth.status);

    const body = await readJsonBody(request);
    if (!body) return jsonResponse({ error: 'فرمت JSON نامعتبر است.' }, 400);

    const { confirmationPhrase, newTitle, endDate, announcementTitle, announcementContent } = body;
    if (!confirmationPhrase || String(confirmationPhrase).trim() !== 'شروع مسابقه جدید') {
      return jsonResponse({
        success: false,
        error: 'عبارت تایید نادرست است. جهت آغاز مسابقه جدید و آرشیو نتایج قبلی، لطفاً عبارت «شروع مسابقه جدید» را دقیقاً وارد نمایید.'
      }, 400);
    }

    const nowMs = Date.now();
    const staleThreshold = nowMs - 120000;
    const lockRes = await env.DB.prepare(
      `UPDATE competition_locks
       SET is_locked = 1, locked_at = ?1
       WHERE id = 'active' AND (is_locked = 0 OR locked_at IS NULL OR locked_at < ?2)`
    ).bind(nowMs, staleThreshold).run();

    if (Number(lockRes?.meta?.changes ?? 0) === 0) {
      return jsonResponse({
        success: false,
        error: 'عملیات شروع مسابقه جدید توسط مدیر در حال انجام است. لطفاً چند لحظه شکیبا باشید.'
      }, 429);
    }

    try {
      const activeComp = await env.DB.prepare(`SELECT * FROM competitions WHERE status = 'ACTIVE' LIMIT 1`).first();
      const prevNumber = activeComp ? Number(activeComp.competition_number) : 1;
      const prevId = activeComp ? activeComp.id : 'COMP_1';
      const prevTitle = activeComp ? activeComp.title : 'مسابقه شماره ۱ (جایزه آیفون و پلی‌استیشن)';

      const { results: usersRows } = await env.DB.prepare(
        `SELECT u.id AS user_id, u.first_name, u.last_name, u.phone_number,
                COALESCE(s.total_score, u.total_score, 0) AS total_score, u.created_at
         FROM users u
         LEFT JOIN user_scores s ON u.id = s.user_id
         ORDER BY total_score DESC, u.created_at ASC, u.id ASC`
      ).all();

      let totalScoreArchived = 0;
      let participantsCount = 0;
      const nowIso = new Date(nowMs).toISOString();
      const safeUsers = usersRows || [];

      const archiveStmts = [];
      for (let i = 0; i < safeUsers.length; i++) {
        const u = safeUsers[i];
        const finalScore = Number(u.total_score || 0);
        const rank = i + 1;
        if (finalScore > 0) participantsCount++;
        totalScoreArchived += finalScore;

        const archId = `ARCH_${prevId}_${u.user_id}`;
        archiveStmts.push(
          env.DB.prepare(
            `INSERT INTO competition_archives (id, competition_id, user_id, final_score, final_rank, first_name, last_name, phone_number, archived_at)
             VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9)
             ON CONFLICT(id) DO UPDATE SET
               final_score = ?4, final_rank = ?5, first_name = ?6, last_name = ?7, phone_number = ?8, archived_at = ?9`
          ).bind(archId, prevId, u.user_id, finalScore, rank, u.first_name || '', u.last_name || '', u.phone_number || '', nowIso)
        );
      }

      // Phase 1: Ensure previous competition row exists if missing, then archive snapshot in safe D1 chunks
      // NOTE: During Phase 1, scores are NOT zeroed and active competition remains ACTIVE.
      // If any chunk fails, re-running is completely idempotent via ON CONFLICT(id) DO UPDATE and zero scores are lost.
      if (!activeComp) {
        await env.DB.prepare(
          `INSERT OR IGNORE INTO competitions (id, competition_number, title, status, started_at, created_by, created_at)
           VALUES (?1, ?2, ?3, 'ACTIVE', ?4, 'admin', ?4)`
        ).bind(prevId, prevNumber, prevTitle, nowIso).run();
      }

      const ARCHIVE_CHUNK_SIZE = 50;
      for (let i = 0; i < archiveStmts.length; i += ARCHIVE_CHUNK_SIZE) {
        const chunk = archiveStmts.slice(i, i + ARCHIVE_CHUNK_SIZE);
        await env.DB.batch(chunk);
        await env.DB.prepare(`UPDATE competition_locks SET locked_at = ?1 WHERE id = 'active'`).bind(Date.now()).run();
      }

      // Phase 2: Fixed-size atomic cutover (archive close + new competition + score reset + square reset + announcement + log)
      const nextNumber = prevNumber + 1;
      const newCompId = `COMP_${nextNumber}_${nowMs}`;
      const competitionTitle = newTitle && String(newTitle).trim() ? String(newTitle).trim() : `مسابقه شماره ${nextNumber}`;
      const competitionEndDate = endDate ? new Date(endDate).toISOString() : null;

      const newAnnId = `ANN_${nowMs}_${generateSecureHex(3)}`;
      const annTitle = announcementTitle && String(announcementTitle).trim()
        ? String(announcementTitle).trim()
        : `شروع ${competitionTitle}`;
      const annBody = announcementContent && String(announcementContent).trim()
        ? String(announcementContent).trim()
        : `دوره جدید مسابقه («${competitionTitle}») آغاز شد! با مشاهده تبلیغات و تکمیل ۱۰۰ مربع روزانه امتیاز کسب کرده و برنده جوایز ارزنده شوید.`;
      const logId = `CLOG_${nowMs}_${generateSecureHex(4)}`;

      await env.DB.batch([
        env.DB.prepare(
          `UPDATE competitions SET status = 'ARCHIVED', ended_at = ?1, total_participants = ?2, total_score = ?3 WHERE id = ?4`
        ).bind(nowIso, participantsCount, totalScoreArchived, prevId),
        env.DB.prepare(
          `INSERT INTO competitions (id, competition_number, title, status, started_at, ended_at, created_by, created_at)
           VALUES (?1, ?2, ?3, 'ACTIVE', ?4, ?5, 'admin', ?4)`
        ).bind(newCompId, nextNumber, competitionTitle, nowIso, competitionEndDate),
        env.DB.prepare(`UPDATE user_scores SET total_score = 0, updated_at = ?1`).bind(nowIso),
        env.DB.prepare(`UPDATE users SET total_score = 0, updated_at = ?1`).bind(nowIso),
        env.DB.prepare(`DELETE FROM square_states`),
        env.DB.prepare(`UPDATE home_announcements SET is_active = 0`),
        env.DB.prepare(
          `INSERT INTO home_announcements (id, title, content, type, is_active, competition_id, starts_at, ends_at, created_by, created_at, updated_at)
           VALUES (?1, ?2, ?3, 'COMPETITION_START', 1, ?4, ?5, ?6, 'admin', ?5, ?5)`
        ).bind(newAnnId, annTitle, annBody, newCompId, nowIso, competitionEndDate),
        env.DB.prepare(
          `INSERT INTO competition_logs (log_id, action, admin_key_used, prev_competition_id, new_competition_id, prev_title, new_title, participants_count, total_score_archived, details, created_at)
           VALUES (?1, 'START_NEW_COMPETITION', ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10)`
        ).bind(
          logId,
          auth.maskedKey,
          prevId,
          newCompId,
          prevTitle,
          competitionTitle,
          participantsCount,
          totalScoreArchived,
          `پایان مسابقه شماره ${prevNumber} با ${participantsCount} شرکت‌کننده و آرشیو ${totalScoreArchived} امتیاز. شروع مسابقه جدید «${competitionTitle}» و انتشار اطلاعیه صفحه اصلی.`,
          nowIso
        )
      ]);

      return jsonResponse({
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
          startedAt: nowMs,
          endedAt: competitionEndDate ? toMs(competitionEndDate, null) : null
        },
        newAnnouncement: {
          id: newAnnId,
          title: annTitle,
          content: annBody,
          type: 'COMPETITION_START',
          isActive: true,
          endsAt: competitionEndDate ? toMs(competitionEndDate, null) : null
        }
      });
    } finally {
      await env.DB.prepare(`UPDATE competition_locks SET is_locked = 0, locked_at = NULL WHERE id = 'active'`).run();
    }
  }

  // Announcements
  if (method === 'GET' && pathname === '/api/announcement/active') {
    const r = await env.DB.prepare(
      `SELECT a.id, a.title, a.content, a.type, a.is_active, a.competition_id,
              a.starts_at, a.ends_at, a.created_at, a.updated_at, c.title AS competition_title
       FROM home_announcements a
       LEFT JOIN competitions c ON a.competition_id = c.id
       WHERE a.is_active = 1
       ORDER BY a.updated_at DESC LIMIT 1`
    ).first();

    const serverNow = Date.now();
    if (!r) {
      return jsonResponse({ success: true, announcement: null, serverTime: serverNow }, 200, {
        'Cache-Control': 'no-cache, no-store, must-revalidate'
      });
    }

    return jsonResponse({
      success: true,
      announcement: {
        id: r.id,
        title: r.title,
        content: r.content,
        type: r.type,
        isActive: Boolean(r.is_active),
        competitionId: r.competition_id,
        competitionTitle: r.competition_title || null,
        startsAt: toMs(r.starts_at, null),
        endsAt: toMs(r.ends_at, null),
        createdAt: toMs(r.created_at, serverNow),
        updatedAt: toMs(r.updated_at, serverNow)
      },
      serverTime: serverNow
    }, 200, { 'Cache-Control': 'no-cache, no-store, must-revalidate' });
  }

  if (method === 'GET' && pathname === '/api/admin/announcements') {
    const auth = verifyAdminAuth(request, env, url);
    if (!auth.ok) return jsonResponse({ error: auth.message }, auth.status);

    const { results } = await env.DB.prepare(
      `SELECT a.id, a.title, a.content, a.type, a.is_active, a.competition_id,
              a.starts_at, a.ends_at, a.created_by, a.created_at, a.updated_at, c.title AS competition_title
       FROM home_announcements a
       LEFT JOIN competitions c ON a.competition_id = c.id
       ORDER BY a.updated_at DESC LIMIT 50`
    ).all();

    return jsonResponse({
      success: true,
      announcements: (results || []).map((r) => ({
        id: r.id,
        title: r.title,
        content: r.content,
        type: r.type,
        isActive: Boolean(r.is_active),
        competitionId: r.competition_id,
        competitionTitle: r.competition_title || null,
        startsAt: toMs(r.starts_at, null),
        endsAt: toMs(r.ends_at, null),
        createdBy: r.created_by,
        createdAt: toMs(r.created_at, Date.now()),
        updatedAt: toMs(r.updated_at, Date.now())
      }))
    });
  }

  if (method === 'POST' && pathname === '/api/admin/announcements') {
    const auth = verifyAdminAuth(request, env, url);
    if (!auth.ok) return jsonResponse({ error: auth.message }, auth.status);

    const body = await readJsonBody(request);
    if (!body) return jsonResponse({ error: 'فرمت JSON نامعتبر است.' }, 400);

    const { id, title, content, type, isActive, competitionId, startsAt, endsAt } = body;
    if (!title || !String(title).trim()) return jsonResponse({ error: 'عنوان پیام نمی‌تواند خالی باشد.' }, 400);
    if (!content || !String(content).trim()) return jsonResponse({ error: 'متن پیام نمی‌تواند خالی باشد.' }, 400);

    const cleanTitle = String(title).trim();
    const cleanContent = String(content).trim();
    const cleanType = type || 'ANNOUNCEMENT';
    const activeBool = isActive !== false;
    const activeInt = activeBool ? 1 : 0;
    const nowIso = new Date().toISOString();

    let startsAtIso = nowIso;
    if (startsAt) {
      const parsedStart = new Date(startsAt);
      if (Number.isNaN(parsedStart.getTime())) {
        return jsonResponse({ error: 'فرمت تاریخ شروع نامعتبر است.' }, 400);
      }
      startsAtIso = parsedStart.toISOString();
    }

    let endsAtIso = null;
    if (endsAt) {
      const parsedEnd = new Date(endsAt);
      if (Number.isNaN(parsedEnd.getTime())) {
        return jsonResponse({ error: 'فرمت تاریخ پایان نامعتبر است.' }, 400);
      }
      endsAtIso = parsedEnd.toISOString();
    }

    // Validate target announcement existence BEFORE deactivating any existing active announcement
    let savedId = id ? String(id).trim() : '';
    if (savedId) {
      const targetRow = await env.DB.prepare(
        `SELECT id FROM home_announcements WHERE id = ?1 LIMIT 1`
      ).bind(savedId).first();
      if (!targetRow) {
        return jsonResponse({ error: 'پیام مورد نظر جهت ویرایش یافت نشد.' }, 404);
      }
    } else {
      savedId = `ANN_${Date.now()}_${generateSecureHex(3)}`;
    }

    const stmts = [];
    if (activeBool) {
      stmts.push(env.DB.prepare(`UPDATE home_announcements SET is_active = 0 WHERE id != ?1`).bind(savedId));
    }

    if (id) {
      stmts.push(
        env.DB.prepare(
          `UPDATE home_announcements
           SET title = ?1, content = ?2, type = ?3, is_active = ?4, competition_id = ?5, starts_at = ?6, ends_at = ?7, updated_at = ?8
           WHERE id = ?9`
        ).bind(cleanTitle, cleanContent, cleanType, activeInt, competitionId || null, startsAtIso, endsAtIso, nowIso, savedId)
      );
    } else {
      stmts.push(
        env.DB.prepare(
          `INSERT INTO home_announcements (id, title, content, type, is_active, competition_id, starts_at, ends_at, created_by, created_at, updated_at)
           VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, 'admin', ?9, ?9)`
        ).bind(savedId, cleanTitle, cleanContent, cleanType, activeInt, competitionId || null, startsAtIso, endsAtIso, nowIso)
      );
    }

    await env.DB.batch(stmts);

    return jsonResponse({
      success: true,
      message: activeBool ? 'پیام با موفقیت ذخیره و در صفحه اصلی منتشر شد.' : 'پیام با موفقیت ذخیره شد (غیرفعال).',
      announcement: {
        id: savedId,
        title: cleanTitle,
        content: cleanContent,
        type: cleanType,
        isActive: activeBool,
        competitionId: competitionId || null,
        startsAt: toMs(startsAtIso, Date.now()),
        endsAt: endsAtIso ? toMs(endsAtIso, null) : null,
        updatedAt: Date.now()
      }
    });
  }

  const toggleAnnMatch = pathname.match(/^\/api\/admin\/announcements\/([^/]+)\/toggle$/);
  if (method === 'PATCH' && toggleAnnMatch) {
    const auth = verifyAdminAuth(request, env, url);
    if (!auth.ok) return jsonResponse({ error: auth.message }, auth.status);

    const annId = decodeURIComponent(toggleAnnMatch[1]);
    const existing = await env.DB.prepare(`SELECT is_active FROM home_announcements WHERE id = ?1 LIMIT 1`).bind(annId).first();
    if (!existing) return jsonResponse({ error: 'پیام یافت نشد.' }, 404);

    const newStatus = !Boolean(existing.is_active);
    const nowIso = new Date().toISOString();
    const stmts = [];
    if (newStatus) {
      stmts.push(env.DB.prepare(`UPDATE home_announcements SET is_active = 0`));
    }
    stmts.push(
      env.DB.prepare(`UPDATE home_announcements SET is_active = ?1, updated_at = ?2 WHERE id = ?3`)
        .bind(newStatus ? 1 : 0, nowIso, annId)
    );
    await env.DB.batch(stmts);

    return jsonResponse({
      success: true,
      isActive: newStatus,
      message: newStatus ? 'پیام با موفقیت در صفحه اصلی فعال شد.' : 'نمایش پیام در صفحه اصلی غیرفعال گردید.'
    });
  }

  // OneSignal Status & Push Notification
  if (method === 'GET' && pathname === '/api/admin/onesignal/status') {
    const auth = verifyAdminAuth(request, env, url);
    if (!auth.ok) return jsonResponse({ error: auth.message }, auth.status);
    const configured = Boolean(env.ONESIGNAL_APP_ID && env.ONESIGNAL_REST_API_KEY);
    return jsonResponse({
      success: true,
      configured,
      appId: env.ONESIGNAL_APP_ID ? `${String(env.ONESIGNAL_APP_ID).substring(0, 8)}...` : null
    });
  }

  if (method === 'POST' && pathname === '/api/admin/push-notification') {
    const auth = verifyAdminAuth(request, env, url);
    if (!auth.ok) return jsonResponse({ error: auth.message }, auth.status);

    const body = await readJsonBody(request);
    const { title, message, audience } = body || {};
    if (!title || !message) return jsonResponse({ error: 'عنوان و متن اعلان الزامی است.' }, 400);

    if (!env.ONESIGNAL_APP_ID || !env.ONESIGNAL_REST_API_KEY) {
      return jsonResponse({
        error: 'کلیدهای ONESIGNAL_APP_ID یا ONESIGNAL_REST_API_KEY در تنظیمات Cloudflare Worker وارد نشده‌اند.'
      }, 503);
    }

    try {
      const osRes = await fetch('https://onesignal.com/api/v1/notifications', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Basic ${env.ONESIGNAL_REST_API_KEY}`
        },
        body: JSON.stringify({
          app_id: env.ONESIGNAL_APP_ID,
          headings: { en: title },
          contents: { en: message },
          included_segments: audience === 'ALL' ? ['All'] : ['Subscribed Users']
        })
      });
      const data = await osRes.json();
      if (osRes.ok) return jsonResponse({ success: true, data });
      const errDetail = Array.isArray(data.errors) ? data.errors.join(', ') : (data.errors || 'خطا از سوی وان‌سیگنال');
      return jsonResponse({ success: false, error: errDetail }, 500);
    } catch {
      return jsonResponse({ success: false, error: 'خطا در برقراری ارتباط با وان‌سیگنال.' }, 500);
    }
  }

  // Login Notification Config (Public & Admin)
  const defaultLoginSettings = {
    id: 'default',
    is_visible: true,
    badge_text: 'سرویس اختصاصی مشترکین ایرانسل',
    badge_icon: 'verified',
    main_title: 'سامانه منحصرأ ویژه مشترکان ارجمند ایرانسل',
    main_description: 'با کمال احترام و افتخار، این سامانه منحصراً جهت حضور، رقابت و بهره‌مندی مشترکین گرامی و همراهان وفادار اپراتور ایرانسل طراحی گردیده است.',
    warning_is_visible: true,
    warning_title: 'شرط الزامی: ورود با «اینترنت دیتای ایرانسل»',
    warning_description: 'لطفاً پیش از ورود، وای‌فای و فیلترشکن را خاموش نموده و از طریق اینترنت همراه سیم‌کارت ایرانسل متصل گردید.',
    warning_icon: 'signal_cellular_alt',
    updated_at: new Date().toISOString()
  };

  if (method === 'GET' && pathname === '/api/app/login-notification') {
    const row = await env.DB.prepare(`SELECT * FROM login_notification_settings WHERE id = 'default' LIMIT 1`).first();
    if (!row) {
      return jsonResponse({ success: true, data: defaultLoginSettings, settings: defaultLoginSettings });
    }
    const formatted = {
      ...row,
      is_visible: Boolean(row.is_visible),
      warning_is_visible: Boolean(row.warning_is_visible)
    };
    return jsonResponse({ success: true, data: formatted, settings: formatted });
  }

  if (method === 'GET' && pathname === '/api/admin/login-notification') {
    const auth = verifyAdminAuth(request, env, url);
    if (!auth.ok) return jsonResponse({ success: false, error: auth.message }, auth.status);

    const row = await env.DB.prepare(`SELECT * FROM login_notification_settings WHERE id = 'default' LIMIT 1`).first();
    if (!row) return jsonResponse({ success: true, data: null, settings: null });
    const formatted = {
      ...row,
      is_visible: Boolean(row.is_visible),
      warning_is_visible: Boolean(row.warning_is_visible)
    };
    return jsonResponse({ success: true, data: formatted, settings: formatted });
  }

  if (method === 'POST' && pathname === '/api/admin/login-notification') {
    const auth = verifyAdminAuth(request, env, url);
    if (!auth.ok) return jsonResponse({ success: false, error: auth.message }, auth.status);

    const body = await readJsonBody(request);
    if (!body) return jsonResponse({ success: false, error: 'فرمت JSON نامعتبر است.' }, 400);

    const {
      is_visible, badge_text, badge_icon, main_title, main_description,
      warning_is_visible, warning_title, warning_description, warning_icon
    } = body;
    const nowIso = new Date().toISOString();

    await env.DB.prepare(
      `INSERT INTO login_notification_settings
         (id, is_visible, badge_text, badge_icon, main_title, main_description, warning_is_visible, warning_title, warning_description, warning_icon, updated_at)
       VALUES ('default', ?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10)
       ON CONFLICT(id) DO UPDATE SET
         is_visible = ?1, badge_text = ?2, badge_icon = ?3, main_title = ?4, main_description = ?5,
         warning_is_visible = ?6, warning_title = ?7, warning_description = ?8, warning_icon = ?9, updated_at = ?10`
    ).bind(
      is_visible ? 1 : 0,
      badge_text || defaultLoginSettings.badge_text,
      badge_icon || defaultLoginSettings.badge_icon,
      main_title || defaultLoginSettings.main_title,
      main_description || defaultLoginSettings.main_description,
      warning_is_visible ? 1 : 0,
      warning_title || defaultLoginSettings.warning_title,
      warning_description || defaultLoginSettings.warning_description,
      warning_icon || defaultLoginSettings.warning_icon,
      nowIso
    ).run();

    const saved = await env.DB.prepare(`SELECT * FROM login_notification_settings WHERE id = 'default' LIMIT 1`).first();
    const formatted = saved ? {
      ...saved,
      is_visible: Boolean(saved.is_visible),
      warning_is_visible: Boolean(saved.warning_is_visible)
    } : defaultLoginSettings;

    return jsonResponse({ success: true, data: formatted, settings: formatted });
  }

  return null;
}
