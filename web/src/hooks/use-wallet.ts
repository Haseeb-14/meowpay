"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { api, ApiError, type Cat, type LedgerLine, type Me } from "@/lib/api";
import { readPending, readSavedCat, saveCat, writePending } from "@/lib/storage";

export type Status =
  | { kind: "idle" }
  | { kind: "sending" }
  | { kind: "sent"; message: string }
  | { kind: "error"; message: string; retryable: boolean };

export type SendFields = { toCatId: string; amount: string; note: string };

/**
 * All wallet state: who you are, your balance and ledger, the send form, and the
 * idempotency key for the send in flight.
 *
 * One Idempotency-Key per *intent*. It is created on submit, reused if the request
 * fails in a way where the server may still have acted (network, 5xx, 503 try_again),
 * and discarded as soon as the user edits the form or the transfer succeeds. While
 * unresolved it is also kept in sessionStorage so a reload cannot turn a retry into
 * a second transfer.
 */
export function useWallet() {
  const [cats, setCats] = useState<Cat[]>([]);
  const [meId, setMeId] = useState<number | null>(null);
  const [me, setMe] = useState<Me | null>(null);
  const [ledger, setLedger] = useState<LedgerLine[]>([]);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [fields, setFields] = useState<SendFields>({ toCatId: "", amount: "", note: "" });
  const [status, setStatus] = useState<Status>({ kind: "idle" });

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
        if (pending && list.some((c) => c.id === pending.meId)) {
          idempotencyKey.current = pending.idempotencyKey;
          setMeId(pending.meId);
          setFields({
            toCatId: String(pending.toCatId),
            amount: String(pending.amount),
            note: pending.note,
          });
          setStatus({
            kind: "error",
            message: "Your last send may not have completed.",
            retryable: true,
          });
          return;
        }
        const saved = readSavedCat();
        setMeId(saved != null && list.some((c) => c.id === saved) ? saved : (list[0]?.id ?? null));
      })
      .catch((e: ApiError) => setLoadError(e.message));
  }, []);

  // Whenever the acting cat changes, remember it and load its balance and ledger.
  useEffect(() => {
    if (meId == null) return;
    saveCat(meId);
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

  const refresh = useCallback(async (catId: number) => {
    const [meData, lines] = await Promise.all([api.me(catId), api.ledger(catId)]);
    // Ignore a response for a cat we have since switched away from.
    if (meIdRef.current !== catId) return;
    setMe(meData);
    setLedger(lines);
  }, []);

  function selectCat(id: number) {
    setMeId(id);
    setMe(null);
    setLedger([]);
    setFields((f) => ({ ...f, toCatId: "" }));
    setStatus({ kind: "idle" });
    resetKey();
  }

  function updateField<K extends keyof SendFields>(key: K, value: SendFields[K]) {
    setFields((f) => ({ ...f, [key]: value }));
    resetKey(); // an edited form is a new intent
  }

  const amountNumber = Number(fields.amount);
  const amountValid = Number.isInteger(amountNumber) && amountNumber >= 1;
  const canSend = meId != null && fields.toCatId !== "" && amountValid && status.kind !== "sending";

  async function send() {
    if (!canSend || meId == null) return;
    idempotencyKey.current ??= crypto.randomUUID();
    writePending({
      idempotencyKey: idempotencyKey.current,
      meId,
      toCatId: Number(fields.toCatId),
      amount: amountNumber,
      note: fields.note,
    });
    setStatus({ kind: "sending" });
    try {
      const transfer = await api.send(
        meId,
        { toCatId: Number(fields.toCatId), amount: amountNumber, note: fields.note.trim() || undefined },
        idempotencyKey.current,
      );
      resetKey();
      setFields((f) => ({ ...f, amount: "", note: "" }));
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

  return {
    cats,
    meId,
    me,
    ledger,
    loadError,
    fields,
    status,
    amountValid,
    amountNumber,
    canSend,
    selectCat,
    updateField,
    send,
  };
}
