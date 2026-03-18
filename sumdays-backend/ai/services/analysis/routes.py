from flask import Blueprint, request, jsonify
from .diary_service import DiaryAnalyzer
from .summary_service import SummaryService

analyzer = DiaryAnalyzer()
summary_service = SummaryService()
analysis_bp = Blueprint("analysis", __name__, url_prefix="/analysis")

@analysis_bp.route("/diary", methods=["POST"])
def analyze_diary():
    """ POST http://localhost:5001/analysis/diary
    \{
        "diary": "오늘은 친구들과 카페에 가서 이야기를 많이 나눴다.",
        "persona": {
            "id": 1,
            "system_prompt": "너는 여우다",
            ...
        }
    \}
    """
    try:
        data = request.get_json()
        diary = data.get("diary", "")
        persona = data.get("persona")
        if not persona:
            return jsonify({"error": "Persona information is required for feedback."}), 400

        result = analyzer.analyze(diary)
        feedback = analyzer.generate_feedback(diary, result, persona)

        response = {
            "entry_date": data.get("entry_date"),
            "user_id": data.get("user_id"),
            "diary": diary,
            "icon": result["emoji"],
            "ai_comment": feedback,
            "analysis": {
                "keywords": result["keywords"],
                "emotion_score": result["emotion_score"]
            }
        }
        return jsonify(response), 200

    except Exception as e:
        return jsonify({"error": str(e)}), 400
    
@analysis_bp.route("/week", methods=["POST"])
def analyze_week():
    """POST http://localhost:5001/analysis/week
    testcase/summarize_week_{number} 참고
    """
    try:
        data = request.get_json()
        diaries = data.get("diaries", "")
        result = summary_service.summarize_week(diaries)

        response = {
            "summary": {
                "title": result["title"],
                "overview": result["overview"],
                "emerging_topics": result["emerging_topics"]
            },
            "emotion_analysis": {
                "trend": result["trend"],
                "dominant_emoji": result["dominant_emoji"],
                "distribution": { "positive": 0, "neutral": 0, "negative": 0 },
                "emotion_score": 0
            },
            "highlights": result["highlights"],
            "insights": {
                "emotion_cycle": result["emotion_cycle"],
                "advice": result["advice"]
            }
        }
        return jsonify(response), 200

    except Exception as e:
        return jsonify({"error": str(e)}), 400

# @analysis_bp.route("/month", methods=["POST"])
# def analyze_month():
#     """POST http://localhost:5001/analysis/month
#     testcase/summarize_month_{number} 참고
#     """
#     try:
#         data = request.get_json()
#         weeks = data.get("weeks", "")
#         result = summary_service.summarize_month(weeks)

#         response = {
#             "summary": {
#                 "title": result["title"],
#                 "overview": result["overview"],
#                 "dominant_emoji": result["dominant_emoji"],
#                 "emerging_topics": result["emerging_topics"],
#                 "emotion_statistics": { "positive": 0, "neutral": 0, "negative": 0 },
#                 "emotion_score": 0
#             },
#             "insights": {
#                 "emotion_cycle": result["emotion_cycle"],
#                 "advice": result["advice"]
#             },
#             "weeks": data.get("weeks")
#         }
#         return jsonify(response), 200

#     except Exception as e:
#         return jsonify({"error": str(e)}), 400