import { create } from "zustand";
import { getEvaluationStatus } from "../modules/admin/admin.api";

export const usePipelineStore = create((set) => ({
    data: null,
    loading: false,
    error: null,

    fetchStatus: async (examId) => {
        try {
        set({ loading: true });

        const res = await getEvaluationStatus(examId);

        set({
            data: res.data,
            loading: false,
        });
        } catch (err) {
        set({
            error: err.message,
            loading: false,
        });
        }
    },
}));