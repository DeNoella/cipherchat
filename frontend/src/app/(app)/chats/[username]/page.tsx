"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { useCallback, useEffect, useRef, useState, type FormEvent, type KeyboardEvent } from "react";
import { useApp, useIncomingMessages } from "@/components/providers";
import { Button, Fingerprint, LockIcon, Notice, SignatureBadge, Spinner, cx } from "@/components/ui";
import { ApiError, type MessageResponse } from "@/lib/api";
import {
  MAX_ATTACHMENT_BYTES,
  decryptEnvelope,
  decryptFile,
  encryptEnvelope,
  encryptFile,
  type MessageEnvelope,
  type SignatureStatus,
} from "@/lib/crypto";
import { acceptNewKey, loadPeerKey, markVerified, type PeerKey } from "@/lib/peers";
import { useSession } from "@/lib/session";

interface ChatItem {
  id: number;
  sender: string;
  createdAt: string;
  envelope?: MessageEnvelope;
  signature?: SignatureStatus;
  failed?: boolean;
}

function formatBytes(n: number) {
  return n < 1024 * 1024 ? `${Math.max(1, Math.round(n / 1024))} KB` : `${(n / 1024 / 1024).toFixed(1)} MB`;
}

/** Keep only characters that are safe in a download filename. */
function safeFileName(name: string) {
  return name.replace(/[^\w.\- ]+/g, "_").slice(0, 120) || "attachment";
}

export default function ChatPage() {
  const params = useParams<{ username: string }>();
  const peerName = decodeURIComponent(params.username).toLowerCase();
  const { api } = useApp();
  const { username: me, privateKey, publicKey: myPublicKey } = useSession();

  const [peer, setPeer] = useState<PeerKey | null>(null);
  const [conversationId, setConversationId] = useState<number | null>(null);
  const [items, setItems] = useState<ChatItem[]>([]);
  const [hasMore, setHasMore] = useState(false);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [sendError, setSendError] = useState<string | null>(null);
  const [text, setText] = useState("");
  const [file, setFile] = useState<File | null>(null);
  const [sending, setSending] = useState(false);
  const [showKey, setShowKey] = useState(false);
  const bottomRef = useRef<HTMLDivElement>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const decrypt = useCallback(
    async (m: MessageResponse, peerKey: PeerKey): Promise<ChatItem> => {
      const senderKey = m.sender === me ? myPublicKey! : peerKey.publicKey;
      try {
        const { envelope, signature } = await decryptEnvelope(m.ciphertext, privateKey!, senderKey);
        return { id: m.id, sender: m.sender, createdAt: m.createdAt, envelope, signature };
      } catch {
        return { id: m.id, sender: m.sender, createdAt: m.createdAt, failed: true };
      }
    },
    [me, myPublicKey, privateKey],
  );

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const peerKey = await loadPeerKey(api, me!, peerName);
        const conversation = await api.startConversation(peerName);
        const history = await api.messages(conversation.id);
        const decrypted = await Promise.all(history.map((m) => decrypt(m, peerKey)));
        if (cancelled) return;
        setPeer(peerKey);
        setConversationId(conversation.id);
        setItems(decrypted);
        setHasMore(history.length === 50);
      } catch (err) {
        if (cancelled) return;
        if (err instanceof ApiError && err.status === 404) {
          setLoadError(
            err.message === "User not found"
              ? `There is no user called “${peerName}”.`
              : `${peerName} has not set up encryption yet, so messages cannot be encrypted for them.`,
          );
        } else {
          setLoadError(err instanceof Error ? err.message : "Could not load the conversation");
        }
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [api, me, peerName, decrypt]);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ block: "end" });
  }, [items.length]);

  useIncomingMessages(async (m) => {
    if (!peer || m.conversationId !== conversationId) return;
    const item = await decrypt(m, peer);
    setItems((current) => (current.some((i) => i.id === item.id) ? current : [...current, item]));
  });

  async function loadOlder() {
    if (!peer || conversationId === null || items.length === 0) return;
    const older = await api.messages(conversationId, items[0].id);
    const decrypted = await Promise.all(older.map((m) => decrypt(m, peer)));
    setItems((current) => [...decrypted, ...current]);
    setHasMore(older.length === 50);
  }

  async function send(event?: FormEvent) {
    event?.preventDefault();
    if (!peer || !privateKey || !myPublicKey || sending) return;
    const body = text.trim();
    if (!body && !file) return;
    setSending(true);
    setSendError(null);
    try {
      let attachment: MessageEnvelope["attachment"];
      if (file) {
        if (file.size > MAX_ATTACHMENT_BYTES) throw new Error("Attachments must be 10 MB or smaller");
        const encrypted = await encryptFile(new Uint8Array(await file.arrayBuffer()), peer.publicKey, myPublicKey, privateKey);
        const uploaded = await api.uploadAttachment(peer.username, encrypted);
        attachment = { id: uploaded.id, name: file.name, type: file.type, size: file.size };
      }
      const envelope: MessageEnvelope = { v: 1, text: body, ...(attachment ? { attachment } : {}) };
      const ciphertext = await encryptEnvelope(envelope, peer.publicKey, myPublicKey, privateKey);
      const saved = await api.sendMessage(peer.username, ciphertext, attachment?.id);
      const item: ChatItem = { id: saved.id, sender: me!, createdAt: saved.createdAt, envelope, signature: "verified" };
      setItems((current) => (current.some((i) => i.id === item.id) ? current : [...current, item]));
      setText("");
      setFile(null);
      if (fileInputRef.current) fileInputRef.current.value = "";
    } catch (err) {
      setSendError(err instanceof Error ? err.message : "Could not send");
    } finally {
      setSending(false);
    }
  }

  function onKeyDown(event: KeyboardEvent<HTMLTextAreaElement>) {
    if (event.key === "Enter" && !event.shiftKey) {
      event.preventDefault();
      void send();
    }
  }

  async function download(item: ChatItem) {
    const meta = item.envelope?.attachment;
    if (!meta || !peer) return;
    try {
      const encrypted = await api.downloadAttachment(meta.id);
      const senderKey = item.sender === me ? myPublicKey! : peer.publicKey;
      const { bytes, signature } = await decryptFile(encrypted, privateKey!, senderKey);
      if (signature !== "verified" && !confirm("This file's signature could not be verified. Download anyway?")) return;
      const url = URL.createObjectURL(new Blob([bytes as BlobPart], { type: "application/octet-stream" }));
      const link = document.createElement("a");
      link.href = url;
      link.download = safeFileName(meta.name);
      link.click();
      setTimeout(() => URL.revokeObjectURL(url), 1000);
    } catch (err) {
      setSendError(err instanceof Error ? `Attachment: ${err.message}` : "Could not decrypt attachment");
    }
  }

  if (loadError) {
    return (
      <main className="mx-auto w-full max-w-3xl px-4 py-8">
        <Notice tone="warn">{loadError}</Notice>
        <Link href="/chats" className="mt-4 inline-block text-sm text-muted hover:text-ink">
          ← Back to chats
        </Link>
      </main>
    );
  }

  if (!peer) {
    return (
      <main className="mx-auto w-full max-w-3xl px-4 py-8">
        <Spinner label="Decrypting conversation" />
      </main>
    );
  }

  const blocked = peer.trust === "changed";

  return (
    <main className="mx-auto flex h-full w-full max-w-3xl flex-col">
      <div className="border-b border-line px-4 py-3">
        <div className="flex items-center justify-between gap-3">
          <div className="flex min-w-0 items-center gap-3">
            <Link href="/chats" className="text-muted hover:text-ink" aria-label="Back to chats">
              ←
            </Link>
            <span className="truncate font-medium">{peer.username}</span>
            {peer.trust === "verified" && (
              <span className="rounded border border-accent/40 px-1.5 py-0.5 text-[11px] text-accent">verified key</span>
            )}
          </div>
          <button onClick={() => setShowKey((v) => !v)} className="shrink-0 text-xs text-muted hover:text-ink">
            {showKey ? "Hide key" : "Key fingerprint"}
          </button>
        </div>

        {showKey && (
          <div className="mt-3 space-y-3 rounded-md border border-line bg-surface p-3">
            <Fingerprint value={peer.fingerprint} />
            <p className="text-xs leading-relaxed text-faint">
              Compare this with the fingerprint {peer.username} sees on their profile, in person or over a call. If
              they match, nobody (including the server) has swapped their key.
            </p>
            {peer.trust !== "verified" && peer.trust !== "changed" && (
              <Button variant="secondary" className="text-xs" onClick={async () => setPeer(await markVerified(me!, peer))}>
                Fingerprints match — mark verified
              </Button>
            )}
          </div>
        )}

        {blocked && (
          <div className="mt-3 space-y-3">
            <Notice tone="warn">
              <strong className="font-medium">{peer.username}&apos;s key has changed.</strong> This happens if they
              reset their key, but it could also mean someone is intercepting. Verify the new fingerprint with them
              before sending.
              <span className="mt-2 block text-xs text-muted">Previously: </span>
              <Fingerprint value={peer.pinnedFingerprint!} className="text-faint" />
              <span className="mt-2 block text-xs text-muted">Now: </span>
              <Fingerprint value={peer.fingerprint} />
            </Notice>
            <Button variant="secondary" className="text-xs" onClick={async () => setPeer(await acceptNewKey(me!, peer))}>
              I have verified it — use the new key
            </Button>
          </div>
        )}
      </div>

      <div className="min-h-0 flex-1 overflow-y-auto px-4 py-6">
        {hasMore && (
          <div className="mb-6 text-center">
            <Button variant="ghost" className="text-xs" onClick={loadOlder}>
              Load earlier messages
            </Button>
          </div>
        )}
        {items.length === 0 && (
          <p className="mt-10 text-center text-sm text-faint">
            <LockIcon className="mr-1.5 inline h-3.5 w-3.5 align-[-2px]" />
            Messages are end-to-end encrypted. Say hello.
          </p>
        )}
        <ol className="space-y-3">
          {items.map((item) => {
            const mine = item.sender === me;
            return (
              <li key={item.id} className={cx("flex", mine ? "justify-end" : "justify-start")}>
                <div
                  className={cx(
                    "max-w-[85%] rounded-lg border px-3.5 py-2.5 sm:max-w-[70%]",
                    mine ? "border-line-strong bg-raised" : "border-line bg-surface",
                  )}
                >
                  {item.failed ? (
                    <p className="text-sm text-danger">This message could not be decrypted with your key.</p>
                  ) : (
                    <>
                      {item.envelope?.text && (
                        <p className="whitespace-pre-wrap break-words text-sm leading-relaxed">{item.envelope.text}</p>
                      )}
                      {item.envelope?.attachment && (
                        <button
                          onClick={() => download(item)}
                          className={cx(
                            "flex w-full items-center gap-2 rounded-md border border-line px-3 py-2 text-left text-sm hover:bg-base/40",
                            item.envelope.text && "mt-2",
                          )}
                        >
                          <LockIcon className="h-3.5 w-3.5 shrink-0 text-faint" />
                          <span className="min-w-0 flex-1 truncate">{item.envelope.attachment.name}</span>
                          <span className="shrink-0 text-xs text-faint">{formatBytes(item.envelope.attachment.size)}</span>
                        </button>
                      )}
                    </>
                  )}
                  <div className="mt-1.5 flex items-center gap-2.5 text-[11px] text-faint">
                    <LockIcon className="h-3 w-3" />
                    <time dateTime={item.createdAt}>
                      {new Date(item.createdAt).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })}
                    </time>
                    {item.signature && <SignatureBadge status={item.signature} />}
                  </div>
                </div>
              </li>
            );
          })}
        </ol>
        <div ref={bottomRef} />
      </div>

      <form onSubmit={send} className="border-t border-line px-4 py-3">
        {sendError && (
          <div className="mb-2">
            <Notice tone="danger">{sendError}</Notice>
          </div>
        )}
        {file && (
          <div className="mb-2 flex items-center justify-between rounded-md border border-line px-3 py-1.5 text-xs text-muted">
            <span className="truncate">
              {file.name} · {formatBytes(file.size)}
            </span>
            <button
              type="button"
              onClick={() => {
                setFile(null);
                if (fileInputRef.current) fileInputRef.current.value = "";
              }}
              className="ml-3 hover:text-ink"
            >
              Remove
            </button>
          </div>
        )}
        <div className="flex items-end gap-2">
          <label
            className={cx(
              "flex h-10 w-10 shrink-0 cursor-pointer items-center justify-center rounded-md border border-line text-muted hover:text-ink",
              blocked && "pointer-events-none opacity-50",
            )}
            title="Attach a file (max 10 MB)"
          >
            <span aria-hidden>+</span>
            <span className="sr-only">Attach a file</span>
            <input
              ref={fileInputRef}
              type="file"
              className="sr-only"
              disabled={blocked}
              onChange={(e) => {
                const chosen = e.target.files?.[0] ?? null;
                if (chosen && chosen.size > MAX_ATTACHMENT_BYTES) {
                  setSendError("Attachments must be 10 MB or smaller");
                  e.target.value = "";
                  return;
                }
                setSendError(null);
                setFile(chosen);
              }}
            />
          </label>
          <textarea
            value={text}
            onChange={(e) => setText(e.target.value)}
            onKeyDown={onKeyDown}
            rows={1}
            maxLength={20000}
            disabled={blocked}
            placeholder={blocked ? "Verify the new key to continue" : "Write an encrypted message"}
            aria-label="Message"
            className="max-h-40 min-h-10 flex-1 resize-none rounded-md border border-line bg-surface px-3 py-2 text-sm placeholder:text-faint focus:border-accent focus:outline-none"
          />
          <Button type="submit" disabled={blocked || sending || (!text.trim() && !file)} className="h-10">
            {sending ? "Encrypting…" : "Send"}
          </Button>
        </div>
      </form>
    </main>
  );
}
