import { ApiError } from "@/lib/cloud-server";
import { hydrateVenue, publicVenue, venueFailure } from "@/lib/venue-server";
export async function GET(
  _request: Request,
  ctx: { params: Promise<{ slug: string }> },
) {
  try {
    const v = await publicVenue((await ctx.params).slug);
    if (!v) throw new ApiError("This venue is not published.", 404);
    return Response.json(await hydrateVenue(v), {
      headers: { "Cache-Control": "no-store" },
    });
  } catch (e) {
    return venueFailure(e);
  }
}
