const assert = require('assert');

console.log('====================================================');
console.log('[Announcement Tests] Starting tests for Home Announcement APIs...');
console.log('====================================================');

// Test 1: Validation Rules
function validateAnnouncement(data) {
  if (!data || !data.title || !data.title.trim()) {
    return { valid: false, error: "عنوان پیام نمی‌تواند خالی باشد." };
  }
  if (!data.content || !data.content.trim()) {
    return { valid: false, error: "متن پیام نمی‌تواند خالی باشد." };
  }
  return { valid: true };
}

assert.strictEqual(validateAnnouncement({}).valid, false);
assert.strictEqual(validateAnnouncement({ title: '', content: 'hello' }).valid, false);
assert.strictEqual(validateAnnouncement({ title: 'Title', content: '' }).valid, false);
assert.strictEqual(validateAnnouncement({ title: 'اطلاعیه مهم', content: 'متن پیام' }).valid, true);
console.log('  ✓ Test 1 Passed: Empty title/content properly rejected, valid input accepted.');

// Test 2: In-Memory Database Simulation of Announcements Management
const db = {
  announcements: [],
  competitions: [
    { id: 'COMP_1', competitionNumber: 1, title: 'مسابقه دوره ۱', status: 'ACTIVE' }
  ],
  archives: [],
  userScores: { 'usr_1': 250, 'usr_2': 100 }
};

function createOrUpdateAnnouncement(data, adminKey) {
  if (adminKey !== 'ReqabatAdmin2026') {
    throw new Error('401 Unauthorized');
  }
  const v = validateAnnouncement(data);
  if (!v.valid) throw new Error(v.error);

  const isActive = data.isActive !== false;
  if (isActive) {
    db.announcements.forEach(a => { a.isActive = false; });
  }

  let saved;
  if (data.id) {
    saved = db.announcements.find(a => a.id === data.id);
    if (!saved) throw new Error('404 Not Found');
    saved.title = data.title.trim();
    saved.content = data.content.trim();
    saved.type = data.type || 'ANNOUNCEMENT';
    saved.isActive = isActive;
    saved.endsAt = data.endsAt || null;
    saved.updatedAt = Date.now();
  } else {
    saved = {
      id: `ANN_${Date.now()}_${Math.floor(Math.random() * 1000)}`,
      title: data.title.trim(),
      content: data.content.trim(),
      type: data.type || 'ANNOUNCEMENT',
      isActive,
      competitionId: data.competitionId || null,
      startsAt: data.startsAt || Date.now(),
      endsAt: data.endsAt || null,
      createdAt: Date.now(),
      updatedAt: Date.now()
    };
    db.announcements.push(saved);
  }
  return saved;
}

function getActiveAnnouncement() {
  const active = db.announcements
    .filter(a => a.isActive)
    .sort((a, b) => b.updatedAt - a.updatedAt)[0];
  return active || null;
}

function toggleAnnouncement(id, adminKey) {
  if (adminKey !== 'ReqabatAdmin2026') throw new Error('401 Unauthorized');
  const ann = db.announcements.find(a => a.id === id);
  if (!ann) throw new Error('404 Not Found');
  const newStatus = !ann.isActive;
  if (newStatus) {
    db.announcements.forEach(a => { a.isActive = false; });
  }
  ann.isActive = newStatus;
  ann.updatedAt = Date.now();
  return ann;
}

// Test 2.1: Create announcement as non-admin -> should reject
assert.throws(() => {
  createOrUpdateAnnouncement({ title: 'Test', content: 'Test' }, 'WrongKey');
}, /401 Unauthorized/);
console.log('  ✓ Test 2.1 Passed: Non-admin creation strictly rejected.');

// Test 2.2: Create valid announcement as admin
const ann1 = createOrUpdateAnnouncement({
  title: 'آغاز مسابقه بزرگ بهاره',
  content: 'به مسابقه جدید خوش آمدید! با تکمیل ۵۰ مربع جایزه بگیرید.',
  type: 'COMPETITION_START',
  isActive: true,
  endsAt: Date.now() + (7 * 24 * 3600 * 1000)
}, 'ReqabatAdmin2026');

assert.strictEqual(ann1.title, 'آغاز مسابقه بزرگ بهاره');
assert.strictEqual(ann1.isActive, true);

// Test 2.3: Active announcement retrieval
const active1 = getActiveAnnouncement();
assert.notStrictEqual(active1, null);
assert.strictEqual(active1.id, ann1.id);
console.log('  ✓ Test 2.2 & 2.3 Passed: Active announcement saved and retrievable.');

// Test 2.4: Create second active announcement -> first is deactivated
const ann2 = createOrUpdateAnnouncement({
  title: 'تمدید مهلت مسابقه',
  content: 'مهلت مسابقه به مدت ۴۸ ساعت تمدید شد.',
  type: 'COMPETITION_END',
  isActive: true
}, 'ReqabatAdmin2026');

assert.strictEqual(ann2.isActive, true);
assert.strictEqual(ann1.isActive, false); // ann1 automatically deactivated
assert.strictEqual(getActiveAnnouncement().id, ann2.id);
console.log('  ✓ Test 2.4 Passed: Setting new announcement deactivates previous one cleanly.');

// Test 2.5: Toggle active state to false -> getActiveAnnouncement returns null
toggleAnnouncement(ann2.id, 'ReqabatAdmin2026');
assert.strictEqual(ann2.isActive, false);
assert.strictEqual(getActiveAnnouncement(), null);
console.log('  ✓ Test 2.5 Passed: Deactivating announcement hides it from active endpoint.');

// Test 2.6: Toggle past announcement ann1 back to active
toggleAnnouncement(ann1.id, 'ReqabatAdmin2026');
assert.strictEqual(ann1.isActive, true);
assert.strictEqual(getActiveAnnouncement().id, ann1.id);
console.log('  ✓ Test 2.6 Passed: Past announcement restored and active.');

// Test 3: Integrated Start New Competition with Automatic Linked Announcement
function startNewCompetitionWithAnn(newTitle, endDate, annContent, adminKey) {
  if (adminKey !== 'ReqabatAdmin2026') throw new Error('401');

  // Archive previous competition
  const prevComp = db.competitions.find(c => c.status === 'ACTIVE');
  if (prevComp) {
    prevComp.status = 'ARCHIVED';
  }

  // New competition
  const nextNumber = prevComp ? prevComp.competitionNumber + 1 : 1;
  const newComp = {
    id: `COMP_${nextNumber}`,
    competitionNumber: nextNumber,
    title: newTitle || `مسابقه دوره ${nextNumber}`,
    status: 'ACTIVE',
    endedAt: endDate || null
  };
  db.competitions.push(newComp);

  // Reset scores
  Object.keys(db.userScores).forEach(u => { db.userScores[u] = 0; });

  // Auto-publish announcement
  const ann = createOrUpdateAnnouncement({
    title: `آغاز ${newComp.title}`,
    content: annContent || 'دوره جدید آغاز شد.',
    type: 'COMPETITION_START',
    isActive: true,
    competitionId: newComp.id,
    endsAt: endDate || null
  }, adminKey);

  return { newComp, ann };
}

const compSimResult = startNewCompetitionWithAnn(
  'مسابقه دوره دوم (پلی‌استیشن ۵)',
  Date.now() + 14 * 24 * 3600 * 1000,
  'مسابقه دوره دوم با ۵۰ مربع جایزه‌دار رسماً آغاز گردید!',
  'ReqabatAdmin2026'
);

assert.strictEqual(compSimResult.newComp.competitionNumber, 2);
assert.strictEqual(compSimResult.newComp.status, 'ACTIVE');
assert.strictEqual(db.userScores['usr_1'], 0); // User scores reset to 0
assert.strictEqual(compSimResult.ann.competitionId, 'COMP_2');
assert.strictEqual(compSimResult.ann.isActive, true);
assert.strictEqual(getActiveAnnouncement().id, compSimResult.ann.id);
console.log('  ✓ Test 3 Passed: Integrated competition rollover archives past, resets scores, and publishes new announcement.');

console.log('====================================================');
console.log('ALL HOME ANNOUNCEMENT TESTS PASSED SUCCESSFULLY!');
console.log('====================================================');
