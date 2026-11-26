/**
 * Cloudflare D1 (SQLite) Adapter for Reqabat Backend
 * Bridges node-postgres (pg) pool.query / pool.connect calls to Cloudflare D1 bindings.
 */

class D1PoolAdapter {
  constructor(d1Binding) {
    this.d1 = d1Binding;
    this.isD1 = true;
  }

  /**
   * Convert PostgreSQL SQL syntax & positional $1..$N params into SQLite/D1 SQL & ? params.
   * Reorders/duplicates parameters whenever $N appears out of order or multiple times.
   */
  convertQuery(text, params = []) {
    let q = String(text || '');

    // Strip PostgreSQL schema prefix
    q = q.replace(/\bpublic\./gi, '');

    // Strip PostgreSQL type casts (::jsonb, ::text, ::int, etc.)
    q = q.replace(/::(jsonb|json|text|varchar|int|integer|bigint|smallint|boolean|bool|timestamptz|timestamp|numeric|float8|float4)/gi, '');

    // PostgreSQL -> SQLite syntax equivalents
    q = q.replace(/\bILIKE\b/gi, 'LIKE');
    q = q.replace(/\bNOW\(\)/gi, 'CURRENT_TIMESTAMP');
    q = q.replace(/\bGEN_RANDOM_UUID\(\)/gi, '(lower(hex(randomblob(4))) || "-" || lower(hex(randomblob(2))) || "-4" || substr(lower(hex(randomblob(2))),2,3) || "-" || substr("89ab", abs(random()) % 4 + 1, 1) || substr(lower(hex(randomblob(2))),2,3) || "-" || lower(hex(randomblob(6))))');
    q = q.replace(/\bFOR\s+(UPDATE|SHARE)(\s+SKIP\s+LOCKED)?\b/gi, '');

    // Map $1, $2, ... outside single-quoted string literals to ? in exact appearance order
    const reorderedParams = [];
    const inputParams = Array.isArray(params) ? params : [];
    q = q.replace(/('(?:''|[^'])*')|\$(\d+)/g, (match, strLiteral, paramIndexStr) => {
      if (strLiteral !== undefined) {
        return strLiteral;
      }
      const idx = parseInt(paramIndexStr, 10) - 1;
      reorderedParams.push(idx >= 0 && idx < inputParams.length ? inputParams[idx] : null);
      return '?';
    });

    return { sql: q, params: reorderedParams };
  }

  sanitizeParam(p) {
    if (p === undefined || p === null) return null;
    if (p instanceof Date) return p.toISOString();
    if (typeof p === 'boolean') return p ? 1 : 0;
    if (typeof p === 'object' && !Buffer.isBuffer(p)) {
      return JSON.stringify(p);
    }
    return p;
  }

  async query(text, params = []) {
    if (!this.d1) {
      throw new Error('[D1 Adapter Error] D1 database binding (env.DB) is not initialized.');
    }

    const trimmed = String(text || '').trim().replace(/;+$/, '');
    const upperCmd = trimmed.toUpperCase();

    // D1 does not support raw BEGIN / COMMIT / ROLLBACK SQL statements in prepare().run()
    if (
      upperCmd === 'BEGIN' ||
      upperCmd === 'BEGIN TRANSACTION' ||
      upperCmd === 'COMMIT' ||
      upperCmd === 'END' ||
      upperCmd === 'ROLLBACK'
    ) {
      return { rows: [], rowCount: 0 };
    }

    const { sql: convertedSql, params: orderedParams } = this.convertQuery(text, params);
    const sanitizedParams = orderedParams.map((p) => this.sanitizeParam(p));

    const stmt = this.d1.prepare(convertedSql);
    const boundStmt = sanitizedParams.length > 0 ? stmt.bind(...sanitizedParams) : stmt;

    const lowerSql = convertedSql.trim().toLowerCase();
    const returnsRows =
      lowerSql.startsWith('select') ||
      lowerSql.startsWith('with') ||
      lowerSql.startsWith('values') ||
      lowerSql.startsWith('pragma') ||
      /\breturning\b/i.test(convertedSql);

    if (returnsRows) {
      const res = await boundStmt.all();
      const rows = res && Array.isArray(res.results) ? res.results : [];
      return {
        rows,
        rowCount: rows.length
      };
    } else {
      const res = await boundStmt.run();
      const rows = res && Array.isArray(res.results) ? res.results : [];
      return {
        rows,
        rowCount: (res && res.meta && typeof res.meta.changes === 'number') ? res.meta.changes : 0
      };
    }
  }

  async connect() {
    if (!this.d1) {
      throw new Error('[D1 Adapter Error] D1 database binding (env.DB) is not initialized.');
    }
    const self = this;
    return {
      isD1: true,
      query(text, params = []) {
        return self.query(text, params);
      },
      release() {
        // No-op for D1
      }
    };
  }

  async end() {
    return Promise.resolve();
  }
}

module.exports = { D1PoolAdapter };
