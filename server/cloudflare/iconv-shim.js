/**
 * High-performance, Web-Standard TextDecoder shim for iconv-lite on Cloudflare Workers.
 * Replaces legacy Node streams with native V8 TextDecoder/TextEncoder.
 */
module.exports = {
  decode: function(buf, encoding) {
    try {
      const dec = new TextDecoder(encoding || 'utf-8');
      return dec.decode(buf);
    } catch (_) {
      return Buffer.from(buf).toString('utf-8');
    }
  },
  encode: function(str, encoding) {
    try {
      return Buffer.from(str, encoding || 'utf-8');
    } catch (_) {
      return new TextEncoder().encode(str);
    }
  },
  encodingExists: function(encoding) {
    try {
      new TextDecoder(encoding);
      return true;
    } catch (_) {
      return false;
    }
  },
  getDecoder: function(encoding) {
    const decoder = new TextDecoder(encoding || 'utf-8');
    return {
      write: function(buf) {
        return decoder.decode(buf, { stream: true });
      },
      end: function() {
        return decoder.decode();
      }
    };
  }
};
