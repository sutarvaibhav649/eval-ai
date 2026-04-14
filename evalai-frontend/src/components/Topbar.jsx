export default function Topbar() {
    return (
        <div className="h-14 bg-white border-b border-gray-200 flex items-center justify-between px-6">
        
        {/* LEFT: Logo */}
        <h1 className="text-lg font-bold text-[#000000]">
            Eval<span className=" text-[#6366f1]">AI</span> 
        </h1>

        {/* RIGHT: User */}
        <div className="flex items-center gap-3">
            <span className="text-[#64748b]">Admin</span>

            <div className="bg-[#6366f1] text-white w-8 h-8 flex items-center justify-center rounded-full">
            VS
            </div>
        </div>

        </div>
    );
}