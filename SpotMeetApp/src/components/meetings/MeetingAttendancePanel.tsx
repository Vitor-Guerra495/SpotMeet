import React from 'react';
import { Text, TouchableOpacity, View } from 'react-native';
import { MeetingAction, useMeetingStyles } from './meetingStyles';
import type { Meeting } from '../../utils/meetings';

interface Props {
  meeting: Meeting;
  userId: number | null;
  isPresident: boolean;
  busy: boolean;
  compact?: boolean;
  onEntry: (userId: number) => void;
  onExit: (userId: number) => void;
  onEntryAll: () => void;
  onExitAll: () => void;
  onShowList: () => void;
  onGenerateList: () => void;
}

/**
 * "Presença" card: each person registers their own entry and exit; the president can register for everyone
 * and generates the attendance list. The records of each person are in the guest list of the detail.
 */
export default function MeetingAttendancePanel({ meeting, userId, isPresident, busy, compact, onEntry, onExit, onEntryAll, onExitAll, onShowList, onGenerateList }: Props) {
  const { colors, styles } = useMeetingStyles();
  const participants = meeting.participants ?? [];
  const mine = participants.find(person => person.userId === userId);
  const live = meeting.status === 'IN_PROGRESS';
  const listable = live || meeting.status === 'ENDED';
  const generated = !!meeting.attendanceListGeneratedAt;
  if (!(live && (mine || isPresident)) && !(meeting.status === 'ENDED' && (isPresident || generated))) return null;

  const canEnter = live && !!mine && !mine.enteredAt;
  const canExit = live && !!mine?.enteredAt && !mine.leftAt;
  const openList = () => { if (generated) onShowList(); else if (isPresident && listable) onGenerateList(); };

  return (
    <View style={[styles.card, styles.liveCard, compact && { padding: 0, borderWidth: 0, marginBottom: 0 }]}>
      {!compact && <>
        <Text style={styles.cardTitle}>Presença</Text>
        <Text style={[styles.subtitle, { marginBottom: 8 }]}>
          {live ? 'Registre sua entrada ao chegar e a saída ao sair.' : 'A reunião terminou. Os registros ficam na lista de presença.'}
        </Text>
      </>}
      {live && mine && <>
        <MeetingAction label={mine.enteredAt ? 'Entrada registrada' : 'Registrar entrada'} icon="log-in-outline" filled={canEnter} muted={!canEnter}
          disabled={busy || !canEnter} onPress={() => onEntry(mine.userId)} />
        <MeetingAction label={mine.leftAt ? 'Saída registrada' : 'Registrar saída'} icon="log-out-outline" filled={canExit} muted={!canExit}
          disabled={busy || !canExit} onPress={() => onExit(mine.userId)} />
      </>}
      <MeetingAction label={generated ? 'Lista de presença' : isPresident && listable ? 'Gerar lista de presença' : 'Lista de presença'} icon="clipboard-outline"
        disabled={busy || (!generated && !(isPresident && listable))} onPress={openList} />
      {!generated && !isPresident && <Text style={styles.hint}>A lista aparece aqui quando o presidente gerar.</Text>}
      {isPresident && generated && listable && <TouchableOpacity accessibilityRole="button" disabled={busy} onPress={onGenerateList} style={{ minHeight: 40, justifyContent: 'center' }}>
        <Text style={styles.link}>Gerar a lista de novo</Text></TouchableOpacity>}
      {live && isPresident && !compact && <View style={[styles.actions, { marginTop: 4 }]}>
        <MeetingAction label="Entrada de todos que aceitaram" icon="people-outline" disabled={busy} onPress={onEntryAll} />
        <MeetingAction label="Saída de todos que estão dentro" icon="exit-outline" disabled={busy} onPress={onExitAll} />
      </View>}
      {live && isPresident && !mine && !compact && <Text style={[styles.hint, { color: colors.subtext }]}>Você não está entre os convidados desta reunião.</Text>}
    </View>
  );
}
