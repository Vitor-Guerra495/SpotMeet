import React, { useState } from 'react';
import { Text, View } from 'react-native';
import FormField from '../forms/FormField';
import FormButton from '../forms/FormButton';
import { MeetingAction, MeetingBadge, useMeetingStyles } from './meetingStyles';
import { RESPONSE_LABELS, formatDay, parseLocal, type Meeting } from '../../utils/meetings';

interface Props {
  meeting: Meeting;
  busy: boolean;
  onAccept: () => void;
  onDecline: (comment: string) => void;
}

/** Answer to the invitation: accept, or decline with an optional comment for the president. In a series, each date has its own answer. */
export default function MeetingInvitationActions({ meeting, busy, onAccept, onDecline }: Props) {
  const { colors, styles } = useMeetingStyles();
  const [declining, setDeclining] = useState(false);
  const [comment, setComment] = useState('');
  const answer = meeting.myResponse;
  if (!answer || (meeting.status !== 'SCHEDULED' && meeting.status !== 'IN_PROGRESS')) return null;

  const color = answer === 'ACCEPTED' ? colors.success : answer === 'DECLINED' ? colors.danger : colors.warning;
  const scope = meeting.seriesId ? `Sua resposta vale só para esta data, ${formatDay(parseLocal(meeting.startsAt))}.`
    : answer === 'PENDING' ? 'O presidente aguarda sua resposta.' : 'Você pode mudar a resposta até o fim da reunião.';
  return (
    <View style={[styles.banner, { flexDirection: 'column', alignItems: 'stretch', gap: 4, marginBottom: 16 }]}>
      <View style={[styles.row, { justifyContent: 'space-between' }]}>
        <Text style={styles.cardTitle}>Seu convite</Text>
        {answer !== 'PENDING' && <MeetingBadge text={RESPONSE_LABELS[answer]} color={color} icon={answer === 'ACCEPTED' ? 'checkmark-circle' : 'close-circle'} />}
      </View>
      <Text style={styles.infoText}>{scope}</Text>
      {declining ? <View style={{ marginTop: 12 }}>
        <FormField label="Motivo (opcional)" placeholder="Diga ao presidente por que não vai" value={comment} maxLength={200} showCount multiline editable={!busy}
          onChangeText={setComment} style={{ minHeight: 70, textAlignVertical: 'top' }} />
        <FormButton label="Confirmar recusa" icon="close-outline" loading={busy} onPress={() => { onDecline(comment); setDeclining(false); setComment(''); }} />
        <FormButton label="Voltar" secondary disabled={busy} onPress={() => setDeclining(false)} />
      </View> : <View style={[styles.actions, { flexWrap: 'nowrap' }]}>
        {answer !== 'DECLINED' && <MeetingAction label="Recusar" icon="close-outline" danger disabled={busy} onPress={() => setDeclining(true)} />}
        {answer !== 'ACCEPTED' && <MeetingAction label="Aceitar" icon="checkmark-outline" filled disabled={busy} onPress={onAccept} />}
      </View>}
    </View>
  );
}
