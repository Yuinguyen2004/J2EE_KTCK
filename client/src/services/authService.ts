import api, { clearAccessToken, refreshAccessToken, setAccessToken } from './api';

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

export const authService = {
  async login(email: string, password: string): Promise<AuthSession> {
    const { data } = await api.post<ServerAuthResponse>('/auth/login', { email, password });
    return applySession(data);
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
