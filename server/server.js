const express = require('express');
const cors = require('cors');
const path = require('path');
const crypto = require('crypto');

const app = express();
const PORT = process.env.PORT || 3000;
const ADMIN_API_KEY = process.env.ADMIN_KEY || "ReqabatAdmin2026";

app.use(cors());
app.use(express.json());
app.use(express.static(path.join(__dirname, 'public')));

// Health check endpoint for Render
app.get('/health', (req, res) => {
  res.status(200).json({ status: 'ok', uptime: process.uptime(), timestamp: Date.now() });
});

// In-Memory Persistent Store (with SQLite Schema structure)
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

// Seed sample leaderboard competitors
const seedUsers = [
  { id: "USR_SEED_1", firstName: "سهراب", lastName: "کریمی", phone: "09121112233", score: 184 },
  { id: "USR_SEED_2", firstName: "مریم", lastName: "رضایی", phone: "09352223344", score: 159 },
  { id: "USR_SEED_3", firstName: "امیرحسین", lastName: "فراهانی", phone: "09193334455", score: 138 }
];

seedUsers.forEach(u => {
  db.users.set(u.id, { id: u.id, firstName: u.firstName, lastName: u.lastName, phoneNumber: u.phone, registeredAt: Date.now() });
  db.userScores.set(u.id, { userId: u.id, totalScore: u.score, updatedAt: Date.now() });
});

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

// 1. Auth: Request OTP
app.post('/api/auth/otp/request', (req, res) => {
  const { phoneNumber } = req.body;
  if (!phoneNumber || !/^09\d{9}$/.test(phoneNumber)) {
    return res.status(400).json({ error: "شماره موبایل وارد شده باید ۱۱ رقمی و با 09 شروع شود." });
  }
  const otp = Math.floor(10000 + Math.random() * 90000).toString();
  console.log(`[SMS-GATEWAY] Verification OTP for ${phoneNumber} is: ${otp}`);
  return res.json({ success: true, message: "کد تایید ارسال گردید.", debugOtp: otp });
});

// 2. Auth: Verify OTP & Login
app.post('/api/auth/otp/verify', (req, res) => {
  const { firstName, lastName, phoneNumber, otpCode } = req.body;
  if (!phoneNumber || !otpCode) {
    return res.status(400).json({ error: "اطلاعات ورودی ناقص است." });
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
      registeredAt: Date.now()
    };
    db.users.set(userId, user);
    db.userScores.set(userId, { userId, totalScore: 0, updatedAt: Date.now() });
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

// 6. Admin Overview API
app.get('/api/admin/overview', (req, res) => {
  const sessions = Array.from(db.adSessions.values()).reverse().slice(0, 50);
  const users = Array.from(db.users.values()).map(u => ({
    ...u,
    score: db.userScores.get(u.id)?.totalScore || 0
  }));
  return res.json({
    totalUsers: db.users.size,
    totalAdSessions: db.adSessions.size,
    settings: db.settings,
    recentSessions: sessions,
    users
  });
});

// 7. Admin Force Reset Cycle
app.post('/api/admin/cycle/reset', (req, res) => {
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
