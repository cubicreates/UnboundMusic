import Link from "next/link";
import { ShieldCheck, Radio } from "lucide-react";

export function Footer() {
  return (
    <footer className="border-t border-unbound-outline/70 bg-unbound-surfaceLowest">
      <div className="mx-auto max-w-7xl px-4 py-10 sm:px-6 sm:py-12 lg:px-8">
        <div className="grid gap-8 text-center md:grid-cols-3 md:items-center md:text-left">
          <div className="flex flex-col items-center gap-3 sm:flex-row md:items-center">
            <img
              src="/assets/logo.png"
              alt="Unbound Music"
              className="h-10 w-10 rounded-xl border border-unbound-outline bg-unbound-surfaceLow p-1.5 object-contain"
            />
            <div>
              <p className="text-base font-bold text-unbound-onSurface">
                Unbound Music
              </p>
              <p className="text-xs text-unbound-onSurfaceVariant">
                Your music, unbound. Pure sound, zero ads, zero subscriptions.
              </p>
            </div>
          </div>

          <div className="flex flex-wrap items-center justify-center gap-6 text-sm text-unbound-onSurfaceVariant">
            <Link href="/" className="py-1 transition-colors hover:text-unbound-primary">
              Home
            </Link>
            <Link href="/download" className="py-1 transition-colors hover:text-unbound-primary">
              Download App
            </Link>
            <Link href="/api" className="py-1 transition-colors hover:text-unbound-primary">
              API Docs
            </Link>
          </div>

          <div className="flex flex-wrap items-center justify-center gap-3 md:justify-end">
            <span className="inline-flex items-center gap-1.5 rounded-full border border-unbound-outline bg-unbound-surfaceLow px-3 py-1 text-xs font-medium text-unbound-secondary">
              <ShieldCheck className="h-3.5 w-3.5" />
              Zero Tracking
            </span>
            <span className="inline-flex items-center gap-1.5 rounded-full border border-unbound-outline bg-unbound-surfaceLow px-3 py-1 text-xs font-medium text-unbound-tertiary">
              <Radio className="h-3.5 w-3.5" />
              Android 8.0+
            </span>
          </div>
        </div>

        <div className="mt-8 border-t border-unbound-outlineVariant pt-6 text-center text-xs text-unbound-onSurfaceVariant">
          © {new Date().getFullYear()} Unbound Music • Built for listeners who want their music back.
        </div>
      </div>
    </footer>
  );
}
