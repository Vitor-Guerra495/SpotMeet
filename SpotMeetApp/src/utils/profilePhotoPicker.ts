import { Platform } from 'react-native';
import * as ImagePicker from 'expo-image-picker';
import { ImageManipulator, SaveFormat } from 'expo-image-manipulator';
import { File, Paths } from 'expo-file-system';
import { MAX_PHOTO_BYTES } from './profilePhotoData';

export type PhotoSource = 'camera' | 'library';
export interface DraftPhoto { uri: string; }
export class PhotoSelectionError extends Error {}
const owned = new Set<string>();

export function releaseDraftPhoto(photo: DraftPhoto | null) {
  if (!photo || !owned.has(photo.uri)) return;
  try { const file = new File(photo.uri); if (file.exists) file.delete(); } catch { /* Cache cleanup is best effort. */ }
  owned.delete(photo.uri);
}

export async function pickProfilePhoto(source: PhotoSource, isCurrent: () => boolean = () => true): Promise<DraftPhoto | null> {
  if (Platform.OS !== 'android' && Platform.OS !== 'ios') throw new PhotoSelectionError('Use o aplicativo Android ou iOS para escolher uma foto.');
  if (source === 'camera' && !(await ImagePicker.requestCameraPermissionsAsync()).granted) {
    throw new PhotoSelectionError('Permita o acesso à câmera nas configurações do aparelho para tirar uma foto.');
  }
  if (!isCurrent()) return null;
  const options: ImagePicker.ImagePickerOptions = { mediaTypes: ['images'], allowsEditing: true, aspect: [1, 1], quality: 0.8, exif: false };
  const result = await (source === 'camera' ? ImagePicker.launchCameraAsync(options) : ImagePicker.launchImageLibraryAsync(options));
  if (result.canceled || !isCurrent()) return null;
  const asset = result.assets?.[0];
  if (!asset || !/^(file|content):\/\//.test(asset.uri) || !Number.isSafeInteger(asset.width) || !Number.isSafeInteger(asset.height)
      || asset.width < 1 || asset.height < 1 || asset.width * asset.height > 40000000 || (asset.fileSize ?? 0) > 20 * MAX_PHOTO_BYTES) {
    throw new PhotoSelectionError('Escolha uma imagem de até 20 MB e 40 megapixels.');
  }
  const context = ImageManipulator.manipulate(asset.uri);
  let image: Awaited<ReturnType<typeof context.renderAsync>> | undefined;
  let draft: DraftPhoto | null = null;
  try {
    const side = Math.min(asset.width, asset.height);
    context.crop({ originX: Math.floor((asset.width - side) / 2), originY: Math.floor((asset.height - side) / 2), width: side, height: side });
    context.resize({ width: Math.min(side, 512), height: Math.min(side, 512) });
    image = await context.renderAsync();
    if (!isCurrent()) return null;
    const output = await image.saveAsync({ format: SaveFormat.JPEG, compress: 0.85 });
    const file = new File(output.uri);
    const uri = new URL(file.uri).href, cache = new URL(Paths.cache.uri).href.replace(/\/?$/, '/');
    if (!uri.startsWith(cache) || !uri.startsWith('file://')) throw new PhotoSelectionError('Não foi possível preparar a foto. Tente outra imagem.');
    draft = { uri }; owned.add(uri);
    if (!isCurrent()) { releaseDraftPhoto(draft); return null; }
    if (file.size < 1 || file.size > MAX_PHOTO_BYTES || output.width < 1 || output.width > 512 || output.width !== output.height) {
      throw new PhotoSelectionError('Não foi possível reduzir a foto. Tente outra imagem.');
    }
    return draft;
  } catch (error) {
    releaseDraftPhoto(draft);
    throw error;
  } finally { image?.release(); context.release(); }
}
