"use client";

import { useRouter, useSearchParams } from "next/navigation";
import { Suspense, useEffect, useState } from "react";
import { useApp } from "@/components/providers";
import { Button, Card, Fingerprint, Notice } from "@/components/ui";
import type { KeyResponse } from "@/lib/api";
import { forgetDevice } from "@/lib/device-key";
import { session, useSession } from "@/lib/session";

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
  const router = useRouter();
  const welcome = useSearchParams().get("welcome") === "1";
  const [serverKey, setServerKey] = useState<KeyResponse | null>(null);
  const [backedUp, setBackedUp] = useState(false);
  const [backupError, setBackupError] = useState<string | null>(null);

  useEffect(() => {
    if (username) api.getKey(username).then(setServerKey, () => setServerKey(null));
  }, [api, username]);

  /** The same passphrase-locked backup the server keeps, as a file for offline safekeeping or GnuPG. */
  async function exportBackup() {
    setBackupError(null);
    try {
      const { keyBackup } = await api.getKeyBackup();
      saveText(`cipherchat-${username}-private-key-backup.asc`, keyBackup);
      setBackedUp(true);
    } catch (err) {
      setBackupError(err instanceof Error ? err.message : "Could not download your key backup");
    }
  }

  async function signOutAndForget() {
    await forgetDevice(username!);
    session.signOut();
    router.replace("/login");
  }

  const mismatch = serverKey && fingerprint && serverKey.fingerprint !== fingerprint;

  return (
    <main className="mx-auto h-full w-full max-w-3xl space-y-6 overflow-y-auto px-4 py-8">
      {welcome && (
        <Notice tone="warn">
          <strong className="font-medium">Remember your key passphrase.</strong> You will not need it to sign in on this
          browser, but you will need it once on any new device or browser. Nobody, including us, can reset it: without
          it and without a device that is already set up, your messages cannot be decrypted.
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
              Optional. Locked with your passphrase, like the copy the server keeps for new devices. Works with GnuPG.
            </p>
          </div>
        </div>
        {backedUp && (
          <div className="mt-4">
            <Notice tone="ok">Backup downloaded. Keep the file and your passphrase separate.</Notice>
          </div>
        )}
        {backupError && (
          <div className="mt-4">
            <Notice tone="danger">{backupError}</Notice>
          </div>
        )}
      </Card>

      <Card>
        <h2 className="font-medium">This device</h2>
        <p className="mt-2 text-sm leading-relaxed text-muted">
          Your private key is kept in this browser, locked by a device key that the browser never lets anyone read or
          export. That is why you only need your password here. On a shared computer, sign out and forget this device:
          the key is deleted from this browser and the next sign-in here asks for your key passphrase.
        </p>
        <Button variant="secondary" className="mt-4" onClick={signOutAndForget}>
          Sign out and forget this device
        </Button>
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
