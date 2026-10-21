import type { LedgerLine } from "@/lib/api";
import { cardClass } from "./styles";

function labelFor(line: LedgerLine): string {
  const other = line.counterparty?.name ?? "a cat";
  switch (line.kind) {
    case "TOPUP":
      return "Top-up";
    case "TRANSFER_OUT":
      return `To ${other}`;
    case "TRANSFER_IN":
      return `From ${other}`;
  }
}

export function ActivityList({ ledger }: { ledger: LedgerLine[] }) {
  return (
    <section className={cardClass}>
      <h2 className="text-lg font-semibold">Activity</h2>
      {ledger.length === 0 ? (
        <p className="text-sm text-zinc-500 dark:text-zinc-400">No treats have moved yet.</p>
      ) : (
        <ul className="divide-y divide-zinc-200 dark:divide-zinc-800">
          {ledger.map((line) => {
            const incoming = line.amount > 0;
            return (
              <li key={line.id} className="flex items-start justify-between gap-4 py-3 text-sm">
                <div>
                  <p className="font-medium">{labelFor(line)}</p>
                  {line.note && <p className="text-zinc-500 dark:text-zinc-400">{line.note}</p>}
                  <p className="text-xs text-zinc-400 dark:text-zinc-500">
                    {new Date(line.createdAt).toLocaleString()}
                  </p>
                </div>
                <div className="shrink-0 text-right">
                  <p
                    className={
                      "font-semibold tabular-nums " +
                      (incoming ? "text-emerald-600 dark:text-emerald-400" : "text-zinc-700 dark:text-zinc-300")
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
  );
}
