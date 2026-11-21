"use client";

import { ChevronDown, Gauge, MessageSquare, Radio, Server, Settings, Shield, Wifi } from "lucide-react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { useState } from "react";

const navigation = [
  { label: "Dashboard", icon: Gauge, href: "/dashboard" },
  { label: "TCP Messaging", icon: MessageSquare, href: "/" },
  { label: "UDP Presence", icon: Wifi, href: "/presence" },
  { label: "Multicast Announcements", icon: Radio, href: "/modules/multicast" },
  { label: "Secure File Transfer", icon: Shield, href: "/modules/tls-transfer" },
  { label: "Remote Resources", icon: Server, href: "/modules/remote-resources" },
];

export function AppSidebar() {
  const [open, setOpen] = useState(false);
  const [notice, setNotice] = useState("");
  const pathname = usePathname();
  return (
    <>
      <button onClick={() => setOpen(!open)} aria-label="Toggle navigation" className="fixed left-4 top-4 z-40 flex h-10 w-10 items-center justify-center rounded-xl bg-slate-900 text-white shadow-lg lg:hidden"><MessageSquare size={18} /></button>
      <aside className={`theme-sidebar fixed inset-y-0 left-0 z-30 flex w-64 flex-col border-r px-4 py-5 theme-border theme-text transition-transform lg:static lg:translate-x-0 ${open ? "translate-x-0" : "-translate-x-full"}`}>
        <div className="flex items-center gap-3 px-3"><div className="flex h-9 w-9 items-center justify-center rounded-xl bg-[#2563EB] text-white shadow-lg shadow-blue-200/50 dark:shadow-blue-950/30"><MessageSquare size={18} /></div><div><p className="text-sm font-bold tracking-tight">FITNet</p><p className="text-[10px] text-slate-500 dark:text-slate-400">Collaboration network</p></div></div>
        <div className="mt-8">
          <p className="px-3 text-[10px] font-bold uppercase tracking-[0.16em] text-slate-400 dark:text-slate-500">Workspace</p>
          <nav className="mt-3 space-y-1">
            {navigation.map(({ label, icon: Icon, href }) => {
              const active = pathname === href;
              return (
                <Link key={label} href={href} onClick={() => setOpen(false)} className={`flex w-full items-center gap-3 rounded-xl px-3 py-2.5 text-left text-xs font-medium transition ${active ? "bg-[#2563EB] text-white shadow-sm" : "text-slate-600 hover:bg-slate-100 hover:text-slate-950 dark:text-slate-400 dark:hover:bg-white/5 dark:hover:text-white"}`}>
                  <Icon size={17} strokeWidth={active ? 2.4 : 1.8} />
                  <span>{label}</span>
                  {active && <span className="ml-auto h-1.5 w-1.5 rounded-full bg-cyan-300" />}
                </Link>
              );
            })}
          </nav>
        </div>
        <div className="mt-auto">{notice && <button type="button" onClick={() => setNotice("")} className="mb-3 w-full rounded-lg bg-white/10 px-3 py-2 text-left text-[11px] text-slate-300">{notice}</button>}<button type="button" onClick={() => setNotice("Settings will be available soon.")} className="mb-5 flex w-full items-center gap-3 rounded-xl px-3 py-2.5 text-xs font-medium text-slate-400 transition hover:bg-white/5 hover:text-white"><Settings size={17} /><span>Settings</span></button><div className="flex items-center gap-3 border-t border-white/10 px-2 pt-4"><div className="flex h-9 w-9 items-center justify-center rounded-full bg-cyan-100 text-xs font-bold text-cyan-700">KP</div><div className="min-w-0 flex-1"><p className="truncate text-xs font-semibold">Kasun Perera</p><p className="mt-0.5 text-[10px] text-slate-400">Online</p></div><ChevronDown size={15} className="text-slate-500" /></div></div>
      </aside>
    </>
  );
}
