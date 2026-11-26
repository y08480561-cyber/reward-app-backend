const assert = require('assert');
const http = require('http');
const fs = require('fs');
const path = require('path');
const Module = require('module');
const { spawn } = require('child_process');

// Fallback only if node_modules/iconv-lite/lib/index.js is missing on disk in this workspace
try {
  require.resolve('iconv-lite');
} catch (_err) {
  const origResolve = Module._resolveFilename;
  const stubPath = path.resolve(__dirname, 'cloudflare/stubs/iconv-lite.js');
  Module._resolveFilename = function (request, parent, isMain, options) {
    if (request === 'iconv-lite') return stubPath;
    return origResolve.call(this, request, parent, isMain, options);
  };
}

process.env.ADMIN_KEY = process.env.ADMIN_KEY || 'test-d1-admin-key-2026';
delete process.env.DATABASE_URL;
const ADMIN_KEY = process.env.ADMIN_KEY;

const { D1PoolAdapter } = require('./cloudflare/d1-adapter.js');
const app = require('./server.js');

/**
 * Persistent Python SQLite 3.40+ driver modeling real Cloudflare D1 semantics:
 * - Strict 100 bound parameter limit per prepared statement (matching D1)
 * - PRAGMA foreign_keys = ON
 * - Atomic batch() wrapped in BEGIN IMMEDIATE ... COMMIT with ROLLBACK on error
 */
class SqliteD1Mock {
  constructor(schemaSql) {
    this.schemaSql = schemaSql;
    this.reqId = 1;
    this.pending = new Map();
    this.buffer = '';

    const pyScript = `
import sys, json, sqlite3

conn = sqlite3.connect(':memory:', isolation_level=None)
conn.row_factory = sqlite3.Row
conn.execute('PRAGMA foreign_keys = ON;')

def handle_stmt(sql, params):
    if len(params) > 100:
        raise Exception('too many SQL variables (D1 max 100 bound params exceeded: %d)' % len(params))
    cur = conn.execute(sql, params)
    cols = [d[0] for d in cur.description] if cur.description else None
    if cols is not None:
        rows = [dict(zip(cols, row)) for row in cur.fetchall()]
        return {'results': rows, 'meta': {'changes': cur.rowcount if cur.rowcount >= 0 else 0}}
    return {'results': [], 'meta': {'changes': cur.rowcount if cur.rowcount >= 0 else 0}}

for line in sys.stdin:
    line = line.strip()
    if not line:
        continue
    req = json.loads(line)
    rid = req['id']
    op = req['op']
    try:
        if op == 'exec':
            conn.executescript(req['sql'])
            resp = {'id': rid, 'ok': True, 'data': {'ok': True}}
        elif op == 'run':
            data = handle_stmt(req['sql'], req.get('params', []))
            resp = {'id': rid, 'ok': True, 'data': data}
        elif op == 'batch':
            conn.execute('BEGIN IMMEDIATE')
            try:
                out = []
                for item in req['stmts']:
                    out.append(handle_stmt(item['sql'], item.get('params', [])))
                conn.execute('COMMIT')
                resp = {'id': rid, 'ok': True, 'data': out}
            except Exception as be:
                try:
                    conn.execute('ROLLBACK')
                except Exception:
                    pass
                raise be
        else:
            raise Exception('Unknown op: ' + str(op))
    except Exception as e:
        resp = {'id': rid, 'ok': False, 'error': str(e)}
    sys.stdout.write(json.dumps(resp, ensure_ascii=False) + '\\n')
    sys.stdout.flush()
`;

    this.proc = spawn('python3', ['-u', '-c', pyScript], { stdio: ['pipe', 'pipe', 'pipe'] });
    this.proc.stdout.setEncoding('utf8');
    this.proc.stdout.on('data', (chunk) => {
      this.buffer += chunk;
      let idx;
      while ((idx = this.buffer.indexOf('\n')) !== -1) {
        const line = this.buffer.slice(0, idx).trim();
        this.buffer = this.buffer.slice(idx + 1);
        if (!line) continue;
        const msg = JSON.parse(line);
        const entry = this.pending.get(msg.id);
        if (entry) {
          this.pending.delete(msg.id);
          if (msg.ok) entry.resolve(msg.data);
          else entry.reject(new Error(msg.error));
        }
      }
    });
    this.proc.stderr.setEncoding('utf8');
    this.proc.stderr.on('data', (d) => {
      console.error('[SqliteD1Mock STDERR]', d);
    });
  }

  _send(payload) {
    const id = this.reqId++;
    return new Promise((resolve, reject) => {
      this.pending.set(id, { resolve, reject });
      this.proc.stdin.write(JSON.stringify({ id, ...payload }) + '\n');
    });
  }

  async init() {
    await this._send({ op: 'exec', sql: this.schemaSql });
  }

  async execRaw(sql) {
    return this._send({ op: 'exec', sql });
  }

  prepare(sql) {
    const self = this;
    const makeStmt = (boundParams = []) => ({
      sql,
      params: boundParams,
      bind(...args) {
        return makeStmt(args);
      },
      async all() {
        return self._send({ op: 'run', sql, params: boundParams });
      },
      async run() {
        return self._send({ op: 'run', sql, params: boundParams });
      },
      async first(colName) {
        const res = await self._send({ op: 'run', sql, params: boundParams });
        const row = res.results && res.results[0] ? res.results[0] : null;
        if (!row) return null;
        return colName ? row[colName] : row;
      }
    });
    return makeStmt([]);
  }

  async batch(stmts) {
    return this._send({
      op: 'batch',
      stmts: stmts.map((s) => ({ sql: s.sql, params: s.params || [] }))
    });
  }

  close() {
    try {
      this.proc.stdin.end();
      this.proc.kill();
    } catch (_e) {}
  }
}

function httpRequest(port, method, reqPath, body = null, headers = {}) {
  return new Promise((resolve, reject) => {
    const isBuffer = Buffer.isBuffer(body);
    const payload = body === null ? null : (isBuffer ? body : JSON.stringify(body));
    const reqHeaders = { ...headers };
    if (payload !== null && !reqHeaders['Content-Type'] && !reqHeaders['content-type']) {
      reqHeaders['Content-Type'] = 'application/json';
    }
    if (payload !== null) {
      reqHeaders['Content-Length'] = Buffer.byteLength(payload);
    }

    const req = http.request(
      { hostname: '127.0.0.1', port, path: reqPath, method, headers: reqHeaders },
      (res) => {
        const chunks = [];
        res.on('data', (c) => chunks.push(c));
        res.on('end', () => {
          const buf = Buffer.concat(chunks);
          const text = buf.toString('utf8');
          let json = null;
          try { json = JSON.parse(text); } catch (_e) {}
          resolve({ status: res.statusCode, headers: res.headers, body: json, raw: buf, text });
        });
      }
    );
    req.on('error', reject);
    if (payload !== null) req.write(payload);
    req.end();
  });
}

async function runTests() {
  console.log('================================================================');
  console.log('  Cloudflare D1 Real-SQLite + PostgreSQL Parity Test Suite');
  console.log('================================================================');

  const schemaSql = fs.readFileSync(path.resolve(__dirname, '../d1_schema.sql'), 'utf8');
  const d1Mock = new SqliteD1Mock(schemaSql);
  await d1Mock.init();

  const d1Pool = new D1PoolAdapter(d1Mock);
  app.setTestPool(d1Pool);

  const server = app.listen(0, '127.0.0.1');
  await new Promise((r) => server.once('listening', r));
  const port = server.address().port;

  let passed = 0;
  const pass = (name) => {
    passed++;
    console.log(`  ✓ [${passed}] ${name}`);
  };

  try {
    // ------------------------------------------------------------------
    // 1. D1 Adapter Timestamp & Query Conversion Tests
    // ------------------------------------------------------------------
    const convertedTs = d1Pool.convertQuery(
      "INSERT INTO logs (msg, a, b) VALUES ('keep NOW() and CURRENT_TIMESTAMP', NOW(), CURRENT_TIMESTAMP)"
    );
    assert(convertedTs.sql.includes("'keep NOW() and CURRENT_TIMESTAMP'"), 'Single-quoted literal must remain intact');
    assert(!/\bNOW\(\)/i.test(convertedTs.sql.replace(/'[^']*'/g, '')), 'Unquoted NOW() must be rewritten');
    assert(!/\bCURRENT_TIMESTAMP\b/i.test(convertedTs.sql.replace(/'[^']*'/g, '')), 'Unquoted CURRENT_TIMESTAMP must be rewritten');
    assert(convertedTs.sql.includes("strftime('%Y-%m-%dT%H:%M:%fZ', 'now')"), 'Must convert to ISO-8601 UTC strftime');

    const tsCheck = await d1Pool.query("SELECT NOW() AS n1, CURRENT_TIMESTAMP AS n2");
    assert(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}Z$/.test(tsCheck.rows[0].n1), 'D1 NOW() must return ISO UTC');
    assert.strictEqual(tsCheck.rows[0].n1, tsCheck.rows[0].n2, 'NOW() and CURRENT_TIMESTAMP must match identically');
    assert.strictEqual(d1Pool.sanitizeParam(new Date('invalid-date')), null, 'Invalid Date must sanitize to null');
    pass('D1Adapter NOW()/CURRENT_TIMESTAMP standardization & Date sanitization');

    // ------------------------------------------------------------------
    // 2. Seed Users, Scoreboards, Store Products, Rewards, Banners
    // ------------------------------------------------------------------
    // Register 2 primary users + 120 bulk users for chunking test
    const u1Res = await httpRequest(port, 'POST', '/api/auth/login', {
      phoneNumber: '09120000001',
      password: 'Password123',
      firstName: 'علی',
      lastName: 'رضایی'
    });
    assert.strictEqual(u1Res.status, 200);
    const u1 = u1Res.body.user;
    assert(u1 && u1.token && u1.id);

    const u2Res = await httpRequest(port, 'POST', '/api/auth/login', {
      phoneNumber: '09120000002',
      password: 'Password123',
      firstName: 'مریم',
      lastName: 'محمدی'
    });
    assert.strictEqual(u2Res.status, 200);
    const u2 = u2Res.body.user;

    // Seed canonical store products, rewards, categories, login notification settings, competition
    await d1Pool.query(
      `INSERT INTO public.competitions (id, competition_number, title, status, started_at, created_by, created_at)
       VALUES ('COMP_1', 1, 'مسابقه شماره ۱', 'ACTIVE', NOW(), 'admin', NOW())`
    );
    await d1Pool.query(
      `INSERT INTO public.store_products (id, title, description, product_type, units_granted, price_toman, myket_sku, badge, is_active, is_ready_in_myket, sort_order)
       VALUES
         ('pkg_squares_50', 'بازگشایی ۵۰ مربع', 'توضیح ۵۰', 'SQUARE_UNLOCK', 50, 55000, 'reqabat_unlock_50', 'محبوب', 1, 1, 1),
         ('pkg_squares_100', 'بازگشایی ۱۰۰ مربع', 'توضیح ۱۰۰', 'SQUARE_UNLOCK', 100, 100000, 'reqabat_unlock_100', 'به‌صرفه', 1, 0, 2),
         ('pkg_upgrade_silver', 'ارتقای نقره‌ای', 'توضیح نقره', 'ACCOUNT_UPGRADE', 3, 70000, 'reqabat_upgrade_silver', 'نقره‌ای', 1, 1, 3),
         ('pkg_upgrade_gold', 'ارتقای طلایی', 'توضیح طلا', 'ACCOUNT_UPGRADE', 6, 120000, 'reqabat_upgrade_gold', 'طلایی', 1, 1, 4)`
    );
    await d1Pool.query(
      `INSERT INTO public.reward_categories (id, name, slug, icon, sort_order, is_active)
       VALUES ('cat_digital', 'گیفت کارت', 'digital-giftcards', 'gift', 1, 1),
              ('cat_inactive', 'غیرفعال', 'inactive-cat', 'lock', 2, 0)`
    );
    await d1Pool.query(
      `INSERT INTO public.rewards (id, title, description, category_id, cost_points, stock_quantity, image_url, additional_images, is_featured, is_active, sort_order)
       VALUES
         ('rew_valid', 'کارت شارژ تست', 'توضیحات جایزه تستی', 'cat_digital', 10, 2, 'https://example.com/r1.jpg', '["https://example.com/r1b.jpg"]', 1, 1, 1),
         ('rew_null_json', 'جایزه JSON نال', 'توضیح', 'cat_digital', 5, 0, '', NULL, 0, 1, 2),
         ('rew_empty_json', 'جایزه JSON خالی', 'توضیح', 'cat_digital', 5, 1, '', '[]', 0, 0, 3)`
    );
    await d1Pool.query(
      `INSERT INTO public.login_notification_settings (id, is_visible, warning_is_visible)
       VALUES ('default', 1, 0)`
    );

    // ------------------------------------------------------------------
    // 3. Health Checks (/ & /api/health & /api/health/d1)
    // ------------------------------------------------------------------
    const hRes = await httpRequest(port, 'GET', '/api/health/d1');
    assert.strictEqual(hRes.status, 200);
    assert.strictEqual(hRes.body.d1Connected, true);
    assert.strictEqual(hRes.body.adapter, 'cloudflare-d1');
    pass('Health endpoints confirm active D1 adapter');

    // ------------------------------------------------------------------
    // 4. POST /api/admin/users/export-selected-excel (D1 chunking & SQLi + PG parity)
    // ------------------------------------------------------------------
    const bulkIds = [];
    for (let i = 0; i < 120; i++) {
      const uid = `bulk_u_${String(i).padStart(3, '0')}`;
      const phone = `0913${String(1000000 + i).padStart(7, '0')}`;
      bulkIds.push(uid);
      await d1Pool.query(
        `INSERT INTO public.users (id, first_name, last_name, phone_number, total_score, status, created_at, updated_at)
         VALUES ($1, $2, $3, $4, 0, 'active', $5, NOW())`,
        [uid, 'کاربر', `${i}`, phone, new Date(1700000000000 + i * 1000).toISOString()]
      );
    }

    const expEmpty = await httpRequest(port, 'POST', '/api/admin/users/export-selected-excel', { userIds: [] }, { 'x-admin-key': ADMIN_KEY });
    assert.strictEqual(expEmpty.status, 400);

    const expSqli = await httpRequest(port, 'POST', '/api/admin/users/export-selected-excel', { userIds: ["' OR '1'='1", "nonexistent"] }, { 'x-admin-key': ADMIN_KEY });
    assert.strictEqual(expSqli.status, 404);

    // 120 IDs exceeds D1's 100 bound-param limit -> D1 chunking (80 + 40) MUST succeed
    const expBulk = await httpRequest(port, 'POST', '/api/admin/users/export-selected-excel', { userIds: bulkIds }, { 'x-admin-key': ADMIN_KEY });
    assert.strictEqual(expBulk.status, 200, `Expected 200 for 120 users, got ${expBulk.status}: ${expBulk.text}`);
    assert.strictEqual(expBulk.raw.slice(0, 2).toString('utf8'), 'PK', 'Export must return valid .xlsx zip binary');
    pass('Selected-users Excel export chunks 120 IDs (<100 D1 params) and blocks SQLi');

    // ------------------------------------------------------------------
    // 5. Boolean (0/1 -> JS boolean) & JSON (TEXT -> array/object) Normalization
    // ------------------------------------------------------------------
    const spRes = await httpRequest(port, 'GET', '/api/store/products');
    assert.strictEqual(spRes.status, 200);
    assert.strictEqual(typeof spRes.body.products[0].isActive, 'boolean');
    assert.strictEqual(spRes.body.products[0].isActive, true);
    assert.strictEqual(typeof spRes.body.products[1].isReadyInMyket, 'boolean');
    assert.strictEqual(spRes.body.products[1].isReadyInMyket, false);

    const spAdminRes = await httpRequest(port, 'GET', '/api/admin/store/products', null, { 'x-admin-key': ADMIN_KEY });
    assert.strictEqual(spAdminRes.status, 200);
    assert.strictEqual(typeof spAdminRes.body.products[0].is_active, 'boolean');
    assert.strictEqual(spAdminRes.body.products[0].is_active, true);
    assert.strictEqual(typeof spAdminRes.body.products[1].is_ready_in_myket, 'boolean');
    assert.strictEqual(spAdminRes.body.products[1].is_ready_in_myket, false);

    const entRes = await httpRequest(port, 'GET', '/api/store/user-entitlements', null, { 'x-user-id': u1.id });
    assert.strictEqual(entRes.status, 200);
    assert.strictEqual(typeof entRes.body.entitlements.isVip, 'boolean');
    assert.strictEqual(entRes.body.entitlements.isVip, false);

    const lnRes = await httpRequest(port, 'GET', '/api/app/login-notification');
    assert.strictEqual(lnRes.status, 200);
    assert.strictEqual(lnRes.body.settings.is_visible, true);
    assert.strictEqual(lnRes.body.settings.warning_is_visible, false);

    const rewAdminRes = await httpRequest(port, 'GET', '/api/rewards/admin/rewards', null, { 'x-admin-key': ADMIN_KEY });
    assert.strictEqual(rewAdminRes.status, 200);
    const rValid = rewAdminRes.body.rewards.find((r) => r.id === 'rew_valid');
    const rNull = rewAdminRes.body.rewards.find((r) => r.id === 'rew_null_json');
    const rEmpty = rewAdminRes.body.rewards.find((r) => r.id === 'rew_empty_json');
    assert.strictEqual(typeof rValid.is_featured, 'boolean');
    assert.strictEqual(rValid.is_featured, true);
    assert.strictEqual(rValid.is_active, true);
    assert.deepStrictEqual(rValid.additional_images, ['https://example.com/r1b.jpg']);
    assert.strictEqual(rNull.is_featured, false);
    assert.deepStrictEqual(rNull.additional_images, []);
    assert.strictEqual(rEmpty.is_active, false);
    assert.deepStrictEqual(rEmpty.additional_images, []);
    pass('All D1 0/1 booleans and TEXT JSON fields (including null/empty) normalize cleanly');

    // ------------------------------------------------------------------
    // 6. saveAppearanceHandler (No Runtime DDL / No JSONB Casts)
    // ------------------------------------------------------------------
    const appSave1 = await httpRequest(
      port,
      'POST',
      '/api/rewards/admin/appearance',
      {
        hero_title: 'ویترین آزمایشی D1',
        hero_subtitle: 'زیرعنوان',
        hero_banner_url: 'https://example.com/hero.jpg',
        primary_color: '#10B981',
        active_sections: { featured: true, categories: true, history: false, banners: true },
        terms_text: 'قوانین نمونه'
      },
      { 'x-admin-key': ADMIN_KEY }
    );
    assert.strictEqual(appSave1.status, 200, `Save appearance failed: ${appSave1.text}`);
    assert.strictEqual(appSave1.body.appearance.hero_title, 'ویترین آزمایشی D1');
    assert.deepStrictEqual(appSave1.body.appearance.active_sections, { featured: true, categories: true, history: false, banners: true });

    const appGet = await httpRequest(port, 'GET', '/api/rewards/appearance');
    assert.strictEqual(appGet.status, 200);
    assert.deepStrictEqual(appGet.body.appearance.active_sections, { featured: true, categories: true, history: false, banners: true });
    pass('saveAppearanceHandler persists & reads on D1 without runtime DDL or ::jsonb');

    // ------------------------------------------------------------------
    // 7. Announcements Boolean & Toggle Tests
    // ------------------------------------------------------------------
    const annCreate = await httpRequest(
      port,
      'POST',
      '/api/admin/announcements',
      { title: 'اعلان آزمایشی', content: 'متن اعلان', type: 'ANNOUNCEMENT', isActive: true },
      { 'x-admin-key': ADMIN_KEY }
    );
    assert.strictEqual(annCreate.status, 200);
    const annId = annCreate.body.announcement.id;

    const annActive = await httpRequest(port, 'GET', '/api/announcement/active');
    assert.strictEqual(annActive.status, 200);
    assert.strictEqual(annActive.body.announcement.isActive, true);

    const annToggleOff = await httpRequest(port, 'PATCH', `/api/admin/announcements/${annId}/toggle`, {}, { 'x-admin-key': ADMIN_KEY });
    assert.strictEqual(annToggleOff.status, 200);
    assert.strictEqual(annToggleOff.body.isActive, false);

    const annToggleOn = await httpRequest(port, 'PATCH', `/api/admin/announcements/${annId}/toggle`, {}, { 'x-admin-key': ADMIN_KEY });
    assert.strictEqual(annToggleOn.status, 200);
    assert.strictEqual(annToggleOn.body.isActive, true);
    pass('Announcements CRUD and toggle work with D1 0/1 booleans');

    // ------------------------------------------------------------------
    // 8. Ad Banners Scheduling, ISO Timestamps, Placement Stats & Reorder
    // ------------------------------------------------------------------
    const now = Date.now();
    const bActive = await httpRequest(port, 'POST', '/api/admin/ad-banners', {
      title: 'بنر فعال',
      image_url: 'https://example.com/b1.png',
      target_url: 'https://example.com/t1',
      link_open_mode: 'external',
      is_active: true,
      start_at: new Date(now - 60000).toISOString(),
      end_at: new Date(now + 3600000).toISOString(),
      display_order: 2,
      placements: ['home', 'profile']
    }, { 'x-admin-key': ADMIN_KEY });
    assert.strictEqual(bActive.status, 201);

    const bExpired = await httpRequest(port, 'POST', '/api/admin/ad-banners', {
      title: 'بنر منقضی',
      image_url: 'https://example.com/b2.png',
      target_url: 'https://example.com/t2',
      is_active: true,
      start_at: new Date(now - 120000).toISOString(),
      end_at: new Date(now - 5000).toISOString(),
      display_order: 1,
      placements: ['home']
    }, { 'x-admin-key': ADMIN_KEY });
    assert.strictEqual(bExpired.status, 201);

    const bActiveList = await httpRequest(port, 'GET', '/api/ad-banners/active?placement=home');
    assert.strictEqual(bActiveList.status, 200);
    assert.strictEqual(bActiveList.body.banners.length, 1);
    assert.strictEqual(bActiveList.body.banners[0].id, bActive.body.banner.id);
    assert.strictEqual(typeof bActiveList.body.banners[0].isActive, 'boolean');
    assert.deepStrictEqual(bActiveList.body.banners[0].placements, ['home', 'profile']);

    const impRes = await httpRequest(port, 'POST', `/api/ad-banners/${bActive.body.banner.id}/impression`, { userId: u1.id, placement: 'home' });
    assert.strictEqual(impRes.status, 200);
    assert.strictEqual(impRes.body.recorded, true);

    const clkRes = await httpRequest(port, 'POST', `/api/ad-banners/${bActive.body.banner.id}/click`, { userId: u1.id, placement: 'home', tapNonce: 'n1' });
    assert.strictEqual(clkRes.status, 200);
    assert.strictEqual(clkRes.body.recorded, true);

    const impExpired = await httpRequest(port, 'POST', `/api/ad-banners/${bExpired.body.banner.id}/impression`, { userId: u1.id, placement: 'home' });
    assert.strictEqual(impExpired.status, 404);

    const reorderRes = await httpRequest(port, 'PATCH', '/api/admin/ad-banners/reorder', {
      items: [
        { id: bActive.body.banner.id, display_order: 0 },
        { id: bExpired.body.banner.id, display_order: 5 }
      ]
    }, { 'x-admin-key': ADMIN_KEY });
    assert.strictEqual(reorderRes.status, 200);

    const adminBanners = await httpRequest(port, 'GET', '/api/admin/ad-banners', null, { 'x-admin-key': ADMIN_KEY });
    assert.strictEqual(adminBanners.status, 200);
    const fetchedActive = adminBanners.body.banners.find((b) => b.id === bActive.body.banner.id);
    assert.strictEqual(fetchedActive.impression_count, 1);
    assert.strictEqual(fetchedActive.click_count, 1);
    assert.strictEqual(fetchedActive.placement_stats.home.impressions, 1);
    assert.strictEqual(fetchedActive.placement_stats.home.clicks, 1);
    assert.strictEqual(fetchedActive.display_order, 0);
    pass('Ad banners ISO schedule windows, D1 placement_stats impression/click & batch reorder verified');

    // ------------------------------------------------------------------
    // 9. Store Purchase Verification + Instant Unlock + Ad Complete + Concurrency
    // ------------------------------------------------------------------
    // Buy SILVER -> Buy 50 credits -> Check replay -> Buy GOLD -> Buy SILVER (keeps GOLD)
    const buySilver = await httpRequest(port, 'POST', '/api/store/verify-purchase', {
      userId: u1.id,
      productId: 'pkg_upgrade_silver',
      purchaseToken: 'tok_silver_1',
      orderId: 'ord_s1'
    }, { 'x-auth-token': u1.token, 'x-user-id': u1.id });
    assert.strictEqual(buySilver.status, 200);
    assert.strictEqual(buySilver.body.entitlements.isVip, false);
    const panelSilver = await httpRequest(port, 'GET', `/api/panels/me?userId=${u1.id}`);
    assert.strictEqual(panelSilver.status, 200);
    assert.strictEqual(panelSilver.body.membership.accountTier, 'SILVER');
    assert.strictEqual(panelSilver.body.membership.multiplier, 3);

    const buyReplay = await httpRequest(port, 'POST', '/api/store/verify-purchase', {
      userId: u1.id,
      productId: 'pkg_upgrade_silver',
      purchaseToken: 'tok_silver_1',
      orderId: 'ord_s1_dup'
    }, { 'x-auth-token': u1.token, 'x-user-id': u1.id });
    assert.strictEqual(buyReplay.status, 409);

    const buyCredits = await httpRequest(port, 'POST', '/api/store/verify-purchase', {
      userId: u1.id,
      productId: 'pkg_squares_50',
      purchaseToken: 'tok_c50_1',
      orderId: 'ord_c1'
    }, { 'x-auth-token': u1.token, 'x-user-id': u1.id });
    assert.strictEqual(buyCredits.status, 200);
    assert.strictEqual(buyCredits.body.entitlements.instantUnlockCredits, 50);

    const buyGold = await httpRequest(port, 'POST', '/api/store/verify-purchase', {
      userId: u1.id,
      productId: 'pkg_upgrade_gold',
      purchaseToken: 'tok_gold_1',
      orderId: 'ord_g1'
    }, { 'x-auth-token': u1.token, 'x-user-id': u1.id });
    assert.strictEqual(buyGold.status, 200);
    assert.strictEqual(buyGold.body.entitlements.isVip, true);
    const panelGold = await httpRequest(port, 'GET', `/api/panels/me?userId=${u1.id}`);
    assert.strictEqual(panelGold.status, 200);
    assert.strictEqual(panelGold.body.membership.accountTier, 'GOLD');
    assert.strictEqual(panelGold.body.membership.multiplier, 6);

    // Purchasing SILVER while already GOLD must keep GOLD (no downgrade)
    const buySilverAfterGold = await httpRequest(port, 'POST', '/api/store/verify-purchase', {
      userId: u1.id,
      productId: 'pkg_upgrade_silver',
      purchaseToken: 'tok_silver_after_gold',
      orderId: 'ord_s_after_g'
    }, { 'x-auth-token': u1.token, 'x-user-id': u1.id });
    assert.strictEqual(buySilverAfterGold.status, 200);
    assert.strictEqual(buySilverAfterGold.body.entitlements.isVip, true);

    // Complete Ad square #1 (u1 is GOLD -> +6 points)
    const adRes = await httpRequest(port, 'POST', '/api/ads/complete', {
      userId: u1.id,
      squareNumber: 1,
      cycleId: u1Res.body.cycle.cycleId,
      adDurationSeconds: 18,
      rewardNonce: 'nonce_sq1'
    });
    assert.strictEqual(adRes.status, 200);
    assert.strictEqual(adRes.body.awardedPoints, 6);
    assert.strictEqual(adRes.body.newTotalScore, 6);

    const adDup = await httpRequest(port, 'POST', '/api/ads/complete', {
      userId: u1.id,
      squareNumber: 1,
      cycleId: u1Res.body.cycle.cycleId,
      adDurationSeconds: 18,
      rewardNonce: 'nonce_sq1_2'
    });
    assert.strictEqual(adDup.status, 409);

    // Instant unlock square #2 (+6 points, 49 credits left)
    const unlockRes = await httpRequest(port, 'POST', '/api/squares/instant-unlock', {
      userId: u1.id,
      squareNumber: 2,
      cycleId: u1Res.body.cycle.cycleId
    });
    assert.strictEqual(unlockRes.status, 200);
    assert.strictEqual(unlockRes.body.awardedPoints, 6);
    assert.strictEqual(unlockRes.body.newScore, 12);
    assert.strictEqual(unlockRes.body.remainingCredits, 49);

    // Test concurrency on instant-unlock with 1 remaining credit on u2:
    await d1Pool.query(
      `INSERT INTO public.user_entitlements (user_id, instant_unlock_credits, is_vip, account_tier, multiplier, total_purchased_toman)
       VALUES ($1, 1, 0, 'NORMAL', 1, 0)`,
      [u2.id]
    );
    await d1Pool.query(`UPDATE public.users SET instant_unlock_credits = 1 WHERE id = $1`, [u2.id]);
    const [raceU2A, raceU2B] = await Promise.all([
      httpRequest(port, 'POST', '/api/squares/instant-unlock', { userId: u2.id, squareNumber: 10, cycleId: u2Res.body.cycle.cycleId }),
      httpRequest(port, 'POST', '/api/squares/instant-unlock', { userId: u2.id, squareNumber: 11, cycleId: u2Res.body.cycle.cycleId })
    ]);
    const unlockSuccesses = [raceU2A, raceU2B].filter((r) => r.status === 200);
    assert.strictEqual(unlockSuccesses.length, 1, 'Only 1 concurrent instant-unlock may consume the single credit');
    const u2Score = await d1Pool.query(`SELECT total_score FROM public.user_scores WHERE user_id = $1`, [u2.id]);
    assert.strictEqual(Number(u2Score.rows[0].total_score), 1, 'Rolled-back concurrent unlock must not award score');
    pass('Store IAB purchase, tier upgrades, ad complete, and concurrent instant-unlock credit guard pass');

    // ------------------------------------------------------------------
    // 10. Feedback Concurrency + Reward Claim Atomic Batch & Double-Refund Prevention
    // ------------------------------------------------------------------
    const [fb1, fb2, fb3] = await Promise.all([
      httpRequest(port, 'POST', '/api/user/feedback', { rating: 5, comment: 'عالی بود' }, { 'x-auth-token': u1.token, 'x-user-id': u1.id }),
      httpRequest(port, 'POST', '/api/user/feedback', { rating: 5, comment: 'عالی بود ۲' }, { 'x-auth-token': u1.token, 'x-user-id': u1.id }),
      httpRequest(port, 'POST', '/api/user/feedback', { rating: 5, comment: 'عالی بود ۳' }, { 'x-auth-token': u1.token, 'x-user-id': u1.id })
    ]);
    const fbOk = [fb1, fb2, fb3].filter((r) => r.status === 200);
    const fbConflict = [fb1, fb2, fb3].filter((r) => r.status === 409);
    assert.strictEqual(fbOk.length, 1, 'Only 1 concurrent feedback claim must succeed');
    assert.strictEqual(fbConflict.length, 2, 'Duplicate concurrent feedback claims must return 409');
    // u1 score is now 12 + 2 = 14

    // Claim reward (cost 10, stock 2 -> u1 left with 4 points, stock 1)
    const claimOk = await httpRequest(port, 'POST', '/api/rewards/claim', {
      reward_id: 'rew_valid',
      recipient_name: 'علی',
      phone: '09120000001',
      shipping_address: 'تهران',
      idempotency_key: 'idemp_claim_1'
    }, { 'x-auth-token': u1.token, 'x-user-id': u1.id });
    assert.strictEqual(claimOk.status, 200, `Claim failed: ${claimOk.text}`);
    const claimId = claimOk.body.claim.id;

    // Duplicate idempotency key -> 409
    const claimDup = await httpRequest(port, 'POST', '/api/rewards/claim', {
      reward_id: 'rew_valid',
      recipient_name: 'علی',
      phone: '09120000001',
      idempotency_key: 'idemp_claim_1'
    }, { 'x-auth-token': u1.token, 'x-user-id': u1.id });
    assert.strictEqual(claimDup.status, 409);

    // Concurrent double-rejection refund test -> must refund ONLY ONCE (4 -> 14, stock 1 -> 2)
    const [rejA, rejB] = await Promise.all([
      httpRequest(port, 'PATCH', `/api/rewards/admin/claims/${claimId}`, { status: 'REJECTED', admin_notes: 'رد' }, { 'x-admin-key': ADMIN_KEY }),
      httpRequest(port, 'PATCH', `/api/rewards/admin/claims/${claimId}`, { status: 'REJECTED', admin_notes: 'رد ۲' }, { 'x-admin-key': ADMIN_KEY })
    ]);
    assert.strictEqual(rejA.status, 200);
    assert.strictEqual(rejB.status, 200);
    const u1AfterRefund = await d1Pool.query(`SELECT total_score FROM public.user_scores WHERE user_id = $1`, [u1.id]);
    const rewAfterRefund = await d1Pool.query(`SELECT stock_quantity FROM public.rewards WHERE id = 'rew_valid'`);
    assert.strictEqual(Number(u1AfterRefund.rows[0].total_score), 14, 'Double rejection must NOT double-refund user score');
    assert.strictEqual(Number(rewAfterRefund.rows[0].stock_quantity), 2, 'Double rejection must NOT double-restore stock');
    pass('Atomic D1 batch prevents concurrent feedback double-award & double reward refund');

    // ------------------------------------------------------------------
    // 11. Support Tickets + Admin Search (ILIKE -> LIKE) + Competition Rollover
    // ------------------------------------------------------------------
    const tickCreate = await httpRequest(port, 'POST', '/api/support/tickets', {
      subject: 'مشکل امتیاز ویژه',
      category: 'SCORES',
      message: 'امتیاز من ثبت نشد'
    }, { 'x-auth-token': u1.token, 'x-user-id': u1.id });
    assert.strictEqual(tickCreate.status, 201);
    const ticketId = tickCreate.body.ticket.id;

    const tickReply = await httpRequest(port, 'POST', `/api/admin/support/tickets/${ticketId}/messages`, {
      message: 'بررسی و اصلاح شد'
    }, { 'x-admin-key': ADMIN_KEY });
    assert.strictEqual(tickReply.status, 201);

    const tickSearch = await httpRequest(port, 'GET', `/api/admin/support/tickets?search=${encodeURIComponent(u1.phoneNumber)}`, null, { 'x-admin-key': ADMIN_KEY });
    assert.strictEqual(tickSearch.status, 200);
    assert.strictEqual(tickSearch.body.tickets.length, 1);

    const rollover = await httpRequest(port, 'POST', '/api/admin/competition/start-new', {
      newTitle: 'مسابقه شماره ۲ D1',
      announcementTitle: 'شروع دوره دوم',
      announcementContent: 'با جوایز تازه',
      confirmationPhrase: 'شروع مسابقه جدید'
    }, { 'x-admin-key': ADMIN_KEY });
    assert.strictEqual(rollover.status, 200, `Competition rollover failed: ${rollover.text}`);
    assert.strictEqual(rollover.body.newCompetition.competitionNumber, 2);

    const u1AfterReset = await d1Pool.query(`SELECT total_score FROM public.user_scores WHERE user_id = $1`, [u1.id]);
    assert.strictEqual(Number(u1AfterReset.rows[0].total_score), 0);
    const archivesRes = await d1Pool.query(`SELECT * FROM public.competition_archives WHERE user_id = $1`, [u1.id]);
    assert.strictEqual(archivesRes.rows.length, 1);
    assert.strictEqual(Number(archivesRes.rows[0].final_score), 14);
    pass('Support tickets (including ILIKE->LIKE) & atomic competition rollover pass on D1');

    // ------------------------------------------------------------------
    // 12. POST /api/user/settings/notifications & Worker Upload Guard
    // ------------------------------------------------------------------
    const nUnauth = await httpRequest(port, 'POST', '/api/user/settings/notifications', { enabled: true });
    assert.strictEqual(nUnauth.status, 401);

    const nWrongUser = await httpRequest(port, 'POST', '/api/user/settings/notifications', { enabled: true }, {
      'x-auth-token': u1.token,
      'x-user-id': u2.id
    });
    assert.strictEqual(nWrongUser.status, 403);

    const nInvalidBody = await httpRequest(port, 'POST', '/api/user/settings/notifications', { enabled: 'true' }, {
      'x-auth-token': u1.token,
      'x-user-id': u1.id
    });
    assert.strictEqual(nInvalidBody.status, 400);

    const nValidTrue = await httpRequest(port, 'POST', '/api/user/settings/notifications', { enabled: true }, {
      'x-auth-token': u1.token,
      'x-user-id': u1.id
    });
    assert.strictEqual(nValidTrue.status, 200);
    assert.strictEqual(nValidTrue.body.success, true);
    assert.strictEqual(nValidTrue.body.enabled, true);

    const nValidFalse = await httpRequest(port, 'POST', '/api/user/settings/notifications', { enabled: false }, {
      'x-auth-token': u1.token,
      'x-user-id': u1.id
    });
    assert.strictEqual(nValidFalse.status, 200);
    assert.strictEqual(nValidFalse.body.enabled, false);
    pass('POST /api/user/settings/notifications auth, ownership, and boolean validation verified');

    // Verify /api/purchases/verify works atomically on D1 and rejects duplicate token with 409
    const legPur = await httpRequest(port, 'POST', '/api/purchases/verify', {
      userId: u2.id,
      productId: 'pkg_upgrade_silver',
      purchaseToken: 'leg_tok_1',
      orderId: 'leg_ord_1'
    });
    assert.strictEqual(legPur.status, 200);
    assert.strictEqual(legPur.body.membership.accountTier, 'SILVER');
    const legPurDup = await httpRequest(port, 'POST', '/api/purchases/verify', {
      userId: u2.id,
      productId: 'pkg_upgrade_silver',
      purchaseToken: 'leg_tok_1',
      orderId: 'leg_ord_2'
    });
    assert.strictEqual(legPurDup.status, 409);

    // Verify Worker runtime upload guard returns 501 instead of touching fs
    const prevWorkerEnv = process.env.WORKER_RUNTIME;
    process.env.WORKER_RUNTIME = 'cloudflare-workers';
    const pngBytes = Buffer.from('89504e470d0a1a0a0000000d49484452000000010000000108060000001f15c4890000000a49444154789c63000100000500010d0a2db40000000049454e44ae426082', 'hex');
    const boundary = '----TestBoundaryD1Worker';
    const multipartAd = Buffer.concat([
      Buffer.from(`--${boundary}\r\nContent-Disposition: form-data; name="image"; filename="b.png"\r\nContent-Type: image/png\r\n\r\n`),
      pngBytes,
      Buffer.from(`\r\n--${boundary}--\r\n`)
    ]);
    const multipartReward = Buffer.concat([
      Buffer.from(`--${boundary}\r\nContent-Disposition: form-data; name="banner"; filename="b.png"\r\nContent-Type: image/png\r\n\r\n`),
      pngBytes,
      Buffer.from(`\r\n--${boundary}--\r\n`)
    ]);
    const uploadWorkerRes = await httpRequest(port, 'POST', '/api/admin/ad-banners/upload-image', multipartAd, {
      'x-admin-key': ADMIN_KEY,
      'Content-Type': `multipart/form-data; boundary=${boundary}`
    });
    const rewardUploadWorkerRes = await httpRequest(port, 'POST', '/api/rewards/admin/upload-banner', multipartReward, {
      'x-admin-key': ADMIN_KEY,
      'Content-Type': `multipart/form-data; boundary=${boundary}`
    });
    if (prevWorkerEnv === undefined) delete process.env.WORKER_RUNTIME;
    else process.env.WORKER_RUNTIME = prevWorkerEnv;
    assert.strictEqual(uploadWorkerRes.status, 501);
    assert.strictEqual(uploadWorkerRes.body.storageUnavailable, true);
    assert.strictEqual(rewardUploadWorkerRes.status, 501);
    assert.strictEqual(rewardUploadWorkerRes.body.storageUnavailable, true);
    pass('Legacy /api/purchases/verify & Worker runtime upload guards (501 JSON) verified');

    // ------------------------------------------------------------------
    // 13. PostgreSQL Parity Check (!isD1 Mock Pool emits PG SQL)
    // ------------------------------------------------------------------
    const capturedPgQueries = [];
    const pgMockPool = {
      isD1: false,
      async query(text, params) {
        capturedPgQueries.push({ text, params });
        if (/FROM public\.users u/i.test(text) && /ANY\(\$1::text\[\]\)/i.test(text)) {
          return {
            rows: [{ id: 'pg_u1', first_name: 'تست', last_name: 'پستگرس', phone_number: '09121112233', created_at: new Date().toISOString() }],
            rowCount: 1
          };
        }
        return { rows: [], rowCount: 0 };
      },
      async connect() {
        return { query: (t, p) => pgMockPool.query(t, p), release() {} };
      },
      async end() {}
    };
    app.setTestPool(pgMockPool);
    const pgExportRes = await httpRequest(port, 'POST', '/api/admin/users/export-selected-excel', { userIds: ['pg_u1'] }, { 'x-admin-key': ADMIN_KEY });
    assert.strictEqual(pgExportRes.status, 200);
    assert(capturedPgQueries.some((q) => /ANY\(\$1::text\[\]\)/.test(q.text)), 'PG pool must still execute ANY($1::text[])');
    pass('PostgreSQL (!isD1) parity verified: ANY($1::text[]) preserved intact');

    console.log('================================================================');
    console.log(`🎉 ALL ${passed} D1 REAL-SQLITE & PG PARITY TESTS PASSED!`);
    console.log('================================================================');
  } finally {
    server.close();
    d1Mock.close();
  }
}

runTests().catch((err) => {
  console.error('❌ D1 Compatibility Test Failed:', err);
  process.exit(1);
});
