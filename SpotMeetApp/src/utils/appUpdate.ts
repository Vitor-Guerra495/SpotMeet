export const ANDROID_APPLICATION_ID = 'com.anonymous.SpotMeetApp';

export interface InstalledAndroidApp { applicationId: string; version: string; versionCode: number; }
export interface AndroidRelease extends InstalledAndroidApp { downloadUrl: string; }

const VERSION = /^(?:0|[1-9][0-9]{0,8})(?:\.(?:0|[1-9][0-9]{0,8})){2}$/;
const BUILD_PAGE = /^https:\/\/expo\.dev\/accounts\/[A-Za-z0-9_-]{1,100}\/projects\/SpotMeetApp\/builds\/[0-9a-fA-F]{8}(?:-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}\/?$/;

export function isExpoBuildPage(value: unknown): value is string {
  return typeof value === 'string' && BUILD_PAGE.test(value) && value.trim() === value;
}

export function installedAndroidApp(input: {
  platform: string; development: boolean; environment: string;
  applicationId: string | null; version: string | null; build: string | null;
}): InstalledAndroidApp | null {
  if (input.platform !== 'android' || input.development || input.environment === 'storeClient'
      || input.applicationId !== ANDROID_APPLICATION_ID || !input.version || !VERSION.test(input.version)
      || input.version.trim() !== input.version || !input.build || !/^[1-9][0-9]{0,9}$/.test(input.build)
      || input.build.trim() !== input.build) return null;
  const versionCode = Number(input.build);
  if (versionCode > 2147483647) return null;
  return { applicationId: input.applicationId, version: input.version, versionCode };
}

export function availableAndroidUpdate(value: unknown, installed: InstalledAndroidApp): AndroidRelease | null {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return null;
  const data = value as Record<string, unknown>;
  if (data.applicationId !== installed.applicationId || typeof data.version !== 'string'
      || !VERSION.test(data.version) || data.version.trim() !== data.version || typeof data.versionCode !== 'number'
      || !Number.isInteger(data.versionCode) || data.versionCode <= installed.versionCode
      || data.versionCode > 2147483647 || !isExpoBuildPage(data.downloadUrl)) return null;
  const next = data.version.split('.').map(Number), current = installed.version.split('.').map(Number);
  const firstDifference = next.findIndex((part, i) => part !== current[i]);
  if (firstDifference >= 0 && next[firstDifference] < current[firstDifference]) return null;
  return { applicationId: installed.applicationId, version: data.version,
    versionCode: data.versionCode, downloadUrl: data.downloadUrl };
}
