# MeowPay

A digital wallet for cats. Humans top it up; cats send each other **treats**.

This repo is a thin vertical slice of the **transfer** flow: one cat sends treats to another,
from a real backend with real persistence through a web UI.

## Stack

- **Backend:** Kotlin + Spring Boot 4 (Web, Data JPA, Validation, Flyway) on Java 17
- **Database:** PostgreSQL 16 (via Docker Compose)
- **Web:** Next.js (App Router) + TypeScript + Tailwind

## Layout

```
backend/   Spring Boot service
web/       Next.js app
compose.yaml   Postgres for local dev
```

## Running locally

Prerequisites: Java 17+, Node 20+, pnpm, Docker.

```sh
# 1. Database
docker compose up -d

# 2. Backend (http://localhost:8080)
cd backend && ./gradlew bootRun

# 3. Web (http://localhost:3000)
cd web && pnpm install && pnpm dev
```

## Status

Scaffold only. Transfer flow not yet implemented.
