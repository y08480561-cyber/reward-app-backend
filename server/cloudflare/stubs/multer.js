function multer(options) {
  return {
    single: (fieldName) => (req, res, next) => next(),
    array: (fieldName, maxCount) => (req, res, next) => next(),
    fields: (fields) => (req, res, next) => next(),
    any: () => (req, res, next) => next()
  };
}
multer.memoryStorage = () => ({});
multer.diskStorage = () => ({});
module.exports = multer;
