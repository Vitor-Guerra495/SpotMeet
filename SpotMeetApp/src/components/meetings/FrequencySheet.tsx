import React, { useEffect, useState } from 'react';
import { Modal, Text, TextInput, TouchableOpacity, View } from 'react-native';
import { Ionicons } from '@react-native-vector-icons/ionicons';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import FormButton from '../forms/FormButton';
import FormScrollView from '../forms/FormScrollView';
import DateTimeField from './DateTimeField';
import { useMeetingStyles } from './meetingStyles';
import { capitalize, formatMonthDay, parseLocal, recurrenceText, toLocalDate, type Frequency, type Recurrence } from '../../utils/meetings';

interface Props {
  visible: boolean;
  anchor: Date;
  value: Recurrence | null;
  onDone: (value: Recurrence | null) => void;
  onClose: () => void;
}

type End = 'never' | 'date' | 'count';
const UNITS: { frequency: Frequency; label: string }[] = [
  { frequency: 'DAILY', label: 'dia' }, { frequency: 'WEEKLY', label: 'semana' }, { frequency: 'MONTHLY', label: 'mês' }, { frequency: 'YEARLY', label: 'ano' },
];
// Buttons from Sunday to Saturday, with the ISO number the server uses (7 = Sunday).
const DAYS = [{ iso: 7, letter: 'D' }, { iso: 1, letter: 'S' }, { iso: 2, letter: 'T' }, { iso: 3, letter: 'Q' }, { iso: 4, letter: 'Q' }, { iso: 5, letter: 'S' }, { iso: 6, letter: 'S' }];
const DAY_NAMES: Record<number, string> = { 1: 'segunda', 2: 'terça', 3: 'quarta', 4: 'quinta', 5: 'sexta', 6: 'sábado', 7: 'domingo' };

const rule = (frequency: Frequency, extra: Partial<Recurrence> = {}): Recurrence =>
  ({ frequency, interval: 1, weekDays: [], monthlyByWeekday: false, endDate: null, occurrences: null, ...extra });

const sameRule = (a: Recurrence | null, b: Recurrence | null) => JSON.stringify(a) === JSON.stringify(b);

/** "Frequência" of the form, with the options of the Google Calendar and the page "Personalizar frequência". */
export default function FrequencySheet({ visible, anchor, value, onDone, onClose }: Props) {
  const insets = useSafeAreaInsets();
  const { colors, styles } = useMeetingStyles();
  const isoDay = anchor.getDay() || 7;
  const presets: { label: string; value: Recurrence | null }[] = [
    { label: 'Não se repete', value: null },
    { label: 'Todos os dias', value: rule('DAILY') },
    { label: `Semanal: ${recurrenceText(rule('WEEKLY'), anchor)}`, value: rule('WEEKLY') },
    { label: `Mensal: ${recurrenceText(rule('MONTHLY', { monthlyByWeekday: true }), anchor).replace(/^toda /, 'na ').replace(/^todo /, 'no ')}`, value: rule('MONTHLY', { monthlyByWeekday: true }) },
    { label: `Anual: em ${formatMonthDay(anchor)}`, value: rule('YEARLY') },
    { label: 'Todos os dias úteis (segunda a sexta)', value: rule('WEEKLY', { weekDays: [1, 2, 3, 4, 5] }) },
  ];
  const [choice, setChoice] = useState<Recurrence | null>(value);
  const [page, setPage] = useState<'list' | 'custom'>('list');
  const [every, setEvery] = useState('1');
  const [frequency, setFrequency] = useState<Frequency>('WEEKLY');
  const [days, setDays] = useState<number[]>([isoDay]);
  const [byWeekday, setByWeekday] = useState(false);
  const [end, setEnd] = useState<End>('never');
  const [endDate, setEndDate] = useState(() => new Date(anchor.getFullYear(), anchor.getMonth() + 1, anchor.getDate()));
  const [count, setCount] = useState('');
  const [error, setError] = useState('');

  useEffect(() => {
    if (!visible) return;
    setChoice(value); setPage('list'); setError('');
    const base = value ?? rule('WEEKLY');
    setEvery(String(base.interval)); setFrequency(base.frequency); setDays(base.weekDays.length ? base.weekDays : [isoDay]);
    setByWeekday(base.monthlyByWeekday); setEnd(base.endDate ? 'date' : base.occurrences ? 'count' : 'never');
    if (base.endDate) setEndDate(parseLocal(base.endDate));
    setCount(base.occurrences ? String(base.occurrences) : '');
  }, [visible]);

  const custom: Recurrence = {
    frequency, interval: Math.max(1, Number(every) || 1), weekDays: frequency === 'WEEKLY' ? [...days].sort((a, b) => a - b) : [],
    monthlyByWeekday: frequency === 'MONTHLY' && byWeekday, endDate: end === 'date' ? toLocalDate(endDate) : null,
    occurrences: end === 'count' ? Number(count) || 0 : null,
  };
  const customIsPreset = !choice || presets.some(preset => sameRule(preset.value, choice));

  const finishCustom = () => {
    const times = Number(every);
    if (!Number.isInteger(times) || times < 1 || times > 99) { setError('Repita a cada 1 a 99.'); return; }
    if (end === 'count' && !(Number(count) >= 1 && Number(count) <= 365)) { setError('Informe quantas vezes a reunião se repete.'); return; }
    if (end === 'date' && endDate < new Date(anchor.getFullYear(), anchor.getMonth(), anchor.getDate())) {
      setError('A repetição precisa terminar depois do primeiro dia.'); return;
    }
    onDone(custom);
  };

  const radio = (selected: boolean) => <Ionicons name={selected ? 'radio-button-on' : 'radio-button-off'} size={24} color={selected ? colors.primary : colors.subtext} />;
  const option = (label: string, selected: boolean, onPress: () => void, trailing?: React.ReactNode) =>
    <TouchableOpacity key={label} accessibilityRole="radio" accessibilityState={{ selected }} onPress={onPress} style={[styles.option, selected && styles.optionActive]}>
      <Text style={[styles.optionText, selected && { fontWeight: '700' }]}>{label}</Text>{trailing ?? radio(selected)}
    </TouchableOpacity>;

  const header = (subtitle: string, title: string, onBack: () => void) => <View style={styles.sheetHeader}>
    <TouchableOpacity accessibilityRole="button" accessibilityLabel="Voltar" style={styles.iconButton} onPress={onBack}>
      <Ionicons name="arrow-back" size={24} color={colors.text} /></TouchableOpacity>
    <View style={styles.flex}><Text style={styles.subtitle}>{subtitle}</Text><Text style={styles.sheetTitle}>{title}</Text></View>
  </View>;

  const footer = (onConfirm: () => void, onCancel: () => void) => <View style={styles.footer}>
    {error ? <Text accessibilityRole="alert" style={styles.error}>{error}</Text> : null}
    <FormButton label="Concluir" icon="checkmark-outline" onPress={onConfirm} />
    <FormButton label="Cancelar" secondary onPress={onCancel} />
  </View>;

  return <Modal visible={visible} transparent animationType="slide" onRequestClose={() => (page === 'custom' ? setPage('list') : onClose())}>
    <View style={styles.overlay}><View style={[styles.sheet, { paddingBottom: insets.bottom }]}><View style={styles.sheetHandle} />
      {page === 'list' ? <>
        {header('Agendar reunião', 'Frequência', onClose)}
        <FormScrollView contentContainerStyle={styles.content} footer={footer(() => onDone(choice), onClose)}>
          {presets.map(preset => option(preset.label, sameRule(preset.value, choice), () => setChoice(preset.value)))}
          {!customIsPreset && choice && option(capitalize(recurrenceText(choice, anchor)), true, () => setPage('custom'))}
          {option('Personalizar...', false, () => { setError(''); setPage('custom'); }, <Ionicons name="chevron-forward" size={22} color={colors.subtext} />)}
        </FormScrollView>
      </> : <>
        {header('Frequência', 'Personalizar frequência', () => setPage('list'))}
        <FormScrollView contentContainerStyle={styles.content} footer={footer(finishCustom, () => setPage('list'))}>
          <Text style={styles.fieldLabel}>REPETIR A CADA</Text>
          <View style={[styles.row, { marginBottom: 18 }]}>
            <TextInput accessibilityLabel="Repetir a cada" keyboardType="number-pad" maxLength={2} value={every}
              onChangeText={value => setEvery(value.replace(/\D/g, ''))} style={[styles.numberInput, { width: 72 }]} />
            <View style={[styles.segment, styles.flex]}>{UNITS.map(unit => <TouchableOpacity key={unit.frequency} accessibilityRole="tab"
              accessibilityState={{ selected: frequency === unit.frequency }} onPress={() => setFrequency(unit.frequency)}
              style={[styles.segmentItem, frequency === unit.frequency && styles.segmentActive]}>
              <Text style={[styles.segmentText, frequency === unit.frequency && { color: colors.isDark ? '#ADB5EF' : colors.primary, fontWeight: '700' }]}>{unit.label}</Text>
            </TouchableOpacity>)}</View>
          </View>

          {frequency === 'WEEKLY' && <>
            <Text style={styles.fieldLabel}>REPETIR NOS DIAS</Text>
            <View style={[styles.row, { justifyContent: 'space-between', marginBottom: 18, gap: 4 }]}>{DAYS.map(day => {
              const selected = days.includes(day.iso);
              return <TouchableOpacity key={day.iso} accessibilityRole="checkbox" accessibilityLabel={DAY_NAMES[day.iso]} accessibilityState={{ checked: selected }}
                onPress={() => setDays(current => selected ? (current.length > 1 ? current.filter(item => item !== day.iso) : current) : [...current, day.iso])}
                style={[styles.dayCircle, selected && styles.dayCircleActive]}>
                <Text style={[styles.dayCircleText, selected && { color: '#FFFFFF' }]}>{day.letter}</Text>
              </TouchableOpacity>;
            })}</View>
          </>}

          {frequency === 'MONTHLY' && <>
            <Text style={styles.fieldLabel}>REPETIR NO MÊS</Text>
            {option(`Todo dia ${anchor.getDate()}`, !byWeekday, () => setByWeekday(false), radio(!byWeekday))}
            {option(capitalize(recurrenceText(rule('MONTHLY', { monthlyByWeekday: true }), anchor).replace(' do mês', '')), byWeekday, () => setByWeekday(true), radio(byWeekday))}
          </>}

          <Text style={[styles.fieldLabel, { marginTop: 8 }]}>TERMINA</Text>
          {option('Nunca', end === 'never', () => setEnd('never'))}
          <TouchableOpacity accessibilityRole="radio" accessibilityState={{ selected: end === 'date' }} onPress={() => setEnd('date')}
            style={[styles.option, end === 'date' && styles.optionActive, { gap: 12 }]}>
            {radio(end === 'date')}<Text style={styles.optionText}>Em</Text>
            <View style={styles.flex} pointerEvents={end === 'date' ? 'auto' : 'none'}>
              <DateTimeField label="Data final da repetição" mode="date" value={endDate} minimumDate={anchor}
                onChange={value => { setEndDate(value); setEnd('date'); }} />
            </View>
          </TouchableOpacity>
          <TouchableOpacity accessibilityRole="radio" accessibilityState={{ selected: end === 'count' }} onPress={() => setEnd('count')}
            style={[styles.option, end === 'count' && styles.optionActive, { gap: 12 }]}>
            {radio(end === 'count')}<Text style={styles.optionText}>Após</Text>
            <TextInput accessibilityLabel="Número de ocorrências" keyboardType="number-pad" maxLength={3} value={count} placeholder="0"
              placeholderTextColor={colors.subtext} onFocus={() => setEnd('count')} onChangeText={value => setCount(value.replace(/\D/g, ''))}
              style={[styles.numberInput, { width: 72 }, error && end === 'count' ? { borderColor: colors.danger } : null]} />
            <Text style={styles.optionText}>ocorrências</Text>
          </TouchableOpacity>

          <View style={[styles.notice, { marginTop: 12 }]}>
            <Ionicons name="repeat-outline" size={22} color={colors.primary} />
            <View style={styles.flex}><Text style={styles.cardTitle}>Resumo</Text>
              <Text style={styles.infoText}>{capitalize(recurrenceText({ ...custom, occurrences: custom.occurrences || null }, anchor))}.</Text></View>
          </View>
        </FormScrollView>
      </>}
    </View></View>
  </Modal>;
}

