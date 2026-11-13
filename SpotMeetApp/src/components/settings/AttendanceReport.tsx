import React, { useEffect, useState } from 'react';
import { ActivityIndicator, BackHandler, Text, TouchableOpacity, View } from 'react-native';
import { Ionicons } from '@react-native-vector-icons/ionicons';
import FormButton from '../forms/FormButton';
import DateTimeField from '../meetings/DateTimeField';
import { MeetingBadge, useMeetingStyles } from '../meetings/meetingStyles';
import { apiRequest } from '../../utils/apiRequest';
import { dateBlock, formatMonthDay, formatTime, parseLocal } from '../../utils/meetings';
import { PERIOD_LABELS, formatTotal, periodRange, readReport, type AttendanceReport as Report, type ReportPeriod } from '../../utils/report';

const MONTHS = ['JANEIRO', 'FEVEREIRO', 'MARÇO', 'ABRIL', 'MAIO', 'JUNHO', 'JULHO', 'AGOSTO', 'SETEMBRO', 'OUTUBRO', 'NOVEMBRO', 'DEZEMBRO'];
const PERIODS: ReportPeriod[] = ['month', 'quarter', 'year', 'all', 'custom'];

/**
 * "Meu Relatório": meetings where the user registered an entry, with filters by period and committee.
 * Only the user's own data: the server takes the user from the session.
 */
export default function AttendanceReport({ token }: { token: string | null }) {
  const { colors, styles } = useMeetingStyles();
  const [period, setPeriod] = useState<ReportPeriod>('quarter');
  const [committeeId, setCommitteeId] = useState<number | null>(null);
  const [custom, setCustom] = useState<{ from: Date; to: Date } | null>(null);
  const [editing, setEditing] = useState<{ from: Date; to: Date } | null>(null);
  const [customError, setCustomError] = useState('');
  const [report, setReport] = useState<Report | null>(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);
  const [reload, setReload] = useState(0);

  useEffect(() => {
    let current = true;
    const range = periodRange(period, new Date(), custom ?? undefined);
    const query = [range.from && `from=${range.from}`, range.to && `to=${range.to}`, committeeId && `committeeId=${committeeId}`].filter(Boolean).join('&');
    setLoading(true); setError('');
    void apiRequest(token, `/users/me/report${query ? `?${query}` : ''}`).then(result => {
      if (!current) return;
      setLoading(false);
      // Old totals would look like the answer for the new filter, so they are cleared.
      if (!result.ok) { setError(result.error); setReport(null); return; }
      try { setReport(readReport(result.data)); } catch { setError('Não foi possível ler o relatório. Tente novamente.'); }
    });
    return () => { current = false; };
  }, [token, period, committeeId, custom, reload]);

  // While the dates are being chosen, the back button of the phone works as "Cancelar".
  const choosing = editing !== null;
  useEffect(() => {
    if (!choosing) return;
    const subscription = BackHandler.addEventListener('hardwareBackPress', () => { setEditing(null); setCustomError(''); return true; });
    return () => subscription.remove();
  }, [choosing]);

  const chip = (label: string, selected: boolean, onPress: () => void) => <TouchableOpacity key={label} accessibilityRole="radio" accessibilityState={{ checked: selected }}
    style={[styles.filter, selected && styles.filterActive]} onPress={onPress}>
    <Text style={[styles.filterText, selected && { color: '#FFFFFF' }]}>{label}</Text>
  </TouchableOpacity>;
  const tile = (value: string, label: string) => <View style={[styles.card, { flex: 1, alignItems: 'center', paddingHorizontal: 8 }]}>
    <Text style={[styles.cardTitle, { fontSize: 20, lineHeight: 26 }]} numberOfLines={1} adjustsFontSizeToFit>{value}</Text>
    <Text style={styles.subtitle}>{label}</Text>
  </View>;

  if (editing) {
    const apply = () => {
      if (editing.to < editing.from) { setCustomError('A data final não pode ser antes da data inicial.'); return; }
      setCustom(editing); setPeriod('custom'); setEditing(null); setCustomError('');
    };
    return <View>
      <Text style={styles.sheetTitle}>Período Personalizado</Text>
      <View style={{ marginTop: 14 }}>
        <DateTimeField label="Data inicial" mode="date" value={editing.from} onChange={from => { setEditing({ ...editing, from }); setCustomError(''); }} />
        <DateTimeField label="Data final" mode="date" value={editing.to} onChange={to => { setEditing({ ...editing, to }); setCustomError(''); }} error={customError || undefined} />
      </View>
      <Text style={[styles.hint, { marginBottom: 12 }]}>O relatório mostra as reuniões em que você registrou entrada nesse período.</Text>
      <FormButton label="Aplicar Período" icon="checkmark-outline" onPress={apply} />
      <FormButton label="Cancelar" secondary onPress={() => { setEditing(null); setCustomError(''); }} />
    </View>;
  }

  // Rows grouped by month, newest first, as they come from the server.
  const groups: { key: string; label: string; rows: Report['rows'] }[] = [];
  for (const row of report?.rows ?? []) {
    const date = parseLocal(row.startsAt);
    const key = `${date.getFullYear()}-${date.getMonth()}`;
    let group = groups.find(item => item.key === key);
    if (!group) { group = { key, label: `${MONTHS[date.getMonth()]} DE ${date.getFullYear()}`, rows: [] }; groups.push(group); }
    group.rows.push(row);
  }
  const committeeName = report?.committees.find(item => item.committeeId === committeeId)?.committeeName;

  return <View>
    <View style={[styles.row, { gap: 8, alignItems: 'stretch' }]}>
      {tile(String(report?.totalMeetings ?? 0), 'Reuniões')}
      {tile(formatTotal(report?.totalMinutes ?? 0), 'Horas no total')}
      {tile(String(report?.totalCommittees ?? 0), 'Comissões')}
    </View>
    <Text style={styles.sectionLabel}>PERÍODO</Text>
    <View style={styles.filters}>
      {PERIODS.map(value => chip(value === 'custom' ? 'Escolher as datas' : PERIOD_LABELS[value], period === value, () => {
        if (value === 'custom') { const today = new Date(); setEditing(custom ?? { from: new Date(today.getFullYear(), today.getMonth(), 1), to: today }); }
        else setPeriod(value);
      }))}
    </View>
    {period === 'custom' && custom ? <Text style={[styles.hint, { marginTop: -8 }]}>De {formatMonthDay(custom.from)} a {formatMonthDay(custom.to)}.</Text> : null}
    <Text style={styles.sectionLabel}>COMISSÃO</Text>
    <View style={styles.filters}>
      {chip('Todas as suas comissões', committeeId === null, () => setCommitteeId(null))}
      {(report?.committees ?? []).map(item => chip(item.committeeName, committeeId === item.committeeId, () => setCommitteeId(item.committeeId)))}
    </View>
    {report?.missingExit ? <Text style={[styles.hint, { marginTop: -6, marginBottom: 8 }]}>Reuniões sem saída registrada não entram em Horas no total.</Text> : null}
    {error ? <><Text accessibilityRole="alert" style={styles.error}>{error}</Text>
      <FormButton label="Tentar novamente" icon="refresh-outline" secondary onPress={() => setReload(value => value + 1)} /></> : null}
    {loading && !report ? <ActivityIndicator color={colors.primary} accessibilityLabel="Carregando o relatório" /> : null}
    {report && !report.rows.length && !error ? <View style={styles.empty}>
      <Ionicons name="bar-chart-outline" size={32} color={colors.primary} />
      <Text style={[styles.cardTitle, { textAlign: 'center' }]}>Você ainda não participou de nenhuma reunião {committeeName ? `da comissão ${committeeName} ` : ''}neste período.</Text>
      <Text style={[styles.subtitle, { textAlign: 'center' }]}>Só contam as reuniões em que você registrou entrada.</Text>
      {period !== 'all' && <FormButton label="Ver Todo o Período" secondary onPress={() => setPeriod('all')} />}
    </View> : null}
    {groups.map(group => <View key={group.key}>
      <Text style={styles.sectionLabel}>{group.label}</Text>
      {group.rows.map(row => {
        const block = dateBlock(row.startsAt);
        return <View key={row.meetingId} style={[styles.card, styles.row, { alignItems: 'flex-start' }]}>
          <View style={styles.dateBlock}>
            <Text style={styles.dateBlockSmall}>{block.weekday}</Text><Text style={styles.dateBlockDay}>{block.day}</Text><Text style={styles.dateBlockSmall}>{block.month}</Text>
          </View>
          <View style={styles.flex}>
            <Text style={styles.cardTitle}>{row.title}</Text>
            <Text style={styles.subtitle}>{row.committeeName}</Text>
            <Text style={styles.subtitle}>Entrada {formatTime(row.enteredAt)} · {row.leftAt ? `Saída ${formatTime(row.leftAt)}` : 'Saída não registrada'}</Text>
          </View>
          {row.durationMinutes === null ? <Text style={[styles.cardTitle, { color: colors.subtext }]} accessibilityLabel="Sem tempo, saída não registrada">–</Text>
            : <MeetingBadge text={formatTotal(row.durationMinutes)} />}
        </View>;
      })}
    </View>)}
  </View>;
}
