const express = require('express');
const { pool } = require('../db/db');
const { createStickerService, StickerError } = require('../services/stickerService');

function createStickerRouter(service = createStickerService(pool)) {
  const router = express.Router();
  const handle = action => async (req, res) => {
    try {
      const data = await action(req, Number(req.params.ownerId), req.params.date);
      res.json({ success: true, data });
    } catch (error) {
      if (error instanceof StickerError) {
        return res.status(error.status).json({ success: false, code: error.code, message: error.message });
      }
      console.error('[stickers]', error.message);
      res.status(500).json({ success: false, code: 'STICKER_SERVER', message: '스티커를 저장하지 못했어요. 다시 시도해 주세요.' });
    }
  };
  router.get('/:ownerId/:date', handle((req, owner, date) => service.list(req.user.userId, owner, date)));
  router.put('/:ownerId/:date/:id', handle((req, owner, date) => service.place(req.user.userId, owner, date, req.params.id, req.body || {})));
  router.patch('/:ownerId/:date/:id', handle((req, owner, date) => service.update(req.user.userId, owner, date, req.params.id, req.body || {})));
  router.delete('/:ownerId/:date/:id', handle((req, owner, date) => service.remove(req.user.userId, owner, date, req.params.id)));
  return router;
}

module.exports = createStickerRouter();
module.exports.createStickerRouter = createStickerRouter;
