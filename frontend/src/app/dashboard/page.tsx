import { AppSidebar } from "@/components/layout/app-sidebar";
import { TopNavbar } from "@/components/layout/top-navbar";
import { Activity, MessageSquare, Radio, Server, Shield, UserRound, Wifi } from "lucide-react";
import Link from "next/link";

const modules = [
  { name: "TCP Messaging", href: "/", description: "Reliable Java Socket communication", icon: MessageSquare, status: "Available" },
  { name: "UDP Presence", href: "/presence", description: "Heartbeat-based online status", icon: Wifi, status: "Gateway-backed" },
  { name: "Multicast Announcements", href: "/modules/multicast", description: "UDP multicast concept and implementation status", icon: Radio, status: "Module page" },
  { name: "TLS Secure Transfer", href: "/modules/tls-transfer", description: "Secure file-transfer interface", icon: Shield, status: "Backend status checked" },
  { name: "Remote Resources", href: "/modules/remote-resources", description: "Java RMI and URL connection concepts", icon: Server, status: "Module page" },
] as const;

export default function DashboardPage() {
  return (
    <main className="theme-bg flex min-h-screen">
      <AppSidebar />
      <section className="flex min-w-0 flex-1 flex-col">
        <TopNavbar title="Dashboard" />
        <div className="min-h-0 flex-1 overflow-y-auto p-4 lg:p-6">
          <div className="rounded-2xl border border-slate-200 bg-white p-5 dark:border-slate-800 dark:bg-slate-900 sm:p-6">
            <p className="text-xs font-bold uppercase tracking-[0.14em] text-[#2563EB]">Student workspace</p>
            <h1 className="mt-2 text-2xl font-bold text-slate-900 dark:text-white">Welcome, Kasun</h1>
            <p className="mt-2 text-sm text-slate-500 dark:text-slate-400">University of Moratuwa · Faculty of Information Technology</p>
          </div>

          <div className="mt-5 grid gap-5 xl:grid-cols-[minmax(0,1fr)_320px]">
            <section>
              <div className="mb-3 flex items-center justify-between"><h2 className="text-sm font-bold text-slate-900 dark:text-white">Networking modules</h2><span className="text-xs text-slate-400">5 modules</span></div>
              <div className="grid gap-3 sm:grid-cols-2">
                {modules.map(({ name, href, description, icon: Icon, status }) => (
                  <Link key={name} href={href} className="group flex min-h-32 flex-col rounded-2xl border border-slate-200 bg-white p-5 transition hover:border-blue-200 hover:shadow-sm dark:border-slate-800 dark:bg-slate-900 dark:hover:border-blue-900">
                    <div className="flex items-start justify-between"><div className="flex h-9 w-9 items-center justify-center rounded-xl bg-blue-50 text-[#2563EB] dark:bg-blue-950/40"><Icon size={18} /></div><span className="rounded-full bg-slate-100 px-2 py-1 text-[10px] font-semibold text-slate-500 dark:bg-slate-800 dark:text-slate-400">{status}</span></div>
                    <h3 className="mt-4 text-sm font-bold text-slate-900 dark:text-white">{name}</h3><p className="mt-1 text-xs leading-5 text-slate-500 dark:text-slate-400">{description}</p>
                  </Link>
                ))}
              </div>
            </section>

            <aside className="space-y-4">
              <section className="rounded-2xl border border-slate-200 bg-white p-5 dark:border-slate-800 dark:bg-slate-900">
                <p className="flex items-center gap-2 text-xs font-bold text-slate-900 dark:text-white"><UserRound size={15} className="text-[#2563EB]" /> Profile</p>
                <div className="mt-4 flex items-center gap-3"><div className="flex h-12 w-12 items-center justify-center rounded-full bg-cyan-100 text-sm font-bold text-cyan-700">KP</div><div><p className="text-sm font-bold text-slate-900 dark:text-white">Kasun Perera</p><p className="mt-1 text-xs text-slate-500">BSc (Hons) IT · Year 3</p></div></div>
                <dl className="mt-4 space-y-2 text-xs"><div className="flex justify-between gap-3"><dt className="text-slate-400">Email</dt><dd className="text-right text-slate-700 dark:text-slate-300">kasun.perera@uom.lk</dd></div><div className="flex justify-between gap-3"><dt className="text-slate-400">Phone</dt><dd className="text-slate-700 dark:text-slate-300">+94 71 234 5678</dd></div><div className="flex justify-between gap-3"><dt className="text-slate-400">Status</dt><dd className="text-emerald-600">Online</dd></div></dl>
              </section>
              <section className="rounded-2xl border border-slate-200 bg-white p-5 dark:border-slate-800 dark:bg-slate-900"><p className="flex items-center gap-2 text-xs font-bold text-slate-900 dark:text-white"><Activity size={15} className="text-[#2563EB]" /> Recent activity</p><ul className="mt-4 space-y-3 text-xs text-slate-500 dark:text-slate-400"><li className="border-l-2 border-blue-200 pl-3 dark:border-blue-900">Gateway messaging session available</li><li className="border-l-2 border-slate-200 pl-3 dark:border-slate-700">UDP Presence checked every 5 seconds</li></ul></section>
            </aside>
          </div>
        </div>
      </section>
    </main>
  );
}
