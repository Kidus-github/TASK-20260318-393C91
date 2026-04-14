import { Component, OnDestroy, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DatePipe, NgFor, NgIf } from '@angular/common';
import { Subject, Subscription, catchError, debounceTime, distinctUntilChanged, of, switchMap, tap } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { ReminderPreference, ReminderSubscription, SearchResponse } from '../../core/models';

@Component({
  selector: 'app-search-page',
  standalone: true,
  imports: [FormsModule, NgFor, NgIf, DatePipe],
  template: `
    <div class="toolbar">
      <div>
        <h1 class="hero-title">Passenger Search</h1>
        <p class="muted">Search routes and stops by route number, stop name, keywords, pinyin, or initials.</p>
      </div>
      <span class="status-pill status-ok">Autocomplete live</span>
    </div>

    <div class="panel">
      <h2 class="section-title">Search Bus Network</h2>
      <div class="input-grid">
        <input [(ngModel)]="query" (ngModelChange)="onType($event)" placeholder="Search route 101, Central Station, zhongxin..." style="padding:16px; border:1px solid #d4dce9; border-radius:14px;">
        <div class="list" *ngIf="searchResponse()?.suggestions?.length">
          <div class="card-item" *ngFor="let suggestion of searchResponse()?.suggestions">
            <strong>{{ suggestion.label }}</strong>
            <div class="muted">{{ suggestion.type }} | score {{ suggestion.score }}</div>
          </div>
        </div>
      </div>
    </div>

    <div class="grid two" style="margin-top:24px;">
      <section class="panel">
        <h2 class="section-title">Results</h2>
        <div *ngIf="loading()" class="spinner"></div>
        <div class="error-banner" *ngIf="error()">{{ error() }}</div>
        <div class="list" *ngIf="!loading()">
          <div class="card-item" *ngFor="let item of searchResponse()?.results">
            <strong>{{ item.primaryText }}</strong>
            <div class="muted">{{ item.secondaryText }}</div>
            <div class="muted">{{ item.type }} | score {{ item.score }}</div>
          </div>
        </div>
      </section>

      <section class="panel">
        <h2 class="section-title">Arrival Reminder Preferences</h2>
        <div *ngIf="prefsLoading()" class="spinner"></div>
        <div class="input-grid" *ngIf="preferences()">
          <label class="button-row">
            <input type="checkbox" [checked]="preferenceEnabled()" (change)="updateEnabled($event)">
            <span>Enable arrival reminders</span>
          </label>
          <label>
            <span class="muted">Lead time (minutes)</span>
            <input
              type="number"
              [ngModel]="preferenceLeadMinutes()"
              (ngModelChange)="updatePreferenceField('leadMinutes', $event)"
              min="1"
              max="120"
              style="display:block; width:100%; padding:12px; border:1px solid #d4dce9; border-radius:12px; margin-top:8px;">
          </label>
          <div class="grid two">
            <label>
              <span class="muted">DND Start</span>
              <input
                [ngModel]="preferenceDndStart()"
                (ngModelChange)="updatePreferenceField('dndStart', $event)"
                style="display:block; width:100%; padding:12px; border:1px solid #d4dce9; border-radius:12px; margin-top:8px;">
            </label>
            <label>
              <span class="muted">DND End</span>
              <input
                [ngModel]="preferenceDndEnd()"
                (ngModelChange)="updatePreferenceField('dndEnd', $event)"
                style="display:block; width:100%; padding:12px; border:1px solid #d4dce9; border-radius:12px; margin-top:8px;">
            </label>
          </div>
          <div class="button-row">
            <button style="padding:12px 16px; border:none; border-radius:12px; background:var(--colorBrandBackground); color:white;" (click)="savePreferences()">Save Preferences</button>
            <span class="muted">{{ prefMessage() }}</span>
          </div>
        </div>
      </section>
    </div>

    <section class="panel" style="margin-top:24px;">
      <div class="button-row" style="justify-content:space-between;">
        <div>
          <h2 class="section-title">Reminder Reservations</h2>
          <p class="muted">Create a reservation from current search results and manage check-ins.</p>
        </div>
        <button style="padding:12px 16px; border:none; border-radius:12px; background:#dce8f8;" (click)="reserveFirstResult()" [disabled]="!canReserve()">Reserve First Result</button>
      </div>
      <div class="muted" *ngIf="reservationMessage()">{{ reservationMessage() }}</div>
      <div class="list" *ngIf="reservations().length">
        <article class="card-item" *ngFor="let item of reservations()">
          <div class="button-row" style="justify-content:space-between;">
            <div>
              <strong>{{ item.reservationName }}</strong>
              <div class="muted">Arrival {{ item.scheduledArrivalAt | date:'short' }} | Reminder {{ item.reminderAt | date:'short' }}</div>
              <div class="muted">Sent: {{ item.reminderSent ? 'Yes' : 'No' }} | Missed check-in: {{ item.missedCheckInSent ? 'Yes' : 'No' }}</div>
            </div>
            <div class="button-row">
              <button style="padding:10px 14px; border:none; border-radius:12px; background:#dff6dd;" (click)="checkIn(item.id)" [disabled]="!!item.checkedInAt || item.canceled">Check In</button>
              <button style="padding:10px 14px; border:none; border-radius:12px; background:#fde7e9;" (click)="cancel(item.id)" [disabled]="item.canceled">Cancel</button>
            </div>
          </div>
        </article>
      </div>
    </section>
  `
})
export class SearchPageComponent implements OnDestroy {
  query = '';
  readonly loading = signal(false);
  readonly prefsLoading = signal(true);
  readonly error = signal('');
  readonly prefMessage = signal('');
  readonly reservationMessage = signal('');
  readonly searchResponse = signal<SearchResponse | null>(null);
  readonly preferences = signal<ReminderPreference | null>(null);
  readonly reservations = signal<ReminderSubscription[]>([]);
  private readonly searchInput$ = new Subject<string>();
  private readonly pageSubscriptions = new Subscription();

  constructor(private readonly apiService: ApiService) {
    this.pageSubscriptions.add(
      this.searchInput$.pipe(
        debounceTime(200),
        distinctUntilChanged(),
        tap(value => {
          if (value.trim().length >= 2) {
            this.loading.set(true);
            this.error.set('');
          }
        }),
        switchMap(value => {
          if (value.trim().length < 2) {
            this.loading.set(false);
            return of<SearchResponse | null>(null);
          }
          return this.apiService.search(value).pipe(
            catchError(error => {
              this.error.set(error?.error?.message ?? 'Search failed');
              this.loading.set(false);
              return of<SearchResponse | null>(null);
            })
          );
        })
      ).subscribe(response => {
        this.searchResponse.set(response);
        this.loading.set(false);
      })
    );

    this.pageSubscriptions.add(
      this.apiService.getPreferences().subscribe({
        next: response => {
          this.preferences.set({ ...response });
          this.prefsLoading.set(false);
        },
        error: () => this.prefsLoading.set(false)
      })
    );
    this.reloadSubscriptions();
  }

  onType(value: string) {
    this.searchInput$.next(value);
  }

  updateEnabled(event: Event) {
    const checked = (event.target as HTMLInputElement).checked;
    this.preferences.update(value => value ? { ...value, enabled: checked } : value);
  }

  preferenceEnabled() {
    return this.preferences()?.enabled ?? false;
  }

  preferenceLeadMinutes() {
    return this.preferences()?.leadMinutes ?? 10;
  }

  preferenceDndStart() {
    return this.preferences()?.dndStart ?? '22:00';
  }

  preferenceDndEnd() {
    return this.preferences()?.dndEnd ?? '07:00';
  }

  updatePreferenceField(field: 'leadMinutes' | 'dndStart' | 'dndEnd', value: string | number) {
    this.preferences.update(current => {
      if (!current) {
        return current;
      }
      if (field === 'leadMinutes') {
        return { ...current, leadMinutes: Number(value) };
      }
      return { ...current, [field]: String(value) };
    });
  }

  savePreferences() {
    const preference = this.preferences();
    if (!preference) {
      return;
    }
    this.apiService.updatePreferences(preference).subscribe({
      next: response => {
        this.preferences.set({ ...response });
        this.prefMessage.set('Preferences saved.');
      },
      error: error => {
        this.prefMessage.set(error?.error?.message ?? 'Unable to save preferences.');
      }
    });
  }

  canReserve() {
    return !!this.searchResponse()?.results?.length;
  }

  reserveFirstResult() {
    if (!this.searchResponse()?.results?.length) {
      return;
    }
    const stop = this.searchResponse()?.results.find(item => item.type === 'STOP');
    const route = this.searchResponse()?.results.find(item => item.type === 'ROUTE');
    if (!route || !stop) {
      this.reservationMessage.set('Search needs both a route and a stop before reserving a reminder.');
      return;
    }
    const scheduledArrivalAt = new Date(Date.now() + 15 * 60 * 1000).toISOString();
    this.apiService.createSubscription({
      routeId: route.id,
      stopId: stop.id,
      reservationName: `${route.primaryText} @ ${stop.primaryText}`,
      scheduledArrivalAt
    }).subscribe({
      next: subscription => {
        this.reservations.update(items => [...items, subscription]);
        this.reservationMessage.set('Reservation created. A confirmation message has been queued.');
      },
      error: error => this.reservationMessage.set(error?.error?.message ?? 'Unable to reserve reminder.')
    });
  }

  checkIn(id: string) {
    this.apiService.checkInSubscription(id).subscribe({
      next: subscription => {
        this.reservations.update(items => items.map(item => item.id === id ? subscription : item));
        this.reservationMessage.set('Check-in recorded.');
      },
      error: error => this.reservationMessage.set(error?.error?.message ?? 'Unable to check in.')
    });
  }

  cancel(id: string) {
    this.apiService.cancelSubscription(id).subscribe({
      next: subscription => {
        this.reservations.update(items => items.map(item => item.id === id ? subscription : item));
        this.reservationMessage.set('Reservation cancelled.');
      },
      error: error => this.reservationMessage.set(error?.error?.message ?? 'Unable to cancel reminder.')
    });
  }

  private reloadSubscriptions() {
    this.pageSubscriptions.add(
      this.apiService.subscriptions().subscribe({
        next: response => this.reservations.set(response),
        error: () => undefined
      })
    );
  }

  ngOnDestroy() {
    this.pageSubscriptions.unsubscribe();
  }
}
