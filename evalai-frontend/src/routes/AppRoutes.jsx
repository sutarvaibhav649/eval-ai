import { BrowserRouter, Routes, Route } from "react-router-dom";

function AppRoutes() {
    return (
        <BrowserRouter>
        <Routes>
            <Route path="/" element={<h1>Login Page</h1>} />
            <Route path="/admin" element={<h1>Admin Dashboard</h1>} />
        </Routes>
        </BrowserRouter>
    );
}

export default AppRoutes;