module.exports = {
  apps: [
    {
      name: "reqabat-backend",
      script: "./server.js",
      cwd: "./",
      instances: 1, // Main server and rewards router run in a single process
      autorestart: true,
      watch: false,
      max_memory_restart: "1G",
      env: {
        NODE_ENV: "production",
        PORT: 3000
      },
      error_file: "./logs/err.log",
      out_file: "./logs/out.log",
      log_date_format: "YYYY-MM-DD HH:mm:ss Z",
      merge_logs: true
    }
  ]
};
