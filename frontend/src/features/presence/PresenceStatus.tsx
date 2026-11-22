import type { PresenceState } from "./api";

export function PresenceStatus({ status }: { status: PresenceState }) {
  return (
    <span className={`inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-[11px] font-bold tracking-wide ${status === "ONLINE"
      ? "bg-emerald-50 text-emerald-700 dark:bg-emerald-950/50 dark:text-emerald-300"
      : "theme-subtle theme-text-secondary"}`}>
      <span aria-hidden="true" className={`size-1.5 rounded-full ${status === "ONLINE" ? "bg-emerald-500 shadow-[0_0_0_3px_rgba(16,185,129,0.15)]" : "bg-slate-400"}`} />
      {status}
    </span>
  );
}
