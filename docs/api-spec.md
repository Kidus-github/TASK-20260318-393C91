# City Bus Platform API Specification

## Overview

This API powers the City Bus Operation and Service Coordination Platform for three primary roles:

- Passenger
- Dispatcher
- Admin

The API is implemented with Spring Boot and exposes REST endpoints under the `/api` base path.

- Base URL: `/api`
- Content type: `application/json`
- Authentication: JWT bearer token (`Authorization: Bearer <accessToken>`)

## Authentication

### Login and Token Flow

1. Client calls `POST /api/auth/login` with username/password.
2. API returns:
   - `accessToken` (short-lived JWT)
   - `refreshToken` (longer-lived JWT)
3. Client sends `accessToken` in `Authorization` header for protected routes.
4. Client refreshes session with `POST /api/auth/refresh`.
5. Client revokes session with `POST /api/auth/logout`.

### Authorization Header

```http
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
```

### Token/Error Behavior

- Invalid login credentials: `401`
- Invalid/expired/revoked access token on protected route: `401`
- Insufficient role/permission: `403`
- Invalid refresh token/session: `401`

## Standard Error Format

All API/domain/validation errors are returned using:

```json
{
  "status": 422,
  "message": "Validation failed",
  "details": [
    "leadMinutes: must be greater than or equal to 1"
  ],
  "timestamp": "2026-04-14T00:00:00Z"
}
```

Fields:

- `status`: HTTP status code
- `message`: human-readable summary
- `details`: validation detail list (may be empty)
- `timestamp`: server timestamp

Common status codes in this API:

| Code | Meaning | Notes |
|---|---|---|
| 200 | OK | Primary success response for current endpoints |
| 201 | Created | Not currently returned by implemented controllers |
| 400 | Bad Request | Possible from malformed JSON / framework-level parse errors |
| 401 | Unauthorized | Missing/invalid credentials or invalid session |
| 403 | Forbidden | Authenticated but not allowed for role/resource |
| 404 | Not Found | Resource not found |
| 422 | Unprocessable Entity | Validation/business-rule failure |
| 500 | Internal Server Error | Unhandled server error |

---

## Role and Access Model

| Area | Required Role |
|---|---|
| `/api/auth/*` | Public for login/refresh/recover; `/me` requires authenticated user |
| `/api/system/version` | Public |
| `/api/passenger/*` | `PASSENGER` |
| `/api/dispatcher/tasks/*` | `DISPATCHER` |
| `/api/admin/*` | `ADMIN` |

Object-level restrictions:

- Passenger resources are user-scoped (subscriptions/messages/preferences tied to authenticated user).
- Dispatcher task access is restricted by dispatcher role and task accessibility/lease ownership rules.
- Admin endpoints are role-restricted; data is administrative scope.

---

## Auth Endpoints

### POST `/api/auth/login`

- Description: Authenticate user with username/password.
- Authorization: None
- Headers:
  - `Content-Type: application/json`
- Request body:

```json
{
  "username": "passenger",
  "password": "Passenger123!"
}
```

- Success `200`:

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9.access",
  "refreshToken": "eyJhbGciOiJIUzI1NiJ9.refresh",
  "profile": {
    "userId": "12f4386b-eea7-4aec-b141-7a0168f68ab0",
    "username": "passenger",
    "displayName": "Passenger User",
    "role": "PASSENGER"
  },
  "passwordChangeRequired": false
}
```

- Errors:
  - `401` Invalid credentials
  - `422` Validation failed (`username`/`password` blank, password too short)

---

### POST `/api/auth/refresh`

- Description: Rotate and issue a new access + refresh token pair.
- Authorization: None
- Headers:
  - `Content-Type: application/json`
- Request body:

```json
{
  "refreshToken": "eyJhbGciOiJIUzI1NiJ9.refresh"
}
```

- Success `200`: same shape as login response.
- Errors:
  - `401` Invalid session
  - `401` Refresh token replay detected
  - `401` Session fingerprint changed
  - `422` Validation failed

---

### POST `/api/auth/logout`

- Description: Revoke the session represented by refresh token.
- Authorization: None
- Headers:
  - `Content-Type: application/json`
- Request body:

```json
{
  "refreshToken": "eyJhbGciOiJIUzI1NiJ9.refresh"
}
```

- Success `200`:

```json
{
  "status": "ok"
}
```

- Errors:
  - `401` Invalid session
  - `422` Validation failed

---

### POST `/api/auth/recover`

- Description: Account recovery with recovery code (or request token path).
- Authorization: None
- Headers:
  - `Content-Type: application/json`
- Request body:

```json
{
  "username": "passenger",
  "recoveryCodeOrRequestToken": "PASSENGER-RECOVERY-1"
}
```

- Success `200`:

```json
{
  "result": "Reseta1b2c3d4!"
}
```

If no matching recovery code is valid, API may return request token string in `result`:

```json
{
  "result": "REQ-AB12CD34"
}
```

- Errors:
  - `404` User not found
  - `422` Validation failed

---

### GET `/api/auth/me`

- Description: Get current authenticated user profile.
- Authorization: Bearer access token
- Headers:
  - `Authorization: Bearer <accessToken>`
- Request body: none
- Success `200`:

```json
{
  "userId": "12f4386b-eea7-4aec-b141-7a0168f68ab0",
  "username": "passenger",
  "displayName": "Passenger User",
  "role": "PASSENGER"
}
```

- Errors:
  - `401` Missing/invalid/expired token
  - `401` User not found

---

## System Endpoint

### GET `/api/system/version`

- Description: Public version and server time.
- Authorization: None
- Request body: none
- Success `200`:

```json
{
  "version": "1.0.0",
  "serverTime": "2026-04-14T00:00:00Z"
}
```

---

## Passenger Endpoints

All passenger endpoints require role `PASSENGER`.

Headers for protected endpoints:

- `Authorization: Bearer <accessToken>`
- `Content-Type: application/json` for endpoints with body

### GET `/api/passenger/search/suggestions?q={query}`

- Description: Search suggestions/results for route/stop query.
- Request body: none
- Success `200`:

```json
{
  "suggestions": [
    {
      "id": "2a6ec0fe-85f0-4bcf-8175-13d02ab1948f",
      "label": "101 - Riverside Loop",
      "type": "ROUTE",
      "score": 24
    }
  ],
  "results": [
    {
      "id": "8f9e3f4f-9b87-4f6b-8f89-7e42461d2da2",
      "primaryText": "Central Station",
      "secondaryText": "Central Ave",
      "type": "STOP",
      "score": 18
    }
  ]
}
```

- Errors:
  - `401` Unauthorized

---

### GET `/api/passenger/search/results?q={query}`

- Description: Same search contract as suggestions endpoint.
- Request body: none
- Success/Error: same shape/codes as suggestions endpoint.

---

### GET `/api/passenger/reminders/preferences`

- Description: Get current reminder preference.
- Request body: none
- Success `200`:

```json
{
  "enabled": true,
  "leadMinutes": 10,
  "dndStart": "22:00",
  "dndEnd": "07:00"
}
```

- Errors:
  - `401` Unauthorized

---

### PUT `/api/passenger/reminders/preferences`

- Description: Update reminder preference.
- Request body:

```json
{
  "enabled": true,
  "leadMinutes": 15,
  "dndStart": "22:00",
  "dndEnd": "07:00"
}
```

- Validation:
  - `leadMinutes` must be `1..120`
  - `dndStart`/`dndEnd` must match `HH:mm`

- Success `200`: same shape as GET preferences.
- Errors:
  - `401` Unauthorized
  - `422` Validation failed

Example `422`:

```json
{
  "status": 422,
  "message": "Validation failed",
  "details": [
    "leadMinutes: must be greater than or equal to 1"
  ],
  "timestamp": "2026-04-14T00:00:00Z"
}
```

---

### GET `/api/passenger/reminders/subscriptions`

- Description: List current user reminder subscriptions.
- Request body: none
- Success `200`:

```json
[
  {
    "id": "4a2be1e4-a95a-46e2-b1f8-6f8e947d2f60",
    "routeId": "2a6ec0fe-85f0-4bcf-8175-13d02ab1948f",
    "stopId": "8f9e3f4f-9b87-4f6b-8f89-7e42461d2da2",
    "reservationName": "101 @ Central Station",
    "scheduledArrivalAt": "2026-04-14T08:10:00Z",
    "reminderAt": "2026-04-14T08:00:00Z",
    "checkedInAt": null,
    "canceled": false,
    "reminderSent": false,
    "missedCheckInSent": false
  }
]
```

- Errors:
  - `401` Unauthorized

---

### POST `/api/passenger/reminders/subscriptions`

- Description: Create reminder subscription for route+stop.
- Request body:

```json
{
  "routeId": "2a6ec0fe-85f0-4bcf-8175-13d02ab1948f",
  "stopId": "8f9e3f4f-9b87-4f6b-8f89-7e42461d2da2",
  "reservationName": "101 @ Central Station",
  "scheduledArrivalAt": "2026-04-14T08:10:00Z"
}
```

- Success `200`: `ReminderSubscriptionResponse` (same shape as list item).
- Errors:
  - `401` Unauthorized
  - `404` Route not found
  - `404` Stop not found
  - `422` Selected stop does not belong to selected route
  - `422` Arrival reminders are disabled for this user
  - `422` Scheduled arrival must be in the future
  - `422` Route/Stop identifier invalid

---

### POST `/api/passenger/reminders/subscriptions/{id}/check-in`

- Description: Mark subscription as checked in.
- Request body: none
- Success `200`: updated `ReminderSubscriptionResponse`.
- Errors:
  - `401` Unauthorized
  - `404` Reminder subscription not found

Object-level restriction:

- `id` must belong to authenticated passenger (`findByIdAndUserId`).

---

### POST `/api/passenger/reminders/subscriptions/{id}/cancel`

- Description: Cancel subscription.
- Request body: none
- Success `200`: updated `ReminderSubscriptionResponse`.
- Errors:
  - `401` Unauthorized
  - `404` Reminder subscription not found

Object-level restriction:

- `id` must belong to authenticated passenger (`findByIdAndUserId`).

---

### GET `/api/passenger/messages`

- Description: List message center items for current user.
- Request body: none
- Success `200`:

```json
[
  {
    "id": "5326614d-6f8c-4338-81f1-e3347cf6b58f",
    "type": "RESERVATION_CONFIRMED",
    "title": "Reservation confirmed",
    "content": "Your reservation reminder was created successfully.",
    "read": false,
    "createdAt": "2026-04-14T00:00:00Z"
  }
]
```

- Errors:
  - `401` Unauthorized

---

### POST `/api/passenger/messages/{id}/read`

- Description: Mark message as read.
- Request body: none
- Success `200`:

```json
{
  "status": "ok"
}
```

- Errors:
  - `401` Unauthorized
  - `404` Message not found

Object-level restriction:

- `id` must belong to authenticated passenger (`findByIdAndUserId`).

---

## Dispatcher Endpoints

All dispatcher endpoints require role `DISPATCHER`.

Headers for protected endpoints:

- `Authorization: Bearer <accessToken>`
- `Content-Type: application/json` for endpoints with body

### GET `/api/dispatcher/tasks`

- Description: List available tasks for dispatcher, including parallel approval child tasks.
- Request body: none
- Success `200`:

```json
[
  {
    "id": "a6de878d-ad61-4a03-be2c-1979892ca366",
    "title": "Route update - Parallel Approval",
    "taskType": "ROUTE_CHANGE_APPROVAL",
    "state": "LEASED",
    "leaseExpiresAt": "2026-04-14T00:15:00Z",
    "payload": "requiresAbnormalReview=false",
    "parentTaskId": "3c00589b-86ba-4f95-a2bc-456cb8cabc9e",
    "approvalGroupId": "817bd864-fda5-4bd9-8759-48a966b1cbe5",
    "approvalMode": "ALL",
    "requiredApprovals": 2,
    "approvalCount": 0,
    "currentApprovals": 0,
    "progressStep": 1,
    "progressTotal": 2,
    "resubmissionCount": 0,
    "escalated": false
  }
]
```

- Errors:
  - `401` Unauthorized
  - `403` Forbidden role

---

### GET `/api/dispatcher/tasks/{id}`

- Description: Get task or approval child task by id.
- Request body: none
- Success `200`: `TaskResponse`.
- Errors:
  - `401` Unauthorized
  - `403` Task/approval belongs to another dispatcher
  - `404` Task not found / parent task not found

---

### POST `/api/dispatcher/tasks/{id}/claim`

- Description: Claim a task lease (or claim approval child task).
- Request body: none
- Success `200`: `TaskResponse` (leased task).
- Errors:
  - `401` Unauthorized
  - `403` Task belongs to another dispatcher
  - `404` Task not found
  - `409` Task already finalized
  - `409` Task currently leased by another dispatcher
  - `409` No claimable approval child task available

---

### POST `/api/dispatcher/tasks/{id}/decision`

- Description: Submit decision on leased task/approval task.
- Request body:

```json
{
  "decision": "APPROVE",
  "comment": "Looks correct"
}
```

- Allowed decisions: `APPROVE`, `REJECT`, `RETURN`
- Success `200`: updated `TaskResponse`.
- Errors:
  - `401` Unauthorized
  - `403` Task belongs to another dispatcher
  - `404` Task not found
  - `409` Task lease expired
  - `409` Task no longer pending / already finalized
  - `409` Same dispatcher cannot provide multiple approvals for the same task
  - `422` Unsupported decision
  - `422` Validation failed (`decision` blank)

---

### POST `/api/dispatcher/tasks/{id}/resubmit`

- Description: Resubmit returned task/approval task.
- Request body:

```json
{
  "decision": "RESUBMIT",
  "comment": "Updated and resubmitted"
}
```

Note: `decision` value is accepted but ignored; only `comment` is used.

- Success `200`: updated `TaskResponse`.
- Errors:
  - `401` Unauthorized
  - `403` Task belongs to another dispatcher
  - `404` Task not found
  - `409` Only returned tasks can be resubmitted

---

### POST `/api/dispatcher/tasks/batch-decision`

- Description: Apply same decision to multiple tasks.
- Request body:

```json
{
  "taskIds": [
    "a6de878d-ad61-4a03-be2c-1979892ca366",
    "b1ab4d3a-aa6d-43e6-8cc8-350e89ce8b0a"
  ],
  "decision": "APPROVE",
  "comment": "Batch approve"
}
```

- Success `200`:

```json
{
  "succeeded": [
    {
      "id": "a6de878d-ad61-4a03-be2c-1979892ca366",
      "title": "Route update - Parallel Approval",
      "taskType": "ROUTE_CHANGE_APPROVAL",
      "state": "APPROVED",
      "leaseExpiresAt": null,
      "payload": "requiresAbnormalReview=false",
      "parentTaskId": "3c00589b-86ba-4f95-a2bc-456cb8cabc9e",
      "approvalGroupId": "817bd864-fda5-4bd9-8759-48a966b1cbe5",
      "approvalMode": "ALL",
      "requiredApprovals": 2,
      "approvalCount": 1,
      "currentApprovals": 1,
      "progressStep": 1,
      "progressTotal": 2,
      "resubmissionCount": 0,
      "escalated": false
    }
  ],
  "failed": [
    {
      "taskId": "b1ab4d3a-aa6d-43e6-8cc8-350e89ce8b0a",
      "message": "Task lease expired"
    }
  ]
}
```

- Errors:
  - `401` Unauthorized
  - `403` Forbidden role
  - `422` Validation failed (`taskIds` empty, `decision` blank)

---

## Admin Endpoints

All admin endpoints require role `ADMIN`.

Headers for protected endpoints:

- `Authorization: Bearer <accessToken>`
- `Content-Type: application/json` for endpoints with body

### GET `/api/admin/templates`

- Description: List notification templates.
- Success `200`:

```json
[
  {
    "id": "cfa7f48b-c221-4dc6-ae53-af7044f4be67",
    "templateKey": "RESERVATION_CONFIRMED",
    "titleTemplate": "Reservation confirmed",
    "contentTemplate": "Reservation {{reservationName}} confirmed.",
    "updatedAt": "2026-04-14T00:00:00Z"
  }
]
```

- Errors: `401`, `403`

---

### PUT `/api/admin/templates/{id}`

- Description: Update notification template.
- Request body:

```json
{
  "titleTemplate": "Reservation confirmed",
  "contentTemplate": "Reservation {{reservationName}} confirmed."
}
```

- Success `200`: `TemplateResponse`
- Errors:
  - `401`, `403`
  - `404` Template not found
  - `422` Validation failed

---

### GET `/api/admin/search-config`

- Description: Get search weight configuration.
- Success `200`:

```json
{
  "id": "688c8e0e-4591-4d24-a8a7-70f6190474f7",
  "exactWeight": 10,
  "prefixWeight": 7,
  "pinyinWeight": 5,
  "popularityWeight": 3,
  "frequencyWeight": 4,
  "revision": 2
}
```

- Errors: `401`, `403`, `404`

---

### PUT `/api/admin/search-config`

- Description: Update search weights with optimistic revision check.
- Request body:

```json
{
  "exactWeight": 10,
  "prefixWeight": 7,
  "pinyinWeight": 5,
  "popularityWeight": 3,
  "frequencyWeight": 4,
  "revision": 2
}
```

- Success `200`: updated `SearchConfigResponse` (revision incremented).
- Errors:
  - `401`, `403`
  - `404` Search config not found
  - `409` Search config revision conflict
  - `422` Validation failed (negative weights/revision)

---

### GET `/api/admin/dictionaries`

- Description: List dictionary entries grouped by `dictionaryType`.
- Success `200`:

```json
[
  {
    "dictionaryType": "apartmentType",
    "items": [
      {
        "id": "f218d8dc-ee72-47f5-8499-30afdf8d4816",
        "dictionaryType": "apartmentType",
        "sourceValue": "1 BR",
        "standardizedValue": "1-bedroom",
        "updatedAt": "2026-04-14T00:00:00Z"
      }
    ]
  }
]
```

- Errors: `401`, `403`

---

### PUT `/api/admin/dictionaries/{id}`

- Description: Update single dictionary standardized value.
- Request body:

```json
{
  "standardizedValue": "2-bedroom"
}
```

- Success `200`: `DictionaryResponse`
- Errors:
  - `401`, `403`
  - `404` Dictionary record not found
  - `422` Validation failed

---

### GET `/api/admin/cleaning-rules`

- Description: List cleaning rules.
- Success `200`:

```json
[
  {
    "id": "3fd9668f-0604-4a25-b5dd-ccd9de47f3c2",
    "ruleKey": "trim_spaces",
    "fieldName": "address",
    "pattern": "\\\\s+",
    "replacementValue": " ",
    "enabled": true,
    "updatedAt": "2026-04-14T00:00:00Z"
  }
]
```

- Errors: `401`, `403`

---

### PUT `/api/admin/cleaning-rules/{id}`

- Description: Update cleaning rule regex/replacement/enabled.
- Request body:

```json
{
  "pattern": "\\\\s+",
  "replacementValue": " ",
  "enabled": true
}
```

- Success `200`: `CleaningRuleResponse`
- Errors:
  - `401`, `403`
  - `404` Cleaning rule not found
  - `422` Validation failed

---

### GET `/api/admin/parsing/templates`

- Description: List parsing templates and versions.
- Success `200`:

```json
[
  {
    "id": "d7f9f530-8c6d-45cf-9287-2f8f7de2ca31",
    "templateName": "listing-json",
    "templateType": "JSON",
    "semanticVersion": "1.0.1",
    "revision": 2,
    "active": true,
    "contentHash": "e8b1d6...",
    "updatedAt": "2026-04-14T00:00:00Z"
  }
]
```

- Errors: `401`, `403`

---

### POST `/api/admin/parsing/templates`

- Description: Save a new parsing template version.
- Request body:

```json
{
  "templateName": "listing-json",
  "templateType": "JSON",
  "semanticVersion": "1.0.2",
  "body": "{\"fields\":{\"stopName\":\"stop.name\",\"address\":\"stop.address\"}}",
  "active": true,
  "revision": 2
}
```

- Success `200`: `ParsingTemplateResponse` with incremented `revision`.
- Errors:
  - `401`, `403`
  - `409` Template revision conflict
  - `422` Template body must be valid JSON mapping metadata
  - `422` Unsupported template type (`JSON`/`HTML` only)
  - `422` Validation failed

---

### POST `/api/admin/parsing/parse`

- Description: Parse and normalize source payload via parsing template.
- Request body:

```json
{
  "templateId": "d7f9f530-8c6d-45cf-9287-2f8f7de2ca31",
  "sourceReference": "source://feed/record-1001",
  "sourceBody": "{\"stop\":{\"name\":\"Central Station\",\"address\":\"Central Ave\"}}"
}
```

- Success `200`:

```json
{
  "id": "878ca459-2fa6-4e74-8ed7-7246bc6a2748",
  "status": "SUCCESS",
  "sourceReference": "source://feed/record-1001",
  "normalizedPayload": "{\"stopName\":\"Central Station\",\"address\":\"Central Ave\",\"residentialAreaName\":null,\"apartmentType\":null,\"area\":null,\"price\":null}",
  "warningMessage": null,
  "createdAt": "2026-04-14T00:00:00Z"
}
```

- Errors:
  - `401`, `403`
  - `404` Parsing template not found
  - `422` Parsing template identifier is invalid
  - `422` Template must define a fields object
  - `422` Unable to parse source with template
  - `422` Validation failed

---

### GET `/api/admin/alerts`

- Description: List latest system alerts (top 20).
- Success `200`:

```json
[
  {
    "id": "3f0f9f40-c7d5-4d9f-b6f3-c1df89b7bf46",
    "alertType": "WORKFLOW_TIMEOUT",
    "severity": "WARN",
    "summary": "Workflow task exceeded 24 hour processing window",
    "details": "Task ... escalated after timeout",
    "createdAt": "2026-04-14T00:00:00Z"
  }
]
```

- Errors: `401`, `403`

---

### GET `/api/admin/reports`

- Description: List latest diagnostic reports.
- Success `200`:

```json
[
  {
    "id": "6df91ce5-57f8-43fd-a465-8ca8fbf6cdf5",
    "reportType": "FAILED_WORKFLOW_TASKS",
    "summaryJson": "{\"taskId\":\"...\",\"reason\":\"TIMEOUT_ESCALATION\"}",
    "traceId": "832cd081-92f3-4287-93de-54f21583a067",
    "generatedAt": "2026-04-14T00:00:00Z"
  }
]
```

- Errors: `401`, `403`

---

### GET `/api/admin/reports/{id}`

- Description: Get a single diagnostic report.
- Success `200`: `DiagnosticReportResponse`
- Errors:
  - `401`, `403`
  - `404` Report not found

---

### GET `/api/admin/users`

- Description: List users summary.
- Success `200`:

```json
[
  {
    "id": "12f4386b-eea7-4aec-b141-7a0168f68ab0",
    "username": "dispatcher",
    "displayName": "Central Dispatcher",
    "role": "DISPATCHER",
    "active": true,
    "temporaryPassword": false
  }
]
```

- Errors: `401`, `403`

---

### POST `/api/admin/users/reset-password`

- Description: Issue one-time password reveal token; user sessions are revoked.
- Request body:

```json
{
  "username": "passenger"
}
```

- Success `200`:

```json
{
  "requestToken": "RST-7A6B5C4D3E2F1A0B9C8D",
  "expiresAt": "2026-04-14T00:05:00Z"
}
```

- Errors:
  - `401`, `403`
  - `404` User not found
  - `422` Validation failed

---

### POST `/api/admin/users/reset-password/reveal`

- Description: Consume one-time request token and reveal temporary password.
- Request body:

```json
{
  "requestToken": "RST-7A6B5C4D3E2F1A0B9C8D"
}
```

- Success `200`:

```json
{
  "temporaryPassword": "Admin8fd0a1bc!"
}
```

- Errors:
  - `401`, `403`
  - `404` Password reset token not found
  - `409` Password reset token expired or consumed
  - `422` Validation failed

---

## Authorization Matrix (Quick Reference)

| Endpoint Prefix | Passenger | Dispatcher | Admin | Notes |
|---|---:|---:|---:|---|
| `/api/auth/login` `/refresh` `/recover` | Y | Y | Y | Public |
| `/api/auth/me` | Y | Y | Y | Any authenticated user |
| `/api/system/version` | Y | Y | Y | Public |
| `/api/passenger/*` | Y | N | N | Role-guarded + user-scoped records |
| `/api/dispatcher/tasks/*` | N | Y | N | Role-guarded + lease/object checks |
| `/api/admin/*` | N | N | Y | Admin only |
