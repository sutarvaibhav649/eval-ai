import { BrowserRouter, Routes, Route } from "react-router-dom";
import AdminDashboard from "../modules/admin/AdminDashboard";

function AppRoutes() {
    return (
        <BrowserRouter>
        <Routes>
            <Route path="/" element={<h1>Login Page</h1>} />
            <Route path="/admin" element={<AdminDashboard />} />
        </Routes>
        </BrowserRouter>
    );
}

export default AppRoutes;