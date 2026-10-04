"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useEffect, useRef, type ReactNode } from "react";
import { LockIcon, Spinner, cx } from "@/components/ui";
import { forgetDevice, unlockFromDevice } from "@/lib/device-key";
import { session, useHydrated, useSession } from "@/lib/session";

export default function AppLayout({ children }: { children: ReactNode }) {
  const hydrated = useHydrated();
  const { token, privateKey, username } = useSession();
  const router = useRouter();
  const pathname = usePathname();
  const ready = hydrated && token !== null && privateKey !== null;
  const restoring = useRef(false);

  useEffect(() => {
    if (!hydrated) return;
    if (!token) {
      router.replace("/login");
      return;
    }
    if (privateKey || !username || restoring.current) return;
    // After a reload the key is gone from memory: unwrap it again with this device's key. No prompt.
    restoring.current = true;
    unlockFromDevice(username)
      .then((local) => {
        if (local) {
          session.unlock(local.privateKey, local.publicKey, local.fingerprint);
        } else {
          // The login page ends this half-signed-in session (signing out here would race this redirect).
          router.replace(`/login?device=missing&next=${encodeURIComponent(pathname)}`);
        }
      })
      .finally(() => {
        restoring.current = false;
      });
  }, [hydrated, token, privateKey, username, router, pathname]);

  if (!ready) {
    return (
      <div className="flex min-h-screen items-center justify-center">
        <Spinner />
      </div>
    );
  }

  async function signOut(forget: boolean) {
    if (forget && username) await forgetDevice(username);
    session.signOut();
    router.replace("/login");
  }

  const nav = [
    { href: "/chats", label: "Chats" },
    { href: "/profile", label: "Profile" },
  ];

  return (
    <div className="flex h-dvh flex-col">
      <header className="border-b border-line">
        <div className="mx-auto flex h-14 w-full max-w-3xl items-center justify-between px-4">
          <Link href="/chats" className="flex items-center gap-2">
            <LockIcon className="h-4 w-4 text-accent" />
            <span className="font-medium tracking-tight">CipherChat</span>
          </Link>
          <nav className="flex items-center gap-1 text-sm">
            {nav.map((item) => (
              <Link
                key={item.href}
                href={item.href}
                className={cx(
                  "rounded-md px-3 py-1.5",
                  pathname.startsWith(item.href) ? "text-ink" : "text-muted hover:text-ink",
                )}
              >
                {item.label}
              </Link>
            ))}
            <details className="relative">
              <summary
                className="cursor-pointer list-none rounded-md px-3 py-1.5 text-muted hover:text-ink [&::-webkit-details-marker]:hidden"
                title={`Signed in as ${username}`}
              >
                Sign out
              </summary>
              <div className="absolute right-0 z-10 mt-2 w-64 rounded-md border border-line bg-surface p-1 shadow-sm">
                <button
                  onClick={() => signOut(false)}
                  className="block w-full rounded px-3 py-2 text-left text-ink hover:bg-raised"
                >
                  Sign out
                  <span className="block text-xs text-faint">Keep my key on this browser</span>
                </button>
                <button
                  onClick={() => signOut(true)}
                  className="block w-full rounded px-3 py-2 text-left text-ink hover:bg-raised"
                >
                  Sign out and forget this device
                  <span className="block text-xs text-faint">Next sign-in here asks for your key passphrase</span>
                </button>
              </div>
            </details>
          </nav>
        </div>
      </header>
      <div className="min-h-0 flex-1">{children}</div>
    </div>
  );
}
