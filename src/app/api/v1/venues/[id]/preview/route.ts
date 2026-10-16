import { ownedTour, session } from "@/lib/cloud-server";
import { hydrateVenue, ownedVenue, venueFailure } from "@/lib/venue-server";
export async function GET(
  request: Request,
  ctx: { params: Promise<{ id: string }> },
) {
  try {
    const { token } = await session(request);
    const v = await ownedVenue((await ctx.params).id, token);
    const tour = v.main_tour_id ? await ownedTour(v.main_tour_id, token) : null;
    return Response.json(
      await hydrateVenue(
        {
          id: v.id,
          slug: v.slug,
          document: v.document,
          revision: v.revision,
          tourSlug: tour?.published_revision ? tour.slug : null,
          preview: true,
        },
        token,
      ),
      { headers: { "Cache-Control": "private, no-store" } },
    );
  } catch (e) {
    return venueFailure(e);
  }
}
