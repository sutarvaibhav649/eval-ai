import { useState, useEffect } from "react";
import AdminLayout from "../../layouts/AdminLayout";
import { createExam } from "./admin.api";
import { api } from "../../services/api";

export default function CreateExam() {
    const [subjects, setSubjects] = useState([]);
    const [form, setForm] = useState({
        title: "",
        subjectIds: [],
        examDate: "",
        academicYear: "",
        totalMarks: "",
        duration: "",
        grievanceDeadline: "",
    });
    const [loading, setLoading] = useState(false);
    const [success, setSuccess] = useState(null);
    const [error, setError] = useState(null);

    useEffect(() => {
        api.get("/admin/subjects")
            .then((res) => setSubjects(res.data || []))
            .catch(() => setSubjects([]));
    }, []);

    const handleChange = (e) => {
        setForm({ ...form, [e.target.name]: e.target.value });
        setError(null);
        setSuccess(null);
    };

    const toggleSubject = (id) => {
        setForm((prev) => ({
            ...prev,
            subjectIds: prev.subjectIds.includes(id)
                ? prev.subjectIds.filter((s) => s !== id)
                : [...prev.subjectIds, id],
        }));
    };

    const handleSubmit = async (e) => {
        e.preventDefault();
        if (form.subjectIds.length === 0) {
            setError("Select at least one subject.");
            return;
        }
        setLoading(true);
        setError(null);
        setSuccess(null);

        try {
            const payload = {
                title: form.title,
                subjectIds: form.subjectIds,
                examDate: new Date(form.examDate).toISOString(),
                academicYear: form.academicYear,
                totalMarks: parseInt(form.totalMarks),
                duration: parseInt(form.duration),
                grievanceDeadline: new Date(form.grievanceDeadline).toISOString(),
            };
            const res = await createExam(payload);
            setSuccess(`Exam "${res.data.title}" created successfully!`);
            setForm({
                title: "",
                subjectIds: [],
                examDate: "",
                academicYear: "",
                totalMarks: "",
                duration: "",
                grievanceDeadline: "",
            });
        } catch (err) {
            const msg = err.response?.data || "Failed to create exam";
            setError(typeof msg === "string" ? msg : "Something went wrong");
        } finally {
            setLoading(false);
        }
    };

    return (
        <AdminLayout>
            <div className="max-w-xl">
                <h1 className="text-2xl font-bold mb-1">Create Exam</h1>
                <p className="text-sm text-gray-500 mb-6">
                    Set up a new exam and link it to one or more subjects.
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
                    {/* Title */}
                    <div>
                        <label className="block text-sm font-medium text-gray-700 mb-1">
                            Exam Title
                        </label>
                        <input
                            name="title"
                            value={form.title}
                            onChange={handleChange}
                            placeholder="e.g. Mid Semester Exam 2025"
                            required
                            className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-400"
                        />
                    </div>

                    {/* Subjects */}
                    <div>
                        <label className="block text-sm font-medium text-gray-700 mb-2">
                            Subjects{" "}
                            <span className="text-gray-400 font-normal">
                                (select one or more)
                            </span>
                        </label>
                        {subjects.length === 0 ? (
                            <p className="text-sm text-gray-400">
                                No subjects found. Create subjects first.
                            </p>
                        ) : (
                            <div className="grid grid-cols-2 gap-2">
                                {subjects.map((s) => (
                                    <label
                                        key={s.subjectId}
                                        className={`flex items-center gap-2 px-3 py-2 rounded-lg border cursor-pointer text-sm transition ${
                                            form.subjectIds.includes(s.subjectId)
                                                ? "border-indigo-400 bg-indigo-50 text-indigo-700"
                                                : "border-gray-200 text-gray-600 hover:border-indigo-200"
                                        }`}
                                    >
                                        <input
                                            type="checkbox"
                                            className="accent-indigo-500"
                                            checked={form.subjectIds.includes(s.subjectId)}
                                            onChange={() => toggleSubject(s.subjectId)}
                                        />
                                        <span>
                                            {s.name}{" "}
                                            <span className="text-xs text-gray-400">
                                                ({s.code})
                                            </span>
                                        </span>
                                    </label>
                                ))}
                            </div>
                        )}
                    </div>

                    {/* Academic Year */}
                    <div>
                        <label className="block text-sm font-medium text-gray-700 mb-1">
                            Academic Year
                        </label>
                        <input
                            name="academicYear"
                            value={form.academicYear}
                            onChange={handleChange}
                            placeholder="e.g. 2024-25"
                            required
                            className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-400"
                        />
                    </div>

                    {/* Exam Date */}
                    <div>
                        <label className="block text-sm font-medium text-gray-700 mb-1">
                            Exam Date
                        </label>
                        <input
                            type="datetime-local"
                            name="examDate"
                            value={form.examDate}
                            onChange={handleChange}
                            required
                            className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-400"
                        />
                    </div>

                    {/* Total Marks + Duration */}
                    <div className="grid grid-cols-2 gap-4">
                        <div>
                            <label className="block text-sm font-medium text-gray-700 mb-1">
                                Total Marks
                            </label>
                            <input
                                type="number"
                                name="totalMarks"
                                value={form.totalMarks}
                                onChange={handleChange}
                                placeholder="e.g. 100"
                                required
                                min={1}
                                className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-400"
                            />
                        </div>
                        <div>
                            <label className="block text-sm font-medium text-gray-700 mb-1">
                                Duration (mins)
                            </label>
                            <input
                                type="number"
                                name="duration"
                                value={form.duration}
                                onChange={handleChange}
                                placeholder="e.g. 180"
                                required
                                min={1}
                                className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-400"
                            />
                        </div>
                    </div>

                    {/* Grievance Deadline */}
                    <div>
                        <label className="block text-sm font-medium text-gray-700 mb-1">
                            Grievance Deadline
                        </label>
                        <input
                            type="datetime-local"
                            name="grievanceDeadline"
                            value={form.grievanceDeadline}
                            onChange={handleChange}
                            required
                            className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-400"
                        />
                    </div>

                    <button
                        type="submit"
                        disabled={loading}
                        className="w-full bg-[#6366f1] text-white py-2.5 rounded-lg text-sm font-semibold hover:bg-[#5254cc] transition disabled:opacity-60"
                    >
                        {loading ? "Creating..." : "Create Exam"}
                    </button>
                </form>
            </div>
        </AdminLayout>
    );
}