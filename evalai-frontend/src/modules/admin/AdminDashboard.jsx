import AdminLayout from "../../layouts/AdminLayout";

export default function AdminDashboard() {
    return (
        <AdminLayout>
        <h1 className="text-2xl font-bold mb-4">Dashboard</h1>

        <p className="text-textSecondary">
            This is your admin dashboard.
        </p>
        </AdminLayout>
    );
}