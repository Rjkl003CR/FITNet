import type { ChatMessage } from "@/types/chat";

export type GatewayStatus = "connecting" | "connected" | "disconnected" | "error";

export interface GatewayCallbacks {
  onStatusChange: (status: GatewayStatus) => void;
  onMessage: (message: ChatMessage) => void;
}

const gatewayUrl = process.env.NEXT_PUBLIC_GATEWAY_WS_URL;

function isChatMessage(value: unknown): value is ChatMessage {
  if (!value || typeof value !== "object") return false;
  const message = value as Record<string, unknown>;
  return (
    typeof message.messageId === "string" &&
    typeof message.senderId === "string" &&
    typeof message.senderName === "string" &&
    typeof message.content === "string" &&
    typeof message.timestamp === "string" &&
    (message.type === "PRIVATE_MESSAGE" || message.type === "GROUP_MESSAGE" || message.type === "SYSTEM")
  );
}

export class ChatGateway {
  private socket: WebSocket | null = null;
  private reconnectTimer: ReturnType<typeof setTimeout> | null = null;
  private reconnectAttempts = 0;
  private manuallyClosed = false;

  constructor(private readonly callbacks: GatewayCallbacks) {}

  connect() {
    if (typeof window === "undefined" || !gatewayUrl || this.socket?.readyState === WebSocket.OPEN) {
      return;
    }

    this.manuallyClosed = false;
    this.callbacks.onStatusChange("connecting");
    this.socket = new WebSocket(gatewayUrl);
    this.socket.onopen = () => {
      this.reconnectAttempts = 0;
      this.callbacks.onStatusChange("connected");
    };
    this.socket.onmessage = (event) => {
      try {
        const parsed: unknown = JSON.parse(event.data);
        if (isChatMessage(parsed)) this.callbacks.onMessage(parsed);
      } catch {
        this.callbacks.onStatusChange("error");
      }
    };
    this.socket.onerror = () => this.callbacks.onStatusChange("error");
    this.socket.onclose = () => {
      this.socket = null;
      if (this.manuallyClosed) {
        this.callbacks.onStatusChange("disconnected");
        return;
      }
      this.callbacks.onStatusChange("disconnected");
      this.scheduleReconnect();
    };
  }

  send(message: ChatMessage) {
    if (this.socket?.readyState !== WebSocket.OPEN) return false;
    this.socket.send(JSON.stringify(message));
    return true;
  }

  disconnect() {
    this.manuallyClosed = true;
    if (this.reconnectTimer) clearTimeout(this.reconnectTimer);
    this.socket?.close();
    this.socket = null;
  }

  private scheduleReconnect() {
    if (this.reconnectTimer) return;
    const delay = Math.min(1000 * 2 ** this.reconnectAttempts, 10000);
    this.reconnectAttempts += 1;
    this.reconnectTimer = setTimeout(() => {
      this.reconnectTimer = null;
      this.connect();
    }, delay);
  }
}

export const isGatewayConfigured = Boolean(gatewayUrl);
