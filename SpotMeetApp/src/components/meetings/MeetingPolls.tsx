import React, { useEffect, useRef, useState } from 'react';
import { ActivityIndicator, Text, TouchableOpacity, View } from 'react-native';
import { Ionicons } from '@react-native-vector-icons/ionicons';
import FormField from '../forms/FormField';
import FormButton from '../forms/FormButton';
import { MeetingAction, MeetingBadge, useMeetingStyles } from './meetingStyles';
import { MeetingHeader } from './MeetingParts';
import { showAlert as alert, confirmAction } from '../../utils/alerts';
import { apiRequest } from '../../utils/apiRequest';
import { notifySync } from '../../utils/syncBus';
import { formatTime, type Meeting } from '../../utils/meetings';
import {
  CHOICE_LABELS, MAX_DESCRIPTION, MAX_JUSTIFICATION, MAX_QUESTION, percentages, readPolls, resultDetail, resultShort, resultTitle,
  type Poll, type VoteChoice,
} from '../../utils/polls';

/** 'poll-open' opens a new vote; 'poll-12' shows the vote with id 12. */
export type PollPage = 'card' | 'poll-open' | `poll-${number}`;

interface Props {
  meeting: Meeting;
  president: boolean;
  userId: number | null;
  token: string | null;
  page: PollPage;
  onPage: (page: PollPage) => void;
  onChanged: () => void;
}

export function pollTitle(page: PollPage): string {
  return page === 'poll-open' ? 'Abrir votação' : 'Votação';
}

const CHOICES: VoteChoice[] = ['YES', 'NO', 'ABSTAIN'];
const plural = (value: number, one: string, many: string) => `${value} ${value === 1 ? one : many}`;

/**
 * Votes of the meeting: the "Votações" card of the detail and the pages to open, vote and see the result.
 * The president opens and closes; present people vote; the result appears for everyone after the vote closes.
 */
export default function MeetingPolls({ meeting, president, userId, token, page, onPage, onChanged }: Props) {
  const { colors, styles } = useMeetingStyles();
  const [polls, setPolls] = useState<Poll[] | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [question, setQuestion] = useState('');
  const [description, setDescription] = useState('');
  const [questionError, setQuestionError] = useState('');
  const [choice, setChoice] = useState<VoteChoice | null>(null);
  const [justification, setJustification] = useState('');
  const busyRef = useRef(false);
  const visible = meeting.status === 'IN_PROGRESS' || meeting.status === 'ENDED';

  // Follows the refresh of the detail, which gives the president the live count.
  useEffect(() => {
    let current = true;
    if (!visible) return undefined;
    void apiRequest(token, `/meetings/${meeting.id}/polls`).then(result => {
      if (!current || !result.ok) return;
      try { setPolls(readPolls(result.data)); } catch { /* The next refresh tries again. */ }
    });
    return () => { current = false; };
  }, [meeting, token, visible]);

  if (!visible) return null;

  const send = async (path: string, body: unknown, title: string, message: string): Promise<Poll[] | null> => {
    if (busyRef.current) return null;
    busyRef.current = true; setBusy(true); setError('');
    try {
      const result = await apiRequest(token, path, 'POST', body);
      if (!result.ok) { setError(result.error); return null; }
      const next = readPolls(result.data);
      setPolls(next);
      alert(title, message);
      notifySync('MEETINGS_MUTATED');
      onChanged();
      return next;
    } catch { setError('Não foi possível concluir a operação. Tente novamente.'); return null; }
    finally { busyRef.current = false; setBusy(false); }
  };

  const live = meeting.status === 'IN_PROGRESS';
  const presentCount = (meeting.participants ?? []).filter(person => person.enteredAt).length;
  const errorLine = error ? <Text accessibilityRole="alert" style={styles.error}>{error}</Text> : null;
  const choiceColor = (value: VoteChoice) => value === 'YES' ? colors.success : value === 'NO' ? colors.danger : colors.subtext;
  const resultColor = (poll: Poll) => poll.result === 'APPROVED' ? colors.success : poll.result === 'REJECTED' ? colors.danger : colors.warning;

  if (page === 'card') {
    const list = polls ?? [];
    if (!list.length && !(president && live)) return null;
    return <View style={styles.card}>
      <Text style={styles.cardTitle}>Votações</Text>
      {!list.length && <Text style={styles.subtitle}>Nenhuma votação aberta nesta reunião.</Text>}
      {list.map(poll => <View key={poll.id} style={styles.personRow}>
        <TouchableOpacity accessibilityRole="button" accessibilityLabel={`Abrir votação ${poll.question}`} onPress={() => { setError(''); setChoice(null); setJustification(''); onPage(`poll-${poll.id}`); }}>
          <View style={styles.row}>
            <Text style={[styles.optionText, styles.flex]}>{poll.question}</Text>
            <Ionicons name="chevron-forward" size={20} color={colors.subtext} />
          </View>
          <View style={[styles.badges, { alignItems: 'center' }]}>
            <MeetingBadge text={poll.closed ? 'Encerrada' : 'Aberta'} color={poll.closed ? colors.subtext : colors.success} />
            <Text style={styles.subtitle}>{poll.closed ? resultShort(poll) : president ? `${poll.totalVotes} de ${poll.eligible} votaram`
              : poll.myVote ? '' : poll.canVote ? 'Você ainda não votou.' : 'Só quem registrou entrada vota.'}</Text>
          </View>
          {poll.myVote ? <Text style={styles.hint}>Seu voto: {CHOICE_LABELS[poll.myVote.choice]}</Text> : null}
        </TouchableOpacity>
        {poll.canVote && !president && <MeetingAction label="Votar Agora" icon="hand-left-outline" filled onPress={() => { setChoice(null); setJustification(''); onPage(`poll-${poll.id}`); }} />}
      </View>)}
      {president && live && <View style={{ marginTop: 8 }}><MeetingAction label="Abrir Votação" icon="add-circle-outline"
        onPress={() => { setQuestion(''); setDescription(''); setQuestionError(''); setError(''); onPage('poll-open'); }} /></View>}
    </View>;
  }

  if (page === 'poll-open') {
    const openPoll = async () => {
      if (!question.trim()) { setQuestionError('Escreva a pergunta da votação.'); return; }
      const next = await send(`/meetings/${meeting.id}/polls`, { question, description: description.trim() || null }, 'Votação aberta',
        'As pessoas que registraram entrada já podem votar.');
      if (next && next.length) onPage(`poll-${next[next.length - 1].id}`);
    };
    return <View>
      <MeetingHeader meeting={meeting}>
        <View style={styles.badges}><MeetingBadge text={live ? 'Em andamento' : 'Encerrada'} color={live ? colors.success : colors.subtext} icon="radio-button-on" /></View>
      </MeetingHeader>
      {!live && <View style={[styles.notice, styles.noticeWarning, { marginBottom: 14 }]}>
        <Ionicons name="warning" size={22} color={colors.warning} />
        <View style={styles.flex}><Text style={styles.cardTitle}>A reunião terminou.</Text>
          <Text style={styles.infoText}>Só é possível abrir uma votação com a reunião em andamento.</Text></View>
      </View>}
      <FormField label="Pergunta" required placeholder="Ex: Aprovar o orçamento de dezembro?" value={question} maxLength={MAX_QUESTION} showCount editable={!busy && live}
        error={questionError} onChangeText={value => { setQuestion(value); setQuestionError(''); }} />
      <FormField label="Descrição (opcional)" placeholder="Ex: Detalhes que ajudam a decidir." value={description} maxLength={MAX_DESCRIPTION} showCount multiline editable={!busy && live}
        onChangeText={setDescription} style={{ minHeight: 80, textAlignVertical: 'top', paddingTop: 12 }} />
      <View style={[styles.notice, { marginBottom: 14 }]}>
        <Ionicons name="information-circle-outline" size={22} color={colors.primary} />
        <View style={styles.flex}><Text style={styles.infoText}>Votam as pessoas que registraram entrada. O voto é nominal e não pode ser trocado.</Text>
          <Text style={styles.hint}>{plural(presentCount, 'pessoa pode', 'pessoas podem')} votar agora. Quem se abster pode explicar o motivo, se quiser.</Text></View>
      </View>
      {errorLine}
      <FormButton label="Abrir Votação" icon="add-circle-outline" loading={busy} disabled={!live} onPress={openPoll} />
      <FormButton label="Cancelar" secondary disabled={busy} onPress={() => onPage('card')} />
    </View>;
  }

  if (!polls) return <ActivityIndicator color={colors.primary} accessibilityLabel="Carregando a votação" />;
  const poll = polls.find(item => `poll-${item.id}` === page);
  if (!poll) return <Text style={styles.subtitle}>Esta votação não foi encontrada.</Text>;

  const counts = [poll.yes ?? 0, poll.no ?? 0, poll.abstain ?? 0];
  const shares = percentages(counts);
  const bars = (label: string) => <>
    <Text style={styles.sectionLabel}>{label}</Text>
    {CHOICES.map((value, index) => <View key={value} style={{ marginBottom: 10 }}>
      <View style={[styles.row, { justifyContent: 'space-between' }]}>
        <Text style={styles.infoText}>{CHOICE_LABELS[value]}</Text>
        <Text style={styles.subtitle}>{counts[index]} · {shares[index]}%</Text>
      </View>
      <View style={{ height: 8, borderRadius: 4, backgroundColor: colors.inputBg, overflow: 'hidden', marginTop: 4 }}>
        <View style={{ width: `${shares[index]}%`, height: 8, backgroundColor: choiceColor(value) }} />
      </View>
    </View>)}
  </>;
  const voteRows = (label: string) => poll.votes && poll.votes.length ? <>
    <Text style={styles.sectionLabel}>{label} ({poll.votes.length})</Text>
    {poll.votes.map(vote => <View key={vote.userId} style={styles.personRow}>
      <View style={[styles.row, { justifyContent: 'space-between' }]}>
        <Text style={[styles.optionText, styles.flex]} numberOfLines={1}>{vote.userName}{vote.userId === userId ? ' (você)' : ''}</Text>
        <MeetingBadge text={CHOICE_LABELS[vote.choice]} color={choiceColor(vote.choice)} />
      </View>
      {vote.justification ? <Text style={styles.hint}>Justificativa: {vote.justification}</Text> : null}
    </View>)}
  </> : null;
  const missing = (label: string, badge: string) => poll.notVoted && poll.notVoted.length ? <>
    <Text style={[styles.sectionLabel, { marginTop: 14 }]}>{label} ({poll.notVoted.length})</Text>
    {poll.notVoted.map(person => <View key={person.userId} style={[styles.personRow, styles.row, { justifyContent: 'space-between' }]}>
      <Text style={[styles.optionText, styles.flex]} numberOfLines={1}>{person.userName}</Text><MeetingBadge text={badge} color={colors.subtext} />
    </View>)}
  </> : null;
  const heading = <>
    <View style={[styles.badges, { marginTop: 0 }]}><MeetingBadge text={poll.closed ? 'Encerrada' : 'Aberta'} color={poll.closed ? colors.subtext : colors.success} /></View>
    <Text style={[styles.sheetTitle, { marginTop: 10 }]}>{poll.question}</Text>
    {poll.description ? <Text style={[styles.infoText, { marginTop: 4 }]}>{poll.description}</Text> : null}
  </>;

  if (poll.closed) {
    const by = poll.closedAutomatically ? `Aberta por ${poll.openedByName} às ${formatTime(poll.openedAt)}.`
      : poll.totalVotes ? `Encerrada por ${poll.closedByName} às ${formatTime(poll.closedAt)}.` : `Encerrada por ${poll.closedByName} às ${formatTime(poll.closedAt)}. Ninguém votou.`;
    return <View>
      {heading}
      <Text style={[styles.hint, { marginBottom: 4 }]}>{by}{poll.totalVotes ? ` Votaram ${poll.totalVotes} de ${poll.eligible}.` : ''}</Text>
      {poll.closedAutomatically && <Text style={[styles.hint, { marginTop: 0 }]}>Encerrada automaticamente às {formatTime(poll.closedAt)}, no fim da reunião.</Text>}
      <View style={[styles.card, { marginTop: 10, borderColor: resultColor(poll) }]}>
        <Text style={[styles.cardTitle, { fontSize: 18 }]}>{resultTitle(poll)}</Text>
        <Text style={styles.infoText}>{resultDetail(poll)}</Text>
        {poll.myVote ? <Text style={styles.hint}>Seu voto: {CHOICE_LABELS[poll.myVote.choice]}</Text> : null}
      </View>
      {poll.result !== 'NO_VOTES' && bars('RESULTADO')}
      {voteRows('QUEM VOTOU EM QUÊ')}
      {missing('NÃO VOTARAM', 'Não votou')}
    </View>;
  }

  const vote = () => {
    if (!choice) return;
    const label = CHOICE_LABELS[choice];
    const note = choice === 'ABSTAIN' && justification.trim() ? `\n\nSua justificativa: ${justification.trim()}` : '';
    confirmAction('Confirmar voto', `Confirmar voto: ${label}? Não será possível trocar.${note}`, async () => {
      const done = await send(`/meetings/${meeting.id}/polls/${poll.id}/vote`, { choice, justification: choice === 'ABSTAIN' ? justification.trim() || null : null },
        'Voto registrado', choice === 'ABSTAIN' ? 'Você se absteve.' : `Você votou ${label}.`);
      if (done) { setChoice(null); setJustification(''); }
    });
  };
  const closePoll = () => {
    const left = Math.max(0, poll.eligible - poll.totalVotes);
    const situation = left ? `${plural(left, 'pessoa ainda não votou', 'pessoas ainda não votaram')}. ` : poll.totalVotes ? 'Todos já votaram. ' : 'Ninguém votou. ';
    confirmAction('Encerrar a votação?', `${situation}Depois de encerrada, ninguém mais vota e o resultado aparece para todos.`,
      () => void send(`/meetings/${meeting.id}/polls/${poll.id}/close`, undefined, 'Votação encerrada', 'O resultado já aparece para todos.'));
  };

  return <View>
    {heading}
    <Text style={[styles.hint, { marginBottom: 10 }]}>Aberta por {president ? 'você' : poll.openedByName} às {formatTime(poll.openedAt)}.{president ? ` ${poll.totalVotes} de ${poll.eligible} votaram.` : ''}</Text>
    {poll.canVote && <View style={{ marginBottom: 12 }}>
      <Text style={styles.sectionLabel}>SEU VOTO</Text>
      {CHOICES.map(value => <TouchableOpacity key={value} accessibilityRole="radio" accessibilityState={{ checked: choice === value, disabled: busy }} disabled={busy}
        style={[styles.option, { borderWidth: 1, borderColor: choice === value ? colors.primary : colors.border }, choice === value && styles.optionActive]}
        onPress={() => setChoice(value)}>
        <Text style={styles.optionText}>{CHOICE_LABELS[value]}</Text>
        <Ionicons name={choice === value ? 'radio-button-on' : 'radio-button-off'} size={22} color={choice === value ? colors.primary : colors.subtext} />
      </TouchableOpacity>)}
      {choice === 'ABSTAIN' && <View style={{ marginTop: 8 }}>
        <FormField label="Justificativa (opcional)" placeholder="Ex: Preciso de mais informações antes de decidir." value={justification} maxLength={MAX_JUSTIFICATION} showCount
          multiline editable={!busy} onChangeText={setJustification} style={{ minHeight: 80, textAlignVertical: 'top', paddingTop: 12 }} />
        <Text style={[styles.hint, { marginTop: -6 }]}>Até 280 caracteres. Aparece com o seu nome, junto com o seu voto.</Text>
      </View>}
      <Text style={styles.hint}>Votam as {poll.eligible} pessoas que registraram entrada. Seu voto aparece com o seu nome e não pode ser trocado.</Text>
      {errorLine}
      <FormButton label="Confirmar Voto" icon="checkmark-outline" loading={busy} disabled={!choice} onPress={vote} />
    </View>}
    {poll.myVote && <View style={[styles.card, { borderColor: colors.primary }]}>
      <Text style={styles.cardTitle}>Seu voto: {CHOICE_LABELS[poll.myVote.choice]}</Text>
      <Text style={styles.subtitle}>Registrado às {formatTime(poll.myVote.votedAt)}, com o seu nome.</Text>
      {poll.myVote.justification ? <Text style={styles.hint}>Justificativa: {poll.myVote.justification}</Text> : null}
      {!president && <Text style={[styles.infoText, { marginTop: 8 }]}>Aguardando o encerramento.</Text>}
    </View>}
    {!poll.canVote && !poll.myVote && <Text style={[styles.hint, { marginBottom: 10 }]}>Só quem registrou entrada na reunião vota.</Text>}
    {president && <>
      {bars('CONTAGEM AO VIVO')}
      {voteRows('QUEM VOTOU')}
      {missing('FALTAM VOTAR', 'Aguardando')}
    </>}
    <Text style={[styles.hint, { marginTop: 12 }]}>{president ? 'O resultado aparece para todos quando você encerrar a votação ou quando a reunião terminar.'
      : 'O resultado aparece para todos quando o presidente encerrar a votação ou quando a reunião terminar.'}</Text>
    {!poll.canVote && errorLine}
    {president && <FormButton label="Encerrar Votação" icon="lock-closed-outline" loading={busy} onPress={closePoll} />}
  </View>;
}
