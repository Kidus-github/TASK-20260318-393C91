import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';

export function roleGuard(...roles: Array<'PASSENGER' | 'DISPATCHER' | 'ADMIN'>): CanActivateFn {
  return () => {
    const authService = inject(AuthService);
    const router = inject(Router);
    const role = authService.profile()?.role;
    if (role && roles.includes(role)) {
      return true;
    }
    const fallback = role === 'ADMIN' ? '/admin/settings' : role === 'DISPATCHER' ? '/dispatcher/tasks' : '/passenger/search';
    return router.createUrlTree([fallback]);
  };
}
