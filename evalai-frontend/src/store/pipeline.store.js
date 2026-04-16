import { create } from "zustand";
import { getEvaluationStatus } from "../modules/admin/admin.api";

export const usePipelineStore = create((set) => ({
    data: null,
    loading: false,
    error: null,

    fetchStatus: async (examId) => {
        try {
            const res = await getEvaluationStatus(examId);

            set((state) => {
            // 🔥 Only update if data actually changed
            if (JSON.stringify(state.data) === JSON.stringify(res.data)) {
                return state; // no update → no re-render
            }

            return {
                data: res.data,
                loading: false,
            };
            });

        } catch (err) {
            set({
            error: err.message,
            loading: false,
            });
        }
        },
}));