import { useEffect } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import toast from "react-hot-toast";
import { notificationApi } from "../lib/endpoints";
import { subscribeToNotifications } from "../lib/websocket";
import { useAuthStore } from "../store/authStore";
import { NOTIFICATION_COPY } from "../lib/display";
import type { NotificationResponse } from "../lib/types";

export function useNotifications() {
  return useQuery({ queryKey: ["notifications"], queryFn: () => notificationApi.list() });
}

export function useUnreadCount() {
  return useQuery({ queryKey: ["notifications", "unread-count"], queryFn: notificationApi.unreadCount, refetchInterval: 60_000 });
}

export function useMarkNotificationRead() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: notificationApi.markRead,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["notifications"] }),
  });
}

export function useMarkAllNotificationsRead() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: notificationApi.markAllRead,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["notifications"] }),
  });
}

/** Subscribes this session to its own live notification queue for the lifetime of the app shell - see websocket.ts and the backend's JwtStompAuthInterceptor for how that queue is scoped to exactly one user. */
export function useLiveNotifications() {
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated());
  const queryClient = useQueryClient();

  useEffect(() => {
    if (!isAuthenticated) return;
    const unsubscribe = subscribeToNotifications((notification: NotificationResponse) => {
      queryClient.invalidateQueries({ queryKey: ["notifications"] });
      toast(NOTIFICATION_COPY[notification.type], { icon: "🔔" });
    });
    return unsubscribe;
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isAuthenticated]);
}
