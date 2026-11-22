"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import { Activity, AlertCircle, RefreshCw, Search, Server, UsersRound, Wifi, WifiOff } from "lucide-react";
import { fetchPresenceUsers, type PresenceUser } from "./api";
import { PresenceList } from "./PresenceList";
import { filterPresenceUsers, getPresenceCounts, type PresenceFilter } from "./presence-utils";

const REFRESH_MS = 5000;
const TIMEOUT_MS = 8000;

export function PresenceDashboard() {
  const [users, setUsers] = useState<PresenceUser[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [updatedAt, setUpdatedAt] = useState<Date | null>(null);
  const [refreshing, setRefreshing] = useState(true);
  const [query, setQuery] = useState("");
  const [filter, setFilter] = useState<PresenceFilter>("ALL");
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

  const counts = useMemo(() => getPresenceCounts(users), [users]);
  const filteredUsers = useMemo(() => {
    if (!users) return [];
    return filterPresenceUsers(users, query, filter)
      .sort((a, b) => Number(b.status === "ONLINE") - Number(a.status === "ONLINE") || a.userId.localeCompare(b.userId));
  }, [filter, query, users]);
  const hasFilters = Boolean(query.trim()) || filter !== "ALL";
  const connection = users !== null ? (error ? "stale" : "connected") : (refreshing ? "connecting" : "unavailable");
  const connectionLabel = connection === "connected" ? "Gateway connected"
    : connection === "stale" ? "Last response retained"
    : connection === "connecting" ? "Connecting to Gateway"
    : "Gateway unavailable";
  const statCards = [
    { label: "Total users", value: users ? counts.total : "—", detail: "Registered by the UDP server", icon: UsersRound, tone: "text-[#2563EB]" },
    { label: "Online users", value: users ? counts.online : "—", detail: "Receiving heartbeats", icon: Wifi, tone: "text-emerald-600" },
    { label: "Offline users", value: users ? counts.offline : "—", detail: "Heartbeat timeout reached", icon: WifiOff, tone: "theme-text-secondary" },
  ];

  return (
    <div className="mt-7 space-y-6">
      <section className="theme-card theme-border rounded-2xl border p-5 sm:p-6">
        <div className="flex flex-col gap-5 lg:flex-row lg:items-center lg:justify-between">
          <div className="min-w-0">
            <div className="flex flex-wrap items-center gap-2.5">
              <span className={`inline-flex items-center gap-2 rounded-full px-2.5 py-1 text-[11px] font-bold ${connection === "connected" ? "bg-emerald-50 text-emerald-700 dark:bg-emerald-950/50 dark:text-emerald-300" : connection === "stale" ? "bg-amber-50 text-amber-700 dark:bg-amber-950/50 dark:text-amber-300" : "theme-subtle theme-text-secondary"}`}>
                <span aria-hidden="true" className={`size-1.5 rounded-full ${connection === "connected" ? "bg-emerald-500" : connection === "connecting" ? "animate-pulse bg-amber-400" : "bg-slate-400"}`} />
                {connectionLabel}
              </span>
              <span className="flex items-center gap-1.5 text-xs theme-text-secondary"><Server size={13} /> UDP :5002 via Gateway</span>
            </div>
            <p role="status" className="mt-3 text-xs theme-text-secondary">
              {updatedAt ? `Last successful update ${updatedAt.toLocaleTimeString(undefined, { timeStyle: "medium" })}` : "Waiting for the presence service"}
              {refreshing && users !== null ? " · Refreshing…" : ""}
            </p>
          </div>
          <button type="button" onClick={() => refresh.current?.()} disabled={refreshing}
            className="theme-primary inline-flex h-10 shrink-0 items-center justify-center gap-2 rounded-xl px-4 text-xs font-semibold shadow-sm transition disabled:cursor-wait disabled:opacity-60">
            <RefreshCw size={15} className={refreshing ? "animate-spin" : ""} />
            {refreshing ? "Refreshing…" : "Refresh"}
          </button>
        </div>
      </section>

      <div className="grid gap-4 sm:grid-cols-3">
        {statCards.map(({ label, value, detail, icon: Icon, tone }) => (
          <section key={label} className="theme-card theme-border rounded-2xl border p-5">
            <div className="flex items-center justify-between"><p className="text-xs font-medium theme-text-secondary">{label}</p><span className="theme-selected flex size-8 items-center justify-center rounded-lg"><Icon size={16} className={tone} /></span></div>
            <p className="mt-3 text-3xl font-bold tracking-tight theme-text">{value}</p>
            <p className="mt-1 text-xs theme-text-secondary">{detail}</p>
          </section>
        ))}
      </div>

      <section aria-labelledby="presence-directory" className="theme-card theme-border rounded-2xl border p-5 sm:p-6">
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div>
            <p className="text-xs font-bold uppercase tracking-[0.14em] text-[#2563EB]">Live user directory</p>
            <h2 id="presence-directory" className="mt-1.5 text-lg font-bold tracking-tight theme-text">Presence overview</h2>
            <p className="mt-1 text-xs theme-text-secondary">{users ? `${filteredUsers.length} of ${counts.total} users shown` : "User records appear after a successful Gateway response"}</p>
          </div>
        </div>

        {users && <div className="mt-5 flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
          <label className="theme-input theme-border flex h-10 min-w-0 items-center gap-2 rounded-xl border px-3 lg:w-80">
            <Search size={15} className="theme-text-muted shrink-0" aria-hidden="true" />
            <input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Search by user ID" aria-label="Search users by ID" className="min-w-0 flex-1 bg-transparent text-sm theme-text outline-none placeholder:theme-text-muted" />
          </label>
          <div className="theme-subtle inline-flex rounded-xl p-1" role="group" aria-label="Filter users by presence status">
            {(["ALL", "ONLINE", "OFFLINE"] as const).map((value) => (
              <button key={value} type="button" onClick={() => setFilter(value)} aria-pressed={filter === value}
                className={`rounded-lg px-3 py-1.5 text-xs font-semibold transition ${filter === value ? "theme-card theme-primary-text shadow-sm" : "theme-text-secondary hover:theme-text"}`}>
                {value === "ALL" ? "All" : value[0] + value.slice(1).toLowerCase()}
              </button>
            ))}
          </div>
        </div>}

        {error && <div role="alert" className="mt-5 flex items-start gap-3 rounded-xl border border-amber-200 bg-amber-50 p-4 text-amber-900 dark:border-amber-900/80 dark:bg-amber-950/40 dark:text-amber-200">
          <AlertCircle size={17} className="mt-0.5 shrink-0 text-amber-600 dark:text-amber-400" />
          <div><h3 className="text-sm font-bold">Presence service unavailable</h3><p className="mt-1 text-xs leading-5">{error} Retrying automatically. {users !== null ? "Showing the last successful response." : "No presence data has been received."}</p></div>
        </div>}

        <div className="mt-5">
          {users === null ? (
            !error ? <div role="status" className="theme-subtle theme-border flex flex-col items-center justify-center rounded-xl border px-5 py-14 text-center"><Activity size={22} className="animate-pulse text-[#2563EB]" /><p className="mt-3 text-sm font-semibold theme-text">Loading presence information…</p><p className="mt-1 text-xs theme-text-secondary">Requesting the latest Gateway snapshot.</p></div> : null
          ) : <PresenceList users={filteredUsers} hasFilters={hasFilters} />}
        </div>
      </section>
      <aside className="theme-text-secondary flex items-start gap-2 border-t theme-border pt-5 text-xs leading-5">
        <Activity size={15} className="mt-0.5 shrink-0 text-[#2563EB]" />
        <p>Presence is reported by the UDP server. Clients send a heartbeat every 3 seconds; users become offline after 10 seconds without one. The directory refreshes every 5 seconds after each request completes.</p>
      </aside>
    </div>
  );
}
