const missionService = require('../services/missionService');
const googlePlayPointService = require('../services/googlePlayPointService');
const { success, fail } = require('../utils/response');

const playPurchaseMessages = {
  INVALID_PLAY_PURCHASE:        ['올바른 Google Play 구매 정보가 필요합니다.', 400],
  PLAY_PURCHASE_PENDING:        ['결제가 완료되면 포인트가 자동으로 지급됩니다.', 409],
  PLAY_PRODUCT_MISMATCH:        ['선택한 포인트 상품과 구매 정보가 일치하지 않습니다.', 409],
  PLAY_PURCHASE_CONSUMED:       ['이미 처리된 Google Play 구매입니다.', 409],
  PLAY_TOKEN_ALREADY_USED:      ['다른 계정에서 이미 사용한 구매입니다.', 409],
  PLAY_PRODUCT_NOT_FOUND:       ['현재 판매하지 않는 포인트 상품입니다.', 404],
  PLAY_BILLING_NOT_CONFIGURED:  ['결제 서버 설정이 아직 완료되지 않았습니다.', 503],
  PLAY_CONSUME_FAILED:          ['포인트는 지급됐지만 결제 처리를 마무리하지 못했습니다. 잠시 후 다시 열어 주세요.', 503],
};

exports.redeemGooglePlayPoints = async (req, res) => {
  try {
    const result = await googlePlayPointService.redeem(req.user.userId, req.body);
    if (result.error) {
      const [message, status] = playPurchaseMessages[result.error] || ['Google Play 구매를 확인하지 못했습니다.', 502];
      return fail(res, result.error, message, status);
    }
    return success(res, 'PLAY_POINT_PURCHASE_GRANTED', '포인트를 충전했습니다.', result);
  } catch (error) {
    console.error('[redeemGooglePlayPoints]', error.response?.status || error.code || error.message);
    const [message, status] = playPurchaseMessages[error.code] || ['Google Play 구매를 확인하지 못했습니다.', 502];
    return fail(res, error.code || 'PLAY_VERIFY_FAILED', message, status);
  }
};

exports.initializeWallet = async (req, res) => {
  try {
    const result = await missionService.initializeWallet(
      req.user.userId,
      req.body.legacyPointBalance
    );
    if (result.error) {
      return fail(res, result.error, '기존 포인트 값이 올바르지 않습니다.', 400);
    }
    return success(res, 'POINT_WALLET_INITIALIZED', '포인트 지갑을 준비했습니다.', result);
  } catch (error) {
    console.error('[initializeWallet]', error);
    return fail(res, 'POINT_WALLET_INIT_FAILED', '포인트 지갑을 준비하지 못했습니다.', 500);
  }
};

exports.getShopCatalog = async (req, res) => {
  try {
    const result = await missionService.getShopCatalog(req.user.userId);
    return success(res, 'SHOP_CATALOG_FETCHED', '상점 정보를 불러왔습니다.', result);
  } catch (error) {
    console.error('[getShopCatalog]', error);
    return fail(res, 'SHOP_CATALOG_FAILED', '상점 정보를 불러오지 못했습니다.', 500);
  }
};

exports.purchaseShopProduct = async (req, res) => {
  try {
    const result = await missionService.purchaseShopProduct(req.user.userId, req.body);
    if (result.error === 'INSUFFICIENT_POINTS') {
      return fail(res, result.error, '포인트가 부족합니다.', 409);
    }
    if (result.error === 'PRODUCT_NOT_FOUND') {
      return fail(res, result.error, '판매 중인 상품이 아닙니다.', 404);
    }
    if (result.error === 'IDEMPOTENCY_KEY_CONFLICT') {
      return fail(res, result.error, '이미 다른 상품에 사용된 구매 요청입니다.', 400);
    }
    if (result.error) {
      return fail(res, result.error, '구매 요청이 올바르지 않습니다.', 400);
    }
    return success(res, 'SHOP_PRODUCT_PURCHASED', '상품을 구매했습니다.', result);
  } catch (error) {
    console.error('[purchaseShopProduct]', error);
    return fail(res, 'SHOP_PURCHASE_FAILED', '상품을 구매하지 못했습니다.', 500);
  }
};

exports.getDashboard = async (req, res) => {
  try {
    const data = await missionService.getMissionDashboard(req.user.userId);
    return success(res, 'MISSIONS_FETCHED', '미션 정보를 불러왔습니다.', data);
  } catch (error) {
    console.error('[getMissionDashboard]', error);
    return fail(res, 'MISSION_FETCH_FAILED', '미션 정보를 불러오지 못했습니다.', 500);
  }
};

exports.claimMission = async (req, res) => {
  try {
    const result = await missionService.claimMission(req.user.userId, req.params.code);
    if (result.error === 'MISSION_NOT_FOUND') {
      return fail(res, result.error, '존재하지 않는 미션입니다.', 404);
    }
    if (result.error === 'MISSION_ALREADY_CLAIMED') {
      return fail(res, result.error, '이미 수령한 미션 보상입니다.', 409);
    }
    if (result.error === 'MISSION_NOT_COMPLETED') {
      return fail(res, result.error, '아직 완료되지 않은 미션입니다.', 409);
    }
    return success(res, 'MISSION_REWARD_CLAIMED', '미션 보상을 수령했습니다.', result);
  } catch (error) {
    console.error('[claimMission]', error);
    return fail(res, 'MISSION_CLAIM_FAILED', '미션 보상을 수령하지 못했습니다.', 500);
  }
};

exports.claimAdReward = async (req, res) => {
  try {
    const result = await missionService.claimAdReward(
      req.user.userId,
      req.body?.rewardId
    );
    if (result.error === 'INVALID_AD_REWARD_ID') {
      return fail(res, result.error, '올바른 광고 보상 식별자가 필요합니다.', 400);
    }
    if (result.error === 'AD_DAILY_LIMIT_REACHED') {
      return fail(res, result.error, '오늘 받을 수 있는 광고 포인트를 모두 받았습니다.', 409);
    }
    return success(res, 'AD_REWARD_CLAIMED', '광고 포인트를 지급했습니다.', result);
  } catch (error) {
    console.error('[claimAdReward]', error);
    return fail(res, 'AD_REWARD_FAILED', '광고 포인트를 지급하지 못했습니다.', 500);
  }
};

exports.recordFriendDiaryView = async (req, res) => {
  try {
    const recorded = await missionService.recordFriendDiaryView(
      req.user.userId,
      Number(req.body.friendId),
      req.body.diaryDate
    );
    if (!recorded) {
      return fail(res, 'FRIEND_DIARY_NOT_VIEWABLE', '열람 가능한 친구 일기를 확인하지 못했습니다.', 403);
    }
    return success(res, 'FRIEND_DIARY_VIEW_RECORDED', '친구 일기 열람을 기록했습니다.');
  } catch (error) {
    console.error('[recordFriendDiaryView]', error);
    return fail(res, 'MISSION_EVENT_FAILED', '미션 진행을 기록하지 못했습니다.', 500);
  }
};

exports.recordWeeklySummaryView = async (req, res) => {
  const { summaryStartDate } = req.body;
  if (!/^\d{4}-\d{2}-\d{2}$/.test(summaryStartDate || '')) {
    return fail(res, 'INVALID_SUMMARY_DATE', '올바른 주간 통계 시작일이 필요합니다.', 400);
  }
  try {
    const recorded = await missionService.recordWeeklySummaryView(
      req.user.userId,
      summaryStartDate
    );
    if (!recorded) {
      return fail(res, 'NEW_SUMMARY_NOT_FOUND', '새로 획득한 주간 통계가 아닙니다.', 409);
    }
    return success(res, 'WEEKLY_SUMMARY_VIEW_RECORDED', '새 주간 통계 확인을 기록했습니다.');
  } catch (error) {
    console.error('[recordWeeklySummaryView]', error);
    return fail(res, 'MISSION_EVENT_FAILED', '미션 진행을 기록하지 못했습니다.', 500);
  }
};
