import { Component, inject } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '../../core/auth.service';

@Component({
  selector: 'app-role-home',
  standalone: true,
  template: `<div class="panel"><div class="spinner"></div></div>`
})
export class RoleHomeComponent {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  constructor() {
    const role = this.auth.profile()?.role;
    const target = role === 'ADMIN'
      ? '/admin/settings'
      : role === 'DISPATCHER'
        ? '/dispatcher/tasks'
        : '/passenger/search';
    void this.router.navigateByUrl(target);
  }
}
