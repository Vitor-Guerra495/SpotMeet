import React, { useEffect, useRef, useState } from 'react';
import {
  View, Text, StyleSheet, ScrollView, Image, KeyboardAvoidingView,
  Keyboard, TouchableOpacity, useWindowDimensions,
} from 'react-native';
import { Ionicons } from '@react-native-vector-icons/ionicons';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useTheme } from '../../context/ThemeContext';
import { getAuthLayoutMetrics, shouldScrollAuth } from '../../utils/authLayout';
import { AuthInputFocus, AuthCompactLayout } from './AuthInputFocus';

interface AuthLayoutProps {
  title: string;
  subtitle: string;
  cardTitle?: string;
  cardSubtitle?: string;
  children: React.ReactNode;
  variant?: 'login' | 'flow';
  step?: number;
  onBack?: () => void;
}

export default function AuthLayout({
  title, subtitle, cardTitle, cardSubtitle, children, variant = 'flow', step, onBack,
}: AuthLayoutProps) {
  const { colors } = useTheme();
  const insets = useSafeAreaInsets();
  const { height, fontScale } = useWindowDimensions();
  const metrics = getAuthLayoutMetrics(height, insets.top, insets.bottom, fontScale);
  const [keyboardVisible, setKeyboardVisible] = useState(false);
  const [viewportHeight, setViewportHeight] = useState(0);
  const [contentHeight, setContentHeight] = useState(0);
  const scroll = useRef<ScrollView>(null);
  const focusedTarget = useRef<number | null>(null);
  const focusTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const backgroundColor = colors.isDark ? '#4C51C6' : '#5C6BC0';
  const compact = metrics.compact || keyboardVisible || variant !== 'login';

  const revealInput = (target: number) => {
    focusedTarget.current = target;
    if (focusTimer.current) clearTimeout(focusTimer.current);
    focusTimer.current = setTimeout(() => {
      if (focusedTarget.current !== null) {
        scroll.current?.scrollResponderScrollNativeHandleToKeyboard(focusedTarget.current, 16, true);
      }
    }, 150);
  };

  useEffect(() => {
    const show = Keyboard.addListener('keyboardDidShow', () => {
      setKeyboardVisible(true);
      if (focusedTarget.current !== null) revealInput(focusedTarget.current);
    });
    const hide = Keyboard.addListener('keyboardDidHide', () => {
      setKeyboardVisible(false);
      focusedTarget.current = null;
      scroll.current?.scrollTo({ y: 0, animated: false });
    });
    return () => {
      show.remove();
      hide.remove();
      if (focusTimer.current) clearTimeout(focusTimer.current);
    };
  }, []);

  useEffect(() => {
    focusedTarget.current = null;
    if (focusTimer.current) clearTimeout(focusTimer.current);
    scroll.current?.scrollTo({ y: 0, animated: false });
  }, [cardTitle]);

  return (
    <AuthInputFocus.Provider value={revealInput}>
      <AuthCompactLayout.Provider value={metrics.dense || keyboardVisible}>
      <View style={[styles.root, { backgroundColor, paddingTop: insets.top }]}>
        <KeyboardAvoidingView style={styles.root} behavior="padding">
          <ScrollView
            ref={scroll} style={styles.root}
            contentContainerStyle={[styles.content, { minHeight: viewportHeight, paddingBottom: insets.bottom }]}
            onLayout={event => setViewportHeight(event.nativeEvent.layout.height)}
            onContentSizeChange={(_, size) => setContentHeight(size)}
            scrollEnabled={shouldScrollAuth(variant, keyboardVisible, contentHeight, viewportHeight)}
            alwaysBounceVertical={false} bounces={false} overScrollMode="never"
            keyboardShouldPersistTaps="handled" showsVerticalScrollIndicator={false}
          >
            <View style={[styles.wrapper, { minHeight: Math.max(0, viewportHeight - insets.bottom) }]}>
              <View style={[styles.brand, {
                minHeight: variant === 'login' && !keyboardVisible ? metrics.brandHeight : 110,
                flexDirection: compact ? 'row' : 'column',
                paddingVertical: metrics.dense ? 8 : 14,
              }]}>
                {onBack ? (
                  <TouchableOpacity style={styles.back} onPress={onBack}
                    accessibilityRole="button" accessibilityLabel="Voltar">
                    <Ionicons name="arrow-back" size={24} color="#FFFFFF" />
                  </TouchableOpacity>
                ) : null}
                <Image source={require('../../../assets/icon.png')}
                  style={{ width: compact ? 48 : 78, height: compact ? 48 : 78, borderRadius: compact ? 12 : 20 }}
                  resizeMode="contain" accessibilityLabel="SpotMeet" />
                <View style={compact ? styles.brandTextRow : styles.brandText}>
                  <Text style={[styles.title, { fontSize: compact ? 23 : 28 }]}>{title}</Text>
                  {!metrics.compact || variant !== 'login' ? (
                    <Text style={styles.subtitle}>{subtitle}</Text>
                  ) : null}
                </View>
              </View>
              <View style={[styles.card, { backgroundColor: colors.card, padding: metrics.dense ? 12 : metrics.compact ? 16 : 22 }]}>
                {step ? (
                  <View style={styles.steps} accessibilityLabel={`Etapa ${step} de 3`}>
                    {[1, 2, 3].map(index => <View key={index}
                      style={[styles.step, { backgroundColor: index <= step ? colors.primary : colors.border }]} />)}
                  </View>
                ) : null}
                {cardTitle ? <Text style={[styles.cardTitle, {
                  color: colors.text, fontSize: metrics.dense ? 20 : 22, lineHeight: metrics.dense ? 24 : 28,
                }]}>{cardTitle}</Text> : null}
                {cardSubtitle && (!metrics.compact || variant !== 'login') ? (
                  <Text style={[styles.cardSubtitle, { color: colors.subtext }]}>{cardSubtitle}</Text>
                ) : null}
                {children}
              </View>
            </View>
          </ScrollView>
        </KeyboardAvoidingView>
      </View>
      </AuthCompactLayout.Provider>
    </AuthInputFocus.Provider>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1 },
  content: { flexGrow: 1, alignItems: 'center' },
  wrapper: { width: '100%', maxWidth: 440 },
  brand: { paddingHorizontal: 20, paddingVertical: 14, alignItems: 'center', justifyContent: 'center', gap: 10 },
  brandText: { alignItems: 'center', gap: 5 },
  brandTextRow: { flex: 1, gap: 4 },
  title: { fontWeight: '700', color: '#FFFFFF', textAlign: 'center' },
  subtitle: { fontSize: 12, lineHeight: 17, color: '#FFFFFF', textAlign: 'center' },
  back: { minWidth: 48, minHeight: 48, justifyContent: 'center', alignItems: 'center' },
  card: { flexGrow: 1, borderTopLeftRadius: 28, borderTopRightRadius: 28 },
  cardTitle: { fontSize: 22, lineHeight: 28, fontWeight: '700', marginBottom: 5 },
  cardSubtitle: { fontSize: 14, lineHeight: 20, marginBottom: 18 },
  steps: { flexDirection: 'row', gap: 6, marginBottom: 18 },
  step: { flex: 1, height: 4, borderRadius: 2 },
});
