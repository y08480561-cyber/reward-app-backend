if (typeof globalThis.__dirname === 'undefined') {
  globalThis.__dirname = '/app/server';
}
if (typeof globalThis.__filename === 'undefined') {
  globalThis.__filename = '/app/server/server.js';
}
if (typeof globalThis.process !== 'undefined' && globalThis.process.env) {
  globalThis.process.env.WORKER_RUNTIME = 'cloudflare-workers';
}

import { httpServerHandler } from 'cloudflare:node';
import http from 'node:http';
const { D1PoolAdapter } = require('./server/cloudflare/d1-adapter.js');

let app = null;
let server = null;
let handler = null;
let bootError = null;
let cachedDbBinding = null;
let cachedD1Pool = null;

function ensureInitialized() {
  if (handler) return handler;
  if (bootError) throw bootError;

  try {
    app = require('./server/server.js');
    server = http.createServer(app);
    handler = httpServerHandler(server);
    return handler;
  } catch (err) {
    bootError = err;
    console.error(
      '[Worker Startup Error] Failed to initialize Express HTTP handler:',
      err && err.stack ? err.stack : err
    );
    throw err;
  }
}

function syncWorkerEnv(env) {
  if (!env || typeof process === 'undefined' || !process.env) return;
  const envKeys = [
    'ADMIN_KEY',
    'DATABASE_URL',
    'ONESIGNAL_APP_ID',
    'ONESIGNAL_REST_API_KEY',
    'SUPABASE_URL',
    'SUPABASE_ANON_KEY',
    'SUPABASE_SERVICE_ROLE_KEY',
    'SUPABASE_AD_BANNERS_BUCKET',
    'KAVENEGAR_API_KEY',
    'SMS_API_KEY',
    'SMS_SENDER',
    'AUTH_PIN',
    'MYKET_PUBLIC_KEY',
    'TAPSELL_APP_KEY',
    'TAPSELL_ZONE_ID',
    'NODE_ENV'
  ];

  for (const key of envKeys) {
    if (env[key] !== undefined && env[key] !== null && env[key] !== '') {
      process.env[key] = String(env[key]);
    }
  }
}

export default {
  async fetch(request, env, ctx) {
    const requestId = request.headers.get('cf-ray') || (typeof crypto !== 'undefined' && crypto.randomUUID ? crypto.randomUUID() : String(Date.now()));

    try {
      syncWorkerEnv(env);
      const activeHandler = ensureInitialized();

      if (env && env.DB) {
        if (cachedDbBinding !== env.DB || !cachedD1Pool) {
          cachedDbBinding = env.DB;
          cachedD1Pool = new D1PoolAdapter(env.DB);
        }
        if (typeof app.setTestPool === 'function') {
          app.setTestPool(cachedD1Pool);
        }
      }

      return await activeHandler.fetch(request, env, ctx);
    } catch (err) {
      const stack = err && err.stack ? err.stack : String(err);
      const url = request && request.url ? request.url : 'unknown';
      const method = request && request.method ? request.method : 'UNKNOWN';

      console.error(`[Worker Unhandled Exception] requestId=${requestId} ${method} ${url}\n${stack}`);

      return new Response(
        JSON.stringify({
          success: false,
          error: 'Worker runtime exception',
          message: err && err.message ? err.message : 'Unexpected server error',
          requestId,
          timestamp: Date.now()
        }),
        {
          status: 500,
          headers: {
            'Content-Type': 'application/json; charset=utf-8',
            'Cache-Control': 'no-store'
          }
        }
      );
    }
  }
};
