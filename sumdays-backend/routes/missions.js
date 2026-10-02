const express = require('express');

const missionController = require('../controllers/missionController');
const mergeMarbleController = require('../controllers/mergeMarbleController');

const router = express.Router();

// api lists
router.get('/', missionController.getDashboard);    // 미션창 정보 불러오기

router.get('/merge-marbles', mergeMarbleController.getState);                          // 여우구슬 구매 페이지 정보 불러오기
router.post('/merge-marbles/tickets/purchase', mergeMarbleController.purchaseTicket);  // 여우구슬(횟수권) 구매
router.post('/merge-marbles/pass/redeem', mergeMarbleController.redeemPass);           // 여우구슬 패스권 구매

router.post('/wallet/initialize', missionController.initializeWallet);              // 지갑 정보 초기화
router.post('/play/points/redeem', missionController.redeemGooglePlayPoints);       // 포인트 구매

router.get('/shop', missionController.getShopCatalog);                                  // 상점 정보 불러오기
router.post('/shop/purchase', missionController.purchaseShopProduct);                   // 상점 상품 구매
router.post('/shop/consume', require('../controllers/inventoryController').consume);    // 상점 아이템 소비

router.post('/events/friend-diary-view', missionController.recordFriendDiaryView);
router.post('/events/weekly-summary-view', missionController.recordWeeklySummaryView);

router.post('/ad-reward', missionController.claimAdReward);     // 광고 시청 보상 수령
router.post('/:code/claim', missionController.claimMission);    // 미션 달성 보상 수령

module.exports = router;
