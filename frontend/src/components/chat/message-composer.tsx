"use client";

import { FileText, Paperclip, Send, X } from "lucide-react";
import Image from "next/image";
import { useRef, useState } from "react";
import type { ChatAttachment } from "@/types/chat";

export function MessageComposer({ disabled, onSend }: { disabled: boolean; onSend: (content: string, attachment?: ChatAttachment) => void }) {
  const [value, setValue] = useState("");
  const [attachment, setAttachment] = useState<(ChatAttachment & { localUrl?: string }) | null>(null);
  const fileInput = useRef<HTMLInputElement>(null);
  const send = () => {
    const content = value.trim();
    if ((!content && !attachment) || disabled) return;
    onSend(content || (attachment?.type.startsWith("image/") ? "Sent an image" : "Sent a document"), attachment ? { name: attachment.name, type: attachment.type, size: attachment.size, dataUrl: attachment.dataUrl } : undefined);
    setValue("");
    setAttachment(null);
  };
  const selectFile = (file: File) => {
    const localUrl = URL.createObjectURL(file);
    if (file.size <= 512 * 1024) {
      const reader = new FileReader();
      reader.onload = () => setAttachment({ name: file.name, type: file.type, size: file.size, dataUrl: typeof reader.result === "string" ? reader.result : undefined, localUrl });
      reader.readAsDataURL(file);
    } else {
      setAttachment({ name: file.name, type: file.type, size: file.size, localUrl });
    }
  };
  return (
    <div className="border-t border-slate-200 bg-white px-4 py-3 dark:border-slate-800 dark:bg-slate-900">
      <input ref={fileInput} type="file" accept="image/*,.pdf,.doc,.docx,.xls,.xlsx,.ppt,.pptx,.txt,.zip" className="hidden" onChange={(event) => { const file = event.target.files?.[0]; if (file) selectFile(file); event.target.value = ""; }} />
      {attachment && <div className="mb-2 flex items-center gap-2 rounded-xl border border-slate-200 bg-slate-50 p-2 dark:border-slate-700 dark:bg-slate-950">
        {attachment.type.startsWith("image/") && attachment.localUrl ? <Image src={attachment.localUrl} alt="" width={40} height={40} unoptimized className="h-10 w-10 rounded-lg object-cover" /> : <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-blue-50 text-blue-700 dark:bg-blue-950/40"><FileText size={18} /></div>}
        <div className="min-w-0 flex-1"><p className="truncate text-xs font-semibold text-slate-800 dark:text-slate-100">{attachment.name}</p><p className="text-[10px] text-slate-500">{attachment.size < 1024 * 1024 ? `${Math.ceil(attachment.size / 1024)} KB` : `${(attachment.size / 1024 / 1024).toFixed(1)} MB`}</p></div>
        <button type="button" aria-label="Remove attachment" onClick={() => setAttachment(null)} className="rounded-md p-1 text-slate-500 hover:bg-slate-200 dark:hover:bg-slate-800"><X size={15} /></button>
      </div>}
      {disabled && <p className="mb-2 text-center text-[11px] font-medium text-amber-700 dark:text-amber-400">Connect to send messages</p>}
      <div className="flex items-end gap-2 rounded-xl border border-slate-300 bg-slate-50 p-2 transition focus-within:border-blue-500 focus-within:ring-2 focus-within:ring-blue-100 dark:border-slate-700 dark:bg-slate-950 dark:focus-within:ring-blue-950">
        <button type="button" aria-label="Attach a file or image" onClick={() => fileInput.current?.click()} className="mb-1 flex h-8 w-8 shrink-0 items-center justify-center rounded-lg text-slate-500 transition hover:bg-slate-200 hover:text-blue-700 dark:hover:bg-slate-800"><Paperclip size={17} /></button>
        <textarea value={value} onChange={(event) => setValue(event.target.value)} onKeyDown={(event) => { if (event.key === "Enter" && !event.shiftKey) { event.preventDefault(); send(); } }} placeholder="Message" rows={1} className="max-h-28 min-h-8 flex-1 resize-none bg-transparent py-1.5 text-sm text-slate-950 outline-none placeholder:text-slate-500 dark:text-white" />
        <button onClick={send} disabled={(!value.trim() && !attachment) || disabled} aria-label="Send message" className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-[#2563EB] text-white transition hover:bg-blue-700 disabled:cursor-not-allowed disabled:bg-slate-300 dark:disabled:bg-slate-700"><Send size={16} /></button>
      </div>
      <p className="mt-2 text-center text-[10px] text-slate-500 dark:text-slate-400">Enter to send</p>
    </div>
  );
}
