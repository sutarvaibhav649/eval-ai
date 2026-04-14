import AdminLayout from "../../layouts/AdminLayout";
import StatCard from "../../components/StatCard";
import SearchBar from "../../components/SearchBar";
import DataTable from "../../components/DataTable";

export default function AdminDashboard() {
    const dummyData = [
        {
            exam: "MSE-March",
            year: 2025,
            students: 250,
            evaluated: 190,
            status: "PROCESSING",
        },
        {
            exam: "ESE-June",
            year: 2025,
            students: 250,
            evaluated: 0,
            status: "QUEUED",
        },
        {
            exam: "MSE-March",
            year: 2024,
            students: 230,
            evaluated: 230,
            status: "COMPLETED",
        },
    ];

    return (
        <AdminLayout>
        <h1 className="text-2xl font-bold mb-6">Dashboard</h1>

        {/* Stat Cards */}
        <div className="flex gap-6 mb-6">
            <StatCard
                title="Total Exams"
                value="10"
                gradient="bg-gradient-to-r from-indigo-500 to-indigo-400"
            />
            <StatCard
                title="Students"
                value="250"
                gradient="bg-gradient-to-r from-purple-500 to-purple-400"
            />
            <StatCard
                title="Completed"
                value="200"
                gradient="bg-gradient-to-r from-green-500 to-green-400"
            />
            <StatCard
                title="Pending"
                value="50"
                gradient="bg-gradient-to-r from-yellow-500 to-yellow-400"
            />
        </div>

        {/* Search */}
        <SearchBar />

        {/* Table */}
        <DataTable data={dummyData} />
        </AdminLayout>
    );
}