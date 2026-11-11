"use client";

import { Paperclip, Send } from "lucide-react";
import { useState } from "react";

export function MessageComposer({ disabled, onSend }: { disabled: boolean; onSend: (content: string) => void }) {
  const [value, setValue] = useState("");
  const send = () => { const content = value.trim(); if (!content || disabled) return; onSend(content); setValue(""); };
  return (
    <div className="border-t border-slate-100 bg-white px-5 py-4">
      {disabled && <p className="mb-2 text-center text-[11px] text-amber-600">Gateway unavailable. Your message will be marked for retry.</p>}
      <div className="flex items-end gap-3 rounded-xl border border-slate-200 bg-slate-50 p-2 transition focus-within:border-violet-300 focus-within:bg-white">
        <button aria-label="Attach a file" className="mb-1 flex h-8 w-8 shrink-0 items-center justify-center rounded-lg text-slate-400 transition hover:bg-slate-100 hover:text-violet-600"><Paperclip size={17} /></button>
        <textarea value={value} onChange={(event) => setValue(event.target.value)} onKeyDown={(event) => { if (event.key === "Enter" && !event.shiftKey) { event.preventDefault(); send(); } }} placeholder="Write a message..." rows={1} className="max-h-28 min-h-8 flex-1 resize-none bg-transparent py-1.5 text-sm text-slate-700 outline-none placeholder:text-slate-400" />
        <button onClick={send} disabled={!value.trim() || disabled} aria-label="Send message" className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-violet-600 text-white transition hover:bg-violet-700 disabled:cursor-not-allowed disabled:bg-slate-200"><Send size={15} /></button>
      </div>
      <p className="mt-2 text-[10px] text-slate-400">Press Enter to send · Shift + Enter for a new line</p>
    </div>
  );
}
