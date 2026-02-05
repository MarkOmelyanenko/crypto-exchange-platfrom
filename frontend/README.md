# Crypto Exchange Simulator - Frontend

React frontend application for the crypto exchange simulator.

## Prerequisites

- Node.js 18+ and npm

## Setup

### 1. Install Dependencies

```bash
npm install
```

**Note**: The dashboard uses Recharts for price charts. If you haven't installed it yet:
```bash
npm install recharts
```

### 2. Environment Configuration

Create a `.env` file in the frontend directory with:

```bash
VITE_API_BASE_URL=http://localhost:8080
```

This sets the base URL for API requests. The default is `http://localhost:8080` if not specified.

### 3. Development

Start the development server:

```bash
npm run dev
```

The application will be available at `http://localhost:5173` (or the next available port).

### 4. Build

Build for production:

```bash
npm run build
```

The production build will be in the `dist/` directory.

## Project Structure

```
src/
├── app/
│   ├── App.jsx          # Main layout component (header, sidebar, main content)
│   └── router.jsx       # Route definitions
├── pages/
│   ├── LoginPage.jsx
│   ├── RegisterPage.jsx
│   ├── DashboardPage.jsx
│   ├── AssetsPage.jsx
│   └── TransactionsPage.jsx
├── shared/
│   └── api/
│       └── apiClient.js  # Axios instance with base URL configuration
├── main.jsx             # Application entry point
└── index.css            # Global styles
```

## Routes

- `/` - Redirects to `/dashboard`
- `/login` - Login page
- `/register` - Registration page
- `/dashboard` - Dashboard page
- `/assets` - Assets management page
- `/transactions` - Transactions history page

## API Client

The API client is configured in `src/shared/api/apiClient.js`:
- Base URL: Read from `VITE_API_BASE_URL` environment variable
- Timeout: 10000ms (10 seconds)

Example usage:
```javascript
import apiClient from '../shared/api/apiClient';

// GET request
const response = await apiClient.get('/api/markets');

// POST request
const response = await apiClient.post('/api/orders', orderData);
```

## Authentication

The frontend implements end-to-end authentication with JWT tokens:

### Authentication Flow

1. **Registration**: Users can register with login, email, and password (minimum 8 characters)
2. **Login**: Users can login using either their login or email address
3. **Token Storage**: JWT access tokens are stored in `localStorage` as `auth_token`
4. **Protected Routes**: Dashboard, Assets, and Transactions pages require authentication
5. **Auto-redirect**: Unauthenticated users are redirected to `/login`
6. **Token Refresh**: On app start, if a token exists, the app attempts to load user data

### Auth Context

The `AuthContext` provides:
- `user` - Current user object (null if not authenticated)
- `loading` - Loading state during authentication check
- `login(credentials)` - Login function
- `register(userData)` - Registration function
- `logout()` - Logout function (clears token and redirects)
- `isAuthenticated` - Boolean indicating authentication status

### API Client

The API client (`apiClient.js`) automatically:
- Adds `Authorization: Bearer <token>` header to all requests
- Handles 401 responses by clearing token and redirecting to login
- Uses base URL from `VITE_API_BASE_URL` environment variable

### User Menu

When authenticated, a user menu appears in the top-right corner showing:
- User login/email
- Logout button

## Features

- React Router for navigation
- JWT authentication with protected routes
- **Dashboard** with portfolio summary, holdings table, price charts, and system status
- Responsive layout with header and sidebar
- Environment-based API configuration
- Clean, simple styling with CSS
- Form validation and error handling

## Dashboard

The dashboard (`/dashboard`) provides a comprehensive view of the user's portfolio:

- **Portfolio Summary Cards**: Total value, available cash, unrealized PnL, realized PnL
- **Holdings Table**: All assets with quantities, prices, market values, and PnL (sortable)
- **Price Trends Chart**: 24-hour price history for top holdings (using Recharts)
- **Recent Transactions**: Latest 10 transactions with link to full history
- **System Status**: Health indicators for API, DB, and Kafka

The dashboard automatically polls for price updates every 10 seconds and handles empty states gracefully.
