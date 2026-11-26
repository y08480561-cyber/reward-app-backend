if (typeof globalThis.__dirname === 'undefined') {
  globalThis.__dirname = '/app/server';
}
if (typeof globalThis.__filename === 'undefined') {
  globalThis.__filename = '/app/server/server.js';
}

import { httpServerHandler } from 'cloudflare:node';
import http from 'node:http';
const { D1PoolAdapter } = require('./server/cloudflare/d1-adapter.js');

const app = require('./server/server.js');

export default {
  async fetch(request, env, ctx) {
    if (env.ADMIN_KEY) {
      process.env.ADMIN_KEY = env.ADMIN_KEY;
    }
    if (env.DB) {
      const d1Pool = new D1PoolAdapter(env.DB);
      if (typeof app.setTestPool === 'function') {
        app.setTestPool(d1Pool);
      }
      try {
        const rewardsModule = require('./server/rewards-service/server.js');
        if (rewardsModule && typeof rewardsModule.setPool === 'function') {
          rewardsModule.setPool(d1Pool);
        }
      } catch (_) {}
    } else if (env.DATABASE_URL) {
      process.env.DATABASE_URL = env.DATABASE_URL;
    }

    if (env.ONESIGNAL_APP_ID) process.env.ONESIGNAL_APP_ID = env.ONESIGNAL_APP_ID;
    if (env.SUPABASE_URL) process.env.SUPABASE_URL = env.SUPABASE_URL;
    if (env.SUPABASE_ANON_KEY) process.env.SUPABASE_ANON_KEY = env.SUPABASE_ANON_KEY;

    const server = http.createServer(app);
    const handler = httpServerHandler(server);
    return handler.fetch(request, env, ctx);
  }
};
