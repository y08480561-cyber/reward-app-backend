# مستندات فنی و راهنمای APIهای بک‌اند (Supabase PostgreSQL)
## برنامه رقابت و دریافت جایزه (۵۰ مربع) - نسخه امن

---

### ۱. معماری و امنیت (Zero Trust Architecture)
تمامی اعتبارسنجی‌ها، ثبت امتیازها و بررسی دسترسی‌ها درون توابع و سیاست‌های امنیتی **PostgreSQL RLS & SECURITY DEFINER** انجام می‌شود:
1. **جلوگیری از تغییر یا جعل مستقیم امتیاز**: کلاینت به جدول `users` و `score_transactions` دسترسی مستقیم `UPDATE` یا `INSERT` ندارد.
2. **احراز هویت اجباری**: در تابع `rpc_award_ad_point`، شناسه کاربر دیگر از پارامتر کلاینت دریافت نمی‌شود، بلکه مستقیماً از توکن معتبر `auth.uid()` استخراج می‌گردد. بنابراین هیچ کاربری نمی‌تواند برای شناسه دیگری امتیاز ثبت کند.
3. **جلوگیری از Replay Attack با Nonce یکتا**: هر تبلیغ تماشاشده یک توکن رمزنگاری‌شده منحصر‌به‌فرد (`nonce`) دارد که در جدول `score_transactions` با قید `UNIQUE` ثبت می‌شود.
4. **محدودیت چرخه ۲۴ ساعته**: هر مربع در هر روز تقویمی فقط ۱ بار می‌تواند امتیاز دریافت کند (ثبت با قید یکتایی در `user_daily_squares`).
5. **محدودیت نرخ درخواست (Rate Limiting)**: حداقل ۱۰ ثانیه فاصله زمانی بین ثبت دو امتیاز متوالی در سرور الزامی است تا از ارسال درخواست‌های رباتیک و پشت‌سرهم جلوگیری شود.
6. **حفظ کامل حریم خصوصی (RLS)**: جدول `users` روی دسترسی مستقیم کاربران ناشناس و سایر کاربران بسته است. هر کاربر تنها رکورد خود را می‌تواند بخواند (`auth.uid() = id`). در جدول رتبه‌بندی عمومی (`rpc_get_leaderboard`)، نام خانوادگی ماسک شده و شماره تلفن‌ها اصلاً واکشی نمی‌شوند.
7. **پنل مدیریت مجهز به احراز هویت واقعی**: ورود به پنل مدیریت نیازمند اعتبارسنجی ایمیل و رمز عبور از طریق `Supabase Auth` بوده و تابع `is_admin()` احراز هویت در جدول `admin_users` را بررسی می‌کند.

---

### ۲. مستندات Endpointهای RPC

#### ۲.۱. اعطای امتیاز مشاهده تبلیغ (`rpc_award_ad_point`)
- **مسیر (POST):**
  `https://<YOUR_PROJECT_ID>.supabase.co/rest/v1/rpc/rpc_award_ad_point`
- **هدرها:**
  - `apikey`: `<SUPABASE_ANON_KEY>`
  - `Authorization`: `Bearer <USER_AUTH_TOKEN>` (توکن JWT کاربر احراز هویت شده)
  - `Content-Type`: `application/json`
- **بدنه درخواست (JSON):**
  ```json
  {
    "p_square_number": 12,
    "p_nonce": "TAPSELL_VERIFIED_98F0123ABDE"
  }
  ```
- **پاسخ موفق (HTTP 200):**
  ```json
  {
    "success": true,
    "new_total_score": 1,
    "square_number": 12,
    "message": "۱ امتیاز مسابقه با موفقیت ثبت شد."
  }
  ```
- **پاسخ در صورت تقلب یا ارسال تکراری:**
  ```json
  {
    "success": false,
    "message": "این درخواست قبلاً ثبت شده است (توکن تکراری)."
  }
  ```
- **پاسخ در صورت فاصله زمانی غیرمجاز (اسپم):**
  ```json
  {
    "success": false,
    "message": "فاصله زمانی بین تماشای تبلیغات رعایت نشده است. لطفاً کمی صبر کنید."
  }
  ```

---

#### ۲.۲. دریافت آمار داشبورد پنل مدیریت (`rpc_get_admin_dashboard_stats`)
- **مسیر (POST):**
  `https://<YOUR_PROJECT_ID>.supabase.co/rest/v1/rpc/rpc_get_admin_dashboard_stats`
- **هدرها:**
  - `apikey`: `<SUPABASE_ANON_KEY>`
  - `Authorization`: `Bearer <ADMIN_AUTH_TOKEN>` (فقط با توکن مدیر مجاز)
- **پاسخ موفق (HTTP 200):**
  ```json
  {
    "total_users": 154,
    "total_score": 3820,
    "today_ads_viewed": 420,
    "active_competitions": 1
  }
  ```
- **پاسخ در صورت عدم دسترسی ادمین:**
  ```
  HTTP 403 Forbidden - دسترسی غیرمجاز: این متد فقط برای مدیران سیستم قابل فراخوانی است.
  ```

---

#### ۲.۳. دریافت لیدربورد عمومی (`rpc_get_leaderboard`)
- **مسیر (POST):**
  `https://<YOUR_PROJECT_ID>.supabase.co/rest/v1/rpc/rpc_get_leaderboard`
- **هدرها:**
  - `apikey`: `<SUPABASE_ANON_KEY>`
  - `Authorization`: `Bearer <SUPABASE_ANON_KEY>`
- **بدنه درخواست (JSON):**
  ```json
  {
    "p_limit": 50
  }
  ```
- **پاسخ موفق:**
  ```json
  [
    {
      "rank": 1,
      "display_name": "مهدی م***",
      "total_score": 45
    }
  ]
  ```
