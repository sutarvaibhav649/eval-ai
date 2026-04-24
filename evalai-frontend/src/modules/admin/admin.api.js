import { api } from "../../services/api";

export const getEvaluationStatus = (examId) =>
    api.get(`/admin/evaluation/status?examId=${examId}`);

export const getAllExams = () =>
    api.get("/admin/exam");

export const createSubject = (data) =>
    api.post("/admin/subjects", data);

export const createExam = (data) =>
    api.post("/admin/exam", data);

export const uploadAnswerSheets = (formData) =>
    api.post("/admin/answer-sheets/upload", formData, {
        headers: { "Content-Type": "multipart/form-data" },
    });

export const triggerPipeline = (examId, subjectId) =>
    api.post("/pipeline/start", { examId, subjectId });

export const getAllSubjects = () =>
    api.get("/admin/subjects");