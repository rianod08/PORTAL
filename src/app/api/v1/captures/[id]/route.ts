import { ApiError, body, failure, guardOrigin, id, session, supa, config } from "@/lib/cloud-server";
import { scheduleCloudWorker } from "@/lib/cloud-worker";
export const maxDuration = 300;
type Context = { params: Promise<{ id: string }> };
export async function GET(request: Request, ctx: Context) {
  try {
    const { token } = await session(request), captureId = id((await ctx.params).id);
    const rows = await supa(`/rest/v1/portal_captures?id=eq.${captureId}&select=*`, token);
    if (!rows.length) throw new ApiError("Capture not found.", 404);
    scheduleCloudWorker(rows);
    return Response.json(rows[0]);
  } catch(e) { return failure(e); }
}
export async function POST(request: Request, ctx: Context) {
  try { guardOrigin(request); const { token } = await session(request), captureId = id((await ctx.params).id), input = await body(request);
    const rows = await supa(`/rest/v1/portal_captures?id=eq.${captureId}&select=*`, token); if (!rows.length) throw new ApiError("Capture not found.", 404); const capture = rows[0];
    if (input.action === "upload") {
      const frame = capture.manifest.frames.find((f: { id: string }) => f.id === input.frameId); if (!frame || capture.status !== "uploading") throw new ApiError("Frame is not awaiting upload.");
      const present = await supa(`/storage/v1/object/info/portal-media/${frame.path}`, token).then(() => true).catch(() => false); if (present) return Response.json({ uploaded: true });
      const signed = await supa(`/storage/v1/object/upload/sign/portal-media/${frame.path}`, token, { method: "POST", body: "{}" }); return Response.json({ path: frame.path, uploadUrl: `${config().url}/storage/v1${signed.url}`, token: signed.token, uploadAuthorization: token, storageUrl: config().url });
    }
    if (!["complete","retry","cancel"].includes(input.action)) throw new ApiError("Invalid capture action.");
    const updated = await supa("/rest/v1/rpc/portal_capture_action", token, { method: "POST", body: JSON.stringify({ p_id: captureId, p_action: input.action }) });
    scheduleCloudWorker([updated]);
    return Response.json(updated);
  } catch(e) { return failure(e); }
}
