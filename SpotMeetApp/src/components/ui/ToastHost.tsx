import React, { useCallback, useEffect, useRef, useState } from 'react';
import { Animated, Easing, StyleSheet, Text, TouchableOpacity, View } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useTheme } from '../../context/ThemeContext';
import { setToastHandler, ToastPayload, ToastVariant } from '../../utils/toast';

/** How long a notification stays on screen before sliding out. */
const VISIBLE_MS = 3500;

const ICONS: Record<ToastVariant, keyof typeof Ionicons.glyphMap> = {
  success: 'checkmark-circle',
  error: 'close-circle',
  warning: 'alert-circle',
  info: 'information-circle',
};

/**
 * In-app notification shown at the bottom of the screen, in the SpotMeet identity,
 * dismissing on its own. Replaces the blocking system dialogs for informational messages.
 *
 * Mounted once at the app root: `showAlert()` routes every existing call here.
 */
export default function ToastHost() {
  const { colors } = useTheme();
  const insets = useSafeAreaInsets();
  const [toast, setToast] = useState<ToastPayload | null>(null);
  const translateY = useRef(new Animated.Value(120)).current;
  const opacity = useRef(new Animated.Value(0)).current;
  const hideTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  const hide = useCallback(() => {
    if (hideTimer.current) {
      clearTimeout(hideTimer.current);
      hideTimer.current = null;
    }
    Animated.parallel([
      Animated.timing(translateY, { toValue: 120, duration: 180, easing: Easing.in(Easing.quad), useNativeDriver: true }),
      Animated.timing(opacity, { toValue: 0, duration: 180, useNativeDriver: true }),
    ]).start(() => setToast(null));
  }, [opacity, translateY]);

  useEffect(() => {
    setToastHandler((next) => {
      if (hideTimer.current) clearTimeout(hideTimer.current);
      setToast(next);
      translateY.setValue(120);
      opacity.setValue(0);
      Animated.parallel([
        Animated.timing(translateY, { toValue: 0, duration: 240, easing: Easing.out(Easing.back(1.2)), useNativeDriver: true }),
        Animated.timing(opacity, { toValue: 1, duration: 200, useNativeDriver: true }),
      ]).start();
      hideTimer.current = setTimeout(() => hide(), VISIBLE_MS);
    });

    return () => {
      setToastHandler(null);
      if (hideTimer.current) clearTimeout(hideTimer.current);
    };
  }, [hide, opacity, translateY]);

  if (!toast) return null;

  const accent =
    toast.variant === 'success' ? colors.success
    : toast.variant === 'error' ? colors.danger
    : toast.variant === 'warning' ? '#FFB300'
    : colors.primary;

  return (
    <Animated.View
      pointerEvents="box-none"
      style={[
        styles.wrapper,
        { bottom: insets.bottom + 74, opacity, transform: [{ translateY }] },
      ]}
    >
      <View style={[styles.toast, { backgroundColor: colors.card, borderColor: accent }]}>
        {/* SpotMeet identity: accent stripe + variant icon */}
        <View style={[styles.stripe, { backgroundColor: accent }]} />
        <Ionicons name={ICONS[toast.variant]} size={20} color={accent} style={styles.icon} />

        <View style={styles.textBlock}>
          <Text style={[styles.title, { color: colors.text }]} numberOfLines={1}>
            {toast.title}
          </Text>
          {toast.message ? (
            <Text style={[styles.message, { color: colors.subtext }]} numberOfLines={4}>
              {toast.message}
            </Text>
          ) : null}
        </View>

        <TouchableOpacity onPress={hide} style={styles.closeBtn} hitSlop={{ top: 8, bottom: 8, left: 8, right: 8 }}>
          <Ionicons name="close" size={16} color={colors.subtext} />
        </TouchableOpacity>
      </View>
    </Animated.View>
  );
}

const styles = StyleSheet.create({
  wrapper: {
    position: 'absolute',
    left: 0,
    right: 0,
    paddingHorizontal: 16,
    zIndex: 9999,
    elevation: 9999,
  },
  toast: {
    flexDirection: 'row',
    alignItems: 'center',
    borderRadius: 12,
    borderWidth: 1,
    paddingVertical: 12,
    paddingRight: 10,
    overflow: 'hidden',
    maxWidth: 700,
    width: '100%',
    alignSelf: 'center',
    shadowColor: '#000',
    shadowOpacity: 0.3,
    shadowRadius: 12,
    shadowOffset: { width: 0, height: 4 },
    elevation: 8,
  },
  stripe: {
    width: 4,
    alignSelf: 'stretch',
    marginRight: 10,
  },
  icon: {
    marginRight: 10,
  },
  textBlock: {
    flex: 1,
  },
  title: {
    fontSize: 14,
    fontWeight: 'bold',
  },
  message: {
    fontSize: 12,
    lineHeight: 16,
    marginTop: 2,
  },
  closeBtn: {
    padding: 6,
    marginLeft: 6,
  },
});
