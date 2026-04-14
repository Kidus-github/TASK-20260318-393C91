export interface UserProfile {
  userId: string;
  username: string;
  displayName: string;
  role: 'PASSENGER' | 'DISPATCHER' | 'ADMIN';
}

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  profile: UserProfile;
  passwordChangeRequired: boolean;
}

export interface SearchSuggestion {
  id: string;
  label: string;
  type: string;
  score: number;
}

export interface SearchResult {
  id: string;
  primaryText: string;
  secondaryText: string;
  type: string;
  score: number;
}

export interface SearchResponse {
  suggestions: SearchSuggestion[];
  results: SearchResult[];
}

export interface ReminderPreference {
  enabled: boolean;
  leadMinutes: number;
  dndStart: string;
  dndEnd: string;
}

export interface ReminderSubscription {
  id: string;
  routeId: string;
  stopId: string;
  reservationName: string;
  scheduledArrivalAt: string;
  reminderAt: string;
  checkedInAt: string | null;
  canceled: boolean;
  reminderSent: boolean;
  missedCheckInSent: boolean;
}

export interface MessageItem {
  id: string;
  type: string;
  title: string;
  content: string;
  read: boolean;
  createdAt: string;
}

export interface TaskItem {
  id: string;
  title: string;
  taskType: string;
  state: string;
  leaseExpiresAt: string | null;
  payload: string;
  parentTaskId: string | null;
  approvalGroupId: string | null;
  approvalMode: string;
  requiredApprovals: number;
  approvalCount: number;
  currentApprovals: number;
  progressStep: number;
  progressTotal: number;
  resubmissionCount: number;
  escalated: boolean;
}
