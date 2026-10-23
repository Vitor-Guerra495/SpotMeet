import React, { useCallback, useEffect, useRef, useState } from 'react';
import { Animated, Easing, StyleSheet, Text, TouchableOpacity, View } from 'react-native';
import { Ionicons, type IoniconsIconName } from '@react-native-vector-icons/ionicons';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useTheme } from '../../context/ThemeContext';
import { registerToastHost, dismissToast, ToastPayload, ToastVariant } from '../../utils/toast';

/** How long a notification stays on screen before sliding out. */
const VISIBLE_MS = 3500;

const ICONS: Record<ToastVariant, IoniconsIconName> = {
  success: 'checkmark-circle',
  error: 'close-circle',
  warning: 'alert-circle',
  info: 'information-circle',
};

/** Top notification layer for the root screen or a visible native modal. */
export default function ToastHost({ active = true }: { active?: boolean }) {
  const { colors } = useTheme();
  const insets = useSafeAreaInsets();
  const [toast, setToast] = useState<ToastPayload | null>(null);
  const translateY = useRef(new Animated.Value(-160)).current;
  const opacity = useRef(new Animated.Value(0)).current;
  const hideTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const toastVersion = useRef(0);
  const currentId = useRef<number | null>(null);

  const hide = useCallback(() => {
    const version = toastVersion.current;
    if (hideTimer.current) {
      clearTimeout(hideTimer.current);
      hideTimer.current = null;
    }
    Animated.parallel([
      Animated.timing(translateY, { toValue: -160, duration: 180, easing: Easing.in(Easing.quad), useNativeDriver: true }),
      Animated.timing(opacity, { toValue: 0, duration: 180, useNativeDriver: true }),
    ]).start(({ finished }) => {
      if (finished && version === toastVersion.current) setToast(null);
    });
  }, [opacity, translateY]);

  useEffect(() => {
    if (!active) { setToast(null); currentId.current = null; return; }
    const unregister = registerToastHost((next) => {
      toastVersion.current += 1;
      if (hideTimer.current) clearTimeout(hideTimer.current);
      setToast(next);
      currentId.current = next.id;
      translateY.setValue(-160);
      opacity.setValue(0);
      Animated.parallel([
        Animated.timing(translateY, { toValue: 0, duration: 240, easing: Easing.out(Easing.back(1.2)), useNativeDriver: true }),
        Animated.timing(opacity, { toValue: 1, duration: 200, useNativeDriver: true }),
      ]).start();
      hideTimer.current = setTimeout(() => hide(), VISIBLE_MS);
    }, id => { if (currentId.current === id) hide(); });

    return () => {
      toastVersion.current += 1;
      currentId.current = null;
      unregister();
      if (hideTimer.current) clearTimeout(hideTimer.current);
    };
  }, [active, hide, opacity, translateY]);

  if (!active || !toast) return null;

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
        { top: insets.top + 12, opacity, transform: [{ translateY }] },
      ]}
    >
      <View accessibilityLiveRegion="polite" style={[styles.toast, { backgroundColor: colors.card, borderColor: accent }]}>
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

        <TouchableOpacity accessibilityRole="button" accessibilityLabel="Fechar aviso" onPress={() => dismissToast(toast.id)} style={styles.closeBtn} hitSlop={{ top: 8, bottom: 8, left: 8, right: 8 }}>
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
    minWidth: 48,
    minHeight: 48,
    alignItems: 'center',
    justifyContent: 'center',
    padding: 6,
    marginLeft: 6,
  },
});
