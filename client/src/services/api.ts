import axios, { AxiosError, type InternalAxiosRequestConfig } from 'axios';
import { extractServerErrorMessage, type ErrorPayload } from './error';

const REQUESTED_WITH_HEADER = 'XMLHttpRequest';
const AUTH_ROUTES = [
  '/auth/login',
  '/auth/register',
  '/auth/refresh',
  '/auth/logout',
  '/auth/oauth2/exchange',
] as const;

export const API_BASE_URL = import.meta.env.VITE_API_URL || '/api/v1';

let accessToken: string | null = null;
let refreshInFlight: Promise<string | null> | null = null;

const baseHeaders = {
  'Content-Type': 'application/json',
  'X-Requested-With': REQUESTED_WITH_HEADER,
};

const api = axios.create({
  baseURL: API_BASE_URL,
  withCredentials: true,
  headers: baseHeaders,
});

const refreshClient = axios.create({
  baseURL: API_BASE_URL,
  withCredentials: true,
  headers: baseHeaders,
});

type RetryableRequestConfig = InternalAxiosRequestConfig & {
  _retry?: boolean;
};

const isAuthRoute = (url?: string) =>
  typeof url === 'string' && AUTH_ROUTES.some((route) => url.includes(route));

export const getAccessToken = () => accessToken;

export const setAccessToken = (token: string | null) => {
  accessToken = token;
};

export const clearAccessToken = () => {
  accessToken = null;
};

export const refreshAccessToken = async (): Promise<string | null> => {
  if (refreshInFlight) {
    return refreshInFlight;
  }

  refreshInFlight = refreshClient
    .post<{ accessToken: string }>('/auth/refresh')
    .then(({ data }) => {
      setAccessToken(data.accessToken);
      return data.accessToken;
    })
    .catch(() => {
      clearAccessToken();
      return null;
    })
    .finally(() => {
      refreshInFlight = null;
    });

  return refreshInFlight;
};

api.interceptors.request.use((config) => {
  if (accessToken) {
    config.headers.Authorization = `Bearer ${accessToken}`;
  }
  return config;
});

api.interceptors.response.use(
  (response) => response,
  async (error: AxiosError<ErrorPayload>) => {
    const backendMessage = extractServerErrorMessage(error.response?.data);
    if (backendMessage) {
      error.message = backendMessage;
    }

    const originalRequest = error.config as RetryableRequestConfig | undefined;

    if (
      !originalRequest ||
      originalRequest._retry ||
      error.response?.status !== 401 ||
      isAuthRoute(originalRequest.url)
    ) {
      return Promise.reject(error);
    }

    originalRequest._retry = true;
    const refreshedToken = await refreshAccessToken();

    if (!refreshedToken) {
      return Promise.reject(error);
    }

    originalRequest.headers.Authorization = `Bearer ${refreshedToken}`;
    return api(originalRequest);
  }
);

export default api;
