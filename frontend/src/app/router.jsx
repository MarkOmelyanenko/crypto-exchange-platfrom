import { createBrowserRouter, Navigate } from 'react-router-dom';
import LoginPage from '../pages/LoginPage';
import RegisterPage from '../pages/RegisterPage';
import DashboardPage from '../pages/DashboardPage';
import AssetsPage from '../pages/AssetsPage';
import AssetDetailPage from '../pages/AssetDetailPage';
import TransactionsPage from '../pages/TransactionsPage';
import DepositPage from '../pages/DepositPage';
import WalletPage from '../pages/WalletPage';
import TradingPage from '../pages/TradingPage';
import App from './App';
import PrivateRoute from '../shared/components/PrivateRoute';

export const router = createBrowserRouter([
  {
    path: '/',
    element: <App />,
    children: [
      {
        index: true,
        element: <Navigate to="/dashboard" replace />,
      },
      {
        path: 'login',
        element: <LoginPage />,
      },
      {
        path: 'register',
        element: <RegisterPage />,
      },
      {
        path: 'dashboard',
        element: (
          <PrivateRoute>
            <DashboardPage />
          </PrivateRoute>
        ),
      },
      {
        path: 'assets',
        element: (
          <PrivateRoute>
            <AssetsPage />
          </PrivateRoute>
        ),
      },
      {
        path: 'assets/:symbol',
        element: (
          <PrivateRoute>
            <AssetDetailPage />
          </PrivateRoute>
        ),
      },
      {
        path: 'transactions',
        element: (
          <PrivateRoute>
            <TransactionsPage />
          </PrivateRoute>
        ),
      },
      {
        path: 'wallet',
        element: (
          <PrivateRoute>
            <WalletPage />
          </PrivateRoute>
        ),
      },
      {
        path: 'deposit',
        element: (
          <PrivateRoute>
            <DepositPage />
          </PrivateRoute>
        ),
      },
      {
        path: 'trade',
        element: (
          <PrivateRoute>
            <TradingPage />
          </PrivateRoute>
        ),
      },
      {
        path: '*',
        element: <Navigate to="/dashboard" replace />,
      },
    ],
  },
]);
