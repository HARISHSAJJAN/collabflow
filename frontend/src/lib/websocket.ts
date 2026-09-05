import { Client, type IMessage } from "@stomp/stompjs";
import { API_BASE_URL } from "./api";
import { useAuthStore } from "../store/authStore";
import type { NotificationResponse, ProjectUpdateMessage } from "./types";

// Native WebSocket only, matching the backend's own choice (see websocket.WebSocketConfig's
// Javadoc: no SockJS fallback, since every browser this app targets supports a real WebSocket
// upgrade). Authentication happens on the STOMP CONNECT frame, not the HTTP handshake - a
// browser's WebSocket API can't set an Authorization header on the upgrade request itself, so
// the token goes in the CONNECT frame's headers instead (see JwtStompAuthInterceptor).
function createClient(): Client {
  const wsUrl = API_BASE_URL.replace(/^http/, "ws") + "/ws";
  return new Client({
    brokerURL: wsUrl,
    connectHeaders: {
      Authorization: `Bearer ${useAuthStore.getState().accessToken ?? ""}`,
    },
    reconnectDelay: 5000,
    heartbeatIncoming: 10000,
    heartbeatOutgoing: 10000,
  });
}

let client: Client | null = null;

/** Connects once (idempotent - safe to call from multiple components mounting) and returns the shared client. */
export function connectSocket(): Client {
  if (client && client.active) return client;
  client = createClient();
  client.activate();
  return client;
}

export function disconnectSocket() {
  client?.deactivate();
  client = null;
}

/** Subscribes to a project's live task/comment updates. Returns an unsubscribe function. */
export function subscribeToProject(projectId: string, onMessage: (msg: ProjectUpdateMessage) => void): () => void {
  const c = connectSocket();
  let subscriptionId: string | undefined;
  const trySubscribe = () => {
    const sub = c.subscribe(`/topic/projects/${projectId}`, (message: IMessage) => {
      onMessage(JSON.parse(message.body) as ProjectUpdateMessage);
    });
    subscriptionId = sub.id;
  };
  if (c.connected) trySubscribe();
  else c.onConnect = trySubscribe;

  return () => {
    if (subscriptionId) c.unsubscribe(subscriptionId);
  };
}

/** Subscribes to this user's own private notification queue. Returns an unsubscribe function. */
export function subscribeToNotifications(onMessage: (notification: NotificationResponse) => void): () => void {
  const c = connectSocket();
  let subscriptionId: string | undefined;
  const trySubscribe = () => {
    const sub = c.subscribe("/user/queue/notifications", (message: IMessage) => {
      onMessage(JSON.parse(message.body) as NotificationResponse);
    });
    subscriptionId = sub.id;
  };
  if (c.connected) trySubscribe();
  else {
    const existing = c.onConnect;
    c.onConnect = (frame) => {
      existing?.(frame);
      trySubscribe();
    };
  }

  return () => {
    if (subscriptionId) c.unsubscribe(subscriptionId);
  };
}
