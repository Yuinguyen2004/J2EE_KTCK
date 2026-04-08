import api, { clearAccessToken, refreshAccessToken, setAccessToken } from './api';

const GOOGLE_AUTHORIZATION_PATH = '/oauth2/authorization/google';
type RuntimeAppConfig = {
  authOrigin?: string;
};

type RuntimeConfigWindow = Window & typeof globalThis & {
  __APP_CONFIG__?: RuntimeAppConfig;
};

export type UserRole = 'admin' | 'staff' | 'customer';

interface ServerAuthUser {
  id: string;
  email: string;
  fullName: string;
  role: 'ADMIN' | 'STAFF' | 'CUSTOMER';
}

interface ServerAuthResponse {
  user: ServerAuthUser;
  accessToken: string;
}

export interface User {
  id: string;
  email: string;
  fullName: string;
  role: UserRole;
}

export interface AuthSession {
  user: User;
  accessToken: string;
}

export interface RegisterInput {
  email: string;
  password: string;
  fullName: string;
  phone?: string;
}

const mapRole = (role: ServerAuthUser['role']): UserRole => role.toLowerCase() as UserRole;

const mapUser = (user: ServerAuthUser): User => ({
  id: user.id,
  email: user.email,
  fullName: user.fullName,
  role: mapRole(user.role),
});

const applySession = (payload: ServerAuthResponse): AuthSession => {
  setAccessToken(payload.accessToken);
  return {
    accessToken: payload.accessToken,
    user: mapUser(payload.user),
  };
};

const googleAuthorizationOrigin = (): string | null => {
  if (typeof window === 'undefined') {
    return null;
  }

  const runtimeOrigin = (window as RuntimeConfigWindow).__APP_CONFIG__?.authOrigin?.trim();
  return runtimeOrigin || window.location.origin;
};

export const authService = {
  async login(email: string, password: string): Promise<AuthSession> {
    const { data } = await api.post<ServerAuthResponse>('/auth/login', { email, password });
    return applySession(data);
  },

  async register(input: RegisterInput): Promise<AuthSession> {
    const { data } = await api.post<ServerAuthResponse>('/auth/register', {
      email: input.email.trim(),
      password: input.password,
      fullName: input.fullName.trim(),
      phone: input.phone?.trim() || null,
    });
    return applySession(data);
  },

  async exchangeOAuthCode(code: string): Promise<AuthSession> {
    const { data } = await api.post<ServerAuthResponse>('/auth/oauth2/exchange', { code });
    return applySession(data);
  },

  startGoogleLogin(): void {
    const origin = googleAuthorizationOrigin();
    if (!origin) {
      return;
    }

    const url = new URL(GOOGLE_AUTHORIZATION_PATH, origin);
    window.location.assign(url.toString());
  },

  async getMe(): Promise<User> {
    const { data } = await api.get<ServerAuthUser>('/auth/me');
    return mapUser(data);
  },

  async restoreSession(): Promise<AuthSession | null> {
    const refreshedToken = await refreshAccessToken();
    if (!refreshedToken) {
      return null;
    }

    const user = await this.getMe();
    return {
      accessToken: refreshedToken,
      user,
    };
  },

  async logout(): Promise<void> {
    try {
      await api.post('/auth/logout');
    } finally {
      clearAccessToken();
    }
  },
};
