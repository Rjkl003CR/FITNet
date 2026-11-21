import { Wifi, WifiOff } from "lucide-react";
import type { GatewayStatus } from "@/services/websocket-service";

export function ConnectionStatus({ status }: { status: GatewayStatus }) {
  const connected = status === "connected";
  const connecting = status === "connecting";
  return <div title={connected ? "WebSocket gateway connected" : "WebSocket gateway unavailable"} className={`flex items-center gap-2 rounded-full border px-3 py-1.5 text-[11px] font-semibold ${connected ? "border-emerald-100 bg-emerald-50 text-emerald-700" : connecting ? "border-violet-100 bg-violet-50 text-violet-700" : "border-slate-200 bg-slate-50 text-slate-500"}`}><span className={`h-1.5 w-1.5 rounded-full ${connected ? "bg-emerald-500" : connecting ? "animate-pulse bg-violet-500" : "bg-slate-400"}`} />{connected ? "Connected" : connecting ? "Connecting" : "Offline"}{connected ? <Wifi size={14} /> : <WifiOff size={14} />}</div>;
}
