import { Routes, Route, Navigate } from "react-router-dom";
import Login from "../modules/auth/Login.jsx";
import ProtectedRoute from "./ProtectedRoute.jsx";
import AdminRoutes from "../modules/admin/AdminRoutes.jsx";

export default function AppRoutes() {
    const token = localStorage.getItem("token");
    const role = localStorage.getItem("role");

    return (
        <Routes>
            {/* DEFAULT */}
            <Route
                path="/"
                element={
                    token
                        ? role === "ADMIN"
                            ? <Navigate to="/admin" />
                            : <Navigate to="/login" />
                        : <Navigate to="/login" />
                }
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
        </Routes>
    );
}