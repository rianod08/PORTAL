import { ApiError, body, guardOrigin, session, supa } from "@/lib/cloud-server";
import { ownedVenue, venueFailure } from "@/lib/venue-server";
import { validateVenue } from "@/lib/venue-contract";
export async function POST(
  request: Request,
  ctx: { params: Promise<{ id: string }> },
) {
  try {
    guardOrigin(request);
    const { token } = await session(request);
    const venue = await ownedVenue((await ctx.params).id, token);
    const input = await body(request);
    if (
      !["publish", "unpublish"].includes(input.action) ||
      !Number.isSafeInteger(input.revision)
    )
      throw new ApiError("Invalid publish request.");
    if (input.revision !== venue.revision)
      throw new ApiError("Revision conflict", 409);
    if (input.action === "publish") {
      const errors = validateVenue(venue.document);
      if (errors.length) throw new ApiError(errors.join(" "));
    }
    return Response.json(
      await supa("/rest/v1/rpc/portal_publish_venue", token, {
        method: "POST",
        body: JSON.stringify({
          p_id: venue.id,
          p_revision: input.revision,
          p_unpublish: input.action === "unpublish",
        }),
      }),
    );
  } catch (e) {
    return venueFailure(e);
  }
}
