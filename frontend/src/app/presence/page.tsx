import type { Metadata } from "next";
import { PresenceDashboard } from "@/features/presence/PresenceDashboard";
import { AppSidebar } from "@/components/layout/app-sidebar";
import { TopNavbar } from "@/components/layout/top-navbar";

export const metadata: Metadata = {
  title: "Online Users | FITNet",
  description: "Student presence across the FITNet collaboration network.",
};

export default function PresencePage() {
  return (
    <main className="theme-bg flex min-h-screen">
      <AppSidebar />
      <section className="flex min-w-0 flex-1 flex-col">
        <TopNavbar title="UDP Presence" />
        <div className="min-h-0 flex-1 overflow-y-auto p-4 lg:p-6">
          <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm dark:border-slate-800 dark:bg-slate-900 sm:p-7">
            <p className="text-xs font-bold uppercase tracking-[0.14em] text-[#2563EB]">Live directory</p>
            <h2 className="mt-2 text-2xl font-bold tracking-tight text-slate-900 dark:text-white">Online users</h2>
            <p className="mt-2 max-w-xl text-sm leading-6 text-slate-500 dark:text-slate-400">See who is connected through the FITNet Gateway presence service.</p>
            <PresenceDashboard />
          </div>
        </div>
      </section>
    </main>
  );
}
