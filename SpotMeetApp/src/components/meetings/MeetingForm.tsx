import React, { useEffect, useRef, useState } from 'react';
import { ActivityIndicator, Text, TouchableOpacity, View } from 'react-native';
import { Controller, useForm, type RegisterOptions } from 'react-hook-form';
import { Ionicons } from '@react-native-vector-icons/ionicons';
import { getDocumentAsync } from 'expo-document-picker';
import { useAuth } from '../../context/AuthContext';
import { API_BASE_URL } from '../../config/api';
import FormField from '../forms/FormField';
import FormButton from '../forms/FormButton';
import FormScrollView from '../forms/FormScrollView';
import DateTimeField, { PickerField } from './DateTimeField';
import FrequencySheet from './FrequencySheet';
import InviteesSheet from './InviteesSheet';
import { useMeetingStyles } from './meetingStyles';
import {
  MAX_ATTACHMENT_BYTES, MODALITY_LABELS, PRIORITY_LABELS, capitalize, formatClock, formatDay, formatSize, parseLocal,
  readCommitteeMembers, recurrenceText, sameDay, toLocalIso, validateLink,
  type CommitteeMemberRow, type Meeting, type MeetingAttachment, type MeetingModality, type MeetingPriority, type PresidedCommittee, type Recurrence,
} from '../../utils/meetings';

/** File chosen on the phone; the screen sends it before saving the meeting. */
export interface PendingFile { uri: string; name: string; size: number; mimeType: string | null; }

export interface MeetingPayload {
  committeeId: number;
  body: {
    title: string; agenda: string | null; location: string | null; link: string | null; priority: MeetingPriority;
    modality: MeetingModality; allDay: boolean; startsAt: string; endsAt: string; invitedUserIds: number[];
    attachmentFileId: number | null; recurrence?: Recurrence;
  };
  upload: PendingFile | null;
}

interface Fields { title: string; location: string; link: string; agenda: string; }

interface Props {
  committees: PresidedCommittee[];
  meeting: Meeting | null;
  busy: boolean;
  error: string;
  onSubmit: (payload: MeetingPayload) => void;
  onCancel: () => void;
}

type Attachment = { kind: 'saved'; file: MeetingAttachment } | { kind: 'new'; file: PendingFile } | null;

const PRIORITIES: MeetingPriority[] = ['LOW', 'MEDIUM', 'HIGH'];
const MODALITIES: MeetingModality[] = ['IN_PERSON', 'ONLINE', 'HYBRID'];
const FILE_TYPES = ['application/pdf', 'application/msword', 'application/vnd.openxmlformats-officedocument.wordprocessingml.document', 'image/jpeg', 'image/png'];
const HOUR = 60 * 60 * 1000;
const DAY = 24 * HOUR;

const startOfDay = (date: Date) => new Date(date.getFullYear(), date.getMonth(), date.getDate());
const withDate = (time: Date, day: Date) => new Date(day.getFullYear(), day.getMonth(), day.getDate(), time.getHours(), time.getMinutes());
const withTime = (day: Date, time: Date) => withDate(time, day);
const firstName = (name: string) => name.trim().split(/\s+/)[0];

function nextHour(): Date {
  const date = new Date(Date.now() + HOUR);
  date.setMinutes(0, 0, 0);
  return date;
}

/** Schedules or edits a meeting: dates, frequency, modality, attachment, priority and guests (everyone starts selected). */
export default function MeetingForm({ committees, meeting, busy, error, onSubmit, onCancel }: Props) {
  const { token } = useAuth();
  const { colors, styles } = useMeetingStyles();
  const editing = !!meeting;
  const originalStart = useRef(meeting ? parseLocal(meeting.startsAt).getTime() : null);
  const [committeeId, setCommitteeId] = useState<number | null>(meeting?.committeeId ?? committees[0]?.committeeId ?? null);
  const [priority, setPriority] = useState<MeetingPriority>(meeting?.priority ?? 'MEDIUM');
  const [modality, setModality] = useState<MeetingModality>(meeting?.modality ?? (meeting?.link ? (meeting.location ? 'HYBRID' : 'ONLINE') : 'IN_PERSON'));
  const [allDay, setAllDay] = useState(meeting?.allDay ?? false);
  const [start, setStart] = useState(() => meeting ? parseLocal(meeting.startsAt) : nextHour());
  const [end, setEnd] = useState(() => meeting ? parseLocal(meeting.endsAt) : new Date(nextHour().getTime() + HOUR));
  const [recurrence, setRecurrence] = useState<Recurrence | null>(null);
  const [attachment, setAttachment] = useState<Attachment>(meeting?.attachment ? { kind: 'saved', file: meeting.attachment } : null);
  const [fileError, setFileError] = useState('');
  const [members, setMembers] = useState<CommitteeMemberRow[] | null>(null);
  const [membersError, setMembersError] = useState('');
  const [invited, setInvited] = useState<Set<number>>(new Set());
  const [sheet, setSheet] = useState<'frequency' | 'invitees' | null>(null);
  const [tried, setTried] = useState(false);
  const initialInvited = useRef(new Set((meeting?.participants ?? []).map(person => person.userId)));
  const { control, handleSubmit } = useForm<Fields>({
    mode: 'onBlur',
    defaultValues: { title: meeting?.title ?? '', location: meeting?.location ?? '', link: meeting?.link ?? '', agenda: meeting?.agenda ?? '' },
  });

  useEffect(() => {
    if (committeeId === null || !token) return;
    let active = true;
    setMembers(null); setMembersError('');
    fetch(`${API_BASE_URL}/committees/${committeeId}/members`, { headers: { Authorization: `Bearer ${token}` } })
      .then(async res => { if (!res.ok) throw new Error('members'); return readCommitteeMembers(await res.json()); })
      .then(rows => {
        if (!active) return;
        setMembers(rows);
        setInvited(editing && committeeId === meeting?.committeeId
          ? new Set([...initialInvited.current].filter(id => rows.some(row => row.userId === id)))
          : new Set(rows.map(row => row.userId)));
      })
      .catch(() => { if (active) setMembersError('Não foi possível carregar os integrantes da comissão.'); });
    return () => { active = false; };
  }, [committeeId, token, editing, meeting?.committeeId]);

  // Changing the start keeps the duration, as in the Google Calendar.
  const moveStart = (next: Date) => { setEnd(current => new Date(current.getTime() + (next.getTime() - start.getTime()))); setStart(next); };
  const changeEndDate = (day: Date) => setEnd(withDate(end, day));
  // An end time before the start on the same day means the next day.
  const changeEndTime = (time: Date) => {
    let next = withTime(end, time);
    if (sameDay(next, start) && next <= start) next = new Date(next.getTime() + DAY);
    setEnd(next);
  };

  const now = new Date();
  const startChanged = originalStart.current === null || originalStart.current !== start.getTime();
  const startError = allDay
    ? (startChanged && startOfDay(start) < startOfDay(now) ? 'A reunião de dia inteiro não pode começar antes de hoje.' : '')
    : (startChanged && start <= now ? 'O início precisa ser no futuro.' : '');
  const endError = allDay
    ? (startOfDay(end) < startOfDay(start) ? 'A data final não pode ser antes da inicial.' : '')
    : end <= start ? 'O término deve ser depois do início.' : editing && end <= now ? 'O término precisa ser depois de agora.' : '';

  const endNotice = allDay
    ? sameDay(start, end) ? 'A reunião vale das 00:00 às 23:59.' : 'A reunião vale das 00:00 da data inicial às 23:59 da data final.'
    : sameDay(start, end) ? ''
      : sameDay(end, new Date(start.getTime() + DAY)) ? `Termina no dia seguinte, às ${formatClock(end)}.` : `Termina ${formatDay(end)}, às ${formatClock(end)}.`;

  const pickFile = async () => {
    try {
      const result = await getDocumentAsync({ type: FILE_TYPES, copyToCacheDirectory: true, multiple: false });
      if (result.canceled || !result.assets?.length) return;
      const asset = result.assets[0];
      const size = asset.size ?? 0;
      if (size > MAX_ATTACHMENT_BYTES) { setFileError('O arquivo passa de 10 MB. Escolha um menor.'); return; }
      setAttachment({ kind: 'new', file: { uri: asset.uri, name: asset.name, size, mimeType: asset.mimeType ?? null } });
      setFileError('');
    } catch { setFileError('Não foi possível abrir o arquivo. Tente de novo.'); }
  };

  const submit = handleSubmit(values => {
    setTried(true);
    if (committeeId === null || !members || startError || endError) return;
    const startsAt = allDay ? toLocalIso(startOfDay(start)) : toLocalIso(start);
    const endsAt = allDay ? toLocalIso(new Date(startOfDay(end).getTime() + DAY - 60 * 1000)) : toLocalIso(end);
    onSubmit({
      committeeId,
      body: {
        title: values.title.trim(), agenda: values.agenda.trim() || null, modality, priority, allDay, startsAt, endsAt,
        location: modality !== 'ONLINE' ? values.location.trim() || null : null,
        link: modality !== 'IN_PERSON' ? values.link.trim() || null : null,
        invitedUserIds: members.filter(row => row.role === 'COMMITTEE_ADMIN' || invited.has(row.userId)).map(row => row.userId),
        attachmentFileId: attachment?.kind === 'saved' ? attachment.file.id : null,
        ...(!editing && recurrence ? { recurrence } : {}),
      },
      upload: attachment?.kind === 'new' ? attachment.file : null,
    });
  }, () => setTried(true));

  const footer = <View style={styles.footer}>
    {error ? <Text accessibilityRole="alert" style={styles.error}>{error}</Text> : null}
    <FormButton label={editing ? 'Salvar alterações' : 'Agendar reunião'} icon={editing ? 'save-outline' : 'calendar-outline'} onPress={submit}
      loading={busy} disabled={!members || committeeId === null} />
    <FormButton label="Cancelar" secondary onPress={onCancel} disabled={busy} />
  </View>;

  const input = (name: keyof Fields, label: string, placeholder: string, rules: RegisterOptions<Fields, keyof Fields>, props: Record<string, unknown> = {}) => (
    <Controller control={control} name={name} rules={rules}
      render={({ field, fieldState }) => <FormField label={label} required={!!rules.required} placeholder={placeholder} value={field.value}
        onChangeText={field.onChange} onBlur={field.onBlur} ref={field.ref} error={fieldState.error?.message} editable={!busy} autoCorrect={false} {...props} />} />
  );

  const chips = <T extends string>(values: T[], current: T, labels: Record<T, string>, onPick: (value: T) => void) =>
    <View style={styles.filters}>{values.map(value => <TouchableOpacity key={value} accessibilityRole="radio" accessibilityState={{ selected: current === value }}
      disabled={busy} style={[styles.filter, current === value && styles.filterActive]} onPress={() => onPick(value)}>
      <Text style={[styles.filterText, current === value && { color: '#FFFFFF' }]}>{labels[value]}</Text></TouchableOpacity>)}</View>;

  const selectedRows = members?.filter(row => row.role === 'COMMITTEE_ADMIN' || invited.has(row.userId)) ?? [];
  const guestNames = selectedRows.filter(row => row.role !== 'COMMITTEE_ADMIN').map(row => firstName(row.userName));
  const namesText = guestNames.length === 0 ? 'Só o presidente.'
    : guestNames.length <= 4 ? `${guestNames.length === 1 ? guestNames[0] : `${guestNames.slice(0, -1).join(', ')} e ${guestNames[guestNames.length - 1]}`}.`
      : `${guestNames.slice(0, 3).join(', ')} e mais ${guestNames.length - 3}.`;
  const committeeName = committees.find(row => row.committeeId === committeeId)?.committeeName ?? meeting?.committeeName ?? '';
  const frequencyText = editing
    ? meeting?.recurrence ? capitalize(recurrenceText(meeting.recurrence, parseLocal(meeting.startsAt))) : 'Não se repete'
    : recurrence ? capitalize(recurrenceText(recurrence, start)) : 'Não se repete';

  return <>
    <FormScrollView contentContainerStyle={styles.content} footer={footer}>
      {editing && meeting?.seriesId ? <View style={[styles.notice, { marginBottom: 12 }]}>
        <Ionicons name="information-circle" size={22} color={colors.primary} />
        <View style={styles.flex}><Text style={styles.cardTitle}>Reunião de uma série</Text><Text style={styles.infoText}>Só esta reunião será alterada.</Text></View>
      </View> : null}
      {editing && meeting?.status === 'IN_PROGRESS' ? <View style={[styles.notice, styles.noticeWarning, { marginBottom: 12 }]}>
        <Ionicons name="time-outline" size={22} color={colors.warning} />
        <View style={styles.flex}><Text style={styles.cardTitle}>Reunião em andamento</Text><Text style={styles.infoText}>As alterações valem a partir de agora.</Text></View>
      </View> : null}

      {!editing && <>
        <Text style={styles.fieldLabel}>COMISSÃO</Text>
        {chips(committees.map(row => String(row.committeeId)), String(committeeId), Object.fromEntries(committees.map(row => [String(row.committeeId), row.committeeName])),
          value => setCommitteeId(Number(value)))}
      </>}
      {input('title', 'Título', 'Ex: Planejamento semanal', {
        required: 'Informe o título da reunião.', minLength: { value: 2, message: 'Use ao menos 2 caracteres.' }, maxLength: { value: 100, message: 'Use até 100 caracteres.' },
      }, { icon: 'create-outline', maxLength: 100, showCount: true })}

      <DateTimeField label="Data inicial" mode="date" value={start} disabled={busy} error={tried && allDay ? startError : ''}
        minimumDate={editing ? undefined : startOfDay(now)} onChange={day => moveStart(withDate(start, day))} />
      <TouchableOpacity accessibilityRole="checkbox" accessibilityState={{ checked: allDay }} disabled={busy} onPress={() => setAllDay(value => !value)}
        style={[styles.row, { minHeight: 48, marginBottom: 8 }]}>
        <Ionicons name={allDay ? 'checkbox' : 'square-outline'} size={26} color={allDay ? colors.primary : colors.subtext} />
        <Text style={styles.optionText}>Dia inteiro</Text>
      </TouchableOpacity>
      <DateTimeField label="Data final" mode="date" value={end} disabled={busy} error={tried ? (allDay ? endError : '') : ''}
        minimumDate={startOfDay(start)} onChange={changeEndDate} />
      {endNotice ? <Text style={[styles.hint, { marginTop: -6, marginBottom: 10 }]}>{endNotice}</Text> : null}
      {!allDay && <View style={[styles.row, { alignItems: 'flex-start' }]}>
        <DateTimeField label="Início" mode="time" value={start} disabled={busy} error={tried ? startError : ''} onChange={time => moveStart(withTime(start, time))} />
        <DateTimeField label="Término" mode="time" value={end} disabled={busy} error={tried ? endError : ''} onChange={changeEndTime} />
      </View>}

      <PickerField label="Frequência" value={frequencyText} icon="repeat-outline" disabled={busy || editing} onPress={() => setSheet('frequency')} />
      {editing && meeting?.seriesId ? <Text style={[styles.hint, { marginTop: -6, marginBottom: 10 }]}>A frequência da série não muda por aqui.</Text> : null}

      <Text style={styles.fieldLabel}>MODALIDADE</Text>
      {chips(MODALITIES, modality, MODALITY_LABELS, setModality)}
      {modality !== 'ONLINE' && input('location', 'Local (opcional)', 'Ex: Sala 3', { maxLength: { value: 100, message: 'Use até 100 caracteres.' } }, { icon: 'location-outline' })}
      {modality !== 'IN_PERSON' && input('link', 'Link (opcional)', 'Ex: meet.google.com/abc-defg-hij', { validate: validateLink },
        { icon: 'link-outline', autoCapitalize: 'none', keyboardType: 'url' })}
      {input('agenda', 'Pauta (opcional)', 'Assuntos que serão tratados', { maxLength: { value: 2000, message: 'Use até 2000 caracteres.' } },
        { icon: 'document-text-outline', multiline: true, maxLength: 2000, showCount: true, style: { minHeight: 90, textAlignVertical: 'top', paddingTop: 12 } })}

      <Text style={styles.fieldLabel}>ANEXO DA PAUTA (OPCIONAL)</Text>
      {attachment ? <View style={[styles.fileCard, fileError ? { borderColor: colors.danger } : null]}>
        <Ionicons name="document-text-outline" size={26} color={colors.primary} />
        <View style={styles.flex}>
          <Text style={styles.cardTitle} numberOfLines={1}>{attachment.kind === 'saved' ? attachment.file.fileName : attachment.file.name}</Text>
          <Text style={styles.subtitle}>{formatSize(attachment.kind === 'saved' ? attachment.file.sizeBytes : attachment.file.size)}</Text>
        </View>
        <TouchableOpacity accessibilityRole="button" accessibilityLabel="Remover anexo" style={styles.iconButton} disabled={busy}
          onPress={() => { setAttachment(null); setFileError(''); }}>
          <Ionicons name="close-circle-outline" size={24} color={colors.subtext} /></TouchableOpacity>
      </View> : <TouchableOpacity accessibilityRole="button" disabled={busy} onPress={() => void pickFile()} style={[styles.action, { marginBottom: 4 }]}>
        <Ionicons name="attach-outline" size={20} color={colors.isDark ? '#ADB5EF' : colors.primary} />
        <Text style={[styles.actionText, { color: colors.isDark ? '#ADB5EF' : colors.primary }]}>Anexar documento da pauta</Text>
      </TouchableOpacity>}
      {fileError ? <Text accessibilityRole="alert" style={styles.fieldError}>{fileError}</Text>
        : <Text style={[styles.hint, { marginBottom: 12 }]}>PDF, DOC ou imagem, até 10 MB.</Text>}

      <Text style={styles.fieldLabel}>PRIORIDADE</Text>
      {chips(PRIORITIES, priority, PRIORITY_LABELS, setPriority)}

      <Text style={styles.fieldLabel}>CONVIDADOS</Text>
      {membersError ? <Text accessibilityRole="alert" style={styles.error}>{membersError}</Text> : null}
      {!members && !membersError ? <ActivityIndicator color={colors.primary} accessibilityLabel="Carregando integrantes" /> : null}
      {members && <>
        <PickerField value={`${selectedRows.length} de ${members.length} selecionados`} icon="people-outline" chevron="forward" disabled={busy}
          onPress={() => setSheet('invitees')} accessibilityLabel={`Convidados: ${selectedRows.length} de ${members.length} selecionados`} />
        <Text style={[styles.hint, { marginTop: -6 }]}>{namesText}</Text>
      </>}
    </FormScrollView>

    <FrequencySheet visible={sheet === 'frequency'} anchor={start} value={recurrence}
      onDone={value => { setRecurrence(value); setSheet(null); }} onClose={() => setSheet(null)} />
    <InviteesSheet visible={sheet === 'invitees'} committeeName={committeeName} members={members ?? []} selected={invited}
      onDone={value => { setInvited(value); setSheet(null); }} onClose={() => setSheet(null)} />
  </>;
}
