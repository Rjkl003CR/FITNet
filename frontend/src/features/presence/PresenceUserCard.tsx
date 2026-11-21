import type { PresenceUser } from "./api";
import { PresenceStatus } from "./PresenceStatus";

export function PresenceUserCard({ user }: { user: PresenceUser }) {
  const seen = new Date(user.lastSeen);
  return (
    <li className="flex flex-wrap items-center gap-4 rounded-2xl border border-zinc-200 bg-white p-5 dark:border-zinc-800 dark:bg-zinc-900">
      <div aria-hidden="true" className="flex size-12 shrink-0 items-center justify-center rounded-xl bg-zinc-100 font-mono text-sm font-semibold uppercase text-zinc-600 dark:bg-zinc-800 dark:text-zinc-300">
        {user.userId.slice(0, 2)}
      </div>
      <div className="min-w-0 flex-1 basis-32">
        <h3 className="break-all text-base font-semibold text-zinc-950 dark:text-zinc-50">{user.userId}</h3>
        <p className="mt-1 text-xs leading-5 text-zinc-500 dark:text-zinc-400">
          Last seen <time dateTime={user.lastSeen} title={seen.toISOString()}>{seen.toLocaleString(undefined, { dateStyle: "medium", timeStyle: "medium" })}</time>
        </p>
      </div>
      <PresenceStatus status={user.status} />
    </li>
  );
}
