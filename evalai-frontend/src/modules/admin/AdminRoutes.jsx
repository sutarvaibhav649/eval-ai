import { Routes, Route } from "react-router-dom";
import AdminDashboard from "./AdminDashboard";
import CreateSubject from "./CreateSubject";
import CreateExam from "./CreateExam";
import UploadAnswerSheets from "./UploadAnswerSheets";
import TriggerPipeline from "./TriggerPipeline";
import ExamDetails from "./ExamDetails";

export default function AdminRoutes() {
    return (
        <Routes>
            <Route index element={<AdminDashboard />} />
            <Route path="create-subject" element={<CreateSubject />} />
            <Route path="create-exam" element={<CreateExam />} />
            <Route path="upload-sheets" element={<UploadAnswerSheets />} />
            <Route path="pipeline" element={<TriggerPipeline />} />
            <Route path="exam/:examId" element={<ExamDetails />} />
        </Routes>
    );
}