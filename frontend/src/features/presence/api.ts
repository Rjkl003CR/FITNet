export type PresenceState = "ONLINE" | "OFFLINE";

export interface PresenceUser {
  userId: string;
  status: PresenceState;
  lastSeen: string;
}

/** Validate the Gateway boundary rather than trusting a TypeScript cast. */
export function parsePresenceUsers(value: unknown): PresenceUser[] {
  if (!Array.isArray(value)) throw new Error("The presence service returned an invalid response.");
  const ids = new Set<string>();
  return value.map((item: unknown) => {
    if (!item || typeof item !== "object") throw new Error("Invalid presence record.");
    const user = item as Record<string, unknown>;
    if (typeof user.userId !== "string" || !/^[A-Za-z0-9_.@-]{1,64}$/.test(user.userId)
      || (user.status !== "ONLINE" && user.status !== "OFFLINE")
      || typeof user.lastSeen !== "string"
      || !/^\d{4}-\d{2}-\d{2}T.*(?:Z|[+-]\d{2}:\d{2})$/.test(user.lastSeen)
      || !Number.isFinite(Date.parse(user.lastSeen)) || ids.has(user.userId)) {
      throw new Error("The presence service returned an invalid user record.");
    }
    ids.add(user.userId);
    return { userId: user.userId, status: user.status, lastSeen: user.lastSeen };
  });
}

export async function fetchPresenceUsers(signal?: AbortSignal): Promise<PresenceUser[]> {
  const base = (process.env.NEXT_PUBLIC_PRESENCE_API_BASE_URL ?? "").replace(/\/$/, "");
  const response = await fetch(`${base}/api/presence/users`, {
    signal, cache: "no-store", headers: { Accept: "application/json" },
  });
  if (!response.ok) throw new Error(`Presence service unavailable (HTTP ${response.status}).`);
  if (!response.headers.get("content-type")?.includes("application/json")) {
    throw new Error("The presence service did not return JSON.");
  }
  return parsePresenceUsers(await response.json());
}
