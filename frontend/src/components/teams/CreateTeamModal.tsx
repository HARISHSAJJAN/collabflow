import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { Modal } from "../ui/Modal";
import { Input, Textarea } from "../ui/Input";
import { Button } from "../ui/Button";
import { useCreateTeam } from "../../hooks/useTeams";

export function CreateTeamModal({ open, onClose }: { open: boolean; onClose: () => void }) {
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const createTeam = useCreateTeam();
  const navigate = useNavigate();

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    createTeam.mutate(
      { name, description: description || undefined },
      {
        onSuccess: (team) => {
          setName("");
          setDescription("");
          onClose();
          navigate(`/teams/${team.id}`);
        },
      },
    );
  };

  return (
    <Modal open={open} onClose={onClose} title="Create a team">
      <form onSubmit={handleSubmit} className="flex flex-col gap-4">
        <Input label="Team name" placeholder="e.g. Platform Engineering" value={name} onChange={(e) => setName(e.target.value)} required autoFocus />
        <Textarea
          label="Description"
          placeholder="What is this team responsible for? (optional)"
          rows={3}
          value={description}
          onChange={(e) => setDescription(e.target.value)}
        />
        <div className="mt-1 flex justify-end gap-2">
          <Button type="button" variant="ghost" onClick={onClose}>
            Cancel
          </Button>
          <Button type="submit" loading={createTeam.isPending} disabled={!name.trim()}>
            Create team
          </Button>
        </div>
      </form>
    </Modal>
  );
}
