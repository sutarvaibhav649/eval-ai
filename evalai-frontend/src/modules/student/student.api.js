import axios from "axios";
import { api } from "../../services/api.js";

const BASE_URL = "http://localhost:8081";

const getAuthHeader = () => {
    const token = localStorage.getItem("token");
    return {
        Authorization: `Bearer ${token}`,
    };
};

// FIXED
export const getStudentResult = (examId, studentId) =>
  api.get(
    `/admin/student/${studentId}/exam/${examId}/result`
  );

// FIXED
export const getStudentFeedback = (examId, studentId) =>
  api.get(
    `/admin/student/${studentId}/exam/${examId}/feedback`
  );