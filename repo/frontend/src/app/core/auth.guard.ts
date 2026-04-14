import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';

export const authGuard: CanActivateFn = (_route, state) => {
  const authService = inject(AuthService);
  const router = inject(Router);
  if (!authService.isAuthenticated()) {
    return router.createUrlTree(['/login']);
  }
  if (authService.requiresPasswordChange() && state.url !== '/auth/change-password') {
    return router.createUrlTree(['/auth/change-password']);
  }
  return true;
};
