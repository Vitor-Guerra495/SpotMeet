import React, { useState } from 'react';
import { Modal, Platform, Text, TouchableOpacity, View } from 'react-native';
import { Ionicons, type IoniconsIconName } from '@react-native-vector-icons/ionicons';
import DateTimePicker, { DateTimePickerAndroid } from '@react-native-community/datetimepicker';
import FormButton from '../forms/FormButton';
import { useMeetingStyles } from './meetingStyles';
import { capitalize, formatClock, formatDay } from '../../utils/meetings';

interface FieldProps {
  label?: string;
  value: string;
  icon?: IoniconsIconName;
  onPress: () => void;
  disabled?: boolean;
  error?: string;
  chevron?: 'down' | 'forward' | null;
  accessibilityLabel?: string;
}

/** Field that opens a choice (date, time, frequency, guests) instead of receiving typed text. */
export function PickerField({ label, value, icon, onPress, disabled, error, chevron = 'down', accessibilityLabel }: FieldProps) {
  const { colors, styles } = useMeetingStyles();
  return <View style={{ marginBottom: 12, flex: 1 }}>
    {label ? <Text style={styles.fieldLabel}>{label.toUpperCase()}</Text> : null}
    <TouchableOpacity accessibilityRole="button" accessibilityLabel={accessibilityLabel ?? `${label ?? ''} ${value}`.trim()}
      accessibilityState={{ disabled: !!disabled }} accessibilityHint={error} disabled={disabled} onPress={onPress}
      style={[styles.pickerFrame, error ? { borderColor: colors.danger } : null, disabled && { opacity: 0.6 }]}>
      {icon ? <Ionicons name={icon} size={20} color={colors.subtext} /> : null}
      <Text style={[styles.pickerText, disabled && { color: colors.subtext }]} numberOfLines={1}>{value}</Text>
      {chevron && !disabled ? <Ionicons name={chevron === 'down' ? 'chevron-down' : 'chevron-forward'} size={20} color={colors.subtext} /> : null}
    </TouchableOpacity>
    {error ? <Text accessibilityRole="alert" style={styles.fieldError}>{error}</Text> : null}
  </View>;
}

interface Props {
  label: string;
  mode: 'date' | 'time';
  value: Date;
  onChange: (value: Date) => void;
  minimumDate?: Date;
  disabled?: boolean;
  error?: string;
}

/** Date or time chosen in the picker of the phone: a dialog on Android and a sheet with the wheel on iOS. */
export default function DateTimeField({ label, mode, value, onChange, minimumDate, disabled, error }: Props) {
  const { colors, styles } = useMeetingStyles();
  const [draft, setDraft] = useState<Date | null>(null);
  const text = mode === 'date' ? capitalize(formatDay(value)) : formatClock(value);

  const open = () => {
    if (Platform.OS === 'android') {
      DateTimePickerAndroid.open({
        value, mode, is24Hour: true, minimumDate,
        onChange: (event, date) => { if (event.type === 'set' && date) onChange(date); },
      });
    } else {
      setDraft(value);
    }
  };

  return <>
    <PickerField label={label} value={text} icon={mode === 'date' ? 'calendar-outline' : undefined} chevron={mode === 'date' ? 'down' : null}
      onPress={open} disabled={disabled} error={error} accessibilityLabel={`${label}: ${text}`} />
    {Platform.OS !== 'android' && <Modal visible={draft !== null} transparent animationType="fade" onRequestClose={() => setDraft(null)}>
      <View style={styles.overlay}><View style={[styles.sheet, { height: undefined, padding: 20 }]}>
        <Text style={styles.sheetTitle}>{label}</Text>
        {draft && <DateTimePicker value={draft} mode={mode} display={mode === 'date' ? 'inline' : 'spinner'} locale="pt-BR"
          minimumDate={minimumDate} themeVariant={colors.isDark ? 'dark' : 'light'} onChange={(_, date) => { if (date) setDraft(date); }} />}
        <FormButton label="Concluir" icon="checkmark-outline" onPress={() => { if (draft) onChange(draft); setDraft(null); }} />
        <FormButton label="Cancelar" secondary onPress={() => setDraft(null)} />
      </View></View>
    </Modal>}
  </>;
}
