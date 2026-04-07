import axios from 'axios';

interface ErrorViolation {
  field?: string;
  message?: string;
}

export interface ErrorPayload {
  message?: string;
  detail?: string;
  error?: string;
  title?: string;
  violations?: ErrorViolation[];
}

const GENERIC_HTTP_MESSAGES = new Set([
  'bad request',
  'unauthorized',
  'forbidden',
  'not found',
  'conflict',
  'unprocessable entity',
  'internal server error',
  'service unavailable',
]);
const GENERIC_AXIOS_STATUS_MESSAGE = /^request failed with status code \d{3}$/i;

const normalizeText = (value?: string | null) => value?.trim() ?? '';

const isSpecificText = (value?: string | null) => {
  const normalized = normalizeText(value);
  return normalized.length > 0 && !GENERIC_HTTP_MESSAGES.has(normalized.toLowerCase());
};

const formatViolations = (violations?: ErrorViolation[]) => {
  if (!Array.isArray(violations) || violations.length === 0) {
    return '';
  }

  return violations
    .map((violation) => {
      const field = normalizeText(violation.field);
      const message = normalizeText(violation.message);

      if (!message) {
        return '';
      }

      return field ? `${field}: ${message}` : message;
    })
    .filter(Boolean)
    .join('; ');
};

export const extractServerErrorMessage = (payload?: ErrorPayload) => {
  if (!payload) {
    return '';
  }

  if (isSpecificText(payload.message)) {
    return normalizeText(payload.message);
  }

  if (isSpecificText(payload.detail)) {
    return normalizeText(payload.detail);
  }

  const violations = formatViolations(payload.violations);
  if (violations) {
    return violations;
  }

  if (isSpecificText(payload.title)) {
    return normalizeText(payload.title);
  }

  if (isSpecificText(payload.error)) {
    return normalizeText(payload.error);
  }

  return normalizeText(payload.detail) || normalizeText(payload.message);
};

export const getErrorMessage = (error: unknown, fallback: string) => {
  if (axios.isAxiosError<ErrorPayload>(error)) {
    const payloadMessage = extractServerErrorMessage(error.response?.data);
    if (payloadMessage) {
      return payloadMessage;
    }

    const axiosMessage = normalizeText(error.message);
    if (axiosMessage && !GENERIC_AXIOS_STATUS_MESSAGE.test(axiosMessage)) {
      return axiosMessage;
    }

    return fallback;
  }

  if (error instanceof Error && error.message) {
    return error.message;
  }

  return fallback;
};
