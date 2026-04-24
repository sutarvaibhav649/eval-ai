import { NavLink, useNavigate } from "react-router-dom";

const menuItems = [
    { label: "Dashboard", path: "/admin" },
    { label: "Create Subject", path: "/admin/create-subject" },
    { label: "Create Exam", path: "/admin/create-exam" },
    { label: "Upload Answer Sheets", path: "/admin/upload-sheets" },
    { label: "Trigger Pipeline", path: "/admin/pipeline" },
    { label: "Grievances", path: "/admin/grievances" },
];

export default function Sidebar() {
    const navigate = useNavigate();

    const handleLogout = () => {
        localStorage.removeItem("token");
        localStorage.removeItem("role");
        navigate("/login");
    };

    return (
        <div className="w-64 h-full bg-gradient-to-b from-[#6366f1] to-[#8b5cf6] text-white flex flex-col">
            {/* Menu */}
            <nav className="flex-1 p-5 space-y-1">
                {menuItems.map((item) => (
                    <NavLink
                        key={item.path}
                        to={item.path}
                        end={item.path === "/admin"}
                        className={({ isActive }) =>
                            `block px-4 py-2.5 rounded-lg text-sm font-medium transition-all duration-150 ${
                                isActive
                                    ? "bg-white/20 text-white shadow-inner"
                                    : "text-white/80 hover:bg-white/10 hover:text-white"
                            }`
                        }
                    >
                        {item.label}
                    </NavLink>
                ))}
            </nav>

            {/* Logout */}
            <div className="p-5 border-t border-white/20">
                <button
                    onClick={handleLogout}
                    className="w-full px-4 py-2.5 rounded-lg text-sm font-medium text-white/80 hover:bg-white/10 hover:text-white transition-all text-left"
                >
                    Logout
                </button>
            </div>
        </div>
    );
}