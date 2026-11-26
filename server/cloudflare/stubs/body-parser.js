module.exports = {
  json: () => (req, res, next) => next(),
  urlencoded: () => (req, res, next) => next(),
  text: () => (req, res, next) => next(),
  raw: () => (req, res, next) => next()
};
