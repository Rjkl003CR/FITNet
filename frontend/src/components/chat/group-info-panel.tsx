import { FileText, MoreHorizontal, Settings2, Users } from "lucide-react";
import type { Conversation } from "@/types/chat";
import { UserAvatar } from "@/components/ui/user-avatar";

export function GroupInfoPanel({ conversation }: { conversation: Conversation }) {
  return (
    <aside className="hidden w-64 shrink-0 border-l border-slate-200 bg-white xl:block">
      <div className="border-b border-slate-100 p-5 text-center">
        <div className={`mx-auto flex h-14 w-14 items-center justify-center rounded-2xl text-lg font-bold ${conversation.color}`}>{conversation.initials}</div>
        <h3 className="mt-3 text-sm font-bold text-slate-900">{conversation.name}</h3>
        <p className="mt-1 text-xs text-slate-400">{conversation.type === "group" ? `${conversation.members.length} members` : "Direct message"}</p>
      </div>
      <div className="space-y-6 p-5">
        <div><div className="mb-3 flex items-center justify-between"><p className="flex items-center gap-2 text-xs font-bold text-slate-700"><Users size={14} className="text-violet-500" /> Members</p><button aria-label="More member options" className="text-slate-400"><MoreHorizontal size={16} /></button></div><div className="space-y-3">{conversation.members.map((member) => <div key={member.id} className="flex items-center gap-2"><UserAvatar initials={member.initials} color={member.color} status={member.status} size="sm" /><span className="min-w-0 flex-1"><span className="block truncate text-xs font-semibold text-slate-700">{member.name}</span><span className="block truncate text-[10px] text-slate-400">{member.role}</span></span></div>)}</div></div>
        <div><p className="mb-3 flex items-center gap-2 text-xs font-bold text-slate-700"><FileText size={14} className="text-violet-500" /> Shared files</p><div className="rounded-xl border border-dashed border-slate-200 p-4 text-center"><p className="text-[11px] text-slate-400">No shared files yet</p></div></div>
        <button className="flex w-full items-center gap-2 rounded-lg px-2 py-2 text-xs font-semibold text-slate-500 transition hover:bg-slate-50"><Settings2 size={15} /> Conversation settings</button>
      </div>
    </aside>
  );
}
