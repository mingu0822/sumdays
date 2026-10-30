from typing import Dict, Any
from pydantic import BaseModel, Field
from langchain_openai import ChatOpenAI
from langchain.prompts import PromptTemplate
import os

MIN_DIARY_NUM = 3
MIN_WEEKSUMMARY_NUM = 2

class Highlight(BaseModel):
    """ Represents a significant day within the week. """
    date        : str   = Field(description="The date (in YYYY-MM-DD format) of a particularly meaningful or emotionally notable day within the week.")
    summary     : str   = Field(description="A concise one-line summary of the day")

class SummaryWeekResult(BaseModel):
    """ Represents the result of the weekly summary """
    title           : str               = Field(description= "A concise title summarizing the week's emotional or thematic essence.")
    overview        : str               = Field(description= "A paragraph of 2-3 sentences summarizing the emotional and behavioral trend of the week, capturing how the user's mood evolved.")
    emerging_topics : list[str]         = Field(description= "A list of 2–5 recurring or emerging themes or keywords observed throughout the week.")
    trend           : str               = Field(description= "Sentiment score trend (increasing, stable, decreasing)")
    dominant_emoji  : str               = Field(description= "An emoji that best represents the overall emotional tone of the week (e.g., 😌, 😊, 😔).")
    highlights      : list[Highlight]   = Field(description= "A list (1–3 items) of significant daily highlights that capture key emotional or thematic moments of the week.")
    emotion_cycle   : str               = Field(description= "A short description summarizing the emotional flow across the week (e.g., 'Early stress → Mid adaptation → Late recovery'). Maximum 3 stages.")
    advice          : str               = Field(description= "Personalized advice or reflection derived from the emotional trends of the week, focusing on well-being or growth.")

class SummaryMonthResult(BaseModel):
    """ Represents the result of the monthly summary """
    title           : str       = Field(description= "A concise title summarizing the month's emotional or thematic essence.")
    overview        : str       = Field(description= "A paragraph of 2-5 sentences summarizing the emotional and behavioral trend of the month, capturing how the user's mood evolved.")
    dominant_emoji  : str       = Field(description= "An emoji that best represents the overall emotional tone of the month (e.g., 😌, 😊, 😔).")
    emerging_topics : list[str] = Field(description= "A list of 2–5 recurring or emerging themes or keywords observed throughout the month.")
    emotion_cycle   : str       = Field(description= "A short description summarizing the emotional flow across the month (e.g., 'Early stress → Mid adaptation → Late recovery'). Maximum 5 stages.")
    advice          : str       = Field(description= "Personalized advice or reflection derived from the emotional trends of the month, focusing on well-being or growth.")

class SummaryService:
    """ Service that summarize diaries weekly/monthly """
    def __init__(self):
        """ Initialize the summary service. """
        self.model = ChatOpenAI(
            model=os.getenv("GPT_MODEL", "gpt-4.1-nano"),
        )

    def summarize_week(self, diaries: list[Dict]) -> Dict[str, Any]:
        """ Summarize diaries of the week """
        if len(diaries) < MIN_DIARY_NUM:
            raise ValueError("At least 3 diaries are required for weekly summary.")
        else: 
            promt_text = """
            당신은 감정 일기 분석에 특화된 AI 요약 도우미입니다.

            사용자가 일주일 동안 작성한 다음 일기를 분석합니다.
            감정적 및 주제별 트렌드를 요약하고, 새롭게 떠오르는 주제를 식별합니다.
            1~3개의 주요 하이라이트를 추출합니다(의미 있는 날).

            'SummaryWeekResult' 스키마와 정확히 일치하는 JSON 객체만 반환합니다.
            추가 텍스트나 설명은 포함하지 마세요. 사용자의 입력과 동일한 언어로 응답하세요. 
            한국어의 경우 '-다'체로 응답하세요. advice는 친한 사람이 조언이나 응원해주듯이 말하고, 확신에 차서 대답하기보다는 조금 부드럽게 대답하세요.
            
            ---
            일기:
            {diaries}
            """

            prompt = PromptTemplate.from_template(promt_text)
            llm = self.model.with_structured_output(SummaryWeekResult)

            chain = prompt | llm
            result = chain.invoke({"diaries": diaries})
            return result.model_dump()
        
    # def summarize_month(self, weeks: list[Dict]) -> Dict[str, Any]:
    #     """ Summarize diaries of the month """
    #     if len(weeks) < MIN_WEEKSUMMARY_NUM:
    #         raise ValueError("At least 2 week-summary are required for monthly summary.")
    #     else: 
    #         promt_text = """
    #         You are an AI summarization assistant specialized in emotional pattern tracking.

    #         Based on the provided week summaries, generate a single monthly overview that
    #         captures the emotional and behavioral trends of the entire month.
    #         Extract emerging topics, summarize emotional cycles, and offer reflective advice.

    #         Return ONLY a JSON object strictly matching the `SummaryMonthResult` schema.
    #         Do not include extra text or explanations. Respond **in the same language** as the user's input.
            
    #         ---
    #         Week summaries:
    #         {weeks}
    #         """

    #         prompt = PromptTemplate.from_template(promt_text)
    #         llm = self.model.with_structured_output(SummaryMonthResult)

    #         chain = prompt | llm
    #         result = chain.invoke({"weeks": weeks})
    #         return result.model_dump()
