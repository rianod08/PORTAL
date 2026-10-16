import { ApiError, body, failure, guardOrigin, id, ownedTour, session, supa } from "@/lib/cloud-server";
import { documentAssets, validateDocument } from "@/lib/tour-contract";
type Context = { params: Promise<{ id: string }> };
export async function GET(request: Request, ctx: Context) { try { const { token } = await session(request); return Response.json(await ownedTour((await ctx.params).id, token)); } catch(e) { return failure(e); } }
export async function PUT(request: Request, ctx: Context) {
  try {
    guardOrigin(request); const { token, user } = await session(request), tourId = id((await ctx.params).id), input = await body(request);
    const errors = validateDocument(input.document); if (errors.length) throw new ApiError(errors.join(" "));
    if (!Number.isInteger(input.revision)) throw new ApiError("Missing revision.");
    for (const path of documentAssets(input.document)) if (!path.startsWith(`${user.id}/${tourId}/`) || path.includes("..") || path.includes("/captures/")) throw new ApiError("Tour media belongs to another tour or is an original capture.");
    for (const scene of input.document.scenes) { delete scene.imageUrl; for (const point of scene.points) delete point.mediaUrl; }
    if(input.document.branding) delete input.document.branding.logoUrl;
    const rows = await supa(`/rest/v1/portal_tours?id=eq.${tourId}&revision=eq.${input.revision}`, token, { method: "PATCH", headers: { Prefer: "return=representation" }, body: JSON.stringify({ document: input.document, title: input.document.title, revision: input.revision + 1, updated_at: new Date().toISOString() }) });
    if (!rows.length) throw new ApiError("This tour changed on another device. Your edits are preserved; reload the latest version or export your edits.", 409);
    return Response.json(rows[0]);
  } catch(e) { return failure(e); }
}
