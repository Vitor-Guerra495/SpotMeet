import React from 'react';
import {
  View,
  Text,
  StyleSheet,
  ScrollView,
  Image,
  KeyboardAvoidingView,
} from 'react-native';
import { useTheme } from '../../context/ThemeContext';

interface AuthLayoutProps {
  title: string;
  subtitle: string;
  cardTitle?: string;
  children: React.ReactNode;
  brandBackground?: boolean;
}

export default function AuthLayout({
  title,
  subtitle,
  cardTitle,
  children,
  brandBackground = true,
}: AuthLayoutProps) {
  const { colors } = useTheme();

  const bgColor = brandBackground
    ? (colors.isDark ? '#4C51C6' : '#5C6BC0')
    : colors.background;

  return (
    <KeyboardAvoidingView
      style={styles.flex}
      // 'padding' on both platforms: with edge-to-edge (Expo SDK 53+) Android ignores adjustResize
      behavior="padding"
    >
      <ScrollView
        contentContainerStyle={[styles.container, { backgroundColor: bgColor }]}
        keyboardShouldPersistTaps="handled"
        showsVerticalScrollIndicator={false}
      >
        <View style={styles.contentWrapper}>
          {/* Logo and brand identity */}
          <View style={styles.iconContainer}>
            <Image
              source={require('../../../assets/icone.jpeg')}
              style={styles.logoImage}
              resizeMode="cover"
            />
          </View>

          <Text style={styles.title}>{title}</Text>
          <Text style={styles.subtitle}>{subtitle}</Text>

          {/* Access card / form */}
          <View style={[styles.card, { backgroundColor: colors.card, borderColor: colors.border }]}>
            {cardTitle ? <Text style={[styles.cardTitle, { color: colors.text }]}>{cardTitle}</Text> : null}
            {children}
          </View>
        </View>
      </ScrollView>
    </KeyboardAvoidingView>
  );
}

const styles = StyleSheet.create({
  flex: {
    flex: 1,
  },
  container: {
    flexGrow: 1,
    alignItems: 'center',
    justifyContent: 'center',
    padding: 20,
  },
  contentWrapper: {
    width: '100%',
    maxWidth: 420,
    alignItems: 'center',
  },
  iconContainer: {
    width: 80,
    height: 80,
    borderRadius: 20,
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: 15,
    overflow: 'hidden',
    borderWidth: 2,
    borderColor: 'rgba(255, 255, 255, 0.4)',
  },
  logoImage: {
    width: 80,
    height: 80,
    borderRadius: 18,
  },
  title: {
    fontSize: 28,
    fontWeight: 'bold',
    color: '#FFFFFF',
    marginBottom: 5,
    textAlign: 'center',
  },
  subtitle: {
    fontSize: 14,
    color: '#E0E0E0',
    marginBottom: 24,
    textAlign: 'center',
    paddingHorizontal: 10,
    lineHeight: 19,
  },
  card: {
    width: '100%',
    borderRadius: 15,
    padding: 25,
    borderWidth: 1,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.25,
    shadowRadius: 8,
    elevation: 5,
  },
  cardTitle: {
    fontSize: 16,
    fontWeight: 'bold',
    marginBottom: 20,
  },
});
