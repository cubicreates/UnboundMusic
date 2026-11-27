import { NextResponse } from "next/server";
import { CURRENT_APP_VERSION } from "@/data/version";

export const dynamic = "force-dynamic";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "GET, OPTIONS",
  "Access-Control-Allow-Headers": "Content-Type",
  "Cache-Control": "no-store",
};

export async function OPTIONS() {
  return new NextResponse(null, {
    status: 204,
    headers: corsHeaders,
  });
}

export async function GET() {
  return NextResponse.json(
    { version: CURRENT_APP_VERSION },
    { status: 200, headers: corsHeaders }
  );
}
