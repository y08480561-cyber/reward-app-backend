# CLOUDFLARE D1 DEPLOYMENT GUIDE

راهنمای استقرار روی Cloudflare Workers با Cloudflare D1:

## گام ۱: ساخت دیتابیس D1 در Cloudflare
```bash
npx wrangler d1 create reward-app-db
```
(شناسه پایگاه داده دریافتی را در `wrangler.jsonc` در بخش `database_id` قرار دهید).

## گام ۲: اجرای اسکریپت ساخت جدول‌ها روی D1
```bash
npx wrangler d1 execute reward-app-db --remote --file=./server/migration.sql
```

## گام ۳: تنظیم Secretها
```bash
npx wrangler secret put ADMIN_KEY
```

## گام ۴: استقرار نهایی (Deploy)
```bash
npx wrangler deploy --compatibility-date 2024-09-23 --compatibility-flag nodejs_compat
```
