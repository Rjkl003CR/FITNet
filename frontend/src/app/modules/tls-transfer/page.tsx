import type { Metadata } from "next";
import { AppSidebar } from "@/components/layout/app-sidebar";
import { TopNavbar } from "@/components/layout/top-navbar";
import { FileTransferPanel } from "@/features/secure-transfer/FileTransferPanel";

export const metadata: Metadata = {
  title: "TLS Secure Transfer | FITNet",
  description: "Upload and download files using FITNet's verified TLS transfer service.",
};

export default function TlsTransferPage() {
  return (
    <main className="theme-bg flex min-h-screen">
      <AppSidebar />
      <section className="flex min-w-0 flex-1 flex-col">
        <TopNavbar title="TLS Secure Transfer" />
        <div className="min-h-0 flex-1 overflow-y-auto p-4 lg:p-6">
          <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm dark:border-slate-800 dark:bg-slate-900 sm:p-7">
            <p className="text-xs font-bold uppercase tracking-[0.14em] text-[#2563EB]">
              TLS File Transfer · Member 04
            </p>
            <h2 className="mt-2 text-2xl font-bold tracking-tight text-slate-900 dark:text-white">
              Secure File Transfer
            </h2>
            <p className="mt-2 max-w-2xl text-sm leading-6 text-slate-500 dark:text-slate-400">
              Upload, browse, and download files through the FITNet Java Gateway over an encrypted TLS socket with SHA-256 integrity verification.
            </p>
            <FileTransferPanel />
          </div>
        </div>
      </section>
    </main>
  );
}
