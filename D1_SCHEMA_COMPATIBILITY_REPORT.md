# گزارش طراحی اسکیمای Cloudflare D1 و انطباق با Backend و Android

- **پروژه:** رقابت کن، جایزه ببر (`reward-app-backend`)
- **شاخه کاری:** `cloudflare-migration`
- **فایل اسکیما:** [`d1_schema.sql`](./d1_schema.sql)
- **دیتابیس مقصد:** Cloudflare D1 (`reward-app-db` / binding `DB`)
- **وضعیت اجرا:** این مرحله صرفاً آماده‌سازی DDL و ممیزی سازگاری است؛ هیچ اسکریپتی روی دیتابیس D1 اجرا نشده، هیچ داده‌ای درج/حذف/ویرایش نشده، و هیچ تغییری در کد Backend یا Android اعمال نگردیده است.

---

## ۱. ترتیب ساخت ۲۲ جدول و گراف وابستگی‌های کلید خارجی (FK)

برای جلوگیری از خطای ارجاع کلید خارجی هنگام اجرا با `PRAGMA foreign_keys = ON;`، جداول در `d1_schema.sql` دقیقاً به ترتیب والد به فرزند مرتب شده‌اند:

| ترتیب | نام جدول | کلید اصلی (PK) | کلید خارجی (FK) و رفتار `ON DELETE` | نقش در سامانه |
|---:|---|---|---|---|
| ۱ | `users` | `id` | — (جدول والد اصلی) | اطلاعات کاربران، شماره تلفن (`UNIQUE`)، رمز هش‌شده، توکن نشست، امتیاز و سطح اشتراک |
| ۲ | `user_scores` | `user_id` | — (نگه‌داشته‌شده عین PostgreSQL) | منبع اصلی امتیاز جاری هر کاربر (`total_score >= 0`) |
| ۳ | `daily_cycles` | `cycle_id` | — | چرخه‌های ۲۴ ساعته مسابقه به ازای هر کاربر (`start_time`, `end_time` بر حسب epoch ms) |
| ۴ | `square_states` | `state_key` | — | وضعیت تکمیل ۱۰۰ خانه روزانه (`square_number` بین ۱ تا ۱۰۰) |
| ۵ | `competitions` | `id` | — (جدول والد مسابقات) | دوره‌های مسابقه ادواری، عنوان، وضعیت و آمار تجمعی |
| ۶ | `ad_sessions` | `session_id` | — | ثبت نشست‌های تماشای تبلیغ و توکن یکتای ضد تقلب (`token UNIQUE`) |
| ۷ | `user_memberships` | `(user_id, cycle_id)` | — | عضویت/ضریب امتیاز کاربر در یک چرخه مشخص (`free` / `silver` / `gold`) |
| ۸ | `store_products` | `id` | — | بسته‌های فروشگاه مایکت (`SQUARE_UNLOCK` و `ACCOUNT_UPGRADE`) |
| ۹ | `purchases` | `purchase_id` | — | سوابق خرید مایکت + ایندکس یکتای جزئی روی `purchase_token` |
| ۱۰ | `user_entitlements` | `user_id` | — | اعتبارات بازگشایی فوری، وضعیت VIP، مجموع خرید تومان و ضریب امتیاز |
| ۱۱ | `competition_archives` | `id` | `competition_id -> competitions(id) ON DELETE CASCADE` | آرشیو دائمی رتبه‌بندی و امتیاز نهایی کاربران در دوره‌های پایان‌یافته |
| ۱۲ | `competition_logs` | `log_id` | — | لاگ‌های نظارتی ریست و تغییر دوره‌های مسابقه |
| ۱۳ | `home_announcements` | `id` | `competition_id -> competitions(id) ON DELETE SET NULL` | اطلاعیه‌های صفحه اصلی اپلیکیشن با بازه زمانی اختیاری |
| ۱۴ | `support_tickets` | `id` | `user_id -> users(id) ON DELETE CASCADE` | تیکت‌های پشتیبانی کاربران (`tracking_code UNIQUE`) |
| ۱۵ | `support_messages` | `id` | `ticket_id -> support_tickets(id) ON DELETE CASCADE` | پیام‌های مکالمه کاربر و ادمین داخل هر تیکت (`USER` / `ADMIN`) |
| ۱۶ | `reward_categories` | `id` | — (جدول والد دسته‌بندی جوایز) | دسته‌بندی جوایز ویترین (`slug UNIQUE`) |
| ۱۷ | `rewards` | `id` | `category_id -> reward_categories(id) ON DELETE SET NULL` | اقلام جایزه، موجودی، امتیاز مورد نیاز، حذف نرم (`deleted_at`) و تصاویر JSON |
| ۱۸ | `reward_appearance` | `id` (`'default'`) | — | تنظیمات ظاهری، متن‌ها و بخش‌های فعال ویترین جوایز |
| ۱۹ | `reward_claims` | `id` | `user_id -> users(id) ON DELETE CASCADE`, `reward_id -> rewards(id) ON DELETE RESTRICT` | درخواست‌های ثبت‌شده کاربران برای دریافت جایزه + `idx_reward_claims_idempotency` |
| ۲۰ | `login_notification_settings` | `id` (`'default'`) | — | متن‌ها و وضعیت نمایش اطلاعیه اینترنت ایرانسل در صفحه ورود |
| ۲۱ | `ad_banners` | `id` | — | بنرهای تبلیغاتی تصویری و لینک‌دار + آمار کلیک/نمایش و جایگاه نمایش JSON |
| ۲۲ | `user_feedback` | `id` (UUIDv4 پیش‌فرض) | `user_id -> users(id) ON DELETE CASCADE` | بازخورد و امتیاز ۱ تا ۵ کاربران جهت ثبت نظر و دریافت هدیه |

### نماهای سازگاری (Views)
علاوه بر ۲۲ جدول بالا، ۷ نمای فقط-خواندنی (`CREATE VIEW IF NOT EXISTS`) مطابق PostgreSQL در انتهای `d1_schema.sql` تعریف شده‌اند:
- `leaderboard` (رتبه‌بندی زنده با `ROW_NUMBER() OVER (...)` که در SQLite 3.25+ و D1 پشتیبانی می‌شود)
- `store_purchases` (`purchases`)
- `competition_archive` (`competition_archives`)
- `announcements` (`home_announcements`)
- `instant_unlock_credits` (`user_entitlements`)
- `user_store_tiers` (`user_entitlements`)
- `user_feedback_rewards` (`user_feedback`)

---

## ۲. جدول تفاوت‌های کلیدی PostgreSQL و Cloudflare D1 (SQLite)

| مورد | PostgreSQL قدیمی | Cloudflare D1 (SQLite) در `d1_schema.sql` | دلیل و توضیح فنی |
|---|---|---|---|
| **پیشوند اسکیما** | `public.table_name` | `table_name` بدون `public.` | SQLite اسکیمای `public` ندارد (آداپتور `d1-adapter.js` نیز در زمان اجرا `public.` را حذف می‌کند). |
| **مقادیر Boolean** | `BOOLEAN DEFAULT true/false` | `INTEGER NOT NULL DEFAULT 1/0 CHECK (col IN (0, 1))` | SQLite نوع بومی `BOOLEAN` ندارد و آن را به صورت `0` و `1` ذخیره می‌کند؛ `CHECK` از مقادیر نامعتبر جلوگیری می‌کند. |
| **انواع عددی بزرگ** | `BIGINT` (`start_time`, `end_time`, `completed_at`, `timestamp`, `price_toman`, `total_purchased_toman`) | `INTEGER` | `INTEGER` در SQLite تا ۶۴ بیت علامت‌دار (تا `9,223,372,036,854,775,807`) را پشتیبانی می‌کند و با epoch میلی‌ثانیه کاملاً سازگار است. |
| **تاریخ و زمان** | `TIMESTAMPTZ NOT NULL DEFAULT NOW()` | `TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now'))` | فرمت یکنواخت ISO-8601 UTC با میلی‌ثانیه و `Z` دقیقاً هم‌قالب `new Date().toISOString()` در جاوااسکریپت است تا مقایسه‌های متنی مانند `start_at <= ...` و `end_at > ...` در همان روز دچار خطای `'T' > ' '` نشوند. |
| **ستون‌های JSON** | `JSONB` (`additional_images`, `active_sections`, `placements`, `placement_stats`) | `TEXT` همراه با `CHECK (col IS NULL OR json_valid(col))` | D1 JSON را در `TEXT` ذخیره می‌کند و با `json_valid()` سلامت ساختار JSON در سطح دیتابیس تضمین می‌شود. |
| **تولید خودکار UUID** | `id UUID PRIMARY KEY DEFAULT gen_random_uuid()` در `user_feedback` | `id TEXT PRIMARY KEY DEFAULT (<SQLite UUIDv4 expression>)` | مسیر `POST /api/user/feedback` ستون `id` را هنگام `INSERT` ارسال نمی‌کند؛ این `DEFAULT` بر پایه `randomblob` بدون نیاز به تغییر کد، UUIDv4 معتبر می‌سازد. |
| **تغییر ساختار مشروط** | بلوک‌های `DO $$ BEGIN ... END $$;` و `ALTER TABLE ... ADD COLUMN IF NOT EXISTS` | حذف‌شده در اسکیمای نهایی (`CREATE TABLE IF NOT EXISTS` کامل) | SQLite بلوک‌های PL/pgSQL و `ADD COLUMN IF NOT EXISTS` را پشتیبانی نمی‌کند. |
| **ایندکس‌های جزئی** | `CREATE UNIQUE INDEX ... WHERE ... IS NOT NULL` | حفظ‌شده بدون تغییر در `idx_purchases_token` و `idx_reward_claims_idempotency` | SQLite از Partial Unique Index کاملاً پشتیبانی می‌کند. |
| **ساخت View** | `CREATE OR REPLACE VIEW` | `CREATE VIEW IF NOT EXISTS` | SQLite عبارت `OR REPLACE` را برای View ندارد. |
| **تراکنش‌ها** | `BEGIN` / `COMMIT` / `ROLLBACK` و `FOR UPDATE` | تعریف `CHECK` در سطح ستون (`total_score >= 0`, `stock_quantity >= 0`) | D1 اجازه اجرای دستور خام `BEGIN` در `prepare().run()` را نمی‌دهد و نیازمند `db.batch()` است. |

---

## ۳. فهرست داده‌های پایه (Seed Data) مورد نیاز برای راه‌اندازی اولیه

> **توجه:** در `d1_schema.sql` هیچ دستور `INSERT` قرار داده نشده است. رکوردهای زیر باید پس از ساخت جداول و در مرحله مجزای استقرار/راه‌اندازی درج شوند:

1. **جدول `competitions` (حداقل ۱ مسابقه فعال اولیه):**
   - `id`: `'COMP_1'` (یا `'COMP_DEFAULT_1'` در اسکیمای اولیه)، `competition_number`: `1`، `title`: `'مسابقه شماره ۱ (جایزه آیفون و پلی‌استیشن)'`، `status`: `'ACTIVE'`، `created_by`: `'admin'`.
2. **جدول `home_announcements` (اطلاعیه پیش‌فرض خوش‌آمدگویی):**
   - `id`: `'ANN_DEFAULT_1'`، `title`: `'آغاز مسابقه بزرگ دوره‌ای!'`، `type`: `'COMPETITION_START'`، `is_active`: `1`، `competition_id`: `'COMP_1'`.
3. **جدول `store_products` (۴ بسته استاندارد فروشگاه مایکت طبق `server/server.js` خطوط 722–799):**
   - `pkg_squares_50` (`myket_sku`: `'reqabat_unlock_50'`, `product_type`: `'SQUARE_UNLOCK'`, `units_granted`: `50`, `price_toman`: `55000`, `sort_order`: `1`, `is_active`: `1`, `is_ready_in_myket`: `1`)
   - `pkg_squares_100` (`myket_sku`: `'reqabat_unlock_100'`, `product_type`: `'SQUARE_UNLOCK'`, `units_granted`: `100`, `price_toman`: `100000`, `sort_order`: `2`, `is_active`: `1`, `is_ready_in_myket`: `1`)
   - `pkg_upgrade_silver` (`myket_sku`: `'reqabat_upgrade_silver'`, `product_type`: `'ACCOUNT_UPGRADE'`, `units_granted`: `3`, `price_toman`: `70000`, `sort_order`: `3`, `is_active`: `1`, `is_ready_in_myket`: `1`)
   - `pkg_upgrade_gold` (`myket_sku`: `'reqabat_upgrade_gold'`, `product_type`: `'ACCOUNT_UPGRADE'`, `units_granted`: `6`, `price_toman`: `120000`, `sort_order`: `4`, `is_active`: `1`, `is_ready_in_myket`: `1`)
4. **جدول `reward_categories` (۴ دسته‌بندی پیش‌فرض جوایز):**
   - `cat_recharge` (`slug`: `'recharge-cards'`), `cat_internet` (`slug`: `'internet-packages'`), `cat_cash` (`slug`: `'cash-gifts'`), `cat_digital` (`slug`: `'digital-giftcards'`).
5. **جدول `reward_appearance` (۱ رکورد تنظیمات ظاهر ویترین):**
   - `id`: `'default'` با تنظیمات پیش‌فرض و `active_sections`: `'{"featured": true, "categories": true, "history": true, "banners": true}'`.
6. **جدول `rewards` (۴ جایزه نمونه اولیه):**
   - `rew_irancell_10k`, `rew_mci_10k`, `rew_irancell_net_5gb`, `rew_cash_50k` (در صورت خالی بودن جدول).
7. **جدول `login_notification_settings` (۱ رکورد تنظیمات صفحه ورود):**
   - `id`: `'default'` (دارای مقادیر پیش‌فرض در DDL؛ تنها درج ردیف `INSERT OR IGNORE INTO login_notification_settings (id) VALUES ('default')` کافی است).

---

## ۴. گزارش ناسازگاری‌ها و نکات مهم Backend و Android (فقط گزارش — بدون تغییر کد)

موارد زیر در ممیزی کدهای فعلی شناسایی شدند و باید پیش از تکیه کامل روی D1 در فاز اصلاح کد بررسی/برطرف شوند:

1. **عدم پشتیبانی SQLite از `= ANY($1::text[])` در خروجی اکسل ادمین (`server/server.js:2800`):**
   - مسیر `POST /api/admin/users/export-selected-excel` از کوئری `WHERE u.id::text = ANY($1::text[])` استفاده می‌کند که در SQLite خطای نحوی می‌دهد و باید در فاز کد با `IN (?, ?, ...)` جایگزین شود.
2. **ناسازگاری مقادیر `0` / `1` به جای `true` / `false` در JSON خروجی D1 و اپلیکیشن اندروید (`BackendRepository.kt:1428-1429` و سایر بخش‌ها):**
   - D1 ستون‌های بولی (`is_active`, `is_featured`, `is_visible`, `warning_is_visible`, `is_ready_in_myket`) را به صورت عدد `0` یا `1` برمی‌گرداند. در اندروید `org.json.JSONObject.optBoolean(key, default)` عدد `0`/`1` را Boolean نمی‌شناسد و مقدار fallback را برمی‌گرداند (مثلاً `isActive` در `/api/rewards` یا `/api/app/login-notification`).
3. **بازگشت ستون‌های JSON به صورت رشته (`String`) به جای آبجکت/آرایه JSON (`rewards-service/server.js:165` و `BackendRepository.kt:1314`):**
   - D1 مقادیر `active_sections`, `additional_images`, `placements`, `placement_stats` را به صورت رشته متنی برمی‌گرداند؛ در حالی که اندروید (`optJSONObject("active_sections")`) و پنل ادمین (`server/public/index.html:4757`) انتظار آبجکت JSON دارند و در Backend باید پیش از ارسال پاسخ `JSON.parse` شوند.
4. **عدم تطابق فرمت `NOW()` در `d1-adapter.js` با فرمت `toISOString()`:**
   - در `server/cloudflare/d1-adapter.js:27` عبارت `NOW()` به `CURRENT_TIMESTAMP` (`YYYY-MM-DD HH:MM:SS` با فاصله) تبدیل می‌شود، در حالی که JS تاریخ‌ها را به صورت ISO-8601 (`YYYY-MM-DDTHH:MM:SS.sssZ` با حرف `T`) ذخیره می‌کند. اگر این دو در کوئری‌هایی مثل `start_at <= NOW()` مقایسه شوند، به دلیل `'T' > ' '` مقایسه در همان روز اشتباه می‌شود. در `d1_schema.sql` پیش‌فرض‌ها با `(strftime('%Y-%m-%dT%H:%M:%fZ', 'now'))` یکدست شده‌اند و در فاز کد بهتر است `d1-adapter.js` نیز از همین عبارت استفاده کند.
5. **اجرای DDL ناسازگار با PostgreSQL در مسیر ذخیره تنظیمات ظاهر جوایز (`server/rewards-service/server.js:505`):**
   - تابع `saveAppearanceHandler` در حین اجرای درخواست ادمین دستورات `CREATE TABLE ... JSONB DEFAULT NOW()` و `ALTER TABLE` را اجرا می‌کند که در D1 خطا می‌دهد.
6. **تراکنش‌های چنددستوری در `d1-adapter.js` (`BEGIN` / `COMMIT` / `ROLLBACK`):**
   - آداپتور فعلی دستورات `BEGIN`/`COMMIT`/`ROLLBACK` را بی‌اثر (no-op) می‌کند؛ بنابراین تراکنش‌های حساس مانند کسر امتیاز در `POST /api/rewards/claim`، پرداخت و افزودن سهمیه، یا ثبت بازخورد در فاز تکمیل Backend باید به `d1.batch()` یا شرط‌های اتمیک `UPDATE ... WHERE ...` متکی شوند.
7. **مسیر مفقود تنظیمات اعلان کاربر در Backend (`BackendRepository.kt:1219`):**
   - اپلیکیشن اندروید درخواست `POST /api/user/settings/notifications` ارسال می‌کند اما چنین مسیری در `server/server.js` تعریف نشده و پاسخ `404` می‌گیرد.
8. **فایل‌های استاتیک پنل ادمین و آپلود تصاویر لوکال (`server/public/` و `/uploads/`):**
   - محیط Cloudflare Workers سیستم فایل ماندگار ندارد؛ ارائه فایل‌های HTML پنل ادمین یا ذخیره تصاویر بنر و جوایز روی دیسک محلی نیازمند Workers Assets / R2 / Cloudflare Images است.
