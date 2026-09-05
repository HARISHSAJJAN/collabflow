import { useState } from "react";
import { Link } from "react-router-dom";
import { motion } from "framer-motion";
import { Input } from "../components/ui/Input";
import { Button } from "../components/ui/Button";
import { useLogin } from "../hooks/useAuth";
import { AuthLayout } from "../components/layout/AuthLayout";

export function LoginPage() {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const login = useLogin();

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    login.mutate({ email, password });
  };

  return (
    <AuthLayout>
      <motion.div initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.3 }}>
        <h1 className="text-2xl font-bold tracking-tight">Welcome back</h1>
        <p className="mt-1.5 text-sm text-[var(--text-secondary)]">Sign in to pick up where you left off.</p>

        <form onSubmit={handleSubmit} className="mt-8 flex flex-col gap-4">
          <Input label="Email" type="email" autoComplete="email" placeholder="you@example.com" value={email} onChange={(e) => setEmail(e.target.value)} required autoFocus />
          <Input label="Password" type="password" autoComplete="current-password" placeholder="••••••••" value={password} onChange={(e) => setPassword(e.target.value)} required />
          <Button type="submit" size="lg" className="mt-2 w-full" loading={login.isPending}>
            Sign in
          </Button>
        </form>

        <p className="mt-6 text-center text-sm text-[var(--text-secondary)]">
          New to CollabFlow?{" "}
          <Link to="/register" className="font-semibold text-brand-600 hover:text-brand-700">
            Create an account
          </Link>
        </p>
      </motion.div>
    </AuthLayout>
  );
}
