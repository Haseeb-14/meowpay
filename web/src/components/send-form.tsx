import type { Cat } from "@/lib/api";
import type { SendFields, Status } from "@/hooks/use-wallet";
import { cardClass, inputClass, labelClass, selectClass } from "./styles";

type Props = {
  recipients: Cat[];
  fields: SendFields;
  status: Status;
  balance: number | null;
  amountValid: boolean;
  amountNumber: number;
  canSend: boolean;
  onChange: <K extends keyof SendFields>(key: K, value: SendFields[K]) => void;
  onSend: () => void;
};

export function SendForm({
  recipients,
  fields,
  status,
  balance,
  amountValid,
  amountNumber,
  canSend,
  onChange,
  onSend,
}: Props) {
  const overBalance = balance != null && amountValid && amountNumber > balance;
  const buttonLabel =
    status.kind === "sending"
      ? "Sending…"
      : status.kind === "error" && status.retryable
        ? "Retry"
        : "Send treats";

  return (
    <form
      onSubmit={(e) => {
        e.preventDefault();
        onSend();
      }}
      className={cardClass}
    >
      <h2 className="text-lg font-semibold">Send treats</h2>

      <label className="block text-sm">
        <span className={labelClass}>To</span>
        <select
          className={selectClass + " mt-1 w-full"}
          value={fields.toCatId}
          onChange={(e) => onChange("toCatId", e.target.value)}
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
          value={fields.amount}
          onChange={(e) => onChange("amount", e.target.value)}
          required
        />
        {overBalance && (
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
          value={fields.note}
          onChange={(e) => onChange("note", e.target.value)}
        />
      </label>

      <button
        type="submit"
        disabled={!canSend}
        className="w-full rounded-xl bg-zinc-900 px-4 py-3 font-medium text-white transition hover:bg-zinc-700 disabled:cursor-not-allowed disabled:opacity-40 dark:bg-amber-400 dark:text-amber-950 dark:hover:bg-amber-300"
      >
        {buttonLabel}
      </button>

      <StatusLine status={status} />
    </form>
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
