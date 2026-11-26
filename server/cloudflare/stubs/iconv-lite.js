module.exports = {
  decode: (buf, enc) => Buffer.from(buf).toString(enc || 'utf8'),
  encode: (str, enc) => Buffer.from(str, enc || 'utf8'),
  encodingExists: () => true,
  getDecoder: () => ({ write: (b) => Buffer.from(b).toString(), end: () => '' }),
  getEncoder: () => ({ write: (s) => Buffer.from(s), end: () => Buffer.alloc(0) })
};
