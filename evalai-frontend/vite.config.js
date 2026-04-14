import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

// https://vite.dev/config/
export default defineConfig({
  content: ["./index.html", "./src/**/*.{js,jsx}"],
  theme: {
    extend: {
      colors: {
        primary: "#6366f1",
        secondary: "#8b5cf6",
        accent: "#06b6d4",

        success: "#10b981",
        warning: "#f59e0b",
        danger: "#f43f5e",

        bg: "#f8fafc",

        textPrimary: "#1e293b",
        textSecondary: "#64748b",
        textMuted: "#94a3b8",
      },
    },
  },
  plugins: [react(),tailwindcss()],
})