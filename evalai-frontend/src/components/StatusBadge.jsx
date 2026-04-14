export default function StatusBadge({ status }) {
    const styles = {
        COMPLETED: "bg-green-100 text-green-800",
        PENDING: "bg-yellow-100 text-yellow-800",
        FAILED: "bg-red-100 text-red-800",
        PROCESSING: "bg-indigo-100 text-indigo-800",
        QUEUED: "bg-cyan-100 text-cyan-800",
    };

    return (
        <span
        className={`px-3 py-1 rounded-full text-sm font-medium ${styles[status]}`}
        >
        {status}
        </span>
    );
}