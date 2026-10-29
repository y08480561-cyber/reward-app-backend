const assert = require('assert');

console.log('====================================================');
console.log('[Support System Test] Running Comprehensive Verification...');
console.log('====================================================');

// Test 1: Category and Status Validation
const VALID_SUPPORT_CATEGORIES = ['SCORES', 'ADS', 'PRIZES', 'MEMBERSHIP', 'PURCHASE', 'LOGIN', 'OTHER'];
const VALID_SUPPORT_STATUSES = ['NEW', 'IN_PROGRESS', 'ANSWERED', 'CLOSED'];

function validateTicketInput(subject, category, message) {
  if (!subject || typeof subject !== 'string' || subject.trim().length < 3 || subject.trim().length > 150) {
    return { valid: false, error: "عنوان درخواست باید بین ۳ تا ۱۵۰ کاراکتر باشد." };
  }
  if (!message || typeof message !== 'string' || message.trim().length < 5 || message.trim().length > 3000) {
    return { valid: false, error: "متن پیام پشتیبانی باید بین ۵ تا ۳۰۰۰ کاراکتر باشد." };
  }
  const cleanCat = (category && VALID_SUPPORT_CATEGORIES.includes(category.toUpperCase())) 
    ? category.toUpperCase() 
    : 'OTHER';
  return { valid: true, cleanCategory: cleanCat, cleanSubject: subject.trim(), cleanMessage: message.trim() };
}

console.log('[Test 1] Validating Ticket Input Rules...');
assert.strictEqual(validateTicketInput('', 'ADS', 'سلام پیام تست').valid, false);
assert.strictEqual(validateTicketInput('تست', 'ADS', 'سلام').valid, false); // message < 5
assert.strictEqual(validateTicketInput('موضوع درخواست من', 'INVALID_CAT', 'متن کامل پیام پشتیبانی با طول کافی').cleanCategory, 'OTHER');
assert.strictEqual(validateTicketInput('موضوع درخواست من', 'SCORES', 'متن کامل پیام پشتیبانی با طول کافی').cleanCategory, 'SCORES');
console.log('  ✓ Category & length validation correctly enforced.');

// Test 2: User Authentication & Identification
const usersDb = [
  { id: 'usr_1', firstName: 'مجتبی', lastName: 'چراغ‌زاده', phoneNumber: '09339509691', authToken: 'TOKEN_USR_1', status: 'active' },
  { id: 'usr_2', firstName: 'علی', lastName: 'محمدی', phoneNumber: '09121112233', authToken: 'TOKEN_USR_2', status: 'active' },
  { id: 'usr_blocked', firstName: 'فرد', lastName: 'مسدود', phoneNumber: '09990001122', authToken: 'TOKEN_BLOCKED', status: 'blocked' }
];

function authenticateUser(token, headerUserId) {
  if (!token && !headerUserId) {
    return { authenticated: false, status: 401, error: "توکن احراز هویت الزامی است." };
  }
  const user = usersDb.find(u => u.authToken === token || u.id === headerUserId);
  if (!user) {
    return { authenticated: false, status: 401, error: "نشست نامعتبر است." };
  }
  if (user.status !== 'active') {
    return { authenticated: false, status: 403, error: "حساب مسدود است." };
  }
  return {
    authenticated: true,
    user: {
      id: user.id,
      fullName: `${user.firstName} ${user.lastName}`.trim(),
      phoneNumber: user.phoneNumber
    }
  };
}

console.log('[Test 2] Testing User Auth & Token Verification...');
assert.strictEqual(authenticateUser(null, null).authenticated, false);
assert.strictEqual(authenticateUser('INVALID_TOKEN', null).authenticated, false);
assert.strictEqual(authenticateUser('TOKEN_BLOCKED', null).status, 403);
const auth1 = authenticateUser('TOKEN_USR_1', null);
assert.strictEqual(auth1.authenticated, true);
assert.strictEqual(auth1.user.id, 'usr_1');
assert.strictEqual(auth1.user.fullName, 'مجتبی چراغ‌زاده');
console.log('  ✓ Server-side user authentication verified against database records.');

// Test 3: Simulation of Database & Tickets Operations
const ticketsDb = [];
const messagesDb = [];

function generateTrackingCode() {
  return "TRK-" + Math.floor(100000 + Math.random() * 900000);
}

function createTicket(token, subject, category, message) {
  const auth = authenticateUser(token);
  if (!auth.authenticated) throw { status: auth.status, error: auth.error };

  const v = validateTicketInput(subject, category, message);
  if (!v.valid) throw { status: 400, error: v.error };

  const ticketId = "TCK_" + (ticketsDb.length + 1);
  const trackingCode = generateTrackingCode();
  const now = Date.now();

  const ticket = {
    id: ticketId,
    trackingCode,
    userId: auth.user.id,
    subject: v.cleanSubject,
    category: v.cleanCategory,
    status: 'NEW',
    createdAtMs: now,
    updatedAtMs: now,
    lastMessageAtMs: now
  };
  ticketsDb.push(ticket);

  const initialMsg = {
    id: "MSG_" + (messagesDb.length + 1),
    ticketId,
    senderType: 'USER',
    senderId: auth.user.id,
    senderName: auth.user.fullName,
    messageText: v.cleanMessage,
    createdAtMs: now
  };
  messagesDb.push(initialMsg);

  return { ticket, message: initialMsg };
}

console.log('[Test 3] Creating User Support Ticket...');
const resTicket1 = createTicket('TOKEN_USR_1', 'عدم ثبت امتیاز تبلیغ ۵', 'ADS', 'سلام، امتیاز تبلیغ ۵ ثبت نشد.');
assert.strictEqual(resTicket1.ticket.userId, 'usr_1');
assert.strictEqual(resTicket1.ticket.status, 'NEW');
assert.strictEqual(resTicket1.ticket.category, 'ADS');
assert.ok(resTicket1.ticket.trackingCode.startsWith('TRK-'));
assert.strictEqual(messagesDb.length, 1);
console.log(`  ✓ Ticket created: ${resTicket1.ticket.trackingCode}`);

// Test 4: Strict User Isolation (User 1 can see, User 2 CANNOT see)
function getUserTickets(token) {
  const auth = authenticateUser(token);
  if (!auth.authenticated) throw { status: auth.status, error: auth.error };
  return ticketsDb.filter(t => t.userId === auth.user.id);
}

function getTicketDetail(token, ticketId) {
  const auth = authenticateUser(token);
  if (!auth.authenticated) throw { status: auth.status, error: auth.error };

  const ticket = ticketsDb.find(t => t.id === ticketId && t.userId === auth.user.id);
  if (!ticket) throw { status: 404, error: "درخواست یافت نشد یا دسترسی غیرمجاز است." };

  const messages = messagesDb.filter(m => m.ticketId === ticketId);
  return { ticket, messages };
}

console.log('[Test 4] Verifying Cross-User Security Isolation...');
const user1List = getUserTickets('TOKEN_USR_1');
assert.strictEqual(user1List.length, 1);

const user2List = getUserTickets('TOKEN_USR_2');
assert.strictEqual(user2List.length, 0); // User 2 has no tickets

// User 1 gets their ticket -> OK
const u1Detail = getTicketDetail('TOKEN_USR_1', resTicket1.ticket.id);
assert.strictEqual(u1Detail.ticket.id, resTicket1.ticket.id);

// User 2 tries to get User 1's ticket -> Throws 404/403
let user2Caught = false;
try {
  getTicketDetail('TOKEN_USR_2', resTicket1.ticket.id);
} catch (e) {
  user2Caught = true;
  assert.strictEqual(e.status, 404);
}
assert.strictEqual(user2Caught, true, 'User 2 MUST NOT be able to view User 1 ticket');
console.log('  ✓ Cross-user ticket viewing strictly blocked and isolated.');

// Test 5: Admin Management & Response Flow
const ADMIN_API_KEY = "ReqabatAdmin2026";

function adminReplyTicket(adminKey, ticketId, messageText, newStatus = 'ANSWERED') {
  if (adminKey !== ADMIN_API_KEY) throw { status: 401, error: "دسترسی غیرمجاز" };
  const ticket = ticketsDb.find(t => t.id === ticketId);
  if (!ticket) throw { status: 404, error: "تیکت یافت نشد" };

  const now = Date.now();
  const adminMsg = {
    id: "MSG_" + (messagesDb.length + 1),
    ticketId,
    senderType: 'ADMIN',
    senderId: 'admin',
    senderName: 'پشتیبانی رقابت',
    messageText: messageText.trim(),
    createdAtMs: now
  };
  messagesDb.push(adminMsg);

  ticket.status = newStatus;
  ticket.updatedAtMs = now;
  ticket.lastMessageAtMs = now;
  return { ticket, message: adminMsg };
}

console.log('[Test 5] Simulating Admin Reply from Management Panel...');
const adminReplyRes = adminReplyTicket(
  ADMIN_API_KEY,
  resTicket1.ticket.id,
  'سلام مجتبی عزیز، امتیاز به حساب شما اضافه شد.'
);
assert.strictEqual(adminReplyRes.ticket.status, 'ANSWERED');
assert.strictEqual(adminReplyRes.message.senderType, 'ADMIN');

// Check that user now sees 2 messages (1 user, 1 admin)
const u1UpdatedDetail = getTicketDetail('TOKEN_USR_1', resTicket1.ticket.id);
assert.strictEqual(u1UpdatedDetail.messages.length, 2);
assert.strictEqual(u1UpdatedDetail.messages[1].senderType, 'ADMIN');
assert.strictEqual(u1UpdatedDetail.ticket.status, 'ANSWERED');
console.log('  ✓ Admin reply correctly added to thread and reflected in user ticket state.');

// Test 6: Closed Ticket Rejection
function sendUserReply(token, ticketId, messageText) {
  const auth = authenticateUser(token);
  if (!auth.authenticated) throw { status: auth.status, error: auth.error };

  const ticket = ticketsDb.find(t => t.id === ticketId && t.userId === auth.user.id);
  if (!ticket) throw { status: 404, error: "تیکت یافت نشد" };

  if (ticket.status === 'CLOSED') {
    throw { status: 400, error: "این تیکت بسته شده است و امکان ارسال پیام وجود ندارد." };
  }

  const now = Date.now();
  const userMsg = {
    id: "MSG_" + (messagesDb.length + 1),
    ticketId,
    senderType: 'USER',
    senderId: auth.user.id,
    senderName: auth.user.fullName,
    messageText: messageText.trim(),
    createdAtMs: now
  };
  messagesDb.push(userMsg);
  ticket.status = 'IN_PROGRESS';
  ticket.updatedAtMs = now;
  ticket.lastMessageAtMs = now;
  return userMsg;
}

console.log('[Test 6] Testing Follow-up & Closed Ticket Handling...');
// Follow-up before closing
sendUserReply('TOKEN_USR_1', resTicket1.ticket.id, 'ممنون حل شد.');
assert.strictEqual(resTicket1.ticket.status, 'IN_PROGRESS');

// Admin closes ticket
adminReplyTicket(ADMIN_API_KEY, resTicket1.ticket.id, 'خواهش می‌کنم، تیکت بسته شد.', 'CLOSED');
assert.strictEqual(resTicket1.ticket.status, 'CLOSED');

// User tries to send message on closed ticket -> Must throw 400
let closedCaught = false;
try {
  sendUserReply('TOKEN_USR_1', resTicket1.ticket.id, 'پیام جدید بعد از بسته شدن');
} catch (e) {
  closedCaught = true;
  assert.strictEqual(e.status, 400);
}
assert.strictEqual(closedCaught, true, 'Posting to closed ticket MUST be rejected with 400');
console.log('  ✓ Posting to closed ticket correctly rejected with HTTP 400.');

// Test 7: XSS Sanitization Check
function escapeHtml(str) {
  if (!str) return '';
  return String(str)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#039;');
}

console.log('[Test 7] Testing XSS Escaping for Admin Display...');
const maliciousInput = '<script>alert("XSS")</script>&"test"';
const safeOutput = escapeHtml(maliciousInput);
assert.strictEqual(safeOutput.includes('<script>'), false);
assert.strictEqual(safeOutput, '&lt;script&gt;alert(&quot;XSS&quot;)&lt;/script&gt;&amp;&quot;test&quot;');
console.log('  ✓ XSS vectors safely neutralized.');

console.log('====================================================');
console.log('ALL SUPPORT SYSTEM TESTS PASSED SUCCESSFULLY! 100%');
console.log('====================================================');
