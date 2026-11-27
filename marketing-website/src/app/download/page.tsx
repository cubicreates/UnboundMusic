import type { Metadata } from "next";
import { DownloadListing } from "@/components/DownloadListing";
import { CURRENT_APP_VERSION } from "@/data/version";

export const metadata: Metadata = {
  title: `Download Unbound Music — Official Android APK (v${CURRENT_APP_VERSION})`,
  description: `Download Unbound Music v${CURRENT_APP_VERSION} for Android (53 MB). Ad-free music streaming, offline vault, equalizer tuning, and synced lyrics.`,
};

export default function DownloadPage() {
  return <DownloadListing />;
}
