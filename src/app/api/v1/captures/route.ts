import { ApiError, body, failure, guardOrigin, ownedTour, session, supa } from "@/lib/cloud-server";
import { scheduleCloudWorker } from "@/lib/cloud-worker";
export const maxDuration = 300;
export async function GET(request: Request) { try { const { token } = await session(request); const captures = await supa("/rest/v1/portal_captures?select=*&order=created_at.desc", token); scheduleCloudWorker(captures); return Response.json(captures); } catch(e) { return failure(e); } }
export async function POST(request: Request) {
  try {
    guardOrigin(request); const { token, user } = await session(request), input = await body(request); await ownedTour(input.tourId, token);
    if (!/^[0-9a-f-]{36}$/.test(input.id) || typeof input.name !== "string" || !input.name.trim() || input.name.length > 160 || !Array.isArray(input.frames) || input.frames.length < 8 || input.frames.length > 200) throw new ApiError("A capture needs 8–200 calibrated frames and a room name.");
    const seen = new Set<string>();
    for (const f of input.frames) {
      if (!/^[0-9a-f-]{36}$/.test(f.id) || seen.has(f.id) || !/^[a-f0-9]{64}$/.test(f.sha256) || !Array.isArray(f.intrinsics) || f.intrinsics.length !== 4 || !f.intrinsics.every(Number.isFinite) || f.intrinsics[0] <= 0 || f.intrinsics[1] <= 0 || !Array.isArray(f.pose) || f.pose.length !== 16 || !f.pose.every(Number.isFinite) || !Number.isInteger(f.width) || !Number.isInteger(f.height) || f.width < 64 || f.height < 64 || f.width > 20000 || f.height > 20000) throw new ApiError("Invalid frame calibration or checksum.");
      seen.add(f.id); f.path = `${user.id}/${input.tourId}/captures/${input.id}/${f.id}.jpg`;
    }
    const manifest = { version: 1, coordinates: "arcore-camera-to-world-column-major", frames: input.frames };
    const existing = await supa(`/rest/v1/portal_captures?id=eq.${input.id}&select=*`, token);
    if (existing.length) {
      const registered = new Map<string, string>(existing[0].manifest.frames.map((f: { id: string; sha256: string }) => [f.id, f.sha256]));
      if (registered.size !== input.frames.length || input.frames.some((f: { id: string; sha256: string }) => registered.get(f.id) !== f.sha256) || existing[0].tour_id !== input.tourId) throw new ApiError("Capture ID already belongs to different photos.", 409);
      return Response.json(existing[0]);
    }
    const rows = await supa("/rest/v1/portal_captures", token, { method: "POST", headers: { Prefer: "return=representation" }, body: JSON.stringify({ id: input.id, owner_id: user.id, tour_id: input.tourId, name: input.name, manifest }) }); return Response.json(rows[0], { status: 201 });
  } catch(e) { return failure(e); }
}
