// Every type here mirrors a real backend DTO/enum 1:1 (see backend/src/main/java/com/collabflow)
// - kept hand-written rather than code-generated from the OpenAPI spec so the mapping stays
// visible in one place. If the backend's shape changes, this is the one file to update.

export type TeamRole = "OWNER" | "ADMIN" | "MEMBER";
export type ProjectStatus = "ACTIVE" | "ARCHIVED";
export type TaskStatus = "TODO" | "IN_PROGRESS" | "REVIEW" | "DONE" | "BLOCKED";
export type TaskPriority = "LOW" | "MEDIUM" | "HIGH" | "CRITICAL";
export type NotificationType =
  | "TASK_ASSIGNED"
  | "TASK_STATUS_CHANGED"
  | "COMMENT_ADDED"
  | "TEAM_MEMBER_ADDED"
  | "TASK_MENTION"
  | "DUE_DATE_APPROACHING";

export const TASK_STATUSES: TaskStatus[] = ["TODO", "IN_PROGRESS", "REVIEW", "DONE", "BLOCKED"];
export const TASK_PRIORITIES: TaskPriority[] = ["LOW", "MEDIUM", "HIGH", "CRITICAL"];

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  last: boolean;
}

export interface ApiError {
  status: number;
  error: string;
  message: string;
  path: string;
  timestamp?: string;
  fieldErrors?: Record<string, string>;
}

export interface AuthResponse {
  userId: string;
  accessToken: string;
  refreshToken: string;
  expiresInSeconds: number;
  tokenType: string;
}

export interface SessionResponse {
  id: string;
  userAgent: string | null;
  ipAddress: string | null;
  issuedAt: string;
  expiresAt: string;
}

export interface UserProfile {
  id: string;
  email: string;
  fullName: string;
  avatarUrl: string | null;
  lastLoginAt: string | null;
  createdAt: string;
}

export interface TeamResponse {
  id: string;
  name: string;
  description: string | null;
  createdBy: string;
  myRole: TeamRole;
  createdAt: string;
  updatedAt: string;
}

export interface TeamMemberResponse {
  userId: string;
  email: string;
  fullName: string;
  avatarUrl: string | null;
  role: TeamRole;
  joinedAt: string;
}

export interface ProjectResponse {
  id: string;
  teamId: string;
  name: string;
  description: string | null;
  status: ProjectStatus;
  createdBy: string;
  createdAt: string;
  updatedAt: string;
}

export interface ProjectMemberResponse {
  userId: string;
  email: string;
  fullName: string;
  avatarUrl: string | null;
  teamRole: TeamRole;
  addedAt: string;
}

export interface TaskResponse {
  id: string;
  projectId: string;
  title: string;
  description: string | null;
  status: TaskStatus;
  priority: TaskPriority;
  assigneeId: string | null;
  reporterId: string;
  dueDate: string | null;
  labels: string[];
  version: number;
  createdAt: string;
  updatedAt: string;
}

export interface CommentResponse {
  id: string;
  taskId: string;
  authorId: string;
  authorName: string;
  body: string;
  edited: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface NotificationResponse {
  id: string;
  type: NotificationType;
  payload: string;
  read: boolean;
  createdAt: string;
}

export interface ProjectUpdateMessage {
  eventType: string;
  data: unknown;
}
