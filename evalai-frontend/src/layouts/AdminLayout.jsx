import Sidebar from "../components/Sidebar";
import Topbar from "../components/Topbar";

export default function AdminLayout({ children }) {
    return (
        <div className="h-screen flex flex-col">
        
        {/* Topbar (FULL WIDTH) */}
        <Topbar />

        {/* Bottom Section */}
        <div className="flex flex-1 overflow-hidden">
            
            {/* Sidebar */}
            <Sidebar />

            {/* Content */}
            <div className="flex-1 p-6 overflow-auto bg-[#f8fafc]">
            {children}
            </div>

        </div>
        </div>
    );
}