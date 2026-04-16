import { create } from "zustand";
import {
    getStudentResult,
    getStudentFeedback,
} from "../modules/student/student.api.js";

export const useStudentStore = create((set) => ({
    result: null,
    feedback: null,
    loading: false,

    fetchStudentData: async (examId, studentId) => {
        set({ loading: true });

        try {
            const [resultRes, feedbackRes] = await Promise.all([
            getStudentResult(examId, studentId),
            getStudentFeedback(examId, studentId),
            ]);

            set({
            result: resultRes.data,
            feedback: feedbackRes.data,
            loading: false,
            });
        } catch (err) {
            console.error(err);
            set({ loading: false });
        }
        }
}));