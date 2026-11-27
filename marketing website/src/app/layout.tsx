import type { Metadata } from "next";
import { Plus_Jakarta_Sans, JetBrains_Mono } from "next/font/google";
import "./globals.css";
import { Navbar } from "@/components/Navbar";
import { Footer } from "@/components/Footer";

const plusJakarta = Plus_Jakarta_Sans({
  subsets: ["latin"],
  variable: "--font-sans",
  display: "swap",
});

const jetbrainsMono = JetBrains_Mono({
  subsets: ["latin"],
  variable: "--font-mono",
  display: "swap",
});

export const metadata: Metadata = {
  metadataBase: new URL(
    process.env.NEXT_PUBLIC_SITE_URL || "https://unboundmusic.vercel.app"
  ),
  title: "Unbound Music — Pure Sound, Zero Ads, Zero Subscriptions",
  description:
    "Stream millions of tracks & podcasts, listen offline with zero wasted mobile data, match sound to 4,000+ headphones, and sing along with live lyrics—free forever on Android.",
  icons: {
    icon: "/favicon.ico",
  },
  openGraph: {
    title: "Unbound Music — Pure Sound, Zero Subscriptions",
    description:
      "Your all-in-one Android music player for streaming, offline vaults, live lyrics, song recognition, and studio-grade headphone tuning.",
    images: ["/assets/banner.png"],
  },
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="en" className={`${plusJakarta.variable} ${jetbrainsMono.variable}`}>
      <body className="min-h-screen bg-unbound-bg font-sans text-unbound-onSurface antialiased studio-grid-bg">
        <div className="flex min-h-screen flex-col">
          <Navbar />
          <main className="flex-1">{children}</main>
          <Footer />
        </div>
      </body>
    </html>
  );
}
