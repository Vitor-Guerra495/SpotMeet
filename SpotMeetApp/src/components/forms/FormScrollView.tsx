import React, { useEffect, useRef } from 'react';
import { Keyboard, KeyboardAvoidingView, ScrollView, ScrollViewProps } from 'react-native';
import { AuthInputFocus } from '../auth/AuthInputFocus';

interface FormScrollViewProps extends ScrollViewProps { footer?: React.ReactNode; scrollableFooter?: boolean; }

export default function FormScrollView({ children, footer, scrollableFooter = false, style, ...props }: FormScrollViewProps) {
  const scroll = useRef<ScrollView>(null);
  const footerScroll = useRef<ScrollView>(null);
  const inputScroll = useRef(scroll);
  const target = useRef<number | null>(null);
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);

  const revealInput = (input: number) => {
    target.current = input;
    if (timer.current) clearTimeout(timer.current);
    timer.current = setTimeout(() => {
      if (target.current !== null) {
        inputScroll.current.current?.scrollResponderScrollNativeHandleToKeyboard(target.current, 16, true);
      }
    }, 150);
  };

  useEffect(() => {
    const show = Keyboard.addListener('keyboardDidShow', () => {
      if (target.current !== null) revealInput(target.current);
    });
    const hide = Keyboard.addListener('keyboardDidHide', () => { target.current = null; });
    return () => {
      show.remove();
      hide.remove();
      if (timer.current) clearTimeout(timer.current);
    };
  }, []);

  return (
    <AuthInputFocus.Provider value={input => { inputScroll.current = scroll; revealInput(input); }}>
      <KeyboardAvoidingView behavior="padding" style={{ flex: 1 }}>
        <ScrollView ref={scroll} style={[{ flex: 1 }, style]} keyboardShouldPersistTaps="handled"
          showsVerticalScrollIndicator={false} {...props}>
          {children}
        </ScrollView>
        {scrollableFooter ? <AuthInputFocus.Provider value={input => { inputScroll.current = footerScroll; revealInput(input); }}>
          <ScrollView ref={footerScroll} style={{ flexGrow: 0, flexShrink: 1 }} keyboardShouldPersistTaps="handled" showsVerticalScrollIndicator={false}>
            {footer}
          </ScrollView>
        </AuthInputFocus.Provider> : footer}
      </KeyboardAvoidingView>
    </AuthInputFocus.Provider>
  );
}
