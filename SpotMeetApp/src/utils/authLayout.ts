export function getAuthLayoutMetrics(height: number, top: number, bottom: number, fontScale = 1) {
  const available = Math.max(0, height - top - bottom);
  return {
    compact: available < 740 || fontScale > 1.15,
    dense: available < 640,
    brandHeight: available < 640 ? 64 : available < 740 ? 110 : 190,
  };
}

export function shouldScrollAuth(mode: 'login' | 'flow', keyboardVisible: boolean, contentHeight: number, viewportHeight: number) {
  return mode !== 'login' || keyboardVisible || (viewportHeight > 0 && contentHeight > viewportHeight + 2);
}
