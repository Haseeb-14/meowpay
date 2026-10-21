import type { Me } from "@/lib/api";

export function BalanceCard({ me }: { me: Me | null }) {
  return (
    <section className="rounded-2xl bg-amber-400 p-6 text-amber-950 shadow-sm">
      <p className="text-sm font-medium uppercase tracking-wide opacity-70">Balance</p>
      <p className="mt-1 text-5xl font-semibold tabular-nums">
        {me ? me.balance.toLocaleString() : "…"}
        <span className="ml-2 text-xl font-normal opacity-70">treats</span>
      </p>
      <p className="mt-2 text-sm opacity-80">{me ? me.name : "Loading"}</p>
    </section>
  );
}
