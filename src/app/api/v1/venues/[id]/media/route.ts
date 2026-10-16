import {
  ApiError,
  body,
  config,
  guardOrigin,
  session,
  supa,
} from "@/lib/cloud-server";
import { ownedVenue, venueFailure } from "@/lib/venue-server";
export async function POST(
  request: Request,
  ctx: { params: Promise<{ id: string }> },
) {
  try {
    guardOrigin(request);
    const { token, user } = await session(request);
    const v = await ownedVenue((await ctx.params).id, token);
    const input = await body(request);
    const ext: Record<string, string> = {
      "image/jpeg": "jpg",
      "image/png": "png",
      "image/webp": "webp",
    };
    if (
      typeof input.mimeType !== "string" ||
      !Object.hasOwn(ext, input.mimeType) ||
      !Number.isSafeInteger(input.size) ||
      input.size <= 0 ||
      input.size > 10 * 1024 * 1024
    )
      throw new ApiError("Choose a JPEG, PNG or WebP image up to 10 MB.");
    const path = `${user.id}/${v.id}/${crypto.randomUUID()}.${ext[input.mimeType]}`;
    const signed = await supa(
      `/storage/v1/object/upload/sign/portal-venue-media/${path}`,
      token,
      { method: "POST", body: "{}" },
    );
    return Response.json({
      path,
      uploadUrl: `${config().url}/storage/v1${signed.url}`,
    });
  } catch (e) {
    return venueFailure(e);
  }
}
