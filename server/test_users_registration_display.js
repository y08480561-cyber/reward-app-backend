/**
 * Automated Verification Suite for New User Badge & Iran/Jalali Registration DateTime
 * Tests:
 * 1. Strict admin authentication on /api/admin/overview (401 without/invalid key, 200 with valid key)
 * 2. Real database created_at mapping (registeredAt, registeredAtIso, isNew, serverNow)
 * 3. Page refresh immutability (repeated fetches preserve exact registration timestamps)
 * 4. Exact Jalali date + Asia/Tehran HH:MM:SS conversion extracted directly from admin HTML
 * 5. Sync and UI/CSS/DOM element presence across all 3 admin panel HTML copies
 */

const assert = require('assert');
const http = require('http');
const fs = require('fs');
const path = require('path');
const vm = require('vm');
const Module = require('module');

process.env.ADMIN_KEY = 'TEST_SECURE_ADMIN_KEY_1234567890';
process.env.NODE_ENV = 'test';
process.env.PORT = '0';

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
    if (request === 'dotenv') {
      return { config: () => ({}) };
    }
    if (request === 'cors') {
      return () => (req, res, next) => next();
    }
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
          const urlPath = (req.url || '/').split('?')[0];
          const query = {};
          const qIdx = (req.url || '').indexOf('?');
          if (qIdx !== -1) {
            const sp = new URLSearchParams(req.url.slice(qIdx + 1));
            for (const [k, v] of sp.entries()) query[k] = v;
          }
          req.query = query;
          req.body = req.body || {};

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

function httpRequestJson(app, method, reqPath, headers = {}) {
  return new Promise((resolve, reject) => {
    const server = http.createServer(app);
    server.listen(0, () => {
      const port = server.address().port;
      const req = http.request(
        {
          hostname: '127.0.0.1',
          port,
          path: reqPath,
          method,
          headers
        },
        (res) => {
          const chunks = [];
          res.on('data', (chunk) => chunks.push(chunk));
          res.on('end', () => {
            const raw = Buffer.concat(chunks).toString('utf8');
            server.close(() => {
              resolve({
                status: res.statusCode,
                body: raw ? JSON.parse(raw) : null
              });
            });
          });
        }
      );
      req.on('error', (err) => server.close(() => reject(err)));
      req.end();
    });
  });
}

function extractFormatterHelpersFromHtml(htmlPath) {
  const html = fs.readFileSync(htmlPath, 'utf8');
  const scriptMatches = [...html.matchAll(/<script(?:\s[^>]*)?>([\s\S]*?)<\/script>/gi)];
  const mainScript = scriptMatches[scriptMatches.length - 1][1];

  const fnStart = mainScript.indexOf('function toPersianDigits(');
  const fnEnd = mainScript.indexOf('function updateNewUsersSummary(');
  assert(fnStart !== -1 && fnEnd !== -1 && fnEnd > fnStart, `Helper functions missing in ${htmlPath}`);

  const snippet = mainScript.slice(fnStart, fnEnd);
  const sandbox = { Intl, Date, Number, String, Math };
  vm.createContext(sandbox);
  vm.runInContext(snippet, sandbox, { filename: htmlPath });
  return sandbox;
}

async function runTests() {
  console.log('=== Testing New User Badge & Jalali/Iran Registration DateTime ===');

  const usedShim = installMinimalExpressShimIfNeeded();
  if (usedShim) {
    console.log('ℹ️ Running with lightweight Express shim (node_modules not present in container)');
  }

  const mainServer = require('./server.js');
  const now = Date.now();
  const tenMinsAgo = new Date(now - 10 * 60 * 1000);
  const twentyThreeHoursAgo = new Date(now - 23 * 60 * 60 * 1000);
  const twentyFiveHoursAgo = new Date(now - 25 * 60 * 60 * 1000);
  const historicalNowruz = new Date('2026-03-20T20:30:05.000Z'); // 1405/01/01 00:00:05 Tehran
  const historicalOld = new Date('2025-11-22T12:34:56.000Z');

  const dbUsers = [
    {
      id: 'usr_new_10m',
      first_name: 'سارا',
      last_name: 'احمدی',
      phone_number: '09121112233',
      created_at: tenMinsAgo.toISOString(),
      score: 120
    },
    {
      id: 'usr_new_23h',
      first_name: 'امیر',
      last_name: 'حسینی',
      phone_number: '09351112233',
      created_at: twentyThreeHoursAgo.toISOString(),
      score: 40
    },
    {
      id: 'usr_old_25h',
      first_name: 'رضا',
      last_name: 'مرادی',
      phone_number: '09191112233',
      created_at: twentyFiveHoursAgo.toISOString(),
      score: 300
    },
    {
      id: 'usr_old_nowruz',
      first_name: 'مریم',
      last_name: 'کاظمی',
      phone_number: '09101112233',
      created_at: historicalNowruz.toISOString(),
      score: 850
    },
    {
      id: 'usr_old_2025',
      first_name: 'حسین',
      last_name: 'نوری',
      phone_number: '09011112233',
      created_at: historicalOld.toISOString(),
      score: 500
    },
    {
      id: 'usr_null_date',
      first_name: 'کاربر',
      last_name: 'قدیمی',
      phone_number: '09021112233',
      created_at: null,
      score: 10
    }
  ];

  const mockPool = {
    query: async (sql) => {
      const text = typeof sql === 'string' ? sql : sql.text || '';
      if (text.includes('FROM public.users u')) {
        assert(text.includes('u.created_at'), 'Admin overview query must read real u.created_at from DB');
        return { rows: dbUsers };
      }
      if (text.includes('COUNT(*) AS total_sessions')) {
        return { rows: [{ total_sessions: 0 }] };
      }
      return { rows: [] };
    },
    end: async () => {}
  };

  mainServer.setTestPool(mockPool);

  // 1. Auth enforcement
  const unauthRes = await httpRequestJson(mainServer.app, 'GET', '/api/admin/overview');
  assert.strictEqual(unauthRes.status, 401, 'Unauthenticated request must return 401');
  const badKeyRes = await httpRequestJson(mainServer.app, 'GET', '/api/admin/overview', {
    'x-admin-key': 'WRONG_KEY'
  });
  assert.strictEqual(badKeyRes.status, 401, 'Wrong admin key must return 401');
  console.log('✓ Admin authentication enforcement (401) verified on /api/admin/overview');

  // 2. Fetch overview twice (simulating page refresh)
  const res1 = await httpRequestJson(mainServer.app, 'GET', '/api/admin/overview', {
    'x-admin-key': process.env.ADMIN_KEY
  });
  assert.strictEqual(res1.status, 200, 'Authorized request must return 200');
  assert(typeof res1.body.serverNow === 'number', 'Response must include numeric serverNow');
  assert.strictEqual(res1.body.users.length, dbUsers.length, 'All users must be returned');

  const byId1 = Object.fromEntries(res1.body.users.map((u) => [u.id, u]));
  assert.strictEqual(byId1.usr_new_10m.isNew, true, 'User registered 10m ago must be marked isNew=true');
  assert.strictEqual(byId1.usr_new_10m.registeredAt, tenMinsAgo.getTime());
  assert.strictEqual(byId1.usr_new_23h.isNew, true, 'User registered 23h ago must be marked isNew=true');
  assert.strictEqual(byId1.usr_old_25h.isNew, false, 'User registered 25h ago must be marked isNew=false');
  assert.strictEqual(byId1.usr_old_nowruz.isNew, false, 'Historical user must be marked isNew=false');
  assert.strictEqual(byId1.usr_old_nowruz.registeredAt, historicalNowruz.getTime());
  assert.strictEqual(byId1.usr_old_2025.registeredAt, historicalOld.getTime());
  assert.strictEqual(byId1.usr_null_date.registeredAt, null);
  assert.strictEqual(byId1.usr_null_date.registeredAtIso, null);
  assert.strictEqual(byId1.usr_null_date.isNew, false);

  // Second fetch (page refresh check)
  await new Promise((r) => setTimeout(r, 20));
  const res2 = await httpRequestJson(mainServer.app, 'GET', '/api/admin/overview', {
    'x-admin-key': process.env.ADMIN_KEY
  });
  assert.strictEqual(res2.status, 200);
  const byId2 = Object.fromEntries(res2.body.users.map((u) => [u.id, u]));
  for (const u of dbUsers) {
    assert.strictEqual(
      byId2[u.id].registeredAt,
      byId1[u.id].registeredAt,
      `Refresh must not change registeredAt for ${u.id}`
    );
    assert.strictEqual(
      byId2[u.id].registeredAtIso,
      byId1[u.id].registeredAtIso,
      `Refresh must not change registeredAtIso for ${u.id}`
    );
  }
  console.log('✓ Real DB created_at mapping, isNew 24h window, and refresh immutability verified');

  // 3. Test Jalali + Iran timezone formatter extracted from all 3 HTML files
  const htmlPaths = [
    path.join(__dirname, 'public', 'index.html'),
    path.join(__dirname, '..', 'reward-backend', 'server', 'public', 'index.html'),
    path.join(__dirname, '..', 'app', 'admin-panel', 'index.html')
  ];

  for (const htmlPath of htmlPaths) {
    const html = fs.readFileSync(htmlPath, 'utf8');
    for (const marker of [
      'badge-new-user',
      'user-row-new',
      'reg-datetime-box',
      'newUsersCountBadge',
      'userFilterMode',
      'userSortMode',
      'تاریخ و ساعت ثبت‌نام',
      'colspan="6"',
      'toPersianDigits',
      'gregorianToJalaliParts',
      'formatIranJalaliDateTime',
      'updateNewUsersSummary'
    ]) {
      assert(html.includes(marker), `Missing "${marker}" in ${htmlPath}`);
    }

    const helpers = extractFormatterHelpersFromHtml(htmlPath);

    // 2026-11-20T06:45:09Z -> Tehran +03:30 = 10:15:09 on 2026-11-20 = 1405/08/29
    const f1 = helpers.formatIranJalaliDateTime(new Date('2026-11-20T06:45:09.000Z').getTime());
    assert.strictEqual(f1.dateFa, '۱۴۰۵/۰۸/۲۹', `Expected ۱۴۰۵/۰۸/۲۹ in ${htmlPath}, got ${f1.dateFa}`);
    assert.strictEqual(f1.timeFa, '۱۰:۱۵:۰۹', `Expected ۱۰:۱۵:۰۹ in ${htmlPath}, got ${f1.timeFa}`);

    // Nowruz boundary: 2026-03-20T20:30:05Z -> Tehran = 2026-03-21 00:00:05 = 1405/01/01 00:00:05
    const f2 = helpers.formatIranJalaliDateTime(historicalNowruz.getTime());
    assert.strictEqual(f2.dateFa, '۱۴۰۵/۰۱/۰۱');
    assert.strictEqual(f2.timeFa, '۰۰:۰۰:۰۵');

    // Last second of 1404: 2026-03-20T20:29:59Z -> Tehran = 2026-03-20 23:59:59 = 1404/12/29 23:59:59
    const f3 = helpers.formatIranJalaliDateTime('2026-03-20T20:29:59.000Z');
    assert.strictEqual(f3.dateFa, '۱۴۰۴/۱۲/۲۹');
    assert.strictEqual(f3.timeFa, '۲۳:۵۹:۵۹');

    // Null timestamp
    assert.strictEqual(helpers.formatIranJalaliDateTime(null), null);

    // Repeated formatting returns identical output
    const f1Repeat = helpers.formatIranJalaliDateTime(new Date('2026-11-20T06:45:09.000Z').getTime());
    assert.deepStrictEqual(f1Repeat, f1);
  }
  console.log('✓ Jalali date & Asia/Tehran HH:MM:SS formatting + HTML DOM verified across all 3 admin panel copies');

  console.log('\n🎉 All new-user & registration date/time tests passed successfully!');
}

runTests().catch((err) => {
  console.error('❌ Test failed:', err);
  process.exit(1);
});
