import { useState } from "react";
import { Modal } from "../ui/Modal";
import { Input, Select, Textarea } from "../ui/Input";
import { Button } from "../ui/Button";
import { useCreateTask } from "../../hooks/useTasks";
import { useProjectMembers } from "../../hooks/useProjects";
import { PRIORITY_META } from "../../lib/display";
import { TASK_PRIORITIES } from "../../lib/types";
import type { TaskPriority } from "../../lib/types";

export function CreateTaskModal({ open, onClose, projectId }: { open: boolean; onClose: () => void; projectId: string }) {
  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [priority, setPriority] = useState<TaskPriority>("MEDIUM");
  const [dueDate, setDueDate] = useState("");
  const [assigneeId, setAssigneeId] = useState("");
  const createTask = useCreateTask(projectId);
  const { data: members } = useProjectMembers(projectId);

  const reset = () => {
    setTitle("");
    setDescription("");
    setPriority("MEDIUM");
    setDueDate("");
    setAssigneeId("");
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    createTask.mutate(
      { projectId, title, description: description || undefined, priority, dueDate: dueDate || null, assigneeId: assigneeId || null },
      {
        onSuccess: () => {
          reset();
          onClose();
        },
      },
    );
  };

  return (
    <Modal open={open} onClose={onClose} title="Create a task">
      <form onSubmit={handleSubmit} className="flex flex-col gap-4">
        <Input label="Title" placeholder="e.g. Fix login redirect bug" value={title} onChange={(e) => setTitle(e.target.value)} required autoFocus />
        <Textarea label="Description" placeholder="Optional details..." rows={3} value={description} onChange={(e) => setDescription(e.target.value)} />
        <div className="grid grid-cols-2 gap-4">
          <Select label="Priority" value={priority} onChange={(e) => setPriority(e.target.value as TaskPriority)}>
            {TASK_PRIORITIES.map((p) => (
              <option key={p} value={p}>
                {PRIORITY_META[p].label}
              </option>
            ))}
          </Select>
          <Input label="Due date" type="date" value={dueDate} onChange={(e) => setDueDate(e.target.value)} />
        </div>
        <Select label="Assignee" value={assigneeId} onChange={(e) => setAssigneeId(e.target.value)}>
          <option value="">Unassigned</option>
          {members?.map((m) => (
            <option key={m.userId} value={m.userId}>
              {m.fullName}
            </option>
          ))}
        </Select>
        <div className="mt-1 flex justify-end gap-2">
          <Button type="button" variant="ghost" onClick={onClose}>
            Cancel
          </Button>
          <Button type="submit" loading={createTask.isPending} disabled={!title.trim()}>
            Create task
          </Button>
        </div>
      </form>
    </Modal>
  );
}
