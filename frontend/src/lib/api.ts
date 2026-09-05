import axios, { type AxiosError, type InternalAxiosRequestConfig } from "axios";
import { useAuthStore } from "../store/authStore";
import type { ApiError, AuthResponse } from "./types";

export const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL as string | undefined) ?? "http://localhost:8080";

export const api = axios.create({
  baseURL: API_BASE_URL,
});

api.interceptors.request.use((config) => {
  const token = useAuthStore.getState().accessToken;
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// A single in-flight refresh shared by every request that races into a 401 at the same time
// (e.g. a page that fires several queries at once right as the access token expires) - without
// this, each would independently POST /auth/refresh, and the backend's rotate-on-use refresh
// token (see ADR-008) means only the first of those would actually succeed: every other
// concurrent refresh attempt would be using an already-rotated, now-dead token and would
// incorrectly log the user out.
let refreshPromise: Promise<string> | null = null;

async function refreshAccessToken(): Promise<string> {
  const { refreshToken, setSession, clear } = useAuthStore.getState();
  if (!refreshToken) {
    clear();
    throw new Error("No refresh token available");
  }
  try {
    const response = await axios.post<AuthResponse>(`${API_BASE_URL}/api/v1/auth/refresh`, { refreshToken });
    setSession({
      userId: response.data.userId,
      accessToken: response.data.accessToken,
      refreshToken: response.data.refreshToken,
    });
    return response.data.accessToken;
  } catch (err) {
    clear();
    throw err;
  }
}

api.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const original = error.config as (InternalAxiosRequestConfig & { _retried?: boolean }) | undefined;
    const isAuthEndpoint = original?.url?.includes("/auth/login") || original?.url?.includes("/auth/register") || original?.url?.includes("/auth/refresh");

    if (error.response?.status === 401 && original && !original._retried && !isAuthEndpoint) {
      original._retried = true;
      try {
        refreshPromise ??= refreshAccessToken().finally(() => {
          refreshPromise = null;
        });
        const newToken = await refreshPromise;
        original.headers = original.headers ?? {};
        original.headers.Authorization = `Bearer ${newToken}`;
        return api(original);
      } catch {
        window.location.assign("/login");
        return Promise.reject(error);
      }
    }
    return Promise.reject(error);
  },
);

/** Extracts this backend's consistent {@link ApiError} shape out of any axios error, falling back to a generic message for a network-level failure with no response body at all. */
export function apiErrorMessage(error: unknown): string {
  if (axios.isAxiosError(error)) {
    const data = error.response?.data as ApiError | undefined;
    if (data?.message) return data.message;
    if (error.message) return error.message;
  }
  return "Something went wrong. Please try again.";
}
