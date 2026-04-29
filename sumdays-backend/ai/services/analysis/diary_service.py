import os
import json
from typing import Dict, Any
from openai import OpenAI

client = OpenAI(api_key=os.environ["OPENAI_API_KEY"])

class DiaryAnalyzer:
    def __init__(self):
        self.model = os.getenv("GPT_MODEL", "gpt-4.1-nano")

    def analyze(self, diary: str) -> Dict[str, Any]:
        if not diary.strip():
            raise ValueError("Diary text is required.")

        developer_msg = """
You are helping an app service that analyzes a diary entry.
Return a JSON object with exactly these keys:
- "keywords": list of 1–5 Korean strings summarizing the diary
- "emoji": a single emoji representing the diary
- "emotion_score": a float from -1.0 (very negative) to 1.0 (very positive)

Respond in the same language as the user's input.
"""
        response = client.chat.completions.create(
            model=self.model,
            messages=[
                {"role": "developer", "content": developer_msg},
                {"role": "user", "content": diary},
            ],
            response_format={"type": "json_object"},
            temperature=0.5,
        )

        result = json.loads(response.choices[0].message.content)
        return {
            "keywords": result["keywords"],
            "emoji": result["emoji"],
            "emotion_score": float(result["emotion_score"]),
        }
