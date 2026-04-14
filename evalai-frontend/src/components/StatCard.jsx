export default function StatCard({ title, value, gradient }) {
    return (
        <div
            className={`flex-1 text-white p-6 rounded-xl shadow-md ${gradient} justify-center items-center`}
        >
        <h2 className="text-2xl font-bold m-auto">{value}</h2>
        <p className="mt-1">{title}</p>
        </div>
    );
}