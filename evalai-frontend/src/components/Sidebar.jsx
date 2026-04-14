const menuItems = [
    "Exams",
    "Results",
    "Analytics",
    "Grievances",
    "Answer Sheets",
    "Students",
    "Faculties",
    "Evaluation",
];

export default function Sidebar() {
    return (
        <div className="w-64 h-screen bg-linear-to-b from-[#6366f1] to-[#8b5cf6] text-white p-5">

        {/* Menu */}
        <div className="space-y-4">
            {menuItems.map((item) => (
            <div
                key={item}
                className="cursor-pointer hover:opacity-80 transition"
            >
                {item}
            </div>
            ))}
        </div>
        </div>
    );
}