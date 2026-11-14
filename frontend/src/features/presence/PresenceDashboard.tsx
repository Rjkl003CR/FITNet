"use client";

import { useEffect, useRef, useState } from "react";
import { fetchPresenceUsers, type PresenceUser } from "./api";
import { PresenceList } from "./PresenceList";

const REFRESH_MS = 5000;
const TIMEOUT_MS = 8000;

export function PresenceDashboard() {
  const [users, setUsers] = useState<PresenceUser[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [updatedAt, setUpdatedAt] = useState<Date | null>(null);
  const [refreshing, setRefreshing] = useState(true);
  const refresh = useRef<(() => void) | null>(null);

  useEffect(() => {
    let disposed = false;
    let pending = false;
    let timer: ReturnType<typeof setTimeout> | undefined;
    let controller: AbortController | undefined;
    async function load() {
      if (disposed || pending) return;
      pending = true;
      clearTimeout(timer);
      setRefreshing(true);
      controller = new AbortController();
      const timeout = setTimeout(() => controller?.abort(), TIMEOUT_MS);
      try {
        const result = await fetchPresenceUsers(controller.signal);
        if (!disposed) {
          setUsers(result);
          setUpdatedAt(new Date());
          setError(null);
        }
      } catch {
        if (!disposed) setError("Presence information is unavailable. The Gateway may be offline or not connected yet.");
      } finally {
        clearTimeout(timeout);
        pending = false;
        if (!disposed) {
          setRefreshing(false);
          timer = setTimeout(load, REFRESH_MS);
        }
      }
    }
    refresh.current = () => { void load(); };
    void load();
    return () => {
      disposed = true;
      refresh.current = null;
      clearTimeout(timer);
      controller?.abort();
    };
  }, []);

  const online = users?.filter(user => user.status === "ONLINE").length;
  return (
    <>
      <div className="mt-8 grid gap-4 sm:grid-cols-3">
        {[
          { label: error ? "Online at last update" : "Online now", value: online ?? "—", accent: true },
          { label: "Registered users", value: users?.length ?? "—" },
          { label: "Refresh interval", value: "5 seconds" },
        ].map(stat => (
          <div key={stat.label} className="rounded-2xl border border-zinc-200 bg-white p-6 dark:border-zinc-800 dark:bg-zinc-900">
            <p className="text-sm text-zinc-500 dark:text-zinc-400">{stat.label}</p>
            <p className={`mt-3 text-3xl font-semibold tracking-tight ${stat.accent ? "text-emerald-700 dark:text-emerald-400" : ""}`}>{stat.value}</p>
          </div>
        ))}
      </div>

      <section aria-labelledby="presence-directory" className="mt-10">
        <div className="mb-5 flex flex-wrap items-center justify-between gap-4">
          <div>
            <h2 id="presence-directory" className="text-lg font-semibold">Presence directory</h2>
            <p role="status" className="mt-1 text-xs text-zinc-500 dark:text-zinc-400">
              {updatedAt ? `Last successful update: ${updatedAt.toLocaleTimeString()}` : "Waiting for the presence service"}
              {refreshing && users !== null ? " · Refreshing…" : ""}
            </p>
          </div>
          <button type="button" onClick={() => refresh.current?.()} disabled={refreshing}
            className="rounded-full border border-zinc-300 bg-white px-5 py-2.5 text-sm font-medium transition hover:bg-zinc-100 focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-emerald-600 disabled:cursor-wait disabled:opacity-50 dark:border-zinc-700 dark:bg-zinc-900 dark:hover:bg-zinc-800">
            {refreshing ? "Refreshing…" : "Refresh now"}
          </button>
        </div>

        {error && <div role="alert" className="mb-5 rounded-2xl border border-amber-200 bg-amber-50 p-5 text-amber-950 dark:border-amber-900 dark:bg-amber-950/40 dark:text-amber-200">
          <h3 className="text-sm font-semibold">Presence service unavailable</h3>
          <p className="mt-1 text-sm leading-6">{error}</p>
          <p className="mt-2 text-xs leading-5">Retrying automatically. {users !== null ? "The list and counts below show the last successful response, not current presence." : "No presence data has been received."}</p>
        </div>}

        {users === null ? (
          !error ? <div role="status" className="rounded-2xl border border-zinc-200 p-10 text-center text-sm text-zinc-500 dark:border-zinc-800 dark:text-zinc-400">Loading presence information…</div> : null
        ) : <PresenceList users={users} />}
      </section>
      <aside className="mt-8 border-t border-zinc-200 pt-6 text-xs leading-6 text-zinc-500 dark:border-zinc-800 dark:text-zinc-400">
        Presence is reported by the UDP server. Clients send a heartbeat every 3 seconds; users become offline after 10 seconds without one. This page refreshes the Gateway data every 5 seconds after each request completes.
      </aside>
    </>
  );
}
