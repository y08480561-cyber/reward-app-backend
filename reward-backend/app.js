/**
 * cPanel Passenger Entry Delegation
 * Ensures that if cPanel or Phusion Passenger looks for the default app.js in the
 * Application Root (reward-backend), it seamlessly boots server/server.js.
 */
require('./server/server.js');
