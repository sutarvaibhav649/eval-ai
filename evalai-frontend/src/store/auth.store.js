import { create } from "zustand";

export const useAuthStore = create((set) => ({
    user: null,
    token: localStorage.getItem("token"),

    setAuth: (data) => {
        localStorage.setItem("token", data.accessToken);

        set({
        user: data,
        token: data.accessToken,
        });
    },

    logout: () => {
        localStorage.removeItem("token");
        set({ user: null, token: null });
    },
}));