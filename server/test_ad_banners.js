const assert = require('assert');
const http = require('http');
const fs = require('fs');
const path = require('path');

const app = require('./server');

const TEST_ADMIN_KEY = process.env.ADMIN_KEY || 'test-ad-banner-admin-key-2026';
if (!process.env.ADMIN_KEY) {
  process.env.ADMIN_KEY = TEST_ADMIN_KEY;
}

// Faithful in-memory PostgreSQL query engine for public.ad_banners & health checks
class InMemoryAdBannersPool {
  constructor() {
    this.banners = new Map();
  }

  async connect() {
    return {
      query: this.query.bind(this),
      release: () => {}
    };
  }

  async end() {}

  async query(sql, params = []) {
    const q = String(sql).replace(/\s+/g, ' ').trim();

    if (q === 'SELECT 1') {
      return { rows: [{ '?column?': 1 }], rowCount: 1 };
    }
    if (q === 'BEGIN' || q === 'COMMIT' || q === 'ROLLBACK') {
      return { rows: [], rowCount: 0 };
    }

    // SELECT all for admin
    if (q.startsWith('SELECT id, title, image_url, target_url, link_open_mode, is_active, start_at, end_at, display_order, click_count, impression_count')) {
      const rows = Array.from(this.banners.values()).sort((a, b) => {
        if (a.display_order !== b.display_order) return a.display_order - b.display_order;
        return new Date(b.created_at).getTime() - new Date(a.created_at).getTime();
      });
      return { rows, rowCount: rows.length };
    }

    // SELECT active for public app
    if (q.startsWith('SELECT id, title, image_url, target_url, link_open_mode, is_active, start_at, end_at, display_order, updated_at FROM public.ad_banners')) {
      const now = Date.now();
      const rows = Array.from(this.banners.values())
        .filter((b) => {
          if (!b.is_active) return false;
          if (b.start_at && new Date(b.start_at).getTime() > now) return false;
          if (b.end_at && new Date(b.end_at).getTime() <= now) return false;
          return true;
        })
        .sort((a, b) => {
          if (a.display_order !== b.display_order) return a.display_order - b.display_order;
          return new Date(b.created_at).getTime() - new Date(a.created_at).getTime();
        });
      return { rows, rowCount: rows.length };
    }

    // SELECT * BY id
    if (q === 'SELECT * FROM public.ad_banners WHERE id = $1 LIMIT 1') {
      const found = this.banners.get(params[0]);
      return { rows: found ? [found] : [], rowCount: found ? 1 : 0 };
    }

    // SELECT 1 BY image_url
    if (q === 'SELECT 1 FROM public.ad_banners WHERE image_url = $1 LIMIT 1') {
      const exists = Array.from(this.banners.values()).some((b) => b.image_url === params[0]);
      return { rows: exists ? [{ '?column?': 1 }] : [], rowCount: exists ? 1 : 0 };
    }

    // INSERT
    if (q.startsWith('INSERT INTO public.ad_banners')) {
      const nowIso = new Date().toISOString();
      const row = {
        id: params[0],
        title: params[1],
        image_url: params[2],
        target_url: params[3],
        link_open_mode: params[4],
        is_active: Boolean(params[5]),
        start_at: params[6],
        end_at: params[7],
        display_order: Number(params[8]),
        click_count: 0,
        impression_count: 0,
        created_at: nowIso,
        updated_at: nowIso
      };
      this.banners.set(row.id, row);
      return { rows: [{ ...row }], rowCount: 1 };
    }

    // UPDATE full
    if (q.startsWith('UPDATE public.ad_banners SET title = $1')) {
      const id = params[8];
      const row = this.banners.get(id);
      if (!row) return { rows: [], rowCount: 0 };
      Object.assign(row, {
        title: params[0],
        image_url: params[1],
        target_url: params[2],
        link_open_mode: params[3],
        is_active: Boolean(params[4]),
        start_at: params[5],
        end_at: params[6],
        display_order: Number(params[7]),
        updated_at: new Date().toISOString()
      });
      return { rows: [{ ...row }], rowCount: 1 };
    }

    // UPDATE reorder
    if (q.startsWith('UPDATE public.ad_banners SET display_order = $1')) {
      const row = this.banners.get(params[1]);
      if (!row) return { rows: [], rowCount: 0 };
      row.display_order = Number(params[0]);
      row.updated_at = new Date().toISOString();
      return { rows: [{ ...row }], rowCount: 1 };
    }

    // TOGGLE is_active
    if (q.startsWith('UPDATE public.ad_banners SET is_active = NOT is_active')) {
      const row = this.banners.get(params[0]);
      if (!row) return { rows: [], rowCount: 0 };
      row.is_active = !row.is_active;
      row.updated_at = new Date().toISOString();
      return { rows: [{ ...row }], rowCount: 1 };
    }

    // INCREMENT impression_count
    if (q.startsWith('UPDATE public.ad_banners SET impression_count = impression_count + 1')) {
      const row = this.banners.get(params[0]);
      const now = Date.now();
      if (
        !row ||
        !row.is_active ||
        (row.start_at && new Date(row.start_at).getTime() > now) ||
        (row.end_at && new Date(row.end_at).getTime() <= now)
      ) {
        return { rows: [], rowCount: 0 };
      }
      row.impression_count += 1;
      return { rows: [{ id: row.id }], rowCount: 1 };
    }

    // INCREMENT click_count
    if (q.startsWith('UPDATE public.ad_banners SET click_count = click_count + 1')) {
      const row = this.banners.get(params[0]);
      const now = Date.now();
      if (
        !row ||
        !row.is_active ||
        (row.start_at && new Date(row.start_at).getTime() > now) ||
        (row.end_at && new Date(row.end_at).getTime() <= now)
      ) {
        return { rows: [], rowCount: 0 };
      }
      row.click_count += 1;
      return { rows: [{ id: row.id }], rowCount: 1 };
    }

    // DELETE
    if (q.startsWith('DELETE FROM public.ad_banners WHERE id = $1')) {
      const row = this.banners.get(params[0]);
      if (!row) return { rows: [], rowCount: 0 };
      this.banners.delete(params[0]);
      return { rows: [{ id: row.id, image_url: row.image_url }], rowCount: 1 };
    }

    return { rows: [], rowCount: 0 };
  }
}

async function runTests() {
  console.log('====================================================');
  console.log('[AdBanners Tests] Starting Full Backend API Verification');
  console.log('====================================================');

  const testPool = new InMemoryAdBannersPool();
  app.setTestPool(testPool);

  const server = http.createServer(app);
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
  const { port } = server.address();
  const baseUrl = `http://127.0.0.1:${port}`;

  const uploadedLocalFilesToClean = [];

  try {
    // 1. Security: unauthenticated requests to admin endpoints return 401
    for (const [method, endpoint] of [
      ['GET', '/api/admin/ad-banners'],
      ['POST', '/api/admin/ad-banners'],
      ['PUT', '/api/admin/ad-banners/ad_1'],
      ['PATCH', '/api/admin/ad-banners/ad_1/toggle'],
      ['PATCH', '/api/admin/ad-banners/reorder'],
      ['DELETE', '/api/admin/ad-banners/ad_1'],
      ['POST', '/api/admin/ad-banners/upload-image']
    ]) {
      const unauthRes = await fetch(`${baseUrl}${endpoint}`, { method });
      assert.strictEqual(unauthRes.status, 401, `${method} ${endpoint} must require admin auth`);
    }
    console.log('  ✓ Test 1 Passed: All admin banner routes enforce 401 without valid ADMIN_KEY.');

    // 2. Upload valid PNG image & reject invalid/oversized image
    const validPngBytes = Buffer.from([
      0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
      0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
      0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
      0x08, 0x02, 0x00, 0x00, 0x00, 0x90, 0x77, 0x53, 0xDE
    ]);
    const validForm = new FormData();
    validForm.append('image', new Blob([validPngBytes], { type: 'image/png' }), 'promo-banner.png');
    const uploadRes = await fetch(`${baseUrl}/api/admin/ad-banners/upload-image`, {
      method: 'POST',
      headers: { 'x-admin-key': TEST_ADMIN_KEY },
      body: validForm
    });
    const uploadJson = await uploadRes.json();
    assert.strictEqual(uploadRes.status, 200);
    assert.strictEqual(uploadJson.success, true);
    assert.ok(uploadJson.url.startsWith('/uploads/ad-banners/'), `Unexpected upload URL: ${uploadJson.url}`);
    uploadedLocalFilesToClean.push(path.join(__dirname, 'public', uploadJson.url));

    // Verify uploaded file is downloadable via Express static route
    const imgFetch = await fetch(`${baseUrl}${uploadJson.url}`);
    assert.strictEqual(imgFetch.status, 200);

    // Reject fake PNG (invalid magic bytes)
    const fakeForm = new FormData();
    fakeForm.append('image', new Blob([Buffer.from('not a real png file content')], { type: 'image/png' }), 'fake.png');
    const fakeRes = await fetch(`${baseUrl}/api/admin/ad-banners/upload-image`, {
      method: 'POST',
      headers: { 'x-admin-key': TEST_ADMIN_KEY },
      body: fakeForm
    });
    assert.strictEqual(fakeRes.status, 400);

    // Reject unsupported mimetype
    const txtForm = new FormData();
    txtForm.append('image', new Blob([Buffer.from('hello world text file')], { type: 'text/plain' }), 'bad.txt');
    const txtRes = await fetch(`${baseUrl}/api/admin/ad-banners/upload-image`, {
      method: 'POST',
      headers: { 'x-admin-key': TEST_ADMIN_KEY },
      body: txtForm
    });
    assert.strictEqual(txtRes.status, 400);
    console.log('  ✓ Test 2 Passed: Image upload saves valid PNG, serves statically, and rejects invalid/fake files with 400.');

    // 3. Target URL validation rejects unsafe protocols
    for (const badUrl of [
      'javascript:alert(1)',
      'data:text/html,<script>alert(1)</script>',
      'ftp://example.com/file',
      'not-a-url',
      'https://user:pass@evil.com/phish',
      ''
    ]) {
      const badCreate = await fetch(`${baseUrl}/api/admin/ad-banners`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'x-admin-key': TEST_ADMIN_KEY },
        body: JSON.stringify({
          title: 'تست لینک بد',
          image_url: uploadJson.url,
          target_url: badUrl,
          is_active: true
        })
      });
      assert.strictEqual(badCreate.status, 400, `Expected 400 for unsafe URL: ${badUrl}`);
    }
    console.log('  ✓ Test 3 Passed: Unsafe and malformed target URLs strictly rejected (HTTP 400).');

    // 4. Create multiple banners (active ordered 2, active ordered 1, expired, scheduled, inactive)
    const now = Date.now();
    const createBanner = async (payload) => {
      const r = await fetch(`${baseUrl}/api/admin/ad-banners`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'x-admin-key': TEST_ADMIN_KEY },
        body: JSON.stringify(payload)
      });
      const j = await r.json();
      assert.strictEqual(r.status, 201, `Create failed: ${JSON.stringify(j)}`);
      return j.banner;
    };

    const bannerSecond = await createBanner({
      title: 'تبلیغ دوم',
      image_url: uploadJson.url,
      target_url: 'https://example.com/second',
      link_open_mode: 'external',
      is_active: true,
      display_order: 2
    });
    const bannerFirst = await createBanner({
      title: 'تبلیغ اول',
      image_url: 'https://cdn.example.com/banner1.jpg',
      target_url: 'https://example.com/first',
      link_open_mode: 'internal',
      is_active: true,
      display_order: 1
    });
    const bannerExpired = await createBanner({
      title: 'تبلیغ منقضی',
      image_url: 'https://cdn.example.com/expired.jpg',
      target_url: 'https://example.com/expired',
      is_active: true,
      start_at: new Date(now - 7200000).toISOString(),
      end_at: new Date(now - 60000).toISOString(),
      display_order: 3
    });
    const bannerScheduled = await createBanner({
      title: 'تبلیغ آینده',
      image_url: 'https://cdn.example.com/future.jpg',
      target_url: 'https://example.com/future',
      is_active: true,
      start_at: new Date(now + 3600000).toISOString(),
      end_at: new Date(now + 7200000).toISOString(),
      display_order: 4
    });
    const bannerInactive = await createBanner({
      title: 'تبلیغ غیرفعال',
      image_url: 'https://cdn.example.com/inactive.jpg',
      target_url: 'https://example.com/inactive',
      is_active: false,
      display_order: 5
    });

    // Public active listing must ONLY return bannerFirst then bannerSecond, ordered by display_order
    const activeRes = await fetch(`${baseUrl}/api/ad-banners/active`);
    assert.strictEqual(activeRes.status, 200);
    const activeJson = await activeRes.json();
    assert.strictEqual(activeJson.success, true);
    assert.strictEqual(activeJson.banners.length, 2);
    assert.strictEqual(activeJson.banners[0].id, bannerFirst.id);
    assert.strictEqual(activeJson.banners[0].linkOpenMode, 'internal');
    assert.strictEqual(activeJson.banners[1].id, bannerSecond.id);
    assert.strictEqual(activeJson.banners[1].linkOpenMode, 'external');
    assert.strictEqual(activeJson.banners[0].click_count, undefined, 'Public endpoint must not expose admin click_count');
    console.log('  ✓ Test 4 Passed: Active endpoint returns only valid active non-expired banners sorted by display_order.');

    // 5. Admin listing exposes computed statuses & summary counts
    const adminListRes = await fetch(`${baseUrl}/api/admin/ad-banners`, {
      headers: { 'x-admin-key': TEST_ADMIN_KEY }
    });
    const adminListJson = await adminListRes.json();
    assert.strictEqual(adminListRes.status, 200);
    assert.strictEqual(adminListJson.summary.total, 5);
    assert.strictEqual(adminListJson.summary.activeCount, 2);
    assert.strictEqual(adminListJson.summary.expiredCount, 1);
    assert.strictEqual(adminListJson.summary.scheduledCount, 1);
    assert.strictEqual(adminListJson.summary.inactiveCount, 1);
    console.log('  ✓ Test 5 Passed: Admin listing computes active/inactive/scheduled/expired statuses accurately.');

    // 6. Edit image, target_url, and link_open_mode
    const editRes = await fetch(`${baseUrl}/api/admin/ad-banners/${bannerFirst.id}`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json', 'x-admin-key': TEST_ADMIN_KEY },
      body: JSON.stringify({
        title: 'تبلیغ اول ویرایش‌شده',
        image_url: 'https://cdn.example.com/banner1-updated.jpg',
        target_url: 'https://example.com/updated-landing',
        link_open_mode: 'external'
      })
    });
    const editJson = await editRes.json();
    assert.strictEqual(editRes.status, 200);
    assert.strictEqual(editJson.banner.target_url, 'https://example.com/updated-landing');
    assert.strictEqual(editJson.banner.image_url, 'https://cdn.example.com/banner1-updated.jpg');
    assert.strictEqual(editJson.banner.link_open_mode, 'external');
    console.log('  ✓ Test 6 Passed: Admin can edit banner image, target URL, and open mode.');

    // 7. Reorder banners
    const reorderRes = await fetch(`${baseUrl}/api/admin/ad-banners/reorder`, {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json', 'x-admin-key': TEST_ADMIN_KEY },
      body: JSON.stringify({
        items: [
          { id: bannerSecond.id, display_order: 1 },
          { id: bannerFirst.id, display_order: 2 }
        ]
      })
    });
    assert.strictEqual(reorderRes.status, 200);
    const activeAfterReorder = await (await fetch(`${baseUrl}/api/ad-banners/active`)).json();
    assert.strictEqual(activeAfterReorder.banners[0].id, bannerSecond.id);
    assert.strictEqual(activeAfterReorder.banners[1].id, bannerFirst.id);
    console.log('  ✓ Test 7 Passed: Reorder endpoint updates live display order.');

    // 8. Impression & click tracking + single-tap nonce deduplication + expired banner rejection
    const impRes = await fetch(`${baseUrl}/api/ad-banners/${bannerFirst.id}/impression`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ userId: 'usr_100' })
    });
    assert.strictEqual(impRes.status, 200);
    assert.strictEqual((await impRes.json()).recorded, true);

    const clickRes1 = await fetch(`${baseUrl}/api/ad-banners/${bannerFirst.id}/click`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ userId: 'usr_100', tapNonce: 'nonce_1' })
    });
    assert.strictEqual(clickRes1.status, 200);
    assert.strictEqual((await clickRes1.json()).recorded, true);

    // Duplicate same tapNonce must NOT increment again
    const clickResDup = await fetch(`${baseUrl}/api/ad-banners/${bannerFirst.id}/click`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ userId: 'usr_100', tapNonce: 'nonce_1' })
    });
    assert.strictEqual(clickResDup.status, 200);
    assert.strictEqual((await clickResDup.json()).deduplicated, true);

    // Clicking expired or inactive banner returns 404
    const clickExpired = await fetch(`${baseUrl}/api/ad-banners/${bannerExpired.id}/click`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ userId: 'usr_100', tapNonce: 'nonce_exp' })
    });
    assert.strictEqual(clickExpired.status, 404);

    const statsCheck = await (await fetch(`${baseUrl}/api/admin/ad-banners`, {
      headers: { 'x-admin-key': TEST_ADMIN_KEY }
    })).json();
    const updatedFirst = statsCheck.banners.find((b) => b.id === bannerFirst.id);
    assert.strictEqual(updatedFirst.impression_count, 1);
    assert.strictEqual(updatedFirst.click_count, 1);
    assert.strictEqual(updatedFirst.ctr_percent, 100);
    console.log('  ✓ Test 8 Passed: Impression & click tracking increment cleanly with double-tap deduplication.');

    // 9. Toggle active -> false hides banner from public active endpoint
    const toggleRes = await fetch(`${baseUrl}/api/admin/ad-banners/${bannerFirst.id}/toggle`, {
      method: 'PATCH',
      headers: { 'x-admin-key': TEST_ADMIN_KEY }
    });
    assert.strictEqual(toggleRes.status, 200);
    const activeAfterToggle = await (await fetch(`${baseUrl}/api/ad-banners/active`)).json();
    assert.strictEqual(activeAfterToggle.banners.length, 1);
    assert.strictEqual(activeAfterToggle.banners[0].id, bannerSecond.id);
    console.log('  ✓ Test 9 Passed: Toggling banner inactive immediately excludes it from public active list.');

    // 10. Delete banners (and confirm local uploaded file cleaned up on bannerSecond deletion)
    for (const b of [bannerFirst, bannerSecond, bannerExpired, bannerScheduled, bannerInactive]) {
      const delRes = await fetch(`${baseUrl}/api/admin/ad-banners/${b.id}`, {
        method: 'DELETE',
        headers: { 'x-admin-key': TEST_ADMIN_KEY }
      });
      assert.strictEqual(delRes.status, 200);
    }
    const activeAfterDelete = await (await fetch(`${baseUrl}/api/ad-banners/active`)).json();
    assert.strictEqual(activeAfterDelete.banners.length, 0);
    assert.strictEqual(fs.existsSync(uploadedLocalFilesToClean[0]), false, 'Local file should be cleaned up on banner delete');
    console.log('  ✓ Test 10 Passed: Deleting banners removes DB records and unreferenced local files.');

    // 11. Existing health & login-notification routes unaffected
    const healthRes = await fetch(`${baseUrl}/health`);
    assert.strictEqual(healthRes.status, 200);
    const loginNotifRes = await fetch(`${baseUrl}/api/app/login-notification`);
    assert.strictEqual(loginNotifRes.status, 200);
    console.log('  ✓ Test 11 Passed: Existing server routes remain intact.');

    console.log('====================================================');
    console.log('[AdBanners Tests] ALL 11 BACKEND INTEGRATION TESTS PASSED!');
    console.log('====================================================');
  } finally {
    for (const f of uploadedLocalFilesToClean) {
      try {
        if (fs.existsSync(f)) fs.unlinkSync(f);
      } catch (_e) {}
    }
    await new Promise((resolve) => server.close(resolve));
  }
}

runTests().catch((err) => {
  console.error('[AdBanners Tests FAILED]', err);
  process.exit(1);
});
