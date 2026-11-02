const inventory = require('../services/inventoryService');
const { success, fail } = require('../utils/response');

exports.consume = async (req, res) => {
  try {
    const result = await inventory.consume(req.user.userId, req.body);
    if (result.error === 'INSUFFICIENT_ITEMS') return fail(res, result.error, '조합에 필요한 아이템이 부족합니다.', 409);
    if (result.error) return fail(res, result.error, '올바른 아이템 사용 요청이 필요합니다.', 400);
    return success(res, 'ITEMS_CONSUMED', '조합 재료를 사용했습니다.', result);
  } catch (error) {
    console.error('[inventory.consume]', error.message);
    return fail(res, 'CONSUMPTION_UNKNOWN', '조합 결과를 확인하지 못했습니다. 같은 요청으로 다시 확인해 주세요.', 500);
  }
};
