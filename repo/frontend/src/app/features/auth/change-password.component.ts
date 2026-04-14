import { Component, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { NgIf } from '@angular/common';
import { Router } from '@angular/router';
import { AuthService } from '../../core/auth.service';

@Component({
  selector: 'app-change-password',
  standalone: true,
  imports: [ReactiveFormsModule, NgIf],
  template: `
    <div style="min-height:100vh; display:grid; place-items:center; padding:24px;">
      <section class="panel" style="width:min(100%, 480px);">
        <p class="muted">Security action required</p>
        <h1 class="hero-title">Change Temporary Password</h1>
        <div class="error-banner" *ngIf="error()">{{ error() }}</div>
        <form [formGroup]="form" (ngSubmit)="submit()" class="input-grid">
          <label>
            <span class="muted">Current password</span>
            <input type="password" formControlName="currentPassword" style="width:100%; padding:12px; border-radius:12px; border:1px solid #cdd6e6; margin-top:8px;">
          </label>
          <label>
            <span class="muted">New password</span>
            <input type="password" formControlName="newPassword" style="width:100%; padding:12px; border-radius:12px; border:1px solid #cdd6e6; margin-top:8px;">
          </label>
          <label>
            <span class="muted">Confirm new password</span>
            <input type="password" formControlName="confirmPassword" style="width:100%; padding:12px; border-radius:12px; border:1px solid #cdd6e6; margin-top:8px;">
          </label>
          <button type="submit" [disabled]="form.invalid || loading()" style="padding:12px 16px; border:none; border-radius:12px; background:var(--colorBrandBackground); color:white;">
            {{ loading() ? 'Updating...' : 'Update password' }}
          </button>
        </form>
      </section>
    </div>
  `
})
export class ChangePasswordComponent {
  readonly form = this.formBuilder.group({
    currentPassword: ['', [Validators.required, Validators.minLength(8)]],
    newPassword: ['', [Validators.required, Validators.minLength(8)]],
    confirmPassword: ['', [Validators.required, Validators.minLength(8)]]
  });
  readonly loading = signal(false);
  readonly error = signal('');

  constructor(
    private readonly formBuilder: FormBuilder,
    private readonly authService: AuthService,
    private readonly router: Router
  ) {}

  submit() {
    if (this.form.invalid) {
      return;
    }
    const { currentPassword, newPassword, confirmPassword } = this.form.getRawValue();
    if (newPassword !== confirmPassword) {
      this.error.set('New password confirmation does not match');
      return;
    }
    this.loading.set(true);
    this.error.set('');
    this.authService.changePassword(currentPassword ?? '', newPassword ?? '').subscribe({
      next: () => {
        this.loading.set(false);
        this.authService.logout();
      },
      error: error => {
        this.loading.set(false);
        this.error.set(error?.error?.message ?? 'Unable to change password');
      }
    });
  }
}
