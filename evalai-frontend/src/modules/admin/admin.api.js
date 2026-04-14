import { api } from "../../services/api";

export const getEvaluationStatus = (examId) =>
    api.get(`/admin/evaluation/status?examId=${examId}`);