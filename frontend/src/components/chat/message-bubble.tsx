import { Check, CheckCheck } from "lucide-react";
import Image from "next/image";
import type { ChatMessage } from "@/types/chat";
import { Download, FileText } from "lucide-react";

export function MessageBubble({ message, isMine }: { message: ChatMessage; isMine: boolean }) {
  return (
    <div className={`flex ${isMine ? "justify-end" : "justify-start"}`}>
      <div className={`max-w-[78%] ${isMine ? "items-end" : "items-start"} flex flex-col`}>
        {!isMine && <span className="mb-1 ml-1 text-[11px] font-semibold text-slate-500">{message.senderName}</span>}
        <div className={`rounded-2xl px-4 py-3 text-sm leading-6 ${isMine ? "rounded-br-md bg-[#2563EB] text-white" : "incoming-message rounded-bl-md bg-slate-200 text-slate-950 dark:bg-slate-700 dark:text-slate-50"}`}>
          <p className="whitespace-pre-wrap break-words">{message.content}</p>
          {message.attachment && <div className="mt-2">
            {message.attachment.type.startsWith("image/") && message.attachment.dataUrl
              ? <Image src={message.attachment.dataUrl} alt={message.attachment.name} width={320} height={240} unoptimized className="max-h-64 max-w-full rounded-xl object-contain" />
              : <a href={message.attachment.dataUrl ?? "#"} download={message.attachment.name} className="flex max-w-64 items-center gap-2 rounded-lg border border-white/30 bg-white/10 px-3 py-2 text-xs no-underline">
                  <FileText size={16} /><span className="min-w-0 flex-1 truncate">{message.attachment.name}</span>{message.attachment.dataUrl && <Download size={14} />}
                </a>}
          </div>}
        </div>
        <span className={`mt-1 flex items-center gap-1 text-[10px] text-slate-400 ${isMine ? "mr-1" : "ml-1"}`}>
          {new Date(message.timestamp).toLocaleTimeString([], { hour: "numeric", minute: "2-digit" })}
          {isMine && (message.delivery === "read" ? <CheckCheck size={13} className="text-cyan-500" /> : <Check size={13} />)}
        </span>
      </div>
    </div>
  );
}
