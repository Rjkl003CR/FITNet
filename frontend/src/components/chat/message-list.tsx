import type { ChatMessage } from "@/types/chat";
import { MessageBubble } from "./message-bubble";

export function MessageList({ messages }: { messages: ChatMessage[] }) {
  const uniqueMessages = [...new Map(messages.map((message) => [message.messageId, message])).values()];
  return (
    <div className="flex-1 space-y-5 overflow-y-auto px-6 py-6">
      {uniqueMessages.map((message) => <MessageBubble key={message.messageId} message={message} isMine={message.senderId === "kasun-perera"} />)}
    </div>
  );
}
