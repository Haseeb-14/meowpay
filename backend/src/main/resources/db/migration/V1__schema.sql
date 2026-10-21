-- Cats are the users. Each cat has exactly one account for now; keeping accounts
-- separate leaves room for a human's funding account later without a rewrite.
create table cats (
    id         bigserial primary key,
    name       text        not null unique,
    created_at timestamptz not null default now()
);

create table accounts (
    id         bigserial primary key,
    cat_id     bigint      not null unique references cats (id),
    -- Cached balance. The ledger below is the source of truth; this is what we
    -- lock and check on every transfer. The CHECK is the last line of defence.
    balance    bigint      not null default 0 check (balance >= 0),
    created_at timestamptz not null default now()
);

-- One row per completed transfer. (from_account_id, idempotency_key) is unique so a
-- retried request can be recognised and answered with the original result.
create table transfers (
    id              uuid primary key,
    idempotency_key text        not null,
    from_account_id bigint      not null references accounts (id),
    to_account_id   bigint      not null references accounts (id),
    amount          bigint      not null check (amount > 0),
    note            text,
    created_at      timestamptz not null default now(),
    check (from_account_id <> to_account_id),
    unique (from_account_id, idempotency_key)
);

-- Double-entry ledger: every transfer writes one negative row for the sender and
-- one positive row for the recipient. sum(amount) per account always equals
-- accounts.balance, and balance_after gives a statement without recomputation.
create table ledger_entries (
    id            bigserial primary key,
    account_id    bigint      not null references accounts (id),
    transfer_id   uuid        references transfers (id),
    kind          text        not null check (kind in ('TOPUP', 'TRANSFER_OUT', 'TRANSFER_IN')),
    amount        bigint      not null check (amount <> 0),
    balance_after bigint      not null check (balance_after >= 0),
    created_at    timestamptz not null default now()
);

create index transfers_from_account_idx on transfers (from_account_id, created_at desc);
create index transfers_to_account_idx   on transfers (to_account_id, created_at desc);
create index ledger_entries_account_idx on ledger_entries (account_id, id desc);
