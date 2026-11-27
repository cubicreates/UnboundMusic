import { createReadStream, existsSync, statSync } from "node:fs";
import path from "node:path";
import { Readable } from "node:stream";
import { NextResponse } from "next/server";
import { CURRENT_APP_VERSION } from "@/data/version";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

const DOWNLOAD_FILENAME = `Unbound-Music-v${CURRENT_APP_VERSION}.apk`;
const FALLBACK_APK_URL =
  process.env.NEXT_PUBLIC_APK_DOWNLOAD_URL ||
  "https://github.com/cubicreates/UnboundMusic/releases/latest/download/unbound-music-debug.apk";

function resolveLocalApkPath(): string | null {
  const candidates = [
    path.join(process.cwd(), "public", "downloads", DOWNLOAD_FILENAME),
    path.join(process.cwd(), "public", "downloads", "Unbound-Music-v2.0.0.apk"),
  ];
  for (const candidate of candidates) {
    if (existsSync(candidate)) {
      return candidate;
    }
  }
  return null;
}

export async function HEAD() {
  const apkPath = resolveLocalApkPath();
  if (!apkPath) {
    return NextResponse.redirect(FALLBACK_APK_URL, 302);
  }
  const stat = statSync(apkPath);
  return new NextResponse(null, {
    status: 200,
    headers: {
      "Content-Type": "application/vnd.android.package-archive",
      "Content-Disposition": `attachment; filename="${DOWNLOAD_FILENAME}"`,
      "Content-Length": String(stat.size),
      "Cache-Control": "no-store",
    },
  });
}

export async function GET() {
  const apkPath = resolveLocalApkPath();
  if (!apkPath) {
    return NextResponse.redirect(FALLBACK_APK_URL, 302);
  }

  const stat = statSync(apkPath);
  const nodeStream = createReadStream(apkPath);
  const webStream = Readable.toWeb(nodeStream) as unknown as ReadableStream;

  return new NextResponse(webStream, {
    status: 200,
    headers: {
      "Content-Type": "application/vnd.android.package-archive",
      "Content-Disposition": `attachment; filename="${DOWNLOAD_FILENAME}"`,
      "Content-Length": String(stat.size),
      "Cache-Control": "no-store",
    },
  });
}
