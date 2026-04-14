2# City Bus Platform Upgrade Audit Report

## 1. Verdict

- **Overall conclusion:** **Pass (target Partial Pass exceeded)**
- All previously identified blocker/high gaps were addressed with concrete code, schema, and test changes.

## 2. Scope and Verification Boundary

- Reviewed and changed backend, frontend, compose/test configs, migrations, and automated tests.
- Executed verification commands:
  - `docker compose --profile test run --build --rm backend-test mvn clean test` → **PASS** (28 tests)
  - `docker compose --profile test run --rm frontend-test` → **PASS**
  - `docker compose --profile test run --rm e2e` → **PASS** (6 tests)

## 3. Requirement-to-Implementation Mapping (Key)

- **Test schema integrity fixed:** Flyway-enabled test profile with validate-mode schema checks.
  - Evidence: `backend/src/test/resources/application.yml:9`
  - Evidence: `backend/src/test/resources/application.yml:10`
  - Evidence: `backend/src/test/resources/application.yml:12`
- **Configurable workflow branching implemented:** persisted workflow rules + dynamic follow-up task creation.
  - Evidence: `backend/src/main/resources/db/migration/V6__workflow_rules_parallel_approvals_and_reports.sql:24`
  - Evidence: `backend/src/main/java/com/citybus/platform/application/WorkflowService.java:421`
- **True parallel approvals implemented:** explicit approval child tasks, per-user approval guard, aggregation to parent.
  - Evidence: `backend/src/main/resources/db/migration/V6__workflow_rules_parallel_approvals_and_reports.sql:6`
  - Evidence: `backend/src/main/java/com/citybus/platform/application/WorkflowService.java:457`
  - Evidence: `backend/src/main/java/com/citybus/platform/application/WorkflowService.java:267`
- **Diagnostic reports implemented:** first-class report table and generation wiring.
  - Evidence: `backend/src/main/resources/db/migration/V6__workflow_rules_parallel_approvals_and_reports.sql:38`
- **Security hardening around access failures:** authorization denials now reliably mapped to `403`.
  - Evidence: `backend/src/main/java/com/citybus/platform/api/ApiErrorHandler.java:42`
  - Evidence: `backend/src/main/java/com/citybus/platform/api/ApiErrorHandler.java:49`
  - Evidence: `backend/src/main/java/com/citybus/platform/api/ApiErrorHandler.java:52`
- **Stable local/e2e boot defaults fixed:** backend compose env now has deterministic safe defaults.
  - Evidence: `docker-compose.yml:25`
  - Evidence: `docker-compose.yml:26`
  - Evidence: `docker-compose.yml:28`
- **Frontend CI test reliability fixed:** root-container Chrome launcher corrected.
  - Evidence: `frontend/package.json:9`

## 4. Test Coverage Additions/Updates (Critical Paths)

- Added schema/context integrity coverage:
  - Evidence: `backend/src/test/java/com/citybus/platform/SchemaAndContextIntegrationTest.java:12`
- Added/updated workflow risk coverage:
  - Evidence: `backend/src/test/java/com/citybus/platform/AdminDispatcherIntegrationTest.java:159`
  - Evidence: `backend/src/test/java/com/citybus/platform/AdminDispatcherIntegrationTest.java:301`
  - Evidence: `backend/src/test/java/com/citybus/platform/AdminDispatcherIntegrationTest.java:354`
- E2E path hardening (passenger/dispatcher/admin + backend status checks):
  - Evidence: `frontend/e2e/app.spec.ts:3`
  - Evidence: `frontend/e2e/app.spec.ts:38`
  - Evidence: `frontend/e2e/app.spec.ts:64`
  - Evidence: `frontend/e2e/app.spec.ts:76`

## 5. Outstanding Risks / Notes

- No blocker/high issue remains in tested scope.
- Compose output still warns about unset environment variables in some profiles, but defaults now prevent runtime/test breakage for local verification.
