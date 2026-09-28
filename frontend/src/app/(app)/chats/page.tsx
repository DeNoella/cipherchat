"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { useApp, useIncomingMessages } from "@/components/providers";
import { Field, LockIcon, Notice, Spinner } from "@/components/ui";
import type { ConversationResponse, UserSummary } from "@/lib/api";

function relativeTime(iso?: string): string {
  if (!iso) return "";
  const date = new Date(iso);
  const sameDay = date.toDateString() === new Date().toDateString();
  return sameDay
    ? date.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })
    : date.toLocaleDateString([], { month: "short", day: "numeric" });
}

export default function ChatsPage() {
  const { api } = useApp();
  const [conversations, setConversations] = useState<ConversationResponse[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [query, setQuery] = useState("");
  const [results, setResults] = useState<UserSummary[]>([]);

  const refresh = useCallback(() => {
    api.conversations().then(setConversations, (err: Error) => setError(err.message));
  }, [api]);

  useEffect(refresh, [refresh]);
  useIncomingMessages(refresh);

  useEffect(() => {
    const q = query.trim();
    if (!/^[A-Za-z0-9_]{1,32}$/.test(q)) return;
    const timer = setTimeout(() => {
      api.searchUsers(q).then(setResults, () => setResults([]));
    }, 250);
    return () => clearTimeout(timer);
  }, [api, query]);

  const showResults = /^[A-Za-z0-9_]{1,32}$/.test(query.trim());

  return (
    <main className="mx-auto h-full w-full max-w-3xl overflow-y-auto px-4 py-8">
      <Field
        label="Start a conversation"
        name="search"
        placeholder="Search by username"
        autoComplete="off"
        value={query}
        onChange={(e) => setQuery(e.target.value)}
      />

      {showResults && (
        <ul className="mt-3 divide-y divide-line rounded-md border border-line">
          {results.length === 0 && <li className="px-4 py-3 text-sm text-faint">No users found</li>}
          {results.map((user) => (
            <li key={user.username}>
              <Link
                href={`/chats/${encodeURIComponent(user.username)}`}
                className="flex items-center justify-between px-4 py-3 text-sm hover:bg-raised"
              >
                <span>{user.username}</span>
                {!user.hasPublicKey && <span className="text-xs text-faint">no key yet</span>}
              </Link>
            </li>
          ))}
        </ul>
      )}

      <h2 className="mt-10 mb-3 text-sm text-muted">Conversations</h2>
      {error && <Notice tone="danger">{error}</Notice>}
      {!conversations && !error && <Spinner />}
      {conversations?.length === 0 && (
        <p className="text-sm text-faint">No conversations yet. Search for someone above to begin.</p>
      )}
      {conversations && conversations.length > 0 && (
        <ul className="divide-y divide-line rounded-md border border-line">
          {conversations.map((c) => (
            <li key={c.id}>
              <Link
                href={`/chats/${encodeURIComponent(c.peer.username)}`}
                className="flex items-center justify-between gap-4 px-4 py-3.5 hover:bg-raised"
              >
                <span className="flex min-w-0 items-center gap-2.5">
                  <LockIcon className="h-3.5 w-3.5 shrink-0 text-faint" />
                  <span className="truncate">{c.peer.username}</span>
                </span>
                <span className="shrink-0 text-xs text-faint">{relativeTime(c.lastMessageAt ?? c.createdAt)}</span>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </main>
  );
}
