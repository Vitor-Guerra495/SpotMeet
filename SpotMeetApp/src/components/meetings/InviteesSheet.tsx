import React, { useEffect, useState } from 'react';
import { Modal, Text, TouchableOpacity, View } from 'react-native';
import { Ionicons } from '@react-native-vector-icons/ionicons';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import FormButton from '../forms/FormButton';
import FormField from '../forms/FormField';
import FormScrollView from '../forms/FormScrollView';
import UserAvatar from '../profile/UserAvatar';
import { MeetingBadge, useMeetingStyles } from './meetingStyles';
import type { CommitteeMemberRow } from '../../utils/meetings';

interface Props {
  visible: boolean;
  committeeName: string;
  members: CommitteeMemberRow[];
  selected: Set<number>;
  onDone: (selected: Set<number>) => void;
  onClose: () => void;
}

// Search ignores accents and case: "joao" finds "João".
const ACCENTS = new RegExp('[' + String.fromCharCode(0x300) + '-' + String.fromCharCode(0x36f) + ']', 'g');
const plain = (text: string) => text.normalize('NFD').replace(ACCENTS, '').toLowerCase();

/** Guests of the meeting among the active members of the committee; the president is always invited. */
export default function InviteesSheet({ visible, committeeName, members, selected, onDone, onClose }: Props) {
  const insets = useSafeAreaInsets();
  const { colors, styles } = useMeetingStyles();
  const [draft, setDraft] = useState<Set<number>>(new Set());
  const [search, setSearch] = useState('');

  useEffect(() => { if (visible) { setDraft(new Set(selected)); setSearch(''); } }, [visible]);

  const isPresident = (row: CommitteeMemberRow) => row.role === 'COMMITTEE_ADMIN';
  const checked = (row: CommitteeMemberRow) => isPresident(row) || draft.has(row.userId);
  const count = members.filter(checked).length;
  const allChecked = count === members.length;
  const visibleRows = members.filter(row => plain(row.userName).includes(plain(search.trim())));
  const toggle = (row: CommitteeMemberRow) => {
    if (isPresident(row)) return;
    setDraft(current => { const next = new Set(current); if (next.has(row.userId)) next.delete(row.userId); else next.add(row.userId); return next; });
  };

  const footer = <View style={styles.footer}>
    <FormButton label={`Concluir (${count})`} icon="checkmark-outline" onPress={() => onDone(draft)} />
    <FormButton label="Cancelar" secondary onPress={onClose} />
  </View>;

  return <Modal visible={visible} transparent animationType="slide" onRequestClose={onClose}>
    <View style={styles.overlay}><View style={[styles.sheet, { paddingBottom: insets.bottom }]}><View style={styles.sheetHandle} />
      <View style={styles.sheetHeader}>
        <Text style={[styles.sheetTitle, styles.flex]}>Convidados</Text>
        <TouchableOpacity accessibilityRole="button" accessibilityLabel="Fechar convidados" style={styles.iconButton} onPress={onClose}>
          <Ionicons name="close-outline" size={26} color={colors.subtext} /></TouchableOpacity>
      </View>
      <FormScrollView contentContainerStyle={styles.content} footer={footer}>
        <Text style={[styles.subtitle, { marginBottom: 12 }]}>Integrantes da comissão {committeeName}.</Text>
        <FormField label="Buscar integrante" icon="search-outline" placeholder="Nome do integrante" value={search} onChangeText={setSearch} autoCorrect={false} />
        <View style={[styles.row, { justifyContent: 'space-between', marginBottom: 4 }]}>
          <Text style={styles.sectionLabel}>CONVIDADOS ({count} DE {members.length})</Text>
          <TouchableOpacity accessibilityRole="button" style={{ minHeight: 44, justifyContent: 'center' }}
            onPress={() => setDraft(allChecked ? new Set() : new Set(members.map(row => row.userId)))}>
            <Text style={styles.link}>{allChecked ? 'Limpar seleção' : 'Selecionar todos'}</Text>
          </TouchableOpacity>
        </View>
        {visibleRows.map(row => <TouchableOpacity key={row.userId} accessibilityRole="checkbox"
          accessibilityLabel={`${row.userName}${isPresident(row) ? ', presidente, sempre convidado' : ''}`}
          accessibilityState={{ checked: checked(row), disabled: isPresident(row) }} onPress={() => toggle(row)} style={[styles.row, styles.personRow]}>
          <UserAvatar userId={row.userId} name={row.userName} style={styles.avatar} textStyle={styles.avatarText} />
          <Text style={[styles.optionText, styles.flex, checked(row) && { fontWeight: '700' }]} numberOfLines={1}>{row.userName}</Text>
          {isPresident(row) && <MeetingBadge text="Presidente" />}
          <Ionicons name={checked(row) ? 'checkbox' : 'square-outline'} size={26} color={isPresident(row) ? colors.subtext : colors.primary} />
        </TouchableOpacity>)}
        {!visibleRows.length && <Text style={styles.hint}>Nenhum integrante com esse nome.</Text>}
      </FormScrollView>
    </View></View>
  </Modal>;
}
