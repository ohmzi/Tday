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
 * How long a burst of server events is collected before it costs one refresh.
 *
 * Completing N tasks together emits N `todo` events back to the actor who
 * caused them — one per completion, none of them coalesced server-side. Acting
 * on each one in the handler meant N rounds of the five `todo`-family
 * refetches above, which is the amplifier behind "completing too many tasks
 * together": the batch is already a bounded fan-out
 * (`BULK_MAX_CONCURRENCY = 4`, up to `BULK_MAX_SELECTION = 100`), so the events
 * arrive spread over seconds, and each round is another read of a server that
 * has not yet been told about the whole batch. On top of the `api_global` rate
 * limit — 180 requests per 60 s per user, covering every `/api/` path — N
 * writes plus their own echoes' reads is how a large selection starts answering
 * 429 mid-batch.
 *
 * So the window is a contract, not a tuning constant: a burst costs one refresh
 * round, on every client. iOS already answers one this way
 * (`AppViewModel.scheduleRealtimeSync`, whose doc names this exact case —
 * "someone bulk-completing tasks"), and Android now mirrors it; web is the third.
 * A single isolated event still refreshes, just after this short delay instead
 * of instantly, which is imperceptible for a background refresh — each sync
 * fetches the whole of the affected keys rather than a delta, so the one round
 * that does run picks up everything the burst produced.
 */
export const REALTIME_COALESCE_MS = 250;

/**
 * The ceiling on that collection, so a stream with no gap in it cannot starve
 * the refresh forever.
 *
 * A pure trailing window resets on every event, which is correct for a burst
 * and wrong for a collaborator typing for a minute straight: the pending keys
 * would never flush. Past this much waiting the next event flushes what is
 * already collected instead of extending the window again.
 */
export const REALTIME_COALESCE_MAX_MS = 1_000;

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

    // The burst collector. Keys are the serialized query keys, so the same root
    // arriving from twenty events is one entry and one refetch.
    const pendingKeys = new Set<string>();
    let pendingSince = 0;
    let flushTimer: number | null = null;

    const flush = () => {
      flushTimer = null;
      const keys = Array.from(pendingKeys, (key) => JSON.parse(key) as string[]);
      pendingKeys.clear();
      pendingSince = 0;
      for (const key of keys) {
        void queryClient.invalidateQueries({ queryKey: key });
      }
    };

    const scheduleCoalescedInvalidation = (keys: string[][]) => {
      const now = Date.now();
      if (pendingKeys.size === 0) pendingSince = now;
      for (const key of keys) pendingKeys.add(JSON.stringify(key));
      // A stream with no gap in it: flush what is held rather than pushing the
      // window back again. See REALTIME_COALESCE_MAX_MS.
      if (flushTimer !== null && now - pendingSince >= REALTIME_COALESCE_MAX_MS) {
        window.clearTimeout(flushTimer);
        flush();
        return;
      }
      if (flushTimer !== null) window.clearTimeout(flushTimer);
      flushTimer = window.setTimeout(flush, REALTIME_COALESCE_MS);
    };

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
          // Not JSON — refresh everything, through the same collector as
          // everything else. Nothing invalidates from this handler directly: a
          // burst that did would cost one refresh round per event.
        }
        scheduleCoalescedInvalidation(keysForEvent(type));
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
      if (flushTimer !== null) window.clearTimeout(flushTimer);
      socket?.close();
    };
  }, [queryClient]);

  return null;
}
