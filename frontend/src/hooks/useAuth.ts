import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useNavigate } from "react-router-dom";
import toast from "react-hot-toast";
import { authApi, userApi } from "../lib/endpoints";
import { apiErrorMessage } from "../lib/api";
import { useAuthStore } from "../store/authStore";
import { disconnectSocket } from "../lib/websocket";

export function useCurrentUser() {
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated());
  return useQuery({
    queryKey: ["me"],
    queryFn: userApi.me,
    enabled: isAuthenticated,
    staleTime: 5 * 60_000,
  });
}

export function useLogin() {
  const setSession = useAuthStore((s) => s.setSession);
  const navigate = useNavigate();
  return useMutation({
    mutationFn: authApi.login,
    onSuccess: (data) => {
      setSession(data);
      navigate("/");
    },
    onError: (err) => toast.error(apiErrorMessage(err)),
  });
}

export function useRegister() {
  const login = useLogin();
  return useMutation({
    mutationFn: authApi.register,
    onSuccess: (_data, variables) => {
      toast.success("Account created - signing you in...");
      login.mutate({ email: variables.email, password: variables.password });
    },
    onError: (err) => toast.error(apiErrorMessage(err)),
  });
}

export function useLogout() {
  const clear = useAuthStore((s) => s.clear);
  const refreshToken = useAuthStore((s) => s.refreshToken);
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  return () => {
    if (refreshToken) authApi.logout(refreshToken).catch(() => undefined);
    disconnectSocket();
    clear();
    queryClient.clear();
    navigate("/login");
  };
}
