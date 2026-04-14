import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { MessageItem, ReminderPreference, ReminderSubscription, SearchResponse, TaskItem } from './models';

@Injectable({ providedIn: 'root' })
export class ApiService {
  constructor(private readonly http: HttpClient) {}

  search(query: string) {
    return this.http.get<SearchResponse>('/api/passenger/search/results', { params: { q: query } });
  }

  suggestions(query: string) {
    return this.http.get<SearchResponse>('/api/passenger/search/suggestions', { params: { q: query } });
  }

  getPreferences() {
    return this.http.get<ReminderPreference>('/api/passenger/reminders/preferences');
  }

  updatePreferences(preferences: ReminderPreference) {
    return this.http.put<ReminderPreference>('/api/passenger/reminders/preferences', preferences);
  }

  subscriptions() {
    return this.http.get<ReminderSubscription[]>('/api/passenger/reminders/subscriptions');
  }

  createSubscription(payload: { routeId: string; stopId: string; reservationName: string; scheduledArrivalAt: string }) {
    return this.http.post<ReminderSubscription>('/api/passenger/reminders/subscriptions', payload);
  }

  checkInSubscription(id: string) {
    return this.http.post<ReminderSubscription>(`/api/passenger/reminders/subscriptions/${id}/check-in`, {});
  }

  cancelSubscription(id: string) {
    return this.http.post<ReminderSubscription>(`/api/passenger/reminders/subscriptions/${id}/cancel`, {});
  }

  messages() {
    return this.http.get<MessageItem[]>('/api/passenger/messages');
  }

  markRead(id: string) {
    return this.http.post(`/api/passenger/messages/${id}/read`, {});
  }

  tasks() {
    return this.http.get<TaskItem[]>('/api/dispatcher/tasks');
  }

  claimTask(id: string) {
    return this.http.post<TaskItem>(`/api/dispatcher/tasks/${id}/claim`, {});
  }

  decideTask(id: string, decision: string, comment: string) {
    return this.http.post<TaskItem>(`/api/dispatcher/tasks/${id}/decision`, { decision, comment });
  }

  resubmitTask(id: string, comment: string) {
    return this.http.post<TaskItem>(`/api/dispatcher/tasks/${id}/resubmit`, { decision: 'RESUBMIT', comment });
  }

  batchDecide(taskIds: string[], decision: string, comment: string) {
    return this.http.post<{ succeeded: TaskItem[]; failed: Array<{ taskId: string; message: string }> }>('/api/dispatcher/tasks/batch-decision', { taskIds, decision, comment });
  }

  adminTemplates() {
    return this.http.get<any[]>('/api/admin/templates');
  }

  adminUpdateTemplate(id: string, payload: { titleTemplate: string; contentTemplate: string }) {
    return this.http.put<any>(`/api/admin/templates/${id}`, payload);
  }

  adminSearchConfig() {
    return this.http.get<any>('/api/admin/search-config');
  }

  adminUpdateSearchConfig(payload: any) {
    return this.http.put<any>('/api/admin/search-config', payload);
  }

  adminDictionaries() {
    return this.http.get<any[]>('/api/admin/dictionaries');
  }

  adminUpdateDictionary(id: string, standardizedValue: string) {
    return this.http.put<any>(`/api/admin/dictionaries/${id}`, { standardizedValue });
  }

  adminCleaningRules() {
    return this.http.get<any[]>('/api/admin/cleaning-rules');
  }

  adminUpdateCleaningRule(id: string, payload: { pattern: string; replacementValue: string; enabled: boolean }) {
    return this.http.put<any>(`/api/admin/cleaning-rules/${id}`, payload);
  }

  adminParsingTemplates() {
    return this.http.get<any[]>('/api/admin/parsing/templates');
  }

  adminSaveParsingTemplate(payload: any) {
    return this.http.post<any>('/api/admin/parsing/templates', payload);
  }

  adminParse(payload: { templateId: string; sourceReference: string; sourceBody: string }) {
    return this.http.post<any>('/api/admin/parsing/parse', payload);
  }

  adminUsers() {
    return this.http.get<any[]>('/api/admin/users');
  }

  adminAlerts() {
    return this.http.get<any[]>('/api/admin/alerts');
  }

  adminReports() {
    return this.http.get<any[]>('/api/admin/reports');
  }

  adminReport(id: string) {
    return this.http.get<any>(`/api/admin/reports/${id}`);
  }

  adminResetPassword(username: string) {
    return this.http.post<{ requestToken: string; expiresAt: string }>('/api/admin/users/reset-password', { username });
  }

  adminRevealTemporaryPassword(requestToken: string) {
    return this.http.post<{ temporaryPassword: string }>('/api/admin/users/reset-password/reveal', { requestToken });
  }

  systemVersion() {
    return this.http.get<{ version: string; serverTime: string }>('/api/system/version');
  }
}
