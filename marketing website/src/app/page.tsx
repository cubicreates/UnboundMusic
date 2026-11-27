import Link from "next/link";
import {
  ArrowRight,
  AudioWaveform,
  BatteryCharging,
  CheckCircle2,
  Download,
  Headphones,
  Mic,
  Music2,
  Radio,
  Scissors,
  Shield,
  Sparkles,
  WifiOff,
  Zap,
} from "lucide-react";

const listenerHighlights = [
  { value: "$0", label: "Forever. No paywalls or trials", accent: "text-unbound-primary" },
  { value: "0", label: "Audio or pop-up ads, ever", accent: "text-unbound-secondary" },
  { value: "4,000+", label: "Headphone sound profiles ready", accent: "text-unbound-tertiary" },
  { value: "53 MB", label: "Featherlight download size", accent: "text-unbound-amber" },
];

const everydaySuperpowers = [
  {
    icon: WifiOff,
    color: "text-unbound-secondary",
    bg: "bg-unbound-secondary/10 border-unbound-secondary/25",
    title: "Never Wastes Mobile Data",
    description:
      "Already have a song saved on your phone? Unbound plays your local copy automatically using zero cellular data—and buffers 30 seconds ahead so subway tunnels never cut the beat.",
  },
  {
    icon: Music2,
    color: "text-unbound-primary",
    bg: "bg-unbound-primary/10 border-unbound-primary/25",
    title: "Keeps Voice Notes Out of Your Shuffle",
    description:
      "Unbound scans your Downloads, Music, WhatsApp, and Telegram folders in seconds, organizing real songs into your music library while tucking short voice memos neatly aside.",
  },
  {
    icon: Scissors,
    color: "text-unbound-rose",
    bg: "bg-unbound-rose/10 border-unbound-rose/25",
    title: "Built-In Ringtone & Tag Studio",
    description:
      "Love a chorus? Trim any track down to the exact second for a custom ringtone or alarm, and fix song titles, artist names, or album covers right inside the app.",
  },
  {
    icon: Zap,
    color: "text-unbound-amber",
    bg: "bg-unbound-amber/10 border-unbound-amber/25",
    title: "Lightweight By Design (53 MB)",
    description:
      "Your phone storage stays yours. Smart AI mood search is completely optional—download it inside the app only if you want it, with a one-tap Cancel anytime.",
  },
  {
    icon: Radio,
    color: "text-unbound-violet",
    bg: "bg-unbound-violet/10 border-unbound-violet/25",
    title: "Share Playlists Nearby Offline",
    description:
      "Road trip or flight with friends? Discover nearby listeners on the same Wi-Fi network and swap playlists and favorites without needing internet access.",
  },
  {
    icon: BatteryCharging,
    color: "text-unbound-tertiary",
    bg: "bg-unbound-tertiary/10 border-unbound-tertiary/25",
    title: "Pitch-Black AMOLED Comfort",
    description:
      "Crafted with deep OLED blacks and five curated visual themes that look stunning at night and go easy on your battery during all-day listening.",
  },
];

export default function HomePage() {
  return (
    <div className="relative overflow-hidden">
      {/* ==================== HERO SECTION ==================== */}
      <section className="mx-auto max-w-7xl px-4 pb-12 pt-10 sm:px-6 sm:pb-16 sm:pt-16 lg:px-8 lg:pt-20">
        <div className="mx-auto max-w-3xl text-center">
          <div className="mb-5 inline-flex items-center gap-2 rounded-full border border-unbound-outline bg-unbound-surfaceLow px-3.5 py-1.5 text-[11px] font-medium text-unbound-onPrimaryContainer shadow-sm sm:mb-6 sm:px-4 sm:text-xs">
            <img src="/assets/logo.png" alt="Unbound" className="h-4 w-4 object-contain" />
            <span>100% Free • Zero Ads • Built for Music Lovers</span>
          </div>

          <h1 className="text-3xl font-extrabold tracking-tight text-unbound-onSurface sm:text-5xl lg:text-6xl lg:leading-[1.08]">
            Every Song You Love.{" "}
            <span className="bg-gradient-to-r from-unbound-primary via-unbound-tertiary to-unbound-secondary bg-clip-text text-transparent">
              Pure Studio Sound.
            </span>{" "}
            Zero Subscriptions.
          </h1>

          <p className="mx-auto mt-5 max-w-2xl text-sm leading-relaxed text-unbound-onSurfaceVariant sm:mt-6 sm:text-lg">
            Stream millions of songs and podcasts, play every MP3 and FLAC already saved on your phone
            without burning mobile data, tune your headphones like a recording studio, and sing along
            with live synced lyrics—completely ad-free.
          </p>

          <div className="mt-8 flex flex-col items-center justify-center gap-3.5 sm:mt-9 sm:flex-row sm:gap-4">
            <Link
              href="/download"
              className="inline-flex min-h-[48px] w-full items-center justify-center gap-2.5 rounded-full bg-unbound-primary px-8 py-3.5 text-base font-bold text-unbound-onPrimary shadow-[0_0_32px_rgba(0,229,255,0.35)] transition-all hover:scale-[1.02] hover:shadow-[0_0_44px_rgba(0,229,255,0.5)] sm:w-auto"
            >
              <Download className="h-5 w-5" />
              <span>Get Unbound Music Free</span>
            </Link>
            <a
              href="#showcase"
              className="inline-flex min-h-[48px] w-full items-center justify-center gap-2 rounded-full border border-unbound-outline bg-unbound-surfaceLow px-7 py-3.5 text-base font-semibold text-unbound-onSurface transition-colors hover:border-unbound-primary/60 hover:bg-unbound-surface sm:w-auto"
            >
              <span>See It in Action</span>
              <ArrowRight className="h-4 w-4 text-unbound-primary" />
            </a>
          </div>

          <div className="mt-6 flex flex-wrap items-center justify-center gap-x-5 gap-y-2 text-xs text-unbound-onSurfaceVariant">
            <span className="inline-flex items-center gap-1.5">
              <CheckCircle2 className="h-3.5 w-3.5 text-unbound-secondary" /> No Account Required
            </span>
            <span className="inline-flex items-center gap-1.5">
              <CheckCircle2 className="h-3.5 w-3.5 text-unbound-secondary" /> Optional YouTube Music Sync
            </span>
            <span className="inline-flex items-center gap-1.5">
              <CheckCircle2 className="h-3.5 w-3.5 text-unbound-secondary" /> Background &amp; Screen-Off Play
            </span>
          </div>
        </div>

        {/* 3-Phone Hero Showcase Stage (Horizontal Snap Carousel on Mobile, 3-Col Stage on Desktop) */}
        <div className="relative mx-auto mt-12 max-w-5xl sm:mt-14">
          <div className="pointer-events-none absolute inset-x-10 top-12 -z-10 h-72 rounded-full bg-gradient-to-r from-unbound-primary/20 via-unbound-violet/15 to-unbound-secondary/20 blur-3xl" />

          <div className="-mx-4 flex snap-x snap-mandatory gap-4 overflow-x-auto px-6 pb-4 md:mx-0 md:grid md:grid-cols-3 md:items-center md:gap-4 md:overflow-visible md:px-0 md:pb-0">
            {/* Left Phone: Home Screen */}
            <div className="w-[72vw] max-w-[260px] shrink-0 snap-center md:mx-auto md:w-[270px] md:-mr-4 md:translate-y-4 md:-rotate-3">
              <div className="rounded-[2.2rem] border border-unbound-outline bg-unbound-oled p-2 shadow-[0_24px_60px_rgba(0,0,0,0.75)] sm:p-2.5">
                <img
                  src="/assets/home-screen-demo.png"
                  alt="Unbound Music Home Screen with Personalized Mixes and Podcasts"
                  className="w-full rounded-[1.7rem] object-cover"
                />
              </div>
            </div>

            {/* Center Phone (Hero Focus): Music Player */}
            <div className="relative z-10 w-[75vw] max-w-[280px] shrink-0 snap-center md:mx-auto md:w-[300px] md:max-w-[300px]">
              <div className="rounded-[2.4rem] border-2 border-unbound-primary/50 bg-unbound-oled p-2 shadow-[0_30px_80px_rgba(0,229,255,0.22)] sm:p-2.5">
                <img
                  src="/assets/music-player-demo.png"
                  alt="Unbound Music Now Playing Screen"
                  className="w-full rounded-[1.85rem] object-cover"
                />
              </div>
            </div>

            {/* Right Phone: Search Screen */}
            <div className="w-[72vw] max-w-[260px] shrink-0 snap-center md:mx-auto md:w-[270px] md:-ml-4 md:translate-y-4 md:rotate-3">
              <div className="rounded-[2.2rem] border border-unbound-outline bg-unbound-oled p-2 shadow-[0_24px_60px_rgba(0,0,0,0.75)] sm:p-2.5">
                <img
                  src="/assets/search-screen-demo.png"
                  alt="Unbound Music Search, Moods, and Song Recognition"
                  className="w-full rounded-[1.7rem] object-cover"
                />
              </div>
            </div>
          </div>
          <p className="mt-1 text-center text-[11px] text-unbound-onSurfaceVariant md:hidden">
            Swipe to preview all screens →
          </p>
        </div>
      </section>

      {/* ==================== BANNER & METRICS STRIP ==================== */}
      <section className="mx-auto max-w-7xl px-4 py-8 sm:px-6 sm:py-10 lg:px-8">
        <div className="overflow-hidden rounded-3xl border border-unbound-outline bg-unbound-surfaceLow shadow-[0_24px_70px_rgba(0,0,0,0.6)]">
          <div className="relative">
            <img
              src="/assets/banner.png"
              alt="Unbound Music Brand Showcase"
              className="min-h-[150px] w-full object-cover"
            />
          </div>
          <div className="grid grid-cols-2 divide-x divide-y divide-unbound-outlineVariant border-t border-unbound-outline bg-unbound-surfaceLowest sm:grid-cols-4 sm:divide-y-0">
            {listenerHighlights.map((stat) => (
              <div key={stat.label} className="px-4 py-5 text-center sm:px-6 sm:py-6">
                <p className={`text-2xl font-extrabold sm:text-3xl ${stat.accent}`}>
                  {stat.value}
                </p>
                <p className="mt-1 text-xs font-medium text-unbound-onSurfaceVariant sm:text-sm">
                  {stat.label}
                </p>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* ==================== 3 LIFESTYLE SHOWCASE SECTIONS ==================== */}
      <section id="showcase" className="mx-auto max-w-7xl space-y-16 px-4 py-12 sm:space-y-24 sm:px-6 sm:py-16 lg:px-8">
        {/* Showcase 1: Home Screen */}
        <div className="grid items-center gap-10 lg:grid-cols-2 lg:gap-12">
          <div className="space-y-5 sm:space-y-6">
            <span className="inline-flex items-center gap-2 rounded-full border border-unbound-primary/30 bg-unbound-primary/10 px-3.5 py-1 text-xs font-semibold text-unbound-primary">
              <Sparkles className="h-3.5 w-3.5" />
              Made For Your Day
            </span>
            <h2 className="text-2xl font-extrabold tracking-tight text-unbound-onSurface sm:text-4xl">
              A Home Screen That Moves With Your Mood
            </h2>
            <p className="text-sm leading-relaxed text-unbound-onSurfaceVariant sm:text-base">
              Jump straight back into your morning playlist, switch between Music and Podcasts with a
              single tap, or explore global and local charts tailored to where you are.
            </p>
            <ul className="space-y-3 text-sm text-unbound-onSurface">
              <li className="flex items-start gap-3">
                <CheckCircle2 className="mt-0.5 h-5 w-5 shrink-0 text-unbound-secondary" />
                <span>
                  <strong className="font-semibold">Use It Privately as a Guest:</strong> Open the app
                  and press play right away—no sign-ups, no nagging accounts.
                </span>
              </li>
              <li className="flex items-start gap-3">
                <CheckCircle2 className="mt-0.5 h-5 w-5 shrink-0 text-unbound-secondary" />
                <span>
                  <strong className="font-semibold">Or Bring Your YouTube Music Library:</strong> Sign in
                  to sync your Liked Songs, personal mixes, and playback history seamlessly across devices.
                </span>
              </li>
              <li className="flex items-start gap-3">
                <CheckCircle2 className="mt-0.5 h-5 w-5 shrink-0 text-unbound-secondary" />
                <span>
                  <strong className="font-semibold">Zero Clutter, Zero Interruptions:</strong> Clean dark
                  design built for one-handed listening on the move.
                </span>
              </li>
            </ul>
          </div>

          <div className="flex justify-center">
            <div className="relative w-full max-w-[260px] rounded-[2.4rem] border border-unbound-outline bg-unbound-oled p-2.5 shadow-[0_24px_70px_rgba(0,229,255,0.12)] sm:max-w-[310px] sm:p-3">
              <img
                src="/assets/home-screen-demo.png"
                alt="Unbound Music Home Feed Demo"
                className="w-full rounded-[1.85rem]"
              />
            </div>
          </div>
        </div>

        {/* Showcase 2: Music Player */}
        <div className="grid items-center gap-10 lg:grid-cols-2 lg:gap-12">
          <div className="order-2 flex justify-center lg:order-1">
            <div className="relative w-full max-w-[260px] rounded-[2.4rem] border border-unbound-primary/40 bg-unbound-oled p-2.5 shadow-[0_24px_70px_rgba(125,255,162,0.12)] sm:max-w-[310px] sm:p-3">
              <img
                src="/assets/music-player-demo.png"
                alt="Unbound Music Player Demo"
                className="w-full rounded-[1.85rem]"
              />
            </div>
          </div>

          <div className="order-1 space-y-5 sm:space-y-6 lg:order-2">
            <span className="inline-flex items-center gap-2 rounded-full border border-unbound-secondary/30 bg-unbound-secondary/10 px-3.5 py-1 text-xs font-semibold text-unbound-secondary">
              <Headphones className="h-3.5 w-3.5" />
              Studio-Grade Sound &amp; Lyrics
            </span>
            <h2 className="text-2xl font-extrabold tracking-tight text-unbound-onSurface sm:text-4xl">
              Feel Every Beat. Sing Every Lyric.
            </h2>
            <p className="text-sm leading-relaxed text-unbound-onSurfaceVariant sm:text-base">
              Pick your exact headphone model from over 4,000 tuned profiles and hear details in your
              favorite songs you never knew were there.
            </p>
            <ul className="space-y-3 text-sm text-unbound-onSurface">
              <li className="flex items-start gap-3">
                <CheckCircle2 className="mt-0.5 h-5 w-5 shrink-0 text-unbound-primary" />
                <span>
                  <strong className="font-semibold">Live Word-by-Word Lyrics:</strong> Uncensored lyrics
                  with instant pronunciation guides for Korean, Japanese, Hindi, Spanish &amp; more.
                </span>
              </li>
              <li className="flex items-start gap-3">
                <CheckCircle2 className="mt-0.5 h-5 w-5 shrink-0 text-unbound-primary" />
                <span>
                  <strong className="font-semibold">Smooth DJ Crossfades &amp; Sleep Timer:</strong> Blend
                  songs seamlessly without awkward silence, or drift off as the volume fades gently.
                </span>
              </li>
              <li className="flex items-start gap-3">
                <CheckCircle2 className="mt-0.5 h-5 w-5 shrink-0 text-unbound-primary" />
                <span>
                  <strong className="font-semibold">Lock-Screen Album Art That Respects Yours:</strong>{" "}
                  Turns your lock screen into a concert poster while playing—and automatically restores
                  your personal photo when you pause or close.
                </span>
              </li>
            </ul>
          </div>
        </div>

        {/* Showcase 3: Search & Recognition */}
        <div className="grid items-center gap-10 lg:grid-cols-2 lg:gap-12">
          <div className="space-y-5 sm:space-y-6">
            <span className="inline-flex items-center gap-2 rounded-full border border-unbound-violet/30 bg-unbound-violet/10 px-3.5 py-1 text-xs font-semibold text-unbound-violet">
              <Mic className="h-3.5 w-3.5" />
              Instant Discovery
            </span>
            <h2 className="text-2xl font-extrabold tracking-tight text-unbound-onSurface sm:text-4xl">
              Name Any Song Around You—Or Just Describe a Vibe
            </h2>
            <p className="text-sm leading-relaxed text-unbound-onSurfaceVariant sm:text-base">
              Heard a song playing at a coffee shop? Remember only three words from the chorus? Or just
              want music that fits your evening? Find it in one tap.
            </p>
            <ul className="space-y-3 text-sm text-unbound-onSurface">
              <li className="flex items-start gap-3">
                <AudioWaveform className="mt-0.5 h-5 w-5 shrink-0 text-unbound-primary" />
                <span>
                  <strong className="font-semibold">Built-In Song Identifier:</strong> Hold your phone up
                  to identify ambient music playing nearby in seconds—no extra app needed.
                </span>
              </li>
              <li className="flex items-start gap-3">
                <Mic className="mt-0.5 h-5 w-5 shrink-0 text-unbound-primary" />
                <span>
                  <strong className="font-semibold">Hum or Speak Lyrics:</strong> Say the line stuck in
                  your head and jump right to the track.
                </span>
              </li>
              <li className="flex items-start gap-3">
                <Sparkles className="mt-0.5 h-5 w-5 shrink-0 text-unbound-primary" />
                <span>
                  <strong className="font-semibold">Smart Mood Search:</strong> Type a feeling like{" "}
                  <em>&ldquo;late night rain drive&rdquo;</em> or <em>&ldquo;gym energy&rdquo;</em> and get
                  an instant radio tailored to your vibe.
                </span>
              </li>
            </ul>
          </div>

          <div className="flex justify-center">
            <div className="relative w-full max-w-[260px] rounded-[2.4rem] border border-unbound-outline bg-unbound-oled p-2.5 shadow-[0_24px_70px_rgba(139,108,255,0.15)] sm:max-w-[310px] sm:p-3">
              <img
                src="/assets/search-screen-demo.png"
                alt="Unbound Music Search & Song Recognition Demo"
                className="w-full rounded-[1.85rem]"
              />
            </div>
          </div>
        </div>
      </section>

      {/* ==================== EVERYDAY SUPERPOWERS GRID ==================== */}
      <section className="mx-auto max-w-7xl px-4 py-12 sm:px-6 sm:py-16 lg:px-8">
        <div className="mx-auto max-w-2xl text-center">
          <h2 className="text-2xl font-extrabold tracking-tight text-unbound-onSurface sm:text-4xl">
            Thoughtful Details You&apos;ll Notice Every Day
          </h2>
          <p className="mt-3 text-sm text-unbound-onSurfaceVariant sm:mt-4 sm:text-base">
            Every corner of Unbound Music was crafted to save your data, protect your privacy, and make
            listening effortless.
          </p>
        </div>

        <div className="mt-10 grid gap-5 sm:mt-12 sm:grid-cols-2 sm:gap-6 lg:grid-cols-3">
          {everydaySuperpowers.map((item) => {
            const Icon = item.icon;
            return (
              <div
                key={item.title}
                className="group rounded-2xl border border-unbound-outline bg-unbound-surfaceLow p-5 transition-all hover:border-unbound-primary/50 hover:bg-unbound-surface sm:p-6"
              >
                <div
                  className={`mb-4 inline-flex h-11 w-11 items-center justify-center rounded-xl border ${item.bg}`}
                >
                  <Icon className={`h-5 w-5 ${item.color}`} />
                </div>
                <h3 className="text-lg font-bold text-unbound-onSurface">{item.title}</h3>
                <p className="mt-2 text-sm leading-relaxed text-unbound-onSurfaceVariant">
                  {item.description}
                </p>
              </div>
            );
          })}
        </div>
      </section>

      {/* ==================== BOTTOM DOWNLOAD CALLOUT ==================== */}
      <section className="mx-auto max-w-7xl px-4 pb-16 pt-6 sm:px-6 sm:pb-20 sm:pt-8 lg:px-8">
        <div className="relative overflow-hidden rounded-3xl border border-unbound-primary/35 bg-gradient-to-br from-unbound-surface via-unbound-surfaceLow to-unbound-surfaceLowest p-6 shadow-[0_24px_80px_rgba(0,229,255,0.12)] sm:p-12">
          <div className="flex flex-col items-center justify-between gap-7 lg:flex-row lg:gap-8">
            <div className="flex flex-col items-center gap-5 text-center sm:flex-row sm:gap-6 sm:text-left">
              <img
                src="/assets/logo-solid.png"
                alt="Unbound Music App Icon"
                className="h-20 w-20 rounded-2xl border border-unbound-outline shadow-lg"
              />
              <div>
                <div className="inline-flex items-center gap-1.5 text-xs font-semibold uppercase tracking-wider text-unbound-secondary">
                  <Shield className="h-3.5 w-3.5" />
                  Privacy-First • Free Forever
                </div>
                <h2 className="mt-1 text-2xl font-extrabold text-unbound-onSurface sm:text-3xl">
                  Ready to Upgrade How You Listen?
                </h2>
                <p className="mt-2 max-w-xl text-sm text-unbound-onSurfaceVariant sm:text-base">
                  Download the lightweight 53 MB Android APK in seconds—no credit card, no ads, and
                  complete control over your music library.
                </p>
              </div>
            </div>

            <div className="flex w-full flex-col gap-3 sm:w-auto sm:flex-row">
              <Link
                href="/download"
                className="inline-flex min-h-[48px] w-full items-center justify-center gap-2.5 rounded-full bg-unbound-primary px-7 py-3.5 text-sm font-bold text-unbound-onPrimary shadow-[0_0_24px_rgba(0,229,255,0.35)] transition-transform hover:scale-[1.02] sm:w-auto"
              >
                <Download className="h-4 w-4" />
                <span>Go to Download Page</span>
              </Link>
              <Link
                href="/api"
                className="inline-flex min-h-[48px] w-full items-center justify-center gap-2 rounded-full border border-unbound-outline bg-unbound-surfaceHigh px-6 py-3.5 text-sm font-semibold text-unbound-onSurface transition-colors hover:border-unbound-primary/50 sm:w-auto"
              >
                <span>Explore API Docs</span>
              </Link>
            </div>
          </div>
        </div>
      </section>
    </div>
  );
}
