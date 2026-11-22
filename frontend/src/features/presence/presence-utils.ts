import type { PresenceState, PresenceUser } from "./api";

export type PresenceFilter = "ALL" | PresenceState;

export function getPresenceCounts(users: PresenceUser[] | null) {
  const total = users?.length ?? 0;
  const online = users?.filter((user) => user.status === "ONLINE").length ?? 0;
  return { total, online, offline: total - online };
}

export function filterPresenceUsers(users: PresenceUser[], query: string, filter: PresenceFilter) {
  const term = query.trim().toLowerCase();
  return users.filter((user) =>
    (filter === "ALL" || user.status === filter)
    && (!term || user.userId.toLowerCase().includes(term))
  );
}
