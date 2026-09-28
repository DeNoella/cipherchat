/**
 * Fetches a contact's public key and applies trust-on-first-use: the first fingerprint we see
 * is pinned locally, and any later change is surfaced to the user instead of silently accepted.
 * This limits what a compromised server could do by swapping keys.
 */
import type { Api } from "./api";
import { fingerprintOf } from "./crypto";
import { contacts } from "./keystore";

export type Trust = "new" | "known" | "verified" | "changed";

export interface PeerKey {
  username: string;
  publicKey: string;
  /** Computed locally from the key itself; we do not rely on the server-reported value. */
  fingerprint: string;
  pinnedFingerprint?: string;
  trust: Trust;
}

export async function loadPeerKey(api: Api, owner: string, peer: string): Promise<PeerKey> {
  const { publicKey } = await api.getKey(peer);
  const fingerprint = await fingerprintOf(publicKey);
  const pinned = await contacts.get(owner, peer);

  if (!pinned) {
    await contacts.put(owner, peer, fingerprint, false);
    return { username: peer, publicKey, fingerprint, trust: "new" };
  }
  if (pinned.fingerprint !== fingerprint) {
    return { username: peer, publicKey, fingerprint, pinnedFingerprint: pinned.fingerprint, trust: "changed" };
  }
  return { username: peer, publicKey, fingerprint, trust: pinned.verified ? "verified" : "known" };
}

export async function markVerified(owner: string, peer: PeerKey): Promise<PeerKey> {
  await contacts.put(owner, peer.username, peer.fingerprint, true);
  return { ...peer, pinnedFingerprint: undefined, trust: "verified" };
}

export async function acceptNewKey(owner: string, peer: PeerKey): Promise<PeerKey> {
  await contacts.put(owner, peer.username, peer.fingerprint, false);
  return { ...peer, pinnedFingerprint: undefined, trust: "known" };
}
