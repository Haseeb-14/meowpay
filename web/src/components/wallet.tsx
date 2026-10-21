"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { api, ApiError, type Cat, type Me, type Transfer } from "@/lib/api";

const STORAGE_KEY = "meowpay.catId";

type Status =
  | { kind: "idle" }
  | { kind: "sending" }
  | { kind: "sent"; message: string }
  | { kind: "error"; message: string; retryable: boolean };

export function Wallet() {
  const [cats, setCats] = useState<Cat[]>([]);
  const [meId, setMeId] = useState<number | null>(null);
  const [me, setMe] = useState<Me | null>(null);
  const [transfers, setTransfers] = useState<Transfer[]>([]);
  const [loadError, setLoadError] = useState<string | null>(null);

  const [toCatId, setToCatId] = useState<string>("");
  const [amount, setAmount] = useState<string>("");
  const [note, setNote] = useState<string>("");
  const [status, setStatus] = useState<Status>({ kind: "idle" });

  // One Idempotency-Key per *intent*. It is created on submit, reused if the
  // request fails in a way where the server may still have acted (network, 5xx),
  // and discarded as soon as the user edits the form or the transfer succeeds.
  const idempotencyKey = useRef<string | null>(null);
  const resetKey = () => {
    idempotencyKey.current = null;
  };

  // Load the directory once; restore who we were last time.
  useEffect(() => {
    api
      .cats()
      .then((list) => {
        setCats(list);
        let saved: number | null = null;
        try {
          const raw = localStorage.getItem(STORAGE_KEY);
          if (raw && list.some((c) => c.id === Number(raw))) saved = Number(raw);
        } catch {
          /* storage unavailable */
        }
        setMeId(saved ?? list[0]?.id ?? null);
      })
      .catch((e: ApiError) => setLoadError(e.message));
  }, []);

  const refresh = useCallback(async (catId: number) => {
    const [meData, history] = await Promise.all([api.me(catId), api.transfers(catId)]);
    setMe(meData);
    setTransfers(history);
  }, []);

  useEffect(() => {
    if (meId == null) return;
    try {
      localStorage.setItem(STORAGE_KEY, String(meId));
    } catch {
      /* storage unavailable */
    }
    let cancelled = false;
    Promise.all([api.me(meId), api.transfers(meId)])
      .then(([meData, history]) => {
        if (cancelled) return;
        setMe(meData);
        setTransfers(history);
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
    setTransfers([]);
    setToCatId("");
    setStatus({ kind: "idle" });
    resetKey();
  }

  const recipients = cats.filter((c) => c.id !== meId);
  const amountNumber = Number(amount);
  const amountValid = Number.isInteger(amountNumber) && amountNumber >= 1;
  const canSend =
    meId != null && toCatId !== "" && amountValid && status.kind !== "sending";

  async function send(e: React.FormEvent) {
    e.preventDefault();
    if (!canSend || meId == null) return;
    idempotencyKey.current ??= crypto.randomUUID();
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
        {transfers.length === 0 ? (
          <p className="text-sm text-zinc-500 dark:text-zinc-400">No treats have moved yet.</p>
        ) : (
          <ul className="divide-y divide-zinc-200 dark:divide-zinc-800">
            {transfers.map((t) => {
              const sent = t.from.id === meId;
              return (
                <li key={t.id} className="flex items-start justify-between gap-4 py-3 text-sm">
                  <div>
                    <p className="font-medium">
                      {sent ? `To ${t.to.name}` : `From ${t.from.name}`}
                    </p>
                    {t.note && <p className="text-zinc-500 dark:text-zinc-400">{t.note}</p>}
                    <p className="text-xs text-zinc-400 dark:text-zinc-500">
                      {new Date(t.createdAt).toLocaleString()}
                    </p>
                  </div>
                  <p
                    className={
                      "shrink-0 font-semibold tabular-nums " +
                      (sent ? "text-zinc-700 dark:text-zinc-300" : "text-emerald-600 dark:text-emerald-400")
                    }
                  >
                    {sent ? "−" : "+"}
                    {t.amount.toLocaleString()}
                  </p>
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
