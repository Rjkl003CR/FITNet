import type { PresenceUser } from "./api";
import { PresenceUserCard } from "./PresenceUserCard";
import { UsersRound } from "lucide-react";

interface PresenceListProps {
  users: PresenceUser[];
  hasFilters?: boolean;
}

export function PresenceList({ users, hasFilters = false }: PresenceListProps) {
  if (!users.length) return (
    <div className="theme-subtle theme-border rounded-xl border border-dashed px-6 py-14 text-center">
      <span className="theme-card theme-border mx-auto flex size-11 items-center justify-center rounded-xl border theme-primary-text"><UsersRound size={20} /></span>
      <h3 className="mt-3 text-sm font-bold theme-text">{hasFilters ? "No users match these filters" : "No users registered yet"}</h3>
      <p className="mx-auto mt-1.5 max-w-md text-xs leading-5 theme-text-secondary">{hasFilters ? "Try a different search term or status filter." : "Users appear here when the server receives their first UDP heartbeat."}</p>
    </div>
  );
  const sorted = [...users].sort((a, b) => Number(b.status === "ONLINE") - Number(a.status === "ONLINE") || a.userId.localeCompare(b.userId));
  return <ul aria-label="User presence" className="grid gap-2.5 xl:grid-cols-2">{sorted.map((user) => <PresenceUserCard key={user.userId} user={user} />)}</ul>;
}
