import { Component, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { JsonPipe, NgFor, NgIf } from '@angular/common';
import { ApiService } from '../../core/api.service';
import { TaskItem } from '../../core/models';

@Component({
  selector: 'app-dispatcher-dashboard',
  standalone: true,
  imports: [NgFor, NgIf, JsonPipe, FormsModule],
  template: `
    <div class="toolbar">
      <div>
        <h1 class="hero-title">Dispatcher Task Dashboard</h1>
        <p class="muted">Approve route changes, reminder rule configuration updates, and abnormal data review tasks.</p>
      </div>
      <span class="status-pill status-ok">24h escalation active</span>
    </div>

    <section class="panel">
      <div class="button-row" style="justify-content:space-between;">
        <div>
          <h2 class="section-title">Batch Processing</h2>
          <p class="muted">Select multiple leased or pool-visible tasks for batch approval or rejection.</p>
        </div>
        <div class="button-row">
          <button style="padding:10px 14px; border:none; border-radius:12px; background:#dff6dd;" (click)="batch('APPROVE')" [disabled]="!selectedIds().length">Batch Approve</button>
          <button style="padding:10px 14px; border:none; border-radius:12px; background:#fde7e9;" (click)="batch('REJECT')" [disabled]="!selectedIds().length">Batch Reject</button>
        </div>
      </div>
      <div class="muted" *ngIf="batchMessage()">{{ batchMessage() }}</div>
    </section>

    <section class="panel" style="margin-top:24px;">
      <div *ngIf="loading()" class="spinner"></div>
      <div class="error-banner" *ngIf="error()">{{ error() }}</div>
      <div class="list" *ngIf="!loading()">
        <article class="card-item" *ngFor="let task of tasks()">
          <div class="button-row" style="justify-content:space-between;">
            <div class="button-row">
              <input type="checkbox" [checked]="isSelected(task.id)" (change)="toggleSelection(task.id, $event)">
                <div>
                <strong>{{ task.title }}</strong>
                <div class="muted">{{ task.taskType }} | {{ task.state }} | approvals {{ task.approvalCount }}/{{ task.requiredApprovals }}</div>
                <div class="muted">Mode {{ task.approvalMode }} | current approvals {{ task.currentApprovals }} | progress {{ task.progressStep }}/{{ task.progressTotal }} | resubmissions {{ task.resubmissionCount }} | escalated {{ task.escalated ? 'Yes' : 'No' }}</div>
                <div class="muted" *ngIf="task.parentTaskId">Parent Task {{ task.parentTaskId }} | Group {{ task.approvalGroupId }}</div>
              </div>
            </div>
            <div class="button-row">
              <button style="padding:10px 14px; border:none; border-radius:12px; background:#dce8f8;" (click)="claim(task.id)">Open</button>
              <button style="padding:10px 14px; border:none; border-radius:12px; background:#dff6dd;" (click)="decide(task.id, 'APPROVE')">Approve</button>
              <button style="padding:10px 14px; border:none; border-radius:12px; background:#fde7e9;" (click)="decide(task.id, 'REJECT')">Reject</button>
              <button style="padding:10px 14px; border:none; border-radius:12px; background:#fff4ce;" (click)="decide(task.id, 'RETURN')">Return</button>
              <button style="padding:10px 14px; border:none; border-radius:12px; background:#e9e5ff;" (click)="resubmit(task.id)" [disabled]="task.state !== 'RETURNED'">Resubmit</button>
            </div>
          </div>
          <pre style="white-space:pre-wrap; margin:12px 0 0;">{{ task.payload }}</pre>
        </article>
      </div>
    </section>
  `
})
export class DispatcherDashboardComponent {
  readonly loading = signal(true);
  readonly error = signal('');
  readonly batchMessage = signal('');
  readonly tasks = signal<TaskItem[]>([]);
  readonly selectedIds = signal<string[]>([]);

  constructor(private readonly apiService: ApiService) {
    this.reload();
  }

  reload() {
    this.apiService.tasks().subscribe({
      next: response => {
        this.tasks.set(response);
        this.loading.set(false);
      },
      error: error => {
        this.error.set(error?.error?.message ?? 'Unable to load tasks');
        this.loading.set(false);
      }
    });
  }

  claim(id: string) {
    this.apiService.claimTask(id).subscribe({
      next: () => this.reload(),
      error: error => this.error.set(error?.error?.message ?? 'Unable to claim task')
    });
  }

  decide(id: string, decision: string) {
    this.apiService.decideTask(id, decision, `${decision} via dashboard`).subscribe({
      next: () => this.reload(),
      error: error => this.error.set(error?.error?.message ?? 'Unable to submit decision')
    });
  }

  resubmit(id: string) {
    this.apiService.resubmitTask(id, 'Resubmitted from dashboard').subscribe({
      next: () => this.reload(),
      error: error => this.error.set(error?.error?.message ?? 'Unable to resubmit task')
    });
  }

  batch(decision: string) {
    this.apiService.batchDecide(this.selectedIds(), decision, `${decision} via batch dashboard`).subscribe({
      next: response => {
        this.batchMessage.set(`Batch ${decision.toLowerCase()} completed: ${response.succeeded.length} succeeded, ${response.failed.length} failed.`);
        this.selectedIds.set([]);
        this.reload();
      },
      error: error => this.error.set(error?.error?.message ?? 'Unable to run batch action')
    });
  }

  isSelected(id: string) {
    return this.selectedIds().includes(id);
  }

  toggleSelection(id: string, event: Event) {
    const checked = (event.target as HTMLInputElement).checked;
    this.selectedIds.update(ids => checked ? [...ids, id] : ids.filter(item => item !== id));
  }
}
