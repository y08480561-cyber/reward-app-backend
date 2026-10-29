# سرویس مستقل جوایز و هدایا (Rewards Service)

این سرویس یک میکرو‌سرویس مستقل Node.js / Express برای مدیریت کامل ویترین جوایز، دسته‌بندی‌ها، تنظیمات ظاهری، ثبت درخواست‌های دریافت جایزه و فرآیند مدیریت درخواست‌ها (پشتیبانی/ادمین) در پروژه «رقابت کن، جایزه ببر» است.

## ویژگی‌ها
- دریافت لیست جوایز فعال همراه با فیلتر دسته‌بندی، جستجو و جوایز ویژه (Featured)
- دریافت تنظیمات ظاهری و بنرهای داینامیک ویترین جوایز بدون نیاز به انتشار مجدد اپلیکیشن
- ثبت ایمن درخواست دریافت جایزه با کسر اتمیک (Atomic Transaction) امتیاز کاربر و موجودی انبار
- تولید کد پیگیری منحصربه‌فرد برای هر درخواست (`CLM-XXXXXX`)
- مدیریت کامل ادمین (CRUD جوایز، دسته‌بندی‌ها، ظاهر ویترین و تغییر وضعیت درخواست‌ها)
- پشتیبانی از پایگاه‌داده PostgreSQL/Supabase و حالت رزرو حافظه (Fallback)

## نحوه استقرار در Render (Deployment)

1. مخزن گیت‌هاب پروژه را به سرویس Render متصل کنید.
2. یک **Web Service** جدید ایجاد کنید و مسیر Root Directory را برابر `rewards-service` قرار دهید.
3. متغیرهای محیطی (Environment Variables) زیر را تنظیم کنید:
   - `DATABASE_URL`: آدرس اتصال مستقیم به PostgreSQL Supabase
   - `ADMIN_KEY`: کلید امنیتی مدیریت (مثلاً `reqabat_admin_secret_2026`)
   - `PORT`: ۱۰000 یا پورت دلخواه
4. دکمه **Create Web Service** را بزنید.

## مستندات APIها

### بخش عمومی کاربران

- `GET /api/rewards/categories`: لیست دسته‌بندی‌های فعال
- `GET /api/rewards/appearance`: تنظیمات ظاهری و بنرهای هدر
- `GET /api/rewards`: دریافت لیست جوایز (`?category=slug&featured=true&search=کارت`)
- `GET /api/rewards/:id`: دریافت جزئیات یک جایزه
- `POST /api/rewards/claim`: ثبت درخواست دریافت جایزه (با هدر `x-user-id`)
- `GET /api/rewards/claims/user/:userId`: دریافت سوابق جوایز کاربر

### بخش مدیریت (Admin - با هدر `x-admin-key`)

- `GET /api/rewards/admin/rewards`: دریافت لیست تمام جوایز
- `POST /api/rewards/admin/rewards`: ایجاد جایزه جدید
- `PUT /api/rewards/admin/rewards/:id`: به‌روزرسانی جایزه
- `DELETE /api/rewards/admin/rewards/:id`: حذف جایزه
- `GET /api/rewards/admin/categories`: دریافت لیست دسته‌بندی‌ها
- `POST /api/rewards/admin/categories`: ایجاد/ویرایش دسته‌بندی
- `POST /api/rewards/admin/appearance`: بروزرسانی تنظیمات ظاهری ویترین
- `GET /api/rewards/admin/claims`: دریافت درخواست‌های دریافت جایزه
- `PATCH /api/rewards/admin/claims/:id`: تغییر وضعیت و ثبت کد رهگیری پستی/توضیحات ادمین
