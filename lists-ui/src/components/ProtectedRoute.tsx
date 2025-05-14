import { useEffect, ReactNode } from 'react';
import { Navigate, useLocation } from 'react-router';
import { useALA } from '#/helpers/context/useALA';

export const ProtectedRoute = ({ children }: { children: ReactNode }) => {
  const location = useLocation();
  const { isAuthenticated, showAuthRequiredNotification } = useALA();
  
  useEffect(() => {
    if (!isAuthenticated) {
      // Check if we navigated here directly (not via link click)
      const cameFromLink = location.state?.fromAuthAwareLink;
      
      // Only show notification if not already shown from link click
      if (!cameFromLink) {
        showAuthRequiredNotification();
      }
    }
  }, [isAuthenticated, location, showAuthRequiredNotification]);

  if (!isAuthenticated) {
    // Redirect to login page, but save the location they were trying to access
    return <Navigate to="/" state={{ from: location.pathname }} replace />;
  }

  return children;
};