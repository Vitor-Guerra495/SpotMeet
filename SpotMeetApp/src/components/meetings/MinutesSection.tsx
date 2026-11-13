import React, { useEffect, useRef, useState } from 'react';
import { ActivityIndicator, Text, View } from 'react-native';
import { Ionicons } from '@react-native-vector-icons/ionicons';
import FormField from '../forms/FormField';
import FormButton from '../forms/FormButton';
import { MeetingAction, MeetingBadge, useMeetingStyles } from './meetingStyles';
import { MeetingHeader, PeopleGrid } from './MeetingParts';
import { showAlert as alert, confirmAction } from '../../utils/alerts';
import { apiRequest } from '../../utils/apiRequest';
import { notifySync } from '../../utils/syncBus';
import { meetingDates, meetingTimes, type Meeting } from '../../utils/meetings';
import {
  MAX_NOTES, MAX_REASON, MAX_TOPICS, MINUTES_LABELS, countText, formatMoment, readMinutes, rejectionText,
  type Minutes, type MinutesAnswer, type MinutesStatus,
} from '../../utils/minutes';

export type MinutesPage = 'card' | 'minutes-write' | 'minutes-read' | 'minutes-reject' | 'minutes-approval';

interface Props {
  meeting: Meeting;
  president: boolean;
  userId: number | null;
  token: string | null;
  page: MinutesPage;
  onPage: (page: MinutesPage) => void;
  onChanged: () => void;
}

/** Sheet title of each minutes page. */
export function minutesTitle(page: MinutesPage, status: MinutesStatus | null): string {
  if (page === 'minutes-write') return status === 'REJECTED' ? 'Corrigir ata' : 'Escrever ata';
  if (page === 'minutes-reject') return 'Reprovar ata';
  if (page === 'minutes-approval') return 'Aprovação da ata';
  return 'Ata da reunião';
}

/**
 * Minutes of the meeting: the "Ata" card of the detail and the pages to write, read, reject and follow the approval.
 * Only the president writes; present people approve or reject; whoever missed the meeting only reads.
 */
export default function MinutesSection({ meeting, president, userId, token, page, onPage, onChanged }: Props) {
  const { colors, styles } = useMeetingStyles();
  const [minutes, setMinutes] = useState<Minutes | null>(null);
  const [loadFailed, setLoadFailed] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [topics, setTopics] = useState('');
  const [decisions, setDecisions] = useState('');
  const [notes, setNotes] = useState('');
  const [reason, setReason] = useState('');
  const [fieldErrors, setFieldErrors] = useState<{ topics?: string; decisions?: string; reason?: string }>({});
  const filled = useRef(false);
  const busyRef = useRef(false);

  // The detail refreshes every few seconds; the minutes follow it so the president sees new answers.
  useEffect(() => {
    let current = true;
    if (meeting.status === 'CANCELED') return undefined;
    void apiRequest(token, `/meetings/${meeting.id}/minutes`).then(result => {
      if (!current) return;
      if (!result.ok) { setLoadFailed(true); return; }
      try { setMinutes(readMinutes(result.data)); setLoadFailed(false); } catch { setLoadFailed(true); }
    });
    return () => { current = false; };
  }, [meeting, token]);

  // The form starts with the saved text once; later refreshes do not overwrite what is being typed.
  useEffect(() => {
    if (page !== 'minutes-write' || !minutes || filled.current) return;
    filled.current = true;
    setTopics(minutes.topics ?? ''); setDecisions(minutes.decisions ?? ''); setNotes(minutes.notes ?? '');
  }, [page, minutes]);

  if (meeting.status === 'CANCELED') return null;

  const send = async (path: string, method: 'POST' | 'PUT', body: unknown, title: string, message: string): Promise<Minutes | null> => {
    if (busyRef.current) return null;
    busyRef.current = true; setBusy(true); setError('');
    try {
      const result = await apiRequest(token, path, method, body);
      if (!result.ok) { setError(result.error); return null; }
      const next = readMinutes(result.data);
      setMinutes(next);
      alert(title, message);
      notifySync('MEETINGS_MUTATED');
      onChanged();
      return next;
    } catch { setError('Não foi possível concluir a operação. Tente novamente.'); return null; }
    finally { busyRef.current = false; setBusy(false); }
  };

  const status = minutes?.status ?? null;
  const ended = meeting.status === 'ENDED';
  const others = (minutes?.present ?? []).filter(person => person.userId !== userId);
  const rejectedAnswers = (minutes?.answers ?? []).filter(answer => answer.approved === false);
  const statusColor = status === 'APPROVED' ? colors.success : status === 'REJECTED' ? colors.danger : status === 'IN_APPROVAL' ? colors.warning : colors.subtext;
  const badge = <MeetingBadge text={status ? MINUTES_LABELS[status] : 'Sem ata'} color={statusColor} />;
  const open = (next: MinutesPage) => { setError(''); setFieldErrors({}); filled.current = false; onPage(next); };

  const sectionText = (label: string, value: string | null, numbered = false) => {
    if (!value) return null;
    const lines = value.split('\n').map(line => line.trim()).filter(Boolean);
    return <View style={{ marginBottom: 14 }}>
      <Text style={styles.sectionLabel}>{label}</Text>
      {lines.map((line, index) => <Text key={index} style={[styles.infoText, { marginBottom: 4 }]}>{numbered && lines.length > 1 ? `${index + 1}. ${line}` : line}</Text>)}
    </View>;
  };
  const people = (list: Minutes) => <>
    <Text style={styles.sectionLabel}>PRESENTES ({list.present.length})</Text>
    <PeopleGrid people={list.present} />
    <Text style={styles.sectionLabel}>AUSENTES ({list.absent.length})</Text>
    <PeopleGrid people={list.absent} />
  </>;
  const answerBadge = (answer: MinutesAnswer) => answer.approved === null
    ? <MeetingBadge text="Sem resposta" color={colors.subtext} />
    : <MeetingBadge text={answer.approved ? 'Aprovou' : 'Reprovou'} color={answer.approved ? colors.success : colors.danger}
      icon={answer.approved ? 'checkmark-circle' : 'close-circle'} />;
  const answerRow = (answer: MinutesAnswer) => <View key={answer.userId} style={styles.personRow}>
    <View style={[styles.row, { justifyContent: 'space-between' }]}>
      <Text style={[styles.optionText, styles.flex]} numberOfLines={1}>{answer.userName}</Text>{answerBadge(answer)}
    </View>
    {answer.reason ? <Text style={styles.hint}>Motivo: {answer.reason}</Text> : null}
  </View>;
  const errorLine = error ? <Text accessibilityRole="alert" style={styles.error}>{error}</Text> : null;

  if (page === 'card') {
    let text: string;
    let action: { label: string; icon: React.ComponentProps<typeof Ionicons>['name']; page: MinutesPage } | null = null;
    let extra: string | null = null;
    if (!ended) {
      text = president ? 'Você poderá escrever a ata depois que a reunião terminar.' : 'A ata fica disponível depois que a reunião terminar.';
    } else if (president) {
      if (!status) { text = 'Registre o que foi tratado e decidido. Só você, presidente, escreve a ata.'; action = { label: 'Escrever Ata', icon: 'create-outline', page: 'minutes-write' }; }
      else if (status === 'DRAFT') { text = `Rascunho salvo em ${formatMoment(minutes!.updatedAt)}. Só você vê o rascunho até enviar.`; action = { label: 'Editar Ata', icon: 'create-outline', page: 'minutes-write' }; }
      else if (status === 'IN_APPROVAL') { text = countText(minutes!); action = { label: 'Acompanhar Aprovação', icon: 'people-outline', page: 'minutes-approval' }; }
      else if (status === 'APPROVED') {
        text = `Aprovada em ${formatMoment(minutes!.closedAt)}, pela maioria de quem respondeu.`; extra = 'Uma ata aprovada não pode mais ser editada.';
        action = { label: 'Ver Ata', icon: 'document-text-outline', page: 'minutes-read' };
      } else {
        const no = minutes!.totalRejected;
        text = no > minutes!.totalApproved ? `${no} ${no === 1 ? 'reprovou' : 'reprovaram'}. Corrija a ata e envie de novo para recomeçar a contagem.`
          : 'Não houve maioria de aprovações. Corrija a ata e envie de novo para recomeçar a contagem.';
        action = { label: 'Corrigir e Reenviar', icon: 'refresh-outline', page: 'minutes-write' };
      }
    } else if (!status) {
      text = 'O presidente ainda não escreveu a ata.';
    } else if (status === 'DRAFT') {
      text = 'O presidente ainda está escrevendo a ata.';
    } else if (status === 'IN_APPROVAL') {
      if (minutes!.canAnswer) { text = 'Você registrou entrada, então pode responder. A ata vale pela maioria de quem responder.'; action = { label: 'Aprovar ou Reprovar', icon: 'checkmark-done-outline', page: 'minutes-read' }; }
      else if (minutes!.myAnswer) { text = `Você ${minutes!.myAnswer.approved ? 'aprovou' : 'reprovou'} esta ata. Falta o presidente encerrar a aprovação.`; action = { label: 'Ver Ata', icon: 'document-text-outline', page: 'minutes-read' }; }
      else { text = 'Você não registrou entrada nesta reunião, então só pode ler a ata.'; action = { label: 'Ver Ata', icon: 'document-text-outline', page: 'minutes-read' }; }
    } else if (status === 'APPROVED') {
      text = 'A ata foi aprovada e não pode mais ser editada.'; action = { label: 'Ver Ata', icon: 'document-text-outline', page: 'minutes-read' };
    } else {
      text = 'A ata foi reprovada. O presidente vai corrigir e enviar de novo.'; action = { label: 'Ver Ata', icon: 'document-text-outline', page: 'minutes-read' };
    }
    return <View style={styles.card}>
      <View style={[styles.row, { justifyContent: 'space-between' }]}><Text style={styles.cardTitle}>Ata</Text>{badge}</View>
      {loadFailed && !minutes && ended ? <Text style={styles.error}>Não foi possível carregar a ata.</Text> : <Text style={[styles.subtitle, { marginTop: 4 }]}>{text}</Text>}
      {extra ? <Text style={styles.hint}>{extra}</Text> : null}
      {!ended && president && <MeetingAction label="Escrever Ata" icon="create-outline" muted disabled onPress={() => undefined} />}
      {action && minutes && <View style={{ marginTop: 8 }}><MeetingAction label={action.label} icon={action.icon} filled={action.page === 'minutes-write' || minutes.canAnswer}
        onPress={() => open(action!.page)} /></View>}
    </View>;
  }

  if (!minutes) return <ActivityIndicator color={colors.primary} accessibilityLabel="Carregando a ata" />;

  if (page === 'minutes-write') {
    const rejected = status === 'REJECTED';
    const validate = () => {
      const errors: typeof fieldErrors = {};
      if (!topics.trim()) errors.topics = 'Registre os assuntos tratados.';
      if (!decisions.trim()) errors.decisions = 'Registre as decisões tomadas.';
      setFieldErrors(errors);
      return !errors.topics && !errors.decisions;
    };
    const body = { topics, decisions, notes };
    const submit = async () => {
      if (!validate()) return;
      const done = await send(`/meetings/${meeting.id}/minutes/submit`, 'POST', body, 'Ata enviada para aprovação',
        'As pessoas presentes foram avisadas por e-mail.');
      if (done) onPage('card');
    };
    const saveDraft = () => {
      setFieldErrors({});
      if (!topics.trim() && !decisions.trim() && !notes.trim()) {
        setError('Escreva algo na ata antes de salvar o rascunho.');
        return;
      }
      void send(`/meetings/${meeting.id}/minutes`, 'PUT', body, 'Rascunho salvo', 'Só você vê o rascunho até enviar.');
    };
    return <View>
      {!others.length && <View style={[styles.notice, styles.noticeWarning, { marginBottom: 14 }]}>
        <Ionicons name="warning" size={22} color={colors.warning} />
        <View style={styles.flex}><Text style={styles.cardTitle}>Não dá para enviar: ninguém além de você registrou entrada nesta reunião.</Text>
          <Text style={styles.infoText}>Corrija a presença na reunião ou salve a ata como rascunho.</Text></View>
      </View>}
      {rejected && <View style={[styles.notice, { marginBottom: 14, flexDirection: 'column', gap: 4 }]}>
        <Text style={styles.cardTitle}>{rejectedAnswers.length ? 'A ata foi reprovada. Motivos:' : 'A ata foi reprovada.'}</Text>
        {rejectedAnswers.map(answer => <Text key={answer.userId} style={styles.infoText}>{answer.userName}: {answer.reason}</Text>)}
        <Text style={styles.hint}>Ao reenviar, a contagem de aprovações recomeça.</Text>
      </View>}
      <MeetingHeader meeting={meeting} />
      <FormField label="Assuntos tratados" required placeholder="Ex: Fechamento das contas de outubro." value={topics} maxLength={MAX_TOPICS} showCount multiline editable={!busy}
        error={fieldErrors.topics} onChangeText={value => { setTopics(value); setFieldErrors(old => ({ ...old, topics: undefined })); }}
        style={{ minHeight: 110, textAlignVertical: 'top', paddingTop: 12 }} />
      <FormField label="Decisões tomadas" required placeholder="Ex: Aprovar o orçamento de dezembro." value={decisions} maxLength={MAX_TOPICS} showCount multiline editable={!busy}
        error={fieldErrors.decisions} onChangeText={value => { setDecisions(value); setFieldErrors(old => ({ ...old, decisions: undefined })); }}
        style={{ minHeight: 110, textAlignVertical: 'top', paddingTop: 12 }} />
      <FormField label="Observações (opcional)" placeholder="Ex: Justificativas de ausência." value={notes} maxLength={MAX_NOTES} showCount multiline editable={!busy}
        onChangeText={setNotes} style={{ minHeight: 80, textAlignVertical: 'top', paddingTop: 12 }} />
      <Text style={styles.sectionLabel}>PRESENTES E AUSENTES</Text>
      <Text style={[styles.hint, { marginTop: -6, marginBottom: 8 }]}>Vem da lista de presença. Não dá para editar.</Text>
      <Text style={[styles.subtitle, { fontWeight: '700' }]}>Presentes ({minutes.present.length})</Text>
      <PeopleGrid people={minutes.present} />
      <Text style={[styles.subtitle, { fontWeight: '700' }]}>Ausentes ({minutes.absent.length})</Text>
      <PeopleGrid people={minutes.absent} />
      {errorLine}
      {rejected ? <>
        <Text style={styles.hint}>A ata continua Reprovada até você reenviar.</Text>
        <FormButton label="Reenviar para Aprovação" icon="send-outline" loading={busy} disabled={!others.length} onPress={submit} />
      </> : <>
        <FormButton label="Enviar para Aprovação" icon="send-outline" loading={busy} disabled={!others.length} onPress={submit} />
        <FormButton label="Salvar Rascunho" secondary disabled={busy} onPress={saveDraft} />
      </>}
    </View>;
  }

  if (page === 'minutes-reject') {
    const reject = async () => {
      if (!reason.trim()) { setFieldErrors({ reason: 'Informe o motivo da reprovação.' }); return; }
      const done = await send(`/meetings/${meeting.id}/minutes/answer`, 'POST', { approved: false, reason }, 'Resposta enviada', 'Você reprovou a ata.');
      if (done) { setReason(''); onPage('minutes-read'); }
    };
    return <View>
      <MeetingHeader meeting={meeting} />
      <FormField label="Motivo" required placeholder="Ex: A decisão sobre o orçamento não está na ata." value={reason} maxLength={MAX_REASON} showCount multiline editable={!busy}
        error={fieldErrors.reason} onChangeText={value => { setReason(value); setFieldErrors({}); }} style={{ minHeight: 100, textAlignVertical: 'top', paddingTop: 12 }} />
      <Text style={styles.hint}>O presidente recebe o motivo com o seu nome e corrige a ata.</Text>
      {errorLine}
      <MeetingAction label="Reprovar Ata" icon="close-outline" danger disabled={busy} onPress={() => void reject()} />
      <FormButton label="Voltar" secondary disabled={busy} onPress={() => onPage('minutes-read')} />
    </View>;
  }

  if (page === 'minutes-approval') {
    const closed = status === 'APPROVED' || status === 'REJECTED';
    const close = () => confirmAction('Encerrar a aprovação?',
      minutes.totalPending ? `${minutes.totalPending} ${minutes.totalPending === 1 ? 'pessoa ainda não respondeu' : 'pessoas ainda não responderam'}. O resultado vale pela maioria de quem respondeu.`
        : 'Todos já responderam. O resultado vale pela maioria de quem respondeu.',
      () => void send(`/meetings/${meeting.id}/minutes/close`, 'POST', undefined, 'Aprovação encerrada', 'As pessoas presentes receberam o resultado por e-mail.'));
    return <View>
      <View style={[styles.row, { justifyContent: 'space-between' }]}>
        <View style={styles.flex}><Text style={styles.cardTitle}>{meeting.title}</Text>
          <Text style={styles.subtitle}>{meeting.committeeName} · {meetingDates(meeting)}</Text></View>
        {badge}
      </View>
      <MeetingAction label="Ver Ata" icon="document-text-outline" onPress={() => onPage('minutes-read')} />
      <View style={[styles.card, { marginTop: 4 }]}>
        <Text style={styles.cardTitle}>{status === 'APPROVED' ? 'Ata aprovada' : status === 'REJECTED' ? 'Ata reprovada' : countText(minutes)}</Text>
        {closed ? <Text style={styles.infoText}>{countText(minutes)} {status === 'APPROVED' ? 'A maioria de quem respondeu aprovou.' : rejectionText(minutes)}</Text>
          : <Text style={styles.subtitle}>A ata é aprovada se a maioria de quem respondeu aprovar. Você não responde à sua própria ata.</Text>}
        {status === 'APPROVED' && <Text style={styles.hint}>Esta ata não pode mais ser editada.</Text>}
      </View>
      <Text style={styles.sectionLabel}>RESPOSTAS ({minutes.answers?.length ?? 0})</Text>
      {(minutes.answers ?? []).map(answerRow)}
      {errorLine}
      {status === 'IN_APPROVAL' && <>
        {minutes.totalPending > 0 && <Text style={[styles.hint, { marginTop: 10 }]}>{minutes.totalPending} {minutes.totalPending === 1 ? 'pessoa ainda não respondeu.' : 'pessoas ainda não responderam.'}</Text>}
        <FormButton label="Encerrar Aprovação" icon="lock-closed-outline" loading={busy} onPress={close} />
      </>}
      {status === 'REJECTED' && <>
        <Text style={[styles.hint, { marginTop: 10 }]}>Ao reenviar, a contagem de aprovações recomeça.</Text>
        <FormButton label="Corrigir e Reenviar" icon="refresh-outline" onPress={() => open('minutes-write')} />
      </>}
    </View>;
  }

  // Reading page: everyone who can see the meeting reads it; present people answer here.
  const approve = () => confirmAction('Aprovar a ata?', 'Depois de enviada, a resposta não pode ser trocada.',
    () => void send(`/meetings/${meeting.id}/minutes/answer`, 'POST', { approved: true }, 'Resposta enviada', 'Você aprovou a ata.'), 'Aprovar');
  if (!status || (status === 'DRAFT' && !president)) {
    return <Text style={styles.subtitle}>{president ? 'A ata ainda não foi escrita.' : 'O presidente ainda não enviou a ata.'}</Text>;
  }
  return <View>
    <View style={[styles.badges, { marginTop: 0 }]}>{badge}</View>
    <Text style={[styles.sheetTitle, { marginTop: 10 }]}>{meeting.title}</Text>
    <Text style={styles.subtitle}>{meeting.committeeName}{minutes.writtenByName ? ` · escrita por ${minutes.writtenByName}` : ''}</Text>
    <Text style={styles.subtitle}>{meetingDates(meeting)} · {meetingTimes(meeting)}</Text>
    {minutes.submittedAt ? <Text style={[styles.hint, { marginBottom: 10 }]}>Enviada em {formatMoment(minutes.submittedAt)}</Text> : null}
    {minutes.myAnswer && <View style={[styles.notice, { flexDirection: 'column', gap: 4, marginBottom: 14 }]}>
      <Text style={styles.cardTitle}>{minutes.myAnswer.approved ? 'Você aprovou esta ata' : 'Você reprovou esta ata'}</Text>
      <Text style={styles.infoText}>Respondida em {formatMoment(minutes.myAnswer.respondedAt)}. {minutes.myAnswer.approved
        ? 'O presidente encerra a aprovação e você recebe o resultado por e-mail.' : 'O presidente vê o motivo, corrige a ata e envia de novo.'}</Text>
      {minutes.myAnswer.reason ? <><Text style={styles.fieldLabel}>SEU MOTIVO</Text><Text style={styles.infoText}>{minutes.myAnswer.reason}</Text></> : null}
    </View>}
    {status === 'APPROVED' && <Text style={[styles.hint, { marginBottom: 10 }]}>Aprovada em {formatMoment(minutes.closedAt)}. Uma ata aprovada não pode mais ser editada.</Text>}
    {sectionText('ASSUNTOS TRATADOS', minutes.topics)}
    {sectionText('DECISÕES TOMADAS', minutes.decisions, true)}
    {sectionText('OBSERVAÇÕES', minutes.notes)}
    {people(minutes)}
    {errorLine}
    {minutes.canAnswer ? <>
      <Text style={styles.hint}>Você registrou entrada, então pode responder. A ata vale pela maioria de quem responder.</Text>
      <View style={[styles.actions, { flexWrap: 'nowrap' }]}>
        <MeetingAction label="Reprovar" icon="close-outline" danger disabled={busy} onPress={() => { setReason(''); setFieldErrors({}); onPage('minutes-reject'); }} />
        <MeetingAction label="Aprovar" icon="checkmark-outline" filled disabled={busy} onPress={approve} />
      </View>
    </> : !president && !minutes.viewerPresent && status !== 'DRAFT' ? <Text style={styles.hint}>Você não registrou entrada nesta reunião, então só pode ler a ata.</Text> : null}
    {president && status === 'IN_APPROVAL' && <MeetingAction label="Acompanhar Aprovação" icon="people-outline" onPress={() => onPage('minutes-approval')} />}
    {president && status === 'DRAFT' && <MeetingAction label="Editar Ata" icon="create-outline" onPress={() => open('minutes-write')} />}
  </View>;
}
