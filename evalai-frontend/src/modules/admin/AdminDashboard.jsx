import { useEffect } from "react";
import AdminLayout from "../../layouts/AdminLayout";
import StatCard from "../../components/StatCard";
import SearchBar from "../../components/SearchBar";
import DataTable from "../../components/DataTable";
import { usePipelineStore } from "../../store/pipeline.store";

export default function AdminDashboard() {
    const { data, fetchStatus, loading } = usePipelineStore();

    useEffect(() => {
        //  Hardcode for now (later dynamic)
        fetchStatus("a76604b6-9531-442b-9b8b-1d42a82c771e");

        const interval = setInterval(() => {
            fetchStatus(examId);
        }, 5000);

        return () => clearInterval(interval);
    }, []);

    if (loading) return <p>Loading...</p>;

    return (
        <AdminLayout>
        <h1 className="text-2xl font-bold mb-6">Dashboard</h1>

        {/* STAT CARDS */}
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

        {/* SEARCH */}
        <SearchBar />

        {/*PROGRESS SECTION */}
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
                style={{
                width: `${(data.completed / data.totalStudents) * 100}%`,
                }}
            />
            </div>
        </div>
        )}

        {/* TABLE */}
        {data && (
            <DataTable
            data={data.students.map((s) => ({
                exam: data.examId,
                year: "2025",
                students: data.totalStudents,
                evaluated: data.completed,
                status: s.evaluationStatus,
            }))}
            />
        )}
        </AdminLayout>
    );
}