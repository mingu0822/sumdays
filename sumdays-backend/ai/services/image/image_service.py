from langchain_openai import ChatOpenAI
from langchain.schema import HumanMessage
from pydantic import BaseModel, Field
from langchain_openai import ChatOpenAI
from langchain.prompts import PromptTemplate
from google.cloud import vision
import os
import base64

os.environ["GOOGLE_APPLICATION_CREDENTIALS"] = os.getenv("GOOGLE_APPLICATION_CREDENTIALS")

class RefinedDiaryResult(BaseModel):
    """ Represents the result of the diary analysis """
    date            : str   = Field(description="Date the diary was written")
    refined_text    : str   = Field(description="A refined diary with unnecessary OCR extracted text removed and paragraphs and sentences divided according to the content.")


class ImageService:
    """ Service for image memos or diaries """
    def __init__(self):
        self.image_model = ChatOpenAI(
            model=os.getenv("IMAGE_MODEL", "gpt-4o-mini"),
            temperature=0.3
        )
        self.refine_model = ChatOpenAI(
            model=os.getenv("GPT_MODEL", "gpt-6-luna"),
        )
        self.ocr_client = vision.ImageAnnotatorClient()

    def analyze(self, image_file, analysis_type: str):
        """ Extract image descriptions or text depending on the analysis type """
        if analysis_type == "extract":
            text = self.extract_text_from_image(image_files=[image_file])[0]
            return {
                "type": analysis_type,
                "text": text.text.strip()
            }
        elif analysis_type == "describe":
            result_text = self.describe_image(image_file)
            return {
                "type": analysis_type,
                "text": result_text
            }
        else:
            raise ValueError(f"Unsupported analysis type: {analysis_type}")

    def detect_texts_from_diaries(self, image_files): 
        """ OCR multiple image files and extract date/refine extracted text """
        result = []
        extracted_texts = self.extract_text_from_image(image_files)
        
        for idx, text_obj in enumerate(extracted_texts):
            extracted_text = text_obj.text.strip()

            prompt_text = """
            You are refining OCR text from a scanned diary page.
            You will receive OCR-extracted diary text that may include formatting noise or irrelevant template sections.

            Task:
            1. Detect the diary's date written in the text (e.g., "2025년 10월 25일").
                - If a date-like expression is present, extract it and normalize it into the format "YYYY-MM-DD".
                - If some parts of the date are missing:
                    * replace unknown year/month/day with "X".
                    * e.g., "10월 6일" → "XXXX-10-06", "2023년 5월" → "2023-05-XX"
            2. Remove irrelevant template sections entirely — do not leave placeholders or explanations.
            Examples of such sections:
            - "오늘 할 일", "내일 할 일", "쓰기 연습", checklists, teacher comments, titles, or prompts.
            3. Keep only the main diary content written by the user.
            4. Preserve original meaning and tone. Do NOT add comments like "removed" or "excluded".
            5. Return JSON following the RefinedDiaryResult schema.

            Respond **in the same language** as the diary text.
            ---
            Make RefinedDiaryResult for diary: {ocr_extracted_diary}
            """

            prompt = PromptTemplate.from_template(prompt_text)
            llm = self.refine_model.with_structured_output(RefinedDiaryResult)

            chain = prompt | llm
            refined_result = chain.invoke({"ocr_extracted_diary": extracted_text})
            result.append(refined_result.model_dump())

        return {
            "result": result
        }        

    def extract_text_from_image(self, image_files):
        extracted_texts = [] 
        for image_file in image_files:
            image_bytes = image_file.read()
            image = vision.Image(content=image_bytes)

            response = self.ocr_client.document_text_detection(image=image)

            if response.error.message:
                raise Exception(f"Vision API Error: {response.error.message}")
            
            texts = response.full_text_annotation
            extracted_texts.append(texts)

        return extracted_texts
    
    def describe_image(self, image_file):
        prompt = """
        You are helping an app that analyzes a single diary image input.
        Your task is to describe what kind of moment or situation the image represents,
        as if it were a short diary note.

        Guidelines:
        - Write in a sinle sentence.
        - Focus on the *context or emotion* implied by the image rather than listing visible objects.
        (e.g., “listening to music” instead of “an audio player on a desk”)
        - Avoid excessive imagination or details not clearly implied by the image.
        """

        image_bytes = image_file.read()
        image_b64 = base64.b64encode(image_bytes).decode("utf-8")
        data_uri = f"data:image/jpeg;base64,{image_b64}"

        response = self.image_model.invoke([
            HumanMessage(content=[
                {"type": "text", "text": prompt},
                {"type": "image_url", "image_url": {'url': data_uri}}
            ])
        ])

        return response.content.strip()
