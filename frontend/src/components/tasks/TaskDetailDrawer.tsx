import { useEffect, useState } from "react";
import { createPortal } from "react-dom";
import { AnimatePresence, motion } from "framer-motion";
import { Calendar, Tag, Trash2, X } from "lucide-react";
import { useDeleteTask, useTask, useTaskLabels, useUpdateTask } from "../../hooks/useTasks";
import { useProjectMembers } from "../../hooks/useProjects";
import { CommentThread } from "../comments/CommentThread";
import { Select, Textarea } from "../ui/Input";
import { Badge, Spinner } from "../ui/Badge";
import { Avatar } from "../ui/Avatar";
import { PRIORITY_META, STATUS_META } from "../../lib/display";
import { useChangeTaskStatus, useAssignTask } from "../../hooks/useTasks";
import type { TaskPriority, TaskStatus } from "../../lib/types";
import { TASK_PRIORITIES, TASK_STATUSES } from "../../lib/types";

export function TaskDetailDrawer({ taskId, projectId, onClose }: { taskId: string | null; projectId: string; onClose: () => void }) {
  const { data: task, isLoading } = useTask(taskId ?? undefined);
  const { data: members } = useProjectMembers(projectId);
  const updateTask = useUpdateTask(projectId);
  const changeStatus = useChangeTaskStatus(projectId);
  const assignTask = useAssignTask(projectId);
  const deleteTask = useDeleteTask(projectId);
  const { addLabel, removeLabel } = useTaskLabels(projectId, taskId ?? "");

  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [labelDraft, setLabelDraft] = useState("");

  useEffect(() => {
    if (task) {
      setTitle(task.title);
      setDescription(task.description ?? "");
    }
  }, [task]);

  // Matches Modal's own Escape-to-close and body-scroll-lock behavior - found missing here by
  // an automated smoke-test run (Phase "make the frontend impressive"): without it, the drawer
  // stayed open and its full-screen backdrop kept intercepting clicks on everything behind it,
  // with no keyboard way to dismiss it.
  useEffect(() => {
    if (!taskId) return;
    const onKey = (e: KeyboardEvent) => e.key === "Escape" && onClose();
    document.addEventListener("keydown", onKey);
    document.body.style.overflow = "hidden";
    return () => {
      document.removeEventListener("keydown", onKey);
      document.body.style.overflow = "";
    };
  }, [taskId, onClose]);

  const open = !!taskId;

  const saveTitleOrDescription = () => {
    if (!task || (title === task.title && description === (task.description ?? ""))) return;
    updateTask.mutate({ taskId: task.id, body: { title, description: description || undefined, priority: task.priority, dueDate: task.dueDate } });
  };

  const handleAddLabel = (e: React.FormEvent) => {
    e.preventDefault();
    if (!labelDraft.trim()) return;
    addLabel.mutate(labelDraft.trim().toLowerCase(), { onSuccess: () => setLabelDraft("") });
  };

  return createPortal(
    <AnimatePresence>
      {open && (
        <div className="fixed inset-0 z-50 flex justify-end">
          <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} className="absolute inset-0 bg-black/30 backdrop-blur-[1px]" onClick={onClose} />
          <motion.div
            initial={{ x: "100%" }}
            animate={{ x: 0 }}
            exit={{ x: "100%" }}
            transition={{ duration: 0.25, ease: [0.16, 1, 0.3, 1] }}
            className="relative flex h-full w-full max-w-xl flex-col border-l border-[var(--border-subtle)] bg-[var(--bg-surface)] shadow-2xl"
          >
            <div className="flex items-center justify-between border-b border-[var(--border-subtle)] px-6 py-4">
              <span className="text-xs font-medium uppercase tracking-wider text-[var(--text-tertiary)]">Task details</span>
              <div className="flex items-center gap-1">
                {task && (
                  <button
                    onClick={() => {
                      if (confirm("Delete this task? This cannot be undone.")) deleteTask.mutate(task.id, { onSuccess: onClose });
                    }}
                    className="rounded-lg p-1.5 text-[var(--text-tertiary)] hover:bg-red-50 hover:text-red-500 dark:hover:bg-red-900/20"
                  >
                    <Trash2 className="size-4" />
                  </button>
                )}
                <button onClick={onClose} className="rounded-lg p-1.5 text-[var(--text-tertiary)] hover:bg-[var(--bg-surface-2)] hover:text-[var(--text-primary)]">
                  <X className="size-4" />
                </button>
              </div>
            </div>

            <div className="flex-1 overflow-y-auto scrollbar-thin px-6 py-5">
              {isLoading || !task ? (
                <Spinner className="py-16" />
              ) : (
                <div className="flex flex-col gap-5">
                  <textarea
                    value={title}
                    onChange={(e) => setTitle(e.target.value)}
                    onBlur={saveTitleOrDescription}
                    rows={1}
                    className="resize-none border-none bg-transparent text-xl font-bold text-[var(--text-primary)] outline-none"
                  />

                  <div className="grid grid-cols-2 gap-4">
                    <Select
                      label="Status"
                      value={task.status}
                      onChange={(e) => changeStatus.mutate({ taskId: task.id, status: e.target.value as TaskStatus })}
                    >
                      {TASK_STATUSES.map((s) => (
                        <option key={s} value={s}>
                          {STATUS_META[s].label}
                        </option>
                      ))}
                    </Select>
                    <Select
                      label="Priority"
                      value={task.priority}
                      onChange={(e) => updateTask.mutate({ taskId: task.id, body: { title: task.title, description: task.description ?? undefined, priority: e.target.value as TaskPriority, dueDate: task.dueDate } })}
                    >
                      {TASK_PRIORITIES.map((p) => (
                        <option key={p} value={p}>
                          {PRIORITY_META[p].label}
                        </option>
                      ))}
                    </Select>
                  </div>

                  <div className="grid grid-cols-2 gap-4">
                    <Select label="Assignee" value={task.assigneeId ?? ""} onChange={(e) => assignTask.mutate({ taskId: task.id, assigneeId: e.target.value || null })}>
                      <option value="">Unassigned</option>
                      {members?.map((m) => (
                        <option key={m.userId} value={m.userId}>
                          {m.fullName}
                        </option>
                      ))}
                    </Select>
                    <div className="flex flex-col gap-1.5">
                      <label className="text-sm font-medium text-[var(--text-secondary)]">Due date</label>
                      <div className="flex h-[42px] items-center gap-2 rounded-lg border border-[var(--border-subtle)] px-3.5 text-sm text-[var(--text-primary)]">
                        <Calendar className="size-4 text-[var(--text-tertiary)]" />
                        {task.dueDate ? new Date(task.dueDate).toLocaleDateString() : "No due date"}
                      </div>
                    </div>
                  </div>

                  {task.assigneeId && (
                    <div className="flex items-center gap-2 text-sm text-[var(--text-secondary)]">
                      <Avatar name={members?.find((m) => m.userId === task.assigneeId)?.fullName ?? "?"} size="xs" />
                      Assigned to {members?.find((m) => m.userId === task.assigneeId)?.fullName}
                    </div>
                  )}

                  <div>
                    <label className="text-sm font-medium text-[var(--text-secondary)]">Description</label>
                    <Textarea
                      className="mt-1.5"
                      value={description}
                      onChange={(e) => setDescription(e.target.value)}
                      onBlur={saveTitleOrDescription}
                      rows={4}
                      placeholder="Add a description..."
                    />
                  </div>

                  <div>
                    <label className="text-sm font-medium text-[var(--text-secondary)]">Labels</label>
                    <div className="mt-1.5 flex flex-wrap items-center gap-1.5">
                      {task.labels.map((label) => (
                        <Badge key={label} className="gap-1 bg-[var(--bg-surface-2)] text-[var(--text-secondary)]">
                          <Tag className="size-2.5" />
                          {label}
                          <button onClick={() => removeLabel.mutate(label)} className="ml-0.5 hover:text-red-500">
                            <X className="size-3" />
                          </button>
                        </Badge>
                      ))}
                      <form onSubmit={handleAddLabel}>
                        <input
                          value={labelDraft}
                          onChange={(e) => setLabelDraft(e.target.value)}
                          placeholder="+ add label"
                          className="w-24 rounded-full border border-dashed border-[var(--border-subtle)] bg-transparent px-2.5 py-0.5 text-xs outline-none focus:border-brand-400"
                        />
                      </form>
                    </div>
                  </div>

                  <div className="border-t border-[var(--border-subtle)] pt-5">
                    <h3 className="mb-3 text-sm font-semibold text-[var(--text-primary)]">Comments</h3>
                    <CommentThread taskId={task.id} />
                  </div>
                </div>
              )}
            </div>
          </motion.div>
        </div>
      )}
    </AnimatePresence>,
    document.body,
  );
}
