const { pool } = require('../db/db');
const mergePass = require('./mergePassService');

function decodeMessage(message) {
  if (!message?.messageId || !message?.data) {
    const error = new Error('Invalid Pub/Sub message');
    error.code = 'INVALID_RTDN';
    throw error;
  }
  try {
    return {
      messageId: String(message.messageId),
      notification: JSON.parse(Buffer.from(message.data, 'base64').toString('utf8')),
    };
  } catch (_error) {
    const error = new Error('Invalid RTDN payload');
    error.code = 'INVALID_RTDN';
    throw error;
  }
}

async function alreadyProcessed(messageId) {
  const [[row]] = await pool.query(
    'SELECT message_id FROM google_play_rtdn_messages WHERE message_id = ?', [messageId]
  );
  return Boolean(row);
}

async function processMessage(message) {
  const { messageId, notification } = decodeMessage(message);
  if (await alreadyProcessed(messageId)) return { duplicate: true };
  if (notification.packageName !== process.env.GOOGLE_PLAY_PACKAGE_NAME) {
    const error = new Error('RTDN package mismatch');
    error.code = 'INVALID_RTDN';
    throw error;
  }

  let result = { ignored: true };
  if (notification.subscriptionNotification) {
    result = await mergePass.syncRtdn(notification.subscriptionNotification);
  } else if (notification.voidedPurchaseNotification) {
    result = await mergePass.revokeVoidedPurchase(notification.voidedPurchaseNotification);
  }

  await pool.query(
    `INSERT IGNORE INTO google_play_rtdn_messages (message_id, notification_type)
     VALUES (?, ?)`,
    [messageId, notification.subscriptionNotification ? 'SUBSCRIPTION' :
      notification.voidedPurchaseNotification ? 'VOIDED_PURCHASE' :
        notification.oneTimeProductNotification ? 'ONE_TIME_PRODUCT' : 'OTHER']
  );
  return result;
}

module.exports = { process: processMessage };
