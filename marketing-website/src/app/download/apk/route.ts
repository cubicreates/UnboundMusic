import { NextResponse } from "next/server";

export const dynamic = "force-dynamic";

const APK_DOWNLOAD_URL =
  process.env.NEXT_PUBLIC_APK_DOWNLOAD_URL ||
  "https://github.com/cubicreates/UnboundMusic/releases/latest/download/unboundmusic_V10271126.apk";

export async function HEAD() {
  return NextResponse.redirect(APK_DOWNLOAD_URL, 302);
}

export async function GET() {
  return NextResponse.redirect(APK_DOWNLOAD_URL, 302);
}
