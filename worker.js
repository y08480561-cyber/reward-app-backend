if (typeof globalThis.__dirname === 'undefined') {
  globalThis.__dirname = '/app/server';
}
if (typeof globalThis.__filename === 'undefined') {
  globalThis.__filename = '/app/server/server.js';
}

import { httpServerHandler } from 'cloudflare:node';
import http from 'node:http';

const app = require('./server/server.js');
const server = http.createServer(app);
const handler = httpServerHandler(server);

export default {
  async fetch(request, env, ctx) {
    if (env.ADMIN_KEY) {
      process.env.ADMIN_KEY = env.ADMIN_KEY;
    }
    // Support Cloudflare Hyperdrive binding or environment secret DATABASE_URL
    if (env.DATABASE && env.DATABASE.connectionString) {
      process.env.DATABASE_URL = env.DATABASE.connectionString;
    } else if (env.DATABASE_URL) {
      process.env.DATABASE_URL = env.DATABASE_URL;
    }
    if (env.ONESIGNAL_APP_ID) process.env.ONESIGNAL_APP_ID = env.ONESIGNAL_APP_ID;
    if (env.SUPABASE_URL) process.env.SUPABASE_URL = env.SUPABASE_URL;
    if (env.SUPABASE_ANON_KEY) process.env.SUPABASE_ANON_KEY = env.SUPABASE_ANON_KEY;

    return handler.fetch(request, env, ctx);
  }
};
