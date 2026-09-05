import { useState } from "react";
import { Link, useParams } from "react-router-dom";
import { FolderKanban, Plus, Trash2, UserPlus } from "lucide-react";
import { useAddTeamMember, useChangeTeamRole, useDeleteTeam, useRemoveTeamMember, useTeam, useTeamMembers } from "../hooks/useTeams";
import { useProjects } from "../hooks/useProjects";
import { useCurrentUser } from "../hooks/useAuth";
import { Card, EmptyState, Spinner } from "../components/ui/Badge";
import { Avatar } from "../components/ui/Avatar";
import { Badge } from "../components/ui/Badge";
import { Button } from "../components/ui/Button";
import { Input, Select } from "../components/ui/Input";
import { CreateProjectModal } from "../components/projects/CreateProjectModal";
import { ROLE_META } from "../lib/display";
import type { TeamRole } from "../lib/types";

export function TeamDetailPage() {
  const { teamId } = useParams<{ teamId: string }>();
  const { data: team } = useTeam(teamId);
  const { data: members } = useTeamMembers(teamId);
  const { data: projects } = useProjects(teamId);
  const { data: me } = useCurrentUser();
  const addMember = useAddTeamMember(teamId ?? "");
  const changeRole = useChangeTeamRole(teamId ?? "");
  const removeMember = useRemoveTeamMember(teamId ?? "");
  const deleteTeam = useDeleteTeam();
  const [inviteEmail, setInviteEmail] = useState("");
  const [createProjectOpen, setCreateProjectOpen] = useState(false);

  if (!teamId || !team) return <Spinner className="py-24" />;

  const myRole = team.myRole;
  const canManage = myRole === "OWNER" || myRole === "ADMIN";

  const handleInvite = (e: React.FormEvent) => {
    e.preventDefault();
    if (!inviteEmail.trim()) return;
    addMember.mutate(inviteEmail.trim(), { onSuccess: () => setInviteEmail("") });
  };

  return (
    <div className="mx-auto max-w-5xl px-6 py-8">
      <div className="flex items-start justify-between">
        <div>
          <h1 className="text-2xl font-bold text-[var(--text-primary)]">{team.name}</h1>
          {team.description && <p className="mt-1 text-sm text-[var(--text-secondary)]">{team.description}</p>}
        </div>
        {myRole === "OWNER" && (
          <button
            onClick={() => {
              if (confirm(`Delete "${team.name}"? This cannot be undone.`)) deleteTeam.mutate(teamId);
            }}
            className="flex items-center gap-1.5 rounded-lg px-3 py-1.5 text-sm text-[var(--text-tertiary)] hover:bg-red-50 hover:text-red-500 dark:hover:bg-red-900/20"
          >
            <Trash2 className="size-4" /> Delete team
          </button>
        )}
      </div>

      <section className="mt-8">
        <div className="mb-3 flex items-center justify-between">
          <h2 className="text-sm font-semibold uppercase tracking-wider text-[var(--text-tertiary)]">Projects</h2>
          <Button size="sm" onClick={() => setCreateProjectOpen(true)}>
            <Plus className="size-4" /> New project
          </Button>
        </div>
        {!projects?.content.length ? (
          <EmptyState icon={<FolderKanban className="size-8" />} title="No projects yet" description="Create a project to start organizing work into a task board." />
        ) : (
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
            {projects.content.map((project) => (
              <Link key={project.id} to={`/projects/${project.id}`}>
                <Card className="flex h-full flex-col gap-2 p-4 transition-transform hover:-translate-y-0.5 hover:shadow-md">
                  <div className="flex items-center justify-between">
                    <span className="font-semibold text-[var(--text-primary)]">{project.name}</span>
                    {project.status === "ARCHIVED" && <Badge className="bg-slate-100 text-slate-500 dark:bg-slate-800">Archived</Badge>}
                  </div>
                  {project.description && <p className="line-clamp-2 text-sm text-[var(--text-secondary)]">{project.description}</p>}
                </Card>
              </Link>
            ))}
          </div>
        )}
      </section>

      <section className="mt-10">
        <h2 className="mb-3 text-sm font-semibold uppercase tracking-wider text-[var(--text-tertiary)]">Members</h2>
        <Card className="divide-y divide-[var(--border-subtle)]">
          {members?.map((member) => (
            <div key={member.userId} className="flex items-center justify-between px-4 py-3">
              <div className="flex items-center gap-3">
                <Avatar name={member.fullName} size="sm" />
                <div>
                  <p className="text-sm font-medium text-[var(--text-primary)]">{member.fullName}</p>
                  <p className="text-xs text-[var(--text-tertiary)]">{member.email}</p>
                </div>
              </div>
              <div className="flex items-center gap-2">
                {canManage && member.userId !== me?.id ? (
                  <Select
                    className="!h-8 !py-1 text-xs"
                    value={member.role}
                    onChange={(e) => changeRole.mutate({ userId: member.userId, role: e.target.value as TeamRole })}
                  >
                    <option value="OWNER">Owner</option>
                    <option value="ADMIN">Admin</option>
                    <option value="MEMBER">Member</option>
                  </Select>
                ) : (
                  <Badge className={ROLE_META[member.role].className}>{ROLE_META[member.role].label}</Badge>
                )}
                {canManage && member.userId !== me?.id && (
                  <button onClick={() => removeMember.mutate(member.userId)} className="rounded p-1 text-[var(--text-tertiary)] hover:text-red-500">
                    <Trash2 className="size-3.5" />
                  </button>
                )}
              </div>
            </div>
          ))}
        </Card>

        {canManage && (
          <form onSubmit={handleInvite} className="mt-3 flex gap-2">
            <Input placeholder="teammate@example.com" type="email" value={inviteEmail} onChange={(e) => setInviteEmail(e.target.value)} className="flex-1" />
            <Button type="submit" variant="secondary" loading={addMember.isPending}>
              <UserPlus className="size-4" /> Invite
            </Button>
          </form>
        )}
      </section>

      <CreateProjectModal open={createProjectOpen} onClose={() => setCreateProjectOpen(false)} teamId={teamId} />
    </div>
  );
}
