import { Platform, Alert } from 'react-native';
import { showToast } from './toast';
import { requestConfirmation } from './confirmation';

/** Shows a top notification, with a system-message fallback before the UI mounts. */
export function showAlert(title: string, message: string): void {
  if (showToast(title, message)) return;

  if (Platform.OS === 'web') {
    window.alert(`${title}: ${message}`);
  } else {
    Alert.alert(title, message);
  }
}

/**
 * Requests an in-app confirmation; an unavailable host cancels the action.
 */
export function confirmAction(
  title: string,
  message: string,
  onConfirm: () => void | Promise<void>,
  confirmText: string = 'Confirmar',
  destructive: boolean = false,
  onClosed?: () => void
): void {
  requestConfirmation({ title, message, onConfirm, confirmText, destructive, onClosed });
}
