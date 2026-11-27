"use client";

import { useEffect, useState } from "react";
import Image from "next/image";
import {
  CheckCircle2,
  Download,
  Lock,
  ShieldCheck,
  Smartphone,
  Sparkles,
  Star,
  Trash2,
  X,
} from "lucide-react";
import { CURRENT_APP_VERSION } from "@/data/version";

const APK_URL = "/download/apk";

const screenshots = [
  {
    src: "/assets/home-screen-demo.png",
    title: "Home & Podcasts",
    caption: "Personalized mixes, quick picks, and podcasts in one clean view",
  },
  {
    src: "/assets/music-player-demo.png",
    title: "Now Playing",
    caption: "Full-screen artwork, equalizer tuning, and live synced lyrics",
  },
  {
    src: "/assets/search-screen-demo.png",
    title: "Search & Song Finder",
    caption: "Find songs by title, lyrics, mood, or sound playing nearby",
  },
  {
    src: "/assets/banner.png",
    title: "Unbound Music",
    caption: "Ad-free music built for everyday listening",
  },
];

export function DownloadListing() {
  const [appVersion, setAppVersion] = useState(CURRENT_APP_VERSION);
  const [downloadState, setDownloadState] = useState<"idle" | "downloading" | "done">("idle");
  const [activeShot, setActiveShot] = useState<(typeof screenshots)[number] | null>(null);

  useEffect(() => {
    let active = true;
    fetch("/api/version", { cache: "no-store" })
      .then((res) => (res.ok ? res.json() : null))
      .then((data) => {
        if (active && data && typeof data.version === "string" && data.version.trim()) {
          setAppVersion(data.version.trim());
        }
      })
      .catch(() => {});
    return () => {
      active = false;
    };
  }, []);

  const apkFilename = `Unbound-Music-v${appVersion}.apk`;

  function handleDownloadClick() {
    setDownloadState("downloading");
    setTimeout(() => {
      setDownloadState("done");
    }, 1200);
  }

  return (
    <div className="mx-auto max-w-6xl px-4 py-8 sm:px-6 sm:py-10 lg:px-8">
      {/* ==================== PLAY STORE HEADER CARD ==================== */}
      <section className="rounded-3xl border border-unbound-outline bg-unbound-surfaceLow p-5 shadow-[0_24px_80px_rgba(0,0,0,0.65)] sm:p-10">
        <div className="flex flex-col gap-6 lg:flex-row lg:items-center lg:justify-between">
          {/* App Identity (Horizontal Play Store Alignment on Mobile too) */}
          <div className="flex items-center gap-4 sm:gap-6">
            <Image
              src="/assets/logo-solid.png"
              alt="Unbound Music App Icon"
              width={112}
              height={112}
              className="h-20 w-20 shrink-0 rounded-2xl border border-unbound-outline bg-unbound-oled object-cover shadow-[0_12px_36px_rgba(0,229,255,0.2)] sm:h-28 sm:w-28 sm:rounded-3xl"
            />
            <div className="min-w-0">
              <h1 className="text-2xl font-extrabold tracking-tight text-unbound-onSurface sm:text-4xl">
                Unbound Music
              </h1>
              <p className="mt-0.5 text-xs font-semibold text-unbound-primary sm:mt-1 sm:text-sm">
                CubiCreates • Music &amp; Audio
              </p>
              <p className="mt-1.5 max-w-xl text-xs leading-relaxed text-unbound-onSurfaceVariant sm:mt-2 sm:text-sm">
                Stream music and podcasts, play your offline library, and enjoy live lyrics—completely
                free and ad-free.
              </p>
            </div>
          </div>

          {/* Single Primary Download Button */}
          <div className="flex w-full flex-col gap-2.5 lg:w-auto lg:min-w-[270px]">
            <a
              href={APK_URL}
              download={apkFilename}
              onClick={handleDownloadClick}
              className="inline-flex min-h-[52px] w-full items-center justify-center gap-2.5 rounded-2xl bg-unbound-primary px-8 py-3.5 text-base font-bold text-unbound-onPrimary shadow-[0_0_28px_rgba(0,229,255,0.35)] transition-all hover:scale-[1.01] hover:shadow-[0_0_40px_rgba(0,229,255,0.5)]"
            >
              {downloadState === "done" ? (
                <CheckCircle2 className="h-5 w-5" />
              ) : (
                <Download className="h-5 w-5" />
              )}
              <span>
                {downloadState === "idle" && "Download App (53 MB)"}
                {downloadState === "downloading" && "Downloading..."}
                {downloadState === "done" && "Download Started"}
              </span>
            </a>

            {downloadState !== "idle" && (
              <p className="text-center text-xs text-unbound-secondary">
                Saving {apkFilename}. Open the file once finished to install on your phone.
              </p>
            )}
          </div>
        </div>

        {/* Play Store Metrics Strip */}
        <div className="mt-7 grid grid-cols-2 divide-x divide-y divide-unbound-outlineVariant rounded-2xl border border-unbound-outline bg-unbound-surfaceLowest sm:mt-8 sm:grid-cols-4 sm:divide-y-0">
          <div className="p-3.5 text-center sm:p-4">
            <div className="flex items-center justify-center gap-1 text-base font-bold text-unbound-onSurface sm:text-lg">
              <span>4.9</span>
              <Star className="h-4 w-4 fill-unbound-amber text-unbound-amber" />
            </div>
            <p className="mt-0.5 text-[11px] text-unbound-onSurfaceVariant sm:text-xs">Listener rating</p>
          </div>
          <div className="p-3.5 text-center sm:p-4">
            <p className="text-base font-bold text-unbound-primary sm:text-lg">53 MB</p>
            <p className="mt-0.5 text-[11px] text-unbound-onSurfaceVariant sm:text-xs">Download size</p>
          </div>
          <div className="p-3.5 text-center sm:p-4">
            <p className="text-base font-bold text-unbound-onSurface sm:text-lg">Android 8.0+</p>
            <p className="mt-0.5 text-[11px] text-unbound-onSurfaceVariant sm:text-xs">Works on most phones</p>
          </div>
          <div className="p-3.5 text-center sm:p-4">
            <p className="text-base font-bold text-unbound-secondary sm:text-lg">Free</p>
            <p className="mt-0.5 text-[11px] text-unbound-onSurfaceVariant sm:text-xs">No ads • No subscriptions</p>
          </div>
        </div>
      </section>

      {/* ==================== PLAY STORE SCREENSHOT REEL ==================== */}
      <section className="mt-9 sm:mt-10">
        <div className="mb-4 flex items-center justify-between">
          <h2 className="text-lg font-bold text-unbound-onSurface sm:text-xl">App Screenshots</h2>
          <span className="text-xs text-unbound-onSurfaceVariant">Tap any image to view larger</span>
        </div>

        <div className="-mx-4 flex snap-x snap-mandatory gap-4 overflow-x-auto px-4 pb-4 sm:mx-0 sm:gap-5 sm:px-0">
          {screenshots.map((shot) => (
            <button
              key={shot.src}
              type="button"
              onClick={() => setActiveShot(shot)}
              className="group shrink-0 snap-start overflow-hidden rounded-2xl border border-unbound-outline bg-unbound-surfaceLow p-2 text-left transition-all hover:border-unbound-primary/60 sm:p-2.5"
            >
              <Image
                src={shot.src}
                alt={shot.title}
                width={shot.src.includes("banner") ? 540 : 180}
                height={360}
                className={`rounded-xl object-cover ${
                  shot.src.includes("banner")
                    ? "h-[260px] w-[82vw] max-w-[440px] sm:h-[360px] sm:w-[540px] sm:max-w-none"
                    : "h-[260px] w-[135px] sm:h-[360px] sm:w-[180px]"
                }`}
              />
              <div className="mt-2.5 px-1">
                <p className="text-xs font-semibold text-unbound-onSurface group-hover:text-unbound-primary">
                  {shot.title}
                </p>
                <p className="mt-0.5 max-w-[160px] truncate text-[11px] text-unbound-onSurfaceVariant sm:max-w-[200px]">
                  {shot.caption}
                </p>
              </div>
            </button>
          ))}
        </div>
      </section>

      {/* ==================== ABOUT, INSTALL GUIDE & DATA SAFETY ==================== */}
      <section className="mt-8 grid gap-6 sm:mt-10 lg:grid-cols-3 lg:gap-8">
        <div className="space-y-6 lg:col-span-2">
          {/* About this app */}
          <div className="rounded-2xl border border-unbound-outline bg-unbound-surfaceLow p-5 sm:p-6">
            <h2 className="text-lg font-bold text-unbound-onSurface sm:text-xl">About this app</h2>
            <p className="mt-3 text-sm leading-relaxed text-unbound-onSurfaceVariant">
              Unbound Music brings together streaming music, podcasts, your saved offline songs, custom
              headphone sound profiles, and live synced lyrics in one clean dark-mode Android app.
              Listen immediately without an account, or sign in with YouTube Music to keep your liked
              songs, playlists, and listening history together.
            </p>
            <div className="mt-5 flex flex-wrap gap-2">
              {[
                "Music Streaming",
                "Offline Playback",
                "Podcasts",
                "Live Lyrics",
                "Equalizer",
                "Song Identifier",
              ].map((tag) => (
                <span
                  key={tag}
                  className="rounded-full border border-unbound-outline bg-unbound-surface px-3 py-1 text-xs font-medium text-unbound-onSurface"
                >
                  {tag}
                </span>
              ))}
            </div>
          </div>

          {/* Easy 3-Step Installation Guide */}
          <div className="rounded-2xl border border-unbound-outline bg-unbound-surfaceLow p-5 sm:p-6">
            <h2 className="text-lg font-bold text-unbound-onSurface sm:text-xl">
              Quick 3-Step Installation
            </h2>
            <div className="mt-4 grid gap-4 sm:grid-cols-3">
              {[
                {
                  step: "01",
                  title: "Download the file",
                  desc: "Tap the Download App button above to save the installer to your Android phone.",
                },
                {
                  step: "02",
                  title: "Open & confirm",
                  desc: "Tap the downloaded file in your notifications or Downloads folder, and allow installation if prompted.",
                },
                {
                  step: "03",
                  title: "Open & listen",
                  desc: "Launch Unbound Music from your home screen and start listening right away.",
                },
              ].map((item) => (
                <div
                  key={item.step}
                  className="rounded-xl border border-unbound-outlineVariant bg-unbound-surfaceLowest p-4"
                >
                  <span className="font-mono text-xs font-bold text-unbound-primary">
                    STEP {item.step}
                  </span>
                  <h3 className="mt-1 text-sm font-bold text-unbound-onSurface">{item.title}</h3>
                  <p className="mt-1 text-xs leading-relaxed text-unbound-onSurfaceVariant">
                    {item.desc}
                  </p>
                </div>
              ))}
            </div>
          </div>

          {/* What's new */}
          <div className="rounded-2xl border border-unbound-outline bg-unbound-surfaceLow p-5 sm:p-6">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <h2 className="text-lg font-bold text-unbound-onSurface sm:text-xl">
                What&apos;s new in v{appVersion}
              </h2>
              <span className="inline-flex items-center gap-1 rounded-full bg-unbound-primary/15 px-3 py-1 text-xs font-semibold text-unbound-primary">
                <Sparkles className="h-3.5 w-3.5" /> Latest Release
              </span>
            </div>
            <ul className="mt-4 space-y-2.5 text-sm text-unbound-onSurfaceVariant">
              <li className="flex items-start gap-2.5">
                <CheckCircle2 className="mt-0.5 h-4 w-4 shrink-0 text-unbound-secondary" />
                <span>
                  <strong className="text-unbound-onSurface">Lighter &amp; Faster Download:</strong>{" "}
                  Streamlined to 53 MB so it installs quickly and saves space on your phone.
                </span>
              </li>
              <li className="flex items-start gap-2.5">
                <CheckCircle2 className="mt-0.5 h-4 w-4 shrink-0 text-unbound-secondary" />
                <span>
                  <strong className="text-unbound-onSurface">Smoother Account Sync:</strong>{" "}
                  Brings over your Google/YouTube Music profile name, photo, liked songs, and mixes cleanly.
                </span>
              </li>
              <li className="flex items-start gap-2.5">
                <CheckCircle2 className="mt-0.5 h-4 w-4 shrink-0 text-unbound-secondary" />
                <span>
                  <strong className="text-unbound-onSurface">Lock-Screen Wallpaper Restore:</strong>{" "}
                  Shows album art while music plays and automatically puts your personal lock-screen photo back when you stop.
                </span>
              </li>
              <li className="flex items-start gap-2.5">
                <CheckCircle2 className="mt-0.5 h-4 w-4 shrink-0 text-unbound-secondary" />
                <span>
                  <strong className="text-unbound-onSurface">Clean Startup Experience:</strong>{" "}
                  Updated splash screen and a quiet, distraction-free interface with zero pop-up clutter.
                </span>
              </li>
            </ul>
          </div>
        </div>

        {/* Right Column: Data Safety & Simple App Info */}
        <div className="space-y-6">
          <div className="rounded-2xl border border-unbound-outline bg-unbound-surfaceLow p-5 sm:p-6">
            <div className="flex items-center gap-2.5">
              <ShieldCheck className="h-5 w-5 text-unbound-secondary" />
              <h2 className="text-lg font-bold text-unbound-onSurface">Data safety</h2>
            </div>
            <p className="mt-2 text-xs leading-relaxed text-unbound-onSurfaceVariant">
              Unbound Music is built to respect your privacy from the ground up.
            </p>
            <div className="mt-4 space-y-3 text-xs">
              <div className="flex items-start gap-2.5 rounded-xl border border-unbound-outlineVariant bg-unbound-surfaceLowest p-3">
                <Lock className="mt-0.5 h-4 w-4 shrink-0 text-unbound-secondary" />
                <div>
                  <p className="font-semibold text-unbound-onSurface">No third-party ads or tracking</p>
                  <p className="mt-0.5 text-unbound-onSurfaceVariant">
                    We never sell your listening habits or show ad pop-ups.
                  </p>
                </div>
              </div>
              <div className="flex items-start gap-2.5 rounded-xl border border-unbound-outlineVariant bg-unbound-surfaceLowest p-3">
                <Smartphone className="mt-0.5 h-4 w-4 shrink-0 text-unbound-primary" />
                <div>
                  <p className="font-semibold text-unbound-onSurface">Stays private on your phone</p>
                  <p className="mt-0.5 text-unbound-onSurfaceVariant">
                    Your playlists, favorites, and offline downloads remain under your control.
                  </p>
                </div>
              </div>
              <div className="flex items-start gap-2.5 rounded-xl border border-unbound-outlineVariant bg-unbound-surfaceLowest p-3">
                <Trash2 className="mt-0.5 h-4 w-4 shrink-0 text-unbound-tertiary" />
                <div>
                  <p className="font-semibold text-unbound-onSurface">Easy cleanup anytime</p>
                  <p className="mt-0.5 text-unbound-onSurfaceVariant">
                    Clear cached songs or remove app data in one tap from Settings.
                  </p>
                </div>
              </div>
            </div>
          </div>

          <div className="rounded-2xl border border-unbound-outline bg-unbound-surfaceLow p-5 sm:p-6">
            <h2 className="text-base font-bold text-unbound-onSurface">App info</h2>
            <dl className="mt-4 space-y-3 text-xs">
              <div className="flex justify-between gap-4">
                <dt className="text-unbound-onSurfaceVariant">Version</dt>
                <dd className="font-mono font-medium text-unbound-primary">{appVersion}</dd>
              </div>
              <div className="flex justify-between gap-4">
                <dt className="text-unbound-onSurfaceVariant">Requires Android</dt>
                <dd className="font-medium text-unbound-onSurface">8.0 and up</dd>
              </div>
              <div className="flex justify-between gap-4">
                <dt className="text-unbound-onSurfaceVariant">Download size</dt>
                <dd className="font-medium text-unbound-onSurface">53 MB</dd>
              </div>
              <div className="flex justify-between gap-4">
                <dt className="text-unbound-onSurfaceVariant">Price</dt>
                <dd className="font-medium text-unbound-secondary">Free</dd>
              </div>
              <div className="flex justify-between gap-4">
                <dt className="text-unbound-onSurfaceVariant">Offered by</dt>
                <dd className="font-medium text-unbound-onSurface">CubiCreates</dd>
              </div>
            </dl>
          </div>
        </div>
      </section>

      {/* Screenshot Lightbox Modal */}
      {activeShot && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/85 p-4 backdrop-blur-md"
          onClick={() => setActiveShot(null)}
        >
          <div
            className="relative max-h-[90vh] w-full max-w-3xl overflow-hidden rounded-3xl border border-unbound-outline bg-unbound-surfaceLow p-4"
            onClick={(e) => e.stopPropagation()}
          >
            <button
              type="button"
              onClick={() => setActiveShot(null)}
              className="absolute right-4 top-4 rounded-full border border-unbound-outline bg-unbound-surfaceHigh p-2.5 text-unbound-onSurface hover:border-unbound-primary"
              aria-label="Close preview"
            >
              <X className="h-4 w-4" />
            </button>
            <Image
              src={activeShot.src}
              alt={activeShot.title}
              width={activeShot.src.includes("banner") ? 1200 : 540}
              height={960}
              className="mx-auto max-h-[72vh] w-auto rounded-2xl object-contain"
            />
            <div className="mt-3 text-center">
              <p className="text-sm font-bold text-unbound-onSurface">{activeShot.title}</p>
              <p className="text-xs text-unbound-onSurfaceVariant">{activeShot.caption}</p>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
