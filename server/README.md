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
۳. در بخش **URI**، رشته اتصال (Connection String) از نوع Transaction یا Session Pooler را کپی کنید (رمز پایگاه داده خود را در آن جایگزین کنید).
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
```

---

## ۳. اجرای محلی سرور
```bash
cd server
npm install
npm start
```
سرور بر روی پورت ۳۰۰۰ اجرا می‌شود و پنل مدیریت تحت وب در آدرس `http://localhost:3000` و جدول رتبه‌بندی کاربران در `http://localhost:3000/admin/users` در دسترس خواهد بود.

---

## ۴. چرخه ۲۴ ساعته و قوانین ضد تقلب
- دوره ۲۴ ساعته به صورت مستقل برای هر کاربر با `startTime` و `endTime` سروری در پایگاه داده مدیریت می‌شود.
- پس از اتمام ۲۴ ساعت، ۵۰ مربع به صورت خودکار بازنشانی می‌شوند ولی مجموع امتیازات پیشین کاربر دست‌نخورده در `public.user_scores` باقی می‌ماند.
- هر مربع در هر دوره تنها ۱ بار مجاز به دریافت ۱ امتیاز است و درخواست‌های تکراری با کد خطای ۴۰۹ رد می‌شوند.
- هر تاییدیه تبلیغ با توکن یکتا (Nonce) در `public.ad_sessions` بررسی می‌شود تا از Replay Attack و ثبت چندباره امتیاز با یک تبلیغ جلوگیری شود.
- عملیات افزایش امتیاز و ثبت لاگ درون یک تراکنش اتمیک (`BEGIN ... COMMIT`) پایگاه داده اجرا می‌شود.
