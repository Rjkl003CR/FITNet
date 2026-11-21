import type { Metadata } from "next";
import Link from "next/link";

export const metadata: Metadata = {
  title: "FITNet | Network Collaboration Platform",
  description: "Messaging, presence, announcements, secure file transfer, and remote resources.",
};

const PLATFORM_SERVICES = [
  { name: "TCP Messaging", tech: "ServerSocket / Socket", port: ":5001", active: false },
  { name: "UDP Presence", tech: "DatagramSocket", port: ":5002", active: true, href: "/presence" },
  { name: "Multicast Announcements", tech: "MulticastSocket", port: ":5003", active: false },
  { name: "Secure File Transfer", tech: "SSLServerSocket / TLS", port: ":5004", active: true, href: "/files" },
  { name: "Remote Resources", tech: "Java RMI & URL", port: ":1099", active: false },
];

export default function Home() {
  return (
    <div className="flex min-h-screen flex-col font-sans text-slate-900 dark:text-slate-100">
      <header className="sticky top-0 z-20 border-b border-slate-200/80 bg-white/80 backdrop-blur-md dark:border-slate-800/80 dark:bg-slate-950/80">
        <div className="mx-auto flex max-w-6xl flex-wrap items-center justify-between gap-4 px-5 py-4 sm:px-8">
          <Link href="/" className="flex items-center gap-2.5 focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-emerald-600">
            <span className="grid size-9 place-items-center rounded-xl bg-gradient-to-br from-emerald-600 to-teal-700 text-white shadow-sm shadow-emerald-600/20">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" className="size-5" aria-hidden="true">
                <path strokeLinecap="round" strokeLinejoin="round" d="M12 3 4.5 6.5v5.5c0 4.6 3.2 8.8 7.5 9.8 4.3-1 7.5-5.2 7.5-9.8V6.5L12 3Z" />
                <path strokeLinecap="round" strokeLinejoin="round" d="M9.5 12.25 11.25 14l3.5-4" />
              </svg>
            </span>
            <span className="text-xl font-bold tracking-tight">
              FIT<span className="text-emerald-600 dark:text-emerald-400">Net</span>
            </span>
          </Link>

          <nav aria-label="Main navigation" className="flex items-center gap-1.5 text-sm font-medium">
            <span
              aria-current="page"
              className="rounded-full bg-slate-900 px-3.5 py-1.5 text-white dark:bg-white dark:text-slate-950"
            >
              Overview
            </span>
            <Link
              href="/files"
              className="rounded-full px-3.5 py-1.5 text-slate-600 transition hover:bg-slate-100 hover:text-slate-950 dark:text-slate-300 dark:hover:bg-slate-800 dark:hover:text-white"
            >
              Secure Files
            </Link>
            <Link
              href="/presence"
              className="rounded-full px-3.5 py-1.5 text-slate-600 transition hover:bg-slate-100 hover:text-slate-950 dark:text-slate-300 dark:hover:bg-slate-800 dark:hover:text-white"
            >
              Online Users
            </Link>
          </nav>
        </div>
      </header>

      <main className="mx-auto w-full max-w-6xl flex-1 px-5 py-12 sm:px-8 sm:py-16">
        <section className="relative overflow-hidden rounded-3xl border border-slate-200/90 bg-white/90 p-8 shadow-sm shadow-slate-900/5 sm:p-12 dark:border-slate-800 dark:bg-slate-900/70">
          <div className="pointer-events-none absolute -top-24 -right-24 size-72 rounded-full bg-emerald-500/10 blur-3xl dark:bg-emerald-400/10" />
          <div className="relative max-w-3xl">
            <div className="inline-flex items-center gap-2 rounded-full border border-emerald-200 bg-emerald-50 px-3.5 py-1 text-xs font-semibold text-emerald-800 dark:border-emerald-900 dark:bg-emerald-950/70 dark:text-emerald-300">
              <span className="size-2 rounded-full bg-emerald-500" aria-hidden="true" />
              Network Collaboration Platform · Java Socket Suite
            </div>
            <h1 className="mt-5 text-4xl font-bold tracking-tight text-slate-950 sm:text-5xl lg:text-6xl dark:text-white">
              One workspace for your{" "}
              <span className="bg-gradient-to-r from-emerald-600 to-teal-600 bg-clip-text text-transparent dark:from-emerald-400 dark:to-teal-300">
                network services
              </span>
              .
            </h1>
            <p className="mt-5 max-w-2xl text-base leading-7 text-slate-600 sm:text-lg dark:text-slate-300">
              Connect through the FITNet Java Gateway to share files over encrypted TLS sockets, verify binary integrity with SHA-256, and monitor live user presence across the network.
            </p>
            <div className="mt-8 flex flex-wrap items-center gap-3">
              <Link
                href="/files"
                className="inline-flex h-12 items-center gap-2 rounded-full bg-emerald-600 px-6 text-sm font-semibold text-white shadow-sm shadow-emerald-600/25 transition hover:bg-emerald-700 focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-emerald-600 dark:bg-emerald-500 dark:text-slate-950 dark:hover:bg-emerald-400"
              >
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" className="size-4" aria-hidden="true">
                  <path strokeLinecap="round" strokeLinejoin="round" d="M12 16V4m0 0-4 4m4-4 4 4M4 17v1a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-1" />
                </svg>
                Open Secure File Transfer
              </Link>
              <Link
                href="/presence"
                className="inline-flex h-12 items-center gap-2 rounded-full border border-slate-300 bg-white px-6 text-sm font-semibold text-slate-800 transition hover:border-slate-400 hover:bg-slate-50 focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-emerald-600 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-100 dark:hover:bg-slate-800"
              >
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" className="size-4" aria-hidden="true">
                  <path strokeLinecap="round" strokeLinejoin="round" d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2m22 0v-2a4 4 0 0 0-3-3.87M16 3.13a4 4 0 0 1 0 7.75M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8Z" />
                </svg>
                View Online Users
              </Link>
            </div>
          </div>

          <div className="relative mt-10 grid gap-3 border-t border-slate-100 pt-7 sm:grid-cols-3 dark:border-slate-800">
            <div className="flex items-center gap-3 rounded-2xl bg-slate-50/90 px-4 py-3 dark:bg-slate-950/60">
              <span className="grid size-9 shrink-0 place-items-center rounded-xl bg-emerald-100 text-emerald-700 dark:bg-emerald-950 dark:text-emerald-300">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" className="size-4.5" aria-hidden="true">
                  <rect width="18" height="11" x="3" y="11" rx="2" />
                  <path strokeLinecap="round" strokeLinejoin="round" d="M7 11V7a5 5 0 0 1 10 0v4" />
                </svg>
              </span>
              <div>
                <p className="text-xs font-semibold text-slate-900 dark:text-white">TLS 1.2 / 1.3 Encrypted</p>
                <p className="text-xs text-slate-500 dark:text-slate-400">SSLServerSocket on port 5004</p>
              </div>
            </div>
            <div className="flex items-center gap-3 rounded-2xl bg-slate-50/90 px-4 py-3 dark:bg-slate-950/60">
              <span className="grid size-9 shrink-0 place-items-center rounded-xl bg-sky-100 text-sky-700 dark:bg-sky-950 dark:text-sky-300">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" className="size-4.5" aria-hidden="true">
                  <path strokeLinecap="round" strokeLinejoin="round" d="M9 12.75 11.25 15 15 9.75M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0Z" />
                </svg>
              </span>
              <div>
                <p className="text-xs font-semibold text-slate-900 dark:text-white">SHA-256 Verified</p>
                <p className="text-xs text-slate-500 dark:text-slate-400">Integrity checked on upload & download</p>
              </div>
            </div>
            <div className="flex items-center gap-3 rounded-2xl bg-slate-50/90 px-4 py-3 dark:bg-slate-950/60">
              <span className="grid size-9 shrink-0 place-items-center rounded-xl bg-violet-100 text-violet-700 dark:bg-violet-950 dark:text-violet-300">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" className="size-4.5" aria-hidden="true">
                  <path strokeLinecap="round" strokeLinejoin="round" d="M5 12h14M12 5l7 7-7 7" />
                </svg>
              </span>
              <div>
                <p className="text-xs font-semibold text-slate-900 dark:text-white">Java Gateway Bridge</p>
                <p className="text-xs text-slate-500 dark:text-slate-400">HTTP :8080 to native Java sockets</p>
              </div>
            </div>
          </div>
        </section>

        <section aria-labelledby="active-modules-heading" className="mt-10">
          <div className="mb-5 flex items-end justify-between gap-4">
            <div>
              <h2 id="active-modules-heading" className="text-lg font-semibold tracking-tight text-slate-950 dark:text-white">
                Available modules
              </h2>
              <p className="mt-1 text-sm text-slate-500 dark:text-slate-400">
                Launch a live service connected through the local FITNet Gateway.
              </p>
            </div>
          </div>

          <div className="grid gap-5 md:grid-cols-2">
            <Link
              href="/files"
              className="group relative flex flex-col justify-between rounded-3xl border border-slate-200/90 bg-white p-7 shadow-sm transition duration-200 hover:-translate-y-1 hover:border-emerald-400 hover:shadow-lg hover:shadow-emerald-950/5 focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-emerald-600 dark:border-slate-800 dark:bg-slate-900/80 dark:hover:border-emerald-500/60"
            >
              <div>
                <div className="flex items-start justify-between gap-4">
                  <span className="grid size-12 place-items-center rounded-2xl bg-gradient-to-br from-emerald-50 to-teal-100 text-emerald-700 ring-1 ring-emerald-200/70 transition group-hover:scale-105 dark:from-emerald-950 dark:to-teal-950 dark:text-emerald-300 dark:ring-emerald-800">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" className="size-6" aria-hidden="true">
                      <path strokeLinecap="round" strokeLinejoin="round" d="M12 3 4.5 6.5v5.5c0 4.6 3.2 8.8 7.5 9.8 4.3-1 7.5-5.2 7.5-9.8V6.5L12 3Z" />
                      <path strokeLinecap="round" strokeLinejoin="round" d="M12 8.5v6m0-6-2.5 2.5M12 8.5l2.5 2.5" />
                    </svg>
                  </span>
                  <span className="inline-flex items-center gap-1.5 rounded-full bg-emerald-50 px-3 py-1 text-xs font-semibold text-emerald-700 ring-1 ring-emerald-200/80 dark:bg-emerald-950/70 dark:text-emerald-300 dark:ring-emerald-800/80">
                    <span className="size-1.5 rounded-full bg-emerald-500" aria-hidden="true" />
                    Member 04 · TLS :5004
                  </span>
                </div>

                <h3 className="mt-6 text-xl font-semibold tracking-tight text-slate-950 dark:text-white">
                  Secure File Transfer
                </h3>
                <p className="mt-2 text-sm leading-6 text-slate-600 dark:text-slate-400">
                  Upload, browse, and download arbitrary binary files over an authenticated TLS socket with end-to-end SHA-256 verification and atomic storage commits.
                </p>

                <div className="mt-5 flex flex-wrap gap-2">
                  {["SSLSocket / TLS", "SHA-256", "Binary Streaming", "V1 & V2"].map((tag) => (
                    <span
                      key={tag}
                      className="rounded-lg bg-slate-100 px-2.5 py-1 text-xs font-medium text-slate-600 dark:bg-slate-800 dark:text-slate-300"
                    >
                      {tag}
                    </span>
                  ))}
                </div>
              </div>

              <div className="mt-7 flex items-center justify-between border-t border-slate-100 pt-5 text-sm font-semibold text-emerald-700 dark:border-slate-800 dark:text-emerald-400">
                <span>Open file sharing</span>
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" className="size-4 transition-transform group-hover:translate-x-1" aria-hidden="true">
                  <path strokeLinecap="round" strokeLinejoin="round" d="M5 12h14m-6-6 6 6-6 6" />
                </svg>
              </div>
            </Link>

            <Link
              href="/presence"
              className="group relative flex flex-col justify-between rounded-3xl border border-slate-200/90 bg-white p-7 shadow-sm transition duration-200 hover:-translate-y-1 hover:border-sky-400 hover:shadow-lg hover:shadow-sky-950/5 focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-emerald-600 dark:border-slate-800 dark:bg-slate-900/80 dark:hover:border-sky-500/60"
            >
              <div>
                <div className="flex items-start justify-between gap-4">
                  <span className="grid size-12 place-items-center rounded-2xl bg-gradient-to-br from-sky-50 to-indigo-100 text-sky-700 ring-1 ring-sky-200/70 transition group-hover:scale-105 dark:from-sky-950 dark:to-indigo-950 dark:text-sky-300 dark:ring-sky-800">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" className="size-6" aria-hidden="true">
                      <path strokeLinecap="round" strokeLinejoin="round" d="M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18Z" />
                      <path strokeLinecap="round" strokeLinejoin="round" d="M12 16a4 4 0 1 0 0-8 4 4 0 0 0 0 8Z" />
                      <circle cx="12" cy="12" r="1.25" fill="currentColor" />
                    </svg>
                  </span>
                  <span className="inline-flex items-center gap-1.5 rounded-full bg-sky-50 px-3 py-1 text-xs font-semibold text-sky-700 ring-1 ring-sky-200/80 dark:bg-sky-950/70 dark:text-sky-300 dark:ring-sky-800/80">
                    <span className="size-1.5 rounded-full bg-sky-500" aria-hidden="true" />
                    Member 02 · UDP :5002
                  </span>
                </div>

                <h3 className="mt-6 text-xl font-semibold tracking-tight text-slate-950 dark:text-white">
                  Online Users &amp; Presence
                </h3>
                <p className="mt-2 text-sm leading-6 text-slate-600 dark:text-slate-400">
                  Discover connected teammates in real time via UDP heartbeat datagrams, live status indicators, and automatic inactivity detection.
                </p>

                <div className="mt-5 flex flex-wrap gap-2">
                  {["DatagramSocket", "UDP Heartbeats", "Auto Refresh"].map((tag) => (
                    <span
                      key={tag}
                      className="rounded-lg bg-slate-100 px-2.5 py-1 text-xs font-medium text-slate-600 dark:bg-slate-800 dark:text-slate-300"
                    >
                      {tag}
                    </span>
                  ))}
                </div>
              </div>

              <div className="mt-7 flex items-center justify-between border-t border-slate-100 pt-5 text-sm font-semibold text-sky-700 dark:border-slate-800 dark:text-sky-400">
                <span>View presence directory</span>
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" className="size-4 transition-transform group-hover:translate-x-1" aria-hidden="true">
                  <path strokeLinecap="round" strokeLinejoin="round" d="M5 12h14m-6-6 6 6-6 6" />
                </svg>
              </div>
            </Link>
          </div>
        </section>

        <section aria-labelledby="architecture-heading" className="mt-10 rounded-3xl border border-slate-200/80 bg-white/80 p-6 sm:p-7 dark:border-slate-800 dark:bg-slate-900/60">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <h2 id="architecture-heading" className="text-sm font-semibold uppercase tracking-[0.14em] text-slate-500 dark:text-slate-400">
              FITNet Backend Service Map
            </h2>
            <span className="font-mono text-xs text-slate-500 dark:text-slate-400">Gateway HTTP :8080</span>
          </div>
          <div className="mt-4 grid gap-3 sm:grid-cols-2 lg:grid-cols-5">
            {PLATFORM_SERVICES.map((service) => {
              const cardContent = (
                <div className="flex h-full flex-col justify-between rounded-2xl border border-slate-200/80 bg-slate-50/70 p-4 transition dark:border-slate-800 dark:bg-slate-950/60">
                  <div className="flex items-center justify-between gap-2">
                    <span className="font-mono text-xs font-semibold text-slate-500 dark:text-slate-400">{service.port}</span>
                    <span
                      className={`rounded-full px-2 py-0.5 text-[11px] font-semibold ${
                        service.active
                          ? "bg-emerald-100 text-emerald-800 dark:bg-emerald-950 dark:text-emerald-300"
                          : "bg-slate-200/80 text-slate-600 dark:bg-slate-800 dark:text-slate-400"
                      }`}
                    >
                      {service.active ? "Ready" : "Team module"}
                    </span>
                  </div>
                  <div className="mt-3">
                    <p className="text-sm font-semibold text-slate-900 dark:text-white">{service.name}</p>
                    <p className="mt-0.5 text-xs text-slate-500 dark:text-slate-400">{service.tech}</p>
                  </div>
                </div>
              );
              return service.href ? (
                <Link key={service.name} href={service.href} className="block rounded-2xl transition hover:-translate-y-0.5 hover:ring-2 hover:ring-emerald-400/60">
                  {cardContent}
                </Link>
              ) : (
                <div key={service.name}>{cardContent}</div>
              );
            })}
          </div>
        </section>
      </main>

      <footer className="border-t border-slate-200/70 py-6 text-center text-xs text-slate-500 dark:border-slate-800/70 dark:text-slate-400">
        FITNet · Network Collaboration Platform — Next.js Frontend · Java Gateway · Native Java Networking Modules
      </footer>
    </div>
  );
}
