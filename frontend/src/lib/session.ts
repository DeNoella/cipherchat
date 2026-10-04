/**
 * Client session state as an external store (works with useSyncExternalStore, so it is
 * hydration-safe). The JWT lives in sessionStorage (tab-scoped, cleared on close); the
 * unlocked private key lives in memory only. After a reload it is unwrapped again from this
 * device's IndexedDB with the non-extractable device key (see device-key.ts), with no prompt.
 */
import { useSyncExternalStore } from "react";
import type { UnlockedKey } from "./crypto";

export interface SessionState {
  token: string | null;
  username: string | null;
  /** Decrypted private key. Memory only; never persisted. */
  privateKey: UnlockedKey | null;
  publicKey: string | null;
  fingerprint: string | null;
}

const STORAGE_KEY = "cipherchat.session";
const EMPTY: SessionState = { token: null, username: null, privateKey: null, publicKey: null, fingerprint: null };

let state: SessionState = EMPTY;
let loaded = false;
const listeners = new Set<() => void>();

function load(): SessionState {
  if (!loaded && typeof window !== "undefined") {
    loaded = true;
    try {
      const saved = JSON.parse(sessionStorage.getItem(STORAGE_KEY) ?? "null");
      if (saved?.token && saved?.username) state = { ...EMPTY, token: saved.token, username: saved.username };
    } catch {
      // Storage unavailable (private mode) or corrupted: start signed out.
    }
  }
  return state;
}

function emit(next: SessionState) {
  state = next;
  try {
    if (next.token) {
      sessionStorage.setItem(STORAGE_KEY, JSON.stringify({ token: next.token, username: next.username }));
    } else {
      sessionStorage.removeItem(STORAGE_KEY);
    }
  } catch {
    // Keep working in memory.
  }
  listeners.forEach((listener) => listener());
}

export const session = {
  get: load,
  subscribe(listener: () => void) {
    listeners.add(listener);
    return () => listeners.delete(listener);
  },
  /** Token only, while a new device is being set up (the key is not unlocked yet). */
  authenticate(token: string, username: string) {
    emit({ ...EMPTY, token, username });
  },
  signIn(token: string, username: string, privateKey: UnlockedKey, publicKey: string, fingerprint: string) {
    emit({ token, username, privateKey, publicKey, fingerprint });
  },
  unlock(privateKey: UnlockedKey, publicKey: string, fingerprint: string) {
    emit({ ...load(), privateKey, publicKey, fingerprint });
  },
  signOut() {
    emit(EMPTY);
  },
};

export function useSession(): SessionState {
  return useSyncExternalStore(session.subscribe, session.get, () => EMPTY);
}

const noopSubscribe = () => () => {};

/** False during server render and hydration, true afterwards. Gate redirects on this. */
export function useHydrated(): boolean {
  return useSyncExternalStore(noopSubscribe, () => true, () => false);
}
