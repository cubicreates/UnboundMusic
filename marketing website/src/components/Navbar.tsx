"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { Download, Menu, Sparkles, Terminal, X } from "lucide-react";

const navItems = [
  { label: "Home", href: "/", icon: Sparkles },
  { label: "Download", href: "/download", icon: Download },
  { label: "API", href: "/api-docs", icon: Terminal },
];

export function Navbar() {
  const pathname = usePathname();
  const [mobileOpen, setMobileOpen] = useState(false);

  useEffect(() => {
    setMobileOpen(false);
  }, [pathname]);

  return (
    <header className="sticky top-0 z-50 w-full border-b border-unbound-outline/70 bg-[#0C0E10]/90 backdrop-blur-xl">
      <div className="mx-auto flex h-16 max-w-7xl items-center justify-between px-4 sm:px-6 md:grid md:grid-cols-[1fr_auto_1fr] lg:px-8">
        {/* Left: Brand Identity */}
        <Link href="/" className="group flex shrink-0 items-center gap-2.5 justify-self-start">
          <div className="flex h-9 w-9 items-center justify-center rounded-xl border border-unbound-outline bg-unbound-surfaceLow p-1 transition-colors group-hover:border-unbound-primary/60">
            <img
              src="/assets/logo.png"
              alt="Unbound Music Logo"
              className="h-full w-full object-contain"
            />
          </div>
          <div className="flex flex-col">
            <span className="text-base font-bold tracking-tight text-unbound-onSurface">
              Unbound Music
            </span>
            <span className="hidden text-[10px] font-medium uppercase tracking-widest text-unbound-onSurfaceVariant sm:block">
              Pure Sound • Zero Limits
            </span>
          </div>
        </Link>

        {/* Center: Desktop Central Navigation Bar */}
        <nav
          aria-label="Central Navigation"
          className="hidden shrink-0 items-center rounded-full border border-unbound-outline bg-unbound-surfaceLow p-1 shadow-[0_8px_30px_rgba(0,0,0,0.45)] justify-self-center md:flex"
        >
          {navItems.map((item) => {
            const Icon = item.icon;
            const isActive =
              item.href === "/"
                ? pathname === "/"
                : pathname?.startsWith(item.href);

            return (
              <Link
                key={item.href}
                href={item.href}
                className={`flex min-h-[38px] items-center gap-1.5 rounded-full px-5 py-1.5 text-sm font-medium transition-all ${
                  isActive
                    ? "bg-unbound-primary font-semibold text-unbound-onPrimary shadow-[0_0_20px_rgba(0,229,255,0.35)]"
                    : "text-unbound-onSurfaceVariant hover:bg-unbound-surfaceHigh hover:text-unbound-onSurface"
                }`}
              >
                <Icon className="h-3.5 w-3.5" />
                <span>{item.label}</span>
              </Link>
            );
          })}
        </nav>

        {/* Right: Mobile Hamburger Button / Desktop Spacer */}
        <button
          type="button"
          onClick={() => setMobileOpen((prev) => !prev)}
          aria-expanded={mobileOpen}
          aria-label="Toggle navigation menu"
          className="inline-flex h-10 w-10 items-center justify-center rounded-xl border border-unbound-outline bg-unbound-surfaceLow text-unbound-onSurface transition-colors hover:border-unbound-primary/60 md:hidden"
        >
          {mobileOpen ? <X className="h-5 w-5" /> : <Menu className="h-5 w-5" />}
        </button>

        <div className="hidden md:block" aria-hidden="true" />
      </div>

      {/* Mobile Hamburger Dropdown & Backdrop */}
      {mobileOpen && (
        <>
          <div
            className="fixed inset-x-0 bottom-0 top-16 z-40 bg-black/65 backdrop-blur-sm md:hidden"
            onClick={() => setMobileOpen(false)}
          />
          <nav
            aria-label="Mobile Navigation"
            className="absolute inset-x-0 top-16 z-50 border-b border-unbound-outline bg-[#0C0E10] p-4 shadow-2xl md:hidden"
          >
            <div className="space-y-2">
              {navItems.map((item) => {
                const Icon = item.icon;
                const isActive =
                  item.href === "/"
                    ? pathname === "/"
                    : pathname?.startsWith(item.href);

                return (
                  <Link
                    key={item.href}
                    href={item.href}
                    onClick={() => setMobileOpen(false)}
                    className={`flex min-h-[48px] w-full items-center gap-3 rounded-xl px-4 py-3 text-base font-medium transition-colors ${
                      isActive
                        ? "bg-unbound-primary font-semibold text-unbound-onPrimary"
                        : "bg-unbound-surfaceLow text-unbound-onSurface hover:bg-unbound-surfaceHigh"
                    }`}
                  >
                    <Icon className="h-4 w-4 shrink-0" />
                    <span>{item.label}</span>
                  </Link>
                );
              })}
            </div>
          </nav>
        </>
      )}
    </header>
  );
}
