import { ApiError, config, id, supa } from "./cloud-server";
import {
  validateVenue,
  validVenueSlug,
  venueAssets,
  type PublicVenue,
  type Venue,
  type VenueDocument,
} from "./venue-contract";

export async function ownedVenue(
  venueId: string,
  token: string,
): Promise<Venue> {
  const rows = await supa(
    `/rest/v1/portal_venues?id=eq.${id(venueId)}&select=*`,
    token,
  );
  if (!rows?.length) throw new ApiError("Venue not found.", 404);
  return rows[0];
}
export function venueFailure(error: unknown) {
  let status = error instanceof ApiError ? error.status : 500;
  let message = error instanceof Error ? error.message : "Request failed.";
  if (/Revision conflict/i.test(message)) {
    status = 409;
    message =
      "This venue changed on another device. Your edits are preserved. Export them or reload the latest saved draft.";
  } else if (/duplicate key|already used by a tour/i.test(message)) {
    status = 409;
    message = "This venue link is already taken. Choose another.";
  } else if (/Venue not found|Tour not found/i.test(message)) {
    status = 404;
    message = "Venue or tour not found.";
  }
  return Response.json({ error: message }, { status });
}
export function checkedVenueInput(input: {
  slug?: unknown;
  document?: unknown;
  revision?: unknown;
  mainTourId?: unknown;
}) {
  if (!validVenueSlug(input.slug))
    throw new ApiError(
      "Use a link of 3–80 lowercase letters, numbers and hyphens, without the reserved tour- prefix.",
    );
  if (!Number.isSafeInteger(input.revision) || (input.revision as number) < 0)
    throw new ApiError("Invalid revision.");
  const errors = validateVenue(input.document);
  if (errors.length) throw new ApiError(errors.join(" "));
  if (
    input.mainTourId !== null &&
    input.mainTourId !== undefined &&
    (typeof input.mainTourId !== "string" ||
      !/^[\da-f-]{36}$/i.test(input.mainTourId))
  )
    throw new ApiError("Invalid main tour.");
  return {
    slug: input.slug,
    document: input.document as VenueDocument,
    revision: input.revision as number,
    mainTourId: input.mainTourId ?? null,
  };
}
export async function hydrateVenue<T extends { document: VenueDocument }>(
  v: T,
  token?: string,
): Promise<T & { logoUrl?: string; coverImageUrl?: string }> {
  const urls = Object.fromEntries(
    await Promise.all(
      venueAssets(v.document).map(async (path) => {
        const signed = await supa(
          `/storage/v1/object/sign/portal-venue-media/${path}`,
          token,
          { method: "POST", body: JSON.stringify({ expiresIn: 3600 }) },
        );
        return [path, `${config().url}/storage/v1${signed.signedURL}`];
      }),
    ),
  );
  return {
    ...v,
    logoUrl: v.document.logoAssetPath
      ? urls[v.document.logoAssetPath]
      : undefined,
    coverImageUrl: v.document.coverAssetPath
      ? urls[v.document.coverAssetPath]
      : undefined,
  };
}
export async function publicVenue(slug: string): Promise<PublicVenue | null> {
  if (!validVenueSlug(slug)) return null;
  const v = (await supa("/rest/v1/rpc/portal_public_venue", undefined, {
    method: "POST",
    body: JSON.stringify({ p_slug: slug }),
  })) as PublicVenue | null;
  if (!v) return null;
  if (validateVenue(v.document).length)
    throw new ApiError(
      "This storefront needs to be repaired by its owner.",
      422,
    );
  return v;
}
