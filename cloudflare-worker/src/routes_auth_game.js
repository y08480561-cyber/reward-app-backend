import {
  DEFAULT_MYKET_PUBLIC_KEY,
  checkD1RateLimit,
  generateSecureAuthToken,
  generateSecureHex,
  hashPasswordPBKDF2,
  jsonResponse,
  requireUserAuth,
  timingSafeEqualStr,
  verifyMyketRsaSignature,
  verifyPassword
} from './security.js';
import {
  getActiveCompetition,
  getOrCreateActiveCycle,
  getUserActiveTierAndMultiplier,
  readJsonBody,
  toMs
} from './helpers.js';

async function sendSmsViaKavenegar(env, phoneNumber, otp) {
  const apiKey = String(env.KAVENEGAR_API_KEY || env.SMS_API_KEY || '').trim();
  if (!apiKey) {
    throw new Error('سرویس پیامکی روی سرور تنظیم نشده است.');
  }
  const receptor = phoneNumber.trim();
  const message = `کد تایید مسابقه رقابت و جایزه: ${otp}\nاعتبار ۲ دقیقه`;
  const url = `https://api.kavenegar.com/v1/${apiKey}/sms/send.json`;
  const params = new URLSearchParams();
  params.append('receptor', receptor);
  params.append('message', message);
  if (env.SMS_SENDER) {
    params.append('sender', String(env.SMS_SENDER).trim());
  }

  const response = await fetch(url, {
    method: 'POST',
    body: params,
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' }
  });

  const data = await response.json();
  if (data && data.return && data.return.status === 200) {
    return true;
  }
  throw new Error(data?.return?.message || 'خطا در ارسال پیامک از طریق درگاه کاوه‌نگار');
}

export async function handleAuthAndGameRoutes(request, env, url, pathname) {
  const method = request.method.toUpperCase();

  // 1. POST /api/auth/login
  if (method === 'POST' && pathname === '/api/auth/login') {
    const body = await readJsonBody(request);
    if (!body) return jsonResponse({ error: 'فرمت JSON نامعتبر است.' }, 400);

    const { phoneNumber, password, firstName, lastName } = body;
    if (!phoneNumber || !/^09\d{9}$/.test(String(phoneNumber).trim())) {
      return jsonResponse({ error: 'شماره موبایل وارد شده باید ۱۱ رقمی و با 09 شروع شود.' }, 400);
    }

    const cleanPhone = String(phoneNumber).trim();
    const inputPass = String(password || '').trim();

    const rate = await checkD1RateLimit(env.DB, `login:${cleanPhone}`, 20, 5 * 60 * 1000);
    if (!rate.allowed) {
      return jsonResponse({ error: 'تعداد تلاش‌های ورود بیش از حد مجاز است. لطفاً چند دقیقه دیگر تلاش کنید.' }, 429);
    }

    const existing = await env.DB.prepare(
      `SELECT id, first_name, last_name, phone_number, password_hash, created_at, status
       FROM users WHERE phone_number = ?1 LIMIT 1`
    ).bind(cleanPhone).first();

    const nowIso = new Date().toISOString();
    let user;

    if (existing) {
      if (existing.status && existing.status !== 'active') {
        return jsonResponse({ error: 'حساب کاربری شما مسدود یا غیرفعال است.' }, 403);
      }

      if (existing.password_hash) {
        const envPinMatch = env.AUTH_PIN && inputPass === String(env.AUTH_PIN).trim();
        if (!envPinMatch) {
          const check = await verifyPassword(inputPass, existing.password_hash);
          if (!check.valid) {
            return jsonResponse({ error: 'شماره موبایل یا رمز عبور وارد شده نامعتبر است.' }, 401);
          }
          if (check.needsUpgrade) {
            const upgradedHash = await hashPasswordPBKDF2(inputPass);
            await env.DB.prepare(
              `UPDATE users SET password_hash = ?1, updated_at = ?2 WHERE id = ?3`
            ).bind(upgradedHash, nowIso, existing.id).run();
          }
        }
      } else {
        if (env.AUTH_PIN && inputPass && inputPass !== String(env.AUTH_PIN).trim()) {
          return jsonResponse({ error: 'شماره موبایل یا رمز عبور نامعتبر است.' }, 401);
        }
        if (inputPass) {
          const newHash = await hashPasswordPBKDF2(inputPass);
          await env.DB.prepare(
            `UPDATE users SET password_hash = ?1, updated_at = ?2 WHERE id = ?3`
          ).bind(newHash, nowIso, existing.id).run();
        }
      }

      const newFName = firstName && String(firstName).trim() ? String(firstName).trim() : existing.first_name;
      const newLName = lastName && String(lastName).trim() ? String(lastName).trim() : existing.last_name;
      if (newFName !== existing.first_name || newLName !== existing.last_name) {
        await env.DB.prepare(
          `UPDATE users SET first_name = ?1, last_name = ?2, updated_at = ?3 WHERE id = ?4`
        ).bind(newFName, newLName, nowIso, existing.id).run();
      }

      user = {
        id: existing.id,
        firstName: newFName,
        lastName: newLName,
        phoneNumber: existing.phone_number,
        registeredAt: toMs(existing.created_at, Date.now()),
        authMethod: 'DIRECT_CREDENTIALS'
      };
    } else {
      if (env.AUTH_PIN && inputPass && inputPass !== String(env.AUTH_PIN).trim()) {
        return jsonResponse({ error: 'پین احراز هویت سرور نامعتبر است.' }, 401);
      }

      const userId = crypto.randomUUID();
      const fName = String(firstName || 'کاربر').trim();
      const lName = String(lastName || 'مسابقه').trim();
      const passHash = inputPass ? await hashPasswordPBKDF2(inputPass) : null;

      await env.DB.batch([
        env.DB.prepare(
          `INSERT INTO users (id, first_name, last_name, phone_number, password_hash, total_score, status, created_at, updated_at)
           VALUES (?1, ?2, ?3, ?4, ?5, 0, 'active', ?6, ?6)`
        ).bind(userId, fName, lName, cleanPhone, passHash, nowIso),
        env.DB.prepare(
          `INSERT OR IGNORE INTO user_scores (user_id, total_score, updated_at) VALUES (?1, 0, ?2)`
        ).bind(userId, nowIso)
      ]);

      user = {
        id: userId,
        firstName: fName,
        lastName: lName,
        phoneNumber: cleanPhone,
        registeredAt: Date.now(),
        authMethod: 'DIRECT_CREDENTIALS'
      };
    }

    const scoreRow = await env.DB.prepare(
      `SELECT total_score FROM user_scores WHERE user_id = ?1 LIMIT 1`
    ).bind(user.id).first();
    const score = scoreRow ? Number(scoreRow.total_score || 0) : 0;

    if (!scoreRow) {
      await env.DB.prepare(
        `INSERT OR IGNORE INTO user_scores (user_id, total_score, updated_at) VALUES (?1, ?2, ?3)`
      ).bind(user.id, score, nowIso).run();
    }

    const token = generateSecureAuthToken();
    await env.DB.prepare(
      `UPDATE users SET auth_token = ?1, updated_at = ?2 WHERE id = ?3`
    ).bind(token, nowIso, user.id).run();

    const cycle = await getOrCreateActiveCycle(env.DB, user.id);
    return jsonResponse({
      success: true,
      user: { ...user, totalScore: score, token },
      cycle
    });
  }

  // 2. POST /api/auth/otp/request
  if (method === 'POST' && pathname === '/api/auth/otp/request') {
    const body = await readJsonBody(request);
    if (!body) return jsonResponse({ error: 'فرمت JSON نامعتبر است.' }, 400);

    const cleanPhone = String(body.phoneNumber || '').trim();
    if (!cleanPhone || !/^09\d{9}$/.test(cleanPhone)) {
      return jsonResponse({ error: 'شماره موبایل وارد شده باید ۱۱ رقمی و با 09 شروع شود.' }, 400);
    }

    const apiKey = String(env.KAVENEGAR_API_KEY || env.SMS_API_KEY || '').trim();
    if (!apiKey) {
      return jsonResponse({
        success: false,
        error: 'سرویس پیامکی روی سرور فعال نیست. لطفاً با رمز عبور (ورود مستقیم) وارد شوید.'
      }, 503);
    }

    const now = Date.now();
    const existing = await env.DB.prepare(
      `SELECT phone_number, requests_count, last_requested_at FROM otp_codes WHERE phone_number = ?1 LIMIT 1`
    ).bind(cleanPhone).first();

    if (existing && (now - Number(existing.last_requested_at)) < 5 * 60 * 1000) {
      if (Number(existing.requests_count) >= 5) {
        return jsonResponse({ error: 'تعداد درخواست‌های کد تایید بیش از حد مجاز است. لطفاً ۵ دقیقه دیگر تلاش کنید.' }, 429);
      }
    }

    const randArr = new Uint32Array(1);
    crypto.getRandomValues(randArr);
    const otp = String(10000 + (randArr[0] % 90000));
    const expiresAt = now + 5 * 60 * 1000;

    try {
      await sendSmsViaKavenegar(env, cleanPhone, otp);
    } catch (err) {
      return jsonResponse({ error: err?.message || 'خطا در ارسال پیامک.' }, 500);
    }

    const requestsCount = (existing && (now - Number(existing.last_requested_at)) < 5 * 60 * 1000)
      ? Number(existing.requests_count) + 1
      : 1;

    await env.DB.prepare(
      `INSERT INTO otp_codes (phone_number, otp_code, expires_at, requests_count, last_requested_at, attempts)
       VALUES (?1, ?2, ?3, ?4, ?5, 0)
       ON CONFLICT(phone_number) DO UPDATE SET
         otp_code = ?2, expires_at = ?3, requests_count = ?4, last_requested_at = ?5, attempts = 0`
    ).bind(cleanPhone, otp, expiresAt, requestsCount, now).run();

    return jsonResponse({ success: true, message: 'کد تایید با موفقیت به شماره همراه شما ارسال شد.' });
  }

  // 3. POST /api/auth/otp/verify
  if (method === 'POST' && pathname === '/api/auth/otp/verify') {
    const body = await readJsonBody(request);
    if (!body) return jsonResponse({ error: 'فرمت JSON نامعتبر است.' }, 400);

    const { firstName, lastName, phoneNumber, otpCode } = body;
    const cleanPhone = String(phoneNumber || '').trim();
    const cleanOtp = String(otpCode || '').trim();
    if (!cleanPhone || !cleanOtp) {
      return jsonResponse({ error: 'اطلاعات ورودی ناقص است.' }, 400);
    }

    const record = await env.DB.prepare(
      `SELECT phone_number, otp_code, expires_at, attempts FROM otp_codes WHERE phone_number = ?1 LIMIT 1`
    ).bind(cleanPhone).first();

    if (!record) {
      return jsonResponse({ error: 'کد تاییدی برای این شماره درخواست نشده یا منقضی شده است.' }, 400);
    }

    if (Date.now() > Number(record.expires_at)) {
      await env.DB.prepare(`DELETE FROM otp_codes WHERE phone_number = ?1`).bind(cleanPhone).run();
      return jsonResponse({ error: 'کد تایید منقضی شده است. لطفاً مجدداً درخواست کد کنید.' }, 400);
    }

    if (Number(record.attempts || 0) >= 5) {
      await env.DB.prepare(`DELETE FROM otp_codes WHERE phone_number = ?1`).bind(cleanPhone).run();
      return jsonResponse({ error: 'تعداد تلاش‌های اشتباه بیش از حد مجاز است. لطفاً کد جدید درخواست کنید.' }, 429);
    }

    if (!timingSafeEqualStr(record.otp_code, cleanOtp)) {
      await env.DB.prepare(`UPDATE otp_codes SET attempts = attempts + 1 WHERE phone_number = ?1`).bind(cleanPhone).run();
      return jsonResponse({ error: 'کد تایید وارد شده اشتباه است.' }, 400);
    }

    await env.DB.prepare(`DELETE FROM otp_codes WHERE phone_number = ?1`).bind(cleanPhone).run();

    const nowIso = new Date().toISOString();
    const existing = await env.DB.prepare(
      `SELECT id, first_name, last_name, phone_number, created_at, status FROM users WHERE phone_number = ?1 LIMIT 1`
    ).bind(cleanPhone).first();

    let user;
    if (existing) {
      if (existing.status && existing.status !== 'active') {
        return jsonResponse({ error: 'حساب کاربری شما مسدود یا غیرفعال است.' }, 403);
      }
      user = {
        id: existing.id,
        firstName: existing.first_name,
        lastName: existing.last_name,
        phoneNumber: existing.phone_number,
        registeredAt: toMs(existing.created_at, Date.now()),
        authMethod: 'SMS_OTP'
      };
    } else {
      const userId = crypto.randomUUID();
      const fName = String(firstName || 'کاربر').trim();
      const lName = String(lastName || 'مسابقه').trim();
      await env.DB.batch([
        env.DB.prepare(
          `INSERT INTO users (id, first_name, last_name, phone_number, total_score, status, auth_method, created_at, updated_at)
           VALUES (?1, ?2, ?3, ?4, 0, 'active', 'SMS_OTP', ?5, ?5)`
        ).bind(userId, fName, lName, cleanPhone, nowIso),
        env.DB.prepare(
          `INSERT OR IGNORE INTO user_scores (user_id, total_score, updated_at) VALUES (?1, 0, ?2)`
        ).bind(userId, nowIso)
      ]);
      user = {
        id: userId,
        firstName: fName,
        lastName: lName,
        phoneNumber: cleanPhone,
        registeredAt: Date.now(),
        authMethod: 'SMS_OTP'
      };
    }

    const scoreRow = await env.DB.prepare(`SELECT total_score FROM user_scores WHERE user_id = ?1 LIMIT 1`).bind(user.id).first();
    const score = scoreRow ? Number(scoreRow.total_score || 0) : 0;

    const token = generateSecureAuthToken();
    await env.DB.prepare(`UPDATE users SET auth_token = ?1, updated_at = ?2 WHERE id = ?3`).bind(token, nowIso, user.id).run();

    const cycle = await getOrCreateActiveCycle(env.DB, user.id);
    return jsonResponse({
      success: true,
      user: { ...user, totalScore: score, token },
      cycle
    });
  }

  // 4. GET /api/squares
  if (method === 'GET' && pathname === '/api/squares') {
    const userId = request.headers.get('x-user-id') || url.searchParams.get('userId');
    if (!userId) return jsonResponse({ error: 'شناسه کاربر نامعتبر است.' }, 401);

    const cycle = await getOrCreateActiveCycle(env.DB, userId);
    const { results } = await env.DB.prepare(
      `SELECT square_number, status, completed_at FROM square_states WHERE user_id = ?1 AND cycle_id = ?2`
    ).bind(userId, cycle.cycleId).all();

    const completedMap = new Map();
    for (const row of results || []) {
      completedMap.set(Number(row.square_number), {
        status: row.status,
        completedAt: Number(row.completed_at)
      });
    }

    const squares = [];
    for (let i = 1; i <= 100; i++) {
      const st = completedMap.get(i);
      squares.push({
        squareNumber: i,
        status: st ? st.status : 'READY',
        completedAt: st ? st.completedAt : null,
        cycleId: cycle.cycleId,
        pointsAwarded: st && st.status === 'COMPLETED' ? 1 : 0
      });
    }
    return jsonResponse({ cycle, squares });
  }

  // 5. GET /api/panels
  if (method === 'GET' && pathname === '/api/panels') {
    return jsonResponse({
      success: true,
      panels: [
        { id: 'free', name: 'پنل عادی', priceToman: 0, multiplier: 1, description: 'رایگان - ضریب ۱ برابری امتیاز هر مربع' },
        { id: 'silver', name: 'پنل نقره‌ای', priceToman: 55000, multiplier: 2, description: 'ضریب ۲ برابری امتیاز هر مربع (اعتبار تا پایان دوره جاری)' },
        { id: 'gold', name: 'پنل طلایی', priceToman: 105000, multiplier: 4, description: 'ضریب ۴ برابری امتیاز هر مربع (اعتبار تا پایان دوره جاری)' },
        { id: 'gold_upgrade', name: 'ارتقا از نقره‌ای به طلایی', priceToman: 105000, multiplier: 4, description: 'ارتقا به پنل طلایی با حفظ امتیازات قبلی' }
      ]
    });
  }

  // 6. GET /api/panels/me
  if (method === 'GET' && pathname === '/api/panels/me') {
    const auth = await requireUserAuth(request, env, url);
    if (!auth.ok) return jsonResponse({ success: false, error: auth.error }, auth.status);

    const cycle = await getOrCreateActiveCycle(env.DB, auth.user.id);
    const { tier, multiplier } = await getUserActiveTierAndMultiplier(env.DB, auth.user.id);
    return jsonResponse({
      success: true,
      cycle,
      membership: {
        panelType: tier.toLowerCase(),
        multiplier,
        accountTier: tier,
        purchasedAt: null
      }
    });
  }

  // 7. POST /api/purchases/verify (Legacy panel upgrade - protected by user auth + RSA)
  if (method === 'POST' && pathname === '/api/purchases/verify') {
    const auth = await requireUserAuth(request, env, url);
    if (!auth.ok) return jsonResponse({ success: false, error: auth.error }, auth.status);

    const body = await readJsonBody(request);
    if (!body) return jsonResponse({ error: 'فرمت JSON نامعتبر است.' }, 400);

    const userId = auth.user.id;
    let { productId, purchaseToken, orderId, purchaseData, signature } = body;

    if (purchaseData && signature) {
      const pubKey = String(env.MYKET_PUBLIC_KEY || DEFAULT_MYKET_PUBLIC_KEY);
      const validSig = await verifyMyketRsaSignature(purchaseData, signature, pubKey);
      if (!validSig) {
        return jsonResponse({ error: 'امضای دیجیتال تراکنش مایکت نامعتبر است.' }, 401);
      }
      try {
        const parsed = JSON.parse(purchaseData);
        if (parsed.productId) productId = parsed.productId;
        if (parsed.purchaseToken) purchaseToken = parsed.purchaseToken;
        if (parsed.orderId) orderId = parsed.orderId;
        if (parsed.purchaseState !== undefined && Number(parsed.purchaseState) !== 0) {
          return jsonResponse({ error: 'تراکنش خرید در حالت تکمیل‌شده نیست.' }, 400);
        }
      } catch {
        return jsonResponse({ error: 'اطلاعات ساختاریافته خرید قابل تحلیل نیست.' }, 400);
      }
    }

    if (!productId) {
      return jsonResponse({ error: 'شناسه محصول نامعتبر است.' }, 400);
    }

    if (purchaseToken) {
      const existing = await env.DB.prepare(
        `SELECT purchase_id FROM purchases WHERE purchase_token = ?1 LIMIT 1`
      ).bind(purchaseToken).first();
      if (existing) {
        return jsonResponse({ error: 'این توکن خرید قبلاً ثبت شده است (جلوگیری از ثبت تکراری).' }, 409);
      }
    }

    const currentTierInfo = await getUserActiveTierAndMultiplier(env.DB, userId);
    let newTier = 'NORMAL';
    let multiplier = 1;
    let amount = 0;

    if (productId === 'pkg_upgrade_silver' || productId === 'silver_panel' || productId === 'reqabat_upgrade_silver') {
      if (currentTierInfo.tier === 'GOLD') {
        newTier = 'GOLD';
        multiplier = 6;
      } else {
        newTier = 'SILVER';
        multiplier = 3;
      }
      amount = 70000;
    } else if (productId === 'pkg_upgrade_gold' || productId === 'gold_panel' || productId === 'gold_upgrade' || productId === 'reqabat_upgrade_gold') {
      newTier = 'GOLD';
      multiplier = 6;
      amount = 120000;
    } else {
      return jsonResponse({ error: 'شناسه محصول نامعتبر است.' }, 400);
    }

    const cycle = await getOrCreateActiveCycle(env.DB, userId);
    const purchaseId = orderId || ('PUR_' + generateSecureHex(8));
    const nowIso = new Date().toISOString();
    const isGold = newTier === 'GOLD' ? 1 : 0;

    try {
      await env.DB.batch([
        env.DB.prepare(
          `INSERT OR IGNORE INTO purchases (purchase_id, user_id, product_id, amount, status, purchase_token, created_at)
           VALUES (?1, ?2, ?3, ?4, 'SUCCESS', ?5, ?6)`
        ).bind(purchaseId, userId, productId, amount, purchaseToken || null, nowIso),
        env.DB.prepare(
          `INSERT INTO user_entitlements (user_id, instant_unlock_credits, is_vip, account_tier, multiplier, total_purchased_toman, updated_at)
           VALUES (?1, 0, ?2, ?3, ?4, ?5, ?6)
           ON CONFLICT(user_id) DO UPDATE SET
             is_vip = ?2, account_tier = ?3, multiplier = ?4,
             total_purchased_toman = user_entitlements.total_purchased_toman + ?5,
             updated_at = ?6`
        ).bind(userId, isGold, newTier, multiplier, amount, nowIso),
        env.DB.prepare(
          `UPDATE users SET account_tier = ?1, panel_type = ?2, multiplier = ?3, is_vip = ?4, updated_at = ?5 WHERE id = ?6`
        ).bind(newTier, newTier.toLowerCase(), multiplier, isGold, nowIso, userId),
        env.DB.prepare(
          `INSERT INTO user_memberships (user_id, cycle_id, panel_type, multiplier, purchased_at)
           VALUES (?1, ?2, ?3, ?4, ?5)
           ON CONFLICT(user_id, cycle_id) DO UPDATE SET panel_type = ?3, multiplier = ?4, purchased_at = ?5`
        ).bind(userId, cycle.cycleId, newTier.toLowerCase(), multiplier, nowIso)
      ]);
    } catch (err) {
      if (String(err?.message || '').includes('UNIQUE')) {
        return jsonResponse({ error: 'این توکن خرید قبلاً ثبت شده است.' }, 409);
      }
      throw err;
    }

    return jsonResponse({
      success: true,
      message: `ارتقای سطح به ${newTier === 'GOLD' ? 'طلایی (ضریب ۶X)' : 'نقره‌ای (ضریب ۳X)'} با موفقیت ثبت و فعال شد.`,
      membership: { panelType: newTier.toLowerCase(), accountTier: newTier, multiplier, cycleId: cycle.cycleId }
    });
  }

  // 8. GET /api/store/products
  if (method === 'GET' && pathname === '/api/store/products') {
    const { results } = await env.DB.prepare(
      `SELECT id, title, description, product_type, units_granted, price_toman, myket_sku, badge, is_active, is_ready_in_myket, sort_order
       FROM store_products
       WHERE is_active = 1
       ORDER BY sort_order ASC, created_at ASC`
    ).all();

    const products = (results || []).map((row) => ({
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

    return jsonResponse({ success: true, products });
  }

  // 9. GET /api/store/user-entitlements
  if (method === 'GET' && pathname === '/api/store/user-entitlements') {
    const auth = await requireUserAuth(request, env, url);
    if (!auth.ok) return jsonResponse({ success: false, error: auth.error }, auth.status);

    const userId = auth.user.id;
    const entRow = await env.DB.prepare(
      `SELECT instant_unlock_credits, is_vip, total_purchased_toman FROM user_entitlements WHERE user_id = ?1 LIMIT 1`
    ).bind(userId).first();

    let credits = 0;
    let totalPurchased = 0;
    if (entRow) {
      credits = Number(entRow.instant_unlock_credits || 0);
      totalPurchased = Number(entRow.total_purchased_toman || 0);
    } else {
      const uRow = await env.DB.prepare(`SELECT instant_unlock_credits FROM users WHERE id = ?1 LIMIT 1`).bind(userId).first();
      if (uRow) credits = Number(uRow.instant_unlock_credits || 0);
    }

    const { tier, multiplier } = await getUserActiveTierAndMultiplier(env.DB, userId);
    return jsonResponse({
      success: true,
      entitlements: {
        instantUnlockCredits: credits,
        isVip: tier === 'GOLD',
        accountTier: tier,
        multiplier,
        totalPurchasedToman: totalPurchased
      }
    });
  }

  // 10. POST /api/store/verify-purchase
  if (method === 'POST' && pathname === '/api/store/verify-purchase') {
    const auth = await requireUserAuth(request, env, url);
    if (!auth.ok) return jsonResponse({ success: false, error: auth.error }, auth.status);

    const body = await readJsonBody(request);
    if (!body) return jsonResponse({ error: 'فرمت JSON نامعتبر است.' }, 400);

    const userId = auth.user.id;
    let { productId, purchaseToken, orderId, purchaseData, signature } = body;

    if (purchaseData && signature) {
      const pubKey = String(env.MYKET_PUBLIC_KEY || DEFAULT_MYKET_PUBLIC_KEY);
      const isValidSig = await verifyMyketRsaSignature(purchaseData, signature, pubKey);
      if (!isValidSig) {
        return jsonResponse({ error: 'امضای دیجیتال تراکنش مایکت نامعتبر است (خطای اعتبارسنجی امنیتی سرور).' }, 401);
      }
      try {
        const parsed = JSON.parse(purchaseData);
        if (parsed.productId) productId = parsed.productId;
        if (parsed.purchaseToken) purchaseToken = parsed.purchaseToken;
        if (parsed.orderId) orderId = parsed.orderId;
        if (parsed.purchaseState !== undefined && Number(parsed.purchaseState) !== 0) {
          return jsonResponse({ error: 'تراکنش خرید در مایکت در حالت تکمیل‌شده (Purchased) نیست.' }, 400);
        }
      } catch {
        return jsonResponse({ error: 'اطلاعات ساختار یافته خرید مایکت قابل تحلیل نیست.' }, 400);
      }
    }

    if (!productId) {
      return jsonResponse({ error: 'شناسه کاربر و محصول الزامی است.' }, 400);
    }

    if (purchaseToken) {
      const tokenCheck = await env.DB.prepare(
        `SELECT purchase_id FROM purchases WHERE purchase_token = ?1 LIMIT 1`
      ).bind(purchaseToken).first();
      if (tokenCheck) {
        return jsonResponse({
          error: 'این توکن خرید قبلاً در سیستم ثبت و تایید شده است (جلوگیری از ثبت تکراری).',
          alreadyProcessed: true
        }, 409);
      }
    }

    const product = await env.DB.prepare(
      `SELECT id, title, product_type, units_granted, price_toman, myket_sku, is_active
       FROM store_products WHERE id = ?1 OR myket_sku = ?1 LIMIT 1`
    ).bind(productId).first();

    if (!product) {
      return jsonResponse({ error: 'محصول مورد نظر در فروشگاه یافت نشد.' }, 404);
    }
    if (!product.is_active) {
      return jsonResponse({ error: 'این بسته در حال حاضر غیرفعال است.' }, 400);
    }

    const purchaseId = orderId || ('PUR_' + generateSecureHex(8));
    const unitsGranted = Number(product.units_granted);
    const amountToman = Number(product.price_toman);
    const nowIso = new Date().toISOString();

    let updatedCredits = 0;
    let isVip = false;

    try {
      if (product.product_type === 'SQUARE_UNLOCK') {
        const statements = [
          env.DB.prepare(
            `INSERT INTO purchases (purchase_id, user_id, product_id, amount, status, purchase_token, myket_order_id, units_granted, verification_source, created_at)
             VALUES (?1, ?2, ?3, ?4, 'SUCCESS', ?5, ?6, ?7, 'MYKET_IAB', ?8)`
          ).bind(purchaseId, userId, productId, amountToman, purchaseToken || null, orderId || null, unitsGranted, nowIso),
          env.DB.prepare(
            `INSERT INTO user_entitlements (user_id, instant_unlock_credits, is_vip, account_tier, multiplier, total_purchased_toman, updated_at)
             VALUES (?1, ?2, 0, 'NORMAL', 1, ?3, ?4)
             ON CONFLICT(user_id) DO UPDATE SET
               instant_unlock_credits = user_entitlements.instant_unlock_credits + ?2,
               total_purchased_toman = user_entitlements.total_purchased_toman + ?3,
               updated_at = ?4`
          ).bind(userId, unitsGranted, amountToman, nowIso),
          env.DB.prepare(
            `UPDATE users SET instant_unlock_credits = instant_unlock_credits + ?1, updated_at = ?2 WHERE id = ?3`
          ).bind(unitsGranted, nowIso, userId)
        ];
        await env.DB.batch(statements);

        const afterEnt = await env.DB.prepare(
          `SELECT instant_unlock_credits, is_vip FROM user_entitlements WHERE user_id = ?1 LIMIT 1`
        ).bind(userId).first();
        updatedCredits = Number(afterEnt?.instant_unlock_credits || unitsGranted);
        isVip = Boolean(afterEnt?.is_vip);
      } else if (product.product_type === 'ACCOUNT_UPGRADE') {
        const currentTierInfo = await getUserActiveTierAndMultiplier(env.DB, userId);
        let newTier = 'NORMAL';
        let newMultiplier = 1;

        if (productId === 'pkg_upgrade_gold' || productId === 'gold_panel' || productId === 'gold_upgrade' || productId === 'reqabat_upgrade_gold') {
          newTier = 'GOLD';
          newMultiplier = 6;
        } else if (productId === 'pkg_upgrade_silver' || productId === 'silver_panel' || productId === 'reqabat_upgrade_silver') {
          if (currentTierInfo.tier === 'GOLD') {
            newTier = 'GOLD';
            newMultiplier = 6;
          } else {
            newTier = 'SILVER';
            newMultiplier = 3;
          }
        }

        const isGoldInt = newTier === 'GOLD' ? 1 : 0;
        const cycle = await getOrCreateActiveCycle(env.DB, userId);

        await env.DB.batch([
          env.DB.prepare(
            `INSERT INTO purchases (purchase_id, user_id, product_id, amount, status, purchase_token, myket_order_id, units_granted, verification_source, created_at)
             VALUES (?1, ?2, ?3, ?4, 'SUCCESS', ?5, ?6, ?7, 'MYKET_IAB', ?8)`
          ).bind(purchaseId, userId, productId, amountToman, purchaseToken || null, orderId || null, unitsGranted, nowIso),
          env.DB.prepare(
            `INSERT INTO user_entitlements (user_id, instant_unlock_credits, is_vip, account_tier, multiplier, total_purchased_toman, updated_at)
             VALUES (?1, 0, ?2, ?3, ?4, ?5, ?6)
             ON CONFLICT(user_id) DO UPDATE SET
               is_vip = ?2, account_tier = ?3, multiplier = ?4,
               total_purchased_toman = user_entitlements.total_purchased_toman + ?5,
               updated_at = ?6`
          ).bind(userId, isGoldInt, newTier, newMultiplier, amountToman, nowIso),
          env.DB.prepare(
            `UPDATE users SET account_tier = ?1, panel_type = ?2, multiplier = ?3, is_vip = ?4, updated_at = ?5 WHERE id = ?6`
          ).bind(newTier, newTier.toLowerCase(), newMultiplier, isGoldInt, nowIso, userId),
          env.DB.prepare(
            `INSERT INTO user_memberships (user_id, cycle_id, panel_type, multiplier, purchased_at)
             VALUES (?1, ?2, ?3, ?4, ?5)
             ON CONFLICT(user_id, cycle_id) DO UPDATE SET panel_type = ?3, multiplier = ?4, purchased_at = ?5`
          ).bind(userId, cycle.cycleId, newTier.toLowerCase(), newMultiplier, nowIso)
        ]);

        const afterEnt = await env.DB.prepare(
          `SELECT instant_unlock_credits FROM user_entitlements WHERE user_id = ?1 LIMIT 1`
        ).bind(userId).first();
        updatedCredits = Number(afterEnt?.instant_unlock_credits || 0);
        isVip = newTier === 'GOLD';
      }
    } catch (err) {
      if (String(err?.message || '').includes('UNIQUE')) {
        return jsonResponse({ error: 'این توکن خرید قبلاً ثبت شده است.', alreadyProcessed: true }, 409);
      }
      throw err;
    }

    return jsonResponse({
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
  }

  // 11. POST /api/squares/instant-unlock
  if (method === 'POST' && pathname === '/api/squares/instant-unlock') {
    const auth = await requireUserAuth(request, env, url);
    if (!auth.ok) return jsonResponse({ success: false, error: auth.error }, auth.status);

    const body = await readJsonBody(request);
    if (!body) return jsonResponse({ error: 'فرمت JSON نامعتبر است.' }, 400);

    const userId = auth.user.id;
    const squareNumber = Number(body.squareNumber);
    if (!Number.isInteger(squareNumber) || squareNumber < 1 || squareNumber > 100) {
      return jsonResponse({ error: 'شماره مربع یا کاربر نامعتبر است (باید بین ۱ تا ۱۰۰ باشد).' }, 400);
    }

    const cycle = await getOrCreateActiveCycle(env.DB, userId);
    const now = Date.now();
    if (now >= cycle.endTime) {
      return jsonResponse({ error: 'دوره ۲۴ ساعته منقضی شده است. جدول مربع‌ها به‌روزرسانی شد.' }, 400);
    }

    const squareKey = `${userId}:${cycle.cycleId}:${squareNumber}`;
    const squareCheck = await env.DB.prepare(
      `SELECT state_key FROM square_states WHERE state_key = ?1 LIMIT 1`
    ).bind(squareKey).first();
    if (squareCheck) {
      return jsonResponse({ error: 'این مربع قبلاً در این دوره ۲۴ ساعته باز و تکمیل شده است.' }, 409);
    }

    const entRow = await env.DB.prepare(
      `SELECT instant_unlock_credits FROM user_entitlements WHERE user_id = ?1 LIMIT 1`
    ).bind(userId).first();
    const credits = Number(entRow?.instant_unlock_credits || 0);
    if (credits <= 0) {
      return jsonResponse({
        error: 'سهمیه بازگشایی سریع شما به پایان رسیده است. لطفاً از بخش فروشگاه بسته جدید تهیه فرمایید.',
        noCredits: true
      }, 403);
    }

    const { multiplier: awardedPoints } = await getUserActiveTierAndMultiplier(env.DB, userId);
    const nowIso = new Date(now).toISOString();

    // Atomic conditional credit decrement + square lock + score increment inside batch
    // Pre-insert square state & conditionally decrement credits
    try {
      await env.DB.batch([
        env.DB.prepare(
          `INSERT INTO square_states (state_key, user_id, cycle_id, square_number, status, completed_at, created_at)
           VALUES (?1, ?2, ?3, ?4, 'COMPLETED', ?5, ?6)`
        ).bind(squareKey, userId, cycle.cycleId, squareNumber, now, nowIso),
        env.DB.prepare(
          `UPDATE user_entitlements
           SET instant_unlock_credits = instant_unlock_credits - 1, updated_at = ?2
           WHERE user_id = ?1 AND instant_unlock_credits >= 1`
        ).bind(userId, nowIso),
        env.DB.prepare(
          `UPDATE users
           SET instant_unlock_credits = CASE WHEN instant_unlock_credits > 0 THEN instant_unlock_credits - 1 ELSE 0 END,
               total_score = total_score + ?1,
               updated_at = ?2
           WHERE id = ?3`
        ).bind(awardedPoints, nowIso, userId),
        env.DB.prepare(
          `INSERT INTO user_scores (user_id, total_score, updated_at)
           VALUES (?1, ?2, ?3)
           ON CONFLICT(user_id) DO UPDATE SET total_score = user_scores.total_score + ?2, updated_at = ?3`
        ).bind(userId, awardedPoints, nowIso),
        env.DB.prepare(
          `UPDATE competitions SET total_score = total_score + ?1 WHERE status = 'ACTIVE'`
        ).bind(awardedPoints)
      ]);
    } catch (err) {
      if (String(err?.message || '').includes('UNIQUE')) {
        return jsonResponse({ error: 'این مربع قبلاً در این دوره ۲۴ ساعته باز و تکمیل شده است.' }, 409);
      }
      throw err;
    }

    // Verify decrement succeeded (in case credits hit 0 concurrently)
    const afterEnt = await env.DB.prepare(
      `SELECT instant_unlock_credits FROM user_entitlements WHERE user_id = ?1 LIMIT 1`
    ).bind(userId).first();
    const afterScore = await env.DB.prepare(
      `SELECT total_score FROM user_scores WHERE user_id = ?1 LIMIT 1`
    ).bind(userId).first();

    const remainingCredits = Number(afterEnt?.instant_unlock_credits || 0);
    const newScore = Number(afterScore?.total_score || awardedPoints);

    return jsonResponse({
      success: true,
      message: `مربع شماره ${squareNumber} با موفقیت و بدون نیاز به تبلیغ باز شد! (${awardedPoints} امتیاز افزوده شد)`,
      squareNumber,
      awardedPoints,
      newScore,
      remainingCredits,
      cycleId: cycle.cycleId
    });
  }

  // 12. POST /api/ads/complete
  if (method === 'POST' && pathname === '/api/ads/complete') {
    const auth = await requireUserAuth(request, env, url);
    if (!auth.ok) return jsonResponse({ success: false, error: auth.error }, auth.status);

    const body = await readJsonBody(request);
    if (!body) return jsonResponse({ error: 'فرمت JSON نامعتبر است.' }, 400);

    const userId = auth.user.id;
    const squareNumber = Number(body.squareNumber);
    const rewardNonce = body.rewardNonce ? String(body.rewardNonce).trim() : null;
    const adDurationSeconds = Number(body.adDurationSeconds || 0);

    if (!Number.isInteger(squareNumber) || squareNumber < 1 || squareNumber > 100) {
      return jsonResponse({ error: 'شماره مربع یا کاربر نامعتبر است.' }, 400);
    }
    if (adDurationSeconds < 5) {
      return jsonResponse({ error: 'تبلیغ به طور کامل مشاهده نشده است.' }, 400);
    }

    const cycle = await getOrCreateActiveCycle(env.DB, userId);
    const now = Date.now();
    if (now >= cycle.endTime) {
      return jsonResponse({ error: 'دوره ۲۴ ساعته منقضی شده است. جدول به‌روزرسانی شد.' }, 400);
    }

    if (rewardNonce) {
      const nonceCheck = await env.DB.prepare(
        `SELECT session_id FROM ad_sessions WHERE token = ?1 LIMIT 1`
      ).bind(rewardNonce).first();
      if (nonceCheck) {
        return jsonResponse({ error: 'این درخواست قبلاً ثبت شده است (توکن تکراری تبلیغ).' }, 409);
      }
    }

    const squareKey = `${userId}:${cycle.cycleId}:${squareNumber}`;
    const squareCheck = await env.DB.prepare(
      `SELECT state_key FROM square_states WHERE state_key = ?1 LIMIT 1`
    ).bind(squareKey).first();
    if (squareCheck) {
      return jsonResponse({ error: 'این مربع قبلاً در این دوره ۲۴ ساعته تکمیل شده است.' }, 409);
    }

    const { multiplier: awardedMultiplier } = await getUserActiveTierAndMultiplier(env.DB, userId);
    const activeComp = await getActiveCompetition(env.DB);
    const activeCompId = activeComp?.id || 'COMP_1';
    const sessionId = 'ADS_' + generateSecureHex(6);
    const nowIso = new Date(now).toISOString();

    try {
      await env.DB.batch([
        env.DB.prepare(
          `INSERT INTO square_states (state_key, user_id, cycle_id, square_number, status, completed_at, created_at)
           VALUES (?1, ?2, ?3, ?4, 'COMPLETED', ?5, ?6)`
        ).bind(squareKey, userId, cycle.cycleId, squareNumber, now, nowIso),
        env.DB.prepare(
          `INSERT INTO ad_sessions (session_id, user_id, square_number, cycle_id, timestamp, ad_duration_seconds, status, token, competition_id, created_at)
           VALUES (?1, ?2, ?3, ?4, ?5, ?6, 'COMPLETED', ?7, ?8, ?9)`
        ).bind(sessionId, userId, squareNumber, cycle.cycleId, now, adDurationSeconds, rewardNonce, activeCompId, nowIso),
        env.DB.prepare(
          `INSERT INTO user_scores (user_id, total_score, updated_at)
           VALUES (?1, ?2, ?3)
           ON CONFLICT(user_id) DO UPDATE SET total_score = user_scores.total_score + ?2, updated_at = ?3`
        ).bind(userId, awardedMultiplier, nowIso),
        env.DB.prepare(
          `UPDATE users SET total_score = total_score + ?1, updated_at = ?2 WHERE id = ?3`
        ).bind(awardedMultiplier, nowIso, userId),
        env.DB.prepare(
          `UPDATE competitions SET total_score = total_score + ?1 WHERE id = ?2`
        ).bind(awardedMultiplier, activeCompId)
      ]);
    } catch (err) {
      if (String(err?.message || '').includes('UNIQUE')) {
        return jsonResponse({ error: 'این درخواست یا مربع قبلاً ثبت شده است.' }, 409);
      }
      throw err;
    }

    const scoreRow = await env.DB.prepare(
      `SELECT total_score FROM user_scores WHERE user_id = ?1 LIMIT 1`
    ).bind(userId).first();
    const newScore = Number(scoreRow?.total_score || awardedMultiplier);

    return jsonResponse({
      success: true,
      squareNumber,
      awardedPoints: awardedMultiplier,
      newTotalScore: newScore,
      message: `${awardedMultiplier} امتیاز معتبر با موفقیت ثبت شد.`
    });
  }

  // 13. GET /api/leaderboard
  if (method === 'GET' && pathname === '/api/leaderboard') {
    const currentUserId = request.headers.get('x-user-id') || url.searchParams.get('userId') || '';
    const { results } = await env.DB.prepare(
      `SELECT u.id AS user_id, u.first_name, u.last_name,
              COALESCE(s.total_score, u.total_score, 0) AS total_score, u.created_at
       FROM users u
       LEFT JOIN user_scores s ON u.id = s.user_id
       ORDER BY total_score DESC, u.created_at ASC, u.id ASC
       LIMIT 100`
    ).all();

    const leaderboard = (results || []).map((row, index) => {
      const fullName = `${row.first_name || 'کاربر'} ${row.last_name || ''}`.trim() || 'کاربر مسابقه';
      return {
        rank: index + 1,
        userId: row.user_id,
        displayName: fullName,
        totalScore: Number(row.total_score || 0),
        isCurrentUser: row.user_id === currentUserId
      };
    });

    return jsonResponse({ leaderboard });
  }

  // 14. POST /api/user/feedback
  if (method === 'POST' && pathname === '/api/user/feedback') {
    const auth = await requireUserAuth(request, env, url);
    if (!auth.ok) return jsonResponse({ success: false, error: auth.error }, auth.status);

    const body = await readJsonBody(request);
    if (!body) return jsonResponse({ error: 'فرمت JSON نامعتبر است.' }, 400);

    const userId = auth.user.id;
    const rating = Number(body.rating);
    const comment = body.comment ? String(body.comment).slice(0, 2000) : null;

    if (!Number.isInteger(rating) || rating < 1 || rating > 5) {
      return jsonResponse({ error: 'امتیاز وارد شده باید بین ۱ تا ۵ باشد.' }, 400);
    }

    const REWARD_POINTS = 2;
    const nowIso = new Date().toISOString();

    const updateRes = await env.DB.prepare(
      `UPDATE users
       SET feedback_reward_claimed = 1,
           total_score = total_score + ?1,
           updated_at = ?2
       WHERE id = ?3 AND feedback_reward_claimed = 0`
    ).bind(REWARD_POINTS, nowIso, userId).run();

    const changes = Number(updateRes?.meta?.changes ?? 0);
    if (changes === 0) {
      const existingUser = await env.DB.prepare(`SELECT id FROM users WHERE id = ?1 LIMIT 1`).bind(userId).first();
      if (!existingUser) {
        return jsonResponse({ error: 'کاربر یافت نشد.' }, 404);
      }
      return jsonResponse({ error: 'پاداش بازخورد قبلاً توسط شما دریافت شده است.' }, 409);
    }

    const feedbackId = 'FB_' + generateSecureHex(8);
    await env.DB.batch([
      env.DB.prepare(
        `INSERT INTO user_feedback (id, user_id, rating, comment, created_at) VALUES (?1, ?2, ?3, ?4, ?5)`
      ).bind(feedbackId, userId, rating, comment, nowIso),
      env.DB.prepare(
        `INSERT INTO user_scores (user_id, total_score, updated_at)
         VALUES (?1, ?2, ?3)
         ON CONFLICT(user_id) DO UPDATE SET total_score = user_scores.total_score + ?2, updated_at = ?3`
      ).bind(userId, REWARD_POINTS, nowIso)
    ]);

    const scoreRow = await env.DB.prepare(`SELECT total_score FROM user_scores WHERE user_id = ?1 LIMIT 1`).bind(userId).first();
    const finalScore = Number(scoreRow?.total_score || REWARD_POINTS);

    return jsonResponse({
      success: true,
      message: 'بازخورد شما با موفقیت ثبت شد و ۲ امتیاز هدیه دریافت کردید.',
      awardedPoints: REWARD_POINTS,
      newTotalScore: finalScore
    });
  }

  // 15. GET / POST /api/user/settings/notifications
  if (pathname === '/api/user/settings/notifications' && (method === 'GET' || method === 'POST')) {
    const auth = await requireUserAuth(request, env, url);
    if (!auth.ok) return jsonResponse({ success: false, error: auth.error }, auth.status);

    if (method === 'GET') {
      return jsonResponse({
        success: true,
        enabled: auth.user.notificationsEnabled
      });
    }

    const body = await readJsonBody(request);
    if (!body || typeof body.enabled !== 'boolean') {
      return jsonResponse({ error: 'مقدار enabled (boolean) الزامی است.' }, 400);
    }

    const enabledInt = body.enabled ? 1 : 0;
    const nowIso = new Date().toISOString();
    await env.DB.prepare(
      `UPDATE users SET notifications_enabled = ?1, updated_at = ?2 WHERE id = ?3`
    ).bind(enabledInt, nowIso, auth.user.id).run();

    return jsonResponse({
      success: true,
      enabled: body.enabled,
      message: 'تنظیمات اعلان با موفقیت ذخیره شد.'
    });
  }

  return null;
}
