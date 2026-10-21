"use client";

import { useWallet } from "@/hooks/use-wallet";
import { ActivityList } from "./activity-list";
import { BalanceCard } from "./balance-card";
import { SendForm } from "./send-form";
import { selectClass } from "./styles";

export function Wallet() {
  const w = useWallet();

  if (w.loadError) {
    return (
      <Shell>
        <p className="rounded-xl border border-red-300 bg-red-50 p-4 text-red-800 dark:border-red-900 dark:bg-red-950 dark:text-red-200">
          {w.loadError}
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
            value={w.meId ?? ""}
            onChange={(e) => w.selectCat(Number(e.target.value))}
            disabled={w.cats.length === 0}
          >
            {w.cats.map((c) => (
              <option key={c.id} value={c.id}>
                {c.name}
              </option>
            ))}
          </select>
        </label>
      </header>

      <BalanceCard me={w.me} />

      <SendForm
        recipients={w.cats.filter((c) => c.id !== w.meId)}
        fields={w.fields}
        status={w.status}
        balance={w.me?.balance ?? null}
        amountValid={w.amountValid}
        amountNumber={w.amountNumber}
        canSend={w.canSend}
        onChange={w.updateField}
        onSend={w.send}
      />

      <ActivityList ledger={w.ledger} />
    </Shell>
  );
}

function Shell({ children }: { children: React.ReactNode }) {
  return (
    <main className="mx-auto flex w-full max-w-xl flex-1 flex-col gap-6 px-4 py-8 sm:py-12">
      {children}
    </main>
  );
}
