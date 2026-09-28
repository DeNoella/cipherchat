/**
 * All OpenPGP operations run here, in the browser. Private keys and plaintext never leave this
 * module except as ciphertext.
 */
import * as openpgp from "openpgp";

export const MAX_ATTACHMENT_BYTES = 10 * 1024 * 1024;

export type SignatureStatus = "verified" | "invalid" | "unsigned";

/** What we encrypt for each message. Attachment metadata lives here so the server never sees it. */
export interface MessageEnvelope {
  v: 1;
  text: string;
  attachment?: { id: string; name: string; type: string; size: number };
}

export interface GeneratedKeys {
  publicKey: string;
  encryptedPrivateKey: string;
  fingerprint: string;
}

export async function generateKeyPair(username: string, passphrase: string): Promise<GeneratedKeys> {
  const { publicKey, privateKey } = await openpgp.generateKey({
    type: "ecc",
    curve: "curve25519Legacy", // Ed25519 signing + X25519 (ECDH) encryption subkey
    userIDs: [{ name: username }],
    passphrase,
    format: "armored",
  });
  return { publicKey, encryptedPrivateKey: privateKey, fingerprint: await fingerprintOf(publicKey) };
}

/** Throws WrongPassphraseError if the passphrase does not decrypt the key. */
export async function unlockPrivateKey(encryptedPrivateKey: string, passphrase: string) {
  const privateKey = await openpgp.readPrivateKey({ armoredKey: encryptedPrivateKey });
  try {
    return await openpgp.decryptKey({ privateKey, passphrase });
  } catch {
    throw new WrongPassphraseError();
  }
}

export class WrongPassphraseError extends Error {
  constructor() {
    super("Wrong passphrase. Your key could not be unlocked.");
  }
}

export type UnlockedKey = openpgp.PrivateKey;

export async function readPrivateKeyFingerprint(encryptedPrivateKey: string): Promise<string> {
  const key = await openpgp.readPrivateKey({ armoredKey: encryptedPrivateKey });
  return key.getFingerprint().toUpperCase();
}

export async function fingerprintOf(armoredPublicKey: string): Promise<string> {
  const key = await openpgp.readKey({ armoredKey: armoredPublicKey });
  return key.getFingerprint().toUpperCase();
}

/** "ABCD EF01 ..." for easier reading aloud / comparing. */
export function formatFingerprint(fp: string): string {
  return fp.replace(/(.{4})/g, "$1 ").trim();
}

async function readKeys(armored: string[]) {
  return Promise.all(armored.map((armoredKey) => openpgp.readKey({ armoredKey })));
}

/** Encrypts to recipient AND sender (so the sender can re-read their history) and signs. */
export async function encryptEnvelope(
  envelope: MessageEnvelope,
  recipientPublicKey: string,
  senderPublicKey: string,
  signingKey: UnlockedKey,
): Promise<string> {
  return openpgp.encrypt({
    message: await openpgp.createMessage({ text: JSON.stringify(envelope) }),
    encryptionKeys: await readKeys([recipientPublicKey, senderPublicKey]),
    signingKeys: signingKey,
  });
}

async function signatureStatus(signatures: { verified: Promise<unknown> }[]): Promise<SignatureStatus> {
  if (signatures.length === 0) return "unsigned";
  try {
    await signatures[0].verified;
    return "verified";
  } catch {
    return "invalid";
  }
}

export interface DecryptedMessage {
  envelope: MessageEnvelope;
  signature: SignatureStatus;
}

/** Decrypts with our key and verifies the signature against the claimed sender's key only. */
export async function decryptEnvelope(
  armoredMessage: string,
  decryptionKey: UnlockedKey,
  senderPublicKey: string,
): Promise<DecryptedMessage> {
  const { data, signatures } = await openpgp.decrypt({
    message: await openpgp.readMessage({ armoredMessage }),
    decryptionKeys: decryptionKey,
    verificationKeys: await readKeys([senderPublicKey]),
  });
  const parsed = JSON.parse(data as string) as Partial<MessageEnvelope>;
  if (parsed.v !== 1 || typeof parsed.text !== "string") {
    throw new Error("Unsupported message format");
  }
  return { envelope: parsed as MessageEnvelope, signature: await signatureStatus(signatures) };
}

export async function encryptFile(
  bytes: Uint8Array,
  recipientPublicKey: string,
  senderPublicKey: string,
  signingKey: UnlockedKey,
): Promise<Uint8Array> {
  if (bytes.byteLength > MAX_ATTACHMENT_BYTES) {
    throw new Error("File is larger than 10 MB");
  }
  return openpgp.encrypt({
    message: await openpgp.createMessage({ binary: bytes }),
    encryptionKeys: await readKeys([recipientPublicKey, senderPublicKey]),
    signingKeys: signingKey,
    format: "binary",
  });
}

export async function decryptFile(
  encrypted: Uint8Array,
  decryptionKey: UnlockedKey,
  senderPublicKey: string,
): Promise<{ bytes: Uint8Array; signature: SignatureStatus }> {
  const { data, signatures } = await openpgp.decrypt({
    message: await openpgp.readMessage({ binaryMessage: encrypted }),
    decryptionKeys: decryptionKey,
    verificationKeys: await readKeys([senderPublicKey]),
    format: "binary",
  });
  return { bytes: data as Uint8Array, signature: await signatureStatus(signatures) };
}
