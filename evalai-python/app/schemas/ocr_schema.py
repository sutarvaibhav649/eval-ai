from pydantic import BaseModel
from typing import List, Optional

class QuestionContext(BaseModel):
    sub_question_id: str
    sub_question_label: str
    question_number: int
    marks: float
    model_answer_embedding: List[float]

class StudentContext(BaseModel):
    student_id: str
    answer_sheet_id: str

class ExamContext(BaseModel):
    exam_id: str
    exam_name: str
    course_id: str
    course_name: str
    subject_code: str
    subject_name: str        # ← add this
    academic_year: str
    question_paper_id: str
    question_paper_set: str

class OcrRequest(BaseModel):
    task_id: str
    context: ExamContext
    student: StudentContext
    cleaned_image_paths: List[str]
    questions: List[QuestionContext]

class ExtractedAnswer(BaseModel):
    sub_question_id: str
    sub_question_label: str
    question_number: int
    subject_code: str
    status: str                          # COMPLETED or FAILED
    failure_reason: Optional[str] = None
    extracted_text: Optional[str] = None
    cleaned_text: Optional[str] = None
    embedding: Optional[List[float]] = None
    ocr_confidence: Optional[float] = None
    similarity_score: Optional[float] = None
    ai_marks: float = 0.0

class OcrResponse(BaseModel):
    task_id: str
    context: ExamContext
    student: StudentContext
    status: str                          # COMPLETED, COMPLETED_WITH_FAILURES, FAILED
    ocr_engine_used: str
    total_pages_processed: int
    failed_count: int
    completed_count: int
    extracted_answers: List[ExtractedAnswer]