import { body, guardOrigin, session, supa } from "@/lib/cloud-server";
import { checkedVenueInput, venueFailure } from "@/lib/venue-server";
export async function GET(request: Request) {
  try {
    const { token } = await session(request);
    return Response.json(
      await supa(
        "/rest/v1/portal_venues?select=*&order=created_at.desc",
        token,
      ),
      { headers: { "Cache-Control": "no-store" } },
    );
  } catch (e) {
    return venueFailure(e);
  }
}
export async function POST(request: Request) {
  try {
    guardOrigin(request);
    const { token } = await session(request);
    const input = checkedVenueInput(await body(request));
    return Response.json(
      await supa("/rest/v1/rpc/portal_save_venue", token, {
        method: "POST",
        body: JSON.stringify({
          p_id: null,
          p_revision: input.revision,
          p_slug: input.slug,
          p_document: input.document,
          p_tour: input.mainTourId,
        }),
      }),
      { status: 201 },
    );
  } catch (e) {
    return venueFailure(e);
  }
}
