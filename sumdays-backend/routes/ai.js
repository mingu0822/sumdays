const express = require('express');
const router = express.Router();
const multer = require('multer');
const upload = multer({ dest: 'tmp/' }); // 임시 저장 폴더

const mergeController = require('../controllers/ai/mergeController');
const analyzeController = require('../controllers/ai/analyzeController');
const ocrController = require('../controllers/ai/ocrController');
const sttController = require('../controllers/ai/sttController');
const extractController = require('../controllers/ai/extractController');

// api list
router.post('/merge', mergeController.merge); // merge memos
router.post('/mood', mergeController.mood);   // generate pet-perspective mood for completed diary

router.post('/analyze', analyzeController.analyze); // analyze a diary: summary, emotion-score, emoji, feedback
router.post('/summarize-week', analyzeController.summarizeWeek); // summarize week
// router.post('/summarize-month', analyzeController.summarizeMonth); // summarize month

router.post('/ocr/memo', upload.single('image'), ocrController.memo); // image memo to text
router.post('/ocr/diary', upload.array('image'), ocrController.diary); // image diary to text(must extract date)
router.post('/stt/memo', upload.single('audio'), sttController.memo);

router.post('/extract-style', upload.array('images'), extractController.extractStyle); // extract diary style from image/db diaries

module.exports = router;