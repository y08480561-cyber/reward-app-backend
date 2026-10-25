# راهنمای استقرار بکاند و اتصال به Supabase PostgreSQL (پروژه: رقابت کن، جایزه ببر)

این مستندات شامل راهنمای راه‌اندازی سرور بکاند، اتصال به پایگاه داده دائمی PostgreSQL در Supabase، و تنظیمات رسمی تپسل پلاس است.

---

## ۱. اتصال به پایگاه داده Supabase PostgreSQL

بک‌اند پروژه با استفاده از درایور استاندارد `pg` و Connection Pooling به دیتابیس ابری Supabase متصل می‌شود:
- **اطلاعات کاربران:** در جدول `public.users` ذخیره می‌شوند.
- **امتیازات کاربران:** در جدول `public.user_scores` ذخیره و مدیریت می‌شوند.
- **وضعیت ۵۰ مربع روزانه:** در جدول `public.square_states` ثبت می‌گردند.
- **چرخه‌های ۲۴ ساعته:** در جدول `public.daily_cycles` نگهداری می‌شوند.
- **رویدادهای تماشای تبلیغ و توکن‌های ضدتقلب (Nonce):** در جدول `public.ad_sessions` ذخیره می‌شوند.
- **رتبه‌بندی عمومی و پنل مدیریت:** به صورت آنی از پایگاه داده واکشی می‌شوند.

### نحوه تنظیم `DATABASE_URL` در Render
۱. وارد داشبورد [Supabase](https://supabase.com) شوید و پروژه خود را انتخاب کنید.
۲. به مسیر **Project Settings** > **Database** > **Connection string** بروید.
۳. در بخش **URI**، حتماً گزینه **Connection Pooler** (با پورت 6543 و هاست `pooler.supabase.com`) را انتخاب کنید، زیرا Render روی شبکه IPv4 اجرا می‌شود و اتصال مستقیم با پورت 5432 با خطای ETIMEDOUT یا ENOTFOUND روبرو خواهد شد.
   نمونه فرمت:
   `postgresql://postgres.[PROJECT-REF]:[YOUR-PASSWORD]@aws-0-[REGION].pooler.supabase.com:6543/postgres`
۴. وارد پنل سرویس وب خود در [Render](https://dashboard.render.com) شوید.
۵. به برگه **Environment** رفته و متغیر زیر را اضافه کنید:
   - **Key:** `DATABASE_URL`
   - **Value:** رشته کپی‌شده از Supabase

---

## ۲. متغیرهای محیطی کامل سرور (.env در Render)
```env
PORT=3000
ADMIN_KEY=ReqabatAdmin2026
AUTH_PIN=2026
DATABASE_URL=postgresql://postgres.xxx:password@aws-0-eu-central-1.pooler.supabase.com:6543/postgres
TAPSELL_APP_KEY=sgedhqdbofphgpajkrqtahjkedbkokdlqiesotcmfqcklbamjaaiddqsqnihdtmobfidcb
TAPSELL_ZONE_ID=6ad231b89140ba2215e772db
KAVENEGAR_API_KEY=your_kavenegar_api_key_here
```

---

## ۳. اجرای اسکریپت Migration در Supabase (اختیاری و پیشنهادی)
فایل `server/migration.sql` شامل دستورات امن `ADD COLUMN IF NOT EXISTS` و `CREATE TABLE IF NOT EXISTS` است که هیچ داده‌ای را حذف نمی‌کند.
برای اطمینان از آماده بودن کامل جداول، می‌توانید محتوای فایل `server/migration.sql` را در بخش **SQL Editor** داشبورد Supabase اجرا فرمایید.

---

## ۴. روش به‌روزرسانی کدها در GitHub و Render
۱. فایل‌های پوشه `server/` به خصوص `server/server.js` و `server/migration.sql` را در مخزن گیت‌هاب پروژه جایگزین (Commit & Push) نمایید.
۲. سرویس Render به صورت خودکار تغییرات را از گیت‌هاب دریافت کرده و Build جدید را دیپلوی می‌کند.
۳. پس از دیپلوی، در بخش Logs پیام موفقیت‌آمیز زیر نمایش داده می‌شود:
   ```text
   [PostgreSQL SUCCESS] Successfully connected to database!
   ```
