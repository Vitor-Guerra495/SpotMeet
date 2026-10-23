import React, { useCallback, useEffect, useRef, useState } from 'react';
import { ActivityIndicator, Linking, Modal, RefreshControl, Text, TouchableOpacity, View } from 'react-native';
import { Ionicons } from '@react-native-vector-icons/ionicons';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useFocusEffect } from '@react-navigation/native';
import { useAuth } from '../context/AuthContext';
import { API_BASE_URL } from '../config/api';
import { showAlert as alert, confirmAction } from '../utils/alerts';
import ToastHost from '../components/ui/ToastHost';
import ConfirmationHost from '../components/ui/ConfirmationHost';
import FormScrollView from '../components/forms/FormScrollView';
import { useLiveSync } from '../hooks/useLiveSync';
import { useRefreshRequest } from '../hooks/useRefreshRequest';
import { notifySync as broadcastSync } from '../utils/syncBus';
import MeetingForm, { type MeetingPayload } from '../components/meetings/MeetingForm';
import MeetingInvitationActions from '../components/meetings/MeetingInvitationActions';
import MeetingAttendancePanel from '../components/meetings/MeetingAttendancePanel';
import AttendanceListView from '../components/meetings/AttendanceListView';
import { MeetingAction, MeetingBadge, useMeetingStyles } from '../components/meetings/meetingStyles';
import {
  PRIORITY_LABELS, STATUS_LABELS, RESPONSE_LABELS, errorText, formatDateTime, isOpenableLink,
  readAttendanceList, readMeeting, readMeetings, readPresided,
  type AttendanceList, type Meeting, type MeetingParticipant, type MeetingPriority, type MeetingStatus, type PresidedCommittee,
} from '../utils/meetings';

type Filter = 'upcoming' | 'past';
type FormState = { mode: 'create' } | { mode: 'edit'; meeting: Meeting } | null;

const GENERIC_ERROR = 'Não foi possível concluir a operação. Tente novamente.';

export default function MeetingsScreen() {
  const insets = useSafeAreaInsets();
  const { token, userId } = useAuth();
  const { colors, styles } = useMeetingStyles();
  const request = useRefreshRequest();
  const [meetings, setMeetings] = useState<Meeting[]>([]);
  const [presided, setPresided] = useState<PresidedCommittee[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState('');
  const [refreshing, setRefreshing] = useState(false);
  const [filter, setFilter] = useState<Filter>('upcoming');
  const [detail, setDetail] = useState<Meeting | null>(null);
  const [view, setView] = useState<'detail' | 'list'>('detail');
  const [attendanceList, setAttendanceList] = useState<AttendanceList | null>(null);
  const [form, setForm] = useState<FormState>(null);
  const [busy, setBusy] = useState(false);
  const [actionError, setActionError] = useState('');
  const detailRef = useRef<Meeting | null>(null);
  detailRef.current = detail;
  const busyRef = useRef(false);
  const presidedIds = new Set(presided.map(row => row.committeeId));
  const headers = { Authorization: `Bearer ${token}` };

  const loadDetail = useCallback(async (meetingId: number) => {
    try {
      const res = await fetch(`${API_BASE_URL}/meetings/${meetingId}`, { headers: { Authorization: `Bearer ${token}` } });
      if (!res.ok) { if (res.status === 403 || res.status === 404 || res.status === 409) setDetail(null); return; }
      const next = readMeeting(await res.json());
      if (detailRef.current?.id === meetingId) setDetail(next);
    } catch { /* The next refresh tries again. */ }
  }, [token]);

  const load = useCallback(() => request.run(async ({ signal, isCurrent }) => {
    try {
      const auth = { Authorization: `Bearer ${token}` };
      const [mine, own] = await Promise.all([
        fetch(`${API_BASE_URL}/meetings/mine`, { headers: auth, signal }),
        fetch(`${API_BASE_URL}/committees/presided`, { headers: auth, signal }),
      ]);
      if (!isCurrent()) return;
      if (!mine.ok || !own.ok) throw new Error('read');
      const rows = readMeetings(await mine.json());
      const committees = readPresided(await own.json());
      if (!isCurrent()) return;
      setMeetings(rows); setPresided(committees); setLoadError('');
      if (detailRef.current && !busyRef.current) void loadDetail(detailRef.current.id);
    } catch { if (isCurrent()) setLoadError('Não foi possível carregar as reuniões. Tente novamente.'); }
    finally { if (isCurrent()) setLoading(false); }
  }), [token, request, loadDetail]);

  // Any entry or exit clears the list on the server, so an open list would keep showing old data.
  useEffect(() => {
    if (view === 'list' && attendanceList && detail && detail.attendanceListGeneratedAt === null) {
      setAttendanceList(null); setView('detail');
      setActionError('A presença mudou depois que a lista foi gerada. A lista precisa ser gerada de novo.');
    }
  }, [view, attendanceList, detail?.attendanceListGeneratedAt]);

  const paused = busy || form !== null;
  useFocusEffect(useCallback(() => { void load(); }, [load]));
  useLiveSync(load, { intervalMs: 10000, enabled: !!token && !paused, eventTypes: ['MEETINGS_MUTATED', 'COMMITTEES_MUTATED', 'VISIBILITY_RESTORED'] });

  const handleRefresh = async () => { setRefreshing(true); try { await load(); } finally { setRefreshing(false); } };

  // Every write goes through here: one at a time, with the server message on failure.
  const send = async (path: string, method: 'POST' | 'PUT' | 'DELETE', body?: unknown, success?: string): Promise<unknown | undefined> => {
    if (busyRef.current) return undefined;
    busyRef.current = true; setBusy(true); setActionError('');
    try {
      const res = await fetch(`${API_BASE_URL}${path}`, {
        method, headers: { ...headers, ...(body === undefined ? {} : { 'Content-Type': 'application/json' }) },
        ...(body === undefined ? {} : { body: JSON.stringify(body) }),
      });
      const text = await res.text();
      if (!res.ok) {
        setActionError(errorText(res.status, text, GENERIC_ERROR));
        if (res.status === 403 || res.status === 404 || res.status === 409) void load();
        return undefined;
      }
      if (success) alert('Sucesso', success);
      broadcastSync('MEETINGS_MUTATED');
      try { return JSON.parse(text) as unknown; } catch { return null; }
    } catch { setActionError('Não foi possível conectar ao servidor. Tente novamente.'); return undefined; }
    finally { busyRef.current = false; setBusy(false); }
  };

  // Mutations answer with the updated meeting, which replaces the open detail.
  const act = async (path: string, method: 'POST' | 'PUT' | 'DELETE', body?: unknown, success?: string) => {
    const data = await send(path, method, body, success);
    if (data === undefined) return;
    try { if (data) setDetail(readMeeting(data)); } catch { if (detailRef.current) void loadDetail(detailRef.current.id); }
    void load();
  };

  const confirm = (title: string, message: string, label: string, action: () => void | Promise<void>, destructive = false) =>
    confirmAction(title, message, action, label, destructive);

  const openDetail = (meeting: Meeting) => {
    setActionError(''); setView('detail'); setAttendanceList(null); setDetail(meeting); void loadDetail(meeting.id);
  };
  const closeDetail = () => { if (!busy) { setDetail(null); setView('detail'); setAttendanceList(null); setActionError(''); } };
  const closeForm = () => { if (!busy) { setForm(null); setActionError(''); } };

  const submitForm = async (payload: MeetingPayload) => {
    const editing = form?.mode === 'edit' ? form.meeting : null;
    const data = await send(editing ? `/meetings/${editing.id}` : `/committees/${payload.committeeId}/meetings`, editing ? 'PUT' : 'POST', payload.body,
      editing ? 'Reunião atualizada.' : 'Reunião agendada.');
    if (data === undefined) return;
    setForm(null); setActionError('');
    try { if (data) openDetail(readMeeting(data)); } catch { /* The list refresh below shows the meeting. */ }
    void load();
  };

  const showList = async (meeting: Meeting) => {
    if (busyRef.current) return;
    busyRef.current = true; setBusy(true); setActionError('');
    try {
      const res = await fetch(`${API_BASE_URL}/meetings/${meeting.id}/attendance-list`, { headers });
      const text = await res.text();
      if (!res.ok) { setActionError(errorText(res.status, text, GENERIC_ERROR)); void loadDetail(meeting.id); return; }
      setAttendanceList(readAttendanceList(JSON.parse(text))); setView('list');
    } catch { setActionError('Não foi possível abrir a lista de presença. Tente novamente.'); }
    finally { busyRef.current = false; setBusy(false); }
  };

  const generateList = (meeting: Meeting) => confirm('Gerar lista de presença',
    'A lista usa os registros de agora e fica disponível para os integrantes. Se a presença mudar depois, será preciso gerar de novo.', 'Gerar', async () => {
      const data = await send(`/meetings/${meeting.id}/attendance-list`, 'POST', undefined, 'Lista de presença gerada.');
      if (data === undefined) return;
      try {
        const list = readAttendanceList(data);
        setDetail(current => current ? { ...current, attendanceListGeneratedAt: list.generatedAt } : current);
        setAttendanceList(list); setView('list');
      } catch { setActionError('A lista foi gerada, mas não pôde ser exibida.'); }
      void loadDetail(meeting.id); void load();
    });

  const cancelMeeting = (meeting: Meeting) => confirm('Cancelar reunião', `Cancelar "${meeting.title}"? Os convidados verão a reunião como cancelada.`, 'Cancelar reunião',
    () => act(`/meetings/${meeting.id}/cancel`, 'POST', undefined, 'Reunião cancelada.'), true);
  const resetPerson = (meeting: Meeting, person: MeetingParticipant) => confirm('Limpar registros', `Apagar a entrada e a saída de ${person.userName}?`, 'Limpar',
    () => act(`/meetings/${meeting.id}/participants/${person.userId}/attendance`, 'DELETE', undefined, 'Registros apagados.'), true);
  const openLink = (link: string) => { if (isOpenableLink(link)) void Linking.openURL(link).catch(() => setActionError('Não foi possível abrir o link.')); };

  const isLive = (meeting: Meeting) => meeting.status === 'IN_PROGRESS' || meeting.status === 'SCHEDULED';
  const visible = meetings
    .filter(meeting => filter === 'upcoming' ? isLive(meeting) : !isLive(meeting))
    .sort((a, b) => filter === 'upcoming' ? a.startsAt.localeCompare(b.startsAt) : b.startsAt.localeCompare(a.startsAt));
  const pending = meetings.filter(meeting => meeting.myResponse === 'PENDING' && isLive(meeting));
  const statusColor = (status: MeetingStatus) => status === 'IN_PROGRESS' ? colors.success : status === 'CANCELED' ? colors.danger : status === 'ENDED' ? colors.subtext : colors.primary;
  const priorityColor = (priority: MeetingPriority) => priority === 'HIGH' ? colors.danger : priority === 'MEDIUM' ? colors.warning : colors.subtext;

  const meetingCard = (meeting: Meeting) => <TouchableOpacity key={meeting.id} accessibilityRole="button" accessibilityLabel={`Abrir reunião ${meeting.title}`}
    style={styles.card} onPress={() => openDetail(meeting)}>
    <View style={styles.row}><View style={styles.flex}>
      <Text style={styles.cardTitle}>{meeting.title}</Text>
      <Text style={styles.subtitle}>{meeting.committeeName}</Text>
      <Text style={styles.subtitle}>{formatDateTime(meeting.startsAt)} até {formatDateTime(meeting.endsAt)}</Text>
      <View style={styles.badges}>
        <MeetingBadge text={STATUS_LABELS[meeting.status]} color={statusColor(meeting.status)} />
        <MeetingBadge text={`Prioridade ${PRIORITY_LABELS[meeting.priority].toLowerCase()}`} color={priorityColor(meeting.priority)} />
        {meeting.myResponse && meeting.myResponse !== 'PENDING' && <MeetingBadge text={RESPONSE_LABELS[meeting.myResponse]}
          color={meeting.myResponse === 'ACCEPTED' ? colors.success : colors.danger} />}
        {meeting.myResponse === 'PENDING' && isLive(meeting) && <MeetingBadge text="Responda ao convite" color={colors.warning} />}
      </View></View>
      <Ionicons name="chevron-forward" size={22} color={colors.subtext} /></View>
  </TouchableOpacity>;

  const empty = (text: string) => <View style={styles.empty}><Ionicons name="calendar-outline" size={28} color={colors.subtext} /><Text style={styles.subtitle}>{text}</Text></View>;

  const renderDetail = (meeting: Meeting) => {
    const president = presidedIds.has(meeting.committeeId);
    const canList = meeting.status === 'IN_PROGRESS' || meeting.status === 'ENDED';
    return <>
      <View style={styles.card}>
        <Text style={styles.cardTitle}>{meeting.title}</Text>
        <Text style={styles.subtitle}>{meeting.committeeName} · agendada por {meeting.createdByName}</Text>
        <View style={styles.badges}>
          <MeetingBadge text={STATUS_LABELS[meeting.status]} color={statusColor(meeting.status)} />
          <MeetingBadge text={`Prioridade ${PRIORITY_LABELS[meeting.priority].toLowerCase()}`} color={priorityColor(meeting.priority)} />
        </View>
        <View style={styles.infoRow}><Ionicons name="calendar-outline" size={20} color={colors.primary} />
          <Text style={styles.infoText}>{formatDateTime(meeting.startsAt)} até {formatDateTime(meeting.endsAt)}</Text></View>
        {meeting.location ? <View style={styles.infoRow}><Ionicons name="location-outline" size={20} color={colors.primary} /><Text style={styles.infoText}>{meeting.location}</Text></View> : null}
        {meeting.link ? <TouchableOpacity accessibilityRole="link" accessibilityLabel="Abrir link da reunião" style={styles.infoRow} onPress={() => openLink(meeting.link!)}>
          <Ionicons name="link-outline" size={20} color={colors.primary} /><Text style={[styles.infoText, { color: colors.primary }]}>{meeting.link}</Text></TouchableOpacity> : null}
        {meeting.agenda ? <View style={styles.infoRow}><Ionicons name="document-text-outline" size={20} color={colors.primary} /><Text style={styles.infoText}>{meeting.agenda}</Text></View> : null}
        <View style={styles.badges}>
          <MeetingBadge text={`${meeting.totalInvited} convidados`} />
          <MeetingBadge text={`${meeting.totalAccepted} aceitaram`} color={colors.success} />
          <MeetingBadge text={`${meeting.totalDeclined} recusaram`} color={colors.danger} />
          <MeetingBadge text={`${meeting.totalPending} sem resposta`} color={colors.warning} />
        </View>
      </View>
      <MeetingInvitationActions key={`${meeting.id}:${meeting.myResponse}`} meeting={meeting} busy={busy}
        onAccept={() => void act(`/meetings/${meeting.id}/respond?accept=true`, 'POST', undefined, 'Presença confirmada.')}
        onDecline={comment => void act(`/meetings/${meeting.id}/respond?accept=false`, 'POST', { comment: comment.trim() || null }, 'Recusa enviada.')} />
      {meeting.participants === null ? <ActivityIndicator color={colors.primary} accessibilityLabel="Carregando participantes" /> : <MeetingAttendancePanel meeting={meeting}
        userId={userId} isPresident={president} busy={busy}
        onEntry={target => void act(`/meetings/${meeting.id}/participants/${target}/entry`, 'POST', undefined, 'Entrada registrada.')}
        onExit={target => void act(`/meetings/${meeting.id}/participants/${target}/exit`, 'POST', undefined, 'Saída registrada.')}
        onEntryAll={() => confirm('Registrar entrada de todos', 'Registrar agora a entrada de todos que aceitaram o convite e ainda não entraram?', 'Registrar',
          () => act(`/meetings/${meeting.id}/attendance/entries`, 'POST', undefined, 'Entradas registradas.'))}
        onExitAll={() => confirm('Registrar saída de todos', 'Registrar agora a saída de todos que entraram e ainda não saíram?', 'Registrar',
          () => act(`/meetings/${meeting.id}/attendance/exits`, 'POST', undefined, 'Saídas registradas.'))}
        onReset={person => resetPerson(meeting, person)} />}
      {(canList && president || meeting.attendanceListGeneratedAt) && <View style={styles.card}>
        <Text style={styles.cardTitle}>Lista de presença</Text>
        {meeting.attendanceListGeneratedAt ? <Text style={styles.subtitle}>Gerada em {formatDateTime(meeting.attendanceListGeneratedAt)}.</Text>
          : <Text style={styles.subtitle}>Ainda não foi gerada.</Text>}
        <View style={styles.actions}>
          {meeting.attendanceListGeneratedAt && <MeetingAction label="Ver lista" icon="list-outline" disabled={busy} onPress={() => void showList(meeting)} />}
          {president && canList && <MeetingAction label={meeting.attendanceListGeneratedAt ? 'Gerar de novo' : 'Gerar lista'} icon="document-text-outline" filled={!meeting.attendanceListGeneratedAt}
            disabled={busy} onPress={() => generateList(meeting)} />}
        </View>
      </View>}
      {president && isLive(meeting) && <View style={styles.actions}>
        {/* The form needs the current invitees; editing before they load would drop them. */}
        {meeting.status === 'SCHEDULED' && <MeetingAction label="Editar reunião" icon="create-outline" disabled={busy || meeting.participants === null}
          onPress={() => { if (meeting.participants !== null) { setDetail(null); setForm({ mode: 'edit', meeting }); } }} />}
        <MeetingAction label="Cancelar reunião" icon="close-circle-outline" danger disabled={busy} onPress={() => cancelMeeting(meeting)} />
      </View>}
    </>;
  };

  const upcomingCount = meetings.filter(isLive).length;
  return <View style={{ flex: 1, backgroundColor: colors.background, paddingTop: insets.top + 12 }}>
    <View style={[styles.row, { paddingHorizontal: 20, marginBottom: 14 }]}>
      <View style={styles.flex}><Text style={styles.title}>Reuniões</Text><Text style={styles.subtitle}>Convites, presença e listas das suas comissões</Text></View>
      <TouchableOpacity accessibilityRole="button" accessibilityLabel="Atualizar reuniões" style={styles.iconButton} onPress={handleRefresh} disabled={paused}>
        {refreshing ? <ActivityIndicator color={colors.primary} /> : <Ionicons name="refresh-outline" size={22} color={colors.primary} />}</TouchableOpacity>
    </View>
    <FormScrollView contentContainerStyle={styles.content}
      refreshControl={<RefreshControl refreshing={refreshing} onRefresh={handleRefresh} tintColor={colors.primary} colors={[colors.primary]} enabled={!paused} />}>
      {loadError ? <><Text accessibilityRole="alert" style={styles.error}>{loadError}</Text><MeetingAction label="Tentar atualizar" icon="refresh-outline" onPress={handleRefresh} /></> : null}
      {presided.length > 0 && <MeetingAction label="Agendar reunião" icon="add-circle-outline" filled onPress={() => { setActionError(''); setForm({ mode: 'create' }); }} />}
      {pending.length > 0 && <>
        <Text style={styles.sectionLabel}>CONVITES PARA RESPONDER ({pending.length})</Text>
        {pending.map(meetingCard)}
      </>}
      <View style={[styles.filters, { marginTop: 8 }]}>
        {(['upcoming', 'past'] as Filter[]).map(value => <TouchableOpacity key={value} accessibilityRole="tab" accessibilityState={{ selected: filter === value }}
          style={[styles.filter, filter === value && styles.filterActive]} onPress={() => setFilter(value)}>
          <Text style={[styles.filterText, filter === value && { color: '#FFFFFF' }]}>{value === 'upcoming' ? `Próximas (${upcomingCount})` : `Anteriores (${meetings.length - upcomingCount})`}</Text></TouchableOpacity>)}
      </View>
      {loading ? <ActivityIndicator color={colors.primary} accessibilityLabel="Carregando reuniões" /> : visible.map(meetingCard)}
      {!loading && !loadError && !visible.length && empty(filter === 'upcoming'
        ? presided.length ? 'Nenhuma reunião agendada. Toque em Agendar reunião para criar a primeira.' : 'Você não tem reuniões marcadas. Quando o presidente de uma comissão convidar você, ela aparece aqui.'
        : 'Nenhuma reunião anterior.')}
    </FormScrollView>

    <Modal visible={!!detail} transparent animationType="slide" onRequestClose={closeDetail}>
      <View style={styles.overlay}><View style={[styles.sheet, { paddingBottom: insets.bottom }]}><View style={styles.sheetHandle} />
        <View style={styles.sheetHeader}>
          {view === 'list' && <TouchableOpacity accessibilityRole="button" accessibilityLabel="Voltar à reunião" style={styles.iconButton} onPress={() => setView('detail')}>
            <Ionicons name="arrow-back" size={24} color={colors.primary} /></TouchableOpacity>}
          <Text style={[styles.cardTitle, styles.flex]}>{view === 'list' ? 'Lista de presença' : 'Reunião'}</Text>
          <TouchableOpacity accessibilityRole="button" accessibilityLabel="Fechar reunião" style={styles.iconButton} onPress={closeDetail} disabled={busy}>
            <Ionicons name="close-outline" size={24} color={colors.subtext} /></TouchableOpacity></View>
        <FormScrollView contentContainerStyle={styles.content}>
          {actionError ? <Text accessibilityRole="alert" style={styles.error}>{actionError}</Text> : null}
          {detail && (view === 'list' && attendanceList ? <AttendanceListView list={attendanceList} /> : renderDetail(detail))}
        </FormScrollView>
      </View></View><ToastHost active={!!detail} /><ConfirmationHost active={!!detail} />
    </Modal>

    <Modal visible={form !== null} transparent animationType="slide" onRequestClose={closeForm}>
      <View style={styles.overlay}><View style={[styles.sheet, { paddingBottom: insets.bottom }]}><View style={styles.sheetHandle} />
        <View style={styles.sheetHeader}><Text style={[styles.cardTitle, styles.flex]}>{form?.mode === 'edit' ? 'Editar reunião' : 'Agendar reunião'}</Text>
          <TouchableOpacity accessibilityRole="button" accessibilityLabel="Fechar formulário" style={styles.iconButton} onPress={closeForm} disabled={busy}>
            <Ionicons name="close-outline" size={24} color={colors.subtext} /></TouchableOpacity></View>
        {form && <MeetingForm key={form.mode === 'edit' ? `edit-${form.meeting.id}` : 'create'} committees={presided} meeting={form.mode === 'edit' ? form.meeting : null}
          busy={busy} error={actionError} onSubmit={payload => void submitForm(payload)} onCancel={closeForm} />}
      </View></View><ToastHost active={form !== null} /><ConfirmationHost active={form !== null} />
    </Modal>
  </View>;
}
