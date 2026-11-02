const axios = require('axios');
const crypto = require('crypto');
const fs = require('fs');

const ANDROID_PUBLISHER_SCOPE = 'https://www.googleapis.com/auth/androidpublisher';
let cachedAccessToken = null;

function base64Url(value) {
  return Buffer.from(value)
    .toString('base64')
    .replace(/=/g, '')
    .replace(/\+/g, '-')
    .replace(/\//g, '_');
}

function configError(message) {
  const error = new Error(message);
  error.code = 'PLAY_BILLING_NOT_CONFIGURED';
  return error;
}

function loadCredentials() {
  const inline = process.env.GOOGLE_PLAY_SERVICE_ACCOUNT_JSON;
  const file = process.env.GOOGLE_PLAY_SERVICE_ACCOUNT_FILE || process.env.GOOGLE_APPLICATION_CREDENTIALS;
  try {
    if (inline) return JSON.parse(inline);
    if (file) return JSON.parse(fs.readFileSync(file, 'utf8'));
  } catch (_error) {
    throw configError('Google Play 서비스 계정 정보를 읽지 못했습니다.');
  }
  throw configError('Google Play 서비스 계정 환경변수가 설정되지 않았습니다.');
}

function requiredPackageName() {
  const packageName = process.env.GOOGLE_PLAY_PACKAGE_NAME;
  if (!packageName) throw configError('GOOGLE_PLAY_PACKAGE_NAME이 설정되지 않았습니다.');
  return packageName;
}

async function accessToken() {
  if (cachedAccessToken && cachedAccessToken.expiresAt > Date.now() + 60_000) {
    return cachedAccessToken.value;
  }

  const credentials = loadCredentials();
  if (!credentials.client_email || !credentials.private_key || !credentials.token_uri) {
    throw configError('Google Play 서비스 계정 JSON 형식이 올바르지 않습니다.');
  }

  const now = Math.floor(Date.now() / 1000);
  const header = base64Url(JSON.stringify({ alg: 'RS256', typ: 'JWT' }));
  const claim = base64Url(JSON.stringify({
    iss: credentials.client_email,
    scope: ANDROID_PUBLISHER_SCOPE,
    aud: credentials.token_uri,
    iat: now,
    exp: now + 3600,
  }));
  const unsigned = `${header}.${claim}`;
  const signature = crypto
    .createSign('RSA-SHA256')
    .update(unsigned)
    .end()
    .sign(credentials.private_key, 'base64url');

  try {
    const response = await axios.post(
      credentials.token_uri,
      new URLSearchParams({
        grant_type: 'urn:ietf:params:oauth:grant-type:jwt-bearer',
        assertion: `${unsigned}.${signature}`,
      }).toString(),
      { headers: { 'Content-Type': 'application/x-www-form-urlencoded' }, timeout: 10_000 }
    );
    cachedAccessToken = {
      value: response.data.access_token,
      expiresAt: Date.now() + Number(response.data.expires_in || 3600) * 1000,
    };
    return cachedAccessToken.value;
  } catch (error) {
    error.code = error.code || 'PLAY_VERIFY_FAILED';
    throw error;
  }
}

async function publisherRequest(method, path) {
  const token = await accessToken();
  try {
    return await axios({
      method,
      url: `https://androidpublisher.googleapis.com/androidpublisher/v3${path}`,
      headers: { Authorization: `Bearer ${token}` },
      timeout: 10_000,
    });
  } catch (error) {
    const status = error.response?.status;
    if (status === 401) cachedAccessToken = null;
    error.code = error.code || 'PLAY_VERIFY_FAILED';
    throw error;
  }
}

async function getProductPurchase(purchaseToken) {
  const packageName = requiredPackageName();
  const response = await publisherRequest(
    'get',
    `/applications/${encodeURIComponent(packageName)}/purchases/productsv2/tokens/${encodeURIComponent(purchaseToken)}`
  );
  return response.data;
}

async function consumeProduct(productId, purchaseToken) {
  const packageName = requiredPackageName();
  await publisherRequest(
    'post',
    `/applications/${encodeURIComponent(packageName)}/purchases/products/${encodeURIComponent(productId)}/tokens/${encodeURIComponent(purchaseToken)}:consume`
  );
}

async function getSubscriptionPurchase(purchaseToken) {
  const packageName = requiredPackageName();
  const response = await publisherRequest(
    'get',
    `/applications/${encodeURIComponent(packageName)}/purchases/subscriptionsv2/tokens/${encodeURIComponent(purchaseToken)}`
  );
  return response.data;
}

async function acknowledgeSubscription(productId, purchaseToken) {
  const packageName = requiredPackageName();
  await publisherRequest(
    'post',
    `/applications/${encodeURIComponent(packageName)}/purchases/subscriptions/${encodeURIComponent(productId)}/tokens/${encodeURIComponent(purchaseToken)}:acknowledge`
  );
}

module.exports = {
  getProductPurchase,
  consumeProduct,
  getSubscriptionPurchase,
  acknowledgeSubscription,
};
