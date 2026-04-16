import { useParams, useNavigate } from "react-router-dom";
import { useEffect } from "react";
import AdminLayout from "../../layouts/AdminLayout";
import { usePipelineStore } from "../../store/pipeline.store";
import StatusBadge from "../../components/StatusBadge";
import { useExamStore } from "../../store/exam.store";

export default function ExamDetails() {
    const { examId } = useParams();
    const navigate = useNavigate();
    const { data, fetchStatus } = usePipelineStore();
    const { exams, fetchExams, selectedExam, setSelectedExam } = useExamStore();

    useEffect(() => {
        if (exams.length === 0) {
            fetchExams();
        }
    }, [exams, fetchExams]);

    useEffect(() => {
        if (!examId || exams.length === 0) return;

        const exam = exams.find((e) => e.id === examId);

        if (exam) {
            setSelectedExam(exam);
        }
    }, [examId, exams, setSelectedExam]);

    useEffect(() => {
        if (!examId) return;
        fetchStatus(examId);
    }, [examId, fetchStatus]);

    // ✅ HANDLE NULL DATA
    if (!data) {
        return (
        <AdminLayout>
            <p className="text-gray-500">Loading exam data...</p>
        </AdminLayout>
        );
    }

    return (
        <AdminLayout>
        <h1 className="text-2xl font-bold mb-6">Exam Details</h1>

        {/* 🔥 HEADER */}
        <div className="bg-white p-4 rounded-xl shadow-md mb-6">
            <h2 className="text-lg font-semibold">
                {selectedExam?.title || "Exam"}
            </h2>

            <p className="text-sm text-gray-500">
                {selectedExam?.academicYear}
            </p>

            <div className="flex gap-6 text-sm text-gray-500 mt-2">
                <span>Total: {data.totalStudents}</span>
                <span>Completed: {data.completed}</span>
                <span>Pending: {data.pending}</span>
            </div>
        </div>

        {/* 🔥 TABLE */}
        <div className="bg-white rounded-xl shadow-md p-4">
            <h2 className="text-lg font-semibold mb-4">
            Student Evaluations
            </h2>

            {data.students?.length === 0 ? (
            <p className="text-center text-gray-500 py-4">
                No students found
            </p>
            ) : (
            <table className="w-full text-left">
                <thead className="border-b">
                <tr>
                    <th className="p-3">Name</th>
                    <th>Pipeline</th>
                    <th>Marks</th>
                </tr>
                </thead>

                <tbody>
                {data.students.map((s, i) => (
                    <tr
                    key={i}
                    className={`border-b cursor-pointer ${
                        s.evaluationStatus === "FAILED"
                        ? "bg-red-50 hover:bg-red-100"
                        : s.evaluationStatus === "COMPLETED"
                        ? "bg-green-50 hover:bg-green-100"
                        : "bg-yellow-50 hover:bg-yellow-100"
                    }`}
                    onClick={() =>
                        navigate(`/admin/exam/${examId}/student/${s.studentId}`)
                    }
                    >
                    <td className="p-3 font-medium">
                        {s.studentName || "-"}
                    </td>

                    <td>
                        <div className="flex items-center gap-3">
                        <StatusBadge status={s.ocrStatus} />
                        <div className="w-6 h-0.5 bg-gray-300"></div>
                        <StatusBadge status={s.evaluationStatus} />
                        </div>
                    </td>

                    <td
                        className={`font-semibold ${
                        s.totalMarks >= 15
                            ? "text-green-600"
                            : s.totalMarks >= 8
                            ? "text-yellow-600"
                            : "text-red-600"
                        }`}
                    >
                        {s.totalMarks ?? "-"}
                    </td>
                    </tr>
                ))}
                </tbody>
            </table>
            )}
        </div>
        </AdminLayout>
    );
}