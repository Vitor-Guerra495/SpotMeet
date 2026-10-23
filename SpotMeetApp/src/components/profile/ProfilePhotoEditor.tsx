import React, { useCallback, useRef, useState } from 'react';
import { ActivityIndicator, Image, StyleSheet, Text, TouchableOpacity, View } from 'react-native';
import { useFocusEffect } from '@react-navigation/native';
import { Ionicons, type IoniconsIconName } from '@react-native-vector-icons/ionicons';
import { useProfilePhoto } from '../../context/ProfilePhotoContext';
import { useTheme } from '../../context/ThemeContext';
import { useAuth } from '../../context/AuthContext';
import { confirmAction } from '../../utils/alerts';
import { API_BASE_URL } from '../../config/api';

export default function ProfilePhotoEditor({ name, disabled }: { name: string; disabled: boolean }) {
  const photo = useProfilePhoto(), { colors } = useTheme(), { getSession } = useAuth();
  const mounted = useRef(false), confirmation = useRef(false);
  const [failedImage, setFailedImage] = useState<string | null>(null);
  const session = getSession(), api = API_BASE_URL;
  useFocusEffect(useCallback(() => {
    mounted.current = true; void photo.refresh();
    // The preview survives a change of section; SettingsScreen drops it when Ajustes loses focus.
    return () => { mounted.current = false; photo.cancelRequest(); };
  }, [photo.refresh, photo.cancelRequest]));
  const current = () => mounted.current && getSession().token === session.token && getSession().version === session.version && API_BASE_URL === api;
  const busy = !!photo.busy || disabled;
  const button = (label: string, icon: IoniconsIconName, action: () => unknown, destructive = false) => (
    <TouchableOpacity accessibilityRole="button" accessibilityLabel={label} accessibilityState={{ disabled: busy }} disabled={busy}
      onPress={() => { if (current() && !busy && !confirmation.current) return action(); }}
      style={[styles.button, { borderColor: colors.border, opacity: busy ? 0.5 : 1 }]}>
      <Ionicons name={icon} size={18} color={destructive ? colors.danger : colors.primary} />
      <Text style={[styles.label, { color: destructive ? colors.danger : colors.primary }]}>{label}</Text>
    </TouchableOpacity>
  );
  const remove = () => {
    if (!current() || confirmation.current) return;
    confirmation.current = true;
    confirmAction('Remover foto', 'Deseja remover sua foto de perfil e voltar ao avatar padrão?',
      async () => { if (current()) await photo.remove(); }, 'Remover', true, () => { confirmation.current = false; });
  };
  const uri = photo.draft?.uri ?? (photo.photo?.imageBase64 ? `data:image/jpeg;base64,${photo.photo.imageBase64}` : null);
  const imageKey = photo.draft?.uri ?? photo.photo?.version ?? null;
  return <View style={styles.root}>
    {uri && failedImage !== imageKey ? <Image key={imageKey} accessibilityLabel={photo.draft ? 'Prévia da nova foto de perfil' : 'Sua foto de perfil'} source={{ uri }} style={styles.avatar}
      onError={() => { if (current()) setFailedImage(imageKey); }} />
      : <View accessibilityLabel="Avatar da conta, sem foto" style={[styles.avatar, { backgroundColor: colors.inputBg }]}>
        <Text style={[styles.initial, { color: colors.primary }]}>{name.trim().charAt(0).toUpperCase() || 'U'}</Text></View>}
    <Text style={[styles.hint, { color: colors.subtext }]}>{photo.draft ? 'Confira a foto antes de salvar.' : 'Foto de perfil'}</Text>
    {photo.busy && <ActivityIndicator accessibilityLabel="Processando foto" color={colors.primary} />}
    <View style={styles.actions}>
      {photo.draft ? <>{button('Salvar foto', 'checkmark-outline', photo.save)}{button('Cancelar foto', 'close-outline', photo.discard)}</>
        : <>{button('Galeria', 'images-outline', () => photo.select('library'))}{button('Câmera', 'camera-outline', () => photo.select('camera'))}
          {photo.photo?.imageBase64 ? button('Remover foto', 'trash-outline', remove, true) : null}</>}
    </View>
    {photo.error ? <><Text accessibilityRole="alert" accessibilityLiveRegion="polite" style={[styles.hint, { color: colors.danger }]}>{photo.error}</Text>
      {!photo.loaded && button('Recarregar foto', 'refresh-outline', photo.refresh)}</> : null}
    {uri && failedImage === imageKey ? <><Text style={[styles.hint, { color: colors.danger }]}>Não foi possível exibir a foto.</Text>
      {button('Tentar exibir foto', 'refresh-outline', () => setFailedImage(null))}</> : null}
  </View>;
}
const styles = StyleSheet.create({
  root: { alignItems: 'center', paddingBottom: 18, paddingTop: 2, gap: 8 },
  avatar: { width: 64, height: 64, borderRadius: 32, alignItems: 'center', justifyContent: 'center' },
  initial: { fontSize: 26, fontWeight: '700' },
  actions: { flexDirection: 'row', flexWrap: 'wrap', justifyContent: 'center', gap: 8 },
  button: { minHeight: 48, borderWidth: 1, borderRadius: 12, paddingHorizontal: 12, flexDirection: 'row', alignItems: 'center', gap: 6 },
  label: { fontSize: 12, fontWeight: '600' },
  hint: { fontSize: 12, lineHeight: 18, textAlign: 'center' },
});
