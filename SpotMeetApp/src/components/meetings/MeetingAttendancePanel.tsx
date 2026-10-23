import React from 'react';
import { Text, View } from 'react-native';
import { MeetingAction, MeetingBadge, useMeetingStyles } from './meetingStyles';
import { RESPONSE_LABELS, formatTime, type Meeting, type MeetingParticipant } from '../../utils/meetings';

interface Props {
  meeting: Meeting;
  userId: number | null;
  isPresident: boolean;
  busy: boolean;
  onEntry: (userId: number) => void;
  onExit: (userId: number) => void;
  onEntryAll: () => void;
  onExitAll: () => void;
  onReset: (participant: MeetingParticipant) => void;
}

const registeredBy = (name: string | null, owner: string) => (name && name !== owner ? ` (registrado por ${name})` : '');

/** Entry and exit records: each person registers their own, and the president can register for anyone. */
export default function MeetingAttendancePanel({ meeting, userId, isPresident, busy, onEntry, onExit, onEntryAll, onExitAll, onReset }: Props) {
  const { colors, styles } = useMeetingStyles();
  const participants = meeting.participants ?? [];
  const mine = participants.find(person => person.userId === userId);
  const live = meeting.status === 'IN_PROGRESS';
  const recordable = live || meeting.status === 'ENDED';
  if (!participants.length || (!live && !(isPresident && recordable) && !participants.some(person => person.enteredAt))) return null;

  return (
    <View style={styles.card}>
      <Text style={styles.cardTitle}>Presença</Text>
      {live && mine && <View style={styles.actions}>
        {!mine.enteredAt && <MeetingAction label="Registrar minha entrada" icon="log-in-outline" filled disabled={busy} onPress={() => onEntry(mine.userId)} />}
        {mine.enteredAt && !mine.leftAt && <MeetingAction label="Registrar minha saída" icon="log-out-outline" disabled={busy} onPress={() => onExit(mine.userId)} />}
      </View>}
      {live && isPresident && <View style={styles.actions}>
        <MeetingAction label="Entrada de todos que aceitaram" icon="people-outline" disabled={busy} onPress={onEntryAll} />
        <MeetingAction label="Saída de todos que estão dentro" icon="exit-outline" disabled={busy} onPress={onExitAll} />
      </View>}
      {live && !isPresident && !mine && <Text style={styles.hint}>Você não foi convidado para esta reunião.</Text>}
      {participants.map(person => <View key={person.userId} style={styles.personRow}>
        <Text style={styles.cardTitle}>{person.userName}</Text>
        <View style={styles.badges}>
          <MeetingBadge text={RESPONSE_LABELS[person.response]} color={person.response === 'ACCEPTED' ? colors.success : person.response === 'DECLINED' ? colors.danger : colors.warning} />
        </View>
        {person.responseComment ? <Text style={styles.hint}>Motivo: {person.responseComment}</Text> : null}
        <Text style={styles.subtitle}>
          Entrada: {person.enteredAt ? `${formatTime(person.enteredAt)}${registeredBy(person.enteredByName, person.userName)}` : 'não registrada'}
        </Text>
        <Text style={styles.subtitle}>
          Saída: {person.leftAt ? `${formatTime(person.leftAt)}${registeredBy(person.leftByName, person.userName)}` : 'não registrada'}
        </Text>
        {isPresident && recordable && <View style={styles.actions}>
          {live && !person.enteredAt && person.userId !== userId && <MeetingAction label="Registrar entrada" icon="log-in-outline" disabled={busy} onPress={() => onEntry(person.userId)} />}
          {live && person.enteredAt && !person.leftAt && person.userId !== userId && <MeetingAction label="Registrar saída" icon="log-out-outline" disabled={busy} onPress={() => onExit(person.userId)} />}
          {(person.enteredAt || person.leftAt) && <MeetingAction label="Limpar registros" icon="refresh-outline" danger disabled={busy} onPress={() => onReset(person)} />}
        </View>}
      </View>)}
    </View>
  );
}
