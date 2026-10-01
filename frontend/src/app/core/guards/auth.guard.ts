import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';
import { isAuthRole } from '../auth/jwt';

export const authGuard: CanActivateFn = route => {
  const auth = inject(AuthService);
  const router = inject(Router);
  if (!auth.token()) return router.createUrlTree(['/login']);
  const roles: unknown = route.data['roles'];
  if (roles !== undefined && (!Array.isArray(roles) || !roles.every(isAuthRole)
    || !roles.some(role => auth.hasRole(role)))) return router.createUrlTree(['/']);
  return true;
};
