import type { Metadata } from "next";
import { PresenceDashboard } from "@/features/presence/PresenceDashboard";
import { AppSidebar } from "@/components/layout/app-sidebar";
import { TopNavbar } from "@/components/layout/top-navbar";

export const metadata: Metadata = {
  title: "UDP Presence | FITNet",
  description: "Student presence across the FITNet collaboration network.",
};

export default function PresencePage() {
  return (
    <main className="theme-bg flex min-h-screen">
      <AppSidebar />
      <section className="flex min-w-0 flex-1 flex-col">
        <TopNavbar title="UDP Presence" />
        <div className="min-h-0 flex-1 overflow-y-auto p-4 lg:p-6">
          <div className="theme-card theme-border rounded-2xl border p-5 shadow-sm sm:p-7">
            <p className="text-xs font-bold uppercase tracking-[0.14em] text-[#2563EB]">Network monitoring · Member 02</p>
            <h2 className="mt-2 text-2xl font-bold tracking-tight theme-text">UDP Presence</h2>
            <p className="mt-2 max-w-xl text-sm leading-6 theme-text-secondary">Real-time network user presence monitoring.</p>
            <PresenceDashboard />
          </div>
        </div>
      </section>
    </main>
  );
}
