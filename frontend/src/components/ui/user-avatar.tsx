import type { PresenceStatus } from "@/types/chat";

interface UserAvatarProps {
  initials: string;
  color: string;
  status?: PresenceStatus;
  size?: "sm" | "md" | "lg";
}

export function UserAvatar({ initials, color, status, size = "md" }: UserAvatarProps) {
  const sizeClass = { sm: "h-8 w-8 text-[10px]", md: "h-10 w-10 text-xs", lg: "h-12 w-12 text-sm" }[size];
  return (
    <div className="relative shrink-0">
      <div className={`flex ${sizeClass} items-center justify-center rounded-xl font-semibold ${color}`}>{initials}</div>
      {status && <span className={`absolute -bottom-0.5 -right-0.5 h-2.5 w-2.5 rounded-full border-2 border-white ${status === "online" ? "bg-emerald-500" : status === "away" ? "bg-amber-400" : "bg-slate-300"}`} />}
    </div>
  );
}
