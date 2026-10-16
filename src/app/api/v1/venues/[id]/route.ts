import { ApiError, body, guardOrigin, session, supa } from "@/lib/cloud-server";
import {
  checkedVenueInput,
  ownedVenue,
  venueFailure,
} from "@/lib/venue-server";
type Context = { params: Promise<{ id: string }> };
export async function GET(request: Request, ctx: Context) {
  try {
    const { token } = await session(request);
    return Response.json(await ownedVenue((await ctx.params).id, token), {
      headers: { "Cache-Control": "no-store" },
    });
  } catch (e) {
    return venueFailure(e);
  }
}
export async function PUT(request: Request, ctx: Context) {
  try {
    guardOrigin(request);
    const { token } = await session(request);
    const venue = await ownedVenue((await ctx.params).id, token);
    const input = checkedVenueInput(await body(request));
    if (input.revision !== venue.revision)
      throw new ApiError("Revision conflict", 409);
    return Response.json(
      await supa("/rest/v1/rpc/portal_save_venue", token, {
        method: "POST",
        body: JSON.stringify({
          p_id: venue.id,
          p_revision: input.revision,
          p_slug: input.slug,
          p_document: input.document,
          p_tour: input.mainTourId,
        }),
      }),
    );
  } catch (e) {
    return venueFailure(e);
  }
}
