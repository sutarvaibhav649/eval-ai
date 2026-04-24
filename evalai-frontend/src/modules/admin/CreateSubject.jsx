import { useState } from "react";
import AdminLayout from "../../layouts/AdminLayout";
import { createSubject } from "./admin.api";

export default function CreateSubject() {
    const [form, setForm] = useState({
        name: "",
        code: "",
        department: "",
        semester: "",
    });
    const [loading, setLoading] = useState(false);
    const [success, setSuccess] = useState(null);
    const [error, setError] = useState(null);

    const handleChange = (e) => {
        setForm({ ...form, [e.target.name]: e.target.value });
        setError(null);
        setSuccess(null);
    };

    const handleSubmit = async (e) => {
        e.preventDefault();
        setLoading(true);
        setError(null);
        setSuccess(null);

        try {
            const res = await createSubject({
                ...form,
                semester: parseInt(form.semester),
            });
            setSuccess(`Subject "${res.data.name}" created successfully!`);
            setForm({ name: "", code: "", department: "", semester: "" });
        } catch (err) {
            const msg = err.response?.data || "Failed to create subject";
            setError(typeof msg === "string" ? msg : "Something went wrong");
        } finally {
            setLoading(false);
        }
    };

    return (
        <AdminLayout>
            <div className="max-w-xl">
                <h1 className="text-2xl font-bold mb-1">Create Subject</h1>
                <p className="text-sm text-gray-500 mb-6">
                    Add a new subject to the system before creating exams.
                </p>

                {success && (
                    <div className="mb-4 px-4 py-3 bg-green-50 border border-green-200 rounded-lg text-green-700 text-sm">
                        {success}
                    </div>
                )}
                {error && (
                    <div className="mb-4 px-4 py-3 bg-red-50 border border-red-200 rounded-lg text-red-700 text-sm">
                        {error}
                    </div>
                )}

                <form
                    onSubmit={handleSubmit}
                    className="bg-white rounded-xl shadow-md p-6 space-y-4"
                >
                    <div>
                        <label className="block text-sm font-medium text-gray-700 mb-1">
                            Subject Name
                        </label>
                        <input
                            name="name"
                            value={form.name}
                            onChange={handleChange}
                            placeholder="e.g. Data Structures"
                            required
                            className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-400"
                        />
                    </div>

                    <div>
                        <label className="block text-sm font-medium text-gray-700 mb-1">
                            Subject Code
                        </label>
                        <input
                            name="code"
                            value={form.code}
                            onChange={handleChange}
                            placeholder="e.g. CS301"
                            required
                            className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-400"
                        />
                    </div>

                    <div>
                        <label className="block text-sm font-medium text-gray-700 mb-1">
                            Department
                        </label>
                        <input
                            name="department"
                            value={form.department}
                            onChange={handleChange}
                            placeholder="e.g. Computer Science"
                            required
                            className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-400"
                        />
                    </div>

                    <div>
                        <label className="block text-sm font-medium text-gray-700 mb-1">
                            Semester
                        </label>
                        <select
                            name="semester"
                            value={form.semester}
                            onChange={handleChange}
                            required
                            className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-400 bg-white"
                        >
                            <option value="">Select semester</option>
                            {[1, 2, 3, 4, 5, 6, 7, 8].map((s) => (
                                <option key={s} value={s}>
                                    Semester {s}
                                </option>
                            ))}
                        </select>
                    </div>

                    <button
                        type="submit"
                        disabled={loading}
                        className="w-full bg-[#6366f1] text-white py-2.5 rounded-lg text-sm font-semibold hover:bg-[#5254cc] transition disabled:opacity-60"
                    >
                        {loading ? "Creating..." : "Create Subject"}
                    </button>
                </form>
            </div>
        </AdminLayout>
    );
}