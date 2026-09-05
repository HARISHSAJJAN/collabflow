import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { Modal } from "../ui/Modal";
import { Input, Textarea } from "../ui/Input";
import { Button } from "../ui/Button";
import { useCreateProject } from "../../hooks/useProjects";

export function CreateProjectModal({ open, onClose, teamId }: { open: boolean; onClose: () => void; teamId: string }) {
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const createProject = useCreateProject();
  const navigate = useNavigate();

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    createProject.mutate(
      { teamId, name, description: description || undefined },
      {
        onSuccess: (project) => {
          setName("");
          setDescription("");
          onClose();
          navigate(`/projects/${project.id}`);
        },
      },
    );
  };

  return (
    <Modal open={open} onClose={onClose} title="Create a project">
      <form onSubmit={handleSubmit} className="flex flex-col gap-4">
        <Input label="Project name" placeholder="e.g. Q3 Mobile Redesign" value={name} onChange={(e) => setName(e.target.value)} required autoFocus />
        <Textarea label="Description" placeholder="What is this project about? (optional)" rows={3} value={description} onChange={(e) => setDescription(e.target.value)} />
        <div className="mt-1 flex justify-end gap-2">
          <Button type="button" variant="ghost" onClick={onClose}>
            Cancel
          </Button>
          <Button type="submit" loading={createProject.isPending} disabled={!name.trim()}>
            Create project
          </Button>
        </div>
      </form>
    </Modal>
  );
}
