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

class FeedbackResult(BaseModel):
    feedback      : str         = Field(description="one-line feedback(Maximum 100 characters)")


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
                "emotion_score": result.emotion_score
            }

    def generate_feedback(self, diary: str, analysis_results: Dict[str, Any], persona: Dict[str, Any]) -> str:
        prompt_text = """
        {system_prompt}

        ### Context
        - User's Diary: {diary}
        - Emotion Score: {emotion_score}
        - Main Keywords: {keywords}

        ### Instructions
        1. 위 제공된 Context(일기 내용, 감정 점수, 키워드)를 충분히 반영하여 답변하세요.
        2. 당신의 페르소나 지침({system_prompt})을 엄격히 준수하세요.
        3. 단순한 위로보다는 일기 내용의 구체적인 부분을 언급하며 대화하듯 작성하세요.
        4. 응답은 반드시 사용자의 언어로 작성하세요.
        """

        prompt = PromptTemplate.from_template(prompt_text)
        llm = self.model.with_structured_output(FeedbackResult)

        chain = prompt | llm
        result = chain.invoke({
            "system_prompt": persona["system_prompt"],
            "diary": diary,
            "emotion_score": analysis_results["emotion_score"],
            "keywords": ", ".join(analysis_results["keywords"])
        })

        return result.feedback