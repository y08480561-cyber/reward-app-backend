import { jsonResponse } from './security.js';
import { CORS_HEADERS, REQUIRED_D1_TABLES, withCors } from './helpers.js';
import { handleAuthAndGameRoutes } from './routes_auth_game.js';
import { handleAdminAndCompetitionRoutes } from './routes_admin_comp.js';
import { handleSupportAndBannerRoutes } from './routes_support_banners.js';
import { handleRewardsRoutes } from './routes_rewards.js';

const WORKER_STARTED_AT = Date.now();

async function handleHealthCheck(env) {
  let dbStatus = 'disconnected';
  if (env.DB) {
    try {
      await env.DB.prepare('SELECT 1 AS ok').first();
      dbStatus = 'connected';
    } catch {
      dbStatus = 'disconnected';
    }
  }
  return jsonResponse({
    status: 'ok',
    uptime: Number(((Date.now() - WORKER_STARTED_AT) / 1000).toFixed(2)),
    timestamp: Date.now(),
    server: 'Reqabat Backend (Cloudflare Worker + D1)',
    environment: env.ENVIRONMENT || 'test',
    database: {
      type: 'Cloudflare D1',
      binding: 'DB',
      status: dbStatus,
      configured: Boolean(env.DB)
    }
  });
}

async function handleD1HealthCheck(env) {
  if (!env.DB) {
    return jsonResponse({
      success: false,
      status: 'error',
      binding: 'DB',
      message: 'Cloudflare D1 binding (DB) پیکربندی نشده است.'
    }, 503);
  }

  try {
    const ping = await env.DB.prepare('SELECT 1 AS ok').first();
    const { results } = await env.DB.prepare(
      `SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%' AND name NOT LIKE '_cf_%' ORDER BY name`
    ).all();
    const existingTables = new Set((results || []).map((r) => r.name));
    const missingTables = REQUIRED_D1_TABLES.filter((t) => !existingTables.has(t));

    return jsonResponse({
      success: Number(ping?.ok) === 1 && missingTables.length === 0,
      status: missingTables.length === 0 ? 'connected' : 'schema_incomplete',
      database: 'reward-app-db',
      binding: 'DB',
      ping: Number(ping?.ok) === 1,
      tablesCount: existingTables.size,
      missingTables,
      timestamp: Date.now()
    }, missingTables.length === 0 ? 200 : 500);
  } catch (err) {
    return jsonResponse({
      success: false,
      status: 'error',
      binding: 'DB',
      message: err?.message || 'خطا در برقراری ارتباط با دیتابیس D1'
    }, 500);
  }
}

async function handleUploadsFetch(request, env, pathname) {
  const key = pathname.replace(/^\/uploads\//, '');
  if (!key || key.includes('..')) {
    return jsonResponse({ success: false, message: 'مسیر فایل نامعتبر است.' }, 400);
  }

  if (env.BANNERS_BUCKET) {
    try {
      const obj = await env.BANNERS_BUCKET.get(key);
      if (obj) {
        const headers = new Headers();
        headers.set('Content-Type', obj.httpMetadata?.contentType || 'application/octet-stream');
        headers.set('Cache-Control', 'public, max-age=86400');
        return new Response(obj.body, { status: 200, headers });
      }
    } catch (err) {
      console.warn('[R2 Read Warning]', err?.message || err);
    }
  }

  if (env.ASSETS) {
    return env.ASSETS.fetch(request);
  }

  return jsonResponse({ success: false, message: 'فایل مورد نظر یافت نشد.' }, 404);
}

async function handleAssetFallback(request, env, url, pathname) {
  if (!env.ASSETS) {
    return jsonResponse({
      success: false,
      error: `مسیر مورد نظر یافت نشد: ${pathname}`
    }, 404);
  }

  let targetPath = pathname;
  if (pathname === '/admin' || pathname === '/admin/dashboard') {
    targetPath = '/index.html';
  } else if (pathname === '/admin/users' || pathname === '/admin/users/') {
    targetPath = '/users.html';
  }

  if (targetPath !== pathname) {
    const rewrittenUrl = new URL(url.toString());
    rewrittenUrl.pathname = targetPath;
    return env.ASSETS.fetch(new Request(rewrittenUrl.toString(), request));
  }
  return env.ASSETS.fetch(request);
}

export async function handleWorkerRequest(request, env) {
  const url = new URL(request.url);
  const pathname = url.pathname;
  const method = request.method.toUpperCase();

  if (method === 'OPTIONS') {
    return new Response(null, { status: 204, headers: CORS_HEADERS });
  }

  try {
    // Health endpoints
    if (method === 'GET' && (pathname === '/health' || pathname === '/api/health')) {
      return withCors(await handleHealthCheck(env));
    }
    if (method === 'GET' && pathname === '/api/health/d1') {
      return withCors(await handleD1HealthCheck(env));
    }

    // Uploaded assets
    if (method === 'GET' && pathname.startsWith('/uploads/')) {
      return withCors(await handleUploadsFetch(request, env, pathname));
    }

    // Ensure DB exists for API routes
    if (pathname.startsWith('/api/')) {
      if (!env.DB) {
        return withCors(jsonResponse({
          success: false,
          error: 'اتصال به پایگاه داده Cloudflare D1 (Binding: DB) برقرار نیست.',
          dbStatus: 'DISCONNECTED'
        }, 503));
      }

      const routeHandlers = [
        handleRewardsRoutes,
        handleAuthAndGameRoutes,
        handleAdminAndCompetitionRoutes,
        handleSupportAndBannerRoutes
      ];

      for (const handler of routeHandlers) {
        const res = await handler(request, env, url, pathname);
        if (res) return withCors(res);
      }

      return withCors(jsonResponse({
        success: false,
        error: `مسیر مورد نظر یافت نشد: ${pathname}`
      }, 404));
    }

    return await handleAssetFallback(request, env, url, pathname);
  } catch (err) {
    console.error('[Cloudflare Worker Unhandled Error]', err?.message || err);
    return withCors(jsonResponse({
      success: false,
      error: 'خطای داخلی سرور رخ داد.'
    }, 500));
  }
}

export default {
  async fetch(request, env) {
    return handleWorkerRequest(request, env);
  }
};
