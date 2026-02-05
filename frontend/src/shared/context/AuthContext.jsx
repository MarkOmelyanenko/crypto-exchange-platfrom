import { createContext, useContext, useState, useEffect } from 'react';
import { login as loginApi, register as registerApi, logout as logoutApi } from '../api/services/authService';
import { me } from '../api/services/userService';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    // Check if user is already logged in
    const token = localStorage.getItem('auth_token');
    if (token) {
      loadUser();
    } else {
      setLoading(false);
    }
  }, []);

  const loadUser = async () => {
    try {
      const userData = await me();
      setUser(userData);
    } catch (err) {
      // Token might be invalid, clear it
      localStorage.removeItem('auth_token');
      setUser(null);
    } finally {
      setLoading(false);
    }
  };

  const login = async (credentials) => {
    const response = await loginApi(credentials);
    await loadUser();
    return response;
  };

  const register = async (userData) => {
    const response = await registerApi(userData);
    // Don't automatically log in after registration - user should login manually
    // Clear the token so they're not automatically logged in
    localStorage.removeItem('auth_token');
    return response;
  };

  const logout = () => {
    logoutApi();
    setUser(null);
    // Redirect will be handled by PrivateRoute or navigation
    window.location.href = '/login';
  };

  const value = {
    user,
    loading,
    login,
    register,
    logout,
    loadUser,
    isAuthenticated: !!user,
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
}
