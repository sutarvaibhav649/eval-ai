import { useState } from "react";
import { useNavigate } from "react-router-dom";
import axios from "axios";

export default function Login() {
    const [email, setEmail] = useState("");
    const [password, setPassword] = useState("");
    const navigate = useNavigate();

    const handleLogin = async (e) => {
        e.preventDefault();

        try {
        const res = await axios.post(
            "http://localhost:8081/auth/login",
            {
                email,
                password,
            }
        );

        const { accessToken, role } = res.data;

        console.log(res);

        // 🔐 Store token
        localStorage.setItem("token", accessToken);
        localStorage.setItem("role", role);

        // 🚀 Redirect based on role
        if (role === "ADMIN") navigate("/admin");
        else if (role === "STUDENT") navigate("/student");
        else if (role === "FACULTY") navigate("/faculty");

        } catch (err) {
        console.error("Login failed", err);
        alert("Invalid credentials");
        }
    };

    return (
        <div className="h-screen flex items-center justify-center bg-gray-100">
        <form
            onSubmit={handleLogin}
            className="bg-white p-8 rounded-xl shadow-md w-80"
        >
            <h2 className="text-xl font-bold mb-6 text-center">Login</h2>

            <input
            type="email"
            placeholder="Email"
            className="w-full mb-4 p-2 border rounded"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            />

            <input
            type="password"
            placeholder="Password"
            className="w-full mb-4 p-2 border rounded"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            />

            <button className="w-full bg-blue-600 text-white py-2 rounded hover:bg-blue-700">
            Login
            </button>
        </form>
        </div>
    );
}