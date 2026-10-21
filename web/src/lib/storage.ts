/** Per-browser conveniences. Every read and write is guarded: storage may be absent or blocked. */

const CAT_KEY = "meowpay.catId";
const PENDING_KEY = "meowpay.pendingSend";

/** A send whose outcome we do not know yet. Survives a reload so Retry reuses the same key. */
export type PendingSend = {
  idempotencyKey: string;
  meId: number;
  toCatId: number;
  amount: number;
  note: string;
};

export function readSavedCat(): number | null {
  try {
    const raw = localStorage.getItem(CAT_KEY);
    return raw ? Number(raw) : null;
  } catch {
    return null;
  }
}

export function saveCat(id: number) {
  try {
    localStorage.setItem(CAT_KEY, String(id));
  } catch {
    /* storage unavailable */
  }
}

export function readPending(): PendingSend | null {
  try {
    const raw = sessionStorage.getItem(PENDING_KEY);
    return raw ? (JSON.parse(raw) as PendingSend) : null;
  } catch {
    return null;
  }
}

export function writePending(p: PendingSend | null) {
  try {
    if (p) sessionStorage.setItem(PENDING_KEY, JSON.stringify(p));
    else sessionStorage.removeItem(PENDING_KEY);
  } catch {
    /* storage unavailable */
  }
}
