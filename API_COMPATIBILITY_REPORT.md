# API COMPATIBILITY REPORT (Cloudflare Workers)

گزارش سازگاری کامل Endpointهای بک‌اند با محیط Cloudflare Workers:

## ۱. پوشش کامل Endpointها
تمامی ۴۸+ مسیر (Routes) موجود در پروژه اصلی بدون حذف یا تغییر منطقی حفظ شده‌اند:
- **احراز هویت و کاربران**: `/api/auth/login`, `/api/auth/otp/request`, `/api/auth/otp/verify`, `/api/admin/users`, `/api/admin/auth/verify`
- **جدول ۱۰۰ مربع**: `/api/squares`, `/api/squares/instant-unlock`, `/api/ads/complete`
- **امتیازات و لیدربورد**: `/api/leaderboard`, `/api/competition/current`, `/api/competitions/history`
- **جوایز و فروشگاه**: `/api/rewards`, `/api/rewards/categories`, `/api/rewards/claim`, `/api/store/products`, `/api/store/user-entitlements`, `/api/store/verify-purchase`
- **پشتیبانی**: `/api/support/tickets`, `/api/support/tickets/:id/messages`
- **تبلیغات بنری**: `/api/ad-banners/active`, `/api/admin/ad-banners`, `/api/ad-banners/:id/impression`, `/api/ad-banners/:id/click`
- **پنل مدیریت**: `/api/admin/overview`, `/api/admin/competition/start-new`, `/api/admin/announcements`, `/api/admin/push-notification`

## ۲. سازگاری با Cloudflare Workers
- استفاده از `nodejs_compat` برای پشتیبانی کامل از ماژول‌های حیاتی Node.js.
- پل ارتباطی `httpServerHandler` برای اجرای برنامه‌های Express بدون نیاز به بازنویسی ساختار مسیریابی.
- ذخیره‌سازی تصاویر از طریق Supabase Storage / R2 بدون تکیه بر فایل‌سیستم محلی Worker.
