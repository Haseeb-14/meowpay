"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { api, ApiError, type Cat, type LedgerLine, type Me } from "@/lib/api";

const CAT_KEY = "meowpay.catId";
const PENDING_KEY = "meowpay.pendingSend";

type Status =
  | { kind: "idle" }
  | { kind: "sending" }
  | { kind: "sent"; message: string }
  | { kind: "error"; message: string; retryable: boolean };

/** A send whose outcome we do not know yet. Survives a reload so Retry reuses the same key. */
type PendingSend = {
  idempotencyKey: string;
  meId: number;
  toCatId: number;
  amount: number;
  note: string;
};

function readPending(): PendingSend | null {
  try {
    const raw = sessionStorage.getItem(PENDING_KEY);
    return raw ? (JSON.parse(raw) as PendingSend) : null;
  } catch {
    return null;
  }
}

function writePending(p: PendingSend | null) {
  try {
    if (p) sessionStorage.setItem(PENDING_KEY, JSON.stringify(p));
    else sessionStorage.removeItem(PENDING_KEY);
  } catch {
    /* storage unavailable */
  }
}

export function Wallet() {
  const [cats, setCats] = useState<Cat[]>([]);
  const [meId, setMeId] = useState<number | null>(null);
  const [me, setMe] = useState<Me | null>(null);
  const [ledger, setLedger] = useState<LedgerLine[]>([]);
  const [loadError, setLoadError] = useState<string | null>(null);

  const [toCatId, setToCatId] = useState<string>("");
  const [amount, setAmount] = useState<string>("");
  const [note, setNote] = useState<string>("");
  const [status, setStatus] = useState<Status>({ kind: "idle" });

  // One Idempotency-Key per *intent*. It is created on submit, reused if the
  // request fails in a way where the server may still have acted (network, 5xx,
  // 503 try_again), and discarded as soon as the user edits the form or the
  // transfer succeeds. While unresolved it is also kept in sessionStorage so a
  // reload cannot turn a retry into a second transfer.
  const idempotencyKey = useRef<string | null>(null);
  const meIdRef = useRef<number | null>(null);
  useEffect(() => {
    meIdRef.current = meId;
  }, [meId]);

  const resetKey = () => {
    idempotencyKey.current = null;
    writePending(null);
  };

  // Load the directory once; restore who we were, and any send still in flight.
  useEffect(() => {
    api
      .cats()
      .then((list) => {
        setCats(list);
        const pending = readPending();
        let saved: number | null = null;
        try {
          const raw = localStorage.getItem(CAT_KEY);
          if (raw && list.some((c) => c.id === Number(raw))) saved = Number(raw);
        } catch {
          /* storage unavailable */
        }
        if (pending && list.some((c) => c.id === pending.meId)) {
          idempotencyKey.current = pending.idempotencyKey;
          setMeId(pending.meId);
          setToCatId(String(pending.toCatId));
          setAmount(String(pending.amount));
          setNote(pending.note);
          setStatus({
            kind: "error",
            message: "Your last send may not have completed.",
            retryable: true,
          });
        } else {
          setMeId(saved ?? list[0]?.id ?? null);
        }
      })
      .catch((e: ApiError) => setLoadError(e.message));
  }, []);

  const refresh = useCallback(async (catId: number) => {
    const [meData, lines] = await Promise.all([api.me(catId), api.ledger(catId)]);
    // Ignore a response for a cat we have since switched away from.
    if (meIdRef.current !== catId) return;
    setMe(meData);
    setLedger(lines);
  }, []);

  useEffect(() => {
    if (meId == null) return;
    try {
      localStorage.setItem(CAT_KEY, String(meId));
    } catch {
      /* storage unavailable */
    }
    let cancelled = false;
    Promise.all([api.me(meId), api.ledger(meId)])
      .then(([meData, lines]) => {
        if (cancelled) return;
        setMe(meData);
        setLedger(lines);
      })
      .catch((e: ApiError) => {
        if (!cancelled) setLoadError(e.message);
      });
    return () => {
      cancelled = true;
    };
  }, [meId]);

  function selectCat(id: number) {
    setMeId(id);
    setMe(null);
    setLedger([]);
    setToCatId("");
    setStatus({ kind: "idle" });
    resetKey();
  }

  const recipients = cats.filter((c) => c.id !== meId);
  const amountNumber = Number(amount);
  const amountValid = Number.isInteger(amountNumber) && amountNumber >= 1;
  const canSend = meId != null && toCatId !== "" && amountValid && status.kind !== "sending";

  async function send(e: React.FormEvent) {
    e.preventDefault();
    if (!canSend || meId == null) return;
    idempotencyKey.current ??= crypto.randomUUID();
    writePending({
      idempotencyKey: idempotencyKey.current,
      meId,
      toCatId: Number(toCatId),
      amount: amountNumber,
      note,
    });
    setStatus({ kind: "sending" });
    try {
      const transfer = await api.send(
        meId,
        { toCatId: Number(toCatId), amount: amountNumber, note: note.trim() || undefined },
        idempotencyKey.current,
      );
      resetKey();
      setAmount("");
      setNote("");
      setStatus({ kind: "sent", message: `Sent ${transfer.amount} treats to ${transfer.to.name}.` });
    } catch (err) {
      const apiErr = err instanceof ApiError ? err : new ApiError(0, "unknown", "Something went wrong");
      if (!apiErr.isRetryable) resetKey();
      setStatus({ kind: "error", message: apiErr.message, retryable: apiErr.isRetryable });
    } finally {
      // Refresh either way: on a loss or a timeout the balance may have changed under us.
      refresh(meId).catch(() => {});
    }
  }

  if (loadError) {
    return (
      <Shell>
        <p className="rounded-xl border border-red-300 bg-red-50 p-4 text-red-800 dark:border-red-900 dark:bg-red-950 dark:text-red-200">
          {loadError}
        </p>
      </Shell>
    );
  }

  return (
    <Shell>
      <header className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-3xl font-semibold tracking-tight">
            <span aria-hidden>🐾</span> MeowPay
          </h1>
          <p className="text-sm text-zinc-500 dark:text-zinc-400">Send treats to another cat.</p>
        </div>
        <label className="flex items-center gap-2 text-sm">
          <span className="text-zinc-500 dark:text-zinc-400">You are</span>
          <select
            className={selectClass}
            value={meId ?? ""}
            onChange={(e) => selectCat(Number(e.target.value))}
            disabled={cats.length === 0}
          >
            {cats.map((c) => (
              <option key={c.id} value={c.id}>
                {c.name}
              </option>
            ))}
          </select>
        </label>
      </header>

      <section className="rounded-2xl bg-amber-400 p-6 text-amber-950 shadow-sm">
        <p className="text-sm font-medium uppercase tracking-wide opacity-70">Balance</p>
        <p className="mt-1 text-5xl font-semibold tabular-nums">
          {me ? me.balance.toLocaleString() : "…"}
          <span className="ml-2 text-xl font-normal opacity-70">treats</span>
        </p>
        <p className="mt-2 text-sm opacity-80">{me ? me.name : "Loading"}</p>
      </section>

      <form onSubmit={send} className={cardClass}>
        <h2 className="text-lg font-semibold">Send treats</h2>

        <label className="block text-sm">
          <span className={labelClass}>To</span>
          <select
            className={selectClass + " mt-1 w-full"}
            value={toCatId}
            onChange={(e) => {
              setToCatId(e.target.value);
              resetKey();
            }}
            required
          >
            <option value="">Choose a cat</option>
            {recipients.map((c) => (
              <option key={c.id} value={c.id}>
                {c.name}
              </option>
            ))}
          </select>
        </label>

        <label className="block text-sm">
          <span className={labelClass}>Amount</span>
          <input
            className={inputClass}
            type="number"
            inputMode="numeric"
            min={1}
            step={1}
            placeholder="How many treats?"
            value={amount}
            onChange={(e) => {
              setAmount(e.target.value);
              resetKey();
            }}
            required
          />
          {me && amountValid && amountNumber > me.balance && (
            <span className="mt-1 block text-xs text-red-600 dark:text-red-400">
              That is more than you have.
            </span>
          )}
        </label>

        <label className="block text-sm">
          <span className={labelClass}>Note (optional)</span>
          <input
            className={inputClass}
            type="text"
            maxLength={140}
            placeholder="for the zoomies"
            value={note}
            onChange={(e) => {
              setNote(e.target.value);
              resetKey();
            }}
          />
        </label>

        <button
          type="submit"
          disabled={!canSend}
          className="w-full rounded-xl bg-zinc-900 px-4 py-3 font-medium text-white transition hover:bg-zinc-700 disabled:cursor-not-allowed disabled:opacity-40 dark:bg-amber-400 dark:text-amber-950 dark:hover:bg-amber-300"
        >
          {status.kind === "sending"
            ? "Sending…"
            : status.kind === "error" && status.retryable
              ? "Retry"
              : "Send treats"}
        </button>

        <StatusLine status={status} />
      </form>

      <section className={cardClass}>
        <h2 className="text-lg font-semibold">Activity</h2>
        {ledger.length === 0 ? (
          <p className="text-sm text-zinc-500 dark:text-zinc-400">No treats have moved yet.</p>
        ) : (
          <ul className="divide-y divide-zinc-200 dark:divide-zinc-800">
            {ledger.map((line) => {
              const incoming = line.amount > 0;
              const label =
                line.kind === "TOPUP"
                  ? "Top-up"
                  : line.kind === "TRANSFER_OUT"
                    ? `To ${line.counterparty?.name ?? "a cat"}`
                    : `From ${line.counterparty?.name ?? "a cat"}`;
              return (
                <li key={line.id} className="flex items-start justify-between gap-4 py-3 text-sm">
                  <div>
                    <p className="font-medium">{label}</p>
                    {line.note && <p className="text-zinc-500 dark:text-zinc-400">{line.note}</p>}
                    <p className="text-xs text-zinc-400 dark:text-zinc-500">
                      {new Date(line.createdAt).toLocaleString()}
                    </p>
                  </div>
                  <div className="shrink-0 text-right">
                    <p
                      className={
                        "font-semibold tabular-nums " +
                        (incoming
                          ? "text-emerald-600 dark:text-emerald-400"
                          : "text-zinc-700 dark:text-zinc-300")
                      }
                    >
                      {incoming ? "+" : "−"}
                      {Math.abs(line.amount).toLocaleString()}
                    </p>
                    <p className="text-xs tabular-nums text-zinc-400 dark:text-zinc-500">
                      {line.balanceAfter.toLocaleString()} after
                    </p>
                  </div>
                </li>
              );
            })}
          </ul>
        )}
      </section>
    </Shell>
  );
}

function StatusLine({ status }: { status: Status }) {
  if (status.kind === "sent") {
    return <p role="status" className="text-sm text-emerald-700 dark:text-emerald-400">{status.message}</p>;
  }
  if (status.kind === "error") {
    return (
      <p role="alert" className="text-sm text-red-600 dark:text-red-400">
        {status.message}
        {status.retryable && " Retrying is safe: the same request will not be applied twice."}
      </p>
    );
  }
  return null;
}

function Shell({ children }: { children: React.ReactNode }) {
  return (
    <main className="mx-auto flex w-full max-w-xl flex-1 flex-col gap-6 px-4 py-8 sm:py-12">
      {children}
    </main>
  );
}

const cardClass =
  "flex flex-col gap-4 rounded-2xl border border-zinc-200 bg-white p-6 shadow-sm dark:border-zinc-800 dark:bg-zinc-950";
const labelClass = "font-medium text-zinc-700 dark:text-zinc-300";
const inputClass =
  "mt-1 w-full rounded-xl border border-zinc-300 bg-white px-3 py-2 text-base outline-none focus:border-amber-500 focus:ring-2 focus:ring-amber-200 dark:border-zinc-700 dark:bg-zinc-900 dark:focus:ring-amber-900";
const selectClass =
  "rounded-xl border border-zinc-300 bg-white px-3 py-2 text-base outline-none focus:border-amber-500 focus:ring-2 focus:ring-amber-200 dark:border-zinc-700 dark:bg-zinc-900 dark:focus:ring-amber-900";
