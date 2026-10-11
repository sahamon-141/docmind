import { Navigate, Outlet, useLocation } from 'react-router';
import { FullPageLoader } from '../components/ui/Spinner';
import { useAuth } from '../providers/auth-context';

/** Only for signed-in users; others are sent to /login and returned afterwards. */
export function RequireAuth() {
  const { status } = useAuth();
  const location = useLocation();
  if (status === 'loading') return <FullPageLoader label="Restoring session" />;
  if (status === 'anonymous') return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  return <Outlet />;
}

/**
 * Login/registration: signed-in users are sent on. This is also what moves the user after a
 * successful sign-in, back to the page RequireAuth redirected them from.
 */
export function PublicOnly() {
  const { status } = useAuth();
  const location = useLocation();
  if (status === 'loading') return <FullPageLoader />;
  if (status === 'authenticated') {
    const from = (location.state as { from?: string } | null)?.from;
    return <Navigate to={from && from !== '/login' ? from : '/chat'} replace />;
  }
  return <Outlet />;
}
