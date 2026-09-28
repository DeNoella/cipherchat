"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useEffect, type ReactNode } from "react";
import { LockIcon, Spinner, cx } from "@/components/ui";
import { session, useHydrated, useSession } from "@/lib/session";

export default function AppLayout({ children }: { children: ReactNode }) {
  const hydrated = useHydrated();
  const { token, privateKey, username } = useSession();
  const router = useRouter();
  const pathname = usePathname();
  const ready = hydrated && token !== null && privateKey !== null;

  useEffect(() => {
    if (!hydrated) return;
    if (!token) router.replace("/login");
    else if (!privateKey) router.replace(`/login?next=${encodeURIComponent(pathname)}`);
  }, [hydrated, token, privateKey, router, pathname]);

  if (!ready) {
    return (
      <div className="flex min-h-screen items-center justify-center">
        <Spinner />
      </div>
    );
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
            <button
              onClick={() => {
                session.signOut();
                router.replace("/login");
              }}
              className="rounded-md px-3 py-1.5 text-muted hover:text-ink"
              title={`Signed in as ${username}`}
            >
              Sign out
            </button>
          </nav>
        </div>
      </header>
      <div className="min-h-0 flex-1">{children}</div>
    </div>
  );
}
