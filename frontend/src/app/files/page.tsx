import type { Metadata } from "next";
import Link from "next/link";
import { FileTransferPanel } from "@/features/secure-transfer/FileTransferPanel";

export const metadata: Metadata = {
  title: "Secure File Transfer | FITNet",
  description: "Upload and download files using FITNet's verified TLS transfer service.",
};

export default function FilesPage() {
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
            <Link
              href="/"
              className="rounded-full px-3.5 py-1.5 text-slate-600 transition hover:bg-slate-100 hover:text-slate-950 dark:text-slate-300 dark:hover:bg-slate-800 dark:hover:text-white"
            >
              Overview
            </Link>
            <span
              aria-current="page"
              className="rounded-full bg-emerald-600 px-3.5 py-1.5 text-white shadow-sm shadow-emerald-600/20 dark:bg-emerald-500 dark:text-slate-950"
            >
              Secure Files
            </span>
            <Link
              href="/presence"
              className="rounded-full px-3.5 py-1.5 text-slate-600 transition hover:bg-slate-100 hover:text-slate-950 dark:text-slate-300 dark:hover:bg-slate-800 dark:hover:text-white"
            >
              Online Users
            </Link>
          </nav>
        </div>
      </header>

      <main className="mx-auto w-full max-w-6xl flex-1 px-5 py-10 sm:px-8 sm:py-12">
        <div className="mb-8 flex flex-wrap items-end justify-between gap-4">
          <div className="max-w-2xl">
            <div className="inline-flex items-center gap-2 rounded-full border border-emerald-200 bg-emerald-50 px-3 py-1 text-xs font-semibold text-emerald-800 dark:border-emerald-900 dark:bg-emerald-950/70 dark:text-emerald-300">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" className="size-3.5" aria-hidden="true">
                <rect width="18" height="11" x="3" y="11" rx="2" />
                <path strokeLinecap="round" strokeLinejoin="round" d="M7 11V7a5 5 0 0 1 10 0v4" />
              </svg>
              TLS File Transfer · Member 04
            </div>
            <h1 className="mt-3 text-3xl font-bold tracking-tight text-slate-950 sm:text-4xl dark:text-white">
              Share files securely
            </h1>
            <p className="mt-2 text-sm leading-6 text-slate-600 sm:text-base dark:text-slate-400">
              Files travel through the local Java Gateway over an encrypted <code className="rounded bg-slate-100 px-1.5 py-0.5 font-mono text-xs text-slate-800 dark:bg-slate-800 dark:text-slate-200">SSLSocket</code> and are verified with SHA-256 on every upload and download.
            </p>
          </div>
          <Link
            href="/"
            className="inline-flex items-center gap-1.5 rounded-full border border-slate-200 bg-white px-4 py-2 text-xs font-semibold text-slate-700 transition hover:border-slate-300 hover:bg-slate-50 dark:border-slate-800 dark:bg-slate-900 dark:text-slate-300 dark:hover:bg-slate-800"
          >
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" className="size-3.5" aria-hidden="true">
              <path strokeLinecap="round" strokeLinejoin="round" d="M19 12H5m6-6-6 6 6 6" />
            </svg>
            Back to overview
          </Link>
        </div>

        <FileTransferPanel />
      </main>

      <footer className="border-t border-slate-200/70 py-6 text-center text-xs text-slate-500 dark:border-slate-800/70 dark:text-slate-400">
        FITNet Secure File Transfer · Browser → Gateway (:8080) → TLS Server (:5004) · SHA-256 Verified
      </footer>
    </div>
  );
}
