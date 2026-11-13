import React, { useEffect, useState } from 'react';
import { Image, StyleSheet, Text, View, type StyleProp, type TextStyle, type ViewStyle } from 'react-native';
import { useAuth } from '../../context/AuthContext';
import { API_BASE_URL } from '../../config/api';
import { parseProfilePhoto } from '../../utils/profilePhotoData';

// Photos are kept for a minute so a list does not ask the server again on every render.
const CACHE_MS = 60000;
const cache = new Map<string, { at: number; image: Promise<string | null> }>();
let cacheToken: string | null = null;

function loadPhoto(token: string, userId: number) {
  if (cacheToken !== token) { cache.clear(); cacheToken = token; }
  const key = `${API_BASE_URL}|${userId}`, hit = cache.get(key);
  if (hit && Date.now() - hit.at < CACHE_MS) return hit.image;
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), 15000);
  const image = fetch(`${API_BASE_URL}/users/${userId}/photo`, { signal: controller.signal, headers: { Authorization: `Bearer ${token}` } })
    .then(async response => response.ok ? parseProfilePhoto(await response.json(), userId).imageBase64 : null)
    .catch(() => null)
    .finally(() => clearTimeout(timer));
  cache.set(key, { at: Date.now(), image });
  return image;
}

// First letters of the first and second names; connectors such as "da" and "de" are skipped.
const CONNECTORS = new Set(['da', 'das', 'de', 'do', 'dos', 'e']);
export function initials(name: string) {
  const words = name.trim().split(/\s+/).filter(Boolean);
  const second = words.slice(1).find(word => !CONNECTORS.has(word.toLowerCase()));
  return ((words[0]?.charAt(0) ?? '') + (second?.charAt(0) ?? '')).toUpperCase();
}

export function forgetUserPhoto(userId: number) {
  cache.delete(`${API_BASE_URL}|${userId}`);
}

interface UserAvatarProps { userId?: number | null; name: string; style: StyleProp<ViewStyle>; textStyle: StyleProp<TextStyle>; }

// Shows the person's photo when there is one; otherwise the initials stay.
export default function UserAvatar({ userId, name, style, textStyle }: UserAvatarProps) {
  const { token } = useAuth();
  const [image, setImage] = useState<string | null>(null);
  const [failed, setFailed] = useState(false);
  useEffect(() => {
    let active = true;
    setImage(null); setFailed(false);
    if (token && typeof userId === 'number' && Number.isSafeInteger(userId) && userId > 0) {
      void loadPhoto(token, userId).then(data => { if (active) setImage(data); });
    }
    return () => { active = false; };
  }, [token, userId]);
  return <View style={[style, styles.clip]}>
    <Text style={textStyle}>{initials(name)}</Text>
    {image && !failed ? <Image accessibilityLabel={`Foto de ${name}`} source={{ uri: `data:image/jpeg;base64,${image}` }}
      style={StyleSheet.absoluteFill} onError={() => setFailed(true)} /> : null}
  </View>;
}
const styles = StyleSheet.create({ clip: { overflow: 'hidden' } });
