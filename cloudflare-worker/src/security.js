/**
 * Security, Authentication, Password Hashing, RSA Verification & Validation Helpers
 * Fully compatible with Cloudflare Workers Web Crypto API (zero external packages).
 */

export const DEFAULT_MYKET_PUBLIC_KEY =
  'MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQCIUMmkIxGVpyKupQajOZqGfhYSkoGA0I1rhDiTLkKCiObtCnqXHJYdTJTRYKlSmA/KIIa4rC08CoZILDRjKKVt+Z3ZOt2pONjTr6jt+FtObbg7bNpTSWbcUE6Np5rz/nV/wHfQvn3wv/AJ9UFW95qAaKpwf0CZLLxG8a33BaB4dwIDAQAB';

export function jsonResponse(payload, status = 200, extraHeaders = {}) {
  return new Response(JSON.stringify(payload), {
    status,
    headers: {
      'Content-Type': 'application/json; charset=utf-8',
      ...extraHeaders
    }
  });
}

export function bytesToHex(bytes) {
  let out = '';
  for (let i = 0; i < bytes.length; i++) {
    out += bytes[i].toString(16).padStart(2, '0');
  }
  return out;
}

export function hexToBytes(hex) {
  const clean = String(hex || '').trim();
  const len = clean.length / 2;
  const out = new Uint8Array(len);
  for (let i = 0; i < len; i++) {
    out[i] = parseInt(clean.slice(i * 2, i * 2 + 2), 16);
  }
  return out;
}

export function timingSafeEqualStr(a, b) {
  const s1 = String(a || '');
  const s2 = String(b || '');
  const enc = new TextEncoder();
  const b1 = enc.encode(s1);
  const b2 = enc.encode(s2);
  const maxLen = Math.max(b1.length, b2.length, 1);
  let diff = b1.length ^ b2.length;
  for (let i = 0; i < maxLen; i++) {
    const v1 = i < b1.length ? b1[i] : 0;
    const v2 = i < b2.length ? b2[i] : 0;
    diff |= v1 ^ v2;
  }
  return diff === 0 && b1.length > 0;
}

export function generateSecureHex(byteLength = 16) {
  const buf = new Uint8Array(byteLength);
  crypto.getRandomValues(buf);
  return bytesToHex(buf);
}

export function generateSecureAuthToken() {
  return 'SEC_JWT_' + generateSecureHex(32);
}

/**
 * Legacy SHA-256 verification helper ONLY for transparent upgrade of existing migrated users.
 */
async function legacySha256Hash(password) {
  const data = new TextEncoder().encode(String(password) + '_reqabat_secure_salt_2026');
  const digest = await crypto.subtle.digest('SHA-256', data);
  return bytesToHex(new Uint8Array(digest));
}

/**
 * PBKDF2-SHA256 password hash format: pbkdf2_v1$<saltHex>$<hashHex>
 */
export async function hashPasswordPBKDF2(password) {
  const saltBytes = new Uint8Array(16);
  crypto.getRandomValues(saltBytes);
  const saltHex = bytesToHex(saltBytes);
  const keyMaterial = await crypto.subtle.importKey(
    'raw',
    new TextEncoder().encode(String(password)),
    { name: 'PBKDF2' },
    false,
    ['deriveBits']
  );
  const derivedBits = await crypto.subtle.deriveBits(
    {
      name: 'PBKDF2',
      salt: saltBytes,
      iterations: 100000,
      hash: 'SHA-256'
    },
    keyMaterial,
    256
  );
  const hashHex = bytesToHex(new Uint8Array(derivedBits));
  return `pbkdf2_v1$${saltHex}$${hashHex}`;
}

export async function verifyPassword(password, storedHash) {
  const pass = String(password || '');
  const hash = String(storedHash || '');
  if (!pass || !hash) return { valid: false, needsUpgrade: false };

  if (hash.startsWith('pbkdf2_v1$')) {
    const parts = hash.split('$');
    if (parts.length !== 3) return { valid: false, needsUpgrade: false };
    const [, saltHex, expectedHex] = parts;
    const saltBytes = hexToBytes(saltHex);
    const keyMaterial = await crypto.subtle.importKey(
      'raw',
      new TextEncoder().encode(pass),
      { name: 'PBKDF2' },
      false,
      ['deriveBits']
    );
    const derivedBits = await crypto.subtle.deriveBits(
      {
        name: 'PBKDF2',
        salt: saltBytes,
        iterations: 100000,
        hash: 'SHA-256'
      },
      keyMaterial,
      256
    );
    const actualHex = bytesToHex(new Uint8Array(derivedBits));
    return { valid: timingSafeEqualStr(actualHex, expectedHex), needsUpgrade: false };
  }

  // Legacy PostgreSQL migrated user hash support + auto-upgrade flag
  const legacyHex = await legacySha256Hash(pass);
  if (timingSafeEqualStr(legacyHex, hash)) {
    return { valid: true, needsUpgrade: true };
  }
  return { valid: false, needsUpgrade: false };
}

/**
 * Strict Header-Only Admin Authentication.
 * Rejects query-string keys and never falls back to a hardcoded password.
 */
export function verifyAdminAuth(request, env, url) {
  const configuredKey = String(env.ADMIN_KEY || '').trim();
  if (!configuredKey) {
    return {
      ok: false,
      status: 503,
      message: 'کلید مدیریت (ADMIN_KEY) روی Cloudflare Worker تنظیم نشده است.'
    };
  }

  // Explicitly reject query-string credential leaks
  if (url && (url.searchParams.has('adminKey') || url.searchParams.has('key'))) {
    return {
      ok: false,
      status: 401,
      message: 'ارسال کلید امنیتی در آدرس URL مجاز نیست. از هدر x-admin-key استفاده کنید.'
    };
  }

  const headerKey = String(request.headers.get('x-admin-key') || '').trim();
  const authHeader = String(request.headers.get('authorization') || '').trim();
  const bearerKey = authHeader.replace(/^Bearer\s+/i, '').trim();
  const candidate = headerKey || bearerKey;

  if (!candidate || !timingSafeEqualStr(candidate, configuredKey)) {
    return {
      ok: false,
      status: 401,
      message: 'دسترسی غیرمجاز. کلید امنیتی مدیریت نامعتبر است.'
    };
  }

  return {
    ok: true,
    maskedKey: candidate.length > 4 ? candidate.slice(0, 4) + '***' : '***'
  };
}

/**
 * Strict Header-Only User Session Authentication against D1 `users.auth_token`.
 * - Rejects query-string tokens
 * - Never authenticates by x-user-id alone
 * - Never overwrites users.auth_token from headers
 */
export async function requireUserAuth(request, env, url) {
  if (url && (url.searchParams.has('token') || url.searchParams.has('authToken'))) {
    return {
      ok: false,
      status: 401,
      error: 'ارسال توکن در پارامتر URL مجاز نیست.'
    };
  }

  const authHeader = String(request.headers.get('authorization') || '').trim();
  const bearerToken = /^Bearer\s+/i.test(authHeader) ? authHeader.replace(/^Bearer\s+/i, '').trim() : '';
  const xAuthToken = String(request.headers.get('x-auth-token') || '').trim();
  const xToken = String(request.headers.get('x-token') || '').trim();
  const token = xAuthToken || xToken || bearerToken;

  if (!token) {
    return {
      ok: false,
      status: 401,
      error: 'لطفاً ابتدا وارد حساب کاربری خود شوید (توکن احراز هویت الزامی است).'
    };
  }

  const headerUserId = String(request.headers.get('x-user-id') || '').trim();
  const userRow = await env.DB.prepare(
    `SELECT id, first_name, last_name, phone_number, status, auth_token,
            instant_unlock_credits, is_vip, account_tier, panel_type, multiplier,
            feedback_reward_claimed, notifications_enabled
     FROM users
     WHERE auth_token = ?1
     LIMIT 1`
  ).bind(token).first();

  if (!userRow) {
    return {
      ok: false,
      status: 401,
      error: 'نشست کاربری شما نامعتبر یا منقضی شده است. لطفاً مجدداً وارد شوید.'
    };
  }

  if (headerUserId && headerUserId !== userRow.id) {
    return {
      ok: false,
      status: 403,
      error: 'توکن امنیتی با شناسه کاربر مطابقت ندارد.'
    };
  }

  if (userRow.status && userRow.status !== 'active') {
    return {
      ok: false,
      status: 403,
      error: 'حساب کاربری شما مسدود یا غیرفعال است.'
    };
  }

  return {
    ok: true,
    user: {
      id: userRow.id,
      firstName: userRow.first_name || '',
      lastName: userRow.last_name || '',
      fullName: `${userRow.first_name || ''} ${userRow.last_name || ''}`.trim() || 'کاربر مسابقه',
      phoneNumber: userRow.phone_number || '',
      accountTier: userRow.account_tier || 'NORMAL',
      panelType: userRow.panel_type || 'free',
      multiplier: Number(userRow.multiplier || 1),
      notificationsEnabled: Boolean(userRow.notifications_enabled ?? 1)
    }
  };
}

/**
 * Web Crypto RSA-SHA1 (RSASSA-PKCS1-v1_5 + SHA-1) verifier for Myket In-App Billing.
 */
export async function verifyMyketRsaSignature(signedData, signatureBase64, publicKeyBase64 = DEFAULT_MYKET_PUBLIC_KEY) {
  if (!signedData || !signatureBase64) return false;
  try {
    const cleanKey = String(publicKeyBase64 || DEFAULT_MYKET_PUBLIC_KEY)
      .replace(/-----BEGIN PUBLIC KEY-----/g, '')
      .replace(/-----END PUBLIC KEY-----/g, '')
      .replace(/\s+/g, '');
    const spkiBinary = Uint8Array.from(atob(cleanKey), (c) => c.charCodeAt(0));
    const sigBinary = Uint8Array.from(atob(String(signatureBase64).trim()), (c) => c.charCodeAt(0));
    const cryptoKey = await crypto.subtle.importKey(
      'spki',
      spkiBinary.buffer,
      { name: 'RSASSA-PKCS1-v1_5', hash: 'SHA-1' },
      false,
      ['verify']
    );
    const dataBytes = new TextEncoder().encode(String(signedData));
    return await crypto.subtle.verify('RSASSA-PKCS1-v1_5', cryptoKey, sigBinary, dataBytes);
  } catch (err) {
    console.error('[Worker RSA Verify Error]', err?.message || err);
    return false;
  }
}

/**
 * D1-backed persistent sliding/fixed window rate limiter.
 */
export async function checkD1RateLimit(db, rateKey, maxRequests, windowMs) {
  const now = Date.now();
  const existing = await db.prepare(
    `SELECT window_start, count FROM rate_limits WHERE rate_key = ?1 LIMIT 1`
  ).bind(rateKey).first();

  if (!existing || (now - Number(existing.window_start)) >= windowMs) {
    await db.prepare(
      `INSERT INTO rate_limits (rate_key, window_start, count, updated_at)
       VALUES (?1, ?2, 1, ?2)
       ON CONFLICT(rate_key) DO UPDATE SET window_start = ?2, count = 1, updated_at = ?2`
    ).bind(rateKey, now).run();
    return { allowed: true, remaining: maxRequests - 1 };
  }

  const nextCount = Number(existing.count || 0) + 1;
  if (nextCount > maxRequests) {
    return { allowed: false, remaining: 0 };
  }

  await db.prepare(
    `UPDATE rate_limits SET count = ?1, updated_at = ?2 WHERE rate_key = ?3`
  ).bind(nextCount, now, rateKey).run();
  return { allowed: true, remaining: Math.max(0, maxRequests - nextCount) };
}

export function detectImageFormatFromBytes(bytes) {
  if (!bytes || bytes.length < 12) return null;
  if (bytes[0] === 0xff && bytes[1] === 0xd8 && bytes[2] === 0xff) {
    return { ext: 'jpg', mime: 'image/jpeg' };
  }
  if (
    bytes[0] === 0x89 && bytes[1] === 0x50 &&
    bytes[2] === 0x4e && bytes[3] === 0x47 &&
    bytes[4] === 0x0d && bytes[5] === 0x0a &&
    bytes[6] === 0x1a && bytes[7] === 0x0a
  ) {
    return { ext: 'png', mime: 'image/png' };
  }
  if (
    bytes[0] === 0x52 && bytes[1] === 0x49 && bytes[2] === 0x46 && bytes[3] === 0x46 &&
    bytes[8] === 0x57 && bytes[9] === 0x45 && bytes[10] === 0x42 && bytes[11] === 0x50
  ) {
    return { ext: 'webp', mime: 'image/webp' };
  }
  return null;
}

export function validateBannerTargetUrl(rawUrl) {
  if (typeof rawUrl !== 'string') {
    return { valid: false, message: 'آدرس مقصد تبلیغ نامعتبر است.' };
  }
  const trimmed = rawUrl.trim();
  if (!trimmed || trimmed.length > 2048) {
    return { valid: false, message: 'لینک مقصد باید بین ۱ تا ۲۰۴۸ کاراکتر باشد.' };
  }
  if (/[\s\u0000-\u001F\u007F]/.test(trimmed)) {
    return { valid: false, message: 'لینک مقصد نباید دارای فاصله یا کاراکترهای کنترلی باشد.' };
  }
  try {
    const parsed = new URL(trimmed);
    const protocol = (parsed.protocol || '').toLowerCase();
    if (protocol !== 'http:' && protocol !== 'https:') {
      return { valid: false, message: 'لینک مقصد باید با http:// یا https:// آغاز شود.' };
    }
    if (!parsed.hostname || parsed.hostname.length < 2) {
      return { valid: false, message: 'دامنه لینک مقصد معتبر نیست.' };
    }
    if (parsed.username || parsed.password) {
      return { valid: false, message: 'لینک مقصد نباید حاوی اطلاعات ورود در آدرس باشد.' };
    }
    return { valid: true, url: parsed.toString() };
  } catch {
    return { valid: false, message: 'فرمت آدرس مقصد (URL) معتبر نیست.' };
  }
}

export function validateBannerImageUrl(rawUrl) {
  if (typeof rawUrl !== 'string') {
    return { valid: false, message: 'آدرس تصویر بنر نامعتبر است.' };
  }
  const trimmed = rawUrl.trim();
  if (!trimmed || trimmed.length > 2048) {
    return { valid: false, message: 'تصویر بنر الزامی است.' };
  }
  if (/[\u0000-\u001F\u007F]/.test(trimmed)) {
    return { valid: false, message: 'آدرس تصویر بنر نامعتبر است.' };
  }
  if (trimmed.startsWith('/uploads/')) {
    if (trimmed.includes('..')) {
      return { valid: false, message: 'مسیر تصویر نامعتبر است.' };
    }
    return { valid: true, url: trimmed };
  }
  try {
    const parsed = new URL(trimmed);
    const protocol = (parsed.protocol || '').toLowerCase();
    if (protocol !== 'http:' && protocol !== 'https:') {
      return { valid: false, message: 'آدرس تصویر باید لینک معتبر http/https یا مسیر آپلود سرور باشد.' };
    }
    return { valid: true, url: parsed.toString() };
  } catch {
    return { valid: false, message: 'آدرس تصویر بنر معتبر نیست.' };
  }
}

export function parseOptionalBannerDate(rawValue) {
  if (rawValue === undefined || rawValue === null || String(rawValue).trim() === '') {
    return { valid: true, date: null };
  }
  const parsed = new Date(rawValue);
  if (Number.isNaN(parsed.getTime())) {
    return { valid: false, message: 'فرمت تاریخ و زمان نامعتبر است.' };
  }
  return { valid: true, date: parsed };
}

export function normalizeLinkOpenMode(rawMode) {
  const mode = String(rawMode || 'external').trim().toLowerCase();
  if (mode === 'internal' || mode === 'in_app' || mode === 'webview') return 'internal';
  if (mode === 'external' || mode === 'browser') return 'external';
  return null;
}

export function parseBannerPlacements(raw) {
  const allowed = ['home', 'leaderboard', 'profile', 'squares'];
  let list = [];
  if (Array.isArray(raw)) {
    list = raw;
  } else if (typeof raw === 'string') {
    try {
      const parsed = JSON.parse(raw);
      list = Array.isArray(parsed) ? parsed : raw.split(',').map((s) => s.trim());
    } catch {
      list = raw.split(',').map((s) => s.trim());
    }
  }
  const unique = [...new Set(list.map((s) => String(s || '').trim().toLowerCase()).filter((s) => allowed.includes(s)))];
  return unique.length > 0 ? unique : ['home'];
}

export function normalizePlacements(input) {
  const allowed = ['home', 'leaderboard', 'profile', 'squares'];
  let list = [];
  if (Array.isArray(input)) {
    list = input;
  } else if (typeof input === 'string') {
    try {
      const parsed = JSON.parse(input);
      list = Array.isArray(parsed) ? parsed : input.split(',').map((s) => s.trim());
    } catch {
      list = input.split(',').map((s) => s.trim());
    }
  } else if (!input) {
    list = ['home'];
  }
  const unique = [...new Set(list.map((s) => String(s || '').trim().toLowerCase()).filter((s) => allowed.includes(s)))];
  if (unique.length === 0) {
    return { valid: false, message: 'حداقل یک محل نمایش معتبر (home, leaderboard, profile, squares) باید انتخاب شود.' };
  }
  return { valid: true, placements: unique };
}
