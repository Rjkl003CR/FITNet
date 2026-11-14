import type { ChatMessage } from "@/types/chat";
import { MessageBubble } from "./message-bubble";

export function MessageList({ messages }: { messages: ChatMessage[] }) {
  const uniqueMessages = [...new Map(messages.map((message) => [message.messageId, message])).values()];
  return (
    <div className="flex-1 space-y-5 overflow-y-auto px-6 py-6">
      <div className="flex items-center gap-3 py-1"><div className="h-px flex-1 bg-slate-100" /><span className="text-[10px] font-semibold uppercase tracking-widest text-slate-400">Yesterday</span><div className="h-px flex-1 bg-slate-100" /></div>
      {uniqueMessages.map((message) => <MessageBubble key={message.messageId} message={message} isMine={message.senderId === "alex-perera"} />)}
    </div>
  );
}
