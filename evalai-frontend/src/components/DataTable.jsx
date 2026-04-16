import StatusBadge from "./StatusBadge";
import { useNavigate } from "react-router-dom";

export default function DataTable({ data }) {
    const navigate = useNavigate();

    return (
        <div className="bg-white rounded-xl shadow-md overflow-hidden">
        <table className="w-full text-left">
            <thead className="border-b">
            <tr>
                <th className="p-3">Exam</th>
                <th>Year</th>
                <th>Total Students</th>
                <th>Evaluated</th>
                <th>Status</th>
                <th>Action</th>
            </tr>
            </thead>

            <tbody>
            {data.length === 0 ? (
                <tr>
                <td
                    colSpan="6"
                    className="text-center py-4 text-gray-500"
                >
                    No data available
                </td>
                </tr>
            ) : (
                data.map((row, index) => (
                <tr
                    key={row.examId || index}
                    className="border-b hover:bg-gray-50"
                >
                    <td className="p-3">{row.exam}</td>
                    <td>{row.year}</td>
                    <td>{row.students}</td>
                    <td>{row.evaluated}</td>
                    <td>
                    <StatusBadge status={row.status} />
                    </td>
                    <td>
                    <button
                        className="text-green-600 font-semibold border rounded-xl px-3 py-1 hover:bg-green-50 transition"
                        onClick={() => {
                        if (!row.examId) return;
                            navigate(`/admin/exam/${row.examId}`);
                        }}
                    >
                        View
                    </button>
                    </td>
                </tr>
                ))
            )}
            </tbody>
        </table>
        </div>
    );
}