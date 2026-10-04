/**
 * Keeps the OpenPGP private key on this device without a passphrase prompt on every login.
 *
 * The decrypted key is encrypted with AES-GCM under a device key created with `extractable: false`
 * and stored in IndexedDB. The browser never reveals that key's bytes, so the stored data cannot be
 * turned back into a private key outside this browser profile. The passphrase is needed only to
 * set up a new device from the backup (see login page).
 */
import { readUnlockedKey, serializeUnlockedKey, type UnlockedKey } from "./crypto";
import { keystore } from "./keystore";

export interface DeviceKey {
  privateKey: UnlockedKey;
  publicKey: string;
  fingerprint: string;
}

export class MissingDeviceKeyError extends Error {
  constructor() {
    super(
      "Your private key is not on this browser any more (its site data may have been cleared). " +
        "Sign in again and enter your key passphrase to set this browser up.",
    );
  }
}

/** Binds the ciphertext to the account and key, so records cannot be swapped between accounts. */
function associatedData(username: string, fingerprint: string) {
  return new TextEncoder().encode(`cipherchat:${username}:${fingerprint}`);
}

/** Wraps the unlocked key with a fresh non-extractable device key and stores it for `username`. */
export async function protectOnDevice(username: string, key: DeviceKey): Promise<void> {
  const deviceKey = await crypto.subtle.generateKey({ name: "AES-GCM", length: 256 }, false, ["encrypt", "decrypt"]);
  const iv = crypto.getRandomValues(new Uint8Array(12));
  const plain = serializeUnlockedKey(key.privateKey);
  try {
    const wrappedPrivateKey = await crypto.subtle.encrypt(
      { name: "AES-GCM", iv, additionalData: associatedData(username, key.fingerprint) },
      deviceKey,
      plain as BufferSource,
    );
    await keystore.put({
      username,
      publicKey: key.publicKey,
      fingerprint: key.fingerprint,
      deviceKey,
      iv,
      wrappedPrivateKey,
      createdAt: new Date().toISOString(),
    });
  } finally {
    plain.fill(0); // best effort: write() returns a copy, so this does not touch the key in memory
  }
}

/**
 * Unwraps this device's key for `username`. Returns null when the device has no key for the
 * account, or only a key that no longer matches `expectedFingerprint` (stale; it is removed).
 */
export async function unlockFromDevice(username: string, expectedFingerprint?: string | null): Promise<DeviceKey | null> {
  const record = await keystore.get(username).catch(() => undefined);
  if (!record?.deviceKey) return null;
  if (expectedFingerprint && record.fingerprint !== expectedFingerprint) {
    await forgetDevice(username);
    return null;
  }
  let plain: Uint8Array;
  try {
    plain = new Uint8Array(
      await crypto.subtle.decrypt(
        { name: "AES-GCM", iv: record.iv as BufferSource, additionalData: associatedData(username, record.fingerprint) },
        record.deviceKey,
        record.wrappedPrivateKey,
      ),
    );
  } catch {
    // Corrupted or tampered record: treat the device as not set up.
    await forgetDevice(username);
    return null;
  }
  // Do not zero `plain`: OpenPGP.js keeps views into this buffer as the key's secret material.
  const privateKey = await readUnlockedKey(plain);
  if (privateKey.getFingerprint().toUpperCase() !== record.fingerprint) return null;
  return { privateKey, publicKey: record.publicKey, fingerprint: record.fingerprint };
}

/** "Forget this device": deletes the wrapped key and its device key from this browser. */
export async function forgetDevice(username: string): Promise<void> {
  await keystore.remove(username).catch(() => undefined);
}
