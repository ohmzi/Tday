import { useEffect } from "react";
import { useQueryClient } from "@tanstack/react-query";

// Query keys refreshed when the server pushes a change event. Events are
// lightweight "something changed, refetch" signals (see backend DomainEvent),
// so invalidating per event-family keeps collaborators' views live without a
// payload protocol.
const EVENT_QUERY_KEYS: Record<string, string[][]> = {
  todo: [["todo"], ["todoTimeline"], ["overdueTodo"], ["calendarTodo"], ["list"]],
  floater: [["floater"], ["floaterList"]],
  list: [["listMetaData"], ["list"], ["todo"], ["todoTimeline"], ["overdueTodo"], ["listMembers"]],
  floaterList: [["floaterListMetaData"], ["floaterList"], ["floater"], ["listMembers"]],
  completed: [["completedTodo"], ["completedFloater"]],
};

const ALL_QUERY_KEYS = Object.values(EVENT_QUERY_KEYS).flat();

function keysForEvent(type: string): string[][] {
  const family = type.split(".")[0]?.toLowerCase();
  switch (family) {
    case "todo":
      return EVENT_QUERY_KEYS.todo;
    case "floater":
      return EVENT_QUERY_KEYS.floater;
    case "list":
      return EVENT_QUERY_KEYS.list;
    case "floaterlist":
      return EVENT_QUERY_KEYS.floaterList;
    case "completed":
      return EVENT_QUERY_KEYS.completed;
    default:
      return ALL_QUERY_KEYS;
  }
}

/**
 * Maintains a /ws connection while mounted (i.e. while the authed app shell is
 * up) and invalidates the affected queries on every server event. Reconnects
 * with capped backoff; the session cookie authenticates the socket.
 */
export default function RealtimeInvalidator() {
  const queryClient = useQueryClient();

  useEffect(() => {
    let socket: WebSocket | null = null;
    let reconnectTimer: number | null = null;
    let attempts = 0;
    let disposed = false;

    const connect = () => {
      if (disposed) return;
      const protocol = window.location.protocol === "https:" ? "wss:" : "ws:";
      try {
        socket = new WebSocket(`${protocol}//${window.location.host}/ws`);
      } catch {
        scheduleReconnect();
        return;
      }

      socket.onopen = () => {
        attempts = 0;
      };
      socket.onmessage = (message) => {
        let type = "";
        try {
          const payload = JSON.parse(String(message.data)) as { type?: string; event?: string };
          type = payload.type ?? payload.event ?? "";
        } catch {
          // Not JSON — refresh everything below.
        }
        for (const key of keysForEvent(type)) {
          void queryClient.invalidateQueries({ queryKey: key });
        }
      };
      socket.onclose = () => {
        socket = null;
        scheduleReconnect();
      };
      socket.onerror = () => {
        socket?.close();
      };
    };

    const scheduleReconnect = () => {
      if (disposed || reconnectTimer !== null) return;
      const delay = Math.min(30_000, 1_000 * 2 ** attempts);
      attempts += 1;
      reconnectTimer = window.setTimeout(() => {
        reconnectTimer = null;
        connect();
      }, delay);
    };

    connect();

    return () => {
      disposed = true;
      if (reconnectTimer !== null) window.clearTimeout(reconnectTimer);
      socket?.close();
    };
  }, [queryClient]);

  return null;
}
