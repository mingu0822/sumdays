const auth = require('../services/googlePlayRtdnAuth');
const rtdn = require('../services/googlePlayRtdnService');

exports.receive = async (req, res) => {
  try {
    const valid = await auth.verifyPushToken(req.headers.authorization);
    if (!valid) return res.sendStatus(401);
    await rtdn.process(req.body?.message);
    return res.sendStatus(204);
  } catch (error) {
    console.error('[googlePlay.rtdn]', error.code || error.message);
    // Pub/Sub는 2xx가 아니면 재시도하므로 일시 오류를 잃지 않는다.
    return res.sendStatus(error.code === 'RTDN_NOT_CONFIGURED' ? 503 : 500);
  }
};
