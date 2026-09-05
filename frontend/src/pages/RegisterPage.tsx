import { useState } from "react";
import { Link } from "react-router-dom";
import { motion } from "framer-motion";
import { Input } from "../components/ui/Input";
import { Button } from "../components/ui/Button";
import { useRegister } from "../hooks/useAuth";
import { AuthLayout } from "../components/layout/AuthLayout";

export function RegisterPage() {
  const [fullName, setFullName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const register = useRegister();

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    register.mutate({ fullName, email, password });
  };

  return (
    <AuthLayout>
      <motion.div initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.3 }}>
        <h1 className="text-2xl font-bold tracking-tight">Create your account</h1>
        <p className="mt-1.5 text-sm text-[var(--text-secondary)]">Start organizing your team's work in minutes.</p>

        <form onSubmit={handleSubmit} className="mt-8 flex flex-col gap-4">
          <Input label="Full name" autoComplete="name" placeholder="Ada Lovelace" value={fullName} onChange={(e) => setFullName(e.target.value)} required autoFocus />
          <Input label="Email" type="email" autoComplete="email" placeholder="you@example.com" value={email} onChange={(e) => setEmail(e.target.value)} required />
          <Input
            label="Password"
            type="password"
            autoComplete="new-password"
            placeholder="At least 8 characters"
            hint="8-128 characters."
            minLength={8}
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
          />
          <Button type="submit" size="lg" className="mt-2 w-full" loading={register.isPending}>
            Create account
          </Button>
        </form>

        <p className="mt-6 text-center text-sm text-[var(--text-secondary)]">
          Already have an account?{" "}
          <Link to="/login" className="font-semibold text-brand-600 hover:text-brand-700">
            Sign in
          </Link>
        </p>
      </motion.div>
    </AuthLayout>
  );
}
