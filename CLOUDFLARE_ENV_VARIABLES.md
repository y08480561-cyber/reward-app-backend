# CLOUDFLARE ENVIRONMENT VARIABLES & SECRETS

مستندات متغیرهای محیطی و Secretهای موردنیاز در Cloudflare Workers:

| نام متغیر | نوع | توضیحات |
|---|---|---|
| `ADMIN_KEY` | Secret | کلید امنیتی دسترسی به پنل مدیریت و APIهای مدیریت |
| `DATABASE_URL` | Secret / Hyperdrive | آدرس اتصال به پایگاه داده PostgreSQL / Supabase (از طریق Connection Pooler) |
| `ONESIGNAL_APP_ID` | Secret / Var | شناسه اپلیکیشن OneSignal برای ارسال پوش نوتیفیکیشن |
| `SUPABASE_URL` | Var | آدرس پروژه Supabase |
| `SUPABASE_ANON_KEY` | Secret | کلید عمومی (Anon) سه‌پایه Supabase |
| `SUPABASE_SERVICE_ROLE_KEY` | Secret | کلید دسترسی سطح سرویس Supabase |
| `TAPSELL_APP_KEY` | Secret | کلید اپلیکیشن تپسل |
| `TAPSELL_KEY` | Secret | کلید ریوارد تپسل |
| `TAPSELL_REWARDED_ZONE_ID` | Var | شناسه منطقه ویدیوی جایزه‌دار تپسل |
| `TAPSELL_ZONE_ID` | Var | شناسه منطقه تبلیغات تپسل |
