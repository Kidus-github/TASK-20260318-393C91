import { Component, OnDestroy, signal } from '@angular/core';
import { DatePipe, NgFor, NgIf } from '@angular/common';
import { ApiService } from '../../core/api.service';
import { MessageItem } from '../../core/models';

@Component({
  selector: 'app-message-center',
  standalone: true,
  imports: [NgFor, NgIf, DatePipe],
  template: `
    <div class="toolbar">
      <div>
        <h1 class="hero-title">Message Center</h1>
        <p class="muted">Unified reminders, reservation notices, delay updates, and missed check-ins.</p>
      </div>
      <span class="status-pill status-warn">Unread {{ unreadCount() }}</span>
    </div>

    <section class="panel">
      <div *ngIf="loading()" class="spinner"></div>
      <div class="list" *ngIf="!loading()">
        <article class="card-item" *ngFor="let message of messages()">
          <div class="button-row" style="justify-content:space-between;">
            <strong>{{ message.title }}</strong>
            <span class="status-pill" [class.status-ok]="message.read" [class.status-warn]="!message.read">{{ message.read ? 'Read' : 'Unread' }}</span>
          </div>
          <p>{{ message.content }}</p>
          <div class="button-row" style="justify-content:space-between;">
            <span class="muted">{{ message.createdAt | date:'medium' }}</span>
            <button style="padding:10px 14px; border:none; border-radius:12px; background:#dce8f8;" (click)="markRead(message)" [disabled]="message.read">Mark as Read</button>
          </div>
        </article>
      </div>
    </section>
  `
})
export class MessageCenterComponent implements OnDestroy {
  readonly loading = signal(true);
  readonly messages = signal<MessageItem[]>([]);
  readonly unreadCount = signal(0);
  private readonly channel = typeof BroadcastChannel !== 'undefined' ? new BroadcastChannel('citybus-messages') : null;

  constructor(private readonly apiService: ApiService) {
    this.channel?.addEventListener('message', event => {
      const messageId = event.data?.id as string | undefined;
      if (messageId) {
        this.messages.update(items => items.map(item => item.id === messageId ? { ...item, read: true } : item));
        this.updateUnread();
      }
    });
    this.reload();
  }

  reload() {
    this.apiService.messages().subscribe({
      next: response => {
        this.messages.set(response);
        this.updateUnread();
        this.loading.set(false);
      },
      error: () => this.loading.set(false)
    });
  }

  markRead(message: MessageItem) {
    this.apiService.markRead(message.id).subscribe(() => {
      this.messages.update(items => items.map(item => item.id === message.id ? { ...item, read: true } : item));
      this.updateUnread();
      this.channel?.postMessage({ id: message.id });
    });
  }

  updateUnread() {
    this.unreadCount.set(this.messages().filter(item => !item.read).length);
  }

  ngOnDestroy() {
    this.channel?.close();
  }
}
