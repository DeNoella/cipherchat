"use client";

import { useSearchParams } from "next/navigation";
import { Suspense, useEffect, useState } from "react";
import { useApp } from "@/components/providers";
import { Button, Card, Fingerprint, Notice } from "@/components/ui";
import type { KeyResponse } from "@/lib/api";
import { keystore } from "@/lib/keystore";
import { useSession } from "@/lib/session";

function saveText(filename: string, contents: string) {
  const url = URL.createObjectURL(new Blob([contents], { type: "application/pgp-keys" }));
  const link = document.createElement("a");
  link.href = url;
  link.download = filename;
  link.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}

function Profile() {
  const { api } = useApp();
  const { username, fingerprint, publicKey } = useSession();
  const welcome = useSearchParams().get("welcome") === "1";
  const [serverKey, setServerKey] = useState<KeyResponse | null>(null);
  const [backedUp, setBackedUp] = useState(false);

  useEffect(() => {
    if (username) api.getKey(username).then(setServerKey, () => setServerKey(null));
  }, [api, username]);

  async function exportBackup() {
    const stored = await keystore.get(username!);
    if (!stored) return;
    saveText(`cipherchat-${username}-private-key-backup.asc`, stored.encryptedPrivateKey);
    setBackedUp(true);
  }

  const mismatch = serverKey && fingerprint && serverKey.fingerprint !== fingerprint;

  return (
    <main className="mx-auto h-full w-full max-w-3xl space-y-6 overflow-y-auto px-4 py-8">
      {welcome && !backedUp && (
        <Notice tone="warn">
          <strong className="font-medium">Back up your key now.</strong> It exists only in this browser. If you clear
          your browser data or switch devices without a backup, your messages cannot be decrypted, and nobody,
          including us, can recover them.
        </Notice>
      )}

      <Card>
        <p className="text-sm text-muted">Signed in as</p>
        <h1 className="mt-1 text-xl font-medium">{username}</h1>
      </Card>

      <Card>
        <h2 className="font-medium">Your key fingerprint</h2>
        <p className="mt-2 text-sm leading-relaxed text-muted">
          Share this with contacts through another channel (in person, a call). When it matches what they see, they
          know messages really come from you.
        </p>
        <div className="mt-4 rounded-md border border-line bg-base p-4">
          <Fingerprint value={fingerprint ?? ""} className="text-sm text-ink" />
        </div>
        {serverKey && (
          <p className="mt-3 text-xs text-faint">
            {serverKey.algorithm} · uploaded {new Date(serverKey.uploadedAt).toLocaleDateString()}
          </p>
        )}
        {mismatch && (
          <div className="mt-4">
            <Notice tone="danger">
              The server has a different key on file for you than the one on this device. Messages sent to you may be
              unreadable. Re-upload your public key or contact support.
            </Notice>
          </div>
        )}
      </Card>

      <Card>
        <h2 className="font-medium">Keys</h2>
        <div className="mt-4 grid gap-4 sm:grid-cols-2">
          <div className="space-y-2">
            <Button variant="secondary" className="w-full" onClick={() => saveText(`cipherchat-${username}-public.asc`, publicKey!)}>
              Export public key
            </Button>
            <p className="text-xs leading-relaxed text-faint">Safe to share with anyone.</p>
          </div>
          <div className="space-y-2">
            <Button variant="secondary" className="w-full" onClick={exportBackup}>
              Export private key backup
            </Button>
            <p className="text-xs leading-relaxed text-faint">
              Encrypted with your passphrase. Store it somewhere safe; you need it to sign in on another device.
            </p>
          </div>
        </div>
        {backedUp && (
          <div className="mt-4">
            <Notice tone="ok">Backup downloaded. Keep the file and your passphrase separate.</Notice>
          </div>
        )}
      </Card>
    </main>
  );
}

export default function ProfilePage() {
  return (
    <Suspense>
      <Profile />
    </Suspense>
  );
}
