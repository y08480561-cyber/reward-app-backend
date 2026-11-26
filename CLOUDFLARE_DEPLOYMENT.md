# CLOUDFLARE DEPLOYMENT GUIDE

راهنمای گام‌به‌گام استقرار بک‌اند روی Cloudflare Workers:

## پیش‌نیازها
1. نصب Node.js نسخه ۲۲+
2. نصب ابزار Wrangler: `npm install -g wrangler`

## گام ۱: احراز هویت در کلاودفلر
```bash
npx wrangler login
```

## گام ۲: تنظیم متغیرهای محیطی و Secretها
برای تنظیم متغیرهای محرمانه (مانند `ADMIN_KEY` و `DATABASE_URL`) در کلاودفلر:
```bash
npx wrangler secret put ADMIN_KEY
npx wrangler secret put DATABASE_URL
```

## گام ۳: تست محلی (Local Development)
یک فایل `.dev.vars` بر اساس `.dev.vars.example` بسازید و دستور زیر را اجرا کنید:
```bash
npx wrangler dev --compatibility-date 2024-09-23 --compatibility-flag nodejs_compat
```

## گام ۴: انتشار روی Cloudflare Workers
```bash
npx wrangler deploy --compatibility-date 2024-09-23 --compatibility-flag nodejs_compat
```

## گام ۵: اتصال دامنه اختصاصی (`https://api.kalametgame.ir`)
پس از تست موفق Worker، از طریق داشبورد کلاودفلر بخش **Workers & Pages -> Custom Domains** دامنه `api.kalametgame.ir` را به Worker متصل کنید.
