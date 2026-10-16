import { ApiError, body, config, failure, guardOrigin, id, ownedTour, session, supa } from "@/lib/cloud-server";
export async function POST(request: Request) {
  try { guardOrigin(request); const { token, user } = await session(request), input = await body(request);
    const tourId = id(input.tourId); await ownedTour(tourId, token);
    const mediaType = input.mediaType ?? "panorama", mimeType = input.mimeType ?? "image/jpeg";
    const extensions: Record<string,string> = {"image/jpeg":"jpg","image/png":"png","image/webp":"webp","video/mp4":"mp4"};
    if (!["panorama","image","video","logo"].includes(mediaType) || typeof mimeType!=="string" || !Object.hasOwn(extensions,mimeType) || (mediaType === "video") !== (mimeType === "video/mp4") || (input.size !== undefined && (!Number.isSafeInteger(input.size) || input.size<=0 || input.size>50*1024*1024))) throw new ApiError("Choose JPEG, PNG, WebP, or MP4 media up to 50 MB.");
    const assetId=input.assetId===undefined?crypto.randomUUID():id(input.assetId);
    const path = `${user.id}/${tourId}/media/${assetId}.${extensions[mimeType]}`;
    const signed = await supa(`/storage/v1/object/upload/sign/portal-media/${path}`, token, { method: "POST", body: "{}" });
    return Response.json({ path, token: signed.token, uploadAuthorization: token, uploadUrl: `${config().url}/storage/v1${signed.url}`, storageUrl: config().url });
  } catch(e) { return failure(e); }
}
export async function GET(request: Request) {
  try { const { token, user } = await session(request); const path = new URL(request.url).searchParams.get("path") ?? "";
    if (!path.startsWith(`${user.id}/`) || path.includes("..")) throw new ApiError("Invalid media path.", 403);
    const result = await supa(`/storage/v1/object/sign/portal-media/${path}`, token, { method: "POST", body: JSON.stringify({ expiresIn: 3600 }) });
    return Response.json({ url: `${config().url}/storage/v1${result.signedURL}` });
  } catch(e) { return failure(e); }
}
