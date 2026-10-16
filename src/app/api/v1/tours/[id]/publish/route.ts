import { ApiError, body, failure, guardOrigin, ownedTour, session, supa } from "@/lib/cloud-server";
import { validateDocument } from "@/lib/tour-contract";
type Context = { params: Promise<{ id: string }> };
export async function POST(request: Request, ctx: Context) {
  try {
    guardOrigin(request); const { token } = await session(request), tour = await ownedTour((await ctx.params).id, token), input = await body(request);
    if (input.action !== "unpublish") { const errors = validateDocument(tour.document, true); if (errors.length) throw new ApiError(errors.join(" ")); }
    if (input.revision !== tour.revision) throw new ApiError("Save the latest draft before publishing.", 409);
    const result = await supa("/rest/v1/rpc/portal_publish", token, { method: "POST", body: JSON.stringify({ p_tour: tour.id, p_revision: input.revision, p_unpublish: input.action === "unpublish" }) });
    return Response.json(result);
  } catch(e) { return failure(e); }
}
