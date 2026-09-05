import { useDroppable } from "@dnd-kit/core";
import { Plus } from "lucide-react";
import { STATUS_META } from "../../lib/display";
import { TaskCard } from "./TaskCard";
import { cn } from "../../lib/cn";
import type { ProjectMemberResponse, TaskResponse, TaskStatus } from "../../lib/types";

export function KanbanColumn({
  status,
  tasks,
  members,
  onTaskClick,
  onAddTask,
}: {
  status: TaskStatus;
  tasks: TaskResponse[];
  members?: ProjectMemberResponse[];
  onTaskClick: (task: TaskResponse) => void;
  onAddTask: () => void;
}) {
  const { setNodeRef, isOver } = useDroppable({ id: status });
  const meta = STATUS_META[status];

  return (
    <div className="flex w-72 shrink-0 flex-col">
      <div className="flex items-center justify-between px-1 pb-3">
        <div className="flex items-center gap-2">
          <span className={cn("size-2 rounded-full", meta.dot)} />
          <span className="text-sm font-semibold text-[var(--text-primary)]">{meta.label}</span>
          <span className="rounded-full bg-[var(--bg-surface-2)] px-1.5 py-0.5 text-xs font-medium text-[var(--text-tertiary)]">{tasks.length}</span>
        </div>
        {status === "TODO" && (
          <button onClick={onAddTask} className="rounded p-0.5 text-[var(--text-tertiary)] hover:bg-[var(--bg-surface-2)] hover:text-[var(--text-primary)]" aria-label="Add task">
            <Plus className="size-4" />
          </button>
        )}
      </div>
      <div
        ref={setNodeRef}
        data-column={status}
        className={cn(
          "flex min-h-24 flex-1 flex-col gap-2.5 overflow-y-auto scrollbar-thin rounded-xl border-2 border-dashed p-2 transition-colors",
          isOver ? "border-brand-400 bg-brand-50/50 dark:bg-brand-900/10" : "border-transparent",
        )}
      >
        {tasks.map((task) => (
          <TaskCard key={task.id} task={task} members={members} onClick={() => onTaskClick(task)} />
        ))}
        {tasks.length === 0 && !isOver && <p className="px-2 py-4 text-center text-xs text-[var(--text-tertiary)]">No tasks</p>}
      </div>
    </div>
  );
}
