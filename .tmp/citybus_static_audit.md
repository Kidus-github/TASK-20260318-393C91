# City Bus Platform Static Delivery Acceptance & Architecture Audit

## 1. Verdict
- **Overall conclusion: Fail**
- The repository is substantial and mostly aligned to the Prompt, but there are **material delivery/verification risks** and **core requirement fit gaps** (notably workflow generality and observability reporting depth), plus a **static test-environment design defect** that undermines reliable acceptance verification.

## 2. Scope and Static Verification Boundary
- **Reviewed (static):** README/docs/config, backend controllers/services/security/persistence/migrations/bootstrap/schedulers, frontend routes/guards/features/styles, test sources, test config.
- **Not reviewed/executed:** runtime behavior, Docker orchestration, DB runtime state, browser runtime interaction, external network behavior.
- **Intentionally not executed:** project startup, Docker, automated tests, migrations, schedulers.
- **Manual verification required:** any runtime claim (task lease timing/escalation timing, queue scheduling latency, real P95 alert behavior, full UI rendering/interaction, production deployment behavior).

## 3. Repository / Requirement Mapping Summary
- **Prompt core goal mapped:** tri-role LAN platform (passenger/dispatcher/admin), searchable route/stop discovery, reminders + message center, dispatcher workflow, admin configuration, local queue + PostgreSQL + backup, parsing/cleaning/version traceability, local auth/security, observability/alerts.
- **Main implementation areas mapped:** Angular role-based UI and guards, Spring Boot REST API, PostgreSQL schema via Flyway, scheduler-driven queue/workflow processing, admin parsing/cleaning/config modules, JWT + Argon2 auth, structured logging + trace IDs + alert persistence.
- **Primary gap themes:** workflow branching/parallelism is narrowly hardcoded, “diagnostic reports” are not concretely implemented beyond alert rows, and static test setup has schema-generation inconsistency.

## 4. Section-by-section Review

### 4.1 Hard Gates
#### 4.1.1 Documentation and static verifiability
- **Conclusion: Partial Pass**
- **Rationale:** Startup/test/config docs and entrypoints exist and are coherent for Docker-first usage, with env sample and API docs.  
- **Evidence:** `README.md:23`, `README.md:30`, `README.md:50`, `.env.example:1`, `docs/api.md:1`, `docs/testing.md:8`, `docker-compose.yml:71`, `docker-compose.yml:92`.
- **Manual verification note:** Runtime correctness cannot be concluded statically.

#### 4.1.2 Material deviation from Prompt
- **Conclusion: Partial Pass**
- **Rationale:** Implementation is centered on the Prompt scenario, but workflow branching/parallel semantics and diagnostic reporting depth are weaker than stated.
- **Evidence:** `frontend/src/app/features/dispatcher/dispatcher-dashboard.component.ts:45`, `backend/src/main/java/com/citybus/platform/application/WorkflowService.java:204`, `backend/src/main/java/com/citybus/platform/application/WorkflowService.java:220`, `backend/src/main/java/com/citybus/platform/api/AdminController.java:86`, `docs/ops.md:45`.

### 4.2 Delivery Completeness
#### 4.2.1 Core explicit requirements coverage
- **Conclusion: Partial Pass**
- **Rationale:** Most core flows exist (search, reminders, message center, admin config, parsing/cleaning/provenance, queue/alerts), but conditional/parallel workflow and diagnostic report requirements are only partially satisfied.
- **Evidence:** `backend/src/main/java/com/citybus/platform/application/PassengerService.java:84`, `backend/src/main/java/com/citybus/platform/application/PassengerService.java:235`, `backend/src/main/java/com/citybus/platform/application/QueueService.java:47`, `backend/src/main/java/com/citybus/platform/application/RequestMetricsService.java:30`, `backend/src/main/java/com/citybus/platform/application/WorkflowService.java:204`.

#### 4.2.2 End-to-end 0→1 deliverable vs fragment
- **Conclusion: Partial Pass**
- **Rationale:** Repo is full-stack and structurally complete, but static test-environment design has a schema consistency defect that weakens acceptance confidence.
- **Evidence:** `backend/src/test/resources/application.yml:9`, `backend/src/test/resources/application.yml:11`, `backend/src/main/java/com/citybus/platform/infrastructure/bootstrap/BootstrapDataLoader.java:288`, `backend/src/main/java/com/citybus/platform/infrastructure/persistence/RouteStopRepository.java:14`, `backend/src/main/resources/db/migration/V1__initial_schema.sql:53`.
- **Manual verification note:** Requires manual confirmation in an actually executed test environment.

### 4.3 Engineering and Architecture Quality
#### 4.3.1 Structure/module decomposition
- **Conclusion: Pass**
- **Rationale:** Clear module boundaries (api/application/domain/infrastructure + frontend feature segmentation), no single-file overloading.
- **Evidence:** `README.md:56`, `README.md:64`, `backend/src/main/java/com/citybus/platform/api/PassengerController.java:23`, `backend/src/main/java/com/citybus/platform/application/PassengerService.java:42`, `frontend/src/app/app.routes.ts:13`.

#### 4.3.2 Maintainability/extensibility
- **Conclusion: Partial Pass**
- **Rationale:** Generally maintainable, but key workflow branching behavior is hardcoded to payload text pattern and a single follow-up type.
- **Evidence:** `backend/src/main/java/com/citybus/platform/application/WorkflowService.java:204`, `backend/src/main/java/com/citybus/platform/application/WorkflowService.java:220`, `backend/src/main/java/com/citybus/platform/infrastructure/bootstrap/BootstrapDataLoader.java:201`.

### 4.4 Engineering Details and Professionalism
#### 4.4.1 Error handling/logging/validation/API design
- **Conclusion: Partial Pass**
- **Rationale:** Strong baseline exists (global error envelope, validation, role guards, trace IDs, structured logs). However, some requirement-critical semantics rely on narrow hardcoded logic rather than policy/config.
- **Evidence:** `backend/src/main/java/com/citybus/platform/api/ApiErrorHandler.java:22`, `backend/src/main/java/com/citybus/platform/api/dto/AuthDtos.java:13`, `backend/src/main/java/com/citybus/platform/infrastructure/observability/TraceIdFilter.java:26`, `backend/src/main/resources/logback-spring.xml:2`, `backend/src/main/java/com/citybus/platform/application/WorkflowService.java:204`.

#### 4.4.2 Product/service realism vs demo shape
- **Conclusion: Pass**
- **Rationale:** Includes auth/session lifecycle, role isolation, queueing, persistence, scheduling, backup sidecar, and admin operations.
- **Evidence:** `docker-compose.yml:55`, `backend/src/main/java/com/citybus/platform/application/AuthService.java:105`, `backend/src/main/java/com/citybus/platform/application/QueueService.java:44`, `ops/backup/backup.sh:12`.

### 4.5 Prompt Understanding and Requirement Fit
#### 4.5.1 Business goal + implicit constraints fit
- **Conclusion: Partial Pass**
- **Rationale:** Strong alignment on LAN/local stack, tri-role usage, local queue and parsing normalization/provenance; weaker alignment on generalized workflow branching/parallel approvals and diagnostic-report artifact requirement.
- **Evidence:** `README.md:139`, `backend/src/main/java/com/citybus/platform/application/PassengerService.java:236`, `backend/src/main/resources/db/migration/V5__parsed_record_provenance.sql:2`, `backend/src/main/java/com/citybus/platform/application/WorkflowService.java:204`, `backend/src/main/java/com/citybus/platform/application/RequestMetricsService.java:31`.

### 4.6 Aesthetics (frontend)
#### 4.6.1 Visual/interaction quality
- **Conclusion: Pass**
- **Rationale:** Consistent design tokens, responsive layout, hover/active states, loading/error feedback, role-differentiated pages.
- **Evidence:** `frontend/src/styles.css:34`, `frontend/src/styles.css:101`, `frontend/src/styles.css:185`, `frontend/src/app/features/passenger/search-page.component.ts:40`, `frontend/src/app/features/dispatcher/dispatcher-dashboard.component.ts:35`, `frontend/src/app/features/admin/admin-settings.component.ts:16`.
- **Manual verification note:** Pixel-perfect rendering and UX smoothness are manual-verification territory.

## 5. Issues / Suggestions (Severity-Rated)

### Blocker
1. **Static test environment schema design mismatch likely breaks integration-context boot**
- **Conclusion:** Fail
- **Evidence:** `backend/src/test/resources/application.yml:9`, `backend/src/test/resources/application.yml:11`, `backend/src/main/java/com/citybus/platform/infrastructure/bootstrap/BootstrapDataLoader.java:288`, `backend/src/main/java/com/citybus/platform/infrastructure/persistence/RouteStopRepository.java:14`, `backend/src/main/resources/db/migration/V1__initial_schema.sql:53`, `backend/src/main/java/com/citybus/platform/domain/Route.java:11`, `backend/src/main/java/com/citybus/platform/domain/Stop.java:11`
- **Impact:** Test DB uses `ddl-auto: create-drop` with Flyway disabled, while bootstrap issues native SQL against `route_stops`; there is no dedicated mapped entity for `route_stops` generation under JPA create-drop, undermining test verifiability.
- **Minimum actionable fix:** In test profile, enable Flyway migrations or add explicit route-stop schema generation (entity or schema.sql), and ensure bootstrap table dependencies are available before `countLinks()`.

### High
2. **Conditional branching is hardcoded to payload substring + single branch type**
- **Conclusion:** Partial requirement fail
- **Evidence:** `backend/src/main/java/com/citybus/platform/application/WorkflowService.java:204`, `backend/src/main/java/com/citybus/platform/application/WorkflowService.java:220`
- **Impact:** Branching is not a configurable workflow engine; only a narrow condition (`requiresAbnormalReview=true`) produces one follow-up task type.
- **Minimum actionable fix:** Introduce configurable branching rules (condition model + mapping to next task templates) persisted in DB/admin-managed config.

3. **“Joint/parallel approvals” represented as sequential counter, not explicit parallel branch work**
- **Conclusion:** Partial requirement fail
- **Evidence:** `backend/src/main/java/com/citybus/platform/application/WorkflowService.java:181`, `backend/src/main/java/com/citybus/platform/application/WorkflowService.java:207`, `frontend/src/app/features/dispatcher/dispatcher-dashboard.component.ts:45`
- **Impact:** Multi-approval exists, but true parallel branch approvals/task fan-out is not modeled; semantics may not match business workflow expectations.
- **Minimum actionable fix:** Model approval stages/parallel approver sets explicitly (e.g., child approval tasks with aggregation rules).

4. **Diagnostic reports requirement not concretely implemented (alerts only)**
- **Conclusion:** Partial requirement fail
- **Evidence:** `backend/src/main/java/com/citybus/platform/application/RequestMetricsService.java:31`, `backend/src/main/java/com/citybus/platform/application/QueueService.java:49`, `backend/src/main/resources/db/migration/V3__reminders_workflow_and_alerts.sql:26`, `backend/src/main/java/com/citybus/platform/api/AdminController.java:86`, `docs/ops.md:45`
- **Impact:** Prompt asks for local alerts **and diagnostic reports**; implementation persists alerts but no explicit diagnostic report artifact/API/workflow is evident.
- **Minimum actionable fix:** Add diagnostic report schema + generation logic (triggered on threshold breach) and admin retrieval endpoint.

### Medium
5. **Workflow task types are minimally seeded; reminder-rule configuration flow lacks explicit concrete task model**
- **Conclusion:** Partial coverage
- **Evidence:** `backend/src/main/java/com/citybus/platform/infrastructure/bootstrap/BootstrapDataLoader.java:201`, `backend/src/main/java/com/citybus/platform/application/WorkflowService.java:220`
- **Impact:** Prompt names route-change, reminder-rule config, abnormal-data-review workflows; explicit reminder-rule task flow is not evident in concrete seeded/typed behavior.
- **Minimum actionable fix:** Add first-class reminder-rule task type creation/handling path and tests.

6. **Potential sensitive operational disclosure: temporary password returned in plain admin API response**
- **Conclusion:** Security caution
- **Evidence:** `backend/src/main/java/com/citybus/platform/api/AdminController.java:97`, `backend/src/main/java/com/citybus/platform/application/AdminService.java:285`, `frontend/src/app/features/admin/admin-settings.component.ts:289`
- **Impact:** If admin channel/UI/session is compromised, issued temporary passwords are immediately exposed.
- **Minimum actionable fix:** Replace plain return with one-time retrieval flow or out-of-band secured display/ack workflow; at minimum add strict audit and short TTL policy.

## 6. Security Review Summary
- **Authentication entry points: Pass**  
  Evidence: `backend/src/main/java/com/citybus/platform/api/AuthController.java:26`, `backend/src/main/java/com/citybus/platform/application/AuthService.java:66`, `backend/src/main/java/com/citybus/platform/api/dto/AuthDtos.java:13`.
- **Route-level authorization: Pass**  
  Evidence: `backend/src/main/java/com/citybus/platform/infrastructure/security/SecurityConfig.java:41`, `backend/src/main/java/com/citybus/platform/api/PassengerController.java:24`, `backend/src/main/java/com/citybus/platform/api/DispatcherController.java:21`, `backend/src/main/java/com/citybus/platform/api/AdminController.java:23`.
- **Object-level authorization: Pass (core paths reviewed)**  
  Evidence: `backend/src/main/java/com/citybus/platform/application/PassengerService.java:187`, `backend/src/main/java/com/citybus/platform/application/PassengerService.java:226`, `backend/src/main/java/com/citybus/platform/application/WorkflowService.java:236`.
- **Function-level authorization: Partial Pass**  
  Evidence: class-level `@PreAuthorize` exists (`PassengerController.java:24`, `DispatcherController.java:21`, `AdminController.java:23`); additional per-operation policy granularity is limited.
- **Tenant/user isolation: Pass (single-tenant local model)**  
  Evidence: user-scoped queries for passenger resources (`PassengerService.java:187`, `PassengerService.java:226`), role-scoped dispatcher/admin controllers.
- **Admin/internal/debug protection: Pass**  
  Evidence: actuator detail restrictions and ADMIN role gating (`SecurityConfig.java:42`, `SecurityConfig.java:43`, `application.yml:21`, `application.yml:22`).

## 7. Tests and Logging Review
- **Unit tests: Partial Pass**  
  Exists but thin; one focused service unit test for search scoring (`backend/src/test/java/com/citybus/platform/PassengerServiceTest.java:26`).
- **API/integration tests: Partial Pass**  
  Broad endpoint coverage exists for auth/passenger/admin/dispatcher (`AuthFlowIntegrationTest.java:17`, `PassengerApiIntegrationTest.java:18`, `AdminDispatcherIntegrationTest.java:27`) including key HTTP status paths.
- **Logging categories/observability: Pass**  
  Structured JSON logging + trace ID propagation + alert creation are present (`logback-spring.xml:2`, `TraceIdFilter.java:26`, `RequestMetricsService.java:31`, `QueueService.java:49`).
- **Sensitive-data leakage risk in logs/responses: Partial Pass**  
  Passwords/tokens are not directly logged in reviewed code paths, but temporary password is returned by admin API (`AdminController.java:97`), requiring operational controls.

## 8. Test Coverage Assessment (Static Audit)

### 8.1 Test Overview
- **Backend tests present:** JUnit + Spring Boot MockMvc integration tests and one unit test.  
  Evidence: `backend/src/test/java/com/citybus/platform/AuthFlowIntegrationTest.java:17`, `PassengerApiIntegrationTest.java:18`, `AdminDispatcherIntegrationTest.java:27`, `PassengerServiceTest.java:26`.
- **Frontend tests present:** Angular unit test + Playwright E2E.  
  Evidence: `frontend/src/app/core/auth.guard.spec.ts:7`, `frontend/e2e/app.spec.ts:1`.
- **Test commands documented:**  
  Evidence: `README.md:50`, `docs/testing.md:8`, `docker-compose.yml:92`, `docker-compose.yml:99`, `docker-compose.yml:111`, `frontend/package.json:9`.

### 8.2 Coverage Mapping Table
| Requirement / Risk Point | Mapped Test Case(s) | Key Assertion / Fixture | Coverage | Gap | Minimum Test Addition |
|---|---|---|---|---|---|
| Auth invalid login | `AuthFlowIntegrationTest.java:24` | 401 + `Invalid credentials` | sufficient | none | n/a |
| Authenticated profile fetch | `AuthFlowIntegrationTest.java:34` | `/api/auth/me` role PASSENGER | basically covered | no session-expiry path | add expired/revoked token tests |
| Recovery fallback token path | `AuthFlowIntegrationTest.java:44` | `REQ-` token assertion | basically covered | no successful recovery-code path assertion | add code-use success + single-use check |
| Passenger unauthenticated access | `PassengerApiIntegrationTest.java:28` | 401 on search | sufficient | none | n/a |
| Passenger search happy path | `PassengerApiIntegrationTest.java:34` | result primaryText `101` | basically covered | no dedup/sort formula assertions | add deterministic scoring/dedup assertions |
| Reminder preference validation | `PassengerApiIntegrationTest.java:44` | 422 for invalid input | basically covered | no DND boundary matrix | add valid/invalid boundary table |
| Reservation route-stop integrity | `PassengerApiIntegrationTest.java:75` | 422 for mismatched route/stop | sufficient | none | n/a |
| Message object-level not-found | `PassengerApiIntegrationTest.java:95` | 404 on missing ID | basically covered | no cross-user ownership test | add user A cannot read user B message/subscription |
| Admin route protection | `AdminDispatcherIntegrationTest.java:40` | passenger gets 403 on admin endpoint | sufficient | none | n/a |
| Dispatcher resubmit flow | `AdminDispatcherIntegrationTest.java:63` | return then resubmit increments count | basically covered | no lease-timeout path | add expired lease decision conflict test |
| Multi-approval uniqueness | `AdminDispatcherIntegrationTest.java:122` | second approve by same dispatcher => 409 | sufficient | no different-dispatcher completion test | add second dispatcher success path |
| Branch follow-up task creation | `AdminDispatcherIntegrationTest.java:157` | created `ABNORMAL_DATA_REVIEW` task | basically covered | only one hardcoded condition | add configurable branch rule coverage |
| Parsing template history | `AdminDispatcherIntegrationTest.java:195` | revision increments + distinct IDs | basically covered | no conflict merge test deep checks | add stale revision + manual merge conflict assertions |
| Frontend role guard | `auth.guard.spec.ts:7` | unauth redirect login | basically covered | no roleGuard unit tests | add role matrix guard tests |
| E2E status-path checks | `frontend/e2e/app.spec.ts:39` | 401/403/404/422 assertions | basically covered | no parsing/admin config CRUD E2E | add admin parsing + cleaning rule E2E |

### 8.3 Security Coverage Audit
- **Authentication:** basically covered (invalid login, authenticated profile), but missing token-expiry/replay/fingerprint-change tests.
- **Route authorization:** covered for at least one forbidden case (`AdminDispatcherIntegrationTest.java:40`) and 401 path (`PassengerApiIntegrationTest.java:28`).
- **Object-level authorization:** **insufficiently covered**; tests check missing IDs but not cross-user access denial.
- **Tenant/data isolation:** **cannot confirm fully** from tests; single-tenant assumptions exist, but no explicit isolation tests across multiple users for all resources.
- **Admin/internal protection:** partially covered (admin endpoint forbidden to passenger), but no explicit actuator security tests.

### 8.4 Final Coverage Judgment
- **Final coverage judgment: Fail**
- **Reasoning:** Important flows are tested, but major risks remain under-covered: cross-user object-level access, token/session security edge cases, observability/alert diagnostics behaviors, and static test-environment schema consistency. Tests could pass while severe authorization/isolation and operational defects remain.

## 9. Final Notes
- This audit is strictly static and does not claim runtime success.
- The repository is close to acceptance shape, but the blocker-level test-environment/schema issue and high-severity workflow/diagnostic gaps should be resolved before delivery acceptance.
