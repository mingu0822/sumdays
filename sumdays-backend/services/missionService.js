const moment = require('moment-timezone');
const { pool } = require('../db/db');
const wallet = require('./pointWalletService');

const KOREA_TIMEZONE = 'Asia/Seoul';

function getPeriod() {
  const today = moment().tz(KOREA_TIMEZONE).startOf('day');
  const weekStart = today.clone().startOf('isoWeek');
  return {
    today: today.format('YYYY-MM-DD'),
    weekStart: weekStart.format('YYYY-MM-DD'),
    weekEnd: weekStart.clone().add(6, 'days').format('YYYY-MM-DD'),
  };
}

function longestConsecutiveDays(dateStrings) {
  if (dateStrings.length === 0) return 0;
  const dates = [...new Set(dateStrings)].sort();
  let longest = 1;
  let current = 1;

  for (let index = 1; index < dates.length; index += 1) {
    const previous = moment(dates[index - 1], 'YYYY-MM-DD');
    const next = moment(dates[index], 'YYYY-MM-DD');
    if (next.diff(previous, 'days') === 1) {
      current += 1;
      longest = Math.max(longest, current);
    } else {
      current = 1;
    }
  }
  return longest;
}

async function getEconomySettings(connection = pool) {
  const values = await wallet.readSettings(connection, {
    AD_REWARD_POINTS: 200, AD_DAILY_LIMIT: 3, DEFAULT_POINT_BALANCE: 9000,
  });
  return {
    adRewardPoints: values.AD_REWARD_POINTS,
    adDailyLimit: values.AD_DAILY_LIMIT,
    defaultPointBalance: values.DEFAULT_POINT_BALANCE,
  };
}

async function initializeWallet(userId, legacyPointBalance) {
  const balance = Number(legacyPointBalance);
  if (!Number.isSafeInteger(balance) || balance < 0) return { error: 'INVALID_LEGACY_POINT_BALANCE' };
  return wallet.withWallet(userId, async (_db, pointBalance, created) => ({
    pointBalance, importedLegacyBalance: created,
  }), balance);
}

async function getInventoryQuantity(connection, userId, productType, itemId) {
  const [rows] = await connection.query(
    `SELECT quantity FROM user_shop_inventory
     WHERE user_id = ? AND product_type = ? AND item_id = ?`,
    [userId, productType, itemId]
  );
  return Number(rows[0]?.quantity ?? 0);
}

async function getShopCatalog(userId) {
  return wallet.withWallet(userId, async (connection, pointBalance) => {
    const [rows] = await connection.query(
      `SELECT sp.product_type, sp.item_id, sp.price,
              COALESCE(usi.quantity, 0) AS quantity
       FROM shop_products sp
       LEFT JOIN user_shop_inventory usi
         ON usi.user_id = ?
        AND usi.product_type = sp.product_type
        AND usi.item_id = sp.item_id
       WHERE sp.is_active = 1 OR usi.quantity > 0
       ORDER BY FIELD(sp.product_type, 'THEME', 'ITEM'), sp.item_id`,
      [userId]
    );

    return {
      pointBalance,
      products: rows.map((row) => ({
        productType: row.product_type,
        itemId: Number(row.item_id),
        price: Number(row.price),
        quantity: Number(row.quantity),
        owned: row.product_type === 'THEME' && Number(row.quantity) > 0,
      })),
    };
  });
}

async function purchaseShopProduct(userId, { productType, itemId, idempotencyKey }) {
  if (!['THEME', 'ITEM'].includes(productType) || !Number.isInteger(itemId)) {
    return { error: 'INVALID_PRODUCT' };
  }
  if (typeof idempotencyKey !== 'string' || !/^[A-Za-z0-9-]{16,80}$/.test(idempotencyKey)) {
    return { error: 'INVALID_IDEMPOTENCY_KEY' };
  }

  const transactionKey = `SHOP:${userId}:${idempotencyKey}`;
  return wallet.withWallet(userId, async (connection, before) => {

    const [previousRows] = await connection.query(
      `SELECT balance_after, change_amount, reference_id FROM point_transactions
       WHERE user_id = ? AND idempotency_key = ?`,
      [userId, transactionKey]
    );
    if (previousRows.length > 0) {
      if (previousRows[0].reference_id !== `${productType}:${itemId}`) {
        return { error: 'IDEMPOTENCY_KEY_CONFLICT' };
      }
      const quantity = await getInventoryQuantity(connection, userId, productType, itemId);
      return {
        pointBalance: before,
        price: Math.abs(Number(previousRows[0].change_amount)),
        duplicate: true,
        quantity,
        owned: productType === 'THEME' && quantity > 0,
      };
    }

    const [productRows] = await connection.query(
      `SELECT price FROM shop_products
       WHERE product_type = ? AND item_id = ? AND is_active = 1`,
      [productType, itemId]
    );
    if (productRows.length === 0) {
      return { error: 'PRODUCT_NOT_FOUND' };
    }

    const price = Number(productRows[0].price);
    const currentQuantity = await getInventoryQuantity(
      connection,
      userId,
      productType,
      itemId
    );
    if (productType === 'THEME' && currentQuantity > 0) {
      return {
        pointBalance: before,
        price: 0,
        duplicate: false,
        quantity: currentQuantity,
        owned: true,
        alreadyOwned: true,
      };
    }
    if (before < price) {
      return { error: 'INSUFFICIENT_POINTS', pointBalance: before };
    }

    const balance = await wallet.changeBalance(connection, userId, before, {
      amount: -price, type: 'SHOP_PURCHASE',
      reference: `${productType}:${itemId}`, key: transactionKey,
    });
    await connection.query(
      `INSERT INTO user_shop_inventory
       (user_id, product_type, item_id, quantity)
       VALUES (?, ?, ?, 1)
       ON DUPLICATE KEY UPDATE quantity =
         IF(product_type = 'THEME', 1, quantity + 1)`,
      [userId, productType, itemId]
    );
    const quantity = await getInventoryQuantity(connection, userId, productType, itemId);
    return {
      pointBalance: balance,
      price,
      duplicate: false,
      quantity,
      owned: productType === 'THEME' && quantity > 0,
      alreadyOwned: false,
    };
  });
}

async function getSummaryRewardState(connection, userId) {
  const [readyRows] = await connection.query(
    `SELECT viewed.event_key AS summary_id
     FROM user_mission_events viewed
     INNER JOIN user_mission_events created
       ON created.user_id = viewed.user_id
      AND created.event_type = 'WEEKLY_SUMMARY_CREATED'
      AND created.event_key = viewed.event_key
     LEFT JOIN user_mission_claims claimed
       ON claimed.user_id = viewed.user_id
      AND claimed.mission_code = 'WEEKLY_SUMMARY_REWARD'
      AND claimed.period_key = viewed.event_key
     WHERE viewed.user_id = ?
       AND viewed.event_type = 'WEEKLY_SUMMARY_VIEWED'
       AND claimed.id IS NULL
     ORDER BY viewed.event_key ASC
     LIMIT 1`,
    [userId]
  );

  const [latestRows] = await connection.query(
    `SELECT created.event_key AS summary_id,
            IF(claimed.id IS NULL, 0, 1) AS rewarded
     FROM user_mission_events created
     LEFT JOIN user_mission_claims claimed
       ON claimed.user_id = created.user_id
      AND claimed.mission_code = 'WEEKLY_SUMMARY_REWARD'
      AND claimed.period_key = created.event_key
     WHERE created.user_id = ?
       AND created.event_type = 'WEEKLY_SUMMARY_CREATED'
     ORDER BY created.event_key DESC
     LIMIT 1`,
    [userId]
  );

  return {
    readySummaryId: readyRows[0]?.summary_id ?? null,
    latestRewarded: Boolean(latestRows[0]?.rewarded),
  };
}

async function calculateProgress(connection, userId, definition, period = getPeriod()) {
  let progress = 0;
  let periodKey = definition.category === 'DAILY' ? period.today : period.weekStart;

  switch (definition.code) {
    case 'DAILY_WRITE_MEMO': {
      const [rows] = await connection.query(
        'SELECT COUNT(*) AS count FROM memo WHERE user_id = ? AND date = ?',
        [userId, period.today]
      );
      progress = Number(rows[0].count);
      break;
    }
    case 'DAILY_COMPLETE_DIARY': {
      const [rows] = await connection.query(
        `SELECT COUNT(*) AS count FROM daily_entry
         WHERE user_id = ? AND date = ? AND diary IS NOT NULL AND diary != ''`,
        [userId, period.today]
      );
      progress = Number(rows[0].count);
      break;
    }
    case 'DAILY_VIEW_FRIEND_DIARY': {
      const [rows] = await connection.query(
        `SELECT COUNT(*) AS count FROM user_mission_events
         WHERE user_id = ? AND event_type = 'FRIEND_DIARY_VIEW' AND event_date = ?`,
        [userId, period.today]
      );
      progress = Number(rows[0].count);
      break;
    }
    case 'WEEKLY_WRITE_DIARY': {
      const [rows] = await connection.query(
        `SELECT COUNT(*) AS count FROM daily_entry
         WHERE user_id = ? AND date BETWEEN ? AND ?
           AND diary IS NOT NULL AND diary != ''`,
        [userId, period.weekStart, period.weekEnd]
      );
      progress = Number(rows[0].count);
      break;
    }
    case 'WEEKLY_DIARY_STREAK': {
      const [rows] = await connection.query(
        `SELECT date FROM daily_entry
         WHERE user_id = ? AND date BETWEEN ? AND ?
           AND diary IS NOT NULL AND diary != ''
         ORDER BY date ASC`,
        [userId, period.weekStart, period.weekEnd]
      );
      progress = longestConsecutiveDays(rows.map((row) => row.date));
      break;
    }
    case 'WEEKLY_SUMMARY_REWARD': {
      const summaryState = await getSummaryRewardState(connection, userId);
      progress = summaryState.readySummaryId ? 1 : 0;
      periodKey = summaryState.readySummaryId;
      return {
        progress,
        periodKey,
        latestSummaryRewarded: summaryState.latestRewarded,
      };
    }
    default:
      progress = 0;
  }

  return { progress, periodKey, latestSummaryRewarded: false };
}

async function getMissionState(connection, userId, definition, period = getPeriod()) {
  const calculated = await calculateProgress(connection, userId, definition, period);
  const progress = Math.min(calculated.progress, definition.target_count);

  if (definition.code === 'WEEKLY_SUMMARY_REWARD') {
    return {
      progress,
      periodKey: calculated.periodKey,
      status: calculated.periodKey
        ? 'CLAIMABLE'
        : calculated.latestSummaryRewarded ? 'CLAIMED' : 'IN_PROGRESS',
    };
  }

  const [claimRows] = await connection.query(
    `SELECT id FROM user_mission_claims
     WHERE user_id = ? AND mission_code = ? AND period_key = ?`,
    [userId, definition.code, calculated.periodKey]
  );

  return {
    progress,
    periodKey: calculated.periodKey,
    status: claimRows.length > 0
      ? 'CLAIMED'
      : progress >= definition.target_count ? 'CLAIMABLE' : 'IN_PROGRESS',
  };
}

async function getMissionDashboard(userId) {
  const [definitions] = await pool.query(
    `SELECT code, category, title, description, target_count, reward_points, display_order
     FROM mission_definitions
     WHERE is_active = 1
     ORDER BY FIELD(category, 'DAILY', 'WEEKLY', 'REWARD'), display_order, code`
  );
  const missions = [];
  
  for (const definition of definitions) {
    const state = await getMissionState(pool, userId, definition);
    
    missions.push({
      code: definition.code,
      category: definition.category,
      title: definition.title,
      description: definition.description,
      targetCount: definition.target_count,
      rewardPoints: definition.reward_points,
      displayOrder: definition.display_order,
      currentCount: state.progress,
      status: state.status,
    });
  }

  const balance = await wallet.getBalance(userId);
  const settings = await getEconomySettings(pool);
  const [adRows] = await pool.query(
    'SELECT claim_count FROM daily_ad_rewards WHERE user_id = ? AND reward_date = ?',
    [userId, getPeriod().today]
  );

  return {
    pointBalance: balance,
    missions,
    adReward: {
      watchedCount: Number(adRows[0]?.claim_count ?? 0),
      dailyLimit: settings.adDailyLimit,
      rewardPoints: settings.adRewardPoints,
    },
  };
}

async function claimMission(userId, missionCode) {
  return wallet.withWallet(userId, async (connection, before) => {
    const [definitions] = await connection.query(
      `SELECT code, category, target_count, reward_points
       FROM mission_definitions WHERE code = ? AND is_active = 1`,
      [missionCode]
    );
    if (definitions.length === 0) {
      return { error: 'MISSION_NOT_FOUND' };
    }

    const definition = definitions[0];
    const state = await getMissionState(connection, userId, definition);
    if (state.status === 'CLAIMED') {
      return { error: 'MISSION_ALREADY_CLAIMED' };
    }
    if (state.status !== 'CLAIMABLE' || !state.periodKey) {
      return { error: 'MISSION_NOT_COMPLETED' };
    }

    await connection.query(
      `INSERT INTO user_mission_claims
       (user_id, mission_code, period_key, reward_points)
       VALUES (?, ?, ?, ?)`,
      [userId, missionCode, state.periodKey, definition.reward_points]
    );

    const balance = await wallet.changeBalance(connection, userId, before, {
      amount: Number(definition.reward_points), type: 'MISSION_REWARD',
      reference: `${missionCode}:${state.periodKey}`,
      key: `MISSION:${userId}:${missionCode}:${state.periodKey}`,
    });
    return { rewardPoints: definition.reward_points, pointBalance: balance };
  });
}

async function claimAdReward(userId, rewardId) {
  if (typeof rewardId !== 'string' || !/^[A-Za-z0-9-]{16,80}$/.test(rewardId)) {
    return { error: 'INVALID_AD_REWARD_ID' };
  }

  return wallet.withWallet(userId, async (connection, before) => {
    const period = getPeriod();
    const settings = await getEconomySettings(connection);
    const transactionKey = `AD:${userId}:${rewardId}`;

    const [previousRows] = await connection.query(
      `SELECT change_amount, balance_after
       FROM point_transactions
       WHERE user_id = ? AND idempotency_key = ?`,
      [userId, transactionKey]
    );
    if (previousRows.length > 0) {
      const [countRows] = await connection.query(
        `SELECT claim_count FROM daily_ad_rewards
         WHERE user_id = ? AND reward_date = ?`,
        [userId, period.today]
      );
      return {
        rewardPoints: Number(previousRows[0].change_amount),
        pointBalance: before,
        watchedCount: Number(countRows[0]?.claim_count ?? 0),
        dailyLimit: settings.adDailyLimit,
      };
    }

    await connection.query(
      `INSERT IGNORE INTO daily_ad_rewards (user_id, reward_date, claim_count)
       VALUES (?, ?, 0)`,
      [userId, period.today]
    );
    const [rows] = await connection.query(
      `SELECT claim_count FROM daily_ad_rewards
       WHERE user_id = ? AND reward_date = ? FOR UPDATE`,
      [userId, period.today]
    );
    const currentCount = Number(rows[0].claim_count);
    if (currentCount >= settings.adDailyLimit) {
      return { error: 'AD_DAILY_LIMIT_REACHED' };
    }

    const watchedCount = currentCount + 1;
    await connection.query(
      `UPDATE daily_ad_rewards SET claim_count = ?
       WHERE user_id = ? AND reward_date = ?`,
      [watchedCount, userId, period.today]
    );

    const balance = await wallet.changeBalance(connection, userId, before, {
      amount: settings.adRewardPoints, type: 'AD_REWARD',
      reference: `${period.today}:${watchedCount}`, key: transactionKey,
    });
    return {
      rewardPoints: settings.adRewardPoints,
      pointBalance: balance,
      watchedCount,
      dailyLimit: settings.adDailyLimit,
    };
  });
}

async function recordEvent(userId, eventType, eventKey = null) {
  const period = getPeriod();
  const resolvedEventKey = eventKey || `${eventType}:${period.today}`;
  await pool.query(
    `INSERT IGNORE INTO user_mission_events
     (user_id, event_type, event_key, event_date)
     VALUES (?, ?, ?, ?)`,
    [userId, eventType, resolvedEventKey, period.today]
  );
}

async function recordWeeklySummaryView(userId, summaryStartDate) {
  const [rows] = await pool.query(
    `SELECT ws.startDate
     FROM week_summary ws
     INNER JOIN user_mission_events created
       ON created.user_id = ws.user_id
      AND created.event_type = 'WEEKLY_SUMMARY_CREATED'
      AND created.event_key = ws.startDate
     WHERE ws.user_id = ? AND ws.startDate = ?`,
    [userId, summaryStartDate]
  );
  if (rows.length === 0) return false;
  await recordEvent(userId, 'WEEKLY_SUMMARY_VIEWED', summaryStartDate);
  return true;
}

async function recordFriendDiaryView(userId, friendId, diaryDate) {
  if (!Number.isInteger(friendId) || !/^\d{4}-\d{2}-\d{2}$/.test(diaryDate || '')) {
    return false;
  }
  const [rows] = await pool.query(
    `SELECT de.date
     FROM daily_entry de
     INNER JOIN friendship f
       ON ((f.requester_id = ? AND f.receiver_id = ?)
        OR (f.requester_id = ? AND f.receiver_id = ?))
      AND f.status = 'ACCEPTED'
     WHERE de.user_id = ? AND de.date = ? AND de.is_allowed = 1
     LIMIT 1`,
    [userId, friendId, friendId, userId, friendId, diaryDate]
  );
  if (rows.length === 0) return false;
  await recordEvent(userId, 'FRIEND_DIARY_VIEW', `FRIEND:${friendId}:${diaryDate}`);
  return true;
}

module.exports = {
  initializeWallet,
  getShopCatalog,
  purchaseShopProduct,
  getMissionDashboard,
  claimMission,
  claimAdReward,
  recordEvent,
  recordFriendDiaryView,
  recordWeeklySummaryView,
};
