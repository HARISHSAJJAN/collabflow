import type { ReactNode } from "react";
import { cn } from "../../lib/cn";

export function Badge({ children, className }: { children: ReactNode; className?: string }) {
  return <span className={cn("inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium", className)}>{children}</span>;
}

export function Spinner({ className }: { className?: string }) {
  return (
    <div className={cn("flex items-center justify-center", className)}>
      <div className="size-6 animate-spin rounded-full border-2 border-[var(--border-subtle)] border-t-brand-600" />
    </div>
  );
}

export function Card({ children, className }: { children: ReactNode; className?: string }) {
  return (
    <div
      className={cn(
        "rounded-xl border border-[var(--border-subtle)] bg-[var(--bg-surface)] shadow-sm",
        className,
      )}
    >
      {children}
    </div>
  );
}

export function EmptyState({ icon, title, description, action }: { icon?: ReactNode; title: string; description?: string; action?: ReactNode }) {
  return (
    <div className="flex flex-col items-center justify-center gap-3 rounded-xl border border-dashed border-[var(--border-subtle)] px-6 py-16 text-center">
      {icon && <div className="text-[var(--text-tertiary)]">{icon}</div>}
      <div>
        <p className="font-semibold text-[var(--text-primary)]">{title}</p>
        {description && <p className="mt-1 max-w-sm text-sm text-[var(--text-secondary)]">{description}</p>}
      </div>
      {action}
    </div>
  );
}
