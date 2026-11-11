import { Wifi, WifiOff } from "lucide-react";
import type { GatewayStatus } from "@/services/websocket-service";

export function ConnectionStatus({ status }: { status: GatewayStatus }) {
  const connected = status === "connected";
  return <div className={`flex items-center gap-2 rounded-lg border px-3 py-2 text-[11px] font-semibold ${connected ? "border-emerald-100 bg-emerald-50 text-emerald-700" : "border-amber-100 bg-amber-50 text-amber-700"}`}><span className={`h-1.5 w-1.5 rounded-full ${connected ? "bg-emerald-500" : "bg-amber-500"}`} />{connected ? "Connected to gateway" : status === "connecting" ? "Connecting..." : "Demo mode"}{connected ? <Wifi size={14} /> : <WifiOff size={14} />}</div>;
}
