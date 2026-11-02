const path = require('path');

process.env.DB_TARGET = 'local';
require('dotenv').config({ path: path.join(__dirname, '.env.local') });

require('./server');
