const axios = require("axios");
const PYTHON_SERVER_URL = process.env.PYTHON_AI_URL;
const marble = require('../../services/mergeMarbleService');
const { sendError } = require('../mergeMarbleController');
const { StringDecoder } = require('node:string_decoder');

const mergeController = {
    // 스타일 예시는 사용자 메모를 받지 않아 합치기 제한 우회 통로가 되지 않는다.
    preview: async (req, res) => {
        const { style_prompt, style_examples } = req.body || {};
        if (!style_prompt || !Array.isArray(style_examples)) {
            return res.status(400).json({ success: false, message: '스타일 정보가 필요합니다.' });
        }
        try {
            const response = await axios.post(`${PYTHON_SERVER_URL}/merge/`, {
                memos: [
                    { content: '아침에 늦잠을 자서 밥을 굶었다.', order: 1 },
                    { content: '점심에는 라면을 먹었다. 맛있었다.', order: 2 },
                    { content: '저녁에는 삼겹살을 구워먹었다. 즐거운 하루였다.', order: 3 },
                ],
                style_prompt, style_examples,
            }, { timeout: 120000 });
            if (!response.data?.diary?.trim()) throw new Error('Empty preview');
            return res.json({ success: true, result: response.data });
        } catch (error) {
            console.error('[merge preview]', error.message);
            return res.status(502).json({ success: false, message: '예시 일기를 생성하지 못했습니다.' });
        }
    },
    // Controller method for a single merge request (Example: POST /api/ai/merge)
    /* POST http://localhost:3000/api/ai/merge
    payload.json 파일 참고
    $ curl --no-buffer -X POST http://localhost:3000/api/ai/merge -H "Content-Type: application/json" -d @payload.json
    이걸로 버퍼링 테스트해볼 수 있음!(windows cmd 기준, sumdays-backend 위치에서)
     */
    merge: async (req, res) => {
        const userId = req.user?.userId;
        if (!userId) return res.status(401).json({ success: false, message: '로그인이 필요합니다.' });
        const requestId = req.body?.request_id;
        let reserved = false;
        const abort = new AbortController();
        const timeout = setTimeout(() => abort.abort(), 120000);
        try {
            const { memos, style_prompt, style_examples, end_flag, length_level } = req.body || {};

            if (!memos || !Array.isArray(memos) || memos.length < 2) {
                return res.status(400).json({
                    success: false,
                    message: "At least two memos are required for merging.",
                });
            }

            if (!style_prompt || !Array.isArray(style_examples)) {
                return res.status(400).json({
                    success: false,
                    message: "style_prompt and style_examples are required.",
                });
            }

            if (memos.some(memo => !memo || typeof memo.content !== 'string' || !memo.content.trim() || !Number.isFinite(memo.order))) {
                return res.status(400).json({ success: false, code: 'INVALID_REQUEST', message: '메모 내용과 순서를 확인해 주세요.' });
            }
            const sortedMemos = [...memos].sort((a, b) => a.order - b.order);
            const payload = { memos: sortedMemos, style_prompt, style_examples, end_flag: Boolean(end_flag), length_level };
            const reservation = await marble.reserve(userId, requestId, payload);
            if (reservation.error) return sendError(res, reservation.error);
            if (reservation.duplicate) {
                return end_flag ? res.json({ success: true, result: reservation.result })
                    : res.type('text/plain; charset=utf-8').send(reservation.result);
            }
            reserved = true;
            // 응답 연결이 끊겨도 결과를 끝까지 저장: 동일 ID로 AI 재호출 없이 복구한다.
                      
            if (!end_flag) {
                const response = await axios.post(
                    `${PYTHON_SERVER_URL}/merge/paragraph`, 
                    { memos: sortedMemos, style_prompt, style_examples, length_level },
                    { responseType: 'stream', signal: abort.signal, timeout: 120000 }
                );

                if (!response.data || !response.data.pipe) {
                    throw new Error('Invalid stream from AI server');
                }
                
                res.setHeader("Content-Type", "text/plain; charset=utf-8");
                const decoder = new StringDecoder('utf8');
                let text = '';
                for await (const chunk of response.data) {
                    const part = decoder.write(Buffer.from(chunk));
                    text += part;
                    if (Buffer.byteLength(text, 'utf8') > 1024 * 1024) throw new Error('Merge result too large');
                    if (!res.destroyed) res.write(part);
                }
                const tail = decoder.end();
                text += tail;
                if (!text.trim()) throw new Error('Empty merge result');
                if (!await marble.finish(userId, requestId, text)) throw new Error('Merge reservation expired');
                reserved = false;
                if (!res.destroyed) res.end(tail);
                return;
            } else {
                const response = await axios.post(
                    `${PYTHON_SERVER_URL}/merge/`, 
                    { memos: sortedMemos, style_prompt, style_examples },
                    { signal: abort.signal, timeout: 120000 }
                );

                if (!response.data || typeof response.data.diary !== 'string' || !response.data.diary.trim()) {
                    throw new Error('Invalid diary from AI server');
                }
                if (!await marble.finish(userId, requestId, response.data)) throw new Error('Merge reservation expired');
                reserved = false;
                
                return res.status(200).json({
                    success: true,
                    result: response.data,
                });
            }
            
        } catch (err) {
            console.error("[mergeController.merge] Error:", err.message);
            if (reserved) {
                try { await marble.finish(userId, requestId, null, 'MERGE_FAILED'); }
                catch (refundError) { console.error('[merge refund]', refundError.message); }
            }
            if (res.headersSent || res.destroyed) return res.destroy();
            return sendError(res, 'MERGE_FAILED');
        } finally {
            clearTimeout(timeout);
        }
    },
    mood: async (req, res) => {
        try {
            const { diary, style_prompt, style_examples } = req.body;

            if (!diary) {
                return res.status(400).json({ success: false, message: "diary is required." });
            }
            if (!style_prompt || !style_examples) {
                return res.status(400).json({ success: false, message: "style_prompt and style_examples are required." });
            }

            const response = await axios.post(
                `${PYTHON_SERVER_URL}/merge/mood`,
                { diary, style_prompt, style_examples }
            );

            return res.status(200).json({ success: true, result: response.data });
        } catch (err) {
            console.error("[mergeController.mood] Error:", err.message);
            return res.status(400).json({ success: false, error: err.message || err });
        }
    },
};

module.exports = mergeController;
