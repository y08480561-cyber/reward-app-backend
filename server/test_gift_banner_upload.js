const fs = require('fs');
const path = require('path');
const http = require('http');

async function runTest() {
  console.log('--- Starting Gift Banner Server Upload Test ---');

  // Verify source image
  const sourceImage = path.join(__dirname, '../public/assets/aistudio/gift_banner_5mb_475fd6ee.jpg');
  if (!fs.existsSync(sourceImage)) {
    console.error('Source image does not exist at:', sourceImage);
    process.exit(1);
  }
  const imgBuffer = fs.readFileSync(sourceImage);
  console.log('Source image loaded, byte length:', imgBuffer.length);

  // Load rewards service and start on test port 3099
  const { app } = require('./rewards-service/server.js');
  const server = http.createServer(app);

  await new Promise((resolve) => server.listen(3099, resolve));
  console.log('Rewards test server listening on port 3099');

  try {
    // 1. Test POST /api/rewards/admin/upload-banner
    console.log('\nStep 1: Uploading banner image via multipart form-data...');
    const boundary = '----WebKitFormBoundary' + Math.random().toString(16).substring(2);
    
    let headerPart = Buffer.from(
      `--${boundary}\r\n` +
      `Content-Disposition: form-data; name="banner"; filename="gift_banner_20gb.jpg"\r\n` +
      `Content-Type: image/jpeg\r\n\r\n`
    );
    let footerPart = Buffer.from(`\r\n--${boundary}--\r\n`);
    let multipartBody = Buffer.concat([headerPart, imgBuffer, footerPart]);

    const uploadRes = await fetch('http://127.0.0.1:3099/api/rewards/admin/upload-banner', {
      method: 'POST',
      headers: {
        'x-admin-key': 'ReqabatAdmin2026',
        'Content-Type': `multipart/form-data; boundary=${boundary}`,
        'Content-Length': multipartBody.length.toString()
      },
      body: multipartBody
    });

    const uploadData = await uploadRes.json();
    console.log('Upload response status:', uploadRes.status);
    console.log('Upload response body:', uploadData);

    if (!uploadData.success || !uploadData.url) {
      throw new Error('Upload banner failed: ' + JSON.stringify(uploadData));
    }
    const uploadedUrl = uploadData.url;
    console.log('SUCCESS: Banner uploaded. Returned URL:', uploadedUrl);

    // 2. Test saving appearance
    console.log('\nStep 2: Saving appearance via POST /api/rewards/admin/appearance...');
    const saveRes = await fetch('http://127.0.0.1:3099/api/rewards/admin/appearance', {
      method: 'POST',
      headers: {
        'x-admin-key': 'ReqabatAdmin2026',
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        hero_title: 'اینترنت ۲۰ گیگ برنده شو!',
        hero_subtitle: 'هدیه ویژه دوره جاری مسابقه رقابت کن، جایزه ببر',
        hero_banner_url: uploadedUrl,
        primary_color: '#10B981',
        is_active: true,
        terms_text: 'این بخش صرفاً برای معرفی هدیه است و نیازی به خرید یا ثبت درخواست ندارد.'
      })
    });
    const saveData = await saveRes.json();
    console.log('Save appearance response:', saveData);
    if (!saveData.success) {
      throw new Error('Save appearance failed: ' + JSON.stringify(saveData));
    }

    // 3. Test GET /api/rewards/appearance
    console.log('\nStep 3: Fetching appearance via GET /api/rewards/appearance...');
    const getRes = await fetch('http://127.0.0.1:3099/api/rewards/appearance');
    const getData = await getRes.json();
    console.log('GET appearance response:', getData);

    if (!getData.success || !getData.appearance) {
      throw new Error('GET appearance failed: ' + JSON.stringify(getData));
    }
    const appConfig = getData.appearance;
    if (appConfig.hero_banner_url !== uploadedUrl) {
      throw new Error(`Banner URL mismatch: expected ${uploadedUrl}, got ${appConfig.hero_banner_url}`);
    }
    console.log('SUCCESS: Active single banner confirmed:', appConfig.hero_banner_url);

    // 4. Test Persistence Across Restarts
    console.log('\nStep 4: Testing persistence across simulated server restart...');
    server.close();

    // Reload module or check appearance file
    const appearanceFile = path.join(__dirname, 'public/uploads/appearance.json');
    if (fs.existsSync(appearanceFile)) {
      const persistedJson = JSON.parse(fs.readFileSync(appearanceFile, 'utf8'));
      console.log('Persisted appearance.json content:', persistedJson);
      if (persistedJson.hero_banner_url !== uploadedUrl) {
        throw new Error('Persisted file has incorrect banner URL');
      }
      console.log('SUCCESS: appearance.json verified on disk!');
    } else {
      console.log('Note: Running with database pool or without local file.');
    }

    // Restart fresh instance
    const freshRewardsModule = require('./rewards-service/server.js');
    const server2 = http.createServer(freshRewardsModule.app);
    await new Promise((resolve) => server2.listen(3098, resolve));

    const checkRes = await fetch('http://127.0.0.1:3098/api/rewards/appearance');
    const checkData = await checkRes.json();
    server2.close();

    console.log('Post-restart GET appearance response:', checkData);
    if (checkData.appearance?.hero_banner_url !== uploadedUrl) {
      throw new Error('Appearance did not persist across restart!');
    }
    console.log('SUCCESS: Appearance fully persisted across server restart!');

    console.log('\n=== ALL TESTS PASSED SUCCESSFULLY! ===');
    process.exit(0);
  } catch (err) {
    console.error('TEST FAILED:', err);
    server.close();
    process.exit(1);
  }
}

runTest();
