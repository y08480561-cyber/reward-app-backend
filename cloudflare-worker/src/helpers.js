import { parseBannerPlacements, normalizePlacements, validateBannerTargetUrl } from './security.js';

export const REQUIRED_D1_TABLES = [
  'users',
  'user_scores',
  'daily_cycles',
  'square_states',
  'ad_sessions',
  'user_memberships',
  'purchases',
  'competitions',
  'competition_archives',
  'competition_logs',
  'home_announcements',
  'support_tickets',
  'support_messages',
  'store_products',
  'user_entitlements',
  'reward_categories',
  'rewards',
  'reward_appearance',
  'reward_claims',
  'login_notification_settings',
  'ad_banners',
  'user_feedback',
  'otp_codes',
  'rate_limits',
  'competition_locks',
  'banner_event_dedup'
];

export const CORS_HEADERS = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Methods': 'GET, POST, PUT, PATCH, DELETE, OPTIONS',
  'Access-Control-Allow-Headers': 'Content-Type, Authorization, x-admin-key, x-user-id, x-auth-token, x-token, Cache-Control, Pragma, Expires'
};

export function withCors(response) {
  const headers = new Headers(response.headers);
  for (const [k, v] of Object.entries(CORS_HEADERS)) {
    headers.set(k, v);
  }
  return new Response(response.body, {
    status: response.status,
    statusText: response.statusText,
    headers
  });
}

export async function readJsonBody(request) {
  try {
    const text = await request.text();
    if (!text || !text.trim()) return {};
    return JSON.parse(text);
  } catch {
    return null;
  }
}

export function toMs(val, fallback = null) {
  if (val === null || val === undefined || val === '') return fallback;
  if (typeof val === 'number') return Number.isFinite(val) ? val : fallback;
  const parsed = Date.parse(String(val));
  return Number.isFinite(parsed) ? parsed : fallback;
}

/**
 * Tehran (UTC+3:30) midnight bounds without DST (Iran abolished DST in 2023).
 */
export function getIranMidnightBounds(nowMs = Date.now()) {
  const tehranOffsetMs = 3.5 * 3600 * 1000;
  const tehranLocalMs = nowMs + tehranOffsetMs;
  const d = new Date(tehranLocalMs);
  const startUtcMs = Date.UTC(d.getUTCFullYear(), d.getUTCMonth(), d.getUTCDate(), 0, 0, 0, 0) - tehranOffsetMs;
  const endUtcMs = startUtcMs + 24 * 60 * 60 * 1000;
  return { startTime: startUtcMs, endTime: endUtcMs };
}

export async function getUserActiveTierAndMultiplier(db, userId) {
  if (!db || !userId) return { tier: 'NORMAL', multiplier: 1 };
  try {
    const row = await db.prepare(
      `SELECT
         COALESCE(u.account_tier, e.account_tier, 'NORMAL') AS account_tier,
         COALESCE(u.multiplier, e.multiplier, 1) AS multiplier,
         COALESCE(u.panel_type, 'free') AS panel_type
       FROM users u
       LEFT JOIN user_entitlements e ON u.id = e.user_id
       WHERE u.id = ?1
       LIMIT 1`
    ).bind(userId).first();

    let tier = 'NORMAL';
    if (row) {
      const rawTier = String(row.account_tier || '').toUpperCase();
      const rawPanel = String(row.panel_type || '').toLowerCase();
      if (rawTier === 'GOLD' || rawTier === 'VIP' || rawPanel === 'gold' || rawPanel === 'vip') {
        tier = 'GOLD';
      } else if (rawTier === 'SILVER' || rawPanel === 'silver') {
        tier = 'SILVER';
      }
    }

    const multiplier = tier === 'GOLD' ? 6 : tier === 'SILVER' ? 3 : 1;
    return { tier, multiplier };
  } catch (err) {
    console.warn('[getUserActiveTierAndMultiplier Warning]', err?.message || err);
    return { tier: 'NORMAL', multiplier: 1 };
  }
}

export async function getOrCreateActiveCycle(db, userId) {
  const now = Date.now();
  const { startTime, endTime } = getIranMidnightBounds(now);

  const existing = await db.prepare(
    `SELECT cycle_id, user_id, start_time, end_time, status
     FROM daily_cycles
     WHERE user_id = ?1 AND start_time <= ?2 AND end_time > ?2
     ORDER BY start_time DESC
     LIMIT 1`
  ).bind(userId, now).first();

  if (existing && now < Number(existing.end_time)) {
    return {
      cycleId: existing.cycle_id,
      userId: existing.user_id,
      startTime: Number(existing.start_time),
      endTime: Number(existing.end_time),
      status: existing.status || 'ACTIVE'
    };
  }

  // Note: cycle_id is PRIMARY KEY; prefix per-user or match legacy CYC_<epoch> safely when same second
  const baseCycleId = 'CYC_' + Math.floor(startTime / 1000);
  const existingConflict = await db.prepare(
    `SELECT cycle_id, user_id FROM daily_cycles WHERE cycle_id = ?1 LIMIT 1`
  ).bind(baseCycleId).first();

  const cycleId = !existingConflict || existingConflict.user_id === userId
    ? baseCycleId
    : `${baseCycleId}_${userId.slice(0, 8)}`;

  await db.prepare(
    `INSERT INTO daily_cycles (cycle_id, user_id, start_time, end_time, status)
     VALUES (?1, ?2, ?3, ?4, 'ACTIVE')
     ON CONFLICT(cycle_id) DO UPDATE SET start_time = ?3, end_time = ?4, status = 'ACTIVE'`
  ).bind(cycleId, userId, startTime, endTime).run();

  return {
    cycleId,
    userId,
    startTime,
    endTime,
    status: 'ACTIVE'
  };
}

export async function getActiveCompetition(db) {
  let active = await db.prepare(
    `SELECT * FROM competitions WHERE status = 'ACTIVE' LIMIT 1`
  ).first();

  if (!active) {
    const nowIso = new Date().toISOString();
    await db.prepare(
      `INSERT OR IGNORE INTO competitions (id, competition_number, title, status, started_at, created_by, created_at)
       VALUES ('COMP_1', 1, 'مسابقه شماره ۱ (جایزه آیفون و پلی‌استیشن)', 'ACTIVE', ?1, 'admin', ?1)`
    ).bind(nowIso).run();
    active = await db.prepare(`SELECT * FROM competitions WHERE status = 'ACTIVE' LIMIT 1`).first();
  }
  return active;
}

export async function generateUniqueTrackingCode(db) {
  for (let attempt = 0; attempt < 5; attempt++) {
    const arr = new Uint32Array(1);
    crypto.getRandomValues(arr);
    const num = 100000 + (arr[0] % 900000);
    const code = `TRK-${num}`;
    const existing = await db.prepare(
      `SELECT id FROM support_tickets WHERE tracking_code = ?1 LIMIT 1`
    ).bind(code).first();
    if (!existing) return code;
  }
  return `TRK-${Date.now().toString().slice(-6)}`;
}

export function enrichAdminBannerRow(row, nowMs = Date.now()) {
  const startMs = toMs(row.start_at, null);
  const endMs = toMs(row.end_at, null);
  const isActive = Boolean(row.is_active);

  let computedStatus = 'active';
  if (!isActive) {
    computedStatus = 'inactive';
  } else if (startMs !== null && startMs > nowMs) {
    computedStatus = 'scheduled';
  } else if (endMs !== null && endMs <= nowMs) {
    computedStatus = 'expired';
  }

  const impressions = Number(row.impression_count || 0);
  const clicks = Number(row.click_count || 0);
  const ctrPercent = impressions > 0 ? Number(((clicks / impressions) * 100).toFixed(2)) : 0;

  let placementStats = {};
  if (row.placement_stats) {
    if (typeof row.placement_stats === 'object') {
      placementStats = row.placement_stats;
    } else {
      try {
        placementStats = JSON.parse(row.placement_stats);
      } catch {
        placementStats = {};
      }
    }
  }

  return {
    id: row.id,
    title: row.title,
    image_url: row.image_url,
    target_url: row.target_url,
    link_open_mode: row.link_open_mode === 'internal' ? 'internal' : 'external',
    is_active: isActive,
    start_at: row.start_at,
    end_at: row.end_at,
    display_order: Number(row.display_order || 0),
    placements: parseBannerPlacements(row.placements),
    placement_stats: placementStats,
    click_count: clicks,
    impression_count: impressions,
    ctr_percent: ctrPercent,
    computed_status: computedStatus,
    created_at: row.created_at,
    updated_at: row.updated_at
  };
}

export function serializePublicBannerRow(row) {
  return {
    id: row.id,
    title: row.title,
    imageUrl: row.image_url,
    targetUrl: row.target_url,
    linkOpenMode: row.link_open_mode === 'internal' ? 'internal' : 'external',
    isActive: Boolean(row.is_active),
    startAt: toMs(row.start_at, null),
    endAt: toMs(row.end_at, null),
    displayOrder: Number(row.display_order || 0),
    placements: parseBannerPlacements(row.placements),
    updatedAt: toMs(row.updated_at, Date.now())
  };
}

export { normalizePlacements, parseBannerPlacements, validateBannerTargetUrl };
