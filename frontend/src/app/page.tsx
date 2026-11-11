"use client";

import { useMemo, useState } from "react";
import { AppSidebar } from "@/components/layout/app-sidebar";
import { TopNavbar } from "@/components/layout/top-navbar";
import { ChatHeader } from "@/components/chat/chat-header";
import { ConnectionStatus } from "@/components/chat/connection-status";
import { ConversationList } from "@/components/chat/conversation-list";
import { GroupInfoPanel } from "@/components/chat/group-info-panel";
import { MessageComposer } from "@/components/chat/message-composer";
import { MessageList } from "@/components/chat/message-list";
import { conversations, initialMessages } from "@/components/chat/chat-data";
import { useChat } from "@/hooks/use-chat";

export default function Home() {
  const [activeId, setActiveId] = useState(conversations[0].id);
  const activeConversation = useMemo(() => conversations.find((item) => item.id === activeId) ?? conversations[0], [activeId]);
  const { messages, status, sendMessage } = useChat(initialMessages);
  const activeMessages = messages.filter((message) => message.groupId === activeConversation.id || (activeConversation.type === "direct" && (message.senderId === activeConversation.id || message.receiverId === activeConversation.id)));

  return <main className="flex min-h-screen bg-slate-50"><AppSidebar /><section className="flex min-w-0 flex-1 flex-col"><TopNavbar /><div className="flex min-h-0 flex-1 flex-col p-4 lg:p-6"><div className="mb-5 flex flex-wrap items-center justify-between gap-3"><div><p className="text-xs text-slate-400">Stay connected with your team</p><h2 className="mt-1 text-xl font-bold tracking-tight text-slate-900">Team conversations</h2></div><ConnectionStatus status={status} /></div><div className="grid min-h-0 flex-1 overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm lg:grid-cols-[275px_minmax(0,1fr)] xl:grid-cols-[275px_minmax(0,1fr)_256px]"><ConversationList conversations={conversations} activeId={activeId} onSelect={setActiveId} /><section className="flex min-h-0 min-w-0 flex-col lg:min-h-[580px]"><ChatHeader conversation={activeConversation} status={status} /><MessageList messages={activeMessages} /><MessageComposer disabled={false} onSend={(content) => sendMessage(content, activeConversation.type === "direct" ? activeConversation.id : null, activeConversation.type === "group" ? activeConversation.id : null)} /></section><GroupInfoPanel conversation={activeConversation} /></div></div></section></main>;
}
