import { config } from "@/lib/cloud-server";

export const dynamic = "force-dynamic";

export function GET() {
  try {
    config();
    return Response.json({ app: "Portal", status: "ready" }, { headers: { "Cache-Control": "no-store" } });
  } catch {
    return Response.json({ app: "Portal", status: "not-configured" }, { status: 503, headers: { "Cache-Control": "no-store" } });
  }
}
