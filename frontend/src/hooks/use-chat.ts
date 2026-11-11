"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { ChatGateway, type GatewayStatus, isGatewayConfigured } from "@/services/websocket-service";
import type { ChatMessage } from "@/types/chat";

const currentUser = { id: "alex-perera", name: "Alex Perera" };

export function useChat(initialMessages: ChatMessage[]) {
  const [messages, setMessages] = useState(initialMessages);
  const [status, setStatus] = useState<GatewayStatus>(isGatewayConfigured ? "connecting" : "disconnected");
  const gateway = useRef<ChatGateway | null>(null);

  useEffect(() => {
    if (!isGatewayConfigured) return;
    const service = new ChatGateway({
      onStatusChange: setStatus,
      onMessage: (message) => setMessages((current) => [...current, message]),
    });
    gateway.current = service;
    service.connect();
    return () => service.disconnect();
  }, []);

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
    setMessages((current) => [...current, message]);
    if (gateway.current?.send(message)) {
      setMessages((current) =>
        current.map((item) => (item.messageId === message.messageId ? { ...item, delivery: "sent" } : item)),
      );
    } else {
      setMessages((current) =>
        current.map((item) => (item.messageId === message.messageId ? { ...item, delivery: "failed" } : item)),
      );
    }
  }, []);

  return { messages, status, sendMessage };
}
