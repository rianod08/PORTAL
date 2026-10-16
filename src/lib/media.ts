import { createId, isBrowser } from "./utils";

const DB_NAME = "portal-media";
const STORE_NAME = "files";

function fileToDataUrl(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(String(reader.result));
    reader.onerror = () => reject(new Error("Could not read this image."));
    reader.readAsDataURL(file);
  });
}

function openDb(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    const request = indexedDB.open(DB_NAME, 1);
    request.onupgradeneeded = () => {
      request.result.createObjectStore(STORE_NAME);
    };
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(new Error("Could not open local media storage."));
  });
}

async function saveToIndexedDb(key: string, value: string): Promise<void> {
  if (!isBrowser() || !("indexedDB" in window)) return;
  const db = await openDb();
  await new Promise<void>((resolve, reject) => {
    const tx = db.transaction(STORE_NAME, "readwrite");
    tx.objectStore(STORE_NAME).put(value, key);
    tx.oncomplete = () => resolve();
    tx.onerror = () => reject(new Error("Could not save image locally."));
  });
  db.close();
}

export function validateImage(file: File): { valid: boolean; error?: string } {
  if (!file.type.startsWith("image/")) {
    return { valid: false, error: "Please upload a JPG, PNG, WebP, or other image file." };
  }
  if (file.size > 8 * 1024 * 1024) {
    return { valid: false, error: "Please use an image smaller than 8 MB." };
  }
  return { valid: true };
}

export async function uploadVenueImage(file: File): Promise<string> {
  const result = validateImage(file);
  if (!result.valid) throw new Error(result.error);
  const dataUrl = await fileToDataUrl(file);
  try {
    await saveToIndexedDb(createId("venue-image"), dataUrl);
  } catch {
    // Data URLs are still safe to store in localStorage for prototype-sized uploads.
  }
  return dataUrl;
}

export async function uploadLogo(file: File): Promise<string> {
  const result = validateImage(file);
  if (!result.valid) throw new Error(result.error);
  const dataUrl = await fileToDataUrl(file);
  try {
    await saveToIndexedDb(createId("logo"), dataUrl);
  } catch {
    // Fall back to returning the data URL.
  }
  return dataUrl;
}

export async function deleteMedia(url: string): Promise<void> {
  if (!isBrowser() || !("indexedDB" in window)) return;
  try {
    const db = await openDb();
    await new Promise<void>((resolve) => {
      const tx = db.transaction(STORE_NAME, "readwrite");
      tx.objectStore(STORE_NAME).delete(url);
      tx.oncomplete = () => resolve();
      tx.onerror = () => resolve();
    });
    db.close();
  } catch {
    return;
  }
}

function loadImage(url: string): Promise<HTMLImageElement> {
  return new Promise((resolve, reject) => {
    const image = new Image();
    image.onload = () => resolve(image);
    image.onerror = () => reject(new Error("Could not load image."));
    image.src = url;
  });
}

export async function detectPanoramaRatio(file: File): Promise<boolean> {
  const result = validateImage(file);
  if (!result.valid) throw new Error(result.error);
  const dataUrl = await fileToDataUrl(file);
  const image = await loadImage(dataUrl);
  return image.width / image.height >= 1.8;
}

export async function createThumbnail(fileOrUrl: File | string): Promise<string> {
  const dataUrl = typeof fileOrUrl === "string" ? fileOrUrl : await fileToDataUrl(fileOrUrl);
  if (dataUrl.startsWith("gradient:")) return dataUrl;
  const image = await loadImage(dataUrl);
  const canvas = document.createElement("canvas");
  canvas.width = 480;
  canvas.height = 300;
  const ctx = canvas.getContext("2d");
  if (!ctx) return dataUrl;
  const ratio = Math.max(canvas.width / image.width, canvas.height / image.height);
  const width = image.width * ratio;
  const height = image.height * ratio;
  const x = (canvas.width - width) / 2;
  const y = (canvas.height - height) / 2;
  ctx.drawImage(image, x, y, width, height);
  return canvas.toDataURL("image/jpeg", 0.78);
}
