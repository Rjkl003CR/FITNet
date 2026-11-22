import type { PresenceUser } from "./api";
import { PresenceStatus } from "./PresenceStatus";
import { Clock3, UserRound } from "lucide-react";

export function PresenceUserCard({ user }: { user: PresenceUser }) {
  const seen = new Date(user.lastSeen);
  return (
    <li className="theme-card theme-border flex min-w-0 flex-wrap items-center gap-3 rounded-xl border p-4 transition hover:border-blue-300 dark:hover:border-blue-800 sm:flex-nowrap">
      <div aria-hidden="true" className="theme-selected theme-primary-text flex size-10 shrink-0 items-center justify-center rounded-xl font-mono text-xs font-bold uppercase">
        <UserRound size={17} />
      </div>
      <div className="min-w-0 flex-1">
        <p className="text-[10px] font-bold uppercase tracking-[0.12em] theme-text-muted">User ID</p>
        <h3 className="mt-0.5 truncate text-sm font-bold theme-text" title={user.userId}>{user.userId}</h3>
        <p className="mt-1 flex min-w-0 items-center gap-1.5 truncate text-xs theme-text-secondary">
          <Clock3 size={13} className="shrink-0" aria-hidden="true" />
          <span className="truncate">Last seen <time dateTime={user.lastSeen} title={seen.toISOString()}>{seen.toLocaleString(undefined, { dateStyle: "medium", timeStyle: "short" })}</time></span>
        </p>
      </div>
      <PresenceStatus status={user.status} />
    </li>
  );
}
