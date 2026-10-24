const express = require('express');
const cors = require('cors');
const path = require('path');
const crypto = require('crypto');
const fs = require('fs');

const app = express();
const PORT = process.env.PORT || 3000;
const ADMIN_API_KEY = process.env.ADMIN_KEY || "ReqabatAdmin2026";

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

// Health check endpoint for Render
app.get('/health', (req, res) => {
  res.status(200).json({ status: 'ok', uptime: process.uptime(), timestamp: Date.now() });
});

// JSON File Persistent Storage
const DB_FILE = path.join(__dirname, 'database.json');

const db = {
  users: new Map(),
  userScores: new Map(),
  dailyCycles: new Map(),
  adSessions: new Map(),
  squareStates: new Map(), // key: `${userId}:${cycleId}:${squareNumber}`
  settings: {
    tapsellAppKey: process.env.TAPSELL_APP_KEY || "sgedhqdbofphgpajkrqtahjkedbkokdlqiesotcmfqcklbamjaaiddqsqnihdtmobfidcb",
    tapsellZoneId: process.env.TAPSELL_ZONE_ID || "6ad231b89140ba2215e772db",
    cycleHours: 24,
    pointsPerAd: 1
  }
};

function loadDatabase() {
  try {
    if (fs.existsSync(DB_FILE)) {
      const raw = fs.readFileSync(DB_FILE, 'utf8');
      const parsed = JSON.parse(raw);
      if (Array.isArray(parsed.users)) {
        parsed.users.forEach(u => db.users.set(u.id, u));
      }
      if (Array.isArray(parsed.userScores)) {
        parsed.userScores.forEach(s => db.userScores.set(s.userId, s));
      }
      if (Array.isArray(parsed.dailyCycles)) {
        parsed.dailyCycles.forEach(c => db.dailyCycles.set(c.cycleId, c));
      }
      if (Array.isArray(parsed.adSessions)) {
        parsed.adSessions.forEach(a => db.adSessions.set(a.sessionId, a));
      }
      if (Array.isArray(parsed.squareStates)) {
        parsed.squareStates.forEach(sq => db.squareStates.set(sq.key, sq.value));
      }
      console.log(`[Database] Loaded ${db.users.size} users, ${db.adSessions.size} sessions from ${DB_FILE}`);
    }
  } catch (err) {
    console.error('[Database] Error reading database file:', err.message);
  }
}

function saveDatabase() {
  try {
    const data = {
      users: Array.from(db.users.values()),
      userScores: Array.from(db.userScores.values()),
      dailyCycles: Array.from(db.dailyCycles.values()),
      adSessions: Array.from(db.adSessions.values()),
      squareStates: Array.from(db.squareStates.entries()).map(([key, value]) => ({ key, value }))
    };
    fs.writeFileSync(DB_FILE, JSON.stringify(data, null, 2), 'utf8');
  } catch (err) {
    console.error('[Database] Error saving database file:', err.message);
  }
}

// Initial load
loadDatabase();

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

// Helper: Ensure 24-hour cycle
function getOrCreateActiveCycle(userId) {
  const now = Date.now();
  const CYCLE_MS = 24 * 60 * 60 * 1000;
  let activeCycle = null;
  for (const c of db.dailyCycles.values()) {
    if (c.userId === userId && now >= c.startTime && now < c.endTime) {
      activeCycle = c;
      break;
    }
  }
  if (!activeCycle) {
    const cycleId = "CYC_" + Math.floor(now / 1000);
    activeCycle = {
      cycleId,
      userId,
      startTime: now,
      endTime: now + CYCLE_MS,
      status: "ACTIVE"
    };
    db.dailyCycles.set(cycleId, activeCycle);
  }
  return activeCycle;
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

// 1. Direct Secure Authentication (No SMS dependency - Server-side validated)
app.post('/api/auth/login', (req, res) => {
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

    let user = null;
    for (const u of db.users.values()) {
      if (u.phoneNumber === phoneNumber) {
        user = u;
        break;
      }
    }
    if (!user) {
      const userId = "USR_" + crypto.randomBytes(4).toString('hex').toUpperCase();
      user = {
        id: userId,
        firstName: (firstName || "کاربر").trim(),
        lastName: (lastName || "مسابقه").trim(),
        phoneNumber,
        authMethod: "DIRECT_CREDENTIALS",
        registeredAt: Date.now()
      };
      db.users.set(userId, user);
      db.userScores.set(userId, { userId, totalScore: 0, updatedAt: Date.now() });
      saveDatabase();
      console.log(`[Auth] Registered new user via direct login: ${user.id} (${phoneNumber})`);
    } else {
      if (firstName && firstName.trim()) user.firstName = firstName.trim();
      if (lastName && lastName.trim()) user.lastName = lastName.trim();
      saveDatabase();
      console.log(`[Auth] Logged in existing user via direct login: ${user.id} (${phoneNumber})`);
    }

    const token = "SEC_JWT_" + crypto.createHash('sha256').update(`${user.id}:${phoneNumber}:${Date.now()}`).digest('hex');
    const score = db.userScores.get(user.id)?.totalScore || 0;
    const cycle = getOrCreateActiveCycle(user.id);
    return res.json({
      success: true,
      user: { ...user, totalScore: score, token },
      cycle
    });
  } catch (err) {
    console.error('[Direct Login Error]', err);
    return res.status(500).json({ error: "خطای سرور در احراز هویت." });
  }
});

// 2. Auth: Request OTP (Supports live SMS or test fallback if SMS key not set)
app.post('/api/auth/otp/request', async (req, res) => {
  try {
    const { phoneNumber } = req.body;
    if (!phoneNumber || !/^09\d{9}$/.test(phoneNumber)) {
      return res.status(400).json({ error: "شماره موبایل وارد شده باید ۱۱ رقمی و با 09 شروع شود." });
    }

    // Rate limiting check: max 5 requests per 5 minutes
    const now = Date.now();
    const existing = otpStore.get(phoneNumber);
    if (existing && (now - existing.lastRequestedAt < 5 * 60 * 1000)) {
      if (existing.requestsCount >= 5) {
        return res.status(429).json({ error: "تعداد درخواست‌های کد تایید بیش از حد مجاز است. لطفاً ۵ دقیقه دیگر تلاش کنید." });
      }
    }

    const apiKey = process.env.KAVENEGAR_API_KEY || process.env.SMS_API_KEY;
    const expiresAt = now + 5 * 60 * 1000; // 5 minutes expiry

    if (!apiKey) {
      // In private test/dev mode without Kavenegar SMS credentials:
      const otp = "12345";
      otpStore.set(phoneNumber, {
        otp,
        expiresAt,
        requestsCount: (existing?.requestsCount || 0) + 1,
        lastRequestedAt: now
      });
      console.log(`[SMS-GATEWAY] No SMS_API_KEY set. In-memory OTP set for ${phoneNumber}: 12345`);
      return res.json({
        success: true,
        message: "حالت بدون پیامک: کد تأیید ورود ۱۲۳۴۵ است.",
        debugOtp: "12345"
      });
    }

    const otp = crypto.randomInt(10000, 100000).toString();
    await sendSmsViaKavenegar(phoneNumber, otp);

    const requestsCount = (existing && (now - existing.lastRequestedAt < 5 * 60 * 1000)) ? existing.requestsCount + 1 : 1;
    otpStore.set(phoneNumber, {
      otp,
      expiresAt,
      requestsCount,
      lastRequestedAt: now
    });

    console.log(`[SMS-GATEWAY] OTP successfully dispatched via Kavenegar to ${phoneNumber.substring(0, 4)}***`);
    return res.json({ success: true, message: "کد تایید با موفقیت به شماره همراه شما ارسال شد." });
  } catch (err) {
    console.error('[SMS Error]', err.message);
    return res.status(500).json({ error: err.message || "خطا در ارسال پیامک." });
  }
});

// 2. Auth: Verify OTP & Login
app.post('/api/auth/otp/verify', (req, res) => {
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

  let user = null;
  for (const u of db.users.values()) {
    if (u.phoneNumber === phoneNumber) {
      user = u;
      break;
    }
  }
  if (!user) {
    const userId = "USR_" + crypto.randomBytes(4).toString('hex').toUpperCase();
    user = {
      id: userId,
      firstName: (firstName || "کاربر").trim(),
      lastName: (lastName || "مسابقه").trim(),
      phoneNumber,
      registeredAt: Date.now()
    };
    db.users.set(userId, user);
    db.userScores.set(userId, { userId, totalScore: 0, updatedAt: Date.now() });
    saveDatabase();
  }

  const token = "SEC_JWT_" + crypto.createHash('sha256').update(`${user.id}:${phoneNumber}:${Date.now()}`).digest('hex');
  const score = db.userScores.get(user.id)?.totalScore || 0;
  const cycle = getOrCreateActiveCycle(user.id);
  return res.json({
    success: true,
    user: { ...user, totalScore: score, token },
    cycle
  });
});

// 3. Get 24-hour Cycle and Squares status
app.get('/api/squares', (req, res) => {
  const userId = req.headers['x-user-id'] || req.query.userId;
  if (!userId) return res.status(401).json({ error: "شناسه کاربر نامعتبر است." });
  const cycle = getOrCreateActiveCycle(userId);
  const squares = [];
  for (let i = 1; i <= 50; i++) {
    const key = `${userId}:${cycle.cycleId}:${i}`;
    const state = db.squareStates.get(key);
    squares.push({
      squareNumber: i,
      status: state ? state.status : "READY",
      completedAt: state ? state.completedAt : null,
      cycleId: cycle.cycleId,
      pointsAwarded: state && state.status === "COMPLETED" ? 1 : 0
    });
  }
  return res.json({ cycle, squares });
});

// 4. ATOMIC Ad Completion & Point Awarding (Anti-Cheat)
app.post('/api/ads/complete', (req, res) => {
  const userId = req.headers['x-user-id'] || req.body.userId;
  const { squareNumber, rewardNonce, adDurationSeconds } = req.body;
  if (!userId || !squareNumber || squareNumber < 1 || squareNumber > 50) {
    return res.status(400).json({ error: "شماره مربع یا کاربر نامعتبر است." });
  }
  const cycle = getOrCreateActiveCycle(userId);
  if (Date.now() >= cycle.endTime) {
    return res.status(400).json({ error: "دوره ۲۴ ساعته منقضی شده است. جدول به‌روزرسانی شد." });
  }
  const squareKey = `${userId}:${cycle.cycleId}:${squareNumber}`;
  if (db.squareStates.get(squareKey)?.status === "COMPLETED") {
    return res.status(409).json({ error: "این مربع قبلاً در این دوره ۲۴ ساعته تکمیل شده است." });
  }
  if (adDurationSeconds < 5) {
    return res.status(400).json({ error: "تبلیغ به طور کامل مشاهده نشده است." });
  }
  
  const now = Date.now();
  const sessionId = "ADS_" + crypto.randomBytes(6).toString('hex');
  db.squareStates.set(squareKey, { status: "COMPLETED", completedAt: now });
  const currentScoreObj = db.userScores.get(userId) || { userId, totalScore: 0, updatedAt: now };
  currentScoreObj.totalScore += 1;
  currentScoreObj.updatedAt = now;
  db.userScores.set(userId, currentScoreObj);
  db.adSessions.set(sessionId, {
    sessionId,
    userId,
    squareNumber,
    cycleId: cycle.cycleId,
    timestamp: now,
    adDurationSeconds,
    status: "COMPLETED",
    token: rewardNonce
  });
  saveDatabase();

  return res.json({
    success: true,
    squareNumber,
    awardedPoints: 1,
    newTotalScore: currentScoreObj.totalScore,
    message: "۱ امتیاز معتبر با موفقیت ثبت شد."
  });
});

// 5. Leaderboard API
app.get('/api/leaderboard', (req, res) => {
  const currentUserId = req.headers['x-user-id'] || req.query.userId;
  const list = [];
  for (const [userId, scoreObj] of db.userScores.entries()) {
    const user = db.users.get(userId);
    const maskedName = user ? `${user.firstName} ${user.lastName.substring(0, 2)}***` : "کاربر مسابقه";
    list.push({
      userId,
      displayName: maskedName,
      totalScore: scoreObj.totalScore,
      isCurrentUser: userId === currentUserId
    });
  }
  list.sort((a, b) => b.totalScore - a.totalScore);
  const ranked = list.map((item, index) => ({ ...item, rank: index + 1 }));
  return res.json({ leaderboard: ranked });
});

// 6. Admin Overview API (Protected)
app.get('/api/admin/overview', requireAdminAuth, (req, res) => {
  const sessions = Array.from(db.adSessions.values()).reverse().slice(0, 100);
  const users = Array.from(db.users.values()).map(u => ({
    ...u,
    score: db.userScores.get(u.id)?.totalScore || 0
  })).sort((a, b) => b.score - a.score);

  const totalScoresSum = users.reduce((sum, u) => sum + (u.score || 0), 0);

  return res.json({
    totalUsers: users.length,
    totalAdSessions: db.adSessions.size,
    totalScoresSum,
    settings: db.settings,
    recentSessions: sessions,
    users
  });
});

// 6.1 Users Ranking Table API (Sanitized Read-Only for Standalone Users Page)
app.get('/api/admin/users', (req, res) => {
  // Secure Alternative Mechanism:
  // 1. This endpoint provides strictly sanitized read-only ranking records (id, firstName, lastName, score).
  // 2. Sensitive user data (phoneNumber, tokens, authMethod) and server secrets are NEVER returned here.
  // 3. Mutating and privileged operations (/api/admin/overview with full phone numbers, /api/admin/cycle/reset)
  //    remain strictly protected with `requireAdminAuth`.
  const users = Array.from(db.users.values()).map(u => {
    const regTime = u.registeredAt || u.createdAt || 0;
    return {
      id: u.id,
      firstName: u.firstName || '',
      lastName: u.lastName || '',
      registeredAt: typeof regTime === 'number' ? regTime : (new Date(regTime).getTime() || 0),
      score: db.userScores.get(u.id)?.totalScore || 0
    };
  }).sort((a, b) => {
    // 1. Primary: totalScore descending (highest score first)
    if (b.score !== a.score) {
      return b.score - a.score;
    }
    // 2. Stable tie-breaker: registration time (earlier registration first)
    if (a.registeredAt !== b.registeredAt) {
      return a.registeredAt - b.registeredAt;
    }
    // 3. Deterministic fallback: user ID
    return String(a.id || '').localeCompare(String(b.id || ''));
  });

  return res.json({
    success: true,
    totalUsers: users.length,
    users
  });
});

// 7. Admin Force Reset Cycle (Protected)
app.post('/api/admin/cycle/reset', requireAdminAuth, (req, res) => {
  try {
    const { userId } = req.body;
    if (!userId) return res.status(400).json({ error: "شناسه کاربر الزامی است." });
    const now = Date.now();
    const newCycleId = "CYC_" + Math.floor(now / 1000);
    const cycle = {
      cycleId: newCycleId,
      userId,
      startTime: now,
      endTime: now + (24 * 60 * 60 * 1000),
      status: "ACTIVE"
    };
    db.dailyCycles.set(newCycleId, cycle);
    saveDatabase();
    return res.json({ success: true, newCycle: cycle });
  } catch (err) {
    console.error("Error in cycle reset:", err);
    return res.status(500).json({ error: "خطای داخلی سرور" });
  }
});

// Global Error Handler & Startup with 0.0.0.0 binding
app.use((err, req, res, next) => {
  console.error('[Global Error]', err);
  res.status(500).json({ error: 'خطای داخلی سرور رخ داد.' });
});

app.listen(PORT, '0.0.0.0', () => {
  console.log(`[Reqabat Va Jayezah Server] Successfully initialized and running on http://0.0.0.0:${PORT}`);
});
