const axios = require('axios');
const crypto = require('crypto');

const GOOGLE_CERTS_URL = 'https://www.googleapis.com/oauth2/v1/certs';
let cachedCerts = null;

function configurationError() {
  const error = new Error('RTDN OIDC configuration missing');
  error.code = 'RTDN_NOT_CONFIGURED';
  return error;
}

function decodePart(value) {
  return JSON.parse(Buffer.from(value, 'base64url').toString('utf8'));
}

async function certificates() {
  if (cachedCerts && cachedCerts.expiresAt > Date.now()) return cachedCerts.value;
  const response = await axios.get(GOOGLE_CERTS_URL, { timeout: 10_000 });
  const cacheSeconds = Number(/max-age=(\d+)/.exec(response.headers['cache-control'] || '')?.[1] || 3600);
  cachedCerts = { value: response.data, expiresAt: Date.now() + cacheSeconds * 1000 };
  return cachedCerts.value;
}

async function verifyPushToken(header) {
  const audience = process.env.GOOGLE_PLAY_RTDN_AUDIENCE;
  const expectedEmail = process.env.GOOGLE_PLAY_RTDN_PUSH_SERVICE_ACCOUNT;
  if (!audience || !expectedEmail) throw configurationError();
  const token = header?.startsWith('Bearer ') ? header.slice(7) : null;
  const [encodedHeader, encodedPayload, encodedSignature, ...extra] = token?.split('.') || [];
  if (!encodedHeader || !encodedPayload || !encodedSignature || extra.length) return false;

  let jwtHeader;
  let payload;
  try {
    jwtHeader = decodePart(encodedHeader);
    payload = decodePart(encodedPayload);
  } catch (_error) {
    return false;
  }
  if (jwtHeader.alg !== 'RS256' || !jwtHeader.kid) return false;
  const cert = (await certificates())[jwtHeader.kid];
  if (!cert) return false;
  const verified = crypto.verify(
    'RSA-SHA256',
    Buffer.from(`${encodedHeader}.${encodedPayload}`),
    cert,
    Buffer.from(encodedSignature, 'base64url')
  );
  const now = Math.floor(Date.now() / 1000);
  return verified &&
    (payload.iss === 'accounts.google.com' || payload.iss === 'https://accounts.google.com') &&
    payload.aud === audience &&
    payload.email === expectedEmail &&
    payload.email_verified === true &&
    Number(payload.exp) > now;
}

module.exports = { verifyPushToken };
