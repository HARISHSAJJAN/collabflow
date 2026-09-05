import type { ReactNode } from "react";
import { motion } from "framer-motion";
import { CheckCircle2, KanbanSquare, MessageSquare, Zap } from "lucide-react";

const features = [
  { icon: KanbanSquare, text: "Drag-and-drop Kanban boards per project" },
  { icon: Zap, text: "Real-time updates the moment a teammate moves a card" },
  { icon: MessageSquare, text: "Threaded comments with @mentions" },
  { icon: CheckCircle2, text: "Role-based access across teams and projects" },
];

export function AuthLayout({ children }: { children: ReactNode }) {
  return (
    <div className="flex min-h-screen">
      <div className="relative hidden w-1/2 flex-col justify-between overflow-hidden bg-gradient-to-br from-brand-700 via-brand-600 to-indigo-700 p-12 text-white lg:flex">
        <div className="absolute -right-24 -top-24 size-96 rounded-full bg-white/10 blur-3xl" />
        <div className="absolute -bottom-32 -left-16 size-80 rounded-full bg-black/10 blur-3xl" />

        <div className="relative flex items-center gap-2.5">
          <div className="flex size-9 items-center justify-center rounded-lg bg-white/20 text-lg font-bold backdrop-blur">C</div>
          <span className="text-xl font-bold tracking-tight">CollabFlow</span>
        </div>

        <div className="relative">
          <motion.h2 initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.4 }} className="text-3xl font-bold leading-tight">
            Everything your team needs to ship, in one place.
          </motion.h2>
          <div className="mt-8 flex flex-col gap-4">
            {features.map((f, i) => (
              <motion.div
                key={f.text}
                initial={{ opacity: 0, x: -12 }}
                animate={{ opacity: 1, x: 0 }}
                transition={{ duration: 0.35, delay: 0.1 + i * 0.08 }}
                className="flex items-center gap-3"
              >
                <div className="flex size-9 shrink-0 items-center justify-center rounded-lg bg-white/15 backdrop-blur">
                  <f.icon className="size-4.5" />
                </div>
                <span className="text-sm text-white/90">{f.text}</span>
              </motion.div>
            ))}
          </div>
        </div>

        <p className="relative text-xs text-white/60">A modular-monolith portfolio project — Spring Boot, PostgreSQL, Redis, Kafka, WebSockets.</p>
      </div>

      <div className="flex w-full flex-col items-center justify-center px-6 py-12 lg:w-1/2">
        <div className="w-full max-w-sm">{children}</div>
      </div>
    </div>
  );
}
