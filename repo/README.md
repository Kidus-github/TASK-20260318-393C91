# City Bus Operation and Service Coordination Platform

## Overview

This repository contains a Docker-first LAN deployment for a city bus coordination platform with three role-based experiences:

- Passenger: search routes and stops, configure reminders, manage message center
- Dispatcher: process workflow tasks, approvals, and abnormal data reviews
- Administrator: manage users, templates, dictionaries, parsing templates, and ranking rules

The stack is:

- Angular frontend
- Spring Boot backend
- PostgreSQL database
- Local scheduled workers, DB-backed queue, and PostgreSQL backup sidecar

## Prerequisites

- Docker
- Docker Compose

## Quick Start

1. Copy `.env.example` to `.env`.
2. Set strong PostgreSQL credentials, JWT secrets, bootstrap passwords, and bootstrap recovery codes in `.env`.
3. Run:

```bash
docker compose up --build
```

Bootstrap accounts are created automatically on first start using the credentials you configure in `.env`:

- Admin username: `admin`
- Dispatcher username: `dispatcher`
- Passenger username: `passenger`

Bootstrap recovery codes are also supplied from `.env`:

- `BOOTSTRAP_ADMIN_RECOVERY_CODE`
- `BOOTSTRAP_DISPATCHER_RECOVERY_CODE`
- `BOOTSTRAP_PASSENGER_RECOVERY_CODE`

## Testing

Run all unit, integration, and Playwright tests with:

```bash
./run_tests.sh
```

This script is the canonical test entrypoint and returns a non-zero exit code if any suite fails.

## Architecture Overview

Backend modules:

- `domain`: business entities, enums, and domain rules
- `application`: use-case services and orchestration
- `infrastructure`: persistence, security, scheduling, bootstrap, tracing
- `api`: REST controllers, request/response DTOs, global error handling

Frontend modules:

- `core`: auth, guards, interceptors, API client
- `features/passenger`: search, reminders, message center
- `features/dispatcher`: task dashboard and approvals
- `features/admin`: templates, dictionaries, parsing, system config
- `layout`: role-aware application shell

## API Summary

### Auth

- `POST /api/auth/login`
- `POST /api/auth/refresh`
- `POST /api/auth/logout`
- `POST /api/auth/recover`
- `GET /api/auth/me`

### Passenger

- `GET /api/passenger/search/suggestions`
- `GET /api/passenger/search/results`
- `GET /api/passenger/reminders/preferences`
- `PUT /api/passenger/reminders/preferences`
- `GET /api/passenger/reminders/subscriptions`
- `POST /api/passenger/reminders/subscriptions`
- `POST /api/passenger/reminders/subscriptions/{id}/check-in`
- `POST /api/passenger/reminders/subscriptions/{id}/cancel`
- `GET /api/passenger/messages`
- `POST /api/passenger/messages/{id}/read`

### Dispatcher

- `GET /api/dispatcher/tasks`
- `GET /api/dispatcher/tasks/{id}`
- `POST /api/dispatcher/tasks/{id}/claim`
- `POST /api/dispatcher/tasks/{id}/decision`
- `POST /api/dispatcher/tasks/{id}/resubmit`
- `POST /api/dispatcher/tasks/batch-decision`

### Admin

- `GET /api/admin/templates`
- `PUT /api/admin/templates/{id}`
- `GET /api/admin/search-config`
- `PUT /api/admin/search-config`
- `GET /api/admin/dictionaries`
- `PUT /api/admin/dictionaries/{id}`
- `GET /api/admin/cleaning-rules`
- `PUT /api/admin/cleaning-rules/{id}`
- `GET /api/admin/parsing/templates`
- `POST /api/admin/parsing/templates`
- `POST /api/admin/parsing/parse`
- `GET /api/admin/alerts`
- `GET /api/admin/reports`
- `GET /api/admin/reports/{id}`
- `GET /api/admin/users`
- `POST /api/admin/users/reset-password`
- `POST /api/admin/users/reset-password/reveal`

### System

- `GET /actuator/health`
- `GET /api/system/version`

## Verification

- Frontend: `http://localhost:4200`
- Backend: `http://localhost:8080`
- Health: `http://localhost:8080/actuator/health`

## Operational Notes

- All configuration uses environment variables.
- The backend applies Flyway migrations on startup.
- Seed data is inserted automatically if the database is empty.
- The `postgres-backup` service writes rolling SQL dumps to the `postgres_backups` volume.
- Detailed actuator info and metrics are restricted to admin-authenticated access.
- The platform is designed for an air-gapped LAN and does not depend on external email, SMS, or push providers.
- Additional reference docs:
  - `docs/city-bus-platform-architecture.md`
  - `docs/api.md`
  - `docs/testing.md`
  - `docs/ops.md`
