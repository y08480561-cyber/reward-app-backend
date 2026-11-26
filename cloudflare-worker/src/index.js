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
  if (env.ASSETS) {
    try {
      let targetPath = pathname;
      if (pathname === '/' || pathname === '/admin' || pathname === '/admin/dashboard') {
        targetPath = '/index.html';
      } else if (pathname === '/admin/users' || pathname === '/admin/users/') {
        targetPath = '/users.html';
      }
      const rewrittenUrl = new URL(url.toString());
      rewrittenUrl.pathname = targetPath;
      const resp = await env.ASSETS.fetch(new Request(rewrittenUrl.toString(), request));
      if (resp && (resp.ok || resp.status === 200)) {
        return resp;
      }
    } catch (err) {
      console.warn('[ASSETS fetch warning]', err);
    }
  }

  const html = `<!DOCTYPE html>
<html lang="fa" dir="rtl">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>پنل مدیریت ابری | رقابت کن، جایزه ببر</title>
  <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/css/bootstrap.rtl.min.css" rel="stylesheet">
  <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/bootstrap-icons@1.11.3/font/bootstrap-icons.min.css">
  <style>
    :root { --bg-main: #0B192C; --bg-card: #152A44; --border-color: #2E5077; --gold: #FFB703; }
    body { background-color: var(--bg-main); color: #F8FAFC; font-family: system-ui, Tahoma, sans-serif; padding: 30px; min-height: 100vh; display: flex; align-items: center; justify-content: center; }
    .admin-card { background: var(--bg-card); border: 1px solid var(--border-color); border-radius: 20px; padding: 40px; max-width: 650px; width: 100%; box-shadow: 0 15px 40px rgba(0,0,0,0.6); text-align: center; }
    .brand-icon { width: 70px; height: 70px; background: linear-gradient(135deg, #FFB703 0%, #FB8500 100%); color: #0B192C; font-size: 32px; font-weight: 900; border-radius: 18px; display: flex; align-items: center; justify-content: center; margin: 0 auto 20px auto; box-shadow: 0 5px 20px rgba(255,183,3,0.3); }
    h1 { color: #fff; font-size: 22px; font-weight: 800; margin-bottom: 12px; }
    p { color: #94A3B8; font-size: 14px; line-height: 1.8; margin-bottom: 25px; }
    .btn-gold { background: linear-gradient(135deg, #FFB703 0%, #FB8500 100%); color: #0B192C; font-weight: 800; padding: 12px 28px; border-radius: 12px; text-decoration: none; display: inline-block; box-shadow: 0 4px 15px rgba(255,183,3,0.3); transition: transform 0.2s; }
    .btn-gold:hover { transform: translateY(-2px); color: #0B192C; }
    .badge-cf { background: rgba(16, 185, 129, 0.15); color: #10B981; border: 1px solid rgba(16, 185, 129, 0.3); padding: 6px 14px; border-radius: 20px; font-size: 12px; font-weight: 700; display: inline-block; margin-bottom: 20px; }
  </style>
</head>
<body>
  <div class="admin-card">
    <div class="brand-icon"><i class="bi bi-trophy-fill"></i></div>
    <div class="badge-cf"><i class="bi bi-cloud-check-fill me-1"></i> متصل به کلادفلر Workers + D1</div>
    <h1>پنل مدیریت ابری «رقابت کن، جایزه ببر»</h1>
    <p>سرور اختصاصی Cloudflare Worker فعال است. تمام درخواست‌های اپلیکیشن اندروید و پنل مدیریت مستقیماً روی بستر ابری کلادفلر و پایگاه داده D1 اجرا می‌شوند.</p>
    <div>
      <a href="/api/health/d1" class="btn-gold"><i class="bi bi-database-check me-2"></i> تست وضعیت اتصال D1</a>
    </div>
  </div>
</body>
</html>`;

  return new Response(html, {
    status: 200,
    headers: { 'Content-Type': 'text/html; charset=utf-8' }
  });
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
