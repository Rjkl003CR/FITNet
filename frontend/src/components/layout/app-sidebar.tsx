"use client";

import { ChevronDown, Gauge, MessageSquare, Radio, Server, Settings, Shield, Wifi } from "lucide-react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { useState } from "react";

const navigation = [
  { label: "Dashboard", icon: Gauge },
  { label: "TCP Messaging", icon: MessageSquare, href: "/" },
  { label: "UDP Presence", icon: Wifi, href: "/presence" },
  { label: "Multicast Announcements", icon: Radio },
  { label: "Secure File Transfer", icon: Shield },
  { label: "Remote Resources", icon: Server },
];

export function AppSidebar() {
  const [open, setOpen] = useState(false);
  const pathname = usePathname();
  return (
    <>
      <button onClick={() => setOpen(!open)} aria-label="Toggle navigation" className="fixed left-4 top-4 z-40 flex h-10 w-10 items-center justify-center rounded-xl bg-slate-900 text-white shadow-lg lg:hidden"><MessageSquare size={18} /></button>
      <aside className={`fixed inset-y-0 left-0 z-30 flex w-64 flex-col bg-[#111827] px-4 py-5 text-white transition-transform lg:static lg:translate-x-0 ${open ? "translate-x-0" : "-translate-x-full"}`}>
        <div className="flex items-center gap-3 px-3"><div className="flex h-9 w-9 items-center justify-center rounded-xl bg-violet-600 shadow-lg shadow-violet-900/30"><MessageSquare size={18} /></div><div><p className="text-sm font-bold tracking-tight">FITNet</p><p className="text-[10px] text-slate-400">Collaboration network</p></div></div>
        <div className="mt-10"><p className="px-3 text-[10px] font-bold uppercase tracking-[0.18em] text-slate-500">Workspace</p><nav className="mt-3 space-y-1">{navigation.map(({ label, icon: Icon, href }) => { const active = href ? pathname === href : false; const content = <><Icon size={17} strokeWidth={active ? 2.4 : 1.8} /><span>{label}</span>{active && <span className="ml-auto h-1.5 w-1.5 rounded-full bg-cyan-400" />}</>; return href ? <Link key={label} href={href} onClick={() => setOpen(false)} className={`flex w-full items-center gap-3 rounded-xl px-3 py-2.5 text-left text-xs font-medium transition ${active ? "bg-violet-600 text-white shadow-lg shadow-violet-900/20" : "text-slate-400 hover:bg-white/5 hover:text-white"}`}>{content}</Link> : <button key={label} type="button" disabled className="flex w-full cursor-not-allowed items-center gap-3 rounded-xl px-3 py-2.5 text-left text-xs font-medium text-slate-600">{content}</button>; })}</nav></div>
        <div className="mt-auto"><button className="mb-5 flex w-full items-center gap-3 rounded-xl px-3 py-2.5 text-xs font-medium text-slate-400 transition hover:bg-white/5 hover:text-white"><Settings size={17} /><span>Settings</span></button><div className="flex items-center gap-3 border-t border-white/10 px-2 pt-4"><div className="flex h-9 w-9 items-center justify-center rounded-full bg-cyan-100 text-xs font-bold text-cyan-700">AP</div><div className="min-w-0 flex-1"><p className="truncate text-xs font-semibold">Alex Perera</p><p className="mt-0.5 text-[10px] text-slate-500">Online</p></div><ChevronDown size={15} className="text-slate-500" /></div></div>
      </aside>
    </>
  );
}
