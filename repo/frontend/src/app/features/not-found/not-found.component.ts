import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-not-found',
  standalone: true,
  imports: [RouterLink],
  template: `
    <section class="panel">
      <h1 class="hero-title">Page Not Found</h1>
      <p class="muted">The requested route does not exist in this deployment.</p>
      <a routerLink="/passenger/search" class="nav-link active" style="display:inline-block; margin-top:16px;">Return to Dashboard</a>
    </section>
  `
})
export class NotFoundComponent {}
