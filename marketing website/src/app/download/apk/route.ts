import { createReadStream, existsSync, statSync } from "node:fs";
import path from "node:path";
import { Readable } from "node:stream";
import { NextResponse } from "next/server";
import { CURRENT_APP_VERSION } from "@/data/version";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

const DOWNLOAD_FILENAME = `Unbound-Music-v${CURRENT_APP_VERSION}.apk`;

function resolveApkPath(): string | null {
  const candidates = [
    path.join(process.cwd(), "public", "downloads", DOWNLOAD_FILENAME),
    path.join(process.cwd(), "public", "downloads", "Unbound-Music-v2.0.0.apk"),
    path.resolve(process.cwd(), "..", "apk_test", "unbound-music-debug.apk"),
  ];
  for (const candidate of candidates) {
    if (existsSync(candidate)) {
      return candidate;
    }
  }
  return null;
}

export async function HEAD() {
  const apkPath = resolveApkPath();
  if (!apkPath) {
    return new NextResponse(null, { status: 404 });
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
  const apkPath = resolveApkPath();
  if (!apkPath) {
    return NextResponse.json(
      { error: "APK binary not found on server" },
      { status: 404 }
    );
  }

  const stat = statSync(apkPath);
  const nodeStream = createReadStream(apkPath);
  const webStream = Readable.toWeb(nodeStream) as ReadableStream;

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
