import { Component, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { NgIf } from '@angular/common';
import { AuthService } from '../../core/auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [ReactiveFormsModule, NgIf],
  template: `
    <div style="min-height:100vh; display:grid; place-items:center; padding:24px;">
      <section class="panel" style="width:min(100%, 420px);">
        <p class="muted">City Bus Operation and Service Coordination Platform</p>
        <h1 class="hero-title">Secure LAN Sign-in</h1>
        <div class="error-banner" *ngIf="error()">{{ error() }}</div>
        <form [formGroup]="form" (ngSubmit)="submit()" class="input-grid">
          <label>
            <span class="muted">Username</span>
            <input style="width:100%; padding:12px; border-radius:12px; border:1px solid #cdd6e6; margin-top:8px;" formControlName="username">
          </label>
          <label>
            <span class="muted">Password</span>
            <input type="password" style="width:100%; padding:12px; border-radius:12px; border:1px solid #cdd6e6; margin-top:8px;" formControlName="password">
          </label>
          <button type="submit" [disabled]="form.invalid || loading()" style="padding:12px 16px; border:none; border-radius:12px; background:var(--colorBrandBackground); color:white;">
            {{ loading() ? 'Signing in...' : 'Sign in' }}
          </button>
        </form>
        <p class="muted" style="margin-top:16px">Seeded users: admin, dispatcher, passenger</p>
      </section>
    </div>
  `
})
export class LoginComponent {
  readonly form = this.formBuilder.group({
    username: ['', Validators.required],
    password: ['', [Validators.required, Validators.minLength(8)]]
  });
  readonly loading = signal(false);
  readonly error = signal('');

  constructor(private readonly formBuilder: FormBuilder, private readonly authService: AuthService, private readonly router: Router) {}

  submit() {
    if (this.form.invalid) {
      return;
    }
    this.loading.set(true);
    this.error.set('');
    const { username, password } = this.form.getRawValue();
    this.authService.login(username ?? '', password ?? '').subscribe({
      next: response => {
        this.loading.set(false);
        if (response.passwordChangeRequired) {
          void this.router.navigateByUrl('/auth/change-password');
          return;
        }
        const route = response.profile.role === 'ADMIN'
          ? '/admin/settings'
          : response.profile.role === 'DISPATCHER'
            ? '/dispatcher/tasks'
            : '/passenger/search';
        void this.router.navigateByUrl(route);
      },
      error: error => {
        this.loading.set(false);
        this.error.set(error?.error?.message ?? 'Unable to sign in');
      }
    });
  }
}
