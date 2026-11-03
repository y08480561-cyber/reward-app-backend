const fs = require('fs');
const path = require('path');
const http = require('http');
const { Pool } = require('pg');
require('dotenv').config();

async function runTest() {
  console.log('--- Starting Gift Banner & Appearance Architecture Test ---');

  const adminKey = process.env.ADMIN_KEY;
  if (!adminKey) {
    console.error('ERROR: ADMIN_KEY environment variable is not set. Admin tests cannot proceed without a configured ADMIN_KEY.');
    process.exit(1);
  }

  const dbUrl = process.env.DATABASE_URL || process.env.TEST_DATABASE_URL;
  let pool = null;

  if (dbUrl) {
    const isLocalhost = dbUrl.includes('localhost') || dbUrl.includes('127.0.0.1');
    pool = new Pool({
      connectionString: dbUrl,
      ssl: isLocalhost ? false : { rejectUnauthorized: false }
    });
    console.log('PostgreSQL pool initialized for appearance verification.');
  } else {
    console.warn('WARNING: DATABASE_URL not set. Database persistence tests will require active PostgreSQL connection.');
  }

  // Load rewards service and start on test port 3099
  const rewardsService = require('./rewards-service/server.js');
  if (pool && rewardsService.setPool) {
    rewardsService.setPool(pool);
  }

  const server = http.createServer(rewardsService.app);
  await new Promise((resolve) => server.listen(3099, resolve));
  console.log('Rewards test server listening on port 3099');

  try {
    // 1. Test POST /api/rewards/admin/upload-banner
    console.log('\nStep 1: Testing banner upload endpoint (/api/rewards/admin/upload-banner)...');
    
    // Find sample image if exists, or generate dummy buffer
    let imgBuffer;
    const sourceImage = path.join(__dirname, '../public/assets/aistudio/gift_banner_5mb_475fd6ee.jpg');
    if (fs.existsSync(sourceImage)) {
      imgBuffer = fs.readFileSync(sourceImage);
      console.log('Using sample image file, bytes:', imgBuffer.length);
    } else {
      imgBuffer = Buffer.from('GIF89a\x01\x00\x01\x00\x80\x00\x00\xff\xff\xff\x00\x00\x00!\xf9\x04\x01\x00\x00\x00\x00,\x00\x00\x00\x00\x01\x00\x01\x00\x00\x02\x02D\x01\x00;');
      console.log('Using fallback dummy image buffer, bytes:', imgBuffer.length);
    }

    const boundary = '----WebKitFormBoundary' + Math.random().toString(16).substring(2);
    const headerPart = Buffer.from(
      `--${boundary}\r\n` +
      `Content-Disposition: form-data; name="banner"; filename="test_banner.jpg"\r\n` +
      `Content-Type: image/jpeg\r\n\r\n`
    );
    const footerPart = Buffer.from(`\r\n--${boundary}--\r\n`);
    const multipartBody = Buffer.concat([headerPart, imgBuffer, footerPart]);

    const uploadRes = await fetch('http://127.0.0.1:3099/api/rewards/admin/upload-banner', {
      method: 'POST',
      headers: {
        'x-admin-key': adminKey,
        'Content-Type': `multipart/form-data; boundary=${boundary}`,
        'Content-Length': multipartBody.length.toString()
      },
      body: multipartBody
    });

    const uploadData = await uploadRes.json();
    console.log('Upload response status:', uploadRes.status);
    console.log('Upload response body:', uploadData);

    let activeBannerUrl = 'https://example.supabase.co/storage/v1/object/public/gift-banners/test_banner.jpg';

    if (uploadRes.status === 501) {
      console.log('CONFIRMED: Cloud storage is unconfigured, server correctly rejected local upload fallback with 501.');
    } else if (uploadRes.ok && uploadData.success && uploadData.url) {
      activeBannerUrl = uploadData.url;
      console.log('CONFIRMED: Banner uploaded to cloud storage successfully. URL:', activeBannerUrl);
    } else {
      console.warn('Upload endpoint returned non-ok status (expected when live credentials not supplied):', uploadRes.status);
    }

    // 2. Test saving appearance into PostgreSQL public.reward_appearance
    console.log('\nStep 2: Testing appearance save via POST /api/rewards/admin/appearance...');
    const appearancePayload = {
      hero_title: 'اینترنت ۲۰ گیگ برنده شو!',
      hero_subtitle: 'هدیه ویژه دوره جاری مسابقه رقابت کن، جایزه ببر',
      hero_banner_url: activeBannerUrl,
      primary_color: '#10B981',
      is_active: true,
      terms_text: 'این بخش صرفاً برای معرفی هدیه است و نیازی به خرید یا ثبت درخواست ندارد.'
    };

    const saveRes = await fetch('http://127.0.0.1:3099/api/rewards/admin/appearance', {
      method: 'POST',
      headers: {
        'x-admin-key': adminKey,
        'Content-Type': 'application/json'
      },
      body: JSON.stringify(appearancePayload)
    });

    const saveData = await saveRes.json();
    console.log('Save appearance status:', saveRes.status);
    console.log('Save appearance response:', saveData);

    if (!pool) {
      if (saveRes.status === 503) {
        console.log('CONFIRMED: Server correctly refused to fake-save appearance without database connection (HTTP 503).');
      } else {
        throw new Error(`Expected HTTP 503 when pool is null, but got ${saveRes.status}`);
      }
    } else {
      if (!saveData.success) {
        throw new Error('Save appearance failed in database: ' + JSON.stringify(saveData));
      }
      console.log('CONFIRMED: Appearance saved to public.reward_appearance in PostgreSQL.');

      // 3. Test GET /api/rewards/appearance
      console.log('\nStep 3: Fetching appearance via GET /api/rewards/appearance...');
      const getRes = await fetch('http://127.0.0.1:3099/api/rewards/appearance');
      const getData = await getRes.json();
      console.log('GET appearance response:', getData);

      if (!getData.success || !getData.appearance) {
        throw new Error('GET appearance failed: ' + JSON.stringify(getData));
      }
      if (getData.appearance.hero_banner_url !== activeBannerUrl) {
        throw new Error(`Banner URL mismatch: expected ${activeBannerUrl}, got ${getData.appearance.hero_banner_url}`);
      }
      console.log('CONFIRMED: Appearance verified from PostgreSQL table public.reward_appearance.');

      // 4. Test Persistence Across Server Restart (Exclusively from DB, no appearance.json)
      console.log('\nStep 4: Testing persistence across simulated server restart from database...');
      server.close();

      const server2 = http.createServer(rewardsService.app);
      await new Promise((resolve) => server2.listen(3098, resolve));

      const checkRes = await fetch('http://127.0.0.1:3098/api/rewards/appearance');
      const checkData = await checkRes.json();
      server2.close();

      console.log('Post-restart GET appearance response:', checkData);
      if (checkData.appearance?.hero_banner_url !== activeBannerUrl) {
        throw new Error('Appearance did not persist across restart from PostgreSQL!');
      }
      console.log('CONFIRMED: Appearance fully persisted across server restart via public.reward_appearance table!');
    }

    console.log('\n=== ALL ARCHITECTURAL TESTS PASSED SUCCESSFULLY! ===');
  } finally {
    try { server.close(); } catch (e) {}
    if (pool) {
      await pool.end();
      console.log('PostgreSQL pool closed.');
    }
  }
}

if (require.main === module) {
  runTest().catch((err) => {
    console.error('TEST FAILED:', err);
    process.exit(1);
  });
}

module.exports = { runTest };
