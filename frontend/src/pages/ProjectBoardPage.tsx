import { useEffect, useState } from "react";
import { useParams } from "react-router-dom";
import { useQueryClient } from "@tanstack/react-query";
import { Archive, ArchiveRestore, Plus, Search, Users } from "lucide-react";
import { KanbanBoard } from "../components/tasks/KanbanBoard";
import { TaskDetailDrawer } from "../components/tasks/TaskDetailDrawer";
import { CreateTaskModal } from "../components/tasks/CreateTaskModal";
import { ManageProjectMembersModal } from "../components/projects/ManageProjectMembersModal";
import { useProject, useProjectMembers, useArchiveProject } from "../hooks/useProjects";
import { useTasks } from "../hooks/useTasks";
import { Input } from "../components/ui/Input";
import { Button } from "../components/ui/Button";
import { Spinner } from "../components/ui/Badge";
import { AvatarStack } from "../components/ui/Avatar";
import { subscribeToProject } from "../lib/websocket";
import type { TaskResponse } from "../lib/types";

export function ProjectBoardPage() {
  const { projectId } = useParams<{ projectId: string }>();
  const { data: project } = useProject(projectId);
  const { data: members } = useProjectMembers(projectId);
  const [keyword, setKeyword] = useState("");
  const { data: tasksPage, isLoading } = useTasks(projectId, keyword ? { keyword } : {});
  const [selectedTask, setSelectedTask] = useState<TaskResponse | null>(null);
  const [createOpen, setCreateOpen] = useState(false);
  const [membersOpen, setMembersOpen] = useState(false);
  const archiveProject = useArchiveProject();
  const queryClient = useQueryClient();

  // Live updates from teammates working on the same project right now (Phase 12's real-time
  // layer) - a card another user moves/edits invalidates this project's task query, so it
  // reflects here without a manual refresh.
  useEffect(() => {
    if (!projectId) return;
    return subscribeToProject(projectId, () => {
      queryClient.invalidateQueries({ queryKey: ["tasks", projectId] });
    });
  }, [projectId, queryClient]);

  if (!projectId) return null;

  return (
    <div className="flex h-full flex-col">
      <div className="flex items-center justify-between border-b border-[var(--border-subtle)] px-6 py-5">
        <div>
          <div className="flex items-center gap-2">
            <h1 className="text-xl font-bold text-[var(--text-primary)]">{project?.name ?? "Loading..."}</h1>
            {project?.status === "ARCHIVED" && (
              <span className="rounded-full bg-slate-100 px-2 py-0.5 text-xs font-medium text-slate-600 dark:bg-slate-800 dark:text-slate-300">Archived</span>
            )}
          </div>
          {project?.description && <p className="mt-0.5 text-sm text-[var(--text-secondary)]">{project.description}</p>}
        </div>
        <div className="flex items-center gap-3">
          {members && members.length > 0 && <AvatarStack names={members.map((m) => m.fullName)} />}
          <Button variant="secondary" size="sm" onClick={() => setMembersOpen(true)}>
            <Users className="size-4" /> Members
          </Button>
          <Button
            variant="secondary"
            size="sm"
            onClick={() => project && archiveProject.mutate({ projectId, archive: project.status === "ACTIVE" })}
          >
            {project?.status === "ACTIVE" ? <Archive className="size-4" /> : <ArchiveRestore className="size-4" />}
            {project?.status === "ACTIVE" ? "Archive" : "Restore"}
          </Button>
          <Button size="sm" onClick={() => setCreateOpen(true)}>
            <Plus className="size-4" /> New task
          </Button>
        </div>
      </div>

      <div className="flex items-center gap-3 px-6 py-4">
        <div className="relative w-72">
          <Search className="absolute left-3 top-1/2 size-4 -translate-y-1/2 text-[var(--text-tertiary)]" />
          <Input placeholder="Search tasks..." className="pl-9" value={keyword} onChange={(e) => setKeyword(e.target.value)} />
        </div>
      </div>

      {isLoading ? (
        <Spinner className="flex-1 py-24" />
      ) : (
        <div className="flex-1 overflow-hidden">
          <KanbanBoard
            projectId={projectId}
            tasks={tasksPage?.content ?? []}
            members={members}
            onTaskClick={setSelectedTask}
            onAddTask={() => setCreateOpen(true)}
          />
        </div>
      )}

      <TaskDetailDrawer taskId={selectedTask?.id ?? null} projectId={projectId} onClose={() => setSelectedTask(null)} />
      <CreateTaskModal open={createOpen} onClose={() => setCreateOpen(false)} projectId={projectId} />
      {project && (
        <ManageProjectMembersModal open={membersOpen} onClose={() => setMembersOpen(false)} projectId={projectId} teamId={project.teamId} />
      )}
    </div>
  );
}
