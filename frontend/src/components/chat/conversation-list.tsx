"use client";

import { Search } from "lucide-react";
import { useMemo, useState } from "react";
import type { Conversation } from "@/types/chat";
import { UserAvatar } from "@/components/ui/user-avatar";

interface ConversationListProps {
  conversations: Conversation[];
  activeId: string;
  onSelect: (id: string) => void;
}

export function ConversationList({ conversations, activeId, onSelect }: ConversationListProps) {
  const [query, setQuery] = useState("");
  const filtered = useMemo(() => conversations.filter((item) => item.name.toLowerCase().includes(query.toLowerCase())), [conversations, query]);
  return (
    <aside className="flex min-h-0 flex-col border-r border-slate-200 bg-white">
      <div className="border-b border-slate-100 p-5">
        <div className="mb-5 flex items-center justify-between">
          <div><p className="text-sm font-bold text-slate-900">Messages</p><p className="mt-1 text-xs text-slate-400">Your conversations</p></div>
          <button aria-label="New conversation" className="flex h-8 w-8 items-center justify-center rounded-lg bg-violet-50 text-lg text-violet-600 transition hover:bg-violet-100">+</button>
        </div>
        <label className="flex h-10 items-center gap-2 rounded-lg bg-slate-50 px-3 text-slate-400 ring-1 ring-transparent focus-within:ring-violet-200">
          <Search size={16} /><input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Search conversations" className="min-w-0 flex-1 bg-transparent text-xs text-slate-700 outline-none placeholder:text-slate-400" />
        </label>
      </div>
      <div className="min-h-0 flex-1 overflow-y-auto p-3">
        <p className="mb-2 px-2 text-[10px] font-bold uppercase tracking-[0.16em] text-slate-400">Recent</p>
        {filtered.map((conversation) => (
          <button key={conversation.id} onClick={() => onSelect(conversation.id)} className={`mb-1 flex w-full items-center gap-3 rounded-xl p-3 text-left transition ${activeId === conversation.id ? "bg-violet-50" : "hover:bg-slate-50"}`}>
            <UserAvatar initials={conversation.initials} color={conversation.color} status={conversation.type === "direct" ? conversation.status : undefined} />
            <span className="min-w-0 flex-1"><span className={`block truncate text-xs font-semibold ${activeId === conversation.id ? "text-violet-900" : "text-slate-800"}`}>{conversation.name}</span><span className="mt-1 block truncate text-[11px] text-slate-400">{conversation.preview}</span></span>
            <span className="self-stretch text-right"><span className="block text-[10px] text-slate-400">{conversation.timestamp}</span>{conversation.unreadCount > 0 && <span className="mt-2 inline-flex h-5 min-w-5 items-center justify-center rounded-full bg-violet-600 px-1.5 text-[10px] font-bold text-white">{conversation.unreadCount}</span>}</span>
          </button>
        ))}
        {filtered.length === 0 && <p className="px-2 py-8 text-center text-xs text-slate-400">No conversations found</p>}
      </div>
    </aside>
  );
}
