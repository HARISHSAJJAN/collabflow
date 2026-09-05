import { useState, useRef, useEffect } from "react";
import { Link, NavLink, Outlet, useParams } from "react-router-dom";
import { ChevronDown, LayoutGrid, LogOut, Moon, Plus, Sun, Users } from "lucide-react";
import { useThemeStore } from "../../store/themeStore";
import { useCurrentUser, useLogout } from "../../hooks/useAuth";
import { useTeams } from "../../hooks/useTeams";
import { Avatar } from "../ui/Avatar";
import { NotificationBell } from "./NotificationBell";
import { CreateTeamModal } from "../teams/CreateTeamModal";
import { cn } from "../../lib/cn";

function UserMenu() {
  const { data: user } = useCurrentUser();
  const logout = useLogout();
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const onClickOutside = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener("mousedown", onClickOutside);
    return () => document.removeEventListener("mousedown", onClickOutside);
  }, []);

  if (!user) return null;

  return (
    <div className="relative" ref={ref}>
      <button onClick={() => setOpen((o) => !o)} className="flex items-center gap-2 rounded-lg p-1 pr-2 transition-colors hover:bg-[var(--bg-surface-2)]">
        <Avatar name={user.fullName} size="sm" />
        <ChevronDown className="size-3.5 text-[var(--text-tertiary)]" />
      </button>
      {open && (
        <div className="absolute right-0 top-11 z-40 w-52 overflow-hidden rounded-xl border border-[var(--border-subtle)] bg-[var(--bg-surface)] shadow-xl">
          <div className="border-b border-[var(--border-subtle)] px-3.5 py-3">
            <p className="truncate text-sm font-semibold">{user.fullName}</p>
            <p className="truncate text-xs text-[var(--text-tertiary)]">{user.email}</p>
          </div>
          <button
            onClick={logout}
            className="flex w-full items-center gap-2 px-3.5 py-2.5 text-sm text-[var(--text-secondary)] transition-colors hover:bg-[var(--bg-surface-2)] hover:text-red-500"
          >
            <LogOut className="size-4" /> Sign out
          </button>
        </div>
      )}
    </div>
  );
}

function Sidebar() {
  const { data: teams } = useTeams();
  const { teamId: activeTeamId } = useParams();
  const [createOpen, setCreateOpen] = useState(false);

  return (
    <aside className="flex w-64 shrink-0 flex-col border-r border-[var(--border-subtle)] bg-[var(--bg-surface)]">
      <div className="flex items-center gap-2 px-5 py-5">
        <div className="flex size-8 items-center justify-center rounded-lg bg-brand-600 font-bold text-white">C</div>
        <span className="text-lg font-bold tracking-tight">CollabFlow</span>
      </div>

      <nav className="px-3">
        <NavLink
          to="/"
          end
          className={({ isActive }) =>
            cn(
              "flex items-center gap-2.5 rounded-lg px-3 py-2 text-sm font-medium transition-colors",
              isActive ? "bg-brand-50 text-brand-700 dark:bg-brand-900/30 dark:text-brand-300" : "text-[var(--text-secondary)] hover:bg-[var(--bg-surface-2)]",
            )
          }
        >
          <LayoutGrid className="size-4" /> My tasks
        </NavLink>
      </nav>

      <div className="mt-6 flex items-center justify-between px-5">
        <span className="text-xs font-semibold uppercase tracking-wider text-[var(--text-tertiary)]">Teams</span>
        <button onClick={() => setCreateOpen(true)} className="rounded p-0.5 text-[var(--text-tertiary)] hover:bg-[var(--bg-surface-2)] hover:text-[var(--text-primary)]" aria-label="Create team">
          <Plus className="size-4" />
        </button>
      </div>

      <div className="mt-1 flex-1 space-y-0.5 overflow-y-auto scrollbar-thin px-3 pb-4">
        {teams?.content.length === 0 && <p className="px-3 py-2 text-xs text-[var(--text-tertiary)]">No teams yet - create one to get started.</p>}
        {teams?.content.map((team) => (
          <Link
            key={team.id}
            to={`/teams/${team.id}`}
            className={cn(
              "flex items-center gap-2.5 rounded-lg px-3 py-2 text-sm font-medium transition-colors",
              activeTeamId === team.id
                ? "bg-brand-50 text-brand-700 dark:bg-brand-900/30 dark:text-brand-300"
                : "text-[var(--text-secondary)] hover:bg-[var(--bg-surface-2)]",
            )}
          >
            <Users className="size-4 shrink-0" />
            <span className="truncate">{team.name}</span>
          </Link>
        ))}
      </div>

      <CreateTeamModal open={createOpen} onClose={() => setCreateOpen(false)} />
    </aside>
  );
}

export function AppShell() {
  const { theme, toggle } = useThemeStore();

  return (
    <div className="flex h-screen overflow-hidden bg-[var(--bg-app)]">
      <Sidebar />
      <div className="flex flex-1 flex-col overflow-hidden">
        <header className="flex h-16 shrink-0 items-center justify-end gap-2 border-b border-[var(--border-subtle)] bg-[var(--bg-surface)] px-6">
          <button
            onClick={toggle}
            className="rounded-lg p-2 text-[var(--text-secondary)] transition-colors hover:bg-[var(--bg-surface-2)] hover:text-[var(--text-primary)]"
            aria-label="Toggle theme"
          >
            {theme === "dark" ? <Sun className="size-5" /> : <Moon className="size-5" />}
          </button>
          <NotificationBell />
          <div className="mx-1 h-6 w-px bg-[var(--border-subtle)]" />
          <UserMenu />
        </header>
        <main className="flex-1 overflow-y-auto scrollbar-thin">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
