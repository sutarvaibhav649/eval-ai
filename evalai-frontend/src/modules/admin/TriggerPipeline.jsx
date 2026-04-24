import { useState, useEffect } from "react";
import AdminLayout from "../../layouts/AdminLayout";
import { triggerPipeline } from "./admin.api";
import { api } from "../../services/api";

export default function TriggerPipeline() {
    const [exams, setExams] = useState([]);
    const [selectedExam, setSelectedExam] = useState(null);
    const [selectedSubjectId, setSelectedSubjectId] = useState("");
    const [showConfirm, setShowConfirm] = useState(false);
    const [loading, setLoading] = useState(false);
    const [result, setResult] = useState(null); // {success, message}
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
        setResult(null);
        setError(null);
    };

    const handleStartClick = () => {
        if (!selectedExam) {
            setError("Please select an exam.");
            return;
        }
        if (!selectedSubjectId) {
            setError("Please select a subject.");
            return;
        }
        setError(null);
        setShowConfirm(true);
    };

    const handleConfirm = async () => {
        setShowConfirm(false);
        setLoading(true);
        setResult(null);
        setError(null);

        try {
            const res = await triggerPipeline(selectedExam.id, selectedSubjectId);
            setResult({
                success: true,
                message: `Pipeline started! ${res.data.sheetsQueued} answer sheet(s) queued for evaluation.`,
            });
        } catch (err) {
            const msg = err.response?.data || "Failed to start pipeline";
            setResult({
                success: false,
                message: typeof msg === "string" ? msg : "Something went wrong",
            });
        } finally {
            setLoading(false);
        }
    };

    const subjects = selectedExam?.subjects || [];
    const selectedSubject = subjects.find((s) => s.subjectId === selectedSubjectId);

    return (
        <AdminLayout>
            <div className="max-w-xl">
                <h1 className="text-2xl font-bold mb-1">Trigger Pipeline</h1>
                <p className="text-sm text-gray-500 mb-6">
                    Start AI evaluation for all pending answer sheets in a selected exam and subject.
                </p>

                {/* Result banner */}
                {result && (
                    <div
                        className={`mb-4 px-4 py-3 rounded-lg text-sm border ${
                            result.success
                                ? "bg-green-50 border-green-200 text-green-700"
                                : "bg-red-50 border-red-200 text-red-700"
                        }`}
                    >
                        {result.message}
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

                    {/* Info box */}
                    {selectedExam && selectedSubjectId && (
                        <div className="bg-indigo-50 border border-indigo-100 rounded-lg px-4 py-3 text-sm text-indigo-700">
                            <p className="font-medium mb-1">Ready to start pipeline</p>
                            <p>Exam: {selectedExam.title}</p>
                            <p>Subject: {selectedSubject?.name} ({selectedSubject?.code})</p>
                            <p className="mt-1 text-indigo-500 text-xs">
                                All PENDING answer sheets for this exam + subject will be queued.
                            </p>
                        </div>
                    )}

                    <button
                        onClick={handleStartClick}
                        disabled={loading}
                        className="w-full bg-[#6366f1] text-white py-2.5 rounded-lg text-sm font-semibold hover:bg-[#5254cc] transition disabled:opacity-60"
                    >
                        {loading ? "Starting..." : "Start Evaluation Pipeline"}
                    </button>
                </div>
            </div>

            {/* Confirm Dialog */}
            {showConfirm && (
                <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40">
                    <div className="bg-white rounded-2xl shadow-xl p-6 w-full max-w-sm mx-4">
                        <h2 className="text-lg font-bold text-gray-800 mb-2">
                            Start Pipeline?
                        </h2>
                        <p className="text-sm text-gray-500 mb-1">
                            You are about to trigger evaluation for:
                        </p>
                        <p className="text-sm font-medium text-gray-700">
                            {selectedExam?.title} — {selectedSubject?.name}
                        </p>
                        <p className="text-xs text-gray-400 mt-2 mb-5">
                            All pending answer sheets will be sent to the AI pipeline. This cannot be undone.
                        </p>
                        <div className="flex gap-3">
                            <button
                                onClick={() => setShowConfirm(false)}
                                className="flex-1 border border-gray-200 text-gray-600 py-2 rounded-lg text-sm font-medium hover:bg-gray-50 transition"
                            >
                                Cancel
                            </button>
                            <button
                                onClick={handleConfirm}
                                className="flex-1 bg-[#6366f1] text-white py-2 rounded-lg text-sm font-semibold hover:bg-[#5254cc] transition"
                            >
                                Yes, Start
                            </button>
                        </div>
                    </div>
                </div>
            )}
        </AdminLayout>
    );
}