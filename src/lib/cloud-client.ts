export class CloudError extends Error { constructor(message: string, public status: number) { super(message); } }
export async function api<T>(path: string, method = "GET", value?: unknown): Promise<T> {
  const response = await fetch(`/api/v1/${path}`, { method, headers: { "Content-Type": "application/json" }, ...(value === undefined ? {} : { body: JSON.stringify(value) }) });
  const result = await response.json(); if (!response.ok) throw new CloudError(result.error ?? "Request failed", response.status); return result;
}
export async function uploadPanorama(tourId: string, file: File): Promise<string> {
  if (!["image/jpeg","image/png","image/webp"].includes(file.type) || file.size > 50 * 1024 * 1024) throw new Error("Choose a JPEG, PNG, or WebP panorama under 50 MB.");
  const bitmap = await createImageBitmap(file); const ratio = bitmap.width / bitmap.height; bitmap.close();
  if (Math.abs(ratio - 2) > 0.02) throw new Error("Use a full 360° equirectangular image with a 2:1 aspect ratio.");
  const signed = await api<{ path: string; token: string; uploadAuthorization: string; storageUrl: string }>("media", "POST", { tourId });
  // TUS creation and chunking. Immutable asset names avoid replacement/cache races.
  const metadata = Object.entries({ bucketName: "portal-media", objectName: signed.path, contentType: file.type, cacheControl: "3600" }).map(([k,v]) => `${k} ${btoa(v)}`).join(",");
  const headers = { "Tus-Resumable": "1.0.0", "x-signature": signed.token, Authorization: `Bearer ${signed.uploadAuthorization}` };
  const created = await fetch(`${signed.storageUrl}/storage/v1/upload/resumable`, { method: "POST", headers: { ...headers, "Upload-Length": String(file.size), "Upload-Metadata": metadata } });
  if (!created.ok || !created.headers.get("Location")) throw new Error("Could not start panorama upload.");
  const location = new URL(created.headers.get("Location")!, signed.storageUrl).href;
  let offset = 0, failures = 0;
  while (offset < file.size) {
    try {
      const response = await fetch(location, { method: "PATCH", headers: { ...headers, "Upload-Offset": String(offset), "Content-Type": "application/offset+octet-stream" }, body: file.slice(offset, offset + 6 * 1024 * 1024) });
      if (!response.ok) throw new Error("Upload interrupted.");
      const next = Number(response.headers.get("Upload-Offset")); if (!Number.isFinite(next) || next <= offset || next > file.size) throw new Error("Invalid upload offset."); offset = next; failures = 0;
    } catch(e) {
      if (++failures > 4) throw e;
      await new Promise(resolve => setTimeout(resolve, failures * 1000));
      const head = await fetch(location, { method: "HEAD", headers });
      if (head.ok) offset = Number(head.headers.get("Upload-Offset")) || 0;
    }
  }
  return signed.path;
}
