"use client";

import { useMemo, useState } from "react";
import { MessageSquare, Plug, PlugZap } from "lucide-react";
import { AppSidebar } from "@/components/layout/app-sidebar";
import { TopNavbar } from "@/components/layout/top-navbar";
import { ChatHeader } from "@/components/chat/chat-header";
import { ConnectionStatus } from "@/components/chat/connection-status";
import { ConversationList } from "@/components/chat/conversation-list";
import { GroupInfoPanel } from "@/components/chat/group-info-panel";
import { MessageComposer } from "@/components/chat/message-composer";
import { MessageList } from "@/components/chat/message-list";
import { useChat } from "@/hooks/use-chat";
import type { Conversation } from "@/types/chat";

export default function Home() {
  const [activeId, setActiveId] = useState<string | null>("chamathka-ranathunga");
  const [showInfo, setShowInfo] = useState(false);
  const { messages, status, sendMessage, disconnect, reconnect } = useChat();
  const conversations = useMemo<Conversation[]>(() => {
    const result = new Map<string, Conversation>([
      ["chamathka-ranathunga", { id: "chamathka-ranathunga", name: "Chamathka Ranathunga", type: "direct", preview: "Are you joining the group study session?", timestamp: "21/11", unreadCount: 0, status: "online", initials: "CR", color: "bg-blue-100 text-blue-800", members: [{ id: "chamathka-ranathunga", name: "Chamathka Ranathunga", role: "University of Moratuwa", status: "online", initials: "CR", color: "bg-blue-100 text-blue-800" }] }],
      ["nethmi-silva", { id: "nethmi-silva", name: "Nethmi Silva", type: "direct", preview: "The lecture slides are ready.", timestamp: "20/11", unreadCount: 1, status: "away", initials: "NS", color: "bg-cyan-100 text-cyan-800", members: [{ id: "nethmi-silva", name: "Nethmi Silva", role: "University of Colombo", status: "away", initials: "NS", color: "bg-cyan-100 text-cyan-800" }] }],
      ["kavindu-fernando", { id: "kavindu-fernando", name: "Kavindu Fernando", type: "direct", preview: "See you in the lab.", timestamp: "19/11", unreadCount: 0, status: "offline", initials: "KF", color: "bg-slate-200 text-slate-800", members: [{ id: "kavindu-fernando", name: "Kavindu Fernando", role: "University of Sri Jayewardenepura", status: "offline", initials: "KF", color: "bg-slate-200 text-slate-800" }] }],
    ]);
    for (const message of messages) {
      const id = message.groupId ?? (message.senderId === "kasun-perera" ? message.receiverId : message.senderId);
      if (!id || result.has(id)) continue;
      const name = message.groupId ? "Network Programming" : message.senderId === "kasun-perera" ? (message.receiverId === "chamathka-ranathunga" ? "Chamathka Ranathunga" : "Student conversation") : message.senderName;
      const initials = name.split(" ").map((part) => part[0]).join("").slice(0, 2).toUpperCase();
      result.set(id, { id, name, type: message.groupId ? "group" : "direct", preview: message.content, timestamp: new Date(message.timestamp).toLocaleDateString([], { month: "short", day: "numeric" }), unreadCount: 0, status: "online", initials, color: "bg-violet-100 text-violet-700", members: [{ id: message.senderId, name: message.senderName, role: "Participant", status: "online", initials, color: "bg-violet-100 text-violet-700" }] });
    }
    return [...result.values()];
  }, [messages]);
  const activeConversation = conversations.find((item) => item.id === activeId) ?? null;
  const activeMessages = useMemo(() => {
    const filtered = activeConversation ? messages.filter((message) => message.groupId === activeConversation.id || (activeConversation.type === "direct" && (message.senderId === activeConversation.id || message.receiverId === activeConversation.id))) : [];
    return [...new Map(filtered.map((message) => [message.messageId, message])).values()];
  }, [activeConversation, messages]);
  const startConversation = (recipientId: string) => {
    setActiveId(recipientId);
    setShowInfo(true);
  };

  const displayConversation = activeConversation ?? (activeId ? { id: activeId, name: activeId, type: "direct" as const, preview: "", timestamp: "", unreadCount: 0, status: "offline" as const, initials: activeId.slice(0, 2).toUpperCase(), color: "bg-slate-100 text-slate-700", members: [{ id: activeId, name: activeId, role: "Recipient", status: "offline" as const, initials: activeId.slice(0, 2).toUpperCase(), color: "bg-slate-100 text-slate-700" }] } : null);

  return (
    <main className="theme-bg flex min-h-screen">
      <AppSidebar />
      <section className="flex min-w-0 flex-1 flex-col">
        <TopNavbar />
        <div className="min-h-0 flex-1 overflow-hidden p-3 sm:p-4 lg:p-5">
          <div className="mb-3 flex items-center justify-end">
            <div className="flex items-center gap-2">
              <ConnectionStatus status={status} />
              {status === "connected"
                ? <button type="button" onClick={disconnect} className="flex h-9 items-center gap-2 rounded-lg border border-red-200 px-3 text-xs font-semibold text-red-600 hover:bg-red-50 dark:border-red-900 dark:hover:bg-red-950/30"><Plug size={14} /> Disconnect</button>
                : <button type="button" onClick={reconnect} className="flex h-9 items-center gap-2 rounded-lg bg-[#2563EB] px-3 text-xs font-semibold text-white"><PlugZap size={14} /> Connect</button>}
            </div>
          </div>

          <div className={`grid h-[calc(100vh-150px)] min-h-[520px] overflow-hidden rounded-2xl border border-slate-300 bg-white shadow-sm dark:border-slate-700 dark:bg-slate-900 lg:grid-cols-[280px_minmax(0,1fr)] ${showInfo ? "xl:grid-cols-[280px_minmax(0,1fr)_280px]" : "xl:grid-cols-[280px_minmax(0,1fr)]"}`}>
            <ConversationList conversations={conversations} activeId={activeId ?? ""} onSelect={setActiveId} onStartConversation={startConversation} />
            {displayConversation ? (
              <>
                <section className="flex min-h-[560px] min-w-0 flex-col">
                  <ChatHeader conversation={displayConversation} onInfo={() => setShowInfo(!showInfo)} />
                  <MessageList messages={activeMessages} />
                  <MessageComposer disabled={status !== "connected"} onSend={(content, attachment) => sendMessage(content, displayConversation.type === "direct" ? displayConversation.id : null, displayConversation.type === "group" ? displayConversation.id : null, attachment)} />
                </section>
                {showInfo && <GroupInfoPanel conversation={displayConversation} />}
              </>
            ) : (
              <section className="flex min-h-[400px] items-center justify-center px-6 text-center">
                <div><div className="mx-auto flex h-16 w-16 items-center justify-center rounded-2xl bg-blue-50 text-[#2563EB] dark:bg-blue-950/40"><MessageSquare size={28} /></div><h2 className="mt-5 text-lg font-bold text-slate-900 dark:text-white">Open a conversation</h2><p className="mt-2 max-w-sm text-sm text-slate-500">Choose a student or enter a user ID.</p></div>
              </section>
            )}
          </div>
        </div>
      </section>
    </main>
  );
}
