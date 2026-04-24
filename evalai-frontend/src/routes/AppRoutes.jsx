import { Routes, Route, Navigate } from "react-router-dom";
import Login from "../modules/auth/Login.jsx";
import ProtectedRoute from "./ProtectedRoute.jsx";
import AdminRoutes from "../modules/admin/AdminRoutes.jsx";

export default function AppRoutes() {
    const token = localStorage.getItem("token");
    const role = localStorage.getItem("role");

    const getDefaultRedirect = () => {
        if (!token) return "/login";
        if (role === "ADMIN") return "/admin";
        if (role === "FACULTY") return "/faculty";
        if (role === "STUDENT") return "/student";
        return "/login";
    };

    return (
        <Routes>
            {/* DEFAULT — redirect based on role */}
            <Route
                path="/"
                element={<Navigate to={getDefaultRedirect()} />}
            />

            {/* LOGIN */}
            <Route path="/login" element={<Login />} />

            {/* ADMIN */}
            <Route
                path="/admin/*"
                element={
                    <ProtectedRoute role="ADMIN">
                        <AdminRoutes />
                    </ProtectedRoute>
                }
            />

            {/* UNAUTHORIZED */}
            <Route
                path="/unauthorized"
                element={
                    <div className="h-screen flex items-center justify-center">
                        <p className="text-gray-500">
                            You are not authorized to view this page.
                        </p>
                    </div>
                }
            />

            {/* 404 */}
            <Route
                path="*"
                element={
                    <div className="h-screen flex items-center justify-center">
                        <p className="text-gray-500">Page not found.</p>
                    </div>
                }
            />
        </Routes>
    );
}