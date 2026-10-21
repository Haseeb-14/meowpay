# MeowPay

A digital wallet for cats. Humans top it up; cats send each other **treats**.

This repo is one thin vertical slice of the **transfer** flow: a cat picks another cat,
types an amount, hits Send, and the treats move. Real backend, real Postgres, real UI.

**Time spent:** about 4 hours, including scoping, the build, a review-and-fix pass, and
this write-up.

## What's in the slice

- `POST /api/transfers` moves treats atomically, with row locks, an idempotency key and a
  double-entry ledger behind it.
- `GET /api/me`, `GET /api/transfers` and `GET /api/ledger` show a cat's balance, its
  sent/received transfers, and its statement with the balance after every line.
- A one-page web UI: pick who you are, pick a recipient, send, watch the balance and
  activity update. Switch cats to see the other side of the transfer.
- A test suite that boots a throwaway Postgres and proves the parts that are hard to
  check by hand: no overdraft under concurrent sends, no deadlock on opposite-direction
  sends, replaying a request moves treats once, and a held lock fails fast with 503.
  CI runs it on every push.

## Stack

| Part     | Choice                                                       |
|----------|--------------------------------------------------------------|
| Backend  | Kotlin, Spring Boot 4.1 (Web MVC, Data JPA, Validation, Flyway), Java 17 |
| Database | PostgreSQL 16 via Docker Compose; Flyway migrations + seed    |
| Web      | Next.js 16 (App Router), React 19, TypeScript, Tailwind 4     |
| Tests    | JUnit 5, Spring Boot Test, MockMvc, Testcontainers 2          |

## Layout

```
backend/src/main/kotlin/com/meowpay/
  cat/        Cat entity, directory endpoint, CatRef
  account/    Account entity (the locked row), /me
  transfer/   Transfer entity, TransferService (the money-moving transaction), /transfers, DTOs
  ledger/     LedgerEntry, statement query, /ledger
  api/        error shape, X-Cat-Id resolver, CORS, /health
  config/     MeowPayProperties (lock timeout, CORS origins)
backend/src/main/resources/db/migration/   versioned schema (Flyway)
backend/src/main/resources/db/seed/        repeatable demo data, excluded by the prod profile
backend/src/test/                          Testcontainers config, TestCats helper, service + API tests
web/src/app/          Next.js route and layout
web/src/hooks/        useWallet: state, loading, idempotency-key lifecycle
web/src/components/   Wallet (composition), BalanceCard, SendForm, ActivityList
web/src/lib/          typed API client, storage helpers
.github/workflows/    CI
compose.yaml          Postgres
```

## Run it from a clean clone

Prerequisites: Docker, Java 17+, Node 20+, pnpm (`corepack enable` gives you pnpm).

```sh
# 1. Database (Postgres on localhost:5432)
docker compose up -d

# 2. Backend on http://localhost:8080  (Flyway creates the schema and seeds four cats)
cd backend && ./gradlew bootRun

# 3. Web on http://localhost:3000
cd web && pnpm install && pnpm dev
```

Open http://localhost:3000. You start as the first cat in the list. Send some treats to
Luna, then switch "You are" to Luna and see them arrive. Tofu starts with zero treats, so
switching to Tofu and sending shows the insufficient-funds path.

Already have a Postgres on 5432? Run the container on another port and point the
backend at it:

```sh
POSTGRES_PORT=5433 docker compose up -d
DB_URL=jdbc:postgresql://localhost:5433/meowpay ./gradlew bootRun
```

The web app proxies `/api/*` to the backend. If the backend is not on
`http://localhost:8080`, set `API_URL` for `pnpm dev`.

### Tests

```sh
cd backend && ./gradlew test
```

Testcontainers starts a `postgres:16` container for the test JVM and Flyway applies the
real migrations to it. Docker must be running. The same suite runs in GitHub Actions
(`.github/workflows/ci.yml`) together with the web lint, typecheck and build.

### Poking it with curl

```sh
curl -s localhost:8080/api/cats
# [{"id":4,"name":"Biscuit"},{"id":2,"name":"Luna"},{"id":1,"name":"Mochi"},{"id":3,"name":"Tofu"}]

curl -s -X POST localhost:8080/api/transfers \
  -H 'X-Cat-Id: 1' -H 'Idempotency-Key: demo-1' -H 'Content-Type: application/json' \
  -d '{"toCatId": 2, "amount": 25, "note": "tuna fund"}'
# 201 {"id":"...","from":{"id":1,"name":"Mochi"},"to":{"id":2,"name":"Luna"},"amount":25,...}

# Same key again: 200 with Idempotency-Replayed: true, same transfer, nothing moves twice.
# Same key, different amount: 422 idempotency_key_mismatch.

curl -s localhost:8080/api/ledger -H 'X-Cat-Id: 2'
# [{"kind":"TRANSFER_IN","amount":25,"balanceAfter":145,"counterparty":{"id":1,"name":"Mochi"},...},
#  {"kind":"TOPUP","amount":120,"balanceAfter":120,"counterparty":null,...}]

curl -s localhost:8080/api/me -H 'X-Cat-Id: 2'
# {"id":2,"name":"Luna","balance":145}
```

## API

| Method | Path             | Acting cat | Notes |
|--------|------------------|------------|-------|
| GET    | `/api/cats`      | no         | Directory: `[{id, name}]` |
| GET    | `/api/me`        | yes        | `{id, name, balance}` |
| GET    | `/api/transfers` | yes        | Sent and received, newest first, 50 max |
| POST   | `/api/transfers` | yes        | `{toCatId, amount, note?}` + `Idempotency-Key` header. `201` new, `200` + `Idempotency-Replayed: true` on replay |
| GET    | `/api/ledger`    | yes        | Statement: every movement with `balanceAfter`, newest first, 50 max |
| GET    | `/api/health`    | no         | `{ok, cats}` |

The acting cat is the `X-Cat-Id` header (see *Identity* below). Errors are RFC 9457
problem+json with a stable `code`:

```json
{ "status": 422, "title": "insufficient_funds", "detail": "Balance is 5 treats, cannot send 6", "code": "insufficient_funds", "instance": "/api/transfers" }
```

Codes: `unauthenticated` 401, `cat_not_found` / `not_found` 404, `validation_error` /
`malformed_body` / `missing_header` / `invalid_parameter` 400, `self_transfer` /
`insufficient_funds` / `idempotency_key_mismatch` 422, `try_again` 503, `internal_error` 500.
Spring's own 404/405/415 responses and unexpected exceptions go through the same handler,
so there is exactly one error shape and a 500 never carries a stack trace.

## Decisions and trade-offs

**Treats are integers.** `bigint` end to end, no decimals, no floating point. If treats ever
get a fractional unit, that is a new column in minor units, not a `numeric` conversion.

**A ledger plus a cached balance.** `accounts.balance` is what gets locked and checked.
`ledger_entries` is the append-only source of truth: every transfer writes one negative
row for the sender and one positive row for the recipient, each with `balance_after`.
The invariant `sum(ledger.amount) == accounts.balance` is asserted in the tests. A
`CHECK (balance >= 0)` on the column is the last line of defence if the code is ever wrong.

**Pessimistic row locks, in a fixed order.** The transfer locks both account rows with
`SELECT ... FOR UPDATE`, always in ascending cat-id order. The fixed order means A→B and
B→A can never deadlock. Locking the sender's row also serialises every transfer from that
cat, which is what makes the idempotency check below race-free without a second
mechanism. I chose this over optimistic versioning (retries under contention make the
client logic harder) and over `SERIALIZABLE` (same retry problem, and harder to reason
about in a code review). The cost is that one cat's transfers run one at a time, which
is fine for a wallet. Lock waits are bounded: each transfer sets a transaction-local
`lock_timeout` (`LOCK_TIMEOUT_MS`, default 3s) and a timeout becomes `503 try_again`
with a hint to retry with the same key, so a hot account degrades to fast failures
instead of a pile of threads holding database connections.

**Idempotency-Key is required, not optional.** Money movement over HTTP needs it: a
client that times out cannot know whether the server acted. Same sender + same key +
same payload returns the original transfer with `200`. Same key with a different payload
is a `422`, because silently returning the old transfer would hide a client bug. The key
is scoped per sender, so two cats can use the same string without colliding. The web UI
mints one key per intent and reuses it only when the failure was a network error or
a 5xx; a 4xx or any edit to the form discards it. While a send is unresolved the key and
payload also sit in `sessionStorage`, so a reload followed by Retry still cannot double-send.

**All or nothing.** Insufficient funds, unknown recipient, self-transfer: the request
fails as a whole, nothing is written, and the HTTP code says why. There is no partial
state to clean up.

**Identity comes from a header, and only there.** Authentication is a separate slice,
so the acting cat is named by `X-Cat-Id` and must exist. It is resolved in exactly one
place (`CurrentCatArgumentResolver`), and no handler reads the sender from the body.
Swapping in a session or JWT is a change to that one class. I would rather be explicit
about this than ship a half-built login that the reviewer has to evaluate too.

**JPA, but the one SQL semantic that matters is explicit.** The scaffold is Spring Data
JPA and that is the stack you use, so I stayed with it. The lock is a `@Lock(PESSIMISTIC_WRITE)`
on a JPQL query, which Hibernate renders as `FOR UPDATE`, and everything that touches
money lives in one `@Transactional` method. Entities are mapped to DTOs inside the
transaction (`open-in-view` is off) so nothing lazy leaks into the controller.

**Flyway owns the schema; Hibernate only validates it.** `ddl-auto: validate` catches a
mapping that drifts from a migration at startup rather than in production. Demo cats
live in a repeatable, idempotent seed under `db/seed`, which the `prod` profile excludes;
versioned migrations never contain data.

**History is ordered by a database sequence, not a timestamp.** `created_at` comes from
the app server's clock. `transfers.seq` (a `bigserial`) is the ordering key, so two
transfers in the same microsecond, or from two servers with skewed clocks, still have
one stable order.

**The web app proxies the API.** A Next.js rewrite sends `/api/*` to Spring, so the
browser never does a cross-origin call and the backend URL is a server-side setting.
CORS is still configured for `http://localhost:3000` (`CORS_ALLOWED_ORIGINS`) so that
calling `:8080` straight from a browser console works too.

## What I skipped on purpose

- **Login.** See *Identity* above.
- **Top-ups.** Humans adding treats is the other half of the product. Opening balances
  are seeded as `TOPUP` ledger rows so the ledger is complete from row one.
- **Pagination, filtering, search.** History returns the latest 50.
- **Pending or scheduled transfers, reversals.** Every transfer completes synchronously
  or fails. A `status` column and a reversal that writes compensating ledger rows is the
  obvious extension; the ledger shape already supports it.
- **Rate limiting, audit log, structured logging, metrics.** Needed in production, not
  needed to show the slice works.
- **Dockerfiles for the apps.** `docker compose` only runs Postgres. A reviewer can run
  the two apps directly; CI covers the build.
- **Frontend tests.** The behaviour worth testing (money) is tested where it lives.

## What I would do next

1. Real sessions: a login endpoint issuing a token, resolved by the same argument resolver.
2. Top-ups as a first-class operation with a treasury account on the other side, so the
   ledger sums to zero globally rather than per transfer. A deferred constraint trigger
   asserting that each transfer's entries net to zero would then make the ledger
   self-protecting instead of relying on the service code.
3. A `transfer_batches` or `status` column once there is any asynchronous step.
4. Cursor pagination on history and the ledger.
5. Pool and timeout tuning: Hikari is on defaults (10 connections, 30s wait). With the
   lock timeout in place the next bottleneck is the pool, and `pg_stat_statements`
   plus a slow-lock alert would show it first.
6. A Playwright flow (send, switch cat, see it arrive) for the one path the API tests
   cannot cover.

## How I worked with AI

I used Claude Code (Anthropic's CLI agent, model Fable 5.1) as a pair programmer for
the whole exercise. Concretely:

- I chose the stack and set up the architecture and project structure myself before the
  agent wrote any code: the Kotlin/Spring Boot backend with Flyway and Data JPA, the
  Next.js app, the Postgres compose file, and the package-by-feature layout (`cat`,
  `account`, `transfer`, `ledger`, `api`). That scaffold is the first commit. The agent
  worked inside that structure and followed it; it did not get to reshape it.
- I set the scope and the design: ledger plus cached balance, pessimistic locks in a fixed
  order, mandatory idempotency key with mismatch detection, whole-request failure, header
  identity with a single seam, problem+json errors. The agent drafted the code from that
  direction and I reviewed every file.
- The agent read the Next.js 16 docs bundled in `node_modules/next/dist/docs` before
  writing the web code, because its training predates that major version, and checked
  Maven Central for Spring Boot 4.1 and Testcontainers 2 artifact names rather than
  guessing. Both saved a round of broken builds.
- Two things the lint and compile loop caught that I kept: the React Compiler's
  `set-state-in-effect` and `refs` rules shaped how cat switching and the idempotency
  key are handled, and Testcontainers 2 renamed its Postgres module and package.
- After the first working version I asked it for a critical review of its own output
  and fixed what came back: unbounded lock waits, a missing catch-all error handler,
  seed data in a versioned migration, and an idempotency key that did not survive a
  reload. Those are separate commits so the history shows the before and after.
- Every commit the agent helped write carries a `Co-Authored-By` trailer so the history
  shows where it was involved. I can walk through any line.
