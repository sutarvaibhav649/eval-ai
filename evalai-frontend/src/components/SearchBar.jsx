export default function SearchBar() {
    return (
        <div className="flex gap-4 mb-6">
        <input
            placeholder="Year"
            className="border px-3 py-2 rounded-md"
        />
        <input
            placeholder="Exam"
            className="border px-3 py-2 rounded-md flex-1"
        />
        <button className="bg-primary text-white px-5 py-2 rounded-md">
            Search
        </button>
        </div>
    );
}