import type { Metadata } from "next";
import Link from "next/link";
import { PresenceDashboard } from "@/features/presence/PresenceDashboard";

export const metadata: Metadata = {
  title: "Online Users | FITNet",
  description: "Student presence across the FITNet collaboration network.",
};

export default function PresencePage() {
  return (
    <div className="min-h-screen bg-zinc-50 font-sans text-zinc-950 dark:bg-black dark:text-zinc-50">
      <header className="border-b border-zinc-200 bg-white dark:border-zinc-800 dark:bg-zinc-950">
        <div className="mx-auto flex max-w-6xl flex-wrap items-center justify-between gap-3 px-5 py-5 sm:px-8">
          <Link href="/" className="text-xl font-bold tracking-tight focus-visible:outline-2 focus-visible:outline-offset-4">FIT<span className="text-emerald-700 dark:text-emerald-400">Net</span></Link>
          <span className="text-xs text-zinc-500 dark:text-zinc-400">Student collaboration network</span>
        </div>
      </header>
      <main className="mx-auto max-w-6xl px-5 py-10 sm:px-8 sm:py-14">
        <p className="text-xs font-semibold uppercase tracking-[0.18em] text-emerald-700 dark:text-emerald-400">UDP Presence · Member 02</p>
        <h1 className="mt-3 text-3xl font-semibold tracking-tight sm:text-4xl">Online users</h1>
        <p className="mt-3 max-w-xl text-sm leading-6 text-zinc-600 dark:text-zinc-400">See who is connected to FITNet, check their presence, and find when they were last seen.</p>
        <PresenceDashboard />
      </main>
    </div>
  );
}
