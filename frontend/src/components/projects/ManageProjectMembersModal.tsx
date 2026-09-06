import { Minus, Plus } from "lucide-react";
import { Modal } from "../ui/Modal";
import { Avatar } from "../ui/Avatar";
import { Spinner } from "../ui/Badge";
import { useProjectMembers, useAddProjectMember, useRemoveProjectMember } from "../../hooks/useProjects";
import { useTeamMembers } from "../../hooks/useTeams";

export function ManageProjectMembersModal({
  open,
  onClose,
  projectId,
  teamId,
}: {
  open: boolean;
  onClose: () => void;
  projectId: string;
  teamId: string;
}) {
  const { data: projectMembers, isLoading: loadingProjectMembers } = useProjectMembers(projectId);
  const { data: teamMembers, isLoading: loadingTeamMembers } = useTeamMembers(teamId);
  const addMember = useAddProjectMember(projectId);
  const removeMember = useRemoveProjectMember(projectId);

  const projectMemberIds = new Set(projectMembers?.map((m) => m.userId));
  const addableTeamMembers = teamMembers?.filter((m) => !projectMemberIds.has(m.userId)) ?? [];

  return (
    <Modal open={open} onClose={onClose} title="Project members">
      {loadingProjectMembers || loadingTeamMembers ? (
        <Spinner className="py-10" />
      ) : (
        <div className="flex flex-col gap-6">
          <div>
            <h3 className="mb-2 text-xs font-semibold uppercase tracking-wider text-[var(--text-tertiary)]">On this project</h3>
            {!projectMembers?.length ? (
              <p className="text-sm text-[var(--text-tertiary)]">Nobody has been added yet.</p>
            ) : (
              <div className="flex flex-col gap-1">
                {projectMembers.map((member) => (
                  <div key={member.userId} className="flex items-center justify-between rounded-lg px-2 py-1.5 hover:bg-[var(--bg-surface-2)]">
                    <div className="flex items-center gap-2.5">
                      <Avatar name={member.fullName} size="sm" />
                      <div>
                        <p className="text-sm font-medium text-[var(--text-primary)]">{member.fullName}</p>
                        <p className="text-xs text-[var(--text-tertiary)]">{member.email}</p>
                      </div>
                    </div>
                    <button
                      onClick={() => removeMember.mutate(member.userId)}
                      className="rounded p-1 text-[var(--text-tertiary)] hover:text-red-500"
                      aria-label={`Remove ${member.fullName} from project`}
                    >
                      <Minus className="size-3.5" />
                    </button>
                  </div>
                ))}
              </div>
            )}
          </div>

          <div>
            <h3 className="mb-2 text-xs font-semibold uppercase tracking-wider text-[var(--text-tertiary)]">On the team, not yet on this project</h3>
            {!addableTeamMembers.length ? (
              <p className="text-sm text-[var(--text-tertiary)]">Every team member is already on this project.</p>
            ) : (
              <div className="flex flex-col gap-1">
                {addableTeamMembers.map((member) => (
                  <div key={member.userId} className="flex items-center justify-between rounded-lg px-2 py-1.5 hover:bg-[var(--bg-surface-2)]">
                    <div className="flex items-center gap-2.5">
                      <Avatar name={member.fullName} size="sm" />
                      <div>
                        <p className="text-sm font-medium text-[var(--text-primary)]">{member.fullName}</p>
                        <p className="text-xs text-[var(--text-tertiary)]">{member.email}</p>
                      </div>
                    </div>
                    <button
                      onClick={() => addMember.mutate(member.userId)}
                      className="rounded p-1 text-[var(--text-tertiary)] hover:text-brand-600"
                      aria-label={`Add ${member.fullName} to project`}
                    >
                      <Plus className="size-3.5" />
                    </button>
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>
      )}
    </Modal>
  );
}
