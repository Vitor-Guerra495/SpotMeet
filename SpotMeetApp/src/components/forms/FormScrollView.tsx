import React, { useEffect, useRef } from 'react';
import { Keyboard, KeyboardAvoidingView, ScrollView, ScrollViewProps, type ScrollViewInstance } from 'react-native';
import { AuthInputFocus } from '../auth/AuthInputFocus';

interface FormScrollViewProps extends ScrollViewProps { footer?: React.ReactNode; scrollableFooter?: boolean; }

export default function FormScrollView({ children, footer, scrollableFooter = false, style, ...props }: FormScrollViewProps) {
  const scroll = useRef<ScrollViewInstance>(null);
  const footerScroll = useRef<ScrollViewInstance>(null);
  const inputScroll = useRef(scroll);
  const target = useRef<number | null>(null);
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);

  const revealInput = (input: number) => {
    target.current = input;
    if (timer.current) clearTimeout(timer.current);
    timer.current = setTimeout(() => {
      const view = inputScroll.current.current;
      const field = target.current;
      if (!view || field === null) return;
      // React Native places the field as if the list started at the top of the screen. Inside a sheet the list
      // starts lower, so its top is added; without it the field ends up behind the keyboard.
      view.measureInWindow((_x, top) => {
        if (target.current === field) view.scrollResponderScrollNativeHandleToKeyboard(field, 16 + Math.max(0, top), true);
      });
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
