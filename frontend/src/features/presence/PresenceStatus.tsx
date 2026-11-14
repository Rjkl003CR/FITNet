import type { PresenceState } from "./api";

export function PresenceStatus({ status }: { status: PresenceState }) {
  return (
    <span className={`inline-flex items-center gap-2 rounded-full px-3 py-1 text-xs font-semibold tracking-wide ${status === "ONLINE"
      ? "bg-emerald-50 text-emerald-800 dark:bg-emerald-950 dark:text-emerald-300"
      : "bg-zinc-100 text-zinc-600 dark:bg-zinc-800 dark:text-zinc-300"}`}>
      <span aria-hidden="true" className={`size-1.5 rounded-full ${status === "ONLINE" ? "bg-emerald-500" : "bg-zinc-400"}`} />
      {status}
    </span>
  );
}
