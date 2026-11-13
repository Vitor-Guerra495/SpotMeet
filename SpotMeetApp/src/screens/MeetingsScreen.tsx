import React, { useCallback, useEffect, useRef, useState } from 'react';
import { ActivityIndicator, BackHandler, Linking, Modal, RefreshControl, Text, TouchableOpacity, View } from 'react-native';
import { Ionicons } from '@react-native-vector-icons/ionicons';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useFocusEffect, useNavigation } from '@react-navigation/native';
import { Directory, File, Paths } from 'expo-file-system';
import * as Sharing from 'expo-sharing';
import { useAuth } from '../context/AuthContext';
import { API_BASE_URL } from '../config/api';
import { showAlert as alert, confirmAction } from '../utils/alerts';
import ToastHost from '../components/ui/ToastHost';
import ConfirmationHost from '../components/ui/ConfirmationHost';
import FormScrollView from '../components/forms/FormScrollView';
import FormField from '../components/forms/FormField';
import FormButton from '../components/forms/FormButton';
import UserAvatar from '../components/profile/UserAvatar';
import { useLiveSync } from '../hooks/useLiveSync';
import { useRefreshRequest } from '../hooks/useRefreshRequest';
import { notifySync as broadcastSync } from '../utils/syncBus';
import { onMeetingLink, takePendingMeeting } from '../utils/meetingLinks';
import MeetingForm, { type MeetingPayload, type PendingFile } from '../components/meetings/MeetingForm';
import MeetingInvitationActions from '../components/meetings/MeetingInvitationActions';
import MeetingAttendancePanel from '../components/meetings/MeetingAttendancePanel';
import AttendanceListView from '../components/meetings/AttendanceListView';
import MinutesSection, { minutesTitle, type MinutesPage } from '../components/meetings/MinutesSection';
import MeetingPolls, { pollTitle, type PollPage } from '../components/meetings/MeetingPolls';
import { MeetingAction, MeetingBadge, useMeetingStyles } from '../components/meetings/meetingStyles';
import {
  MODALITY_LABELS, PRIORITY_LABELS, RESPONSE_LABELS, STATUS_LABELS, capitalize, dateBlock, displayLink, errorText, formatClock, formatDay,
  formatSize, formatTime, isOpenableLink, meetingDates, meetingTimes, parseLocal, readAttachmentResponse, readAttendanceList, readMeeting,
  readMeetings, readPresided, recurrenceText, sameDay, toLocalIso,
  type AttendanceList, type Meeting, type MeetingParticipant, type MeetingPriority, type MeetingStatus, type PresidedCommittee,
} from '../utils/meetings';

type Filter = 'all' | 'upcoming' | 'past';
type FormState = { mode: 'create' } | { mode: 'edit'; meeting: Meeting } | null;
type DetailView = 'detail' | 'list' | 'cancel' | Exclude<MinutesPage, 'card'> | Exclude<PollPage, 'card'>;

const GENERIC_ERROR = 'Não foi possível concluir a operação. Tente novamente.';
const FILTERS: { value: Filter; label: string }[] = [{ value: 'all', label: 'Todas' }, { value: 'upcoming', label: 'Próximas' }, { value: 'past', label: 'Passadas' }];
const GUESTS_SHOWN = 5;

// Files are written with the name the user sees, without characters the file system refuses.
const safeName = (name: string) => name.replace(/[\\/:*?"<>|]/g, '_').slice(0, 150) || 'anexo';

function attachmentsFolder(): Directory {
  const folder = new Directory(Paths.cache, 'anexos');
  if (!folder.exists) folder.create({ intermediates: true });
  return folder;
}

export default function MeetingsScreen() {
  const insets = useSafeAreaInsets();
  const { token, userId } = useAuth();
  const navigation = useNavigation<any>();
  const { colors, styles } = useMeetingStyles();
  const request = useRefreshRequest();
  const [meetings, setMeetings] = useState<Meeting[]>([]);
  const [presided, setPresided] = useState<PresidedCommittee[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState('');
  const [refreshing, setRefreshing] = useState(false);
  const [filter, setFilter] = useState<Filter>('all');
  const [page, setPage] = useState<'list' | 'invites'>('list');
  const [detail, setDetail] = useState<Meeting | null>(null);
  const [view, setView] = useState<DetailView>('detail');
  const [attendanceList, setAttendanceList] = useState<AttendanceList | null>(null);
  const [form, setForm] = useState<FormState>(null);
  const [busy, setBusy] = useState(false);
  const [actionError, setActionError] = useState('');
  const [allGuests, setAllGuests] = useState(false);
  const [cancelReason, setCancelReason] = useState('');
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

  const openDetail = (meeting: Meeting) => {
    setActionError(''); setView('detail'); setAttendanceList(null); setAllGuests(false); setDetail(meeting); void loadDetail(meeting.id);
  };

  // Meeting opened from the link of an e-mail.
  const openById = useCallback(async (meetingId: number) => {
    try {
      const res = await fetch(`${API_BASE_URL}/meetings/${meetingId}`, { headers: { Authorization: `Bearer ${token}` } });
      if (!res.ok) { alert('Reunião indisponível', 'Esta reunião não foi encontrada ou você não tem acesso a ela.'); return; }
      const meeting = readMeeting(await res.json());
      setForm(null); setActionError(''); setView('detail'); setAttendanceList(null); setAllGuests(false); setDetail(meeting);
    } catch { alert('Sem conexão', 'Não foi possível abrir a reunião. Tente novamente.'); }
  }, [token]);

  useEffect(() => onMeetingLink(() => { const meetingId = takePendingMeeting(); if (meetingId) void openById(meetingId); }), [openById]);

  const paused = busy || form !== null;
  useFocusEffect(useCallback(() => {
    void load();
    const meetingId = takePendingMeeting();
    if (meetingId) void openById(meetingId);
  }, [load, openById]));
  useLiveSync(load, { intervalMs: 10000, enabled: !!token && !paused, eventTypes: ['MEETINGS_MUTATED', 'COMMITTEES_MUTATED', 'VISIBILITY_RESTORED'] });

  // The back button of the phone leaves Pendências before leaving the app.
  const pageRef = useRef(page);
  pageRef.current = page;
  useFocusEffect(useCallback(() => {
    const subscription = BackHandler.addEventListener('hardwareBackPress', () => {
      if (pageRef.current === 'list') return false;
      setPage('list');
      return true;
    });
    return () => subscription.remove();
  }, []));

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
    if (data === undefined) return false;
    try { if (data) setDetail(current => current ? readMeeting(data) : current); } catch { if (detailRef.current) void loadDetail(detailRef.current.id); }
    void load();
    return true;
  };

  const confirm = (title: string, message: string, label: string, action: () => void | Promise<void>, destructive = false) =>
    confirmAction(title, message, action, label, destructive);

  const closeDetail = () => { if (!busy) { setDetail(null); setView('detail'); setAttendanceList(null); setActionError(''); } };
  const closeForm = () => { if (!busy) { setForm(null); setActionError(''); } };

  // The attachment is sent first; the meeting then points to the file the server kept.
  const uploadFile = async (committeeId: number, file: PendingFile): Promise<number | undefined> => {
    if (busyRef.current) return undefined;
    busyRef.current = true; setBusy(true); setActionError('');
    try {
      const copy = new File(attachmentsFolder(), safeName(file.name));
      if (copy.exists) copy.delete();
      new File(file.uri).copy(copy);
      const body = new FormData();
      body.append('file', copy);
      const res = await fetch(`${API_BASE_URL}/committees/${committeeId}/meeting-files`, { method: 'POST', headers, body });
      const text = await res.text();
      if (!res.ok) { setActionError(errorText(res.status, text, 'Não foi possível enviar o anexo. Tente novamente.')); return undefined; }
      return readAttachmentResponse(JSON.parse(text)).id;
    } catch { setActionError('Não foi possível enviar o anexo. Tente novamente.'); return undefined; }
    finally { busyRef.current = false; setBusy(false); }
  };

  const submitForm = async (payload: MeetingPayload) => {
    const editing = form?.mode === 'edit' ? form.meeting : null;
    let attachmentFileId = payload.body.attachmentFileId;
    if (payload.upload) {
      const uploaded = await uploadFile(payload.committeeId, payload.upload);
      if (uploaded === undefined) return;
      attachmentFileId = uploaded;
    }
    const series = !editing && !!payload.body.recurrence;
    const data = await send(editing ? `/meetings/${editing.id}` : `/committees/${payload.committeeId}/meetings`, editing ? 'PUT' : 'POST',
      { ...payload.body, attachmentFileId },
      editing ? 'Reunião atualizada. Os convidados recebem um e-mail com o que mudou.'
        : series ? 'Reuniões agendadas para os próximos 3 meses. Os convidados recebem um e-mail.' : 'Reunião agendada. Os convidados recebem um e-mail.');
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

  const downloadAttachment = async (meeting: Meeting) => {
    const attachment = meeting.attachment;
    if (!attachment || busyRef.current) return;
    busyRef.current = true; setBusy(true); setActionError('');
    try {
      const target = new File(attachmentsFolder(), `${meeting.id}-${safeName(attachment.fileName)}`);
      if (target.exists) target.delete();
      const file = await File.downloadFileAsync(`${API_BASE_URL}/meetings/${meeting.id}/attachment`, target, { headers });
      if (await Sharing.isAvailableAsync()) await Sharing.shareAsync(file.uri, { mimeType: attachment.contentType, dialogTitle: attachment.fileName });
      else setActionError('Este aparelho não tem um aplicativo para abrir o arquivo.');
    } catch { setActionError('Não foi possível baixar o anexo. Tente novamente.'); }
    finally { busyRef.current = false; setBusy(false); }
  };

  const cancelMeeting = async (meeting: Meeting) => {
    const done = await act(`/meetings/${meeting.id}/cancel`, 'POST', { reason: cancelReason.trim() || null }, 'Reunião cancelada. Os convidados recebem um e-mail.');
    if (done) { setView('detail'); setCancelReason(''); }
  };
  const resetPerson = (meeting: Meeting, person: MeetingParticipant) => confirm('Limpar registros', `Apagar a entrada e a saída de ${person.userName}?`, 'Limpar',
    () => void act(`/meetings/${meeting.id}/participants/${person.userId}/attendance`, 'DELETE', undefined, 'Registros apagados.'), true);
  const openLink = (link: string) => { if (isOpenableLink(link)) void Linking.openURL(link).catch(() => setActionError('Não foi possível abrir o link.')); };
  const entry = (meeting: Meeting, target: number) => void act(`/meetings/${meeting.id}/participants/${target}/entry`, 'POST', undefined, 'Entrada registrada.');
  const exit = (meeting: Meeting, target: number) => void act(`/meetings/${meeting.id}/participants/${target}/exit`, 'POST', undefined, 'Saída registrada.');

  const now = toLocalIso(new Date());
  const isOpen = (meeting: Meeting) => meeting.status === 'IN_PROGRESS' || meeting.status === 'SCHEDULED';
  const live = meetings.filter(meeting => meeting.status === 'IN_PROGRESS').sort((a, b) => a.startsAt.localeCompare(b.startsAt));
  const upcoming = meetings.filter(meeting => meeting.status === 'SCHEDULED' || (meeting.status === 'CANCELED' && meeting.endsAt > now))
    .sort((a, b) => a.startsAt.localeCompare(b.startsAt));
  const past = meetings.filter(meeting => meeting.status === 'ENDED' || (meeting.status === 'CANCELED' && meeting.endsAt <= now))
    .sort((a, b) => b.startsAt.localeCompare(a.startsAt));
  const pending = meetings.filter(meeting => meeting.myResponse === 'PENDING' && isOpen(meeting)).sort((a, b) => a.startsAt.localeCompare(b.startsAt));
  const minutesPending = meetings.filter(meeting => meeting.minutesToAnswer).sort((a, b) => b.startsAt.localeCompare(a.startsAt));
  const plural = (count: number, one: string, many: string) => `${count} ${count === 1 ? one : many}`;
  const pendingText = [pending.length ? plural(pending.length, 'convite', 'convites') : '', minutesPending.length ? plural(minutesPending.length, 'ata', 'atas') : '']
    .filter(Boolean).join(' e ');
  // Refusals stay in the banner until the meeting ends or the person changes the answer.
  const refusals = meetings.filter(meeting => presidedIds.has(meeting.committeeId) && isOpen(meeting))
    .sort((a, b) => a.startsAt.localeCompare(b.startsAt)).flatMap(meeting => meeting.declines.map(person => ({ meeting, person })));
  const refusalText = refusals.length ? `${refusals.length === 1 ? '1 convidado recusou' : `${refusals.length} convidados recusaram`} seu convite.` : '';
  const hasPending = pendingText !== '' || refusals.length > 0;
  // Leaders of the organization follow the meetings of every committee without taking part in them.
  const viewOnly = (meeting: Meeting) => !presidedIds.has(meeting.committeeId) && meeting.myResponse === null;
  const openMinutes = (meeting: Meeting) => { openDetail(meeting); setView('minutes-read'); };
  const statusColor = (status: MeetingStatus) => status === 'IN_PROGRESS' ? colors.success : status === 'CANCELED' ? colors.danger : status === 'ENDED' ? colors.subtext : undefined;
  const statusIcon = (status: MeetingStatus) => status === 'IN_PROGRESS' ? 'radio-button-on' as const : status === 'CANCELED' ? 'ban-outline' as const
    : status === 'ENDED' ? 'checkmark-done-outline' as const : 'calendar-outline' as const;
  const priorityColor = (priority: MeetingPriority) => priority === 'HIGH' ? colors.warning : colors.subtext;
  const endsText = (meeting: Meeting) => {
    const end = parseLocal(meeting.endsAt);
    if (meeting.allDay && sameDay(end, new Date())) return 'Dia inteiro';
    return sameDay(end, new Date()) ? `Termina às ${formatClock(end)}` : `Termina ${formatDay(end)}`;
  };

  const meetingCard = (meeting: Meeting) => {
    const block = dateBlock(meeting.startsAt);
    return <TouchableOpacity key={meeting.id} accessibilityRole="button" accessibilityLabel={`Abrir reunião ${meeting.title}`} style={styles.card} onPress={() => openDetail(meeting)}>
      <View style={[styles.row, { alignItems: 'flex-start' }]}>
        <View style={styles.dateBlock}>
          <Text style={styles.dateBlockSmall}>{block.weekday}</Text><Text style={styles.dateBlockDay}>{block.day}</Text><Text style={styles.dateBlockSmall}>{block.month}</Text>
        </View>
        <View style={styles.flex}>
          <View style={styles.row}>
            <Text style={[styles.cardTitle, styles.flex]} numberOfLines={2}>{meeting.title}</Text>
            {meeting.seriesId ? <Ionicons name="repeat-outline" size={18} color={colors.subtext} accessibilityLabel="Reunião de uma série" /> : null}
          </View>
          <View style={[styles.row, { gap: 6 }]}>
            <Ionicons name="time-outline" size={15} color={colors.subtext} />
            <Text style={styles.subtitle} numberOfLines={1}>{meeting.committeeName} · {meeting.allDay ? 'Dia inteiro' : meetingTimes(meeting)}</Text>
          </View>
          <View style={styles.badges}>
            <MeetingBadge text={STATUS_LABELS[meeting.status]} color={statusColor(meeting.status)} icon={statusIcon(meeting.status)} />
            <MeetingBadge text={PRIORITY_LABELS[meeting.priority]} color={priorityColor(meeting.priority)} icon="flag-outline" />
            {meeting.myResponse === 'PENDING' && isOpen(meeting) && <MeetingBadge text="Responda ao convite" color={colors.warning} icon="mail-unread-outline" />}
            {meeting.minutesToAnswer && <MeetingBadge text="Ata para aprovar" color={colors.warning} icon="document-text-outline" />}
            {presidedIds.has(meeting.committeeId) && isOpen(meeting) && meeting.totalDeclined > 0
              && <MeetingBadge text={`${meeting.totalDeclined} ${meeting.totalDeclined === 1 ? 'recusou' : 'recusaram'}`} color={colors.danger} icon="close-circle" />}
            {viewOnly(meeting) && <MeetingBadge text="Só visualização" icon="eye-outline" />}
          </View>
        </View>
        <Ionicons name="chevron-forward" size={22} color={colors.subtext} style={{ alignSelf: 'center' }} />
      </View>
    </TouchableOpacity>;
  };

  // "Acontecendo agora": the presence buttons stay on the card, without opening the meeting.
  const liveCard = (meeting: Meeting) => {
    const mine = { ...meeting, participants: meeting.myResponse && userId ? [{ userId, userName: '', response: meeting.myResponse,
      responseComment: null, enteredAt: meeting.myEnteredAt, enteredByName: null, leftAt: meeting.myLeftAt, leftByName: null }] : [] };
    return <TouchableOpacity key={meeting.id} accessibilityRole="button" accessibilityLabel={`Abrir reunião ${meeting.title}`}
      style={[styles.card, styles.liveCard]} onPress={() => openDetail(meeting)}>
      <View style={[styles.row, { justifyContent: 'space-between' }]}>
        <View style={[styles.row, { gap: 6, flexWrap: 'wrap', flexShrink: 1 }]}>
          <MeetingBadge text="Em andamento" color={colors.success} icon="radio-button-on" />
          {viewOnly(meeting) && <MeetingBadge text="Só visualização" icon="eye-outline" />}
        </View>
        <Text style={styles.subtitle}>{endsText(meeting)}</Text>
      </View>
      <View style={[styles.row, { marginTop: 8 }]}>
        <Text style={[styles.cardTitle, styles.flex, { fontSize: 18, lineHeight: 24 }]}>{meeting.title}</Text>
        {meeting.seriesId ? <Ionicons name="repeat-outline" size={18} color={colors.subtext} /> : null}
      </View>
      <Text style={[styles.subtitle, { marginBottom: 10 }]}>{meeting.committeeName} · {meetingTimes(meeting)}</Text>
      <MeetingAttendancePanel meeting={mine} userId={userId} isPresident={presidedIds.has(meeting.committeeId)} busy={busy} compact
        onEntry={target => entry(meeting, target)} onExit={target => exit(meeting, target)} onEntryAll={() => undefined} onExitAll={() => undefined}
        onShowList={() => { openDetail(meeting); void showList(meeting); }} onGenerateList={() => openDetail(meeting)} />
    </TouchableOpacity>;
  };

  const empty = <View style={[styles.empty, { marginTop: 40 }]}>
    <View style={[styles.dateBlock, { width: 72, paddingVertical: 18 }]}><Ionicons name="calendar-outline" size={32} color={colors.primary} /></View>
    <Text style={[styles.cardTitle, { textAlign: 'center' }]}>{filter === 'past' ? 'Nenhuma reunião anterior' : 'Nenhuma reunião marcada'}</Text>
    <Text style={[styles.subtitle, { textAlign: 'center' }]}>{filter === 'past' ? 'As reuniões que já aconteceram aparecem aqui.'
      : presided.length ? 'Agende a primeira reunião da sua comissão.' : 'Quando o presidente de uma comissão convidar você, a reunião aparece aqui.'}</Text>
    {presided.length > 0 && filter !== 'past' && <FormButton label="Agendar reunião" icon="add-outline" onPress={() => { setActionError(''); setForm({ mode: 'create' }); }} />}
  </View>;

  // Who answers the minutes comes from the records, so they freeze once the minutes are sent.
  const attendanceLocked = (meeting: Meeting) => meeting.minutesStatus !== null && meeting.minutesStatus !== 'DRAFT';

  const personRow = (meeting: Meeting, person: MeetingParticipant, president: boolean) => {
    const recordable = meeting.status === 'IN_PROGRESS' || meeting.status === 'ENDED';
    const color = person.response === 'ACCEPTED' ? colors.success : person.response === 'DECLINED' ? colors.danger : colors.warning;
    const records = [person.enteredAt && `Entrada ${formatTime(person.enteredAt)}${person.enteredByName && person.enteredByName !== person.userName ? ` (por ${person.enteredByName})` : ''}`,
      person.leftAt && `Saída ${formatTime(person.leftAt)}${person.leftByName && person.leftByName !== person.userName ? ` (por ${person.leftByName})` : ''}`].filter(Boolean).join(' · ');
    return <View key={person.userId} style={styles.personRow}>
      <View style={styles.row}>
        <UserAvatar userId={person.userId} name={person.userName} style={styles.avatar} textStyle={styles.avatarText} />
        <View style={styles.flex}>
          <Text style={styles.optionText} numberOfLines={1}>{person.userName}</Text>
          {records ? <Text style={styles.subtitle}>{records}</Text> : null}
        </View>
        <MeetingBadge text={RESPONSE_LABELS[person.response]} color={color}
          icon={person.response === 'ACCEPTED' ? 'checkmark-circle' : person.response === 'DECLINED' ? 'close-circle' : 'time-outline'} />
      </View>
      {person.responseComment ? <Text style={[styles.hint, { marginLeft: 56 }]}>Motivo: {person.responseComment}</Text> : null}
      {president && recordable && <View style={[styles.actions, { marginLeft: 56 }]}>
        {meeting.status === 'IN_PROGRESS' && !person.enteredAt && person.userId !== userId && <MeetingAction label="Registrar entrada" icon="log-in-outline" disabled={busy} onPress={() => entry(meeting, person.userId)} />}
        {meeting.status === 'IN_PROGRESS' && person.enteredAt && !person.leftAt && person.userId !== userId && <MeetingAction label="Registrar saída" icon="log-out-outline" disabled={busy} onPress={() => exit(meeting, person.userId)} />}
        {(person.enteredAt || person.leftAt) && !attendanceLocked(meeting) && <MeetingAction label="Limpar registros" icon="refresh-outline" danger disabled={busy} onPress={() => resetPerson(meeting, person)} />}
      </View>}
    </View>;
  };

  const info = (icon: React.ComponentProps<typeof Ionicons>['name'], text: string, sub?: string | null, onPress?: () => void) => {
    const content = <>
      <Ionicons name={icon} size={22} color={colors.primary} />
      <View style={styles.flex}>
        <Text style={[styles.infoText, onPress && { color: colors.isDark ? '#ADB5EF' : colors.primary, textDecorationLine: 'underline' }]}>{text}</Text>
        {sub ? <Text style={styles.infoSub}>{sub}</Text> : null}
      </View>
    </>;
    return onPress
      ? <TouchableOpacity key={text} accessibilityRole="link" style={styles.infoRow} onPress={onPress}>{content}</TouchableOpacity>
      : <View key={text} style={styles.infoRow}>{content}</View>;
  };

  // Minutes and vote pages open inside the same sheet; 'card' goes back to the detail.
  const openPage = (page: MinutesPage | PollPage) => { setActionError(''); setView(page === 'card' ? 'detail' : page); };
  const refreshDetail = () => { if (detailRef.current) void loadDetail(detailRef.current.id); void load(); };

  const renderDetail = (meeting: Meeting) => {
    const president = presidedIds.has(meeting.committeeId);
    const participants = [...(meeting.participants ?? [])].sort((a, b) => a.userName.localeCompare(b.userName));
    const declined = participants.filter(person => person.response === 'DECLINED');
    const shown = allGuests ? participants : participants.slice(0, GUESTS_SHOWN);
    const organizer = meeting.createdByUserId && meeting.createdByUserId === userId ? 'você' : meeting.createdByName;
    const [agendaFirst, ...agendaRest] = (meeting.agenda ?? '').split('\n');
    return <>
      <View style={[styles.badges, { marginTop: 0 }]}>
        <MeetingBadge text={STATUS_LABELS[meeting.status]} color={statusColor(meeting.status)} icon={statusIcon(meeting.status)} />
        <MeetingBadge text={PRIORITY_LABELS[meeting.priority]} color={priorityColor(meeting.priority)} icon="flag-outline" />
      </View>
      <Text style={[styles.sheetTitle, { marginTop: 10 }]}>{meeting.title}</Text>
      <Text style={styles.subtitle}>{meeting.committeeName} · organizada por {organizer}</Text>
      {viewOnly(meeting) && <View style={[styles.notice, { marginTop: 12 }]}>
        <Ionicons name="eye-outline" size={22} color={colors.primary} />
        <View style={styles.flex}><Text style={styles.cardTitle}>Só visualização</Text>
          <Text style={styles.infoText}>Você vê esta reunião por ser líder da organização. Só o presidente da comissão edita, e só os convidados respondem e registram presença.</Text></View>
      </View>}
      {president && declined.length > 0 && <View style={[styles.notice, styles.noticeDanger, { marginTop: 12 }]}>
        <Ionicons name="close-circle" size={22} color={colors.danger} />
        <View style={styles.flex}>
          <Text style={styles.cardTitle}>{declined.length === 1 ? '1 convidado recusou' : `${declined.length} convidados recusaram`}</Text>
          {declined.map(person => <Text key={person.userId} style={[styles.infoText, { marginTop: 4 }]}>
            <Text style={{ fontWeight: '700' }}>{person.userName}</Text>: {person.responseComment ?? 'não informou o motivo.'}</Text>)}
        </View>
      </View>}
      <View style={{ marginTop: 8, marginBottom: 12 }}>
        {meeting.status === 'CANCELED' && info('ban-outline', 'Reunião cancelada', meeting.cancelReason ? `Motivo: ${meeting.cancelReason}` : null)}
        {info('calendar-outline', meetingDates(meeting))}
        {info('time-outline', meetingTimes(meeting), meeting.allDay ? 'Das 00:00 às 23:59' : null)}
        {meeting.recurrence && info('repeat-outline', `Repete ${recurrenceText(meeting.recurrence, parseLocal(meeting.startsAt))}`, 'Esta reunião faz parte de uma série.')}
        {meeting.location && info('location-outline', meeting.location, meeting.modality ? `Reunião ${MODALITY_LABELS[meeting.modality].toLowerCase()}` : null)}
        {!meeting.location && meeting.modality && info('videocam-outline', `Reunião ${MODALITY_LABELS[meeting.modality].toLowerCase()}`)}
        {meeting.link && info('link-outline', displayLink(meeting.link), null, () => openLink(meeting.link!))}
        {meeting.agenda && info('document-text-outline', agendaFirst, agendaRest.join('\n') || null)}
      </View>
      {meeting.attachment && <>
        <Text style={styles.sectionLabel}>ANEXO DA PAUTA</Text>
        <TouchableOpacity accessibilityRole="button" accessibilityLabel={`Baixar ${meeting.attachment.fileName}`} disabled={busy}
          onPress={() => void downloadAttachment(meeting)} style={[styles.fileCard, { marginBottom: 16 }]}>
          <Ionicons name="document-text-outline" size={26} color={colors.primary} />
          <View style={styles.flex}><Text style={styles.cardTitle} numberOfLines={1}>{meeting.attachment.fileName}</Text>
            <Text style={styles.subtitle}>{formatSize(meeting.attachment.sizeBytes)}</Text></View>
          {busy ? <ActivityIndicator color={colors.primary} /> : <Ionicons name="download-outline" size={24} color={colors.subtext} />}
        </TouchableOpacity>
      </>}
      <MeetingInvitationActions key={`${meeting.id}:${meeting.myResponse}`} meeting={meeting} busy={busy}
        onAccept={() => void act(`/meetings/${meeting.id}/respond?accept=true`, 'POST', undefined, 'Presença confirmada.')}
        onDecline={comment => void act(`/meetings/${meeting.id}/respond?accept=false`, 'POST', { comment: comment.trim() || null }, 'Recusa enviada.')} />
      {meeting.participants === null ? <ActivityIndicator color={colors.primary} accessibilityLabel="Carregando participantes" /> : <>
        <MeetingAttendancePanel meeting={meeting} userId={userId} isPresident={president} busy={busy}
          onEntry={target => entry(meeting, target)} onExit={target => exit(meeting, target)}
          onEntryAll={() => confirm('Registrar entrada de todos', 'Registrar agora a entrada de todos que aceitaram o convite e ainda não entraram?', 'Registrar',
            () => void act(`/meetings/${meeting.id}/attendance/entries`, 'POST', undefined, 'Entradas registradas.'))}
          onExitAll={() => confirm('Registrar saída de todos', 'Registrar agora a saída de todos que entraram e ainda não saíram?', 'Registrar',
            () => void act(`/meetings/${meeting.id}/attendance/exits`, 'POST', undefined, 'Saídas registradas.'))}
          onShowList={() => void showList(meeting)} onGenerateList={() => generateList(meeting)} />
        <MinutesSection meeting={meeting} president={president} userId={userId} token={token} page="card" onPage={openPage} onChanged={refreshDetail} />
        <MeetingPolls meeting={meeting} president={president} userId={userId} token={token} page="card" onPage={openPage} onChanged={refreshDetail} />
        <Text style={[styles.sectionLabel, { marginTop: 4 }]}>CONVIDADOS ({participants.length})</Text>
        {president && meeting.status === 'ENDED' && <Text style={[styles.hint, { marginTop: -6, marginBottom: 6 }]}>{attendanceLocked(meeting)
          ? 'A presença não pode mais ser corrigida porque a ata já foi enviada.' : 'Você pode corrigir a presença até enviar a ata.'}</Text>}
        {shown.map(person => personRow(meeting, person, president))}
        {participants.length > GUESTS_SHOWN && <TouchableOpacity accessibilityRole="button" style={[styles.personRow, { minHeight: 44, justifyContent: 'center' }]}
          onPress={() => setAllGuests(value => !value)}>
          <Text style={styles.link}>{allGuests ? 'Mostrar menos' : `+ ${participants.length - GUESTS_SHOWN} convidados`}</Text>
        </TouchableOpacity>}
      </>}
      {president && isOpen(meeting) && <View style={{ marginTop: 16 }}>
        {/* The form needs the current invitees; editing before they load would drop them. */}
        <MeetingAction label="Editar reunião" icon="create-outline" disabled={busy || meeting.participants === null}
          onPress={() => { if (meeting.participants !== null) { setDetail(null); setForm({ mode: 'edit', meeting }); } }} />
        <MeetingAction label="Cancelar reunião" icon="ban-outline" danger disabled={busy} onPress={() => { setCancelReason(''); setActionError(''); setView('cancel'); }} />
      </View>}
    </>;
  };

  const renderCancel = (meeting: Meeting) => <>
    {meeting.seriesId ? <View style={[styles.notice, styles.noticeWarning, { marginBottom: 16 }]}>
      <Ionicons name="warning" size={22} color={colors.warning} />
      <View style={styles.flex}><Text style={styles.cardTitle}>Reunião de uma série</Text>
        <Text style={styles.infoText}>Só a reunião de {formatDay(parseLocal(meeting.startsAt))} será cancelada. As outras da série continuam agendadas.</Text></View>
    </View> : null}
    <Text style={styles.cardTitle}>{meeting.title}</Text>
    <Text style={[styles.subtitle, { marginBottom: 16 }]}>{meeting.committeeName} · {meetingTimes(meeting)}</Text>
    <FormField label="Motivo (opcional)" icon="create-outline" placeholder="Ex: Sala indisponível nesta data." value={cancelReason} maxLength={300} showCount
      multiline editable={!busy} onChangeText={setCancelReason} style={{ minHeight: 90, textAlignVertical: 'top', paddingTop: 12 }} />
    <View style={[styles.row, { gap: 8 }]}>
      <Ionicons name="mail-outline" size={18} color={colors.subtext} />
      <Text style={[styles.hint, styles.flex]}>O motivo aparece no e-mail enviado aos convidados.</Text>
    </View>
  </>;

  const detailTitle = view === 'list' ? 'Lista de presença' : view === 'cancel' ? 'Cancelar reunião'
    : view.startsWith('minutes-') ? minutesTitle(view as MinutesPage, detail?.minutesStatus ?? null)
    : view.startsWith('poll-') ? pollTitle(view as PollPage) : 'Detalhe da reunião';
  const cancelFooter = detail && view === 'cancel' ? <View style={styles.footer}>
    {actionError ? <Text accessibilityRole="alert" style={styles.error}>{actionError}</Text> : null}
    <MeetingAction label="Cancelar reunião" icon="ban-outline" danger disabled={busy} onPress={() => void cancelMeeting(detail)} />
    <FormButton label="Manter reunião" secondary disabled={busy} onPress={() => setView('detail')} />
  </View> : undefined;

  const listContent = page === 'invites' ? <>
    <TouchableOpacity accessibilityRole="button" accessibilityLabel="Voltar às reuniões" style={[styles.row, { minHeight: 48, marginBottom: 8 }]} onPress={() => setPage('list')}>
      <Ionicons name="arrow-back" size={22} color={colors.text} /><Text style={styles.cardTitle}>Pendências</Text>
    </TouchableOpacity>
    {minutesPending.length > 0 && <>
      <Text style={styles.sectionLabel}>ATAS PARA RESPONDER ({minutesPending.length})</Text>
      <Text style={[styles.subtitle, { marginBottom: 12 }]}>Toque em uma ata para ler e aprovar ou reprovar.</Text>
      {minutesPending.map(meeting => <TouchableOpacity key={`ata-${meeting.id}`} accessibilityRole="button" accessibilityLabel={`Ler a ata de ${meeting.title}`}
        style={styles.card} onPress={() => openMinutes(meeting)}>
        <View style={styles.row}>
          <Ionicons name="document-text-outline" size={24} color={colors.primary} />
          <View style={styles.flex}><Text style={styles.cardTitle}>{meeting.title}</Text>
            <Text style={styles.subtitle}>{meeting.committeeName} · {capitalize(formatDay(parseLocal(meeting.startsAt)))}</Text></View>
          <Ionicons name="chevron-forward" size={22} color={colors.subtext} />
        </View>
      </TouchableOpacity>)}
    </>}
    {refusals.length > 0 && <>
      <Text style={styles.sectionLabel}>RECUSAS DOS SEUS CONVITES ({refusals.length})</Text>
      <Text style={[styles.subtitle, { marginBottom: 12 }]}>Cada recusa sai daqui quando a reunião termina ou quando a pessoa muda a resposta.</Text>
      {refusals.map(({ meeting, person }) => <TouchableOpacity key={`recusa-${meeting.id}-${person.userId}`} accessibilityRole="button"
        accessibilityLabel={`${person.userName} recusou ${meeting.title}`} style={styles.card} onPress={() => openDetail(meeting)}>
        <View style={[styles.row, { alignItems: 'flex-start' }]}>
          <UserAvatar userId={person.userId} name={person.userName} style={styles.avatar} textStyle={styles.avatarText} />
          <View style={styles.flex}>
            <Text style={styles.cardTitle}>{person.userName} recusou</Text>
            <Text style={styles.subtitle}>{meeting.title} · {capitalize(formatDay(parseLocal(meeting.startsAt)))}</Text>
            <Text style={[styles.infoText, { marginTop: 6 }]}>{person.responseComment ? `Motivo: ${person.responseComment}` : 'Não informou o motivo.'}</Text>
          </View>
          <Ionicons name="chevron-forward" size={22} color={colors.subtext} />
        </View>
      </TouchableOpacity>)}
    </>}
    <Text style={styles.sectionLabel}>CONVITES PARA RESPONDER ({pending.length})</Text>
    <Text style={[styles.subtitle, { marginBottom: 12 }]}>Toque em um convite para ver a reunião e responder. Em uma série, cada data tem a sua resposta.</Text>
    {pending.map(meetingCard)}
    {!pending.length && <Text style={styles.subtitle}>Nenhum convite pendente.</Text>}
  </> : <>
    {hasPending && <>
      <TouchableOpacity accessibilityRole="button" style={styles.banner} onPress={() => setPage('invites')}>
        <Ionicons name={pendingText ? 'mail-unread-outline' : 'close-circle-outline'} size={26} color={pendingText ? colors.primary : colors.danger} />
        <View style={styles.flex}>
          <Text style={styles.cardTitle}>{pendingText ? `Você tem ${pendingText} para responder.` : refusalText}</Text>
          <Text style={styles.infoText}>{pendingText && refusalText ? `${refusalText} Toque para ver as pendências.`
            : !pendingText ? 'Toque para ver quem recusou e o motivo.'
            : !pending.length ? `Toque para ler ${minutesPending.length === 1 ? 'a ata' : 'as atas'} e aprovar ou reprovar.`
            : !minutesPending.length ? 'Toque para ver os convites e responder.' : `Toque para ver os convites e ${minutesPending.length === 1 ? 'a ata' : 'as atas'}.`}</Text>
        </View>
      </TouchableOpacity>
      <TouchableOpacity accessibilityRole="button" style={[styles.row, { justifyContent: 'center', minHeight: 44, marginBottom: 6 }]}
        onPress={() => (!pending.length && !refusals.length && minutesPending.length === 1 ? openMinutes(minutesPending[0]) : setPage('invites'))}>
        <Text style={styles.link}>{refusals.length && pendingText ? 'Ver Pendências' : refusals.length ? 'Ver recusas'
          : !pending.length ? (minutesPending.length === 1 ? 'Ver Ata' : 'Ver Atas') : !minutesPending.length ? 'Ver convites' : 'Ver Pendências'}</Text>
        <Ionicons name="chevron-forward" size={18} color={colors.isDark ? '#ADB5EF' : colors.primary} />
      </TouchableOpacity>
    </>}
    <View style={styles.filters}>
      {FILTERS.map(item => <TouchableOpacity key={item.value} accessibilityRole="tab" accessibilityState={{ selected: filter === item.value }}
        style={[styles.filter, filter === item.value && styles.filterActive]} onPress={() => setFilter(item.value)}>
        <Text style={[styles.filterText, filter === item.value && { color: '#FFFFFF' }]}>{item.label}</Text></TouchableOpacity>)}
    </View>
    {loading ? <ActivityIndicator color={colors.primary} accessibilityLabel="Carregando reuniões" /> : <>
      {filter !== 'past' && live.length > 0 && <><Text style={styles.sectionLabel}>ACONTECENDO AGORA</Text>{live.map(liveCard)}</>}
      {filter !== 'past' && upcoming.length > 0 && <><Text style={styles.sectionLabel}>PRÓXIMAS</Text>{upcoming.map(meetingCard)}</>}
      {filter !== 'upcoming' && past.length > 0 && <><Text style={styles.sectionLabel}>ANTERIORES</Text>{past.map(meetingCard)}</>}
      {!loadError && (filter === 'past' ? !past.length : filter === 'upcoming' ? !live.length && !upcoming.length : !meetings.length) && empty}
    </>}
  </>;

  return <View style={{ flex: 1, backgroundColor: colors.background, paddingTop: insets.top + 12 }}>
    <View style={[styles.row, { paddingHorizontal: 20, marginBottom: 14 }]}>
      <View style={styles.flex}><Text style={styles.title}>Reuniões</Text><Text style={styles.subtitle}>{capitalize(formatDay(new Date()))}</Text></View>
      <TouchableOpacity accessibilityRole="button" accessibilityLabel="Abrir Meu Relatório" style={[styles.row, { gap: 6, minHeight: 48 }]}
        onPress={() => navigation.navigate('Settings', { open: 'report' })}>
        <Ionicons name="bar-chart-outline" size={20} color={colors.isDark ? '#ADB5EF' : colors.primary} /><Text style={styles.link}>Meu Relatório</Text>
      </TouchableOpacity>
      <TouchableOpacity accessibilityRole="button" accessibilityLabel="Atualizar reuniões" style={styles.iconButton} onPress={handleRefresh} disabled={paused}>
        {refreshing ? <ActivityIndicator color={colors.primary} /> : <Ionicons name="refresh-outline" size={22} color={colors.primary} />}</TouchableOpacity>
    </View>
    <FormScrollView contentContainerStyle={[styles.content, { paddingBottom: 110 }]}
      refreshControl={<RefreshControl refreshing={refreshing} onRefresh={handleRefresh} tintColor={colors.primary} colors={[colors.primary]} enabled={!paused} />}>
      {loadError ? <><Text accessibilityRole="alert" style={styles.error}>{loadError}</Text><MeetingAction label="Tentar atualizar" icon="refresh-outline" onPress={handleRefresh} /></> : null}
      {listContent}
    </FormScrollView>
    {presided.length > 0 && page === 'list' && <TouchableOpacity accessibilityRole="button" accessibilityLabel="Agendar reunião" style={[styles.fab, { bottom: 20 }]}
      onPress={() => { setActionError(''); setForm({ mode: 'create' }); }}>
      <Ionicons name="add" size={32} color="#FFFFFF" /></TouchableOpacity>}

    <Modal visible={!!detail} transparent animationType="slide" onRequestClose={() => (view === 'detail' ? closeDetail() : setView('detail'))}>
      <View style={styles.overlay}><View style={[styles.sheet, { paddingBottom: insets.bottom }]}><View style={styles.sheetHandle} />
        <View style={styles.sheetHeader}>
          {view !== 'detail' && <TouchableOpacity accessibilityRole="button" accessibilityLabel="Voltar à reunião" style={styles.iconButton} onPress={() => setView('detail')} disabled={busy}>
            <Ionicons name="arrow-back" size={24} color={colors.text} /></TouchableOpacity>}
          <Text style={[styles.sheetTitle, styles.flex]}>{detailTitle}</Text>
          <TouchableOpacity accessibilityRole="button" accessibilityLabel="Fechar reunião" style={styles.iconButton} onPress={closeDetail} disabled={busy}>
            <Ionicons name="close-outline" size={26} color={colors.subtext} /></TouchableOpacity></View>
        <FormScrollView contentContainerStyle={styles.content} footer={cancelFooter}>
          {actionError && view !== 'cancel' ? <Text accessibilityRole="alert" style={styles.error}>{actionError}</Text> : null}
          {detail && (view === 'list' && attendanceList ? <AttendanceListView list={attendanceList} />
            : view === 'cancel' ? renderCancel(detail)
            : view.startsWith('minutes-') ? <MinutesSection meeting={detail} president={presidedIds.has(detail.committeeId)} userId={userId} token={token}
              page={view as MinutesPage} onPage={openPage} onChanged={refreshDetail} />
            : view.startsWith('poll-') ? <MeetingPolls meeting={detail} president={presidedIds.has(detail.committeeId)} userId={userId} token={token}
              page={view as PollPage} onPage={openPage} onChanged={refreshDetail} />
            : renderDetail(detail))}
        </FormScrollView>
      </View></View><ToastHost active={!!detail} /><ConfirmationHost active={!!detail} />
    </Modal>

    <Modal visible={form !== null} transparent animationType="slide" onRequestClose={closeForm}>
      <View style={styles.overlay}><View style={[styles.sheet, { paddingBottom: insets.bottom }]}><View style={styles.sheetHandle} />
        <View style={styles.sheetHeader}><Text style={[styles.sheetTitle, styles.flex]}>{form?.mode === 'edit' ? 'Editar reunião' : 'Agendar reunião'}</Text>
          <TouchableOpacity accessibilityRole="button" accessibilityLabel="Fechar formulário" style={styles.iconButton} onPress={closeForm} disabled={busy}>
            <Ionicons name="close-outline" size={26} color={colors.subtext} /></TouchableOpacity></View>
        {form && <MeetingForm key={form.mode === 'edit' ? `edit-${form.meeting.id}` : 'create'} committees={presided} meeting={form.mode === 'edit' ? form.meeting : null}
          busy={busy} error={actionError} onSubmit={payload => void submitForm(payload)} onCancel={closeForm} />}
      </View></View><ToastHost active={form !== null} /><ConfirmationHost active={form !== null} />
    </Modal>
  </View>;
}
