import { Controller, useForm } from 'react-hook-form';
import { validateModerationReason } from '../../utils/moderation';
import React, { useState, useEffect, useCallback, useMemo, useRef } from 'react';
import { View, Text, StyleSheet, TouchableOpacity, FlatList, ScrollView, ActivityIndicator, RefreshControl, KeyboardAvoidingView } from 'react-native';
import { Ionicons } from '@react-native-vector-icons/ionicons';
import { useFocusEffect } from '@react-navigation/native';
import { useAuth } from '../../context/AuthContext';
import { useTheme, type ThemeColors } from '../../context/ThemeContext';
import { API_BASE_URL } from '../../config/api';
import { showAlert } from '../../utils/alerts';
import { useLiveSync } from '../../hooks/useLiveSync';
import { useRefreshRequest } from '../../hooks/useRefreshRequest';
import { useAdminOperation } from '../../hooks/useAdminOperation';
import { notifySync } from '../../utils/syncBus';
import { parseOrganizations, parseMembers, isAdminRole, isRecord, formatNumber, formatTimestamp, type AdminOrganization, type AdminMember } from '../../utils/adminData';
import { matchesSearch } from '../../utils/organizationForms';
import FormField from '../../components/forms/FormField';
import { AdminHeader, AdminCard, AdminAction, AdminError, AdminEmpty, AdminSheet, AdminMetric, StatusBadge } from '../../components/admin/AdminUi';

export type OrganizationItem = AdminOrganization;
interface Props { route: { params?: { initialTab?: 'all' | 'pending' } }; }
type StatusFilter = 'ALL' | 'APPROVED' | 'PENDING' | 'REJECTED' | 'BLOCKED';
const FILTERS: { value: StatusFilter; label: string }[] = [
  { value: 'ALL', label: 'Todas' }, { value: 'APPROVED', label: 'Aprovadas' }, { value: 'PENDING', label: 'Pendentes' }, { value: 'REJECTED', label: 'Recusadas' }, { value: 'BLOCKED', label: 'Bloqueadas' },
];

export default function AdminOrganizationsScreen({ route }: Props) {
  const { token, role, userId, getSession } = useAuth(); const { colors } = useTheme();
  const styles = useMemo(() => createStyles(colors), [colors]); const version = getSession().version;
  const pendingMode = route.params?.initialTab === 'pending', isAdmin = isAdminRole(role);
  const [organizations, setOrganizations] = useState<AdminOrganization[]>([]), [pendingOrgs, setPendingOrgs] = useState<AdminOrganization[]>([]);
  const [search, setSearch] = useState(''), [filter, setFilter] = useState<StatusFilter>('ALL');
  const [loading, setLoading] = useState(true), [refreshing, setRefreshing] = useState(false), [loaded, setLoaded] = useState(false), [loadError, setLoadError] = useState('');
  const [selected, setSelected] = useState<AdminOrganization | null>(null), [transfer, setTransfer] = useState(false);
  const [moderation, setModeration] = useState<{ org: AdminOrganization; action: 'reject' | 'block' } | null>(null);
  const reasonForm = useForm({ defaultValues: { reason: '' }, mode: 'onBlur' });
  const [members, setMembers] = useState<AdminMember[]>([]), [memberSearch, setMemberSearch] = useState(''), [memberError, setMemberError] = useState(''), [loadingMembers, setLoadingMembers] = useState(false);
  const refreshRequest = useRefreshRequest(role);
  const memberRequest = useRefreshRequest(`${role}:${selected?.id ?? ''}:${transfer}`);
  const cancelReads = useCallback(() => { refreshRequest.cancel(); memberRequest.cancel(); }, [refreshRequest.cancel, memberRequest.cancel]);
  const operation = useAdminOperation(`${pendingMode}:${selected?.id ?? ''}:${transfer}:${moderation?.org.id ?? ''}:${moderation?.action ?? ''}`, cancelReads);
  const paused = !!selected || !!moderation || operation.busy || operation.confirming;
  const pausedRef = useRef(paused); pausedRef.current = paused;
  const loadedRef = useRef(false);
  // Leadership only moves inside an approved organization that has someone besides the owner.
  const transferBlocked = !selected ? null : selected.status !== 'APPROVED'
    ? 'A liderança só pode ser transferida depois que a organização for aprovada.'
    : (selected.totalMembers ?? 0) < 2 ? 'A organização tem apenas o dono. A liderança pode ser transferida quando houver outro integrante.' : null;
  const mayTransfer = !!selected && (role === 'SYSADMIN' || selected.ownerId === userId) && !transferBlocked;
  useEffect(() => {
    loadedRef.current = false; setOrganizations([]); setPendingOrgs([]); setSearch(''); setFilter('ALL'); setSelected(null); setTransfer(false); setModeration(null); reasonForm.reset({ reason: '' });
    setMembers([]); setMemberSearch(''); setMemberError(''); setLoadingMembers(false); setLoadError(''); setLoaded(false); setLoading(!!token && isAdmin); setRefreshing(false);
  }, [token, role, version, isAdmin, API_BASE_URL]);
  useEffect(() => { if (paused) refreshRequest.cancel(); }, [paused, refreshRequest.cancel]);

  const loadData = useCallback((silent = false) => refreshRequest.run(async ({ signal, isCurrent, isCurrentScope }) => {
    if (!token || !isAdmin) return;
    if (!silent && !loadedRef.current) setLoading(true);
    try {
      const headers = { Authorization: `Bearer ${token}` };
      const [all, pending] = await Promise.all([
        fetch(`${API_BASE_URL}/admin/organizations`, { headers, signal }), fetch(`${API_BASE_URL}/admin/organizations/pending`, { headers, signal }),
      ]);
      if (!isCurrent()) return;
      if (all.status === 401 || pending.status === 401) return;
      if (all.status === 403 || pending.status === 403) {
        setOrganizations([]); setPendingOrgs([]); setSelected(null); setMembers([]); setLoaded(false); loadedRef.current = false;
        setLoadError('Acesso negado pelo servidor. Esta área exige permissão administrativa.'); return;
      }
      if (!all.ok || !pending.ok) throw new Error('Organization read failed');
      const allData = parseOrganizations(await all.json()), pendingData = parseOrganizations(await pending.json());
      if (!isCurrent()) return;
      setOrganizations(allData); setPendingOrgs(pendingData); setLoadError(''); setLoaded(true); loadedRef.current = true;
      setSelected(previous => previous ? allData.find(org => org.id === previous.id) ?? null : null);
    } catch { if (isCurrent()) setLoadError('Não foi possível atualizar as organizações. Os últimos dados recebidos foram preservados.'); }
    finally { if (isCurrentScope()) { setLoading(false); setRefreshing(false); } }
  }), [token, isAdmin, refreshRequest]);
  useFocusEffect(useCallback(() => { if (!pausedRef.current) void loadData(true); }, [loadData]));
  useLiveSync(useCallback(() => loadData(true), [loadData]), { intervalMs: 15000, enabled: !!token && isAdmin && !paused,
    eventTypes: ['ADMIN_ORGS_MUTATED', 'ORGANIZATIONS_MUTATED', 'MEMBERS_MUTATED', 'VISIBILITY_RESTORED'] });
  const refresh = () => { if (refreshRequest.isActive() && !pausedRef.current) { setRefreshing(true); void loadData(true); } };
  const notify = (orgId: number) => { notifySync('ADMIN_ORGS_MUTATED', { orgId }); notifySync('ORGANIZATIONS_MUTATED', { orgId }); notifySync('MEMBERS_MUTATED', { orgId }); };
  const deny = () => { cancelReads(); setOrganizations([]); setPendingOrgs([]); setSelected(null); setTransfer(false); setModeration(null); reasonForm.reset({ reason: '' }); setMembers([]); setLoaded(false); loadedRef.current = false; };

  const confirmState = (org: AdminOrganization, action: 'approve' | 'reject' | 'block', reason?: string) => {
    const label = action === 'approve' ? 'Aprovar' : action === 'reject' ? 'Recusar' : 'Bloquear';
    operation.confirm(`${label} organização`, `${label} ${org.name}? ${action === 'approve' ? 'A organização poderá receber integrantes e criar comissões.' : 'Novos pedidos e criação de comissões ficarão indisponíveis.'}`, async () => {
      await operation.execute({ path: action === 'block' || !pendingMode && action === 'approve' ? `/admin/organizations/${org.id}/status` : `/admin/organizations/${org.id}/${action}`,
        method: action === 'block' || !pendingMode && action === 'approve' ? 'PATCH' : 'POST',
        body: action === 'block' ? { approved: false, justification: reason }
          : !pendingMode && action === 'approve' ? { approved: true } : action === 'reject' ? { reason } : undefined,
        accept: data => isRecord(data) && data.id === org.id && typeof data.approved === 'boolean',
        onDenied: deny,
        onSuccess: async data => {
          const updated = parseOrganizations([data])[0]; refreshRequest.cancel();
          setOrganizations(list => list.map(item => item.id === org.id ? updated : item)); setPendingOrgs(list => list.filter(item => item.id !== org.id));
          setSelected(item => item?.id === org.id ? updated : item); setModeration(null); reasonForm.reset({ reason: '' }); notify(org.id); showAlert('Sucesso', `Estado de ${org.name} atualizado.`); await loadData(true);
        } });
    }, label, action !== 'approve');
  };
  const changeState = (org: AdminOrganization, action: 'approve' | 'reject' | 'block') => {
    if (!operation.isCurrent() || operation.blocked()) return;
    if (action === 'approve') { confirmState(org, action); return; }
    cancelReads(); reasonForm.reset({ reason: '' }); operation.clearError(); setTransfer(false); setModeration({ org, action });
  };
  const closeModeration = () => {
    if (!operation.isCurrent() || operation.blocked()) return;
    setModeration(null); reasonForm.reset({ reason: '' }); operation.clearError();
  };
  const submitModeration = reasonForm.handleSubmit(values => {
    if (moderation) confirmState(moderation.org, moderation.action, values.reason.trim());
  });
  const open = (org: AdminOrganization) => {
    if (!operation.isCurrent() || operation.blocked()) return;
    refreshRequest.cancel(); setSelected(org); setTransfer(false); setMembers([]); setMemberError(''); operation.clearError();
  };
  const close = () => {
    if (!operation.isCurrent() || operation.blocked()) return;
    memberRequest.cancel(); setSelected(null); setTransfer(false); setMembers([]); setMemberError(''); operation.clearError();
  };
  const loadMembers = useCallback(() => memberRequest.run(async ({ signal, isCurrent, isCurrentScope }) => {
    if (!selected || !transfer || !(role === 'SYSADMIN' || selected.ownerId === userId)) return;
    setLoadingMembers(true);
    try {
      const res = await fetch(`${API_BASE_URL}/organizations/${selected.id}/members`, { headers: { Authorization: `Bearer ${token}` }, signal });
      if (!res.ok) { if (isCurrent() && res.status === 403) { deny(); setLoadError('Acesso negado pelo servidor. Atualize as organizações para consultar suas permissões atuais.'); return; } throw new Error('Members unavailable'); }
      const data = parseMembers(await res.json()); if (isCurrent()) { setMembers(data); setMemberError(''); }
    } catch { if (isCurrent()) setMemberError('Não foi possível consultar os integrantes autorizados para a transferência. Tente novamente.'); }
    finally { if (isCurrentScope()) setLoadingMembers(false); }
  }), [token, role, userId, selected?.id, selected?.ownerId, transfer, memberRequest]);
  useEffect(() => { if (transfer) void loadMembers(); }, [transfer, loadMembers]);
  const startTransfer = () => { if (mayTransfer && operation.isCurrent() && !operation.blocked()) { setMembers([]); setMemberSearch(''); setTransfer(true); operation.clearError(); } };
  const transferTo = (member: AdminMember) => {
    if (!selected || !mayTransfer || member.userId === selected.ownerId) return;
    const org = selected;
    operation.confirm('Transferir liderança', `Transferir a liderança de ${org.name} para ${member.name}? O antigo dono será membro comum. O papel global da conta e o estado da organização serão preservados.`,
      () => operation.execute({ path: `/organizations/${org.id}/members/${member.userId}/make-owner`, method: 'POST',
        accept: data => isRecord(data) && data.organizationId === org.id && data.ownerId === member.userId && data.previousOwnerRole === 'MEMBER' && data.ownerRole === 'ORG_OWNER',
        onDenied: deny,
        onSuccess: async () => { memberRequest.cancel(); setSelected(null); setTransfer(false); setMembers([]); notify(org.id); showAlert('Sucesso', 'Liderança transferida. O antigo dono passou a ser membro comum.'); await loadData(true); } }), 'Transferir');
  };
  const visible = (pendingMode ? pendingOrgs : organizations).filter(org => (pendingMode || filter === 'ALL' || org.status === filter) && matchesSearch(search, org.name, org.accessKey, org.ownerName));
  const actions = (org: AdminOrganization) => pendingMode ? <View style={styles.buttons}>
    <View style={styles.flex}><AdminAction label="Recusar" onPress={() => changeState(org, 'reject')} disabled={operation.busy || operation.confirming} danger icon="close-outline" /></View>
    <View style={styles.flex}><AdminAction label="Aprovar" onPress={() => changeState(org, 'approve')} disabled={operation.busy || operation.confirming} filled icon="checkmark-outline" /></View>
  </View> : <AdminAction label={org.approved ? 'Bloquear Organização' : 'Aprovar Organização'} onPress={() => changeState(org, org.approved ? 'block' : 'approve')} disabled={operation.busy || operation.confirming} danger={org.approved} icon={org.approved ? 'ban-outline' : 'checkmark-outline'} />;
  if (!isAdmin) return <View style={styles.container}><AdminHeader title="Organizações" subtitle="Área administrativa" /><AdminEmpty text="Acesso restrito a administradores." /></View>;
  return <KeyboardAvoidingView behavior="padding" style={styles.container}>
    <AdminHeader title={pendingMode ? 'Aprovações' : 'Organizações'} subtitle={pendingMode ? `${loaded ? pendingOrgs.length : '...'} organizações pendentes` : 'Busca global e situação das organizações'} onRefresh={refresh} refreshing={refreshing} disabled={paused || refreshing} />
    <View style={styles.search}><FormField label="Buscar organização" placeholder="Buscar por nome, #chave ou responsável..." value={search} icon="search-outline" onChangeText={value => { if (operation.isCurrent() && !operation.blocked()) setSearch(value); }} />
      {!pendingMode && <ScrollView horizontal showsHorizontalScrollIndicator={false}><View style={styles.filters}>{FILTERS.map(item => <TouchableOpacity key={item.value} accessibilityRole="button" accessibilityState={{ selected: filter === item.value }}
        style={[styles.filter, filter === item.value && { backgroundColor: colors.primary }]} onPress={() => { if (operation.isCurrent() && !operation.blocked()) setFilter(item.value); }}>
        <Text style={[styles.filterText, filter === item.value && { color: '#FFFFFF' }]}>{item.label}</Text></TouchableOpacity>)}</View></ScrollView>}
      <AdminError message={loadError} /><AdminError message={operation.error} />{loadError && <AdminAction label="Tentar novamente" onPress={refresh} disabled={paused} icon="refresh-outline" />}
    </View>
    {loading && !loaded ? <View style={styles.loading}><ActivityIndicator color={colors.primary} /><Text style={styles.caption}>Carregando organizações...</Text></View> : <FlatList data={visible} keyExtractor={item => String(item.id)} contentContainerStyle={styles.content}
      refreshControl={<RefreshControl refreshing={refreshing} onRefresh={refresh} enabled={!paused} tintColor={colors.primary} colors={[colors.primary]} />}
      ListEmptyComponent={loaded && !loadError ? <AdminEmpty text={search.trim() ? 'Nenhuma organização corresponde à busca.' : pendingMode ? 'Nenhuma organização aguardando aprovação.' : 'Nenhuma organização corresponde ao filtro.'} /> : null}
      renderItem={({ item }) => <AdminCard><TouchableOpacity accessibilityRole="button" accessibilityLabel={`Detalhes de ${item.name}`} onPress={() => open(item)} disabled={operation.busy || operation.confirming}>
        <View style={styles.row}><View style={styles.avatar}><Text style={styles.avatarText}>{item.name.slice(0, 1).toUpperCase()}</Text></View><View style={styles.flex}><Text style={styles.cardTitle}>{item.name}</Text>
          <Text style={styles.caption}>{item.accessKey}</Text></View><StatusBadge value={item.status} /></View>
        <Text style={styles.caption}>{pendingMode ? `Criada em ${formatTimestamp(item.createdAt)}` : `${formatNumber(item.totalMembers)} membros, ${formatNumber(item.totalCommittees)} comissões`}</Text>
        {pendingMode && <><Text style={styles.caption}>Responsável: {item.ownerName}</Text><Text style={styles.caption}>CNPJ: {item.cnpjMasked ?? 'Não informado'}</Text>
          {item.resubmittedAt && <Text style={styles.caption}>Reenvio após recusa. Veja a justificativa nos detalhes.</Text>}</>}
      </TouchableOpacity>{actions(item)}</AdminCard>}
    />}
    <AdminSheet visible={!!selected || !!moderation} title={moderation ? (moderation.action === 'reject' ? 'Recusar organização' : 'Bloquear organização') : transfer ? 'Transferir liderança' : 'Detalhes da organização'} onClose={moderation ? closeModeration : close} disabled={operation.busy || operation.confirming}>
      {moderation ? <><Text style={styles.cardTitle}>{moderation.org.name}</Text>
        <Text style={styles.caption}>O motivo será apresentado ao responsável pela organização e aos administradores.</Text>
        <Controller control={reasonForm.control} name="reason" rules={{ validate: validateModerationReason }}
          render={({ field: { value, onChange, onBlur }, fieldState: { error } }) => <FormField label="Motivo" required
            placeholder="Explique o motivo" value={value} onChangeText={text => { if (operation.isCurrent() && !operation.blocked()) onChange(text); }}
            onBlur={onBlur} error={error?.message} multiline maxLength={500} editable={!operation.busy && !operation.confirming} />} />
        <Text style={styles.caption}>De 3 a 500 caracteres.</Text><AdminError message={operation.error} />
        <AdminAction label={moderation.action === 'reject' ? 'Confirmar recusa' : 'Confirmar bloqueio'} onPress={() => void submitModeration()}
          disabled={operation.busy || operation.confirming} danger icon="checkmark-outline" />
        <AdminAction label="Cancelar" onPress={closeModeration} disabled={operation.busy || operation.confirming} />
      </> : selected && <><Text style={styles.cardTitle}>{selected.name}</Text><Text style={styles.caption}>{selected.accessKey}</Text><StatusBadge value={selected.status} />
        <AdminError message={operation.error} />{transfer ? <><Text style={styles.caption}>Escolha qualquer outro integrante atual desta organização. O antigo dono será membro comum.</Text>
          <FormField label="Buscar integrante" placeholder="Nome ou e-mail do integrante" value={memberSearch} icon="search-outline" onChangeText={value => { if (operation.isCurrent() && !operation.blocked()) setMemberSearch(value); }} />
          <AdminError message={memberError} />{loadingMembers ? <ActivityIndicator color={colors.primary} /> : members.filter(member => member.userId !== selected.ownerId && matchesSearch(memberSearch, member.name, member.email)).map(member => <AdminCard key={member.userId}>
            <Text style={styles.cardTitle}>{member.name}</Text><Text style={styles.caption}>{member.email}</Text><AdminAction label={`Transferir para ${member.name}`} onPress={() => transferTo(member)} disabled={operation.busy || operation.confirming} icon="swap-horizontal-outline" /></AdminCard>)}
          {memberError && <AdminAction label="Atualizar integrantes" onPress={loadMembers} disabled={operation.busy} icon="refresh-outline" />}
          {!loadingMembers && !memberError && !members.some(member => member.userId !== selected.ownerId) && <AdminEmpty text="A organização precisa ter outro integrante para receber a liderança." />}
        </> : <><AdminMetric label="Responsável" value={selected.ownerName} /><AdminMetric label="E-mail (mascarado)" value={selected.ownerEmailMasked ?? 'Não informado'} /><AdminMetric label="CNPJ (mascarado)" value={selected.cnpjMasked ?? 'Não informado'} />
          <AdminMetric label="Membros" value={formatNumber(selected.totalMembers)} /><AdminMetric label="Comissões" value={formatNumber(selected.totalCommittees)} />
          <Text style={styles.caption}>Criada em {formatTimestamp(selected.createdAt)}</Text>
          {selected.resubmittedAt && <Text style={styles.caption}>Reenviada para avaliação em {formatTimestamp(selected.resubmittedAt)}.{selected.status === 'PENDING' ? ' É a única reavaliação: uma nova recusa é definitiva.' : ''}</Text>}
          {selected.resubmissionNote && <Text style={styles.caption}>Justificativa do dono: {selected.resubmissionNote}</Text>}
          {selected.moderationReason && <Text style={styles.caption}>{selected.status === 'PENDING' ? 'Motivo da recusa anterior' : 'Motivo'}: {selected.moderationReason}</Text>}{actions(selected)}
          {mayTransfer && <AdminAction label="Transferir liderança" onPress={startTransfer} disabled={operation.busy || operation.confirming} icon="swap-horizontal-outline" />}
          {transferBlocked && (role === 'SYSADMIN' || selected.ownerId === userId) && <Text style={styles.caption}>{transferBlocked}</Text>}
        </>}</>}
    </AdminSheet>
  </KeyboardAvoidingView>;
}
function createStyles(colors: ThemeColors) { return StyleSheet.create({
  container: { flex: 1, backgroundColor: colors.background }, search: { paddingHorizontal: 20, paddingBottom: 12 }, content: { paddingHorizontal: 20, paddingBottom: 24 },
  row: { flexDirection: 'row', alignItems: 'center', gap: 10, marginBottom: 8 }, flex: { flex: 1, minWidth: 0 }, buttons: { flexDirection: 'row', gap: 8 },
  cardTitle: { color: colors.text, fontSize: 15, lineHeight: 22, fontWeight: '700', marginBottom: 4 }, caption: { color: colors.subtext, fontSize: 12, lineHeight: 19, marginVertical: 3 },
  avatar: { width: 36, height: 36, borderRadius: 18, alignItems: 'center', justifyContent: 'center', backgroundColor: colors.inputBg }, avatarText: { color: colors.isDark ? '#ADB5EF' : colors.primary, fontWeight: '700', fontSize: 15 },
  filters: { flexDirection: 'row', gap: 8, paddingBottom: 8 }, filter: { minHeight: 48, paddingHorizontal: 13, justifyContent: 'center', borderWidth: 1, borderColor: colors.border, borderRadius: 24, backgroundColor: colors.card }, filterText: { color: colors.text, fontSize: 12, fontWeight: '600' },
  loading: { flex: 1, alignItems: 'center', justifyContent: 'center', gap: 10 },
}); }
