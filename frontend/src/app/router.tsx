import { createBrowserRouter, Navigate } from 'react-router';
import { AppShell } from '../components/layout/AppShell';
import { FullPageLoader } from '../components/ui/Spinner';
import { ErrorScreen, RouteError } from './ErrorBoundary';
import { PublicOnly, RequireAuth } from './guards';

// Pages are code-split; each chunk loads on first navigation.
const page = (load: () => Promise<{ default: React.ComponentType }>) => async () => ({ Component: (await load()).default });

export const router = createBrowserRouter([
  {
    errorElement: <RouteError />,
    hydrateFallbackElement: <FullPageLoader />,
    children: [
      {
        element: <PublicOnly />,
        children: [
          { path: '/login', lazy: page(() => import('../pages/LoginPage')) },
          { path: '/register', lazy: page(() => import('../pages/RegisterPage')) },
        ],
      },
      {
        element: <RequireAuth />,
        children: [
          {
            element: <AppShell />,
            children: [
              { path: '/', element: <Navigate to="/chat" replace /> },
              // one route for both, so creating a conversation does not remount the chat view
              { path: '/chat/:conversationId?', lazy: page(() => import('../pages/ChatPage')) },
              { path: '/library/:documentId?', lazy: page(() => import('../pages/LibraryPage')) },
              { path: '/settings', lazy: page(() => import('../pages/SettingsPage')) },
            ],
          },
        ],
      },
      { path: '*', element: <ErrorScreen notFound /> },
    ],
  },
]);
