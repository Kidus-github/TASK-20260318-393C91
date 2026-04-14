import { Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { tap } from 'rxjs';
import { AuthResponse, UserProfile } from './models';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly storage = sessionStorage;
  private readonly tokenKey = 'citybus_access_token';
  private readonly refreshKey = 'citybus_refresh_token';
  private readonly profileKey = 'citybus_profile';
  private readonly passwordChangeRequiredKey = 'citybus_password_change_required';

  readonly profile = signal<UserProfile | null>(this.readProfile());

  constructor(private readonly http: HttpClient, private readonly router: Router) {}

  login(username: string, password: string) {
    return this.http.post<AuthResponse>('/api/auth/login', { username, password }).pipe(
      tap(response => this.persist(response))
    );
  }

  changePassword(currentPassword: string, newPassword: string) {
    return this.http.post<{ status: string }>('/api/auth/change-password', {
      currentPassword,
      newPassword
    }).pipe(
      tap(() => this.storage.setItem(this.passwordChangeRequiredKey, 'false'))
    );
  }

  logout() {
    const refreshToken = this.storage.getItem(this.refreshKey);
    if (refreshToken) {
      this.http.post('/api/auth/logout', { refreshToken }).subscribe({ error: () => undefined });
    }
    this.storage.removeItem(this.tokenKey);
    this.storage.removeItem(this.refreshKey);
    this.storage.removeItem(this.profileKey);
    this.storage.removeItem(this.passwordChangeRequiredKey);
    this.profile.set(null);
    void this.router.navigateByUrl('/login');
  }

  accessToken() {
    return this.storage.getItem(this.tokenKey);
  }

  isAuthenticated() {
    return !!this.accessToken() && !!this.profile();
  }

  requiresPasswordChange() {
    return this.storage.getItem(this.passwordChangeRequiredKey) === 'true';
  }

  private persist(response: AuthResponse) {
    this.storage.setItem(this.tokenKey, response.accessToken);
    this.storage.setItem(this.refreshKey, response.refreshToken);
    this.storage.setItem(this.profileKey, JSON.stringify(response.profile));
    this.storage.setItem(this.passwordChangeRequiredKey, response.passwordChangeRequired ? 'true' : 'false');
    this.profile.set(response.profile);
  }

  private readProfile(): UserProfile | null {
    const value = this.storage.getItem(this.profileKey);
    return value ? JSON.parse(value) as UserProfile : null;
  }
}
