const assert = require('assert');
const http = require('http');
const { app, rewardsRouter, pool } = require('../rewards-service/server.js');

let server;
const PORT = 3099;
const ADMIN_KEY = 'reqabat_admin_secret_2026';

function request(options, postData) {
  return new Promise((resolve, reject) => {
    const req = http.request(options, (res) => {
      let data = '';
      res.on('data', (chunk) => data += chunk);
      res.on('end', () => {
        try {
          const parsed = JSON.parse(data);
          resolve({ status: res.statusCode, body: parsed });
        } catch (e) {
          resolve({ status: res.statusCode, raw: data });
        }
      });
    });
    req.on('error', reject);
    if (postData) {
      req.write(typeof postData === 'string' ? postData : JSON.stringify(postData));
    }
    req.end();
  });
}

async function runTests() {
  console.log('====================================================');
  console.log('  تست کامل APIهای سرویس مستقل جوایز (Rewards Test)');
  console.log('====================================================');

  server = app.listen(PORT);
  let createdRewardId = null;
  let testClaimTrackingCode = null;

  try {
    // 1. Get Categories
    console.log('\n[تست ۱] دریافت لیست دسته‌بندی‌های جوایز...');
    const catRes = await request({
      hostname: 'localhost',
      port: PORT,
      path: '/api/rewards/categories',
      method: 'GET'
    });
    assert.strictEqual(catRes.status, 200, 'کد ۲00 برای دریافت دسته‌بندی‌ها');
    assert.strictEqual(catRes.body.success, true);
    assert(Array.isArray(catRes.body.categories), 'لیست دسته‌بندی‌ها آرایه است');
    console.log('✓ دسته‌بندی‌ها با موفقیت دریافت شدند. تعداد:', catRes.body.categories.length);

    // 2. Get Appearance Settings
    console.log('\n[تست ۲] دریافت تنظیمات ظاهری ویترین...');
    const appRes = await request({
      hostname: 'localhost',
      port: PORT,
      path: '/api/rewards/appearance',
      method: 'GET'
    });
    assert.strictEqual(appRes.status, 200);
    assert.strictEqual(appRes.body.success, true);
    assert(appRes.body.appearance.hero_title, 'عنوان هدر تعریف شده است');
    console.log('✓ تنظیمات ظاهر دریافت شد:', appRes.body.appearance.hero_title);

    // 3. Admin Security Check (Unauthenticated request should fail 401)
    console.log('\n[تست ۳] بررسی امنیت هدر مدیریت (x-admin-key)...');
    const unauthRes = await request({
      hostname: 'localhost',
      port: PORT,
      path: '/api/rewards/admin/rewards',
      method: 'GET'
    });
    assert.strictEqual(unauthRes.status, 401, 'عدم ارسال کلید باید ۴01 بدهد');
    console.log('✓ هدر غیرمجاز به درستی مسدود گردید.');

    // 4. Admin Create Reward
    console.log('\n[تست ۴] ایجاد جایزه جدید توسط مدیر...');
    const createRes = await request({
      hostname: 'localhost',
      port: PORT,
      path: '/api/rewards/admin/rewards',
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'x-admin-key': ADMIN_KEY
      }
    }, {
      title: 'گیفت کارت ۵۰ هزار تومانی تست',
      description: 'توضیحات تست اتوماتیک برای گیفت کارت',
      category_id: 'cat_digital',
      cost_points: 150,
      stock_quantity: 10,
      image_url: 'https://images.unsplash.com/photo-1513151233558-d860c5398176',
      is_featured: true,
      is_active: true
    });
    assert.strictEqual(createRes.status, 200);
    assert.strictEqual(createRes.body.success, true);
    createdRewardId = createRes.body.reward.id;
    console.log('✓ جایزه جدید ساخته شد با شناسه:', createdRewardId);

    // 5. Get Public Rewards List
    console.log('\n[تست ۵] دریافت ویترین عمومی جوایز...');
    const listRes = await request({
      hostname: 'localhost',
      port: PORT,
      path: '/api/rewards',
      method: 'GET'
    });
    assert.strictEqual(listRes.status, 200);
    assert(listRes.body.rewards.length > 0, 'ویترین جوایز شامل موارد است');
    console.log('✓ تعداد جوایز فعال در ویترین:', listRes.body.rewards.length);

    // 6. User Claim Reward Request
    console.log('\n[تست ۶] ثبت درخواست دریافت جایزه توسط کاربر...');
    const claimRes = await request({
      hostname: 'localhost',
      port: PORT,
      path: '/api/rewards/claim',
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'x-user-id': 'user_test_999'
      }
    }, {
      reward_id: createdRewardId || 'rew_irancell_10k',
      recipient_name: 'علی محمدی',
      phone: '09121112233',
      shipping_address: 'تهران، خیابان آزادی، پلاک ۱۲'
    });
    assert.strictEqual(claimRes.status, 200);
    assert.strictEqual(claimRes.body.success, true);
    testClaimTrackingCode = claimRes.body.tracking_code;
    console.log('✓ درخواست جایزه با موفقیت ثبت شد. کد پیگیری:', testClaimTrackingCode);

    // 7. Get User Claim History
    console.log('\n[تست ۷] مشاهده سوابق درخواست‌های کاربر...');
    const historyRes = await request({
      hostname: 'localhost',
      port: PORT,
      path: '/api/rewards/claims/user/user_test_999',
      method: 'GET'
    });
    assert.strictEqual(historyRes.status, 200);
    assert(historyRes.body.claims.length > 0, 'سوابق کاربر دریافت شد');
    console.log('✓ تعداد درخواست‌های ثبت‌شده کاربر:', historyRes.body.claims.length);

    // 8. Admin Update Claim Status
    console.log('\n[تست ۸] تغییر وضعیت درخواست جایزه توسط مدیر...');
    const claimIdToUpdate = historyRes.body.claims[0].id;
    const patchRes = await request({
      hostname: 'localhost',
      port: PORT,
      path: `/api/rewards/admin/claims/${claimIdToUpdate}`,
      method: 'PATCH',
      headers: {
        'Content-Type': 'application/json',
        'x-admin-key': ADMIN_KEY
      }
    }, {
      status: 'SHIPPED',
      tracking_number: 'POST-987654321',
      admin_notes: 'مرسوله به اداره پست تحویل گردید'
    });
    assert.strictEqual(patchRes.status, 200);
    assert.strictEqual(patchRes.body.success, true);
    assert.strictEqual(patchRes.body.claim.status, 'SHIPPED');
    console.log('✓ وضعیت درخواست به SHIPPED تغییر یافت همراه با کد رهگیری پستی.');

    // 9. Admin Update Appearance
    console.log('\n[تست ۹] بروزرسانی بنرها و ظاهر ویترین توسط مدیر...');
    const updateAppRes = await request({
      hostname: 'localhost',
      port: PORT,
      path: '/api/rewards/admin/appearance',
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'x-admin-key': ADMIN_KEY
      }
    }, {
      hero_title: 'ویترین طلایی جوایز بهاره',
      primary_color: '#F59E0B',
      hero_subtitle: 'ویژه کاربران وفادار شبکه ایرانسل',
      hero_banner_url: 'https://images.unsplash.com/photo-1513151233558-d860c5398176',
      terms_text: 'قوانین و تکمیلی تحویل جایزه'
    });
    assert.strictEqual(updateAppRes.status, 200);
    assert.strictEqual(updateAppRes.body.success, true);
    console.log('✓ ظاهر ویترین با موفقیت در سیستم ذخیره گردید.');

    console.log('\n====================================================');
    console.log('  تمام تست‌های سرویس جوایز با موفقیت ۱00٪ پاس شدند!  ');
    console.log('====================================================');

  } catch (err) {
    console.error('FAILED TEST:', err);
    process.exitCode = 1;
  } finally {
    if (server) server.close();
    if (pool) await pool.end().catch(() => {});
  }
}

runTests();
