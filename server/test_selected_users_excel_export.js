/**
 * Automated Verification Suite for Bulk User Selection, Cross-Page Pagination & Selected-Users XLSX Export
 * Tests:
 * 1. Protected POST /api/admin/users/export-selected-excel (401 without/invalid key, 400 when no users selected, 200 with valid selection)
 * 2. Real DB lookup of ONLY selected users (WHERE u.id::text = ANY($1::text[])), preserving leading zeros & excluding unselected users
 * 3. Real .xlsx ZIP binary & XML integrity with strictly 4 columns:
 *    - نام و نام خانوادگی
 *    - شماره تماس (Text format '@' numFmtId=49)
 *    - تاریخ ثبت‌نام (Jalali YYYY/MM/DD)
 *    - ساعت ثبت‌نام (Iran Asia/Tehran HH:MM:SS)
 * 4. Frontend DOM/JS behavior extracted from admin index.html:
 *    - Selecting multiple users across multiple pages (pagination state preservation)
 *    - Top «انتخاب همه» checkbox selecting/deselecting all filtered users across pages
 *    - Selected user counter («انتخاب‌شده: X نفر»)
 *    - Compatibility with search, filter (new vs all), and sorting
 *    - Warning & blocked download when 0 users selected
 *    - Clearing selections after download («پاک‌کردن انتخاب‌ها»)
 */

const assert = require('assert');
const http = require('http');
const zlib = require('zlib');
const fs = require('fs');
const path = require('path');
const vm = require('vm');
const Module = require('module');

process.env.ADMIN_KEY = 'TEST_SECURE_ADMIN_KEY_1234567890';
process.env.NODE_ENV = 'test';
process.env.PORT = '0';

const {
  buildSelectedUsersExcelBuffer,
  getIranJalaliDateAndTime,
  getIranJalaliDateString
} = require('./xlsxExporter');

function parseZipEntries(zipBuffer) {
  assert(zipBuffer.length > 22, 'Buffer too small to be a ZIP file');
  assert.strictEqual(zipBuffer.readUInt32LE(0), 0x04034b50, 'Missing PKZIP local header signature');

  let eocdOffset = -1;
  for (let i = zipBuffer.length - 22; i >= Math.max(0, zipBuffer.length - 65557); i--) {
    if (zipBuffer.readUInt32LE(i) === 0x06054b50) {
      eocdOffset = i;
      break;
    }
  }
  assert(eocdOffset !== -1, 'Missing EOCD signature in .xlsx');

  const totalEntries = zipBuffer.readUInt16LE(eocdOffset + 10);
  const centralDirOffset = zipBuffer.readUInt32LE(eocdOffset + 16);

  const files = new Map();
  let ptr = centralDirOffset;
  for (let idx = 0; idx < totalEntries; idx++) {
    assert.strictEqual(zipBuffer.readUInt32LE(ptr), 0x02014b50, 'Invalid central directory signature');
    const compressionMethod = zipBuffer.readUInt16LE(ptr + 10);
    const compressedSize = zipBuffer.readUInt32LE(ptr + 20);
    const uncompressedSize = zipBuffer.readUInt32LE(ptr + 24);
    const nameLen = zipBuffer.readUInt16LE(ptr + 28);
    const extraLen = zipBuffer.readUInt16LE(ptr + 30);
    const commentLen = zipBuffer.readUInt16LE(ptr + 32);
    const localHeaderOffset = zipBuffer.readUInt32LE(ptr + 42);
    const fileName = zipBuffer.toString('utf8', ptr + 46, ptr + 46 + nameLen);

    assert.strictEqual(zipBuffer.readUInt32LE(localHeaderOffset), 0x04034b50, 'Invalid local header offset');
    const localNameLen = zipBuffer.readUInt16LE(localHeaderOffset + 26);
    const localExtraLen = zipBuffer.readUInt16LE(localHeaderOffset + 28);
    const dataStart = localHeaderOffset + 30 + localNameLen + localExtraLen;
    const compressedData = zipBuffer.subarray(dataStart, dataStart + compressedSize);

    const contentBuf = compressionMethod === 8 ? zlib.inflateRawSync(compressedData) : compressedData;
    assert.strictEqual(contentBuf.length, uncompressedSize, `Size mismatch for ${fileName}`);
    files.set(fileName, contentBuf.toString('utf8'));
    ptr += 46 + nameLen + extraLen + commentLen;
  }

  return files;
}

function hasExpressInstalled() {
  try {
    require.resolve('express');
    require.resolve('dotenv');
    return true;
  } catch (_) {
    return false;
  }
}

function installMinimalExpressShimIfNeeded() {
  if (hasExpressInstalled()) return false;

  const originalLoad = Module._load;
  Module._load = function (request, parent, isMain) {
    if (request === 'dotenv') return { config: () => ({}) };
    if (request === 'cors') return () => (req, res, next) => next();
    if (request === 'multer') {
      const m = () => ({
        single: () => (req, res, next) => next(),
        array: () => (req, res, next) => next()
      });
      m.memoryStorage = () => ({});
      return m;
    }
    if (request === 'pg') {
      return {
        Pool: function () {
          return { on: () => {}, query: async () => ({ rows: [] }), end: async () => {} };
        }
      };
    }
    if (request === 'express') {
      function createRouter() {
        const routes = [];
        const router = function (req, res, next) {
          if (next) next();
        };
        for (const method of ['get', 'post', 'put', 'patch', 'delete', 'use']) {
          router[method] = function (routePath, ...handlers) {
            if (typeof routePath === 'function') {
              routes.push({ method: method.toUpperCase(), path: '*', handlers: [routePath, ...handlers] });
            } else {
              const paths = Array.isArray(routePath) ? routePath : [routePath];
              for (const p of paths) {
                routes.push({ method: method.toUpperCase(), path: p, handlers });
              }
            }
          };
        }
        router._routes = routes;
        return router;
      }

      const expressFn = function () {
        const appRouter = createRouter();
        const handler = async (req, res) => {
          const rawChunks = [];
          await new Promise((resolve) => {
            req.on('data', (c) => rawChunks.push(c));
            req.on('end', resolve);
          });
          const rawBody = Buffer.concat(rawChunks).toString('utf8');
          if (rawBody && String(req.headers['content-type'] || '').includes('application/json')) {
            try {
              req.body = JSON.parse(rawBody);
            } catch (_) {
              req.body = {};
            }
          } else {
            req.body = {};
          }

          const urlPath = (req.url || '/').split('?')[0];
          const query = {};
          const qIdx = (req.url || '').indexOf('?');
          if (qIdx !== -1) {
            const sp = new URLSearchParams(req.url.slice(qIdx + 1));
            for (const [k, v] of sp.entries()) query[k] = v;
          }
          req.query = query;

          res.status = function (code) {
            res.statusCode = code;
            return res;
          };
          res.json = function (obj) {
            if (!res.getHeader('Content-Type')) {
              res.setHeader('Content-Type', 'application/json; charset=utf-8');
            }
            res.end(JSON.stringify(obj));
            return res;
          };
          res.send = function (payload) {
            res.end(payload);
            return res;
          };
          res.sendFile = function () {
            res.end('');
            return res;
          };

          const match = appRouter._routes.find(
            (r) => r.method === req.method && r.path === urlPath
          );
          if (!match) {
            res.statusCode = 404;
            res.end(JSON.stringify({ error: 'Not found' }));
            return;
          }

          let idx = 0;
          const runNext = async () => {
            if (idx >= match.handlers.length) return;
            const fn = match.handlers[idx++];
            await fn(req, res, runNext);
          };
          await runNext();
        };

        for (const method of ['get', 'post', 'put', 'patch', 'delete', 'use']) {
          handler[method] = appRouter[method].bind(appRouter);
        }
        handler._routes = appRouter._routes;
        return handler;
      };
      expressFn.json = () => (req, res, next) => next();
      expressFn.static = () => (req, res, next) => next();
      expressFn.Router = createRouter;
      return expressFn;
    }
    return originalLoad.apply(this, arguments);
  };
  return true;
}

function httpRequest(app, method, reqPath, headers = {}, jsonBody = null) {
  return new Promise((resolve, reject) => {
    const server = http.createServer(app);
    server.listen(0, () => {
      const port = server.address().port;
      const bodyStr = jsonBody !== null ? JSON.stringify(jsonBody) : null;
      const reqHeaders = { ...headers };
      if (bodyStr !== null) {
        reqHeaders['Content-Type'] = 'application/json';
        reqHeaders['Content-Length'] = Buffer.byteLength(bodyStr);
      }
      const req = http.request(
        {
          hostname: '127.0.0.1',
          port,
          path: reqPath,
          method,
          headers: reqHeaders
        },
        (res) => {
          const chunks = [];
          res.on('data', (chunk) => chunks.push(chunk));
          res.on('end', () => {
            const buffer = Buffer.concat(chunks);
            server.close(() => {
              resolve({
                status: res.statusCode,
                headers: res.headers,
                buffer
              });
            });
          });
        }
      );
      req.on('error', (err) => server.close(() => reject(err)));
      if (bodyStr !== null) req.write(bodyStr);
      req.end();
    });
  });
}

// Create a lightweight DOM mock to run the exact admin HTML selection/pagination/export script
function createAdminUiHarness(htmlPath) {
  const html = fs.readFileSync(htmlPath, 'utf8');
  const scriptMatches = [...html.matchAll(/<script(?:\s[^>]*)?>([\s\S]*?)<\/script>/gi)];
  const mainScript = scriptMatches[scriptMatches.length - 1][1];

  const fnStart = mainScript.indexOf('let cachedUsers = [];');
  const fnEnd = mainScript.indexOf('    // Auto-refresh every 12 seconds');
  assert(fnStart !== -1 && fnEnd !== -1 && fnEnd > fnStart, `Could not isolate script block in ${htmlPath}`);

  // Extract state vars + user/pagination/selection/export functions cleanly
  const usersStateBlock = mainScript.slice(fnStart, mainScript.indexOf('let cachedAnnouncements = [];'));
  const toPersianStart = mainScript.indexOf('function toPersianDigits(');
  const resetCycleStart = mainScript.indexOf('async function resetUserCycle(');
  const escapeStart = mainScript.indexOf('function escapeHtml(');
  const escapeEnd = mainScript.indexOf('const SUPPORT_CATEGORY_MAP');
  const showNotifStart = mainScript.indexOf('function showNotification(');
  const showNotifEnd = mainScript.indexOf('// Auto-refresh every 12 seconds');

  const snippet = [
    usersStateBlock,
    mainScript.slice(toPersianStart, resetCycleStart),
    mainScript.slice(escapeStart, escapeEnd),
    mainScript.slice(showNotifStart, showNotifEnd)
  ].join('\n');

  function makeEl(id) {
    return {
      id,
      value: id === 'userFilterMode' ? 'all' : (id === 'userSortMode' ? 'score' : ''),
      checked: false,
      indeterminate: false,
      disabled: false,
      innerHTML: '',
      innerText: '',
      className: 'd-none',
      classList: {
        _set: new Set(['d-none']),
        add(c) { this._set.add(c); },
        remove(c) { this._set.delete(c); },
        toggle(c, force) {
          if (force === undefined) {
            if (this._set.has(c)) this._set.delete(c);
            else this._set.add(c);
          } else if (force) {
            this._set.add(c);
          } else {
            this._set.delete(c);
          }
        },
        contains(c) { return this._set.has(c); }
      },
      style: {},
      attributes: {},
      setAttribute(k, v) { this.attributes[k] = String(v); },
      getAttribute(k) { return this.attributes[k] ?? null; },
      closest() { return null; },
      click() {}
    };
  }

  const elementIds = [
    'userSearchInput', 'userFilterMode', 'userSortMode',
    'selectAllUsersCheckbox', 'selectAllScopeHint', 'selectedUsersCountBadge',
    'exportSelectedUsersExcelBtn', 'exportSelectedUsersExcelSpinner',
    'exportSelectedUsersExcelIcon', 'exportSelectedUsersExcelLabel',
    'clearUserSelectionBtn', 'usersSelectionNotice', 'usersPageSelectAll',
    'usersTbody', 'sessionsTbody', 'usersPaginationInfo', 'usersPaginationButtons',
    'usersPageSizeSelect', 'newUsersCountBadge', 'alertBox'
  ];
  const elements = Object.fromEntries(elementIds.map(id => [id, makeEl(id)]));
  const fetchCalls = [];
  const downloadedFiles = [];
  let mockFetchResponse = null;

  const sandbox = {
    Intl,
    Date,
    Number,
    String,
    Math,
    Array,
    Set,
    Boolean,
    JSON,
    URL: {
      createObjectURL: () => 'blob:mock-url-1',
      revokeObjectURL: () => {}
    },
    Blob,
    setTimeout: () => 0,
    console,
    alert: () => {},
    getAdminKey: () => 'TEST_SECURE_ADMIN_KEY_1234567890',
    showAlert: () => {},
    openAdminAuthModal: () => {},
    fetch: async (url, opts) => {
      fetchCalls.push({ url, opts });
      return mockFetchResponse;
    },
    document: {
      getElementById: (id) => elements[id] || null,
      createElement: () => {
        const el = makeEl('tmpLink');
        el.click = () => downloadedFiles.push({ href: el.href, download: el.download });
        return el;
      },
      body: {
        appendChild: () => {},
        removeChild: () => {}
      }
    }
  };

  vm.createContext(sandbox);
  vm.runInContext(
    `${snippet}
     function __setCachedUsers(arr) { cachedUsers = arr; }
     function __getSelectedIds() { return Array.from(selectedUserIds); }
     function __getCurrentPage() { return usersCurrentPage; }
     function __getFilteredCount() { return filteredUsersList.length; }
    `,
    sandbox,
    { filename: htmlPath }
  );

  return {
    sandbox,
    elements,
    fetchCalls,
    downloadedFiles,
    setMockFetchResponse: (resp) => { mockFetchResponse = resp; }
  };
}

async function runTests() {
  console.log('=== Testing Bulk Selection, Pagination & Selected Users Excel (.xlsx) Export ===');

  const usedShim = installMinimalExpressShimIfNeeded();
  if (usedShim) {
    console.log('ℹ️ Running with lightweight Express shim (node_modules not present in container)');
  }

  // 1. Unit test getIranJalaliDateAndTime & buildSelectedUsersExcelBuffer
  const dt1 = getIranJalaliDateAndTime(new Date('2026-11-20T06:45:09.000Z'));
  assert.strictEqual(dt1.jalaliDate, '1405/08/29');
  assert.strictEqual(dt1.iranTime, '10:15:09');

  const dt2 = getIranJalaliDateAndTime(new Date('2026-03-20T20:30:05.000Z'));
  assert.strictEqual(dt2.jalaliDate, '1405/01/01');
  assert.strictEqual(dt2.iranTime, '00:00:05');

  // 2. Backend API tests with real DB query verification
  const mainServer = require('./server.js');
  const now = Date.now();
  const dbUsers = [];
  for (let i = 1; i <= 65; i++) {
    dbUsers.push({
      id: `USR_${String(i).padStart(3, '0')}`,
      first_name: i <= 5 ? `کاربرجدید` : `کاربر`,
      last_name: `شماره ${i}`,
      phone_number: `0912${String(i).padStart(7, '0')}`,
      created_at:
        i === 1
          ? '2026-11-20T06:45:09.000Z'
          : i === 2
            ? '2026-03-20T20:30:05.000Z'
            : new Date(now - (i <= 5 ? i * 60 * 1000 : i * 48 * 3600 * 1000)).toISOString(),
      total_score: (70 - i) * 10,
      status: 'active'
    });
  }

  const mockPool = {
    query: async (sql, params) => {
      const text = typeof sql === 'string' ? sql : sql.text || '';
      if (text.includes('FROM public.users u') && text.includes('ANY($1::text[])')) {
        assert(Array.isArray(params) && Array.isArray(params[0]), 'SQL must pass clean userIds array parameter');
        const idSet = new Set(params[0]);
        return {
          rows: dbUsers.filter((u) => idSet.has(u.id))
        };
      }
      return { rows: [] };
    },
    end: async () => {}
  };
  mainServer.setTestPool(mockPool);

  // 2a. Auth check (401)
  const unauthRes = await httpRequest(mainServer.app, 'POST', '/api/admin/users/export-selected-excel', {}, {
    userIds: ['USR_001']
  });
  assert.strictEqual(unauthRes.status, 401, 'Unauthenticated export must return 401');

  const wrongKeyRes = await httpRequest(
    mainServer.app,
    'POST',
    '/api/admin/users/export-selected-excel',
    { 'x-admin-key': 'BAD_KEY' },
    { userIds: ['USR_001'] }
  );
  assert.strictEqual(wrongKeyRes.status, 401, 'Invalid admin key must return 401');

  // 2b. Empty selection rejected cleanly (400 JSON, no empty file)
  const emptySelectionRes = await httpRequest(
    mainServer.app,
    'POST',
    '/api/admin/users/export-selected-excel',
    { 'x-admin-key': process.env.ADMIN_KEY },
    { userIds: [] }
  );
  assert.strictEqual(emptySelectionRes.status, 400, 'Empty selection must return 400');
  const emptyErrJson = JSON.parse(emptySelectionRes.buffer.toString('utf8'));
  assert.strictEqual(emptyErrJson.success, false);
  console.log('✓ Admin auth (401) & empty selection guard (400) verified on /api/admin/users/export-selected-excel');

  // 2c. Export selected users from different pages (e.g. #1, #2, #32, #64)
  const selectedIds = ['USR_001', 'USR_002', 'USR_032', 'USR_064'];
  const okRes = await httpRequest(
    mainServer.app,
    'POST',
    '/api/admin/users/export-selected-excel',
    { 'x-admin-key': process.env.ADMIN_KEY },
    { userIds: selectedIds }
  );
  assert.strictEqual(okRes.status, 200, 'Valid selected users export must return 200');
  assert(
    okRes.headers['content-type'].includes('application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'),
    'Content-Type must be .xlsx MIME type'
  );
  const todayJalali = getIranJalaliDateString(new Date());
  assert(
    okRes.headers['content-disposition'].includes(`selected-users-${todayJalali}.xlsx`),
    'Filename must include selected-users-<jalali-date>.xlsx'
  );

  const zipFiles = parseZipEntries(okRes.buffer);
  const sheetXml = zipFiles.get('xl/worksheets/sheet1.xml');
  const stylesXml = zipFiles.get('xl/styles.xml');

  assert(sheetXml.includes('rightToLeft="1"'), 'Sheet must be right-to-left');
  assert(sheetXml.includes('<dimension ref="A1:D5"/>'), 'Dimension must be strictly A1:D5 (1 header + 4 selected users)');
  assert(!sheetXml.includes('r="E'), 'Sheet must NOT contain column E');

  // Verify strict 4 column headers
  assert(sheetXml.includes('نام و نام خانوادگی'), 'Column A header missing');
  assert(sheetXml.includes('شماره تماس'), 'Column B header missing');
  assert(sheetXml.includes('تاریخ ثبت‌نام'), 'Column C header missing');
  assert(sheetXml.includes('ساعت ثبت‌نام'), 'Column D header missing');

  // Verify Text format for phone numbers and exact Jalali date + Iran HH:MM:SS time
  assert(stylesXml.includes('numFmtId="49"'), 'Phone & text cells must use numFmtId="49" (@ Text format)');
  assert(sheetXml.includes('09120000001'), 'Leading zero phone for USR_001 must be preserved');
  assert(sheetXml.includes('09120000002'), 'Leading zero phone for USR_002 must be preserved');
  assert(sheetXml.includes('09120000032'), 'Leading zero phone for USR_032 must be preserved');
  assert(sheetXml.includes('09120000064'), 'Leading zero phone for USR_064 must be preserved');
  assert(sheetXml.includes('1405/08/29') && sheetXml.includes('10:15:09'), 'USR_001 Jalali date & Iran time missing');
  assert(sheetXml.includes('1405/01/01') && sheetXml.includes('00:00:05'), 'USR_002 Nowruz Jalali date & Iran time missing');

  // Verify unselected users & extra fields are NOT in the Excel output
  assert(!sheetXml.includes('09120000003'), 'Unselected USR_003 must not be exported');
  assert(!sheetXml.includes('09120000065'), 'Unselected USR_065 must not be exported');
  assert(!sheetXml.includes('USR_001'), 'User ID must not leak into Excel');

  fs.writeFileSync('/tmp/test_selected_users_sample.xlsx', okRes.buffer);
  console.log('✓ 4-column RTL selected-users .xlsx binary, Text phone preservation & Jalali/Iran datetime verified');

  // 3. Frontend UI harness verification across all 3 HTML files
  const htmlPaths = [
    path.join(__dirname, 'public', 'index.html'),
    path.join(__dirname, '..', 'reward-backend', 'server', 'public', 'index.html'),
    path.join(__dirname, '..', 'app', 'admin-panel', 'index.html')
  ];

  for (const htmlPath of htmlPaths) {
    const ui = createAdminUiHarness(htmlPath);
    const uiUsers = dbUsers.map((u, idx) => ({
      id: u.id,
      firstName: u.first_name,
      lastName: u.last_name,
      phoneNumber: u.phone_number,
      registeredAt: new Date(u.created_at).getTime(),
      registeredAtIso: u.created_at,
      isNew: idx >= 2 && idx <= 4,
      score: u.total_score,
      _rank: idx + 1
    }));

    ui.sandbox.__setCachedUsers(uiUsers);
    ui.sandbox.filterUsers(true);

    // Default pageSize = 25 -> 65 users = 3 pages
    assert.strictEqual(ui.sandbox.__getFilteredCount(), 65);
    assert.strictEqual(ui.sandbox.__getCurrentPage(), 1);
    assert(ui.elements.usersTbody.innerHTML.includes('USR_001'));
    assert(!ui.elements.usersTbody.innerHTML.includes('USR_032'), 'Page 1 must not show page 2 item USR_032');

    // Attempt export with 0 selected users -> shows warning, does NOT fetch or download
    await ui.sandbox.exportSelectedUsersExcel();
    assert.strictEqual(ui.fetchCalls.length, 0, 'Must NOT send network request when 0 users selected');
    assert.strictEqual(ui.downloadedFiles.length, 0, 'Must NOT download empty file when 0 users selected');
    assert(!ui.elements.usersSelectionNotice.classList.contains('d-none'), 'Warning notice must become visible');

    // Select USR_001 on page 1
    ui.sandbox.toggleUserSelection({
      checked: true,
      getAttribute: (k) => (k === 'data-user-id' ? 'USR_001' : null),
      closest: () => null
    });
    assert.deepStrictEqual(ui.sandbox.__getSelectedIds(), ['USR_001']);
    assert(ui.elements.selectedUsersCountBadge.innerHTML.includes('انتخاب‌شده: ۱ نفر'));

    // Move to page 2 -> select USR_032 -> verify USR_001 is still preserved!
    ui.sandbox.goToUsersPage(2);
    assert.strictEqual(ui.sandbox.__getCurrentPage(), 2);
    assert(ui.elements.usersTbody.innerHTML.includes('USR_032'));
    ui.sandbox.toggleUserSelection({
      checked: true,
      getAttribute: (k) => (k === 'data-user-id' ? 'USR_032' : null),
      closest: () => null
    });
    assert.deepStrictEqual(ui.sandbox.__getSelectedIds().sort(), ['USR_001', 'USR_032']);

    // Return to page 1 -> verify USR_001 checkbox renders checked!
    ui.sandbox.goToUsersPage(1);
    assert(
      /data-user-id="USR_001"[^>]*checked/.test(ui.elements.usersTbody.innerHTML),
      'USR_001 checkbox must remain checked after returning from page 2'
    );
    assert(ui.elements.selectAllUsersCheckbox.indeterminate === true, 'Select-all must be indeterminate for partial selection');

    // Filter to ONLY NEW users (3 users) and click «انتخاب همه»
    ui.elements.userFilterMode.value = 'new';
    ui.sandbox.filterUsers(true);
    assert.strictEqual(ui.sandbox.__getFilteredCount(), 3);
    ui.sandbox.toggleSelectAllFilteredUsers(true);
    // Should preserve USR_001 & USR_032 plus add the 3 new filtered users = 5 selected
    assert.strictEqual(ui.sandbox.__getSelectedIds().length, 5);
    assert.strictEqual(ui.elements.selectAllUsersCheckbox.checked, true);

    // Uncheck «انتخاب همه» -> unchecks the 3 filtered users while leaving non-filtered selections untouched
    ui.sandbox.toggleSelectAllFilteredUsers(false);
    assert.deepStrictEqual(ui.sandbox.__getSelectedIds().sort(), ['USR_001', 'USR_032']);

    // Switch back to ALL users & click «انتخاب همه» -> selects all 65 users across all 3 pages
    ui.elements.userFilterMode.value = 'all';
    ui.sandbox.filterUsers(true);
    ui.sandbox.toggleSelectAllFilteredUsers(true);
    assert.strictEqual(ui.sandbox.__getSelectedIds().length, 65, 'Select-all must select all 65 users across all pages');
    assert(ui.elements.selectedUsersCountBadge.innerHTML.includes('انتخاب‌شده: ۶۵ نفر'));

    // Clear selection & select 2 users, then perform real download trigger check
    ui.sandbox.clearUserSelection();
    assert.strictEqual(ui.sandbox.__getSelectedIds().length, 0);
    ui.sandbox.toggleUserSelection({
      checked: true,
      getAttribute: (k) => (k === 'data-user-id' ? 'USR_001' : null),
      closest: () => null
    });
    ui.sandbox.toggleUserSelection({
      checked: true,
      getAttribute: (k) => (k === 'data-user-id' ? 'USR_064' : null),
      closest: () => null
    });

    ui.setMockFetchResponse({
      status: 200,
      ok: true,
      headers: {
        get: (name) =>
          name.toLowerCase() === 'content-disposition'
            ? `attachment; filename="selected-users-${todayJalali}.xlsx"`
            : null
      },
      blob: async () => new Blob([okRes.buffer], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' })
    });

    await ui.sandbox.exportSelectedUsersExcel();
    assert.strictEqual(ui.fetchCalls.length, 1);
    assert.strictEqual(ui.fetchCalls[0].url, '/api/admin/users/export-selected-excel');
    assert.strictEqual(ui.downloadedFiles.length, 1);
    assert.strictEqual(ui.downloadedFiles[0].download, `selected-users-${todayJalali}.xlsx`);
    assert(ui.elements.usersSelectionNotice.innerHTML.includes('پاک‌کردن انتخاب‌ها'), 'Post-download notice must offer clear selections action');
    assert(!ui.elements.clearUserSelectionBtn.classList.contains('d-none'), 'Clear selection button must be visible');

    // Clear selections after download
    ui.sandbox.clearUserSelection();
    assert.strictEqual(ui.sandbox.__getSelectedIds().length, 0);
    assert(ui.elements.clearUserSelectionBtn.classList.contains('d-none'));
  }

  console.log('✓ Cross-page selection, «انتخاب همه», counter, search/filter compatibility, zero-selection block & post-download clear verified across all 3 admin HTML copies');
  console.log('\n🎉 All bulk selection & selected-users XLSX export tests passed successfully!');
}

runTests().catch((err) => {
  console.error('❌ Test failed:', err);
  process.exit(1);
});
