import { create } from "zustand";
import { getAllExams } from "../modules/admin/admin.api";

export const useExamStore = create((set) => ({
    exams: [],
    selectedExam: null,

    fetchExams: async () => {
        const res = await getAllExams();

        set({
        exams: res.data,
        selectedExam: res.data[0] || null,
        });
    },

    setSelectedExam: (exam) => set({ selectedExam: exam }),
}));