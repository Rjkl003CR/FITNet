"use client";

import { useMemo, useState } from "react";
import { MessageSquare } from "lucide-react";
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
  const [activeId, setActiveId] = useState<string | null>(null);
  const [showInfo, setShowInfo] = useState(true);
  const { messages, status, sendMessage } = useChat();
  const conversations = useMemo<Conversation[]>(() => {
    const result = new Map<string, Conversation>();
    for (const message of messages) {
      const id = message.groupId ?? (message.senderId === "alex-perera" ? message.receiverId : message.senderId);
      if (!id || result.has(id)) continue;
      const name = message.groupId ? "Group conversation" : message.senderId === "alex-perera" ? "Direct message" : message.senderName;
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

  return <main className="flex min-h-screen bg-slate-50"><AppSidebar /><section className="flex min-w-0 flex-1 flex-col"><TopNavbar /><div className="flex min-h-0 flex-1 flex-col p-4 lg:p-6"><div className="mb-5 flex items-center justify-end"><ConnectionStatus status={status} /></div><div className="grid min-h-0 flex-1 overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm lg:grid-cols-[275px_minmax(0,1fr)] xl:grid-cols-[275px_minmax(0,1fr)_256px]"><ConversationList conversations={conversations} activeId={activeId ?? ""} onSelect={setActiveId} onStartConversation={startConversation} />{displayConversation ? <><section className="flex min-h-0 min-w-0 flex-col lg:min-h-[580px]"><ChatHeader conversation={displayConversation} status={status} onInfo={() => setShowInfo(!showInfo)} /><MessageList messages={activeMessages} /><MessageComposer disabled={status !== "connected"} onSend={(content) => sendMessage(content, displayConversation.type === "direct" ? displayConversation.id : null, displayConversation.type === "group" ? displayConversation.id : null)} /></section>{showInfo && <GroupInfoPanel conversation={displayConversation} />}</> : <section className="flex min-h-[400px] min-w-0 flex-1 flex-col items-center justify-center bg-slate-50/60 px-6 text-center"><div className="flex h-16 w-16 items-center justify-center rounded-2xl bg-violet-50 text-violet-600"><MessageSquare size={28} /></div><h2 className="mt-5 text-lg font-bold text-slate-900">Your inbox is ready</h2><p className="mt-2 max-w-sm text-sm leading-6 text-slate-500">{status === "connected" ? "Start a conversation from Messages." : "Connect the gateway to start messaging."}</p></section>}</div></div></section></main>;
}
