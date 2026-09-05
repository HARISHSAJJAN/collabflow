import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { isAxiosError } from "axios";
import toast from "react-hot-toast";
import { taskApi, type TaskFilters } from "../lib/endpoints";
import { apiErrorMessage } from "../lib/api";
import type { TaskPriority, TaskStatus } from "../lib/types";

export function useTasks(projectId: string | undefined, filters: TaskFilters = {}) {
  const hasSearchFilter = !!(filters.keyword || filters.label);
  return useQuery({
    queryKey: ["tasks", projectId, filters],
    queryFn: () => (hasSearchFilter ? taskApi.search(projectId!, filters) : taskApi.list(projectId!, filters)),
    enabled: !!projectId,
  });
}

export function useTask(taskId: string | undefined) {
  return useQuery({ queryKey: ["task", taskId], queryFn: () => taskApi.get(taskId!), enabled: !!taskId });
}

function invalidateTaskLists(queryClient: ReturnType<typeof useQueryClient>, projectId: string) {
  queryClient.invalidateQueries({ queryKey: ["tasks", projectId] });
}

export function useCreateTask(projectId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: taskApi.create,
    onSuccess: () => {
      invalidateTaskLists(queryClient, projectId);
      toast.success("Task created");
    },
    onError: (err) => toast.error(apiErrorMessage(err)),
  });
}

export function useUpdateTask(projectId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ taskId, body }: { taskId: string; body: { title: string; description?: string; priority?: TaskPriority; dueDate?: string | null } }) =>
      taskApi.update(taskId, body),
    onSuccess: (data) => {
      invalidateTaskLists(queryClient, projectId);
      queryClient.invalidateQueries({ queryKey: ["task", data.id] });
    },
    onError: (err) => toast.error(apiErrorMessage(err)),
  });
}

/**
 * Optimistic status updates for smooth drag-and-drop, with a real rollback path: this is the
 * one mutation in the app that can legitimately fail with 409 CONCURRENT_MODIFICATION (see
 * ADR-006) if someone else changed the same task first. On that specific status code, the
 * optimistic move is reverted and the user is told to look again, instead of leaving the
 * board showing a state the server rejected.
 */
export function useChangeTaskStatus(projectId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ taskId, status }: { taskId: string; status: TaskStatus }) => taskApi.changeStatus(taskId, status),
    onMutate: async ({ taskId, status }) => {
      await queryClient.cancelQueries({ queryKey: ["tasks", projectId] });
      const previous = queryClient.getQueriesData({ queryKey: ["tasks", projectId] });
      queryClient.setQueriesData({ queryKey: ["tasks", projectId] }, (old: unknown) => {
        const page = old as { content: { id: string; status: TaskStatus }[] } | undefined;
        if (!page) return old;
        return { ...page, content: page.content.map((t) => (t.id === taskId ? { ...t, status } : t)) };
      });
      return { previous };
    },
    onError: (err, _vars, context) => {
      context?.previous.forEach(([key, data]) => queryClient.setQueryData(key, data));
      if (isAxiosError(err) && err.response?.status === 409) {
        toast.error("Someone else already updated this task - refreshed to the latest version.");
      } else {
        toast.error(apiErrorMessage(err));
      }
    },
    onSettled: () => invalidateTaskLists(queryClient, projectId),
  });
}

export function useAssignTask(projectId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ taskId, assigneeId }: { taskId: string; assigneeId: string | null }) => taskApi.assign(taskId, assigneeId),
    onSuccess: (data) => {
      invalidateTaskLists(queryClient, projectId);
      queryClient.invalidateQueries({ queryKey: ["task", data.id] });
    },
    onError: (err) => toast.error(apiErrorMessage(err)),
  });
}

export function useDeleteTask(projectId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: taskApi.remove,
    onSuccess: () => {
      invalidateTaskLists(queryClient, projectId);
      toast.success("Task deleted");
    },
    onError: (err) => toast.error(apiErrorMessage(err)),
  });
}

export function useTaskLabels(projectId: string, taskId: string) {
  const queryClient = useQueryClient();
  const invalidate = (data: { id: string }) => {
    invalidateTaskLists(queryClient, projectId);
    queryClient.invalidateQueries({ queryKey: ["task", data.id] });
  };
  const addLabel = useMutation({
    mutationFn: (label: string) => taskApi.addLabel(taskId, label),
    onSuccess: invalidate,
    onError: (err) => toast.error(apiErrorMessage(err)),
  });
  const removeLabel = useMutation({
    mutationFn: (label: string) => taskApi.removeLabel(taskId, label),
    onSuccess: invalidate,
    onError: (err) => toast.error(apiErrorMessage(err)),
  });
  return { addLabel, removeLabel };
}
