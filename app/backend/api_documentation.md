# مستندات فنی و راهنمای APIهای بک‌اند (Supabase PostgreSQL)
## برنامه رقابت و دریافت جایزه (۵۰ مربع)

---

### ۱. معماری و امنیت (Zero Trust Architecture)
تمامی اعتبارسنجی‌ها و ثبت امتیازها درون توابع **PostgreSQL Stored Procedures (SECURITY DEFINER)** انجام می‌شود:
1. **جلوگیری از تغییر مستقیم امتیاز**: کلاینت به جدول `users` دسترسی `UPDATE` مستقیم ندارد. افزایش امتیاز صرفاً از طریق تابع اتمیک `rpc_award_ad_point` ممکن است.
2. **جلوگیری از تقلب و Replay Attack**: هر تبلیغ تماشاشده یک توکن رمزنگاری‌شده منحصر‌به‌فرد (`nonce`) دارد که در جدول `score_transactions` ثبت می‌شود. ارسال مجدد یک توکن با خطای یکتایی مواجه شده و رد می‌شود.
3. **محدودیت چرخه ۲۴ ساعته**: هر مربع در هر روز تقویمی فقط ۱ بار می‌تواند امتیاز دریافت کند (ثبت در `user_daily_squares`).
4. **حفظ حریم خصوصی**: در پاسخ‌های عمومی (مانند لیدربورد)، نام خانوادگی ماسک شده (`سهراب ک***`) و شماره تلفن‌ها هرگز به عموم ارسال نمی‌شوند.

---

### ۲. مستندات Endpointهای RPC

#### ۲.۱. ثبت‌نام یا ورود کاربر (`rpc_register_or_login`)
- **مسیر (POST):**
  `https://<YOUR_PROJECT_ID>.supabase.co/rest/v1/rpc/rpc_register_or_login`
- **هدرها:**
  - `apikey`: `<SUPABASE_ANON_KEY>`
  - `Authorization`: `Bearer <SUPABASE_ANON_KEY>`
  - `Content-Type`: `application/json`
- **بدنه درخواست (JSON):**
  ```json
  {
    "p_first_name": "مهدی",
    "p_last_name": "محمدی",
    "p_phone": "09121234567"
  }
  ```
- **پاسخ موفق (HTTP 200):**
  ```json
  {
    "success": true,
    "user": {
      "id": "USR_8F2A10CB",
      "first_name": "مهدی",
      "last_name": "محمدی",
      "phone_number": "09121234567",
      "total_score": 0,
      "status": "active",
      "created_at": "2026-10-17T04:40:00Z"
    }
  }
  ```
- **پاسخ خطا:**
  ```json
  {
    "success": false,
    "message": "شماره موبایل نامعتبر است. باید ۱۱ رقمی و با 09 شروع شود."
  }
  ```

---

#### ۲.۲. اعطای امتیاز مشاهده تبلیغ (`rpc_award_ad_point`)
- **مسیر (POST):**
  `https://<YOUR_PROJECT_ID>.supabase.co/rest/v1/rpc/rpc_award_ad_point`
- **بدنه درخواست (JSON):**
  ```json
  {
    "p_user_id": "USR_8F2A10CB",
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
    "message": "امتیاز با موفقیت در سرور ثبت شد."
  }
  ```
- **پاسخ در صورت تقلب یا ارسال تکراری:**
  ```json
  {
    "success": false,
    "message": "این درخواست قبلاً ثبت شده است (تکراری)."
  }
  ```

---

#### ۲.۳. دریافت آمار داشبورد پنل مدیریت (`rpc_get_admin_dashboard_stats`)
- **مسیر (POST):**
  `https://<YOUR_PROJECT_ID>.supabase.co/rest/v1/rpc/rpc_get_admin_dashboard_stats`
- **پاسخ موفق (HTTP 200):**
  ```json
  {
    "total_users": 154,
    "total_score": 3820,
    "today_ads_viewed": 420,
    "active_competitions": 1
  }
  ```

---

#### ۲.۴. دریافت لیدربورد عمومی (`rpc_get_leaderboard`)
- **مسیر (POST):**
  `https://<YOUR_PROJECT_ID>.supabase.co/rest/v1/rpc/rpc_get_leaderboard`
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
      "user_id": "USR_8F2A10CB",
      "display_name": "مهدی م***",
      "total_score": 45
    }
  ]
  ```
