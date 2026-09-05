import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import toast from "react-hot-toast";
import { teamApi } from "../lib/endpoints";
import { apiErrorMessage } from "../lib/api";
import type { TeamRole } from "../lib/types";

export function useTeams() {
  return useQuery({ queryKey: ["teams"], queryFn: () => teamApi.list() });
}

export function useTeam(teamId: string | undefined) {
  return useQuery({ queryKey: ["teams", teamId], queryFn: () => teamApi.get(teamId!), enabled: !!teamId });
}

export function useTeamMembers(teamId: string | undefined) {
  return useQuery({ queryKey: ["teams", teamId, "members"], queryFn: () => teamApi.members(teamId!), enabled: !!teamId });
}

export function useCreateTeam() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: teamApi.create,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["teams"] });
      toast.success("Team created");
    },
    onError: (err) => toast.error(apiErrorMessage(err)),
  });
}

export function useAddTeamMember(teamId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (email: string) => teamApi.addMember(teamId, email),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["teams", teamId, "members"] });
      toast.success("Member added");
    },
    onError: (err) => toast.error(apiErrorMessage(err)),
  });
}

export function useChangeTeamRole(teamId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ userId, role }: { userId: string; role: TeamRole }) => teamApi.changeRole(teamId, userId, role),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["teams", teamId, "members"] }),
    onError: (err) => toast.error(apiErrorMessage(err)),
  });
}

export function useRemoveTeamMember(teamId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (userId: string) => teamApi.removeMember(teamId, userId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["teams", teamId, "members"] });
      toast.success("Member removed");
    },
    onError: (err) => toast.error(apiErrorMessage(err)),
  });
}

export function useDeleteTeam() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: teamApi.remove,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["teams"] });
      toast.success("Team deleted");
    },
    onError: (err) => toast.error(apiErrorMessage(err)),
  });
}
