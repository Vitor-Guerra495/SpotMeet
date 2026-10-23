import React, { useCallback, useEffect, useState } from 'react';
import { ActivityIndicator, Modal, Text, TouchableOpacity, View } from 'react-native';
import { Ionicons } from '@react-native-vector-icons/ionicons';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useAuth } from '../../context/AuthContext';
import { API_BASE_URL } from '../../config/api';
import { showAlert as alert, confirmAction } from '../../utils/alerts';
import { notifySync as broadcastSync } from '../../utils/syncBus';
import ToastHost from '../ui/ToastHost';
import ConfirmationHost from '../ui/ConfirmationHost';
import FormScrollView from '../forms/FormScrollView';
import { MeetingAction, MeetingBadge, useMeetingStyles } from './meetingStyles';
import { errorText, readCommitteeMembers, type CommitteeMemberRow } from '../../utils/meetings';

interface Props {
  committee: { id: number; name: string } | null;
  onClose: () => void;
  onChanged: () => void;
}

/** Lets an organization leader designate or replace the committee president among the active members. */
export default function PresidentSheet({ committee, onClose, onChanged }: Props) {
  const insets = useSafeAreaInsets();
  const { token } = useAuth();
  const { colors, styles } = useMeetingStyles();
  const [members, setMembers] = useState<CommitteeMemberRow[] | null>(null);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    if (!committee) return;
    try {
      const res = await fetch(`${API_BASE_URL}/committees/${committee.id}/members`, { headers: { Authorization: `Bearer ${token}` } });
      if (!res.ok) throw new Error('members');
      setMembers(readCommitteeMembers(await res.json())); setError('');
    } catch { setError('Não foi possível carregar os integrantes da comissão.'); }
  }, [committee, token]);

  useEffect(() => { setMembers(null); setError(''); void load(); }, [load]);

  const designate = (member: CommitteeMemberRow) => {
    if (!committee || busy) return;
    confirmAction('Definir presidente', `${member.userName} será o presidente de ${committee.name} e poderá agendar reuniões. O presidente atual deixa o cargo.`, async () => {
      setBusy(true); setError('');
      try {
        const res = await fetch(`${API_BASE_URL}/committees/${committee.id}/president/${member.userId}`, { method: 'PUT', headers: { Authorization: `Bearer ${token}` } });
        const text = await res.text();
        if (!res.ok) { setError(errorText(res.status, text, 'Não foi possível definir o presidente. Tente novamente.')); void load(); return; }
        alert('Sucesso', 'Presidente definido.');
        broadcastSync('COMMITTEES_MUTATED');
        await load(); onChanged();
      } catch { setError('Não foi possível conectar ao servidor. Tente novamente.'); }
      finally { setBusy(false); }
    }, 'Definir');
  };

  const active = !!committee;
  return (
    <Modal visible={active} transparent animationType="slide" onRequestClose={() => { if (!busy) onClose(); }}>
      <View style={styles.overlay}><View style={[styles.sheet, { paddingBottom: insets.bottom }]}><View style={styles.sheetHandle} />
        <View style={styles.sheetHeader}>
          <View style={styles.flex}><Text style={styles.cardTitle}>Presidente da comissão</Text><Text style={styles.subtitle}>{committee?.name}</Text></View>
          <TouchableOpacity accessibilityRole="button" accessibilityLabel="Fechar presidente da comissão" style={styles.iconButton} onPress={onClose} disabled={busy}>
            <Ionicons name="close-outline" size={24} color={colors.subtext} /></TouchableOpacity>
        </View>
        <FormScrollView contentContainerStyle={styles.content}>
          <Text style={styles.hint}>O presidente agenda as reuniões da comissão, convida os integrantes e gera a lista de presença. Escolha entre os integrantes ativos.</Text>
          {error ? <Text accessibilityRole="alert" style={styles.error}>{error}</Text> : null}
          {!members && !error && <ActivityIndicator color={colors.primary} accessibilityLabel="Carregando integrantes" />}
          {members?.map(member => <View key={member.userId} style={styles.card}>
            <Text style={styles.cardTitle}>{member.userName}</Text>
            {member.role === 'COMMITTEE_ADMIN' ? <MeetingBadge text="Presidente" color={colors.success} />
              : <MeetingAction label="Definir presidente" icon="ribbon-outline" disabled={busy} onPress={() => designate(member)} />}
          </View>)}
          {members && !members.length && <Text style={styles.hint}>Esta comissão ainda não tem integrantes ativos. Convide ou aprove integrantes antes de escolher o presidente.</Text>}
        </FormScrollView>
      </View></View><ToastHost active={active} /><ConfirmationHost active={active} />
    </Modal>
  );
}
