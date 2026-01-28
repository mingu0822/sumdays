from flask import Blueprint, request, jsonify, Response
from .merge_service import merge_stream, merge_rerank, merge_paragraph_stream
from ..analysis.diary_service import DiaryAnalyzer

analysis_service = DiaryAnalyzer()
merge_bp = Blueprint("merge", __name__, url_prefix="/merge")

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

        response = {
            "entry_date": data.get("entry_date"),
            "user_id": data.get("user_id"),
            "diary": diary,
            "icon": result["emoji"],
            "ai_comment": result["feedback"],
            "analysis": {
                "keywords": result["keywords"],
                "emotion_score": result["emotion_score"]
            }
        }

        return jsonify(response), 200

    except Exception as e:
        return jsonify({"error": str(e)}), 401
    
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
    try:
        data = request.get_json()
        memos =  [m["content"] for m in sorted(data["memos"], key=lambda x: x["order"])]
        length_level = data["length_level"]
        style_prompt = data["style_prompt"]
        style_examples = data["style_examples"]

        def generate():
            for chunk in merge_paragraph_stream(memos, style_prompt, style_examples, length_level=length_level):
                delta = chunk.choices[0].delta
                content = delta.content or ""
                if not content:
                    continue
                yield content

        return Response(generate(), mimetype="text/plain; charset=utf-8")

    except Exception as e:
        return jsonify({"error": str(e)}), 402
