/**
 * Cloudflare D1 (SQLite) Adapter for Reqabat Backend
 * Bridges node-postgres (pg) pool.query calls to Cloudflare D1 bindings.
 */

class D1PoolAdapter {
  constructor(d1Binding) {
    this.d1 = d1Binding;
  }

  // Convert PostgreSQL $1, $2 syntax to SQLite ? or ?1, ?2
  convertQuery(text) {
    let q = text;
    // Replace CAST($x AS jsonb) or similar Postgres cast syntax
    q = q.replace(/::jsonb/gi, '').replace(/::text/gi, '').replace(/::int/gi, '');
    q = q.replace(/NOW\(\)/gi, 'CURRENT_TIMESTAMP');
    q = q.replace(/GEN_RANDOM_UUID\(\)/gi, '(lower(hex(randomblob(4))) || "-" || lower(hex(randomblob(2))) || "-4" || substr(lower(hex(randomblob(2))),2,3) || "-" || substr("89ab", abs(random()) % 4 + 1, 1) || substr(lower(hex(randomblob(2))),2,3) || "-" || lower(hex(randomblob(6))))');
    
    // Replace $1, $2 with ?
    let index = 1;
    q = q.replace(/\$[0-9]+/g, () => `?`);
    return q;
  }

  async query(text, params = []) {
    if (!this.d1) {
      throw new Error('[D1 Adapter Error] D1 database binding is not initialized.');
    }

    const convertedSql = this.convertQuery(text);
    const sanitizedParams = (params || []).map(p => {
      if (p && typeof p === 'object' && !Buffer.isBuffer(p) && !(p instanceof Date)) {
        return JSON.stringify(p);
      }
      return p === undefined ? null : p;
    });

    const stmt = this.d1.prepare(convertedSql);
    const boundStmt = sanitizedParams.length > 0 ? stmt.bind(...sanitizedParams) : stmt;

    const lowerSql = text.trim().toLowerCase();
    if (lowerSql.startsWith('select') || lowerSql.startsWith('with') || lowerSql.startsWith('values')) {
      const res = await boundStmt.all();
      return {
        rows: res.results || [],
        rowCount: (res.results || []).length
      };
    } else {
      const res = await boundStmt.run();
      return {
        rows: res.results || [],
        rowCount: res.meta?.changes || 0
      };
    }
  }

  async end() {
    return Promise.resolve();
  }
}

module.exports = { D1PoolAdapter };
