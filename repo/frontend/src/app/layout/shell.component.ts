import { Component, computed, signal } from '@angular/core';
import { NgClass, NgIf } from '@angular/common';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../core/auth.service';
import { ApiService } from '../core/api.service';

@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, NgClass, NgIf],
  template: `
    <div class="page-shell">
      <aside class="sidebar">
        <p class="muted">City Bus Platform</p>
        <h1 class="hero-title" style="font-size:28px">Operations Hub</h1>
        <p class="muted">{{ auth.profile()?.displayName }} | {{ auth.profile()?.role }}</p>
        <nav style="margin-top:24px">
          <a class="nav-link" routerLink="/passenger/search" routerLinkActive="active" *ngIf="showPassenger()">Passenger Search</a>
          <a class="nav-link" routerLink="/passenger/messages" routerLinkActive="active" *ngIf="showPassenger()">Message Center</a>
          <a class="nav-link" routerLink="/dispatcher/tasks" routerLinkActive="active" *ngIf="showDispatcher()">Dispatcher Tasks</a>
          <a class="nav-link" routerLink="/admin/settings" routerLinkActive="active" *ngIf="showAdmin()">Admin Settings</a>
        </nav>
        <div class="panel" style="margin-top:24px; min-height:160px;">
          <h2 class="section-title">Service Status</h2>
          <p class="muted">LAN-only deployment with local queue and PostgreSQL.</p>
          <span class="status-pill status-ok">Healthy baseline</span>
        </div>
        <div class="button-row" style="margin-top:16px">
          <button appearance="accent" (click)="logout()">Sign out</button>
        </div>
      </aside>
      <main class="main-canvas">
        <div class="error-banner" *ngIf="updateAvailable()">
          A newer client version is available.
          <button style="margin-left:12px; padding:8px 12px; border:none; border-radius:10px;" (click)="reload()">Reload</button>
        </div>
        <router-outlet></router-outlet>
      </main>
    </div>
  `
})
export class ShellComponent {
  readonly showPassenger = computed(() => this.auth.profile()?.role === 'PASSENGER');
  readonly showDispatcher = computed(() => this.auth.profile()?.role === 'DISPATCHER');
  readonly showAdmin = computed(() => this.auth.profile()?.role === 'ADMIN');
  readonly remoteVersion = signal<string | null>(null);
  readonly updateAvailable = computed(() => !!this.remoteVersion() && this.remoteVersion() !== this.currentVersion);
  private readonly currentVersion = '1.0.0';

  constructor(public readonly auth: AuthService, private readonly apiService: ApiService) {
    this.checkVersion();
    window.setInterval(() => this.checkVersion(), 300000);
  }

  logout() {
    this.auth.logout();
  }

  reload() {
    window.location.reload();
  }

  private checkVersion() {
    this.apiService.systemVersion().subscribe({
      next: response => this.remoteVersion.set(response.version)
    });
  }
}
