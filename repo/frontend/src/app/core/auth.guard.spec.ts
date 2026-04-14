import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { authGuard } from './auth.guard';
import { AuthService } from './auth.service';

describe('authGuard', () => {
  it('redirects unauthenticated users to login', () => {
    const authService = { isAuthenticated: () => false };
    const router = { createUrlTree: jasmine.createSpy('createUrlTree') };
    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: authService },
        { provide: Router, useValue: router }
      ]
    });

    TestBed.runInInjectionContext(() => {
      authGuard({} as never, {} as never);
    });

    expect(router.createUrlTree).toHaveBeenCalledWith(['/login']);
  });
});
