import type { NotificationType, TaskPriority, TaskStatus, TeamRole } from "./types";

export const STATUS_META: Record<TaskStatus, { label: string; dot: string }> = {
  TODO: { label: "To Do", dot: "bg-slate-400" },
  IN_PROGRESS: { label: "In Progress", dot: "bg-blue-500" },
  REVIEW: { label: "In Review", dot: "bg-amber-500" },
  DONE: { label: "Done", dot: "bg-emerald-500" },
  BLOCKED: { label: "Blocked", dot: "bg-red-500" },
};

export const PRIORITY_META: Record<TaskPriority, { label: string; className: string }> = {
  LOW: { label: "Low", className: "bg-slate-100 text-slate-600 dark:bg-slate-800 dark:text-slate-300" },
  MEDIUM: { label: "Medium", className: "bg-blue-100 text-blue-700 dark:bg-blue-900/50 dark:text-blue-300" },
  HIGH: { label: "High", className: "bg-amber-100 text-amber-700 dark:bg-amber-900/50 dark:text-amber-300" },
  CRITICAL: { label: "Critical", className: "bg-red-100 text-red-700 dark:bg-red-900/50 dark:text-red-300" },
};

export const ROLE_META: Record<TeamRole, { label: string; className: string }> = {
  OWNER: { label: "Owner", className: "bg-brand-100 text-brand-700 dark:bg-brand-900/50 dark:text-brand-300" },
  ADMIN: { label: "Admin", className: "bg-blue-100 text-blue-700 dark:bg-blue-900/50 dark:text-blue-300" },
  MEMBER: { label: "Member", className: "bg-slate-100 text-slate-600 dark:bg-slate-800 dark:text-slate-300" },
};

export const NOTIFICATION_COPY: Record<NotificationType, string> = {
  TASK_ASSIGNED: "assigned you a task",
  TASK_STATUS_CHANGED: "changed a task's status",
  COMMENT_ADDED: "commented on a task",
  TEAM_MEMBER_ADDED: "added you to a team",
  TASK_MENTION: "mentioned you in a comment",
  DUE_DATE_APPROACHING: "A task is due soon",
};

export function initials(name: string): string {
  const parts = name.trim().split(/\s+/);
  if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase();
  return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
}

// Deterministic per-name color instead of random, so the same person's avatar is always the
// same color across the app (and across reloads) without needing to store a color anywhere.
const AVATAR_PALETTE = [
  "bg-rose-500",
  "bg-orange-500",
  "bg-amber-500",
  "bg-emerald-500",
  "bg-teal-500",
  "bg-blue-500",
  "bg-indigo-500",
  "bg-violet-500",
  "bg-fuchsia-500",
];

export function avatarColor(seed: string): string {
  let hash = 0;
  for (let i = 0; i < seed.length; i++) hash = (hash * 31 + seed.charCodeAt(i)) | 0;
  return AVATAR_PALETTE[Math.abs(hash) % AVATAR_PALETTE.length];
}

export function relativeTime(iso: string): string {
  const date = new Date(iso);
  const seconds = Math.round((date.getTime() - Date.now()) / 1000);
  const divisions: [number, Intl.RelativeTimeFormatUnit][] = [
    [60, "seconds"],
    [60, "minutes"],
    [24, "hours"],
    [7, "days"],
    [4.34524, "weeks"],
    [12, "months"],
    [Number.POSITIVE_INFINITY, "years"],
  ];
  const rtf = new Intl.RelativeTimeFormat("en", { numeric: "auto" });
  let duration = seconds;
  for (const [amount, unit] of divisions) {
    if (Math.abs(duration) < amount) return rtf.format(Math.round(duration), unit);
    duration /= amount;
  }
  return rtf.format(Math.round(duration), "years");
}
