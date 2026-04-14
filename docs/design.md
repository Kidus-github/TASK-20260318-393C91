# City Bus Operation and Service Coordination Platform

## 1. Scope

This document captures the resolved architecture and operational rules for the City Bus Operation and Service Coordination Platform. It covers:

- Passenger-facing search, reminders, and message center behavior
- Dispatcher workflow processing and task coordination
- Administrator configuration and governance responsibilities
- Security, storage, parsing, observability, and reliability rules

The platform is deployed in an air-gapped local area network using:

- Angular frontend for passenger, dispatcher, and administrator interfaces
- Spring Boot backend exposing RESTful APIs
- PostgreSQL as the system of record
- Local scheduled jobs and an in-platform message queue

No external SMS, email, or push providers are used.

## 2. User Roles

### 2.1 Passenger

Passengers can:

- Search routes and stops by route number, stop name, or keywords
- Use autocomplete with pinyin and initial-letter matching
- Configure arrival reminder preferences and do-not-disturb periods
- View centralized notifications in the message center
- Manage their own profile, password, and reminder settings

Passengers cannot:

- View other users' data
- Access dispatcher or administrator features

### 2.2 Dispatcher

Dispatchers can:

- Process workflow tasks assigned to them or available in the task pool
- Review route data change approvals
- Configure reminder rules if granted that permission
- Review abnormal data and batch-process eligible tasks
- View workflow progress and timeout/escalation status

Dispatchers cannot:

- Administer users, roles, encryption settings, or system-wide retention policy unless explicitly assigned an administrator role

### 2.3 Administrator

Administrators can:

- Manage users, roles, and password resets
- Maintain notification templates
- Configure sorting weights and search rule parameters
- Maintain field standard dictionaries and cleaning rules
- Manage parsing templates, field mappings, and active template versions
- Review failed queue messages and abnormal parsing records
- Configure backup, retention, and system-level settings
- Define workflow models and escalation parameters

Administrators do not silently impersonate users. If impersonation support is added later, it must be explicit, time-bound, reason-coded, visibly indicated in the UI, and fully audit logged.

## 3. Authentication and Session Security

### 3.1 Login

Authentication is local only and based on username and password.

Rules:

- Password minimum length is 8 characters
- Passwords are stored using Argon2id
- Each user has a random 16-byte salt
- Hash parameters are centrally configured and versioned
- Existing password hashes are transparently upgraded on successful login when the configured policy changes

An optional server-side pepper may be used if local secret storage is available.

### 3.2 Password Recovery

Because the environment is air-gapped, password recovery does not use email, SMS, or security questions.

Recovery model:

- After first successful login, the user is issued a one-time recovery code set
- Recovery codes are displayed once and stored only as salted hashes
- A valid recovery code can be used to initiate a password reset flow
- If recovery codes are unavailable, only an Administrator can reset the account
- Administrator reset issues a temporary password valid for one login only
- The user must change the password immediately after using the temporary password

All recovery attempts, resets, and forced password changes are audit logged.

### 3.3 Session Management

The backend uses:

- Short-lived access tokens
- Rotating refresh tokens
- Server-side session records with revocation support

Session anomaly checks:

- `User-Agent` is used as the primary fingerprint signal
- IP changes are treated as soft risk indicators, not strict lock criteria
- Refresh token replay or suspicious token reuse causes session revocation
- Password reset, role changes, manual lockout, or admin-enforced logout revoke all active sessions for that user

## 4. Passenger Search and Reminder Experience

### 4.1 Search Features

Passengers can search using:

- Route number
- Stop name
- Free-text keywords
- Pinyin
- Initial letters
- Aliases or abbreviations where configured

Search results support:

- Autocomplete suggestions
- Automatic deduplication
- Ranking based on frequency priority plus stop popularity
- Exact-match, prefix-match, and normalized text boosts

### 4.2 Search Consistency and Performance

Frontend behavior:

- Angular autocomplete uses RxJS `switchMap`
- When a new query is triggered, the previous pending request is discarded client-side

Backend behavior:

- Each request executes independently under standard PostgreSQL transaction semantics
- Search relies on normalized search fields and admin-managed ranking weights
- PostgreSQL `pg_trgm` and GIN indexes are used for fuzzy search
- Precomputed normalized search columns are maintained for pinyin and initials
- Optional in-memory caching may be introduced only after measurement validates the need

Latency target:

- API P95 for search should remain under 500 ms
- A P95 breach triggers a local alert and diagnostic report

### 4.3 Reminder Preferences

Passengers can configure:

- Reminder enabled or disabled
- Reminder lead time, defaulting to 10 minutes before arrival
- Do-not-disturb time windows such as `22:00` to `07:00`

Reminder and DND logic is server-authoritative. The client may render adjusted times, but reminder scheduling and suppression decisions are computed on the server.

### 4.4 Reminder Lifecycle

Reminders are dynamic subscriptions rather than static timers.

Rules:

- Scheduled jobs periodically re-check ETA and route status
- If ETA changes materially, the reminder is recalculated
- If a route is cancelled, pending reminders are cancelled
- Delay-update notifications may be generated when ETA shifts significantly
- Missed check-in notifications are generated 5 minutes after the relevant start time when conditions are met

### 4.5 DND Catch-up Rule

If a reminder should fire during the user's DND window:

- The reminder is suppressed during DND
- When DND ends, the system checks whether the reminder would have fired within the configurable catch-up grace window
- If it is still within that window, the reminder is delivered immediately

Default catch-up grace window:

- 15 minutes

## 5. Message Center and Notification Delivery

### 5.1 Message Types

The unified message center displays at least:

- Reservation success messages
- Upcoming arrival reminders
- Missed check-in reminders
- Delay or cancellation updates
- Workflow or system notices for authorized internal roles

### 5.2 Delivery Model

All notifications are written to an in-platform database-backed message queue.

Each queued message includes:

- Message type
- Recipient identifier
- Payload
- Sensitivity level
- Scheduled delivery time
- Retry count
- Idempotency key
- Trace ID

Consumers process queued messages using local scheduled tasks only.

### 5.3 Read-State Synchronization

For multiple tabs in the same browser:

- Angular uses the BroadcastChannel API to synchronize read/unread counts and related UI state

The backend remains the source of truth for persisted read state.

### 5.4 Sensitive Content Handling

Message handling uses two separate controls:

- Encryption at rest for highly sensitive stored fields
- Role-based masking or redaction when returning content through APIs

Default API behavior should favor masked or desensitized output unless a privileged role explicitly requires the raw value.

## 6. Dispatcher Workflow Engine

### 6.1 Supported Workflow Features

The workflow engine supports:

- Conditional branching
- Joint approvals
- Parallel approvals
- Task return for resubmission
- Batch processing for eligible tasks
- Timeout escalation alerts
- Visual progress tracking

### 6.2 Task Locking

When a dispatcher opens a task, the system grants a lease-based lock.

Lock rules:

- Lease duration is 15 minutes
- The client sends periodic heartbeats to renew the lease
- If heartbeats stop and the lease expires, the task is automatically unlocked and returned to the eligible pool
- Lease ownership and workflow state are tracked separately

### 6.3 Zombie Task Prevention

If the browser crashes, the session expires, or the dispatcher disconnects:

- Lease renewal stops
- A background sweeper job detects expired leases
- Expired tasks return to the queue automatically
- Any attempted submission after lease expiry must first reacquire a valid lease

### 6.4 Approval Conflict Rules

All workflow decisions are executed transactionally with row-level locking.

Rules:

- `REJECT` has precedence and is terminal
- A request arriving after finalization fails gracefully with current-state feedback
- Workflow models explicitly define whether parallel steps require unanimity, quorum, or any-one approval

### 6.5 Timeout Escalation

If a task remains unprocessed for 24 hours:

- The workflow engine generates an escalation warning
- The warning is routed to the appropriate supervisor or administrator dashboard
- The event is included in audit and observability records

## 7. Administrator Configuration

Administrators maintain:

- Notification templates
- Search sorting rule weights
- Field standard dictionaries
- Cleaning rules
- Parsing templates and field mappings
- Backup retention parameters
- Workflow definitions and escalation settings

Configuration changes must be:

- Versioned where applicable
- Audit logged
- Traceable to the responsible user

## 8. Data Parsing, Cleaning, and Version Management

### 8.1 Parsing Inputs

The platform supports structured parsing of:

- HTML templates
- JSON templates

The parser must support configurable:

- Field mapping
- Template version selection
- Source log capture

### 8.2 Stop Structure Versioning

Each parsing template or structure definition contains:

- Immutable record ID
- Semantic version for human interpretation
- Internal revision number for optimistic locking
- Schema or content hash
- Active/inactive status

Conflict handling:

- A save operation checks the base revision
- If another administrator has changed the same template since the editor loaded it, the update is rejected
- Conflicting updates are routed to a manual merge workflow
- Only one active version per template type and scope is allowed at a time

### 8.3 Cleaning and Normalization Rules

Fields such as the following undergo standardization:

- Stop names
- Addresses
- Residential area names
- Apartment types
- Areas
- Prices

Examples:

- Areas are normalized to `㎡`
- Prices are normalized to `yuan/month`
- Missing fields are stored as `NULL`

Every cleaned record should preserve:

- Source value
- Cleaned value
- Cleaning rule version
- Parser/template version
- Operator or system identity
- Trace ID

### 8.4 Partial Parsing Failure

The ETL pipeline is field-tolerant.

Rules:

- A record is not discarded solely because some fields are missing
- Successfully parsed fields are saved
- Missing or invalid fields are stored as `NULL`
- Record status is marked as `SUCCESS`, `WARNING`, or `FAILED`
- `WARNING` records are visible for administrator review with a source-log link

## 9. Data Protection and Encryption

### 9.1 Encryption at Rest

Highly sensitive fields use application-layer encryption before persistence.

Requirements:

- `AES-256-GCM` is used for encryption
- Keys are stored locally outside the database
- Keys are versioned to support rotation
- Decryption is allowed only for authorized roles and only in the services that require it

### 9.2 Response Masking

Masking is separate from encryption.

Rules:

- APIs return desensitized content by default when the caller lacks clearance
- UI masking must not be the only protection layer
- The service layer is responsible for enforcing masking based on role and sensitivity level

## 10. Database, Backup, and Retention

### 10.1 PostgreSQL

PostgreSQL is the system of record for:

- User accounts and sessions
- Searchable route and stop data
- Message queue and message center records
- Workflow definitions, tasks, and approvals
- Parsing templates, mappings, and version history
- Audit logs and operational metadata

### 10.2 Backup Strategy

Backups are local only.

Operational targets:

- Recovery Point Objective (RPO): 24 hours maximum data loss unless a stricter schedule is configured
- Recovery Time Objective (RTO): 4 hours for standard restoration incidents

Restore drills must be executed periodically and recorded.

### 10.3 Retention and Disk Pressure Controls

Retention defaults:

- Structured logs retained online for 30 days
- Local backups retained for at least 7 days
- Older logs compressed and archived where supported
- Audit logs retained longer and protected from emergency cleanup

Disk capacity thresholds:

- 75% warning
- 85% elevated warning
- 90% critical alert

At critical threshold:

- Nonessential debug and trace verbosity may be reduced
- Optional export or maintenance jobs may be paused
- Core business operations, audit logging, and queue processing must continue as long as safely possible

## 11. Time Authority and Offline Client Behavior

### 11.1 Server Time Authority

Because client clocks may drift, the server is the authoritative time source for:

- Reminder scheduling
- DND evaluation
- Workflow timeouts
- Queue processing
- Lease expiry

The frontend calculates and refreshes a display offset from server time for UI consistency.

### 11.2 Frontend Update Distribution

Because users may keep the application open for long periods:

- The Angular client polls a version-check endpoint every 5 minutes
- If the server reports a newer client version, the app displays an update banner
- Mandatory reload flags may be used for urgent UI or schema changes

## 12. Queue Reliability and Failure Handling

### 12.1 Dead Letter Handling

If a queued message fails repeatedly:

- After 3 failed attempts, it is moved to `failed_messages`
- The failure record stores the error summary, stack-trace snippet, payload hash, template version, and trace ID
- The consumer continues processing subsequent messages

### 12.2 Idempotency

To prevent duplicate delivery:

- Every queued message carries an idempotency key
- Consumer logic must be safe under retry
- Retries must not create duplicate message-center entries or repeated notifications for the same event

## 13. Observability and Local Alerts

### 13.1 Structured Logging and Metrics

The platform provides:

- Structured logs
- Metrics
- Health checks
- Trace IDs across critical workflows

Trace IDs must be included in:

- Search requests
- Parsing jobs
- Workflow transitions
- Queue enqueue and consume operations
- Authentication and session events
- Batch operations

### 13.2 Health Checks

Health checks cover at least:

- Application readiness
- Database connectivity
- Scheduler liveness
- Queue backlog status
- Disk capacity thresholds

### 13.3 Local Alert Rules

The system generates local alerts and diagnostic reports when:

- Queue backlog exceeds the configured threshold
- API P95 response time exceeds 500 ms
- Disk usage crosses warning or critical thresholds
- Key schedulers or consumers are unhealthy

Alerts are delivered through local dashboards and in-platform administrative notifications only.

## 14. Non-Functional Requirements

### 14.1 Security

- Local-only authentication
- Strong password hashing with Argon2id
- Recovery codes and administrator-assisted reset
- Session revocation support
- Role-based access enforcement
- Audit logging for security-sensitive actions

### 14.2 Reliability

- Queue retry and dead letter handling
- Lease recovery for abandoned tasks
- Field-tolerant parsing
- Local backup with restore drills

### 14.3 Performance

- Search API P95 under 500 ms target
- Search optimized with indexed normalized fields
- Large batch operations processed in chunks or streamed form

### 14.4 Auditability

The following actions must be audit logged:

- Password recovery and resets
- Login anomalies and session revocations
- Role and permission changes
- Template, mapping, and dictionary edits
- Workflow decisions and escalations
- Manual merges and failed parsing corrections

## 15. Open Implementation Notes

This document defines behavior and constraints, not framework-specific code structure. During implementation, the following should be formalized as code artifacts:

- Database schema and entity boundaries
- REST API contracts
- Workflow state transition matrix
- Search ranking formula and weight configuration schema
- Queue table schema and consumer contract
- Encryption key management procedure
- Backup job schedule and restore runbook

## 16. Operations Notes

## Services

- `frontend`: Angular app served by Nginx
- `backend`: Spring Boot API with scheduler and queue consumers
- `postgres`: PostgreSQL system of record
- `postgres-backup`: local rolling SQL dump service

## Bootstrap Users

- Usernames are fixed as `admin`, `dispatcher`, and `passenger`
- Passwords and recovery codes must be provided through environment variables during initial bootstrap
- The repository does not publish runtime passwords or recovery codes

## Health

- Backend health: `/actuator/health`
- Client version endpoint: `/api/system/version`
- Detailed actuator info and metrics are restricted to administrator-authenticated access

## Recovery

- Password recovery is LAN-local and does not use email or SMS.
- Users may use recovery codes when available.
- Otherwise an administrator performs a temporary-password reset.

## Logging Hygiene

- Structured JSON logs are emitted by the backend
- Passwords, tokens, and recovery codes must not be logged
- Trace IDs are returned in `X-Trace-Id`

## Local Backups

- The `postgres-backup` service runs `pg_dump` on a fixed interval.
- Dumps are written to the `postgres_backups` Docker volume.
- Retention is controlled by `BACKUP_RETENTION_DAYS`.

## Local Alerts

- Queue backlog warnings are written when pending queue depth exceeds the configured threshold.
- API latency warnings are written when the rolling P95 for a route exceeds `500ms`.
- Workflow timeout escalations generate local `WORKFLOW_TIMEOUT` alerts.
- Recent alerts are available through `GET /api/admin/alerts`.
- Linked diagnostic reports are available through `GET /api/admin/reports` and `GET /api/admin/reports/{id}`.
