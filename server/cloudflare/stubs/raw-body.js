module.exports = async function rawBody(req, options, callback) {
  if (typeof options === 'function') {
    callback = options;
    options = {};
  }
  let data = Buffer.alloc(0);
  req.on('data', chunk => {
    data = Buffer.concat([data, Buffer.from(chunk)]);
  });
  req.on('end', () => {
    if (callback) callback(null, data);
  });
  req.on('error', err => {
    if (callback) callback(err);
  });
};
