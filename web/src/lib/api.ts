export type Cat = { id: number; name: string };
export type Me = Cat & { balance: number };
export type Transfer = {
  id: string;
  from: Cat;
  to: Cat;
  amount: number;
  note: string | null;
  createdAt: string;
};

export type LedgerKind = "TOPUP" | "TRANSFER_OUT" | "TRANSFER_IN";
export type LedgerLine = {
  id: number;
  kind: LedgerKind;
  amount: number;
  balanceAfter: number;
  counterparty: Cat | null;
  note: string | null;
  transferId: string | null;
  createdAt: string;
};

export class ApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly code: string,
    message: string,
  ) {
    super(message);
  }

  get isRetryable() {
    return this.status === 0 || this.status >= 500;
  }
}

async function request<T>(path: string, init: RequestInit = {}, catId?: number): Promise<T> {
  const headers: Record<string, string> = {
    Accept: "application/json",
    ...(init.headers as Record<string, string> | undefined),
  };
  if (init.body) headers["Content-Type"] = "application/json";
  if (catId != null) headers["X-Cat-Id"] = String(catId);

  let res: Response;
  try {
    res = await fetch(`/api${path}`, { ...init, headers, cache: "no-store" });
  } catch {
    throw new ApiError(0, "network_error", "Could not reach MeowPay. Is the backend running?");
  }
  if (!res.ok) {
    let code = "http_error";
    let message = `Request failed (${res.status})`;
    try {
      const problem = await res.json();
      if (typeof problem?.code === "string") code = problem.code;
      if (typeof problem?.detail === "string") message = problem.detail;
    } catch {}
    throw new ApiError(res.status, code, message);
  }
  return res.json() as Promise<T>;
}

export const api = {
  cats: () => request<Cat[]>("/cats"),
  me: (catId: number) => request<Me>("/me", {}, catId),
  ledger: (catId: number) => request<LedgerLine[]>("/ledger", {}, catId),
  send: (
    catId: number,
    body: { toCatId: number; amount: number; note?: string },
    idempotencyKey: string,
  ) =>
    request<Transfer>(
      "/transfers",
      { method: "POST", body: JSON.stringify(body), headers: { "Idempotency-Key": idempotencyKey } },
      catId,
    ),
};
