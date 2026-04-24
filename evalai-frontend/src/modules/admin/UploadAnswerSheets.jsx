import { useState, useEffect } from "react";
import AdminLayout from "../../layouts/AdminLayout";
import { uploadAnswerSheets } from "./admin.api";
import { api } from "../../services/api";

export default function UploadAnswerSheets() {
    const [exams, setExams] = useState([]);
    const [selectedExam, setSelectedExam] = useState(null);
    const [selectedSubjectId, setSelectedSubjectId] = useState("");
    const [rows, setRows] = useState([]); // [{file, studentId}]
    const [loading, setLoading] = useState(false);
    const [success, setSuccess] = useState(null);
    const [error, setError] = useState(null);

    useEffect(() => {
        api.get("/admin/exam")
            .then((res) => setExams(res.data || []))
            .catch(() => setExams([]));
    }, []);

    const handleExamChange = (e) => {
        const exam = exams.find((ex) => ex.id === e.target.value);
        setSelectedExam(exam || null);
        setSelectedSubjectId("");
        setRows([]);
        setError(null);
        setSuccess(null);
    };

    const handleFilesChange = (e) => {
        const files = Array.from(e.target.files);
        setRows(files.map((file) => ({ file, studentId: "" })));
        setError(null);
        setSuccess(null);
    };

    const handleStudentIdChange = (index, value) => {
        setRows((prev) =>
            prev.map((row, i) => (i === index ? { ...row, studentId: value } : row))
        );
    };

    const removeRow = (index) => {
        setRows((prev) => prev.filter((_, i) => i !== index));
    };

    const handleSubmit = async () => {
        if (!selectedExam) {
            setError("Please select an exam.");
            return;
        }
        if (!selectedSubjectId) {
            setError("Please select a subject.");
            return;
        }
        if (rows.length === 0) {
            setError("Please upload at least one answer sheet.");
            return;
        }
        const emptyIds = rows.some((r) => !r.studentId.trim());
        if (emptyIds) {
            setError("Please enter a student ID for every file.");
            return;
        }

        setLoading(true);
        setError(null);
        setSuccess(null);

        try {
            const formData = new FormData();
            formData.append("examId", selectedExam.id);
            formData.append("subjectId", selectedSubjectId);
            rows.forEach((row) => {
                formData.append("studentIds", row.studentId.trim());
                formData.append("files", row.file);
            });

            const res = await uploadAnswerSheets(formData);
            setSuccess(
                `Successfully uploaded ${res.data.totalUploaded} answer sheet(s). Status: ${res.data.status}`
            );
            setRows([]);
        } catch (err) {
            const msg = err.response?.data || "Upload failed";
            setError(typeof msg === "string" ? msg : "Something went wrong");
        } finally {
            setLoading(false);
        }
    };

    const subjects = selectedExam?.subjects || [];

    return (
        <AdminLayout>
            <div className="max-w-3xl">
                <h1 className="text-2xl font-bold mb-1">Upload Answer Sheets</h1>
                <p className="text-sm text-gray-500 mb-6">
                    Upload student answer sheet PDFs in bulk. Each file must be matched to a student ID.
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

                <div className="bg-white rounded-xl shadow-md p-6 space-y-5">
                    {/* Exam selector */}
                    <div>
                        <label className="block text-sm font-medium text-gray-700 mb-1">
                            Select Exam
                        </label>
                        <select
                            className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-400 bg-white"
                            value={selectedExam?.id || ""}
                            onChange={handleExamChange}
                        >
                            <option value="">-- Choose exam --</option>
                            {exams.map((exam) => (
                                <option key={exam.id} value={exam.id}>
                                    {exam.title} ({exam.academicYear})
                                </option>
                            ))}
                        </select>
                    </div>

                    {/* Subject selector */}
                    {selectedExam && subjects.length > 0 && (
                        <div>
                            <label className="block text-sm font-medium text-gray-700 mb-1">
                                Select Subject
                            </label>
                            <select
                                className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-400 bg-white"
                                value={selectedSubjectId}
                                onChange={(e) => setSelectedSubjectId(e.target.value)}
                            >
                                <option value="">-- Choose subject --</option>
                                {subjects.map((s) => (
                                    <option key={s.subjectId} value={s.subjectId}>
                                        {s.name} ({s.code})
                                    </option>
                                ))}
                            </select>
                        </div>
                    )}

                    {/* File picker */}
                    <div>
                        <label className="block text-sm font-medium text-gray-700 mb-1">
                            Select PDF Files
                        </label>
                        <input
                            type="file"
                            accept="application/pdf"
                            multiple
                            onChange={handleFilesChange}
                            className="block w-full text-sm text-gray-500 file:mr-4 file:py-2 file:px-4 file:rounded-lg file:border-0 file:text-sm file:font-medium file:bg-indigo-50 file:text-indigo-700 hover:file:bg-indigo-100 cursor-pointer"
                        />
                        <p className="text-xs text-gray-400 mt-1">
                            Only PDF files accepted. Each file = one student's answer sheet.
                        </p>
                    </div>

                    {/* Rows table */}
                    {rows.length > 0 && (
                        <div>
                            <p className="text-sm font-medium text-gray-700 mb-2">
                                Enter Student ID for each file
                            </p>
                            <div className="rounded-lg border border-gray-200 overflow-hidden">
                                <table className="w-full text-sm">
                                    <thead className="bg-gray-50 text-gray-600">
                                        <tr>
                                            <th className="text-left px-4 py-2 font-medium">#</th>
                                            <th className="text-left px-4 py-2 font-medium">
                                                File Name
                                            </th>
                                            <th className="text-left px-4 py-2 font-medium">
                                                Student ID
                                            </th>
                                            <th className="px-4 py-2"></th>
                                        </tr>
                                    </thead>
                                    <tbody>
                                        {rows.map((row, index) => (
                                            <tr
                                                key={index}
                                                className="border-t border-gray-100"
                                            >
                                                <td className="px-4 py-2 text-gray-400">
                                                    {index + 1}
                                                </td>
                                                <td className="px-4 py-2 text-gray-700 max-w-[180px] truncate">
                                                    {row.file.name}
                                                </td>
                                                <td className="px-4 py-2">
                                                    <input
                                                        type="text"
                                                        placeholder="Enter student ID"
                                                        value={row.studentId}
                                                        onChange={(e) =>
                                                            handleStudentIdChange(
                                                                index,
                                                                e.target.value
                                                            )
                                                        }
                                                        className="border border-gray-200 rounded-md px-2 py-1 text-sm w-full focus:outline-none focus:ring-1 focus:ring-indigo-400"
                                                    />
                                                </td>
                                                <td className="px-4 py-2">
                                                    <button
                                                        onClick={() => removeRow(index)}
                                                        className="text-red-400 hover:text-red-600 text-xs"
                                                    >
                                                        Remove
                                                    </button>
                                                </td>
                                            </tr>
                                        ))}
                                    </tbody>
                                </table>
                            </div>
                        </div>
                    )}

                    <button
                        onClick={handleSubmit}
                        disabled={loading || rows.length === 0}
                        className="w-full bg-[#6366f1] text-white py-2.5 rounded-lg text-sm font-semibold hover:bg-[#5254cc] transition disabled:opacity-60"
                    >
                        {loading
                            ? "Uploading..."
                            : `Upload ${rows.length > 0 ? rows.length : ""} Answer Sheet${rows.length !== 1 ? "s" : ""}`}
                    </button>
                </div>
            </div>
        </AdminLayout>
    );
}