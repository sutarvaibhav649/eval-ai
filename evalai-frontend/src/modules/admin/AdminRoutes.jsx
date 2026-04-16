import { Routes, Route } from "react-router-dom";
import AdminDashboard from "./AdminDashboard";
import ExamDetails from "./ExamDetails";
import StudentDetails from "../student/StudentDetails";

export default function AdminRoutes() {
    return (
        <Routes>
            {/* ✅ DEFAULT */}
            <Route index element={<AdminDashboard />} />

            <Route path="exam/:examId" element={<ExamDetails />} />

            <Route
                path="exam/:examId/student/:studentId"
                element={<StudentDetails />}
            />
        </Routes>
    );
}