import { useEffect } from "react";
import AdminLayout from "../../layouts/AdminLayout";
import StatCard from "../../components/StatCard";
import DataTable from "../../components/DataTable";
import { usePipelineStore } from "../../store/pipeline.store";
import { useExamStore } from "../../store/exam.store";

export default function AdminDashboard() {
    const { data, fetchStatus } = usePipelineStore();
    const { exams, fetchExams, selectedExam, setSelectedExam } =
        useExamStore();

    // Fetch exams once
    useEffect(() => {
        fetchExams();
    }, [fetchExams]);

    // Polling with smart stop
    useEffect(() => {
        if (!selectedExam) return;

        fetchStatus(selectedExam.id);

        const interval = setInterval(() => {
        // 🔥 Stop polling when completed
        if (data && data.pending === 0) return;

        fetchStatus(selectedExam.id);
        }, 5000);

        return () => clearInterval(interval);
    }, [selectedExam, data, fetchStatus]);

    // Table data
    const tableData =
        data?.students?.map((s) => ({
        examId: selectedExam?.id,
        examTitle: selectedExam?.title,
        exam: selectedExam?.title || "-",
        year: selectedExam?.academicYear || "-",
        students: data?.totalStudents || 0,
        evaluated: data?.completed || 0,
        status: s.evaluationStatus,
        })) || [];

    // Progress safe calc
    const progress =
        data?.totalStudents > 0
        ? (data.completed / data.totalStudents) * 100
        : 0;

    return (
        <AdminLayout>
        <h1 className="text-2xl font-bold mb-6">Dashboard</h1>

        {/* 🔥 EXAM SELECTOR */}
        <div className="mb-6">
            {exams.length === 0 ? (
            <p className="text-sm text-gray-500">
                No exams available
            </p>
            ) : (
            <select
                className="border px-3 py-2 rounded-md bg-white shadow-sm"
                value={selectedExam?.id || ""}
                onChange={(e) => {
                const exam = exams.find(
                    (ex) => ex.id === e.target.value
                );
                setSelectedExam(exam);
                }}
            >
                {exams.map((exam) => (
                <option key={exam.id} value={exam.id}>
                    {exam.title} ({exam.academicYear})
                </option>
                ))}
            </select>
            )}
        </div>

        {/* 📘 SUBJECT */}
        {selectedExam?.subjects?.length > 0 && (
            <p className="text-sm text-gray-500 mb-4">
            Subject: {selectedExam.subjects[0].name}
            </p>
        )}

        {/* 🔥 STAT CARDS */}
        {data && (
            <div className="flex gap-6 mb-6">
            <StatCard
                title="Total Students"
                value={data.totalStudents}
                gradient="bg-gradient-to-r from-[#6366f1] to-[#818cf8]"
            />
            <StatCard
                title="Completed"
                value={data.completed}
                gradient="bg-gradient-to-r from-[#10b981] to-[#34d399]"
            />
            <StatCard
                title="Pending"
                value={data.pending}
                gradient="bg-gradient-to-r from-[#f59e0b] to-[#fbbf24]"
            />
            <StatCard
                title="Failed"
                value={data.failed}
                gradient="bg-gradient-to-r from-[#f43f5e] to-[#fb7185]"
            />
            </div>
        )}

        {/* 📊 PROGRESS */}
        {data && (
            <div className="bg-white p-4 rounded-xl shadow-md mb-6">
            <h2 className="text-lg font-semibold mb-2">
                Evaluation Progress
            </h2>

            <p className="text-sm text-gray-600 mb-2">
                {data.completed} / {data.totalStudents} Evaluated
            </p>

            <div className="w-full bg-gray-200 rounded-full h-2">
                <div
                className="bg-green-500 h-2 rounded-full transition-all duration-500"
                style={{ width: `${progress}%` }}
                />
            </div>
            </div>
        )}

        {/* 📋 TABLE */}
        {data && <DataTable data={tableData} />}
        </AdminLayout>
    );
}