
const http = require('http');

const options = {
  hostname: 'localhost',
  port: 3000,
  path: '/api/app/login-notification',
  method: 'GET'
};

const req = http.request(options, res => {
  console.log(`Status Code: ${res.statusCode}`);
  let data = '';
  res.on('data', chunk => {
    data += chunk;
  });
  res.on('end', () => {
    console.log('Response Body:', data);
    try {
      const json = JSON.parse(data);
      if (json.success && json.settings) {
        console.log('TEST PASSED: Public API returned settings correctly.');
      } else {
        console.log('TEST FAILED: Success flag or settings missing.');
      }
    } catch (e) {
      console.log('TEST FAILED: Invalid JSON response.');
    }
    process.exit(0);
  });
});

req.on('error', error => {
  console.error('Request Error:', error.message);
  process.exit(1);
});

req.end();
