/**
 * Automated Verification Suite for Admin Users Excel (.xlsx) Export
 * Tests:
 * 1. Jalali date filename formatting (e.g. users-1405-08-29.xlsx)
 * 2. Strict admin authentication (401 without/invalid key, 200 with valid key)
 * 3. Valid OOXML .xlsx ZIP binary structure and XML integrity
 * 4. Exactly 2 columns (نام و نام خانوادگی, شماره تماس), RTL sheetView, Text format (@) preserving leading zeros
 * 5. Complete exclusion of score, rank, user ID, account status, and purchase data
 * 6. All database users exported (no pagination truncation) & graceful 500 error handling
 */

const assert = require('assert');
const http = require('http');
const zlib = require('zlib');
const fs = require('fs');
const path = require('path');
const Module = require('module');

process.env.ADMIN_KEY = 'TEST_SECURE_ADMIN_KEY_1234567890';
process.env.NODE_ENV = 'test';
process.env.PORT = '0';

const { buildUsersExcelBuffer, getIranJalaliDateString, gregorianToJalali } = require('./xlsxExporter');

// Minimal ZIP reader to inspect actual generated .xlsx entries
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

    const contentBuf =
      compressionMethod === 8
        ? zlib.inflateRawSync(compressedData)
        : compressedData;

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

// Lightweight fallback loader when running tests in an environment without node_modules
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

function httpRequestBinary(app, method, reqPath, headers = {}) {
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
      req.on('error', (err) => {
        server.close(() => reject(err));
      });
      req.end();
    });
  });
}

async function runTests() {
  console.log('=== Testing Users Excel (.xlsx) Export ===');

  const usedShim = installMinimalExpressShimIfNeeded();
  if (usedShim) {
    console.log('ℹ️ Running with lightweight Express shim (node_modules not present in container)');
  }

  // 1. Test Jalali date helper
  const jalaliSample = gregorianToJalali(2026, 11, 20);
  assert.strictEqual(
    `${jalaliSample.year}-${jalaliSample.month}-${jalaliSample.day}`,
    '1405-08-29',
    'Gregorian 2026-11-20 must map to Jalali 1405-08-29'
  );
  const todayJalali = getIranJalaliDateString(new Date());
  assert(/^\d{4}-\d{2}-\d{2}$/.test(todayJalali), 'Jalali date must be YYYY-MM-DD ASCII digits');
  console.log('✓ Jalali date formatting verified:', `users-${todayJalali}.xlsx`);

  // 2. Load server with isolated mock pool (250+ users to ensure no pagination truncation)
  const mainServer = require('./server.js');
  const sampleUsers = [
    {
      id: 'USR_SECRET_9991',
      first_name: 'علی',
      last_name: 'رضایی',
      phone_number: '09123456789',
      total_score: 450,
      status: 'active',
      created_at: new Date('2026-10-01T10:00:00Z')
    },
    {
      id: 'USR_SECRET_9992',
      first_name: 'زهرا',
      last_name: 'محمدی & کریمی',
      phone_number: '09350001234',
      total_score: 980,
      status: 'vip',
      created_at: new Date('2026-10-02T10:00:00Z')
    },
    {
      id: 'USR_SECRET_9993',
      first_name: 'مهدی',
      last_name: '',
      phone_number: '09010000001',
      total_score: 15,
      status: 'suspended',
      created_at: new Date('2026-10-03T10:00:00Z')
    }
  ];

  // Append 250 additional users to verify all DB rows are exported without pagination limits
  for (let i = 1; i <= 250; i++) {
    sampleUsers.push({
      id: `USR_BULK_${i}`,
      first_name: `کاربر`,
      last_name: `شماره ${i}`,
      phone_number: `0919${String(i).padStart(7, '0')}`,
      total_score: i * 10,
      status: 'active',
      created_at: new Date('2026-10-04T10:00:00Z')
    });
  }

  let simulateDbError = false;
  const mockPool = {
    query: async (sql) => {
      const text = typeof sql === 'string' ? sql : sql.text || '';
      if (simulateDbError && text.includes('FROM public.users')) {
        throw new Error('Simulated PostgreSQL failure');
      }
      if (text.includes('SELECT') && text.includes('FROM public.users u')) {
        assert(!text.includes('total_score'), 'SQL query must not select total_score');
        assert(!text.includes('purchases'), 'SQL query must not join purchases');
        assert(!text.includes('LIMIT'), 'SQL query must export ALL users without pagination LIMIT');
        return {
          rows: sampleUsers.map((u) => ({
            first_name: u.first_name,
            last_name: u.last_name,
            phone_number: u.phone_number
          }))
        };
      }
      return { rows: [] };
    },
    end: async () => {}
  };

  mainServer.setTestPool(mockPool);

  // 3. Test unauthorized requests (must return 401)
  const noKeyRes = await httpRequestBinary(mainServer.app, 'GET', '/api/admin/users/export-excel');
  assert.strictEqual(noKeyRes.status, 401, 'Request without admin key must return 401');

  const wrongKeyRes = await httpRequestBinary(mainServer.app, 'GET', '/api/admin/users/export-excel', {
    'x-admin-key': 'WRONG_KEY'
  });
  assert.strictEqual(wrongKeyRes.status, 401, 'Request with invalid admin key must return 401');
  console.log('✓ Admin authorization enforcement (401) verified');

  // 4. Test authorized Excel export
  const okRes = await httpRequestBinary(mainServer.app, 'GET', '/api/admin/users/export-excel', {
    'x-admin-key': process.env.ADMIN_KEY
  });
  assert.strictEqual(okRes.status, 200, 'Valid admin key must return 200');
  assert(
    okRes.headers['content-type'].includes('application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'),
    'Content-Type must be .xlsx MIME type'
  );
  assert(
    okRes.headers['content-disposition'].includes(`users-${todayJalali}.xlsx`),
    'Content-Disposition must include Jalali dated filename'
  );

  const zipFiles = parseZipEntries(okRes.buffer);
  for (const requiredPart of [
    '[Content_Types].xml',
    '_rels/.rels',
    'xl/_rels/workbook.xml.rels',
    'xl/workbook.xml',
    'xl/styles.xml',
    'xl/worksheets/sheet1.xml'
  ]) {
    assert(zipFiles.has(requiredPart), `Missing required OOXML part: ${requiredPart}`);
  }

  const sheetXml = zipFiles.get('xl/worksheets/sheet1.xml');
  const stylesXml = zipFiles.get('xl/styles.xml');

  const expectedLastRow = sampleUsers.length + 1; // 254 (1 header + 253 users)
  assert(sheetXml.includes('rightToLeft="1"'), 'Worksheet must enable rightToLeft="1"');
  assert(sheetXml.includes(`<dimension ref="A1:B${expectedLastRow}"/>`), `Dimension must be A1:B${expectedLastRow}`);
  assert(!sheetXml.includes('r="C'), 'Must NOT contain column C or any extra columns');
  assert(sheetXml.includes('نام و نام خانوادگی'), 'Header A1 missing');
  assert(sheetXml.includes('شماره تماس'), 'Header B1 missing');
  assert(stylesXml.includes('numFmtId="49"'), 'Phone cells must use numFmtId="49" (@ Text format)');
  assert(sheetXml.includes('t="inlineStr"'), 'Cells must be stored as explicit string type');
  assert(sheetXml.includes('09123456789'), 'Leading zero phone 09123456789 must be preserved');
  assert(sheetXml.includes('09350001234'), 'Leading zero phone 09350001234 must be preserved');
  assert(sheetXml.includes('09010000001'), 'Leading zero phone 09010000001 must be preserved');
  assert(sheetXml.includes('09190000250'), 'Last bulk user phone 09190000250 must be present');
  assert(sheetXml.includes('زهرا محمدی &amp; کریمی'), 'XML entities in Persian names must be properly escaped');

  // Ensure forbidden fields never appear in sheet
  for (const forbidden of ['USR_SECRET_9991', 'USR_BULK_250', '450', '980', 'active', 'vip', 'suspended', 'امتیاز', 'رتبه']) {
    assert(!sheetXml.includes(forbidden), `Forbidden data leaked into Excel: ${forbidden}`);
  }
  console.log(`✓ Real .xlsx binary (${sampleUsers.length} users), RTL sheetView, Text phone format, and 2-column strict schema verified`);

  // Write temporary sample .xlsx to /tmp so external ZIP/XML tools can also inspect it
  const sampleOutPath = '/tmp/test_users_export_sample.xlsx';
  fs.writeFileSync(sampleOutPath, okRes.buffer);

  // 5. Test DB error handling (must return 500 JSON cleanly)
  simulateDbError = true;
  const errRes = await httpRequestBinary(mainServer.app, 'GET', '/api/admin/users/export-excel', {
    'x-admin-key': process.env.ADMIN_KEY
  });
  assert.strictEqual(errRes.status, 500, 'DB failure must return 500 JSON');
  const errJson = JSON.parse(errRes.buffer.toString('utf8'));
  assert.strictEqual(errJson.success, false);
  assert(errJson.error && errJson.error.length > 0);
  console.log('✓ Error handling (500 JSON) verified');

  // 6. Verify admin panel HTML files contain the button & export function
  for (const htmlPath of [
    path.join(__dirname, 'public', 'index.html'),
    path.join(__dirname, '..', 'reward-backend', 'server', 'public', 'index.html'),
    path.join(__dirname, '..', 'app', 'admin-panel', 'index.html')
  ]) {
    const html = fs.readFileSync(htmlPath, 'utf8');
    assert(html.includes('دانلود خروجی اکسل'), `Button title missing in ${htmlPath}`);
    assert(html.includes('exportUsersExcel()'), `exportUsersExcel() missing in ${htmlPath}`);
    assert(html.includes('/api/admin/users/export-excel'), `Export endpoint call missing in ${htmlPath}`);
  }
  console.log('✓ Admin panel UI files verified');

  // Also test buildUsersExcelBuffer with 0 users (empty state)
  const emptyBuf = buildUsersExcelBuffer([]);
  const emptyZip = parseZipEntries(emptyBuf);
  assert(emptyZip.get('xl/worksheets/sheet1.xml').includes('<dimension ref="A1:B1"/>'));
  console.log('✓ Empty user list edge case verified');

  console.log('\n🎉 All Excel export tests passed successfully!');
}

runTests().catch((err) => {
  console.error('❌ Test failed:', err);
  process.exit(1);
});
