import { Check, CheckCheck } from "lucide-react";
import type { ChatMessage } from "@/types/chat";

export function MessageBubble({ message, isMine }: { message: ChatMessage; isMine: boolean }) {
  return (
    <div className={`flex ${isMine ? "justify-end" : "justify-start"}`}>
      <div className={`max-w-[78%] ${isMine ? "items-end" : "items-start"} flex flex-col`}>
        {!isMine && <span className="mb-1 ml-1 text-[11px] font-semibold text-slate-500">{message.senderName}</span>}
        <div className={`rounded-2xl px-4 py-3 text-sm leading-6 ${isMine ? "rounded-br-md bg-violet-600 text-white shadow-sm shadow-violet-200" : "rounded-bl-md bg-slate-100 text-slate-700"}`}>
          <p className="whitespace-pre-wrap break-words">{message.content}</p>
        </div>
        <span className={`mt-1 flex items-center gap-1 text-[10px] text-slate-400 ${isMine ? "mr-1" : "ml-1"}`}>
          {new Date(message.timestamp).toLocaleTimeString([], { hour: "numeric", minute: "2-digit" })}
          {isMine && (message.delivery === "read" ? <CheckCheck size={13} className="text-cyan-500" /> : <Check size={13} />)}
        </span>
      </div>
    </div>
  );
}
