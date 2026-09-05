import { useDraggable } from "@dnd-kit/core";
import { CSS } from "@dnd-kit/utilities";
import { Calendar, Tag } from "lucide-react";
import { PRIORITY_META } from "../../lib/display";
import { Avatar } from "../ui/Avatar";
import { Badge } from "../ui/Badge";
import type { ProjectMemberResponse, TaskResponse } from "../../lib/types";
import { cn } from "../../lib/cn";

function memberName(members: ProjectMemberResponse[] | undefined, userId: string | null): string | null {
  if (!userId) return null;
  return members?.find((m) => m.userId === userId)?.fullName ?? null;
}

function isOverdue(dueDate: string | null, status: string): boolean {
  if (!dueDate || status === "DONE") return false;
  return new Date(dueDate) < new Date(new Date().toDateString());
}

export function TaskCard({ task, members, onClick }: { task: TaskResponse; members?: ProjectMemberResponse[]; onClick: () => void }) {
  const { attributes, listeners, setNodeRef, transform, isDragging } = useDraggable({ id: task.id });
  const assigneeName = memberName(members, task.assigneeId);
  const overdue = isOverdue(task.dueDate, task.status);

  return (
    <div
      ref={setNodeRef}
      {...listeners}
      {...attributes}
      onClick={onClick}
      style={{ transform: CSS.Translate.toString(transform) }}
      className={cn(
        "group cursor-grab select-none rounded-xl border border-[var(--border-subtle)] bg-[var(--bg-surface)] p-3.5 shadow-sm transition-shadow",
        "hover:shadow-md active:cursor-grabbing",
        isDragging && "opacity-40",
      )}
    >
      <div className="flex items-start justify-between gap-2">
        <p className="text-sm font-medium leading-snug text-[var(--text-primary)]">{task.title}</p>
      </div>

      {task.labels.length > 0 && (
        <div className="mt-2 flex flex-wrap gap-1">
          {task.labels.map((label) => (
            <Badge key={label} className="bg-[var(--bg-surface-2)] text-[var(--text-secondary)]">
              <Tag className="mr-1 size-2.5" />
              {label}
            </Badge>
          ))}
        </div>
      )}

      <div className="mt-3 flex items-center justify-between">
        <div className="flex items-center gap-2">
          <Badge className={PRIORITY_META[task.priority].className}>{PRIORITY_META[task.priority].label}</Badge>
          {task.dueDate && (
            <span className={cn("flex items-center gap-1 text-xs", overdue ? "font-medium text-red-500" : "text-[var(--text-tertiary)]")}>
              <Calendar className="size-3" />
              {new Date(task.dueDate).toLocaleDateString(undefined, { month: "short", day: "numeric" })}
            </span>
          )}
        </div>
        {assigneeName && <Avatar name={assigneeName} size="xs" />}
      </div>
    </div>
  );
}
