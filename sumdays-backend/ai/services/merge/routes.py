from flask import Blueprint, request, jsonify, Response, current_app
from openai import OpenAIError
from .merge_service import merge_stream, merge_paragraph_stream, generate_mood
from ..analysis.diary_service import DiaryAnalyzer

analysis_service = DiaryAnalyzer()
merge_bp = Blueprint("merge", __name__, url_prefix="/merge")


def generation_error():
    current_app.logger.exception("AI merge generation failed")
    return jsonify({
        "error": "AI 응답을 생성하지 못했습니다. 잠시 후 다시 시도해 주세요.",
        "code": "AI_GENERATION_FAILED",
    }), 502


@merge_bp.route("/", methods=["POST"])
def merge_memo():
    """ POST http://localhost:5001/merge/ 
    \{
        "memos": [
            {"content": "아침에 늦잠 자서 학교에 지각했냥. 수업 가서 듣는데, 저번 내용 복습 안 했더니 머리가 멍멍해졌다냥. 공부를 좀 더 열심히 할 거다냥! 집에 와서 생각하니까, 역시 꾸준히 해야겠다냥. 오늘은 좀 귀찮긴 했지만, 내일은 좀 더 잘할 거다냥!", "order": 1},
            {"content": "집에 와서 소개원실 실습과제나 했는데, 아침에 다짐한 덕분에 일찍 시작하니까 마음이 무척 편안하더라냥. 그런데 오늘은 또 닭다리 과자를 처음 먹어봤다냥! 와, 존맛이더라니까! 고양이처럼 새콤달콤한 냄새에 먼저 코를 킁킁 거렸는데, 한입 넣자마자 바로 눈이 반짝였어. 집사도 이렇게 맛있는 건 또 처음 본다며 웃던데, 나도 모르게 냥냥거릴 정도로 빠져들었지 뭐냐냥. 오늘 하루는 진짜 완전 꿀맛이었냥!", "order": 2},
        ],
        "style_prompt": {...},
        "style_examples": {"adsf", "asdfaf", "FKSJD"}
    \}
    """
    try:
        data = request.get_json()
        memos =  [m["content"] for m in sorted(data["memos"], key=lambda x: x["order"])]
        style_prompt = data["style_prompt"]
        style_examples = data["style_examples"]

        diary = ""

        for chunk in merge_stream(memos, style_prompt, style_examples):
            delta = chunk.choices[0].delta
            content = delta.content or ""
            if not content:
                continue
            diary += content

        result = analysis_service.analyze(diary)
        mood = generate_mood(diary, style_prompt, style_examples)

        response = {
            "entry_date": data.get("entry_date"),
            "user_id": data.get("user_id"),
            "diary": diary,
            "icon": result["emoji"],
            "mood": mood,
            "analysis": {
                "keywords": result["keywords"],
                "emotion_score": result["emotion_score"]
            }
        }

        return jsonify(response), 200

    except OpenAIError:
        return generation_error()
    except Exception as e:
        return jsonify({"error": str(e)}), 400
    
@merge_bp.route("/mood", methods=["POST"])
def generate_mood_route():
    """ POST http://localhost:5001/merge/mood
    \{
        "diary": "완성된 일기 텍스트",
        "style_prompt": {...},
        "style_examples": ["...", "..."]
    \}
    """
    try:
        data = request.get_json()
        diary = data["diary"]
        style_prompt = data["style_prompt"]
        style_examples = data["style_examples"]

        mood = generate_mood(diary, style_prompt, style_examples)
        return jsonify({"mood": mood}), 200

    except OpenAIError:
        return generation_error()
    except Exception as e:
        return jsonify({"error": str(e)}), 400

@merge_bp.route("/paragraph", methods=["POST"])
def merge_memo_paragraph():
    """ POST http://localhost:5001/merge/paragraph
    \{
        "memos": [
            {"content": "아침에 늦잠을 잤다. 학교에 지각했다.", "order": 1},
            {"content": "수업을 들었는데 저번 내용을 복습을 안했더니 이해가 안 갔다. 앞으로는 열심히 공부할거야.", "order": 2}
        ],
        "length_level": 0-2
        "style_prompt": {...},
        "style_examples": {"adsf", "asdfaf", "FKSJD"}
    \}
    """
    data = request.get_json(silent=True)
    try:
        if not isinstance(data, dict):
            raise ValueError("JSON 요청 본문이 필요합니다.")
        memo_entries = data["memos"]
        if not isinstance(memo_entries, list) or len(memo_entries) < 2:
            raise ValueError("두 개 이상의 메모가 필요합니다.")
        for memo in memo_entries:
            if (not isinstance(memo, dict)
                    or not isinstance(memo.get("content"), str)
                    or not memo["content"].strip()
                    or type(memo.get("order")) is not int):
                raise ValueError("메모 내용과 순서를 확인해 주세요.")
        memos = [m["content"] for m in sorted(memo_entries, key=lambda x: x["order"])]
        length_level = data["length_level"]
        if type(length_level) is not int or length_level not in (0, 1, 2):
            raise ValueError("length_level은 0, 1, 2 중 하나여야 합니다.")
        style_prompt = data["style_prompt"]
        style_examples = data["style_examples"]
    except (KeyError, TypeError, ValueError) as e:
        return jsonify({"error": str(e), "code": "INVALID_REQUEST"}), 400

    def generate_text():
        chunks = merge_paragraph_stream(
            memos, style_prompt, style_examples, length_level=length_level,
        )
        try:
            for chunk in chunks:
                content = chunk.choices[0].delta.content or ""
                if content:
                    yield content
        finally:
            chunks.close()

    text_stream = generate_text()
    try:
        # Start the upstream request before HTTP headers are committed. A model
        # error must return 502 instead of an empty successful streaming response.
        first_text = next(text_stream, None)
        if first_text is None:
            raise RuntimeError("AI returned an empty paragraph")
    except Exception:
        text_stream.close()
        return generation_error()

    def generate():
        try:
            yield first_text
            # Later failures must propagate and abort the connection so the Node
            # server refunds the request rather than saving a partial paragraph.
            yield from text_stream
        finally:
            text_stream.close()

    response = Response(generate(), content_type="text/plain; charset=utf-8")
    response.call_on_close(text_stream.close)
    return response
