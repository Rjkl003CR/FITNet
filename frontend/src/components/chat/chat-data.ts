import type { ChatMessage, Conversation } from "@/types/chat";

export const conversations: Conversation[] = [
  {
    id: "design-team",
    name: "Design Team",
    type: "group",
    preview: "Maya: The new flow looks great!",
    timestamp: "10:42 AM",
    unreadCount: 3,
    initials: "DT",
    color: "bg-violet-100 text-violet-700",
    members: [
      { id: "maya", name: "Maya Fernando", role: "Product designer", status: "online", initials: "MF", color: "bg-cyan-100 text-cyan-700" },
      { id: "dilan", name: "Dilan Silva", role: "Frontend developer", status: "online", initials: "DS", color: "bg-amber-100 text-amber-700" },
      { id: "alex-perera", name: "Alex Perera", role: "Frontend developer", status: "online", initials: "AP", color: "bg-violet-100 text-violet-700" },
    ],
  },
  {
    id: "maya",
    name: "Maya Fernando",
    type: "direct",
    preview: "Can you review the latest prototype?",
    timestamp: "Yesterday",
    unreadCount: 0,
    status: "online",
    initials: "MF",
    color: "bg-cyan-100 text-cyan-700",
    members: [{ id: "maya", name: "Maya Fernando", role: "Product designer", status: "online", initials: "MF", color: "bg-cyan-100 text-cyan-700" }],
  },
  {
    id: "networking-group",
    name: "Networking Group",
    type: "group",
    preview: "Dilan shared a file",
    timestamp: "Tue",
    unreadCount: 0,
    initials: "NG",
    color: "bg-amber-100 text-amber-700",
    members: [
      { id: "dilan", name: "Dilan Silva", role: "Frontend developer", status: "online", initials: "DS", color: "bg-amber-100 text-amber-700" },
      { id: "naveen", name: "Naveen Raj", role: "Backend developer", status: "away", initials: "NR", color: "bg-rose-100 text-rose-700" },
      { id: "alex-perera", name: "Alex Perera", role: "Frontend developer", status: "online", initials: "AP", color: "bg-violet-100 text-violet-700" },
    ],
  },
  {
    id: "naveen",
    name: "Naveen Raj",
    type: "direct",
    preview: "Thanks, that works for me.",
    timestamp: "Mon",
    unreadCount: 0,
    status: "away",
    initials: "NR",
    color: "bg-rose-100 text-rose-700",
    members: [{ id: "naveen", name: "Naveen Raj", role: "Backend developer", status: "away", initials: "NR", color: "bg-rose-100 text-rose-700" }],
  },
];

export const initialMessages: ChatMessage[] = [
  { messageId: "m1", type: "GROUP_MESSAGE", senderId: "maya", senderName: "Maya Fernando", receiverId: null, groupId: "design-team", content: "Hi team! I’ve added the latest onboarding screens to the shared prototype.", timestamp: "2026-11-11T09:30:00Z", delivery: "read" },
  { messageId: "m2", type: "GROUP_MESSAGE", senderId: "dilan", senderName: "Dilan Silva", receiverId: null, groupId: "design-team", content: "Nice work, Maya. The reduced number of steps makes the flow feel much lighter.", timestamp: "2026-11-11T09:34:00Z", delivery: "read" },
  { messageId: "m3", type: "GROUP_MESSAGE", senderId: "alex-perera", senderName: "Alex Perera", receiverId: null, groupId: "design-team", content: "Agreed. I’ll wire the new states into the frontend this afternoon.", timestamp: "2026-11-11T09:38:00Z", delivery: "read" },
  { messageId: "m4", type: "GROUP_MESSAGE", senderId: "maya", senderName: "Maya Fernando", receiverId: null, groupId: "design-team", content: "The new flow looks great! I’ve also left a few notes around the empty states.", timestamp: "2026-11-11T10:42:00Z", delivery: "read" },
  { messageId: "m5", type: "GROUP_MESSAGE", senderId: "maya", senderName: "Maya Fernando", receiverId: null, groupId: "design-team", content: "Would love your feedback before our stand-up tomorrow.", timestamp: "2026-11-11T10:43:00Z", delivery: "read" },
];
