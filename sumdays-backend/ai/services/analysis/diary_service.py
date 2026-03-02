from typing import Dict, Any
from pydantic import BaseModel, Field
from langchain_openai import ChatOpenAI
from langchain.prompts import PromptTemplate
import os

class DiaryAnalysisResult(BaseModel):
    """ Represents the result of the diary analysis """
    keywords      : list[str]   = Field(description="list of keywords summarizing the diary (min: 1 ~ max: 5)")
    emoji         : str         = Field(description="an emoji representing the diary")
    emotion_score : float       = Field(description="emotional score, criteria: Happiness (-1.0 ~ 1.0)")
    feedback      : str         = Field(description="one-line feedback(Maximum 50 characters)")


class DiaryAnalyzer:
    """ Service that analyzes a diary """
    def __init__(self):
        self.model = ChatOpenAI(
            model=os.getenv("GPT_MODEL", "gpt-4.1-nano"),
            temperature=0.5
        )

    def analyze(self, diary: str) -> Dict[str, Any]:
        """ Analyze diary(text) and return  """
        if not diary.strip():
            raise ValueError("Diary text is required.")
        else: 
            prompt_text = """
            You are helping an app service that analyzes the diary. 
            Here is the user daily diary. Please analyze this to create the following information:

            1. list of keywords summarizing the diary (min: 1 ~ max: 5)
            2. an emoji representing the diary
            3. emotional score, criteria: Happiness (-1.0 ~ 1.0)
            4. one-line feedback
                - Maximum 50 characters.
                - Write in a warm, supportive, and respectful tone (Polite '존댓말').
                - Focus on being a empathetic listener who truly understands the user's day.
                - Mention specific details from the diary to show you're paying attention.
                - (Example 1: "원하던 팀이 승리해서 정말 기분 좋은 저녁이었겠네요!")
                - (Example 2: "오늘 하루는 유독 고단했겠지만, 푹 자고 일어나면 내일은 더 가벼운 마음이길 바랍니다.")

            Return JSON matching the DiaryAnalysis schema. Respond **in the same language** as the user's input.
            ---
            Make diaryAnalysis for diary: {diary}
            """

            prompt = PromptTemplate.from_template(prompt_text)
            llm = self.model.with_structured_output(DiaryAnalysisResult)

            chain = prompt | llm
            result = chain.invoke({"diary": diary})
            return {
                "keywords": result.keywords,
                "emoji": result.emoji,
                "emotion_score": result.emotion_score,
                "feedback": result.feedback
            }

