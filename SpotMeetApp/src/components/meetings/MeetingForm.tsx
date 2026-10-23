import React, { useEffect, useRef, useState } from 'react';
import { ActivityIndicator, Text, TouchableOpacity, View } from 'react-native';
import { Controller, useForm, type RegisterOptions } from 'react-hook-form';
import { Ionicons } from '@react-native-vector-icons/ionicons';
import { useAuth } from '../../context/AuthContext';
import { API_BASE_URL } from '../../config/api';
import FormField from '../forms/FormField';
import FormButton from '../forms/FormButton';
import FormScrollView from '../forms/FormScrollView';
import { MeetingBadge, useMeetingStyles } from './meetingStyles';
import {
  DATE_TIME_PLACEHOLDER, MAX_DURATION_HOURS, PRIORITY_LABELS, maskDateTime, parseDateTime, readCommitteeMembers, toInputText, validateLink,
  type CommitteeMemberRow, type Meeting, type MeetingPriority, type PresidedCommittee,
} from '../../utils/meetings';

export interface MeetingPayload {
  committeeId: number;
  body: {
    title: string; agenda: string | null; location: string | null; link: string | null; priority: MeetingPriority;
    startsAt: string; endsAt: string; invitedUserIds: number[];
  };
}

interface Fields { title: string; startsAt: string; endsAt: string; location: string; link: string; agenda: string; }

interface Props {
  committees: PresidedCommittee[];
  meeting: Meeting | null;
  busy: boolean;
  error: string;
  onSubmit: (payload: MeetingPayload) => void;
  onCancel: () => void;
}

const PRIORITIES: MeetingPriority[] = ['LOW', 'MEDIUM', 'HIGH'];

/** Schedules or edits a meeting: data, priority and who is invited (everyone starts selected). */
export default function MeetingForm({ committees, meeting, busy, error, onSubmit, onCancel }: Props) {
  const { token } = useAuth();
  const { colors, styles } = useMeetingStyles();
  const editing = !!meeting;
  const [committeeId, setCommitteeId] = useState<number | null>(meeting?.committeeId ?? committees[0]?.committeeId ?? null);
  const [priority, setPriority] = useState<MeetingPriority>(meeting?.priority ?? 'MEDIUM');
  const [members, setMembers] = useState<CommitteeMemberRow[] | null>(null);
  const [membersError, setMembersError] = useState('');
  const [invited, setInvited] = useState<Set<number>>(new Set());
  const initialInvited = useRef(new Set((meeting?.participants ?? []).map(person => person.userId)));
  const { control, handleSubmit, getValues } = useForm<Fields>({
    mode: 'onBlur',
    defaultValues: {
      title: meeting?.title ?? '', startsAt: meeting ? toInputText(meeting.startsAt) : '', endsAt: meeting ? toInputText(meeting.endsAt) : '',
      location: meeting?.location ?? '', link: meeting?.link ?? '', agenda: meeting?.agenda ?? '',
    },
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

  const toggle = (row: CommitteeMemberRow) => {
    if (row.role === 'COMMITTEE_ADMIN' || busy) return;
    setInvited(current => { const next = new Set(current); if (next.has(row.userId)) next.delete(row.userId); else next.add(row.userId); return next; });
  };

  const validateStart = (value: string): true | string => {
    const start = parseDateTime(value);
    if (!start) return 'Use o formato dd/mm/aaaa hh:mm com uma data real.';
    return new Date(start).getTime() > Date.now() || 'O início deve ser uma data futura.';
  };
  const validateEnd = (value: string): true | string => {
    const end = parseDateTime(value);
    if (!end) return 'Use o formato dd/mm/aaaa hh:mm com uma data real.';
    const start = parseDateTime(getValues('startsAt'));
    if (!start) return true;
    const minutes = (new Date(end).getTime() - new Date(start).getTime()) / 60000;
    if (minutes <= 0) return 'O término deve ser depois do início.';
    return minutes <= MAX_DURATION_HOURS * 60 || `A reunião pode durar no máximo ${MAX_DURATION_HOURS} horas.`;
  };

  const submit = handleSubmit(values => {
    if (committeeId === null || !members) return;
    onSubmit({
      committeeId,
      body: {
        title: values.title.trim(), agenda: values.agenda.trim() || null, location: values.location.trim() || null, link: values.link.trim() || null,
        priority, startsAt: parseDateTime(values.startsAt)!, endsAt: parseDateTime(values.endsAt)!,
        invitedUserIds: members.filter(row => row.role === 'COMMITTEE_ADMIN' || invited.has(row.userId)).map(row => row.userId),
      },
    });
  });

  const footer = <View style={styles.footer}>
    {error ? <Text accessibilityRole="alert" style={styles.error}>{error}</Text> : null}
    <FormButton label={editing ? 'Salvar alterações' : 'Agendar reunião'} icon={editing ? 'save-outline' : 'calendar-outline'} onPress={submit}
      loading={busy} disabled={!members || committeeId === null} />
    <FormButton label="Cancelar" secondary onPress={onCancel} disabled={busy} />
  </View>;

  const input = (name: keyof Fields, label: string, placeholder: string, rules: RegisterOptions<Fields, keyof Fields>, extra: Record<string, unknown> = {}) => (
    <Controller control={control} name={name} rules={rules}
      render={({ field, fieldState }) => <FormField label={label} required={!!rules.required} placeholder={placeholder} value={field.value}
        onChangeText={value => field.onChange(extra.mask ? maskDateTime(value) : value)} onBlur={field.onBlur} ref={field.ref}
        error={fieldState.error?.message} editable={!busy} autoCorrect={false} {...extra.props as object} />} />
  );

  return (
    <FormScrollView contentContainerStyle={styles.content} footer={footer}>
      {!editing && <>
        <Text style={styles.sectionLabel}>COMISSÃO</Text>
        <View style={styles.filters}>{committees.map(committee => <TouchableOpacity key={committee.committeeId} accessibilityRole="button"
          accessibilityState={{ selected: committeeId === committee.committeeId }} disabled={busy}
          style={[styles.filter, committeeId === committee.committeeId && styles.filterActive]} onPress={() => setCommitteeId(committee.committeeId)}>
          <Text style={[styles.filterText, committeeId === committee.committeeId && { color: '#FFFFFF' }]}>{committee.committeeName}</Text></TouchableOpacity>)}</View>
      </>}
      {input('title', 'Título', 'Ex: Planejamento semanal', {
        required: 'Informe o título.', minLength: { value: 2, message: 'Use ao menos 2 caracteres.' }, maxLength: { value: 100, message: 'Use até 100 caracteres.' },
      })}
      {input('startsAt', 'Início', DATE_TIME_PLACEHOLDER, { required: 'Informe o início.', validate: validateStart },
        { mask: true, props: { keyboardType: 'number-pad', icon: 'calendar-outline', maxLength: 16 } })}
      {input('endsAt', 'Término', DATE_TIME_PLACEHOLDER, { required: 'Informe o término.', validate: validateEnd },
        { mask: true, props: { keyboardType: 'number-pad', icon: 'time-outline', maxLength: 16 } })}
      <Text style={styles.hint}>Horário do servidor. A reunião pode durar até {MAX_DURATION_HOURS} horas.</Text>
      {input('location', 'Local (opcional)', 'Ex: Sala 3', { maxLength: { value: 100, message: 'Use até 100 caracteres.' } }, { props: { icon: 'location-outline' } })}
      {input('link', 'Link (opcional)', 'https://', { validate: validateLink }, { props: { icon: 'link-outline', autoCapitalize: 'none', keyboardType: 'url' } })}
      {input('agenda', 'Pauta (opcional)', 'Assuntos que serão tratados', { maxLength: { value: 2000, message: 'Use até 2000 caracteres.' } },
        { props: { multiline: true, style: { minHeight: 90, textAlignVertical: 'top' } } })}

      <Text style={styles.sectionLabel}>PRIORIDADE</Text>
      <View style={styles.filters}>{PRIORITIES.map(value => <TouchableOpacity key={value} accessibilityRole="button" accessibilityState={{ selected: priority === value }}
        disabled={busy} style={[styles.filter, priority === value && styles.filterActive]} onPress={() => setPriority(value)}>
        <Text style={[styles.filterText, priority === value && { color: '#FFFFFF' }]}>{PRIORITY_LABELS[value]}</Text></TouchableOpacity>)}</View>

      <Text style={styles.sectionLabel}>CONVIDADOS {members ? `(${members.filter(row => row.role === 'COMMITTEE_ADMIN' || invited.has(row.userId)).length} de ${members.length})` : ''}</Text>
      {membersError ? <Text accessibilityRole="alert" style={styles.error}>{membersError}</Text> : null}
      {!members && !membersError && <ActivityIndicator color={colors.primary} accessibilityLabel="Carregando integrantes" />}
      {members?.map(row => {
        const checked = row.role === 'COMMITTEE_ADMIN' || invited.has(row.userId);
        return <TouchableOpacity key={row.userId} accessibilityRole="checkbox" accessibilityState={{ checked, disabled: row.role === 'COMMITTEE_ADMIN' || busy }}
          accessibilityLabel={`${row.userName}${row.role === 'COMMITTEE_ADMIN' ? ', presidente' : ''}`} style={[styles.card, styles.row]} onPress={() => toggle(row)}>
          <Ionicons name={checked ? 'checkbox' : 'square-outline'} size={24} color={row.role === 'COMMITTEE_ADMIN' ? colors.subtext : colors.primary} />
          <View style={styles.flex}><Text style={styles.cardTitle}>{row.userName}</Text></View>
          {row.role === 'COMMITTEE_ADMIN' && <MeetingBadge text="Presidente" />}
        </TouchableOpacity>;
      })}
      {members && members.length <= 1 && <Text style={styles.hint}>Só o presidente é integrante ativo desta comissão por enquanto.</Text>}
    </FormScrollView>
  );
}
