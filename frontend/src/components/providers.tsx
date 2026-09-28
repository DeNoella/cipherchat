"use client";

import { Client } from "@stomp/stompjs";
import { useRouter } from "next/navigation";
import { createContext, useContext, useEffect, useMemo, useRef, type ReactNode } from "react";
import { createApi, type Api, type MessageResponse } from "@/lib/api";
import { session, useSession } from "@/lib/session";

interface AppContextValue {
  api: Api;
  wsUrl: string;
}

const AppContext = createContext<AppContextValue | null>(null);

export function Providers({ apiUrl, wsUrl, children }: { apiUrl: string; wsUrl: string; children: ReactNode }) {
  const router = useRouter();
  const value = useMemo(
    () => ({
      api: createApi(
        apiUrl,
        () => session.get().token,
        () => {
          session.signOut();
          router.replace("/login?expired=1");
        },
      ),
      wsUrl,
    }),
    [apiUrl, wsUrl, router],
  );
  return <AppContext.Provider value={value}>{children}</AppContext.Provider>;
}

export function useApp(): AppContextValue {
  const ctx = useContext(AppContext);
  if (!ctx) throw new Error("useApp must be used inside <Providers>");
  return ctx;
}

/**
 * Subscribes to real-time ciphertext pushed over STOMP. The JWT goes in the CONNECT frame,
 * never in the URL. The latest handler is kept in a ref so reconnects are not triggered by re-renders.
 */
export function useIncomingMessages(onMessage: (message: MessageResponse) => void) {
  const { wsUrl } = useApp();
  const { token } = useSession();
  const handler = useRef(onMessage);

  useEffect(() => {
    handler.current = onMessage;
  });

  useEffect(() => {
    if (!token) return;
    const client = new Client({
      brokerURL: wsUrl,
      connectHeaders: { Authorization: `Bearer ${token}` },
      reconnectDelay: 5000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      onConnect: () => {
        client.subscribe("/user/queue/messages", (frame) => {
          try {
            handler.current(JSON.parse(frame.body) as MessageResponse);
          } catch {
            // Ignore malformed frames.
          }
        });
      },
    });
    client.activate();
    return () => {
      void client.deactivate();
    };
  }, [token, wsUrl]);
}
