export interface ProfilePhotoData { userId: number; imageBase64: string | null; version: string | null; }
export const MAX_PHOTO_BYTES = 1024 * 1024;

export function parseProfilePhoto(value: unknown, userId: number | null): ProfilePhotoData {
  if (!value || typeof value !== 'object' || Array.isArray(value) || !Number.isSafeInteger(userId) || userId! < 1) throw new Error('Invalid photo response');
  const data = value as Record<string, unknown>;
  if (data.userId !== userId) throw new Error('Invalid photo account');
  if (data.imageBase64 === null && data.version === null) return { userId: userId!, imageBase64: null, version: null };
  if (typeof data.version !== 'string' || !/^[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}$/i.test(data.version)
      || typeof data.imageBase64 !== 'string' || !data.imageBase64.startsWith('/9j/') || data.imageBase64.length % 4 !== 0
      || data.imageBase64.length > Math.ceil(MAX_PHOTO_BYTES / 3) * 4 || !/^[A-Za-z0-9+/]+={0,2}$/.test(data.imageBase64)) {
    throw new Error('Invalid photo content');
  }
  return { userId: userId!, imageBase64: data.imageBase64, version: data.version };
}
