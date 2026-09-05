import { api } from "./api";
import type {
  AuthResponse,
  CommentResponse,
  NotificationResponse,
  PageResponse,
  ProjectMemberResponse,
  ProjectResponse,
  ProjectStatus,
  SessionResponse,
  TaskPriority,
  TaskResponse,
  TaskStatus,
  TeamMemberResponse,
  TeamResponse,
  TeamRole,
  UserProfile,
} from "./types";

// ---- Auth ----
export const authApi = {
  register: (body: { email: string; password: string; fullName: string }) =>
    api.post<{ userId: string }>("/api/v1/auth/register", body).then((r) => r.data),
  login: (body: { email: string; password: string }) => api.post<AuthResponse>("/api/v1/auth/login", body).then((r) => r.data),
  logout: (refreshToken: string) => api.post<void>("/api/v1/auth/logout", { refreshToken }).then((r) => r.data),
  logoutAll: () => api.delete<void>("/api/v1/auth/sessions").then((r) => r.data),
  sessions: () => api.get<SessionResponse[]>("/api/v1/auth/sessions").then((r) => r.data),
};

// ---- Users ----
export const userApi = {
  me: () => api.get<UserProfile>("/api/v1/users/me").then((r) => r.data),
  updateProfile: (body: { fullName: string; avatarUrl?: string | null }) => api.patch<UserProfile>("/api/v1/users/me", body).then((r) => r.data),
  changePassword: (body: { currentPassword: string; newPassword: string }) => api.post<void>("/api/v1/users/me/password", body).then((r) => r.data),
};

// ---- Teams ----
export const teamApi = {
  list: (page = 0, size = 50) => api.get<PageResponse<TeamResponse>>("/api/v1/teams", { params: { page, size } }).then((r) => r.data),
  get: (teamId: string) => api.get<TeamResponse>(`/api/v1/teams/${teamId}`).then((r) => r.data),
  create: (body: { name: string; description?: string }) => api.post<TeamResponse>("/api/v1/teams", body).then((r) => r.data),
  update: (teamId: string, body: { name: string; description?: string }) => api.patch<TeamResponse>(`/api/v1/teams/${teamId}`, body).then((r) => r.data),
  remove: (teamId: string) => api.delete<void>(`/api/v1/teams/${teamId}`).then((r) => r.data),
  members: (teamId: string) => api.get<TeamMemberResponse[]>(`/api/v1/teams/${teamId}/members`).then((r) => r.data),
  addMember: (teamId: string, email: string) => api.post<TeamMemberResponse>(`/api/v1/teams/${teamId}/members`, { email }).then((r) => r.data),
  changeRole: (teamId: string, targetUserId: string, role: TeamRole) =>
    api.patch<TeamMemberResponse>(`/api/v1/teams/${teamId}/members/${targetUserId}`, { role }).then((r) => r.data),
  removeMember: (teamId: string, targetUserId: string) => api.delete<void>(`/api/v1/teams/${teamId}/members/${targetUserId}`).then((r) => r.data),
};

// ---- Projects ----
export const projectApi = {
  listByTeam: (teamId: string, status?: ProjectStatus) =>
    api.get<PageResponse<ProjectResponse>>("/api/v1/projects", { params: { teamId, status, size: 50 } }).then((r) => r.data),
  get: (projectId: string) => api.get<ProjectResponse>(`/api/v1/projects/${projectId}`).then((r) => r.data),
  create: (body: { teamId: string; name: string; description?: string }) => api.post<ProjectResponse>("/api/v1/projects", body).then((r) => r.data),
  update: (projectId: string, body: { name: string; description?: string }) =>
    api.patch<ProjectResponse>(`/api/v1/projects/${projectId}`, body).then((r) => r.data),
  archive: (projectId: string) => api.post<ProjectResponse>(`/api/v1/projects/${projectId}/archive`).then((r) => r.data),
  unarchive: (projectId: string) => api.post<ProjectResponse>(`/api/v1/projects/${projectId}/unarchive`).then((r) => r.data),
  members: (projectId: string) => api.get<ProjectMemberResponse[]>(`/api/v1/projects/${projectId}/members`).then((r) => r.data),
  addMember: (projectId: string, userId: string) =>
    api.post<ProjectMemberResponse>(`/api/v1/projects/${projectId}/members`, { userId }).then((r) => r.data),
  removeMember: (projectId: string, targetUserId: string) => api.delete<void>(`/api/v1/projects/${projectId}/members/${targetUserId}`).then((r) => r.data),
};

// ---- Tasks ----
export interface TaskFilters {
  status?: TaskStatus;
  priority?: TaskPriority;
  assigneeId?: string;
  keyword?: string;
  label?: string;
}

export const taskApi = {
  list: (projectId: string, filters: TaskFilters = {}, page = 0, size = 100) =>
    api
      .get<PageResponse<TaskResponse>>("/api/v1/tasks", { params: { projectId, page, size, ...filters } })
      .then((r) => r.data),
  search: (projectId: string, filters: TaskFilters = {}, page = 0, size = 100) =>
    api
      .get<PageResponse<TaskResponse>>("/api/v1/tasks/search", { params: { projectId, page, size, ...filters } })
      .then((r) => r.data),
  myAssigned: (page = 0, size = 50) => api.get<PageResponse<TaskResponse>>("/api/v1/tasks/me", { params: { page, size } }).then((r) => r.data),
  get: (taskId: string) => api.get<TaskResponse>(`/api/v1/tasks/${taskId}`).then((r) => r.data),
  create: (body: { projectId: string; title: string; description?: string; priority?: TaskPriority; dueDate?: string | null; assigneeId?: string | null }) =>
    api.post<TaskResponse>("/api/v1/tasks", body).then((r) => r.data),
  update: (taskId: string, body: { title: string; description?: string; priority?: TaskPriority; dueDate?: string | null }) =>
    api.patch<TaskResponse>(`/api/v1/tasks/${taskId}`, body).then((r) => r.data),
  changeStatus: (taskId: string, status: TaskStatus) => api.patch<TaskResponse>(`/api/v1/tasks/${taskId}/status`, { status }).then((r) => r.data),
  assign: (taskId: string, assigneeId: string | null) => api.patch<TaskResponse>(`/api/v1/tasks/${taskId}/assignee`, { assigneeId }).then((r) => r.data),
  remove: (taskId: string) => api.delete<void>(`/api/v1/tasks/${taskId}`).then((r) => r.data),
  addLabel: (taskId: string, label: string) => api.post<TaskResponse>(`/api/v1/tasks/${taskId}/labels`, { label }).then((r) => r.data),
  removeLabel: (taskId: string, label: string) => api.delete<TaskResponse>(`/api/v1/tasks/${taskId}/labels/${encodeURIComponent(label)}`).then((r) => r.data),
};

// ---- Comments ----
export const commentApi = {
  list: (taskId: string, page = 0, size = 50) =>
    api.get<PageResponse<CommentResponse>>("/api/v1/comments", { params: { taskId, page, size } }).then((r) => r.data),
  create: (taskId: string, body: string) => api.post<CommentResponse>("/api/v1/comments", { taskId, body }).then((r) => r.data),
  update: (commentId: string, body: string) => api.patch<CommentResponse>(`/api/v1/comments/${commentId}`, { body }).then((r) => r.data),
  remove: (commentId: string) => api.delete<void>(`/api/v1/comments/${commentId}`).then((r) => r.data),
};

// ---- Notifications ----
export const notificationApi = {
  list: (page = 0, size = 30) => api.get<PageResponse<NotificationResponse>>("/api/v1/notifications", { params: { page, size } }).then((r) => r.data),
  unreadCount: () => api.get<{ unreadCount: number }>("/api/v1/notifications/unread-count").then((r) => r.data),
  markRead: (notificationId: string) => api.post<void>(`/api/v1/notifications/${notificationId}/read`).then((r) => r.data),
  markAllRead: () => api.post<void>("/api/v1/notifications/read-all").then((r) => r.data),
};
