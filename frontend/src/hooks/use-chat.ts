"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { ChatGateway, type GatewayStatus, isGatewayConfigured } from "@/services/websocket-service";
import type { ChatMessage } from "@/types/chat";

const currentUser = { id: "alex-perera", name: "Alex Perera" };

export function useChat() {
  const [messages, setMessages] = useState<ChatMessage[]>([]);
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

  const sendMessage = useCallback((content: string, receiverId: string | null, groupId: string | null) => {
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
    };
    upsertMessage(message);
    if (gateway.current?.send(message)) {
      upsertMessage(message, "sent");
    } else {
      upsertMessage(message, "failed");
    }
  }, [upsertMessage]);

  return { messages, status, sendMessage };
}
