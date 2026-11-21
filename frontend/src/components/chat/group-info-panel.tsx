import { Building2, GraduationCap, Mail, Phone } from "lucide-react";
import type { Conversation } from "@/types/chat";

export function GroupInfoPanel({ conversation }: { conversation: Conversation }) {
  const member = conversation.members[0];
  const profile = conversation.id === "chamathka-ranathunga"
    ? { university: "University of Moratuwa", faculty: "Faculty of Information Technology", department: "Computing", year: "Year 3", email: "chamathka.r@uom.lk", phone: "+94 77 123 4567", bio: "Network programming student." }
    : conversation.id === "nethmi-silva"
      ? { university: "University of Colombo", faculty: "Faculty of Science", department: "Statistics and Computer Science", year: "Year 2", email: "nethmi.s@ucsc.cmb.ac.lk", phone: "+94 71 234 5678", bio: "Interested in software systems and distributed networks." }
      : conversation.id === "kavindu-fernando"
        ? { university: "University of Sri Jayewardenepura", faculty: "Faculty of Computing", department: "Computer Science", year: "Year 4", email: "kavindu.f@sjp.ac.lk", phone: "+94 76 345 6789", bio: "Final-year computing student." }
        : { university: "University of Moratuwa", faculty: "Faculty of Information Technology", department: "Computing", year: "Year 3", email: `${conversation.name.toLowerCase().replace(/\s+/g, ".")}@uom.lk`, phone: "+94 71 000 0000", bio: "FITNet student." };
  return (
    <aside className="hidden w-[280px] shrink-0 border-l border-slate-200 bg-white dark:border-slate-800 dark:bg-slate-900 xl:block">
      <div className="border-b border-slate-100 p-6 text-center dark:border-slate-800">
        <div className={`mx-auto flex h-16 w-16 items-center justify-center rounded-full text-lg font-bold ${conversation.color}`}>{conversation.initials}</div>
        <h3 className="mt-3 text-base font-bold text-slate-950 dark:text-white">{conversation.name}</h3>
        <p className="mt-1 text-xs font-medium text-emerald-600">{member?.status === "online" ? "Online" : member?.status === "away" ? "Away" : "Offline"}</p>
      </div>
      <div className="space-y-4 p-5 text-xs">
        <p className="leading-5 text-slate-600 dark:text-slate-300">{profile.bio}</p>
        <div className="space-y-3">
          <p className="flex items-start gap-2 text-slate-700 dark:text-slate-200"><Building2 size={15} className="mt-0.5 shrink-0 text-blue-600" /><span>{profile.university}</span></p>
          <p className="flex items-start gap-2 text-slate-700 dark:text-slate-200"><GraduationCap size={15} className="mt-0.5 shrink-0 text-blue-600" /><span>{profile.faculty}<br />{profile.department} · {profile.year}</span></p>
          <p className="flex items-start gap-2 text-slate-700 dark:text-slate-200"><Mail size={15} className="mt-0.5 shrink-0 text-blue-600" /><span className="break-all">{profile.email}</span></p>
          <p className="flex items-start gap-2 text-slate-700 dark:text-slate-200"><Phone size={15} className="mt-0.5 shrink-0 text-blue-600" /><span>{profile.phone}</span></p>
        </div>
      </div>
    </aside>
  );
}
