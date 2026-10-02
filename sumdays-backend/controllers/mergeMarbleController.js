const marble = require('../services/mergeMarbleService');
const mergePass = require('../services/mergePassService');

const messages = {
  INVALID_REQUEST               : '올바른 합치기 요청이 필요합니다.',
  MERGE_TICKET_NOT_FOUND        : '판매 중인 횟수권이 아닙니다.',
  MERGE_TICKET_PRICE_CHANGED    : '횟수권 가격이 변경되었습니다. 다시 확인해 주세요.',
  INSUFFICIENT_POINTS           : '포인트가 부족합니다.',
  MERGE_PASS_ACTIVE             : '무제한 패스 이용 중에는 횟수권을 구매할 수 없습니다.',
  INVALID_MERGE_PASS_PURCHASE   : '올바른 무제한 패스 구매 정보가 필요합니다.',
  MERGE_PASS_PURCHASE_PENDING   : '결제가 완료되면 무제한 패스가 자동으로 활성화됩니다.',
  MERGE_PASS_PRODUCT_MISMATCH   : '선택한 패스와 Google Play 구매 정보가 일치하지 않습니다.',
  MERGE_PASS_PRODUCT_NOT_FOUND  : '현재 판매하지 않는 무제한 패스입니다.',
  MERGE_PASS_TOKEN_ALREADY_USED : '다른 계정에서 이미 사용한 패스 구매입니다.',
  MERGE_PASS_ALREADY_ACTIVE     : '이미 무제한 패스를 이용 중입니다.',
  IDEMPOTENCY_KEY_CONFLICT      : '다른 내용에 사용된 요청입니다.',
  MERGE_LIMIT_REACHED           : '무료 횟수와 보유 횟수권을 모두 사용했어요. 이용권 상점에서 횟수권을 구매할 수 있어요.',
  MERGE_IN_PROGRESS             : '이전 합치기를 처리 중이에요. 잠시 후 다시 시도해 주세요.',
  MERGE_FAILED                  : '합치기에 실패했습니다. 사용 횟수는 반환됐어요.',
  MERGE_EXPIRED                 : '합치기 시간이 초과되어 사용 횟수를 반환했어요. 다시 시도해 주세요.',
};

function sendError(res, error) {
  const status = ['INVALID_REQUEST', 'IDEMPOTENCY_KEY_CONFLICT', 'INVALID_MERGE_PASS_PURCHASE'].includes(error) ? 400
    : error === 'MERGE_FAILED' ? 502 : 409;
  return res.status(status).json({ success: false, code: error, message: messages[error] || '합치기 요청을 처리하지 못했습니다.' });
}

exports.sendError = sendError;
exports.getState = async (req, res) => {
  try {
    res.json({ success: true, data: await marble.getState(req.user.userId) });
  } catch (error) {
    console.error('[mergeMarble.getState]', error.message);
    res.status(500).json({ success: false, code: 'MERGE_STATE_FAILED', message: '합치기 횟수를 불러오지 못했습니다.' });
  }
};
exports.purchaseTicket = async (req, res) => {
  try {
    const result = await marble.purchaseTicket(req.user.userId, req.body);
    if (result.error) return sendError(res, result.error);
    res.json({ success: true, data: result });
  } catch (error) {
    console.error('[mergeMarble.purchaseTicket]', error.message);
    res.status(500).json({ success: false, code: 'MERGE_TICKET_PURCHASE_FAILED', message: '횟수권 구매 결과를 확인하지 못했습니다. 다시 확인해 주세요.' });
  }
};
exports.redeemPass = async (req, res) => {
  try {
    const result = await mergePass.redeem(req.user.userId, req.body);
    if (result.error) return sendError(res, result.error);
    res.json({ success: true, data: result });
  } catch (error) {
    console.error('[mergePass.redeem]', error.response?.status || error.code || error.message);
    const status = error.code === 'PLAY_BILLING_NOT_CONFIGURED' ? 503 : 502;
    res.status(status).json({
      success: false,
      code: error.code || 'MERGE_PASS_REDEEM_FAILED',
      message: error.code === 'PLAY_BILLING_NOT_CONFIGURED'
        ? '결제 서버 설정이 아직 완료되지 않았습니다.'
        : '패스 구매를 확인하지 못했습니다. 잠시 후 다시 열어 주세요.',
    });
  }
};
