import { useEffect, useState, type ReactNode } from 'react';
import { authService, type RegisterInput, type User } from '../services/authService';
import { AuthContext } from './auth-context';

const USER_STORAGE_KEY = 'user';

const readStoredUser = (): User | null => {
  if (typeof window === 'undefined') {
    return null;
  }

  const raw = window.localStorage.getItem(USER_STORAGE_KEY);
  if (!raw) {
    return null;
  }

  try {
    return JSON.parse(raw) as User;
  } catch {
    window.localStorage.removeItem(USER_STORAGE_KEY);
    return null;
  }
};

const persistUser = (user: User | null) => {
  if (typeof window === 'undefined') {
    return;
  }

  if (!user) {
    window.localStorage.removeItem(USER_STORAGE_KEY);
    return;
  }

  window.localStorage.setItem(USER_STORAGE_KEY, JSON.stringify(user));
};

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(() => readStoredUser());
  const [hasStoredUser] = useState<boolean>(() => readStoredUser() !== null);
  const [isLoading, setIsLoading] = useState<boolean>(hasStoredUser);

  useEffect(() => {
    if (!hasStoredUser) {
      return;
    }

    let cancelled = false;

    void authService.restoreSession()
      .then((session) => {
        if (cancelled) {
          return;
        }

        if (session) {
          setUser(session.user);
          persistUser(session.user);
          return;
        }

        setUser(null);
        persistUser(null);
      })
      .finally(() => {
        if (!cancelled) {
          setIsLoading(false);
        }
      });

    return () => {
      cancelled = true;
    };
  }, [hasStoredUser]);

  const login = async (email: string, password: string) => {
    const session = await authService.login(email, password);
    setUser(session.user);
    persistUser(session.user);
  };

  const register = async (input: RegisterInput) => {
    const session = await authService.register(input);
    setUser(session.user);
    persistUser(session.user);
  };

  const completeGoogleLogin = async (code: string) => {
    const session = await authService.exchangeOAuthCode(code);
    setUser(session.user);
    persistUser(session.user);
  };

  const logout = async () => {
    await authService.logout();
    setUser(null);
    persistUser(null);
  };

  return (
    <AuthContext.Provider
      value={{
        user,
        isAuthenticated: Boolean(user),
        isLoading,
        login,
        register,
        completeGoogleLogin,
        startGoogleLogin: authService.startGoogleLogin,
        logout,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
}
