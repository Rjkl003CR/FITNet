"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { ChatGateway, type GatewayStatus, isGatewayConfigured } from "@/services/websocket-service";
import type { ChatAttachment, ChatMessage } from "@/types/chat";

const currentUser = { id: "kasun-perera", name: "Kasun Perera" };

export function useChat() {
  const [messages, setMessages] = useState<ChatMessage[]>([
    {
      messageId: "sample-networking-1",
      type: "PRIVATE_MESSAGE",
      senderId: "chamathka-ranathunga",
      senderName: "Chamathka Ranathunga",
      receiverId: "kasun-perera",
      groupId: null,
      content: "Are you joining the group study session later?",
      timestamp: "2026-11-21T14:30:00+05:30",
      delivery: "delivered",
    },
    {
      messageId: "sample-networking-2",
      type: "PRIVATE_MESSAGE",
      senderId: "kasun-perera",
      senderName: "Kasun Perera",
      receiverId: "chamathka-ranathunga",
      groupId: null,
      content: "Yes, I'll be there after my lecture.",
      timestamp: "2026-11-21T14:32:00+05:30",
      delivery: "read",
    },
  ]);
  const [status, setStatus] = useState<GatewayStatus>(isGatewayConfigured ? "connecting" : "disconnected");
  const gateway = useRef<ChatGateway | null>(null);
  const upsertMessage = useCallback((message: ChatMessage, delivery?: ChatMessage["delivery"]) => {
    setMessages((current) => {
      const next = current.filter((item) => item.messageId !== message.messageId);
      const existing = current.find((item) => item.messageId === message.messageId);
      return [...next, { ...existing, ...message, delivery: delivery ?? message.delivery ?? existing?.delivery }];
    });
  }, []);

  useEffect(() => {
    if (!isGatewayConfigured) return;
    const service = new ChatGateway({
      onStatusChange: setStatus,
      onMessage: (message) => upsertMessage(message, "sent"),
    });
    gateway.current = service;
    service.connect();
    return () => service.disconnect();
  }, [upsertMessage]);

  const sendMessage = useCallback((content: string, receiverId: string | null, groupId: string | null, attachment?: ChatAttachment) => {
    const message: ChatMessage = {
      messageId: crypto.randomUUID(),
      type: groupId ? "GROUP_MESSAGE" : "PRIVATE_MESSAGE",
      senderId: currentUser.id,
      senderName: currentUser.name,
      receiverId,
      groupId,
      content,
      timestamp: new Date().toISOString(),
      delivery: "sending",
      attachment,
    };
    upsertMessage(message);
    if (gateway.current?.send(message)) {
      upsertMessage(message, "sent");
    } else {
      upsertMessage(message, "failed");
    }
  }, [upsertMessage]);

  const disconnect = useCallback(() => gateway.current?.disconnect(), []);
  const reconnect = useCallback(() => gateway.current?.connect(), []);

  return { messages, status, sendMessage, disconnect, reconnect };
}
