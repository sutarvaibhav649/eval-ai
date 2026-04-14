import StatusBadge from "./StatusBadge";

export default function DataTable({ data }) {
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

            <tbody className=" justify-center align-middle">
            {data.map((row, index) => (
                <tr key={index} className="border-b hover:bg-gray-50">
                <td className="p-3">{row.exam}</td>
                <td>{row.year}</td>
                <td>{row.students}</td>
                <td>{row.evaluated}</td>
                <td>
                    <StatusBadge status={row.status} />
                </td>
                <td className="text-primary cursor-pointer">
                    <button className="bg-transparent text-green-600 font-bold border rounded-2xl w-20 cursor-pointer">View</button>
                </td>
                </tr>
            ))}
            </tbody>
        </table>
        </div>
    );
}