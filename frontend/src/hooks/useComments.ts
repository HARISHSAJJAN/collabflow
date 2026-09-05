import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import toast from "react-hot-toast";
import { commentApi } from "../lib/endpoints";
import { apiErrorMessage } from "../lib/api";

export function useComments(taskId: string | undefined) {
  return useQuery({ queryKey: ["comments", taskId], queryFn: () => commentApi.list(taskId!), enabled: !!taskId });
}

export function useAddComment(taskId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: string) => commentApi.create(taskId, body),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["comments", taskId] }),
    onError: (err) => toast.error(apiErrorMessage(err)),
  });
}

export function useUpdateComment(taskId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ commentId, body }: { commentId: string; body: string }) => commentApi.update(commentId, body),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["comments", taskId] }),
    onError: (err) => toast.error(apiErrorMessage(err)),
  });
}

export function useDeleteComment(taskId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (commentId: string) => commentApi.remove(commentId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["comments", taskId] }),
    onError: (err) => toast.error(apiErrorMessage(err)),
  });
}
