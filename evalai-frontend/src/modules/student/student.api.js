import { api } from "../../services/api.js";

export const getStudentResult = (examId, studentId) =>
  api.get(
    `/admin/student/${studentId}/exam/${examId}/result`
  );

export const getStudentFeedback = (examId, studentId) =>
  api.get(
    `/admin/student/${studentId}/exam/${examId}/feedback`
  );
