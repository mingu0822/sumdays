const { OAuth2Client } = require('google-auth-library');

const client = new OAuth2Client();

async function verifyGoogleIdToken(idToken) {
  const clientId = process.env.GOOGLE_CLIENT_ID;
  if (!clientId) {
    const error = new Error('GOOGLE_CLIENT_ID is not configured');
    error.code = 'GOOGLE_AUTH_NOT_CONFIGURED';
    throw error;
  }

  const ticket = await client.verifyIdToken({ idToken, audience: clientId });
  const payload = ticket.getPayload();
  if (!payload?.sub || !payload.email || payload.email_verified !== true) {
    throw new Error('Google ID token is missing a verified identity');
  }

  return {
    subject: payload.sub,
    email: payload.email.toLowerCase(),
    name: payload.name || payload.email.split('@')[0],
    picture: payload.picture || null,
    trustedEmail: payload.email.toLowerCase().endsWith('@gmail.com') || Boolean(payload.hd),
  };
}

module.exports = { verifyGoogleIdToken };
