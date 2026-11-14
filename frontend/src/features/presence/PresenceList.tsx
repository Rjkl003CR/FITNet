import type { PresenceUser } from "./api";
import { PresenceUserCard } from "./PresenceUserCard";

export function PresenceList({ users }: { users: PresenceUser[] }) {
  if (!users.length) return (
    <div className="rounded-2xl border border-dashed border-zinc-300 px-6 py-16 text-center dark:border-zinc-700">
      <h3 className="font-semibold">No users registered yet</h3>
      <p className="mx-auto mt-2 max-w-md text-sm leading-6 text-zinc-500 dark:text-zinc-400">Users appear here when the server receives their first UDP heartbeat.</p>
    </div>
  );
  const sorted = [...users].sort((a, b) => Number(b.status === "ONLINE") - Number(a.status === "ONLINE") || a.userId.localeCompare(b.userId));
  return <ul aria-label="User presence" className="grid gap-3 lg:grid-cols-2">{sorted.map(user => <PresenceUserCard key={user.userId} user={user} />)}</ul>;
}
