import { Routes } from '@angular/router';
import { LoginComponent } from './features/auth/login.component';
import { ShellComponent } from './layout/shell.component';
import { authGuard } from './core/auth.guard';
import { roleGuard } from './core/role.guard';
import { SearchPageComponent } from './features/passenger/search-page.component';
import { MessageCenterComponent } from './features/passenger/message-center.component';
import { DispatcherDashboardComponent } from './features/dispatcher/dispatcher-dashboard.component';
import { AdminSettingsComponent } from './features/admin/admin-settings.component';
import { NotFoundComponent } from './features/not-found/not-found.component';
import { RoleHomeComponent } from './features/auth/role-home.component';
import { ChangePasswordComponent } from './features/auth/change-password.component';

export const routes: Routes = [
  { path: 'login', component: LoginComponent },
  {
    path: '',
    component: ShellComponent,
    canActivate: [authGuard],
    children: [
      { path: 'passenger/search', component: SearchPageComponent, canActivate: [roleGuard('PASSENGER')] },
      { path: 'passenger/messages', component: MessageCenterComponent, canActivate: [roleGuard('PASSENGER')] },
      { path: 'dispatcher/tasks', component: DispatcherDashboardComponent, canActivate: [roleGuard('DISPATCHER')] },
      { path: 'admin/settings', component: AdminSettingsComponent, canActivate: [roleGuard('ADMIN')] },
      { path: 'auth/change-password', component: ChangePasswordComponent },
      { path: '', pathMatch: 'full', component: RoleHomeComponent }
    ]
  },
  { path: '**', component: NotFoundComponent }
];
