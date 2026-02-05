# Crypto Exchange Simulator - Frontend

React frontend application for the crypto exchange simulator.

## Prerequisites

- Node.js 18+ and npm

## Setup

### 1. Install Dependencies

```bash
npm install
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

## Features

- React Router for navigation
- Responsive layout with header and sidebar
- Environment-based API configuration
- Clean, simple styling with CSS

## Next Steps

- Authentication logic
- Form handling
- API integration
- State management (if needed)
