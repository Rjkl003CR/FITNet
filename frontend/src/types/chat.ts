export type PresenceStatus = "online" | "away" | "offline";
export type ConversationType = "direct" | "group";
export type MessageDelivery = "sending" | "sent" | "delivered" | "read" | "failed";

export interface ChatMessage {
  messageId: string;
  type: "PRIVATE_MESSAGE" | "GROUP_MESSAGE" | "SYSTEM";
  senderId: string;
  senderName: string;
  receiverId: string | null;
  groupId: string | null;
  content: string;
  timestamp: string;
  delivery?: MessageDelivery;
}

export interface ConversationMember {
  id: string;
  name: string;
  role: string;
  status: PresenceStatus;
  initials: string;
  color: string;
}

export interface Conversation {
  id: string;
  name: string;
  type: ConversationType;
  preview: string;
  timestamp: string;
  unreadCount: number;
  status?: PresenceStatus;
  initials: string;
  color: string;
  members: ConversationMember[];
}
