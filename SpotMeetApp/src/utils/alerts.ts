import { Platform, Alert } from 'react-native';
import { showToast } from './toast';

/**
 * Shows an informational message.
 *
 * It is delivered as an in-app notification at the bottom of the screen (ToastHost),
 * which dismisses on its own instead of blocking the user with a system dialog.
 * Falls back to the native dialog if the host is not mounted yet (e.g. before the
 * providers render, or on web without the host).
 *
 * Decisions that need an answer must keep using confirmAction(): a toast has no buttons.
 */
export function showAlert(title: string, message: string): void {
  if (showToast(title, message)) return;

  if (Platform.OS === 'web') {
    window.alert(`${title}: ${message}`);
  } else {
    Alert.alert(title, message);
  }
}

/**
 * Shows a confirmation dialog compatible with Web (window.confirm) and native mobile.
 */
export function confirmAction(
  title: string,
  message: string,
  onConfirm: () => void,
  confirmText: string = 'Confirmar',
  destructive: boolean = false
): void {
  if (Platform.OS === 'web') {
    const accepted = window.confirm(`${title}\n\n${message}`);
    if (accepted) {
      onConfirm();
    }
  } else {
    Alert.alert(title, message, [
      { text: 'Cancelar', style: 'cancel' },
      {
        text: confirmText,
        style: destructive ? 'destructive' : 'default',
        onPress: onConfirm,
      },
    ]);
  }
}
