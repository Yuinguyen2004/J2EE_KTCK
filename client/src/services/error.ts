import axios from 'axios';

interface ErrorPayload {
  message?: string;
  error?: string;
}

export const getErrorMessage = (error: unknown, fallback: string) => {
  if (axios.isAxiosError<ErrorPayload>(error)) {
    return error.response?.data?.message || error.response?.data?.error || fallback;
  }

  if (error instanceof Error && error.message) {
    return error.message;
  }

  return fallback;
};
