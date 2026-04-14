import { Component, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { JsonPipe, NgFor, NgIf } from '@angular/common';
import { ApiService } from '../../core/api.service';

@Component({
  selector: 'app-admin-settings',
  standalone: true,
  imports: [NgFor, NgIf, JsonPipe, FormsModule],
  template: `
    <div class="toolbar">
      <div>
        <h1 class="hero-title">Administrator Control Surface</h1>
        <p class="muted">Maintain templates, sorting weights, dictionaries, cleaning rules, parsing versions, alerts, and local user operations.</p>
      </div>
      <span class="status-pill status-ok">Audit logging enabled</span>
    </div>

    <div class="error-banner" *ngIf="message()">{{ message() }}</div>

    <div class="grid two">
      <section class="panel">
        <h2 class="section-title">Notification Templates</h2>
        <div *ngIf="loading()" class="spinner"></div>
        <div class="list">
          <div class="card-item" *ngFor="let item of templates()">
            <strong>{{ item.templateKey }}</strong>
            <input [(ngModel)]="item.titleTemplate" style="display:block; width:100%; padding:10px; border:1px solid #d4dce9; border-radius:10px; margin-top:8px;">
            <textarea [(ngModel)]="item.contentTemplate" rows="3" style="display:block; width:100%; padding:10px; border:1px solid #d4dce9; border-radius:10px; margin-top:8px;"></textarea>
            <button style="margin-top:10px; padding:10px 14px; border:none; border-radius:12px; background:#dce8f8;" (click)="saveTemplate(item)">Save Template</button>
          </div>
        </div>
      </section>

      <section class="panel">
        <h2 class="section-title">Search Weight Configuration</h2>
        <div class="input-grid" *ngIf="searchConfig() as config">
          <label><span class="muted">Exact</span><input [(ngModel)]="config.exactWeight" type="number" style="display:block; width:100%; padding:10px; border:1px solid #d4dce9; border-radius:10px; margin-top:8px;"></label>
          <label><span class="muted">Prefix</span><input [(ngModel)]="config.prefixWeight" type="number" style="display:block; width:100%; padding:10px; border:1px solid #d4dce9; border-radius:10px; margin-top:8px;"></label>
          <label><span class="muted">Pinyin</span><input [(ngModel)]="config.pinyinWeight" type="number" style="display:block; width:100%; padding:10px; border:1px solid #d4dce9; border-radius:10px; margin-top:8px;"></label>
          <label><span class="muted">Popularity</span><input [(ngModel)]="config.popularityWeight" type="number" style="display:block; width:100%; padding:10px; border:1px solid #d4dce9; border-radius:10px; margin-top:8px;"></label>
          <label><span class="muted">Frequency</span><input [(ngModel)]="config.frequencyWeight" type="number" style="display:block; width:100%; padding:10px; border:1px solid #d4dce9; border-radius:10px; margin-top:8px;"></label>
          <button style="padding:10px 14px; border:none; border-radius:12px; background:#dff6dd;" (click)="saveSearchConfig(config)">Save Weights</button>
        </div>
      </section>
    </div>

    <div class="grid two" style="margin-top:24px;">
      <section class="panel">
        <h2 class="section-title">Field Dictionaries</h2>
        <div class="list">
          <div class="card-item" *ngFor="let group of dictionaries()">
            <strong>{{ group.dictionaryType }}</strong>
            <div class="card-item" *ngFor="let item of group.items" style="margin-top:8px;">
              <div class="muted">{{ item.sourceValue }}</div>
              <div class="button-row" style="justify-content:space-between;">
                <input [(ngModel)]="item.standardizedValue" style="flex:1; padding:10px; border:1px solid #d4dce9; border-radius:10px;">
                <button style="padding:10px 14px; border:none; border-radius:12px; background:#dce8f8;" (click)="saveDictionary(item)">Save</button>
              </div>
            </div>
          </div>
        </div>
      </section>

      <section class="panel">
        <h2 class="section-title">Cleaning Rules</h2>
        <div class="list">
          <div class="card-item" *ngFor="let rule of cleaningRules()">
            <strong>{{ rule.ruleKey }}</strong>
            <div class="muted">{{ rule.fieldName }}</div>
            <input [(ngModel)]="rule.pattern" style="display:block; width:100%; padding:10px; border:1px solid #d4dce9; border-radius:10px; margin-top:8px;">
            <input [(ngModel)]="rule.replacementValue" style="display:block; width:100%; padding:10px; border:1px solid #d4dce9; border-radius:10px; margin-top:8px;">
            <label class="button-row" style="margin-top:8px;">
              <input type="checkbox" [(ngModel)]="rule.enabled">
              <span>Enabled</span>
            </label>
            <button style="margin-top:10px; padding:10px 14px; border:none; border-radius:12px; background:#dce8f8;" (click)="saveCleaningRule(rule)">Save Rule</button>
          </div>
        </div>
      </section>
    </div>

    <div class="grid two" style="margin-top:24px;">
      <section class="panel">
        <h2 class="section-title">Parsing Templates</h2>
        <div class="list">
          <div class="card-item" *ngFor="let item of parsingTemplates()">
            <strong>{{ item.templateName }}</strong>
            <div class="muted">{{ item.templateType }} | {{ item.semanticVersion }} | revision {{ item.revision }} | hash {{ item.contentHash }}</div>
          </div>
        </div>
        <div class="input-grid" style="margin-top:12px;">
          <input [(ngModel)]="parsingDraft.templateName" placeholder="Template name" style="padding:10px; border:1px solid #d4dce9; border-radius:10px;">
          <input [(ngModel)]="parsingDraft.templateType" placeholder="JSON or HTML" style="padding:10px; border:1px solid #d4dce9; border-radius:10px;">
          <input [(ngModel)]="parsingDraft.semanticVersion" placeholder="Semantic version" style="padding:10px; border:1px solid #d4dce9; border-radius:10px;">
          <textarea [(ngModel)]="parsingDraft.body" rows="6" style="padding:10px; border:1px solid #d4dce9; border-radius:10px;"></textarea>
          <label class="button-row"><input type="checkbox" [(ngModel)]="parsingDraft.active"><span>Active version</span></label>
          <button style="padding:10px 14px; border:none; border-radius:12px; background:#dff6dd;" (click)="saveParsingTemplate()">Save Parsing Template</button>
        </div>
      </section>

      <section class="panel">
        <h2 class="section-title">Parse Source</h2>
        <select [(ngModel)]="parseDraft.templateId" style="display:block; width:100%; padding:10px; border:1px solid #d4dce9; border-radius:10px;">
          <option value="">Choose template</option>
          <option *ngFor="let item of parsingTemplates()" [value]="item.id">{{ item.templateName }}</option>
        </select>
        <input [(ngModel)]="parseDraft.sourceReference" placeholder="Source reference" style="display:block; width:100%; padding:10px; border:1px solid #d4dce9; border-radius:10px; margin-top:8px;">
        <textarea [(ngModel)]="parseDraft.sourceBody" rows="8" style="display:block; width:100%; padding:10px; border:1px solid #d4dce9; border-radius:10px; margin-top:8px;"></textarea>
        <button style="margin-top:10px; padding:10px 14px; border:none; border-radius:12px; background:#dce8f8;" (click)="parseSource()">Run Parse</button>
        <pre style="white-space:pre-wrap; margin-top:12px;">{{ parseResult() | json }}</pre>
      </section>
    </div>

    <section class="panel" style="margin-top:24px;">
      <h2 class="section-title">User Maintenance</h2>
      <p class="muted" *ngIf="resetMessage()">{{ resetMessage() }}</p>
      <div class="button-row" *ngIf="pendingResetToken()">
        <button style="padding:10px 14px; border:none; border-radius:12px; background:#fff4ce;" (click)="revealTemporaryPassword()">Reveal Temporary Password Once</button>
        <span class="muted" *ngIf="revealedPassword()">Temporary password: {{ revealedPassword() }}</span>
      </div>
      <div class="list">
        <div class="card-item" *ngFor="let user of users()">
          <div class="button-row" style="justify-content:space-between;">
            <div>
              <strong>{{ user.displayName }}</strong>
              <div class="muted">{{ user.username }} | {{ user.role }}</div>
            </div>
            <button style="padding:10px 14px; border:none; border-radius:12px; background:#dce8f8;" (click)="resetPassword(user.username)">Issue Temp Password</button>
          </div>
        </div>
      </div>
    </section>

    <section class="panel" style="margin-top:24px;">
      <h2 class="section-title">Local Alerts</h2>
      <div class="list">
        <div class="card-item" *ngFor="let alert of alerts()">
          <strong>{{ alert.alertType }} | {{ alert.severity }}</strong>
          <div class="muted">{{ alert.summary }}</div>
          <p>{{ alert.details }}</p>
        </div>
      </div>
    </section>

    <section class="panel" style="margin-top:24px;">
      <h2 class="section-title">Diagnostic Reports</h2>
      <div class="list">
        <div class="card-item" *ngFor="let report of reports()">
          <strong>{{ report.reportType }}</strong>
          <div class="muted">{{ report.generatedAt }}</div>
          <pre style="white-space:pre-wrap;">{{ report.summaryJson }}</pre>
        </div>
      </div>
    </section>
  `
})
export class AdminSettingsComponent {
  readonly loading = signal(true);
  readonly message = signal('');
  readonly templates = signal<any[]>([]);
  readonly searchConfig = signal<any>(null);
  readonly dictionaries = signal<any[]>([]);
  readonly cleaningRules = signal<any[]>([]);
  readonly parsingTemplates = signal<any[]>([]);
  readonly users = signal<any[]>([]);
  readonly alerts = signal<any[]>([]);
  readonly reports = signal<any[]>([]);
  readonly resetMessage = signal('');
  readonly pendingResetToken = signal('');
  readonly revealedPassword = signal('');
  readonly parseResult = signal<any>(null);

  readonly parsingDraft = {
    templateName: '',
    templateType: 'JSON',
    semanticVersion: '1.0.0',
    body: '{"fields":{"stopName":"stop.name","address":"stop.address","residentialAreaName":"housing.areaName","apartmentType":"housing.apartmentType","area":"housing.area","price":"housing.price"}}',
    active: false,
    revision: 0
  };

  readonly parseDraft = {
    templateId: '',
    sourceReference: 'sample-source',
    sourceBody: '{"stop":{"name":"Central Station","address":"1 Main Street"},"housing":{"areaName":"Riverside Residency","apartmentType":"Studio","area":"45sqm","price":"3200 CNY/month"}}'
  };

  constructor(private readonly apiService: ApiService) {
    this.reload();
  }

  reload() {
    this.loading.set(true);
    this.apiService.adminTemplates().subscribe(response => this.templates.set(response));
    this.apiService.adminSearchConfig().subscribe(response => this.searchConfig.set(response));
    this.apiService.adminDictionaries().subscribe(response => this.dictionaries.set(response));
    this.apiService.adminCleaningRules().subscribe(response => this.cleaningRules.set(response));
    this.apiService.adminUsers().subscribe(response => this.users.set(response));
    this.apiService.adminAlerts().subscribe(response => this.alerts.set(response));
    this.apiService.adminReports().subscribe(response => this.reports.set(response));
    this.apiService.adminParsingTemplates().subscribe({
      next: response => {
        this.parsingTemplates.set(response);
        this.loading.set(false);
      },
      error: error => {
        this.message.set(error?.error?.message ?? 'Unable to load admin settings');
        this.loading.set(false);
      }
    });
  }

  saveTemplate(item: any) {
    this.apiService.adminUpdateTemplate(item.id, { titleTemplate: item.titleTemplate, contentTemplate: item.contentTemplate }).subscribe({
      next: () => this.message.set(`Updated template ${item.templateKey}`),
      error: error => this.message.set(error?.error?.message ?? 'Unable to update template')
    });
  }

  saveSearchConfig(config: any) {
    this.apiService.adminUpdateSearchConfig(config).subscribe({
      next: response => {
        this.searchConfig.set(response);
        this.message.set('Search configuration updated');
      },
      error: error => this.message.set(error?.error?.message ?? 'Unable to update search config')
    });
  }

  saveDictionary(item: any) {
    this.apiService.adminUpdateDictionary(item.id, item.standardizedValue).subscribe({
      next: () => this.message.set(`Updated dictionary ${item.dictionaryType}`),
      error: error => this.message.set(error?.error?.message ?? 'Unable to update dictionary')
    });
  }

  saveCleaningRule(rule: any) {
    this.apiService.adminUpdateCleaningRule(rule.id, rule).subscribe({
      next: () => this.message.set(`Updated cleaning rule ${rule.ruleKey}`),
      error: error => this.message.set(error?.error?.message ?? 'Unable to update cleaning rule')
    });
  }

  saveParsingTemplate() {
    this.apiService.adminSaveParsingTemplate(this.parsingDraft).subscribe({
      next: () => {
        this.message.set('Parsing template saved');
        this.reload();
      },
      error: error => this.message.set(error?.error?.message ?? 'Unable to save parsing template')
    });
  }

  parseSource() {
    this.apiService.adminParse(this.parseDraft).subscribe({
      next: response => {
        this.parseResult.set(response);
        this.message.set('Parsing completed');
      },
      error: error => this.message.set(error?.error?.message ?? 'Unable to parse source')
    });
  }

  resetPassword(username: string) {
    this.apiService.adminResetPassword(username).subscribe({
      next: response => {
        this.pendingResetToken.set(response.requestToken);
        this.revealedPassword.set('');
        this.resetMessage.set(`Reset token for ${username}: ${response.requestToken} (expires ${response.expiresAt}). Use reveal action once.`);
      },
      error: error => this.message.set(error?.error?.message ?? 'Unable to reset password')
    });
  }

  revealTemporaryPassword() {
    if (!this.pendingResetToken()) {
      return;
    }
    this.apiService.adminRevealTemporaryPassword(this.pendingResetToken()).subscribe({
      next: response => {
        this.revealedPassword.set(response.temporaryPassword);
        this.resetMessage.set('Temporary password revealed once. Token is now consumed.');
      },
      error: error => this.message.set(error?.error?.message ?? 'Unable to reveal temporary password')
    });
  }
}
