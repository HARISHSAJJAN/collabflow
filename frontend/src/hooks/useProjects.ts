import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import toast from "react-hot-toast";
import { projectApi } from "../lib/endpoints";
import { apiErrorMessage } from "../lib/api";

export function useProjects(teamId: string | undefined) {
  return useQuery({ queryKey: ["projects", teamId], queryFn: () => projectApi.listByTeam(teamId!), enabled: !!teamId });
}

export function useProject(projectId: string | undefined) {
  return useQuery({ queryKey: ["project", projectId], queryFn: () => projectApi.get(projectId!), enabled: !!projectId });
}

export function useProjectMembers(projectId: string | undefined) {
  return useQuery({ queryKey: ["project", projectId, "members"], queryFn: () => projectApi.members(projectId!), enabled: !!projectId });
}

export function useCreateProject() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: projectApi.create,
    onSuccess: (data) => {
      queryClient.invalidateQueries({ queryKey: ["projects", data.teamId] });
      toast.success("Project created");
    },
    onError: (err) => toast.error(apiErrorMessage(err)),
  });
}

export function useArchiveProject() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ projectId, archive }: { projectId: string; archive: boolean }) =>
      archive ? projectApi.archive(projectId) : projectApi.unarchive(projectId),
    onSuccess: (data) => {
      queryClient.invalidateQueries({ queryKey: ["projects", data.teamId] });
      queryClient.invalidateQueries({ queryKey: ["project", data.id] });
    },
    onError: (err) => toast.error(apiErrorMessage(err)),
  });
}

export function useAddProjectMember(projectId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (userId: string) => projectApi.addMember(projectId, userId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["project", projectId, "members"] });
      toast.success("Member added to project");
    },
    onError: (err) => toast.error(apiErrorMessage(err)),
  });
}

export function useRemoveProjectMember(projectId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (userId: string) => projectApi.removeMember(projectId, userId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["project", projectId, "members"] }),
    onError: (err) => toast.error(apiErrorMessage(err)),
  });
}
