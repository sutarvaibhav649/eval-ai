import { useEffect, useState } from "react";
import { useParams } from "react-router-dom";
import { getStudentResult, getStudentFeedback } from "./student.api";

export default function StudentDetails() {
    const { examId, studentId } = useParams();

    const [resultData, setResultData] = useState(null);
    const [feedbackData, setFeedbackData] = useState([]);
    const [loading, setLoading] = useState(true);
    const [openIndex, setOpenIndex] = useState(null);

    useEffect(() => {
        const fetchData = async () => {
        try {
            const [resultRes, feedbackRes] = await Promise.all([
            getStudentResult(examId, studentId),
            getStudentFeedback(examId, studentId),
            ]);

            setResultData(resultRes.data);
            setFeedbackData(feedbackRes.data.feedbacks || []);
        } catch (err) {
            console.error("Error fetching student data", err);
        } finally {
            setLoading(false);
        }
        };

        fetchData();
    }, [examId, studentId]);

    if (loading) return <div className="p-6">Loading...</div>;

    if (!resultData) return <div className="p-6">No data found</div>;

    const {
        examTitle,
        subjectName,
        obtainedMarks,
        totalMarks,
        result = [],
    } = resultData;

    const percentage = ((obtainedMarks / totalMarks) * 100).toFixed(1);

    return (
        <div className="p-6 space-y-6">
        {/* 🔥 HEADER */}
        <div className="bg-white p-5 rounded-xl shadow">
            <h2 className="text-xl font-bold">{examTitle}</h2>
            <p className="text-gray-500">{subjectName}</p>

            <div className="mt-2 flex items-center gap-4">
            <span className="font-semibold">
                Total Marks:{" "}
                <span className="text-green-600">
                {obtainedMarks}/{totalMarks}
                </span>
            </span>

            <span className="bg-green-100 text-green-700 px-3 py-1 rounded-full text-sm">
                COMPLETED
            </span>
            </div>

            {/* 🔥 Progress Bar */}
            <div className="mt-4">
            <div className="w-full bg-gray-200 rounded-full h-3">
                <div
                className="bg-green-500 h-3 rounded-full"
                style={{ width: `${percentage}%` }}
                ></div>
            </div>
            <p className="text-sm mt-1 text-gray-600">{percentage}% Score</p>
            </div>
        </div>

        {/* 🔥 PERFORMANCE CARDS */}
        <div className="grid grid-cols-3 gap-4">
            <div className="bg-white p-4 rounded-xl shadow">
            <p>Total Marks</p>
            <h2 className="text-lg font-bold">
                {obtainedMarks}/{totalMarks}
            </h2>
            </div>

            <div className="bg-white p-4 rounded-xl shadow">
            <p>Percentage</p>
            <h2 className="text-lg font-bold">{percentage}%</h2>
            </div>

            <div className="bg-white p-4 rounded-xl shadow">
            <p>Status</p>
            <h2 className="text-green-600 font-bold">COMPLETED</h2>
            </div>
        </div>

        {/* 🔥 QUESTION TABLE */}
        <div className="bg-white rounded-2xl shadow-md p-6">
            <h3 className="text-lg font-semibold mb-4">Question-wise Marks</h3>

            <div className="overflow-hidden rounded-xl border">
                <table className="w-full text-sm">
                
                {/* HEADER */}
                <thead className="bg-gray-50 text-gray-600">
                    <tr>
                    <th className="text-left px-4 py-3">Question</th>
                    <th className="text-left px-4 py-3">Marks</th>
                    <th className="text-left px-4 py-3">Performance</th>
                    <th className="text-left px-4 py-3">Status</th>
                    </tr>
                </thead>

                {/* BODY */}
                <tbody>
                    {result.map((q, index) => {
                    const percentage = (q.finalMarks / q.maxMarks) * 100;

                    return (
                        <tr
                        key={q.resultId}
                        className="border-t hover:bg-gray-50 transition"
                        >
                        {/* Question */}
                        <td className="px-4 py-3 font-medium">
                            Q{index + 1}
                        </td>

                        {/* Marks */}
                        <td className="px-4 py-3">
                            <span className="font-semibold">
                            {q.finalMarks}
                            </span>
                            <span className="text-gray-500">
                            /{q.maxMarks}
                            </span>
                        </td>

                        {/* Progress Bar */}
                        <td className="px-4 py-3 w-64">
                            <div className="w-full bg-gray-200 rounded-full h-2">
                            <div
                                className={`h-2 rounded-full ${
                                percentage > 70
                                    ? "bg-green-500"
                                    : percentage > 40
                                    ? "bg-yellow-500"
                                    : "bg-red-500"
                                }`}
                                style={{ width: `${percentage}%` }}
                            ></div>
                            </div>
                        </td>

                        {/* Status */}
                        <td className="px-4 py-3">
                            <span className="bg-green-100 text-green-700 px-3 py-1 rounded-full text-xs font-semibold">
                            {q.status}
                            </span>
                        </td>
                        </tr>
                    );
                    })}
                </tbody>
                </table>
            </div>
            </div>

        {/* 🔥 FEEDBACK SECTION */}
        <div className="space-y-4">
            {feedbackData.length === 0 ? (
            <div className="text-gray-500">No feedback available</div>
            ) : (
            feedbackData.map((f, index) => (
                <div key={index} className="bg-white p-5 rounded-xl shadow">
                {/* Clickable header */}
                <div
                    className="cursor-pointer font-semibold text-lg"
                    onClick={() =>
                    setOpenIndex(index === openIndex ? null : index)
                    }
                >
                    Question {index + 1}
                </div>

                {/* Expand */}
                {openIndex === index && (
                    <div className="mt-3 space-y-2">
                    <p className="text-green-700">
                        <b>Strength:</b> {f.strengths}
                    </p>

                    <p className="text-red-500">
                        <b>Weakness:</b> {f.weakness}
                    </p>

                    <p className="text-blue-500">
                        <b>Suggestion:</b> {f.suggestions}
                    </p>

                    <div>
                        <b>Missed Concepts:</b>
                        <ul className="list-disc ml-5">
                        {(f.keyConceptsMissed || []).map((c, i) => (
                            <li key={i}>{c}</li>
                        ))}
                        </ul>
                    </div>
                    </div>
                )}
                </div>
            ))
            )}
        </div>
        </div>
    );
}