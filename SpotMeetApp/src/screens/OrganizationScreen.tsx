import React, { useState, useCallback, useEffect, useMemo, useRef } from 'react';
import { View, Text, TouchableOpacity, StyleSheet, ScrollView, ActivityIndicator, Modal, RefreshControl } from 'react-native';
import { Controller, useForm, useWatch, type UseFormReturn } from 'react-hook-form';
import { Ionicons, type IoniconsIconName } from '@react-native-vector-icons/ionicons';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useFocusEffect } from '@react-navigation/native';
import { useAuth } from '../context/AuthContext';
import { useTheme, type ThemeColors } from '../context/ThemeContext';
import { API_BASE_URL } from '../config/api';
import { showAlert as alert, confirmAction } from '../utils/alerts';
import ToastHost from '../components/ui/ToastHost';
import ConfirmationHost from '../components/ui/ConfirmationHost';
import { useLiveSync } from '../hooks/useLiveSync';
import { useRefreshRequest } from '../hooks/useRefreshRequest';
import { notifySync as broadcastSync } from '../utils/syncBus';
import { normalizeEmail, isValidEmail } from '../utils/validators';
import { matchesSearch, normalizeAccessKey, normalizeOrganizationName, validateAccessKey, validateCommitteeName, validateOrganizationName } from '../utils/organizationForms';
import FormField from '../components/forms/FormField';
import FormButton from '../components/forms/FormButton';
import FormScrollView from '../components/forms/FormScrollView';
import PresidentSheet from '../components/meetings/PresidentSheet';

type OrganizationRole = 'ORG_OWNER' | 'ORG_VICE_OWNER' | 'ORG_SUBOWNER' | 'MEMBER';
interface MyOrganization { id: number; name: string; accessKey: string; ownerId: number; ownerName: string; myRole?: OrganizationRole; approved?: boolean; status?: string; moderationReason?: string | null; resubmitted?: boolean; }
interface Member { userId: number; name: string; email: string; role: OrganizationRole; }
interface OrganizationAccessRequest { id: number; userName: string; userEmail: string; }
interface CommitteeJoinRequest extends OrganizationAccessRequest { committeeId: number; committeeName: string; }
interface Committee { id: number; name: string; description: string | null; totalActiveMembers: number; totalPending: number; presidentName?: string | null; }
interface UserCommitteeLink { committeeId: number; committeeName: string; description: string | null; linked: boolean; membershipStatus: string | null; }
interface PendingInvitation { id: number; committeeId: number; committeeName: string; organizationId: number; invitedByName: string; }
interface OrganizationInvitation { id: number; organizationId: number; organizationName: string; invitedByName: string; expiresAt: string; }
interface OrganizationPreview { name: string; ownerName: string; }
interface InviteCandidate { name: string; email: string; }
type Tab = 'mine' | 'join' | 'create' | 'manage' | 'committees';
type Page = 'overview' | 'members' | 'requests' | 'committees' | 'invitations';
type Sheet = 'organization' | 'committee' | 'invite' | 'transfer' | 'member' | 'resubmit' | null;
type Fields = { name: string; accessKey: string; description: string; email: string; };
const EMPTY_FORM: Fields = { name: '', accessKey: '', description: '', email: '' };
const ROLE_LABELS: Record<OrganizationRole, string> = { ORG_OWNER: 'Dono', ORG_VICE_OWNER: 'Vice-líder', ORG_SUBOWNER: 'Subdono', MEMBER: 'Membro' };
const canManage = (org: MyOrganization | null) => !!org && ['ORG_OWNER', 'ORG_VICE_OWNER', 'ORG_SUBOWNER'].includes(org.myRole ?? '');
const record = (value: unknown): value is Record<string, unknown> => value !== null && typeof value === 'object' && !Array.isArray(value);
const id = (value: unknown) => typeof value === 'number' && Number.isSafeInteger(value) && value > 0;

async function readList<T>(res: Response, ids: string[], texts: string[]): Promise<T[]> {
  const data: unknown = await res.json();
  if (!Array.isArray(data) || !data.every(row => record(row) && ids.every(key => id(row[key])) && texts.every(key => typeof row[key] === 'string')
    && (row.description === undefined || row.description === null || typeof row.description === 'string')
    && (row.moderationReason === undefined || row.moderationReason === null || typeof row.moderationReason === 'string' && row.moderationReason.length <= 500))) {
    throw new Error('Invalid list response');
  }
  return data as T[];
}

function errorMessage(res: Response, text: string, fallback: string): string {
  if (res.status >= 500) return fallback;
  if (res.status === 401) return 'Sua sessão foi encerrada. Entre novamente.';
  if (!text) return fallback;
  try { const value: unknown = JSON.parse(text); return record(value) && typeof value.message === 'string' ? value.message : fallback; }
  catch { return text.length <= 500 && !/[<>]/.test(text) ? text : fallback; }
}

function expiresLabel(value: string): string {
  const date = new Date(value);
  return Number.isFinite(date.getTime()) ? `Válido até ${date.toLocaleString('pt-BR', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' })}` : 'Validade de 24 horas';
}

export default function OrganizationScreen() {
  const insets = useSafeAreaInsets();
  const { token, userId: authUserId, getSession } = useAuth();
  const sessionVersion = getSession().version;
  const { colors } = useTheme();
  const styles = useMemo(() => createStyles(colors), [colors]);
  const [tab, setTab] = useState<Tab>('mine');
  const [page, setPage] = useState<Page>('overview');
  const [myOrgs, setMyOrgs] = useState<MyOrganization[]>([]);
  const [selectedOrg, setSelectedOrg] = useState<MyOrganization | null>(null);
  const selectedOrgRef = useRef(selectedOrg);
  selectedOrgRef.current = selectedOrg;
  const [members, setMembers] = useState<Member[]>([]);
  const [pendingOrgRequests, setPendingOrgRequests] = useState<OrganizationAccessRequest[]>([]);
  const [committeeJoinRequests, setCommitteeJoinRequests] = useState<CommitteeJoinRequest[]>([]);
  const [committees, setCommittees] = useState<Committee[]>([]);
  const [myInvitations, setMyInvitations] = useState<PendingInvitation[]>([]);
  const [organizationInvitations, setOrganizationInvitations] = useState<OrganizationInvitation[]>([]);
  const [myLinks, setMyLinks] = useState<UserCommitteeLink[]>([]);
  const [sheet, setSheet] = useState<Sheet>(null);
  const [editingCommittee, setEditingCommittee] = useState<Committee | null>(null);
  const [presidentCommittee, setPresidentCommittee] = useState<Committee | null>(null);
  const [selectedMember, setSelectedMember] = useState<Member | null>(null);
  const [linksModalVisible, setLinksModalVisible] = useState(false);
  const [memberLinks, setMemberLinks] = useState<UserCommitteeLink[]>([]);
  const [loadingLinks, setLoadingLinks] = useState(false);
  const [loadingData, setLoadingData] = useState(true);
  const [loadingDetails, setLoadingDetails] = useState(false);
  const [detailsLoaded, setDetailsLoaded] = useState(false);
  const [refreshing, setRefreshing] = useState(false);
  const [busy, setBusy] = useState(false);
  const [confirmingAction, setConfirmingAction] = useState(false);
  const writeLock = useRef<object | null>(null);
  const confirmLock = useRef<object | null>(null);
  const refreshLock = useRef<Promise<void> | null>(null);
  const hasLoaded = useRef(false);
  const [loadError, setLoadError] = useState('');
  const [invitationError, setInvitationError] = useState('');
  const [detailsError, setDetailsError] = useState('');
  const [formError, setFormError] = useState('');
  const [joinMessage, setJoinMessage] = useState('');
  const [search, setSearch] = useState('');
  const [memberSearch, setMemberSearch] = useState('');
  const [memberRole, setMemberRole] = useState<OrganizationRole | 'ALL'>('ALL');
  const joinForm = useForm<Fields>({ defaultValues: { ...EMPTY_FORM }, mode: 'onBlur' });
  const createForm = useForm<Fields>({ defaultValues: { ...EMPTY_FORM }, mode: 'onBlur' });
  const sheetForm = useForm<Fields>({ defaultValues: { ...EMPTY_FORM }, mode: 'onBlur' });
  const joinDraft = useWatch({ control: joinForm.control });
  const inviteDraft = useWatch({ control: sheetForm.control, name: 'email' });
  const [preview, setPreview] = useState<OrganizationPreview | null>(null);
  const [previewError, setPreviewError] = useState('');
  const [previewLoading, setPreviewLoading] = useState(false);
  const [candidates, setCandidates] = useState<InviteCandidate[]>([]);
  const [candidatesError, setCandidatesError] = useState('');
  const [candidatesLoading, setCandidatesLoading] = useState(false);
  const pickedEmail = useRef('');
  const orgsRequest = useRefreshRequest<MyOrganization | null>();
  const invitationsRequest = useRefreshRequest();
  const organizationScope = `${selectedOrg?.id ?? ''}:${selectedOrg?.myRole ?? ''}`;
  const detailsRequest = useRefreshRequest(organizationScope);
  const linksRequest = useRefreshRequest(`${organizationScope}:${linksModalVisible ? selectedMember?.userId : ''}`);
  const cancelReads = useCallback(() => { orgsRequest.cancel(); invitationsRequest.cancel(); detailsRequest.cancel(); linksRequest.cancel(); },
    [orgsRequest.cancel, invitationsRequest.cancel, detailsRequest.cancel, linksRequest.cancel]);
  const syncPaused = tab === 'create' || (tab === 'join' && !!joinDraft.accessKey) || !!sheet || !!presidentCommittee || linksModalVisible || busy || confirmingAction;
  const syncPausedRef = useRef(syncPaused);
  syncPausedRef.current = syncPaused;
  const viewRef = useRef('');
  viewRef.current = `${organizationScope}:${tab}:${page}:${sheet}:${linksModalVisible}:${selectedMember?.userId ?? ''}`;
  const viewKey = viewRef.current;
  const currentView = () => viewRef.current === viewKey && orgsRequest.isActive();
  const myRole = selectedOrg?.myRole ?? 'MEMBER';
  const isOwner = myRole === 'ORG_OWNER';
  const isVice = myRole === 'ORG_VICE_OWNER';
  const isSubowner = myRole === 'ORG_SUBOWNER';
  const isLeader = canManage(selectedOrg);
  const isOwnerOrVice = isOwner || isVice;

  const clearDetails = useCallback(() => {
    setMembers([]); setPendingOrgRequests([]); setCommitteeJoinRequests([]); setCommittees([]); setMyLinks([]);
    setMemberLinks([]); setDetailsError(''); setLoadingDetails(false); setDetailsLoaded(false);
  }, []);
  const clearSelection = useCallback(() => { selectedOrgRef.current = null; setSelectedOrg(null); clearDetails(); }, [clearDetails]);
  useEffect(() => {
    hasLoaded.current = false; writeLock.current = null; confirmLock.current = null; refreshLock.current = null;
    setMyOrgs([]); setMyInvitations([]); setOrganizationInvitations([]); clearSelection();
    setTab('mine'); setPage('overview'); setSheet(null); setLinksModalVisible(false); setSelectedMember(null);
    setLoadingData(!!token); setLoadingLinks(false); setBusy(false); setConfirmingAction(false); setRefreshing(false);
    setLoadError(''); setInvitationError(''); setFormError(''); setJoinMessage(''); setSearch(''); setMemberSearch(''); setMemberRole('ALL');
    joinForm.reset({ ...EMPTY_FORM }); createForm.reset({ ...EMPTY_FORM }); sheetForm.reset({ ...EMPTY_FORM });
  }, [token, sessionVersion, clearSelection, joinForm.reset, createForm.reset, sheetForm.reset]);
  useEffect(() => { if (syncPaused) cancelReads(); }, [syncPaused, cancelReads]);
  useEffect(() => {
    if (selectedOrg && !isLeader && tab === 'manage') { setTab('committees'); setPage('committees'); setSheet(null); setLinksModalVisible(false); }
  }, [isLeader, selectedOrg?.id, tab]);

  // Shows the organization behind the typed key before the user asks to join it.
  useEffect(() => {
    setPreview(null); setPreviewError('');
    const key = joinDraft.accessKey ?? '';
    if (tab !== 'join' || !token || validateAccessKey(key) !== true) { setPreviewLoading(false); return; }
    const controller = new AbortController();
    setPreviewLoading(true);
    const timer = setTimeout(async () => {
      try {
        const res = await fetch(`${API_BASE_URL}/organizations/preview?accessKey=${encodeURIComponent(normalizeAccessKey(key))}`,
          { headers: { Authorization: `Bearer ${token}` }, signal: controller.signal });
        const text = await res.text();
        if (controller.signal.aborted) return;
        if (!res.ok) { setPreviewError(errorMessage(res, text, 'Não foi possível consultar a organização.')); return; }
        const data: unknown = JSON.parse(text);
        if (record(data) && typeof data.name === 'string' && typeof data.ownerName === 'string') setPreview({ name: data.name, ownerName: data.ownerName });
      } catch { if (!controller.signal.aborted) setPreviewError('Não foi possível consultar a organização.'); }
      finally { if (!controller.signal.aborted) setPreviewLoading(false); }
    }, 500);
    return () => { clearTimeout(timer); controller.abort(); };
  }, [tab, token, joinDraft.accessKey]);

  // Looks for people to invite by part of the name or e-mail (3 characters or more).
  useEffect(() => {
    const term = (inviteDraft ?? '').trim();
    setCandidates([]); setCandidatesError('');
    if (sheet !== 'invite') pickedEmail.current = '';
    if (sheet !== 'invite' || !selectedOrg || !token || term.length < 3 || term === pickedEmail.current) { setCandidatesLoading(false); return; }
    const controller = new AbortController();
    setCandidatesLoading(true);
    const timer = setTimeout(async () => {
      try {
        const res = await fetch(`${API_BASE_URL}/organizations/${selectedOrg.id}/invitations/candidates?q=${encodeURIComponent(term)}`,
          { headers: { Authorization: `Bearer ${token}` }, signal: controller.signal });
        const text = await res.text();
        if (controller.signal.aborted) return;
        if (!res.ok) { setCandidatesError(errorMessage(res, text, 'Não foi possível buscar as pessoas.')); return; }
        const data: unknown = JSON.parse(text);
        if (Array.isArray(data)) setCandidates(data.filter((row): row is InviteCandidate => record(row) && typeof row.name === 'string' && typeof row.email === 'string'));
      } catch { if (!controller.signal.aborted) setCandidatesError('Não foi possível buscar as pessoas.'); }
      finally { if (!controller.signal.aborted) setCandidatesLoading(false); }
    }, 400);
    return () => { clearTimeout(timer); controller.abort(); };
  }, [sheet, selectedOrg?.id, token, inviteDraft]);
  const pickCandidate = (candidate: InviteCandidate) => {
    pickedEmail.current = candidate.email;
    sheetForm.setValue('email', candidate.email, { shouldValidate: true });
    setCandidates([]); setFormError('');
  };
  // Drafts of "Entrar" and "Criar" do not survive leaving the tab or the screen.
  const clearDrafts = useCallback(() => {
    joinForm.reset({ ...EMPTY_FORM }); createForm.reset({ ...EMPTY_FORM }); setJoinMessage(''); setFormError('');
  }, [joinForm.reset, createForm.reset]);
  useFocusEffect(useCallback(() => clearDrafts, [clearDrafts]));

  const selectOrganization = (org: MyOrganization) => {
    if (!currentView() || writeLock.current || confirmLock.current) return;
    cancelReads(); clearDetails(); setSheet(null); setLinksModalVisible(false); setSelectedMember(null);
    setMemberSearch(''); setMemberRole('ALL'); setFormError('');
    selectedOrgRef.current = org; setSelectedOrg(org);
  };

  const loadMyOrgs = useCallback((silent = false) => orgsRequest.run(async ({ signal, isCurrent, isCurrentScope }) => {
    if (!silent && !hasLoaded.current) setLoadingData(true);
    try {
      const res = await fetch(`${API_BASE_URL}/organizations/mine`, { headers: { Authorization: `Bearer ${token}` }, signal });
      if (!res.ok) {
        if (isCurrent() && res.status === 403) { setMyOrgs([]); clearSelection(); }
        throw new Error('Organization read failed');
      }
      const rows = await readList<MyOrganization>(res, ['id'], ['name', 'accessKey', 'ownerName']);
      if (!isCurrent()) return;
      const data = rows.map(org => ({ ...org, myRole: org.myRole && Object.prototype.hasOwnProperty.call(ROLE_LABELS, org.myRole) ? org.myRole : 'MEMBER' as OrganizationRole }));
      hasLoaded.current = true; setLoadError(''); setMyOrgs(data);
      const previous = selectedOrgRef.current;
      const selected = data.find(org => org.id === previous?.id) ?? data[0] ?? null;
      if (selected?.id !== previous?.id || selected?.myRole !== previous?.myRole) {
        detailsRequest.cancel(); linksRequest.cancel(); clearDetails(); setSheet(null); setLinksModalVisible(false); setSelectedMember(null);
      }
      selectedOrgRef.current = selected; setSelectedOrg(selected);
      return selected;
    } catch {
      if (isCurrent()) setLoadError('Não foi possível atualizar suas organizações. Tente novamente.');
    } finally { if (isCurrentScope()) setLoadingData(false); }
  }), [token, orgsRequest, detailsRequest.cancel, linksRequest.cancel, clearSelection, clearDetails]);

  const loadInvitations = useCallback(() => invitationsRequest.run(async ({ signal, isCurrent }) => {
    try {
    const headers = { Authorization: `Bearer ${token}` };
    const responses = await Promise.all([
      fetch(`${API_BASE_URL}/organization-invitations/mine`, { headers, signal }),
      fetch(`${API_BASE_URL}/committees/invitations`, { headers, signal }),
    ]);
    if (responses.some(res => !res.ok)) throw new Error('Invitation read failed');
    const orgInvites = await readList<OrganizationInvitation>(responses[0], ['id', 'organizationId'], ['organizationName', 'invitedByName', 'expiresAt']);
    const committeeInvites = await readList<PendingInvitation>(responses[1], ['id', 'committeeId', 'organizationId'], ['committeeName', 'invitedByName']);
    if (!isCurrent()) return;
    setOrganizationInvitations(orgInvites); setMyInvitations(committeeInvites); setInvitationError('');
    } catch { if (isCurrent()) setInvitationError('Não foi possível consultar seus convites. Tente novamente.'); }
  }), [token, invitationsRequest]);

  const loadDetails = useCallback((org: MyOrganization, silent = false) => detailsRequest.run(async ({ signal, isCurrent, isCurrentScope }) => {
    const current = () => isCurrent() && selectedOrgRef.current?.id === org.id && selectedOrgRef.current?.myRole === org.myRole;
    if (!silent) setLoadingDetails(true);
    const headers = { Authorization: `Bearer ${token}` };
    const read = (path: string) => fetch(`${API_BASE_URL}${path}`, { headers, signal });
    try {
      const leader = canManage(org), ownerOrVice = org.myRole === 'ORG_OWNER' || org.myRole === 'ORG_VICE_OWNER';
      const responses = await Promise.all([
        read(`/organizations/${org.id}/members`), read(`/organizations/${org.id}/committees`),
        ownerOrVice ? read(`/organizations/${org.id}/access-requests`) : null,
        leader ? read(`/organizations/${org.id}/committees/join-requests`) : null,
        authUserId ? read(`/organizations/${org.id}/members/${authUserId}/committees`) : null,
      ]);
      if (!current()) return;
      if (responses.slice(0, 2).some(res => res?.status === 403 || res?.status === 404)) {
        clearSelection(); setTab('mine'); return;
      }
      if (responses.some(res => res && !res.ok)) throw new Error('Details read failed');
      const memberRows = await readList<Member>(responses[0]!, ['userId'], ['name', 'email', 'role']);
      const committeeRows = await readList<Committee>(responses[1]!, ['id'], ['name']);
      const accessRows = responses[2] ? await readList<OrganizationAccessRequest>(responses[2], ['id'], ['userName', 'userEmail']) : [];
      const requestRows = responses[3] ? await readList<CommitteeJoinRequest>(responses[3], ['id', 'committeeId'], ['userName', 'userEmail', 'committeeName']) : [];
      const linkRows = responses[4] ? await readList<UserCommitteeLink>(responses[4], ['committeeId'], ['committeeName']) : [];
      if (!current()) return;
      setMembers(memberRows); setCommittees(committeeRows); setPendingOrgRequests(accessRows); setCommitteeJoinRequests(requestRows); setMyLinks(linkRows); setDetailsError(''); setDetailsLoaded(true);
    } catch { if (current()) setDetailsError('Não foi possível atualizar os dados da organização. Tente novamente.'); }
    finally { if (isCurrentScope()) setLoadingDetails(false); }
  }), [token, authUserId, detailsRequest, clearSelection]);

  const refreshCurrent = useCallback(async () => {
    const org = await loadMyOrgs(true);
    if (org === undefined) return;
    await loadInvitations();
    if (org && orgsRequest.isActive() && !syncPausedRef.current && (tab === 'manage' || tab === 'committees')) await loadDetails(org, true);
  }, [loadMyOrgs, loadInvitations, loadDetails, orgsRequest.isActive, tab]);
  useFocusEffect(useCallback(() => {
    if (!syncPausedRef.current) { void loadMyOrgs(hasLoaded.current); void loadInvitations(); }
  }, [loadMyOrgs, loadInvitations]));
  useEffect(() => {
    if (selectedOrg && (tab === 'manage' || tab === 'committees') && !syncPausedRef.current) void loadDetails(selectedOrg);
  }, [selectedOrg?.id, selectedOrg?.myRole, tab, loadDetails]);
  useLiveSync(refreshCurrent, { intervalMs: 15000, enabled: !!token && !syncPaused,
    eventTypes: ['ORGANIZATIONS_MUTATED', 'MEMBERS_MUTATED', 'REQUESTS_MUTATED', 'COMMITTEES_MUTATED', 'ADMIN_ORGS_MUTATED', 'VISIBILITY_RESTORED'] });
  const handleRefresh = () => {
    if (!currentView() || syncPausedRef.current || refreshLock.current) return refreshLock.current ?? undefined;
    setRefreshing(true);
    const pending = refreshCurrent().finally(() => {
      if (refreshLock.current === pending) { refreshLock.current = null; if (orgsRequest.isCurrentScope()) setRefreshing(false); }
    });
    refreshLock.current = pending; return pending;
  };

  const loadMemberLinks = useCallback((memberId: number) => linksRequest.run(async ({ signal, isCurrent, isCurrentScope }) => {
    if (!selectedOrg) return;
    setLoadingLinks(true);
    try {
      const res = await fetch(`${API_BASE_URL}/organizations/${selectedOrg.id}/members/${memberId}/committees`, { headers: { Authorization: `Bearer ${token}` }, signal });
      if (!res.ok) throw new Error('Links read failed');
      const rows = await readList<UserCommitteeLink>(res, ['committeeId'], ['committeeName']);
      if (isCurrent()) { setMemberLinks(rows); setFormError(''); }
    } catch { if (isCurrent()) setFormError('Não foi possível consultar as comissões. Tente novamente.'); }
    finally { if (isCurrentScope()) setLoadingLinks(false); }
  }), [token, selectedOrg?.id, linksRequest]);
  useEffect(() => { if (linksModalVisible && selectedMember) void loadMemberLinks(selectedMember.userId); }, [linksModalVisible, selectedMember?.userId, loadMemberLinks]);

  const mutate = async (path: string, method: string, body: unknown, message: string | ((data: unknown) => string), onSuccess?: () => void, form?: UseFormReturn<Fields>) => {
    if (!currentView() || writeLock.current) return;
    const lock = {}; writeLock.current = lock; cancelReads(); setBusy(true); setFormError('');
    const current = () => currentView() && writeLock.current === lock;
    try {
      const res = await fetch(`${API_BASE_URL}${path}`, {
        method, headers: { Authorization: `Bearer ${token}`, ...(body === undefined ? {} : { 'Content-Type': 'application/json' }) },
        ...(body === undefined ? {} : { body: JSON.stringify(body) }),
      });
      const text = await res.text();
      if (!current()) return;
      if (!res.ok) {
        const message = errorMessage(res, text, 'Não foi possível concluir a operação. Tente novamente.');
        let fields = false;
        if (form && res.status === 400) {
          try { const data: unknown = JSON.parse(text); if (record(data) && record(data.errors)) for (const key of ['name', 'accessKey', 'description', 'email'] as const) {
            if (typeof data.errors[key] === 'string') { form.setError(key, { message: data.errors[key] as string }); fields = true; }
          } } catch { /* Plain error responses use the form message. */ }
        }
        if (!fields) setFormError(message);
        if (res.status === 403 || res.status === 404 || res.status === 409) {
          void loadInvitations();
          if (res.status === 403 || res.status === 404) void loadMyOrgs(true);
        }
        return;
      }
      let data: unknown; try { data = JSON.parse(text); } catch { data = null; }
      onSuccess?.(); alert('Sucesso', typeof message === 'function' ? message(data) : message);
      broadcastSync('ORGANIZATIONS_MUTATED'); broadcastSync('COMMITTEES_MUTATED'); broadcastSync('ADMIN_ORGS_MUTATED');
      // The next reads take roles and membership from the server.
      await loadMyOrgs(true); await loadInvitations();
      if (linksModalVisible && selectedMember && linksRequest.isActive()) await loadMemberLinks(selectedMember.userId);
    } catch { if (current()) setFormError('Não foi possível conectar ao servidor. Tente novamente.'); }
    finally { if (writeLock.current === lock) { writeLock.current = null; if (orgsRequest.isCurrentScope()) setBusy(false); } }
  };

  const confirm = (title: string, message: string, label: string, action: () => void | Promise<void>, destructive = false) => {
    if (!currentView() || writeLock.current || confirmLock.current) return;
    const lock = {}; confirmLock.current = lock; cancelReads(); setConfirmingAction(true);
    const finish = () => { if (confirmLock.current === lock) { confirmLock.current = null; if (orgsRequest.isCurrentScope()) setConfirmingAction(false); } };
    const execute = async () => { try { if (confirmLock.current === lock && currentView()) await action(); } finally { finish(); } };
    confirmAction(title, message, execute, label, destructive, finish);
  };
  const openSheet = (next: Sheet, values = EMPTY_FORM) => {
    if (!currentView() || writeLock.current || confirmLock.current) return;
    cancelReads(); sheetForm.reset({ ...values }); setFormError(''); setSheet(next);
  };
  const closeSheet = () => { if (currentView() && !writeLock.current && !confirmLock.current) { setSheet(null); sheetForm.reset({ ...EMPTY_FORM }); setFormError(''); } };
  const openLinks = (member: Member) => {
    if (!currentView() || writeLock.current) return;
    cancelReads(); setSheet(null); setSelectedMember(member); setMemberLinks([]); setFormError(''); setLinksModalVisible(true);
  };
  const closeLinks = () => { if (currentView() && !writeLock.current && !confirmLock.current) { linksRequest.cancel(); setLinksModalVisible(false); setFormError(''); } };
  const switchTab = (next: Tab) => {
    if (!currentView() || writeLock.current || confirmLock.current || (next === 'manage' && !isLeader)) return;
    if (next !== tab) clearDrafts();
    setTab(next); setPage(next === 'committees' ? 'committees' : 'overview'); setFormError('');
  };
  const handleJoin = async () => {
    if (!currentView() || writeLock.current) return;
    setJoinMessage('');
    let sent = false;
    await joinForm.handleSubmit(data => mutate(`/access-requests?accessKey=${encodeURIComponent(normalizeAccessKey(data.accessKey))}`, 'POST', undefined,
      'Solicitação enviada. Aguarde a aprovação dos responsáveis.', () => { sent = true; }, joinForm))();
    if (sent && orgsRequest.isActive()) {
      // Reset after handleSubmit finishes updating submission state.
      joinForm.reset({ ...EMPTY_FORM });
      setJoinMessage('Solicitação enviada. Aguarde a aprovação dos responsáveis.');
    }
  };
  const handleCreate = createForm.handleSubmit(data => mutate('/organizations', 'POST', { name: normalizeOrganizationName(data.name), accessKey: normalizeAccessKey(data.accessKey) },
    'Organização criada. Aguarde a avaliação do administrador do sistema.', () => { createForm.reset({ ...EMPTY_FORM }); setTab('mine'); }, createForm));
  const handleSheetSave = sheetForm.handleSubmit(data => {
    if (!selectedOrg) return;
    if (sheet === 'organization' && isOwnerOrVice) return mutate(`/organizations/${selectedOrg.id}`, 'PUT', { name: normalizeOrganizationName(data.name), accessKey: normalizeAccessKey(data.accessKey) },
      'Organização atualizada. Uma chave alterada passa a valer para novas solicitações.', () => setSheet(null), sheetForm);
    if (sheet === 'committee' && isLeader) return mutate(editingCommittee ? `/committees/${editingCommittee.id}` : `/organizations/${selectedOrg.id}/committees`, editingCommittee ? 'PUT' : 'POST',
      { name: normalizeOrganizationName(data.name), description: data.description.trim() || null }, editingCommittee ? 'Comissão atualizada.' : 'Comissão criada.', () => setSheet(null), sheetForm);
    if (sheet === 'invite' && isOwnerOrVice) return mutate(`/organizations/${selectedOrg.id}/invitations`, 'POST', { email: normalizeEmail(data.email) },
      'Convite enviado. A pessoa tem 24 horas para aceitar ou recusar.', () => setSheet(null), sheetForm);
    if (sheet === 'resubmit' && isOwner) return mutate(`/organizations/${selectedOrg.id}/resubmit`, 'POST', { justification: data.description.trim() },
      'Organização reenviada. Aguarde a nova avaliação do administrador do sistema.', () => setSheet(null), sheetForm);
  });
  const respondOrganizationInvitation = (invitation: OrganizationInvitation, accept: boolean) => mutate(`/organization-invitations/${invitation.id}/respond`, 'POST', { accept }, accept ? 'Convite aceito. Você entrou na organização.' : 'Convite recusado.');
  const respondCommitteeInvitation = (committeeId: number, accept: boolean) => mutate(`/committees/${committeeId}/invitations/respond?accept=${accept}`, 'POST', undefined, accept ? 'Você entrou na comissão.' : 'Convite recusado.');
  const requestCommitteeJoin = (committeeId: number) => mutate(`/committees/${committeeId}/join-requests`, 'POST', undefined, 'Pedido de ingresso enviado. Aguarde a aprovação dos líderes.');
  const changeMemberRole = (member: Member, promote: boolean) => {
    if (!selectedOrg) return;
    const next = promote ? member.role === 'MEMBER' ? 'Subdono' : 'Vice-líder' : member.role === 'ORG_VICE_OWNER' ? 'Subdono' : 'Membro';
    confirm('Alterar papel', `Alterar o papel de ${member.name} para ${next}?`, promote ? 'Elevar' : 'Rebaixar',
      () => mutate(`/organizations/${selectedOrg.id}/members/${member.userId}/${promote ? 'promote' : 'demote'}`, 'POST', undefined, 'Papel atualizado.', () => setSheet(null)));
  };
  const transferLeadership = (member: Member) => {
    if (!selectedOrg || !isOwner || member.userId === authUserId) return;
    confirm('Transferir liderança', `Transferir a liderança de ${selectedOrg.name} para ${member.name}? Você passará a ser membro comum e perderá os poderes de dono.`, 'Transferir',
      () => mutate(`/organizations/${selectedOrg.id}/members/${member.userId}/make-owner`, 'POST', undefined, 'Liderança transferida. Seu papel agora é membro.', () => { setSheet(null); setTab('committees'); setPage('committees'); }));
  };
  const removeMember = (member: Member) => {
    if (!selectedOrg) return;
    confirm('Remover integrante', `Remover ${member.name} desta organização e de suas comissões?`, 'Remover',
      () => mutate(`/organizations/${selectedOrg.id}/members/${member.userId}`, 'DELETE', undefined, 'Integrante removido.', () => setSheet(null)), true);
  };
  const deleteOrganization = () => {
    if (!selectedOrg || !isOwnerOrVice) return;
    confirm('Excluir organização', `Excluir ${selectedOrg.name}? A ação é permanente e apaga as comissões, vínculos, convites e solicitações desta organização. As contas dos usuários permanecem.`, 'Excluir',
      () => mutate(`/organizations/${selectedOrg.id}`, 'DELETE', undefined, 'Organização excluída.', () => { clearSelection(); setTab('mine'); }), true);
  };
  const editCommittee = (committee: Committee) => {
    if (!currentView() || writeLock.current || confirmLock.current) return;
    setEditingCommittee(committee); openSheet('committee', { ...EMPTY_FORM, name: committee.name, description: committee.description ?? '' });
  };
  // Members, invitations and committees only make sense after approval (the server also refuses invitations and committees).
  const warnIfNotApproved = () => {
    if (!selectedOrg || selectedOrg.approved === true) return false;
    alert('Organização não aprovada', ['REJECTED', 'BLOCKED'].includes(selectedOrg.status ?? '')
      ? 'Esta organização foi recusada ou bloqueada pelo administrador do sistema. Membros, convites e comissões não estão disponíveis.'
      : 'Sua organização ainda não foi aprovada pelo administrador do sistema. Membros, convites e comissões ficam disponíveis depois da aprovação.');
    return true;
  };
  const newCommittee = () => {
    if (!currentView() || writeLock.current || confirmLock.current || !selectedOrg || warnIfNotApproved()) return;
    setEditingCommittee(null); openSheet('committee');
  };
  const openMember = (member: Member) => {
    if (!currentView() || writeLock.current || confirmLock.current) return;
    setSelectedMember(member); openSheet('member');
  };
  const deleteCommittee = (committee: Committee) => {
    if (!isOwnerOrVice) return;
    confirm('Excluir comissão', `Excluir ${committee.name} e todos os seus vínculos?`, 'Excluir', () => mutate(`/committees/${committee.id}`, 'DELETE', undefined, 'Comissão excluída.'), true);
  };

  const field = (form: UseFormReturn<Fields>, name: keyof Fields, label: string, placeholder: string, validate?: (value: string) => true | string, multiline = false, icon?: IoniconsIconName) => (
    <Controller control={form.control} name={name} rules={validate ? { validate } : { maxLength: { value: 500, message: 'Use até 500 caracteres.' } }}
      render={({ field: input, fieldState }) => <FormField label={label} required={!!validate} placeholder={placeholder} icon={icon}
        prefix={name === 'accessKey' ? '#' : undefined} value={name === 'accessKey' ? input.value.replace(/^#/, '') : input.value}
        onChangeText={value => { if (currentView() && !writeLock.current) { input.onChange(name === 'accessKey' ? normalizeAccessKey(value) : value); setFormError(''); setJoinMessage(''); } }}
        onBlur={() => { if (currentView() && !writeLock.current) input.onBlur(); }} ref={input.ref} error={fieldState.error?.message} editable={!busy} multiline={multiline}
        autoCorrect={false} autoCapitalize={name === 'accessKey' || name === 'email' ? 'none' : 'sentences'}
        style={multiline ? { minHeight: 90, textAlignVertical: 'top' } : undefined} />}
    />
  );
  const error = (message: string) => message ? <Text accessibilityRole="alert" style={styles.error}>{message}</Text> : null;
  const action = (label: string, onPress: () => void | Promise<void>, icon: IoniconsIconName, danger = false) => {
    const primary = /^(Aceitar convite|Aprovar pedido|Aprovar ingresso)/.test(label);
    const short = /^(Aceitar|Recusar) convite/.test(label) ? label.split(' ')[0]
      : /^(Aprovar|Rejeitar) (pedido|ingresso)/.test(label) ? label.split(' ')[0]
        : /^Transferir para/.test(label) ? 'Transferir'
          : /^Presidente de/.test(label) ? 'Presidente'
          : /^Solicitar entrada em/.test(label) ? 'Solicitar ingresso'
            : /^Remover vínculo com/.test(label) ? 'Remover vínculo' : label;
    const tint = danger ? colors.danger : primary ? '#FFFFFF' : colors.isDark ? '#ADB5EF' : colors.primary;
    return (
    <TouchableOpacity accessibilityRole="button" accessibilityLabel={label} disabled={busy} onPress={onPress}
      style={[styles.action, danger && { borderColor: colors.danger }, primary && { backgroundColor: colors.primary }, busy && { opacity: 0.5 }]}>
      <Ionicons name={icon} size={19} color={tint} /><Text style={[styles.actionText, { color: tint }]}>{short}</Text>
    </TouchableOpacity>
    );
  };
  const avatar = (name: string) => <View style={styles.avatar}><Text style={styles.avatarText}>{name.trim().slice(0, 1).toUpperCase()}</Text></View>;
  const status = (org: MyOrganization) => org.status === 'BLOCKED' ? ['Bloqueada', colors.danger] : org.status === 'REJECTED' ? ['Rejeitada', colors.danger] : org.approved === true ? ['Aprovada', colors.success] : ['Pendente', colors.warning];
  const badge = (text: string, color = colors.primary) => <View style={[styles.badge, { borderColor: color }]}><Text style={[styles.badgeText, { color }]}>{text}</Text></View>;
  const empty = (text: string) => <View style={styles.empty}><Ionicons name="folder-open-outline" size={28} color={colors.subtext} /><Text style={styles.subtitle}>{text}</Text></View>;
  const openOrganization = (org: MyOrganization) => {
    if (!currentView() || writeLock.current || confirmLock.current) return;
    selectOrganization(org); setTab(canManage(org) ? 'manage' : 'committees'); setPage(canManage(org) ? 'overview' : 'committees');
  };
  const back = () => { if (currentView() && !writeLock.current && !confirmLock.current) { setPage('overview'); if (tab === 'committees') setTab('mine'); setFormError(''); } };
  const showPage = (next: Page) => { if (currentView() && !writeLock.current && !confirmLock.current) { setPage(next); setFormError(''); } };
  const requestCount = pendingOrgRequests.length + committeeJoinRequests.length;
  const visibleOrgs = myOrgs.filter(org => matchesSearch(search, org.name, org.accessKey, org.ownerName));
  const visibleMembers = members.filter(member => (memberRole === 'ALL' || member.role === memberRole) && matchesSearch(memberSearch, member.name, member.email));

  const renderInvitations = () => <>
    {error(invitationError)}{invitationError && action('Atualizar convites', loadInvitations, 'refresh-outline')}
    {organizationInvitations.length > 0 && <Text style={styles.sectionLabel}>CONVITES DE ORGANIZAÇÃO</Text>}
    {organizationInvitations.map(invitation => <View key={`org-${invitation.id}`} style={styles.card}>
      <View style={styles.row}>{avatar(invitation.organizationName)}<View style={styles.flex}><Text style={styles.cardTitle}>{invitation.organizationName}</Text>
        <Text style={styles.subtitle}>Convite de {invitation.invitedByName}</Text><Text style={styles.hint}>{expiresLabel(invitation.expiresAt)}</Text></View></View>
      <View style={styles.actions}>{action(`Recusar convite de ${invitation.organizationName}`, () => respondOrganizationInvitation(invitation, false), 'close-outline', true)}
        {action(`Aceitar convite de ${invitation.organizationName}`, () => respondOrganizationInvitation(invitation, true), 'checkmark-outline')}</View>
    </View>)}
    {myInvitations.length > 0 && <Text style={styles.sectionLabel}>CONVITES DE COMISSÃO</Text>}
    {myInvitations.map(invitation => <View key={`committee-${invitation.id}`} style={styles.card}>
      <View style={styles.row}><Ionicons name="mail-outline" size={24} color={colors.primary} /><View style={styles.flex}><Text style={styles.cardTitle}>{invitation.committeeName}</Text>
        <Text style={styles.subtitle}>Convite de {invitation.invitedByName}</Text><Text style={styles.hint}>{myOrgs.find(org => org.id === invitation.organizationId)?.name ?? 'Sua organização'}</Text></View></View>
      <View style={styles.actions}>{action(`Recusar convite de ${invitation.committeeName}`, () => respondCommitteeInvitation(invitation.committeeId, false), 'close-outline', true)}
        {action(`Aceitar convite de ${invitation.committeeName}`, () => respondCommitteeInvitation(invitation.committeeId, true), 'checkmark-outline')}</View>
    </View>)}
    {!invitationError && !organizationInvitations.length && !myInvitations.length && empty('Você não tem convites pendentes.')}
  </>;
  const renderMine = () => <>
    {error(invitationError)}{invitationError && action('Atualizar convites', loadInvitations, 'refresh-outline')}
    {(organizationInvitations.length + myInvitations.length > 0) && <View style={styles.card}><View style={styles.row}><Ionicons name="mail-unread-outline" size={24} color={colors.primary} />
      <View style={styles.flex}><Text style={styles.cardTitle}>Você recebeu convites</Text><Text style={styles.subtitle}>Veja a organização ou comissão antes de responder.</Text></View></View>
      {action(`Ver convites (${organizationInvitations.length + myInvitations.length})`, () => showPage('invitations'), 'mail-outline')}</View>}
    <Text style={styles.sectionLabel}>SUAS ORGANIZAÇÕES ({myOrgs.length})</Text>
    <FormField label="Buscar organização" placeholder="Nome, chave ou responsável" icon="search-outline" value={search} onChangeText={value => { if (currentView()) setSearch(value); }} />
    {loadingData ? <ActivityIndicator color={colors.primary} accessibilityLabel="Carregando organizações" /> : visibleOrgs.map(org => {
      const [label, color] = status(org);
      return <TouchableOpacity key={org.id} accessibilityRole="button" accessibilityLabel={`Acessar ${org.name}`} disabled={busy} style={styles.card} onPress={() => openOrganization(org)}>
        <View style={styles.row}>{avatar(org.name)}<View style={styles.flex}><Text style={styles.cardTitle}>{org.name}</Text><Text style={styles.hint}>{org.accessKey}</Text>
          <View style={styles.badges}>{badge(label, color)}{badge(ROLE_LABELS[org.myRole ?? 'MEMBER'])}</View></View><Ionicons name="chevron-forward" size={22} color={colors.subtext} /></View>
        {org.ownerId === authUserId && ['REJECTED', 'BLOCKED'].includes(org.status ?? '') && <Text style={styles.subtitle}>
          Motivo: {org.moderationReason || 'Não informado no registro anterior.'}</Text>}
      </TouchableOpacity>;
    })}
    {!loadingData && !visibleOrgs.length && empty(search.trim() ? 'Nenhuma organização corresponde à busca.' : 'Entre em uma organização ou crie sua equipe.')}
  </>;
  const renderOverview = () => !selectedOrg ? empty('Selecione uma organização em Minhas.') : <>
    <View style={styles.card}><View style={styles.row}>{avatar(selectedOrg.name)}<View style={styles.flex}><Text style={styles.cardTitle}>{selectedOrg.name}</Text>
      <Text style={styles.subtitle}>Responsável: {selectedOrg.ownerName}</Text></View></View>
      <View style={styles.infoRow}><Text style={styles.subtitle}>Status</Text>{badge(...status(selectedOrg) as [string, string])}</View>
      <View style={styles.infoRow}><Text style={styles.subtitle}>Chave</Text><Text style={styles.value}>{selectedOrg.accessKey}</Text></View>
      <View style={styles.infoRow}><Text style={styles.subtitle}>Seu papel</Text><Text style={styles.value}>{ROLE_LABELS[myRole]}</Text></View>
      {selectedOrg.ownerId === authUserId && ['REJECTED', 'BLOCKED'].includes(selectedOrg.status ?? '') && <View style={styles.reason}>
        <Text style={styles.subtitle}>{selectedOrg.status === 'BLOCKED' ? 'Motivo do bloqueio' : 'Motivo da recusa'}</Text>
        <Text style={styles.reasonText}>{selectedOrg.moderationReason || 'Não informado no registro anterior.'}</Text></View>}
    </View>
    <Text style={styles.sectionLabel}>ATALHOS</Text>
    <View style={styles.tiles}>{(['members', 'requests', 'committees'] as const).map((next, index) => <TouchableOpacity key={next} accessibilityRole="button"
      accessibilityLabel={['Membros', 'Solicitações', 'Comissões'][index]} style={styles.tile} disabled={busy}
      onPress={() => { if (next === 'requests' || !warnIfNotApproved()) showPage(next); }}>
      <Ionicons name={(['people-outline', 'mail-outline', 'layers-outline'] as const)[index]} size={25} color={colors.primary} />
      <Text style={styles.tileLabel}>{['Membros', 'Solicitações', 'Comissões'][index]}</Text><Text style={styles.tileCount}>{detailsLoaded ? [members.length, requestCount, committees.length][index] : '...'}</Text>
    </TouchableOpacity>)}</View>
    {isOwnerOrVice && <View style={styles.actions}>{action('Editar organização', () => openSheet('organization', { ...EMPTY_FORM, name: selectedOrg.name, accessKey: selectedOrg.accessKey }), 'create-outline')}
      {action('Excluir organização', deleteOrganization, 'trash-outline', true)}</View>}
    {isOwnerOrVice && action('Convidar para organização', () => { if (!warnIfNotApproved()) openSheet('invite'); }, 'person-add-outline')}
    {isOwner && selectedOrg.approved === true && action('Transferir liderança', () => openSheet('transfer'), 'swap-horizontal-outline')}
    {isOwner && selectedOrg.status === 'REJECTED' && (selectedOrg.resubmitted
      ? <Text style={styles.hint}>A reavaliação desta organização já foi usada. Para tentar de novo, exclua a organização e crie outra.</Text>
      : action('Reenviar para avaliação', () => openSheet('resubmit'), 'refresh-outline'))}
  </>;
  const renderMembers = () => <>
    <FormField label="Buscar membro" placeholder="Nome ou e-mail" icon="search-outline" value={memberSearch} onChangeText={value => { if (currentView()) setMemberSearch(value); }} />
    <ScrollView horizontal showsHorizontalScrollIndicator={false} style={styles.filters}><View style={styles.row}>
      {(['ALL', 'ORG_OWNER', 'ORG_VICE_OWNER', 'ORG_SUBOWNER', 'MEMBER'] as const).map(role => <TouchableOpacity key={role} accessibilityRole="button"
        accessibilityState={{ selected: memberRole === role }} style={[styles.filter, memberRole === role && styles.filterActive]} onPress={() => { if (currentView()) setMemberRole(role); }}>
        <Text style={[styles.filterText, memberRole === role && { color: '#FFFFFF' }]}>{role === 'ALL' ? 'Todos' : ROLE_LABELS[role]}</Text>
      </TouchableOpacity>)}
    </View></ScrollView>
    {visibleMembers.map(member => <TouchableOpacity key={member.userId} accessibilityRole="button" accessibilityLabel={`Ações de ${member.name}`} disabled={busy} style={styles.card}
      onPress={() => openMember(member)}><View style={styles.row}>{avatar(member.name)}<View style={styles.flex}>
        <Text style={styles.cardTitle}>{member.name}</Text><Text style={styles.subtitle}>{member.email}</Text>{badge(ROLE_LABELS[member.role] ?? 'Membro')}</View>
        <Ionicons name="ellipsis-horizontal" size={22} color={colors.subtext} /></View></TouchableOpacity>)}
    {detailsLoaded && !visibleMembers.length && empty('Nenhum integrante corresponde ao filtro.')}
  </>;
  const renderRequests = () => <>
    {pendingOrgRequests.length > 0 && <Text style={styles.sectionLabel}>ENTRADA NA ORGANIZAÇÃO</Text>}
    {pendingOrgRequests.map(request => <View key={`org-${request.id}`} style={styles.card}><View style={styles.row}>{avatar(request.userName)}<View style={styles.flex}>
      <Text style={styles.cardTitle}>{request.userName}</Text><Text style={styles.subtitle}>{request.userEmail}</Text></View></View>
      <View style={styles.actions}>{action(`Rejeitar pedido de ${request.userName}`, () => mutate(`/access-requests/${request.id}/reject`, 'POST', undefined, 'Solicitação rejeitada.'), 'close-outline', true)}
        {action(`Aprovar pedido de ${request.userName}`, () => mutate(`/access-requests/${request.id}/approve`, 'POST', undefined, 'Integrante aprovado.'), 'checkmark-outline')}</View></View>)}
    {committeeJoinRequests.length > 0 && <Text style={styles.sectionLabel}>INGRESSO EM COMISSÃO</Text>}
    {committeeJoinRequests.map(request => <View key={`committee-${request.id}`} style={styles.card}><View style={styles.row}>{avatar(request.userName)}<View style={styles.flex}>
      <Text style={styles.cardTitle}>{request.userName}</Text><Text style={styles.subtitle}>{request.committeeName}</Text></View></View>
      <View style={styles.actions}>{action(`Rejeitar ingresso de ${request.userName}`, () => mutate(`/committees/join-requests/${request.id}/respond?approve=false`, 'POST', undefined, 'Solicitação rejeitada.'), 'close-outline', true)}
        {action(`Aprovar ingresso de ${request.userName}`, () => mutate(`/committees/join-requests/${request.id}/respond?approve=true`, 'POST', undefined, 'Integrante aprovado na comissão.'), 'checkmark-outline')}</View></View>)}
    {detailsLoaded && !requestCount && empty('Nenhuma solicitação pendente para seu papel.')}
  </>;
  const renderCommittees = () => <>
    {myInvitations.length > 0 && action(`Ver convites (${myInvitations.length})`, () => showPage('invitations'), 'mail-outline')}
    {isLeader && action('Nova comissão', newCommittee, 'add-outline')}
    {committees.map(committee => {
      const link = myLinks.find(row => row.committeeId === committee.id);
      const invitation = myInvitations.find(row => row.committeeId === committee.id);
      return <View key={committee.id} style={[styles.card, styles.committeeCard]}><Text style={styles.cardTitle}>{committee.name}</Text>
        {committee.description ? <Text style={styles.subtitle}>{committee.description}</Text> : null}
        <Text style={styles.hint}>{committee.presidentName ? `Presidente: ${committee.presidentName}` : 'Sem presidente definido'}</Text>
        <View style={styles.badges}>{badge(`${Number.isFinite(committee.totalActiveMembers) ? committee.totalActiveMembers : 0} ativos`, colors.success)}
          {badge(`${Number.isFinite(committee.totalPending) ? committee.totalPending : 0} pendentes`, colors.warning)}
          {link?.membershipStatus === 'ACTIVE' && badge('Você participa', colors.primary)}
          {link?.membershipStatus === 'PENDING_LEADER_APPROVAL' && badge('Seu pedido está pendente', colors.warning)}</View>
        {isLeader ? <View style={styles.actions}>{action(`Editar ${committee.name}`, () => editCommittee(committee), 'create-outline')}
          {action(`Presidente de ${committee.name}`, () => { if (currentView() && !writeLock.current && !confirmLock.current) { cancelReads(); setPresidentCommittee(committee); } }, 'ribbon-outline')}
          {isOwnerOrVice && action(`Excluir ${committee.name}`, () => deleteCommittee(committee), 'trash-outline', true)}</View>
          : invitation ? <View style={styles.actions}>{action(`Recusar convite de ${committee.name}`, () => respondCommitteeInvitation(committee.id, false), 'close-outline', true)}
              {action(`Aceitar convite de ${committee.name}`, () => respondCommitteeInvitation(committee.id, true), 'checkmark-outline')}</View>
            : (!link || !link.linked || link.membershipStatus === 'DECLINED') && action(`Solicitar entrada em ${committee.name}`, () => requestCommitteeJoin(committee.id), 'paper-plane-outline')}
      </View>;
    })}
    {detailsLoaded && !committees.length && empty('Nenhuma comissão cadastrada nesta organização.')}
  </>;
  const canPromote = selectedMember && ((isOwner && ['MEMBER', 'ORG_SUBOWNER'].includes(selectedMember.role)) || (isVice && selectedMember.role === 'MEMBER'));
  const canDemote = selectedMember && ((isOwner && ['ORG_VICE_OWNER', 'ORG_SUBOWNER'].includes(selectedMember.role)) || (isVice && selectedMember.role === 'ORG_SUBOWNER'));
  const canRemove = selectedMember && selectedMember.userId !== authUserId && selectedMember.role !== 'ORG_OWNER'
    && (isOwner || (isVice && selectedMember.role !== 'ORG_VICE_OWNER') || (isSubowner && selectedMember.role === 'MEMBER'));
  const sheetTitle = sheet === 'organization' ? 'Editar organização' : sheet === 'committee' ? editingCommittee ? 'Editar comissão' : 'Nova comissão' : sheet === 'invite' ? 'Convidar integrante' : sheet === 'transfer' ? 'Transferir liderança' : sheet === 'resubmit' ? 'Reenviar para avaliação' : selectedMember?.name ?? 'Integrante';
  const renderSheet = () => <>
    {sheet === 'organization' && <>{field(sheetForm, 'name', 'Nome da organização', 'Digite o nome da organização', validateOrganizationName, false, 'business-outline')}
      {field(sheetForm, 'accessKey', 'Chave de acesso', 'ChaveDaOrganizacao', validateAccessKey, false, 'key-outline')}<Text style={styles.hint}>O # é automático. Maiúsculas e minúsculas são equivalentes. Ao alterar, a chave anterior deixa de valer para novos pedidos.</Text></>}
    {sheet === 'committee' && <>{field(sheetForm, 'name', 'Nome da comissão', 'Ex: Comissão de Eventos 2', validateCommitteeName, false, 'layers-outline')}
      {field(sheetForm, 'description', 'Descrição (opcional)', 'Finalidade desta comissão', undefined, true)}<Text style={styles.hint}>Nome único nesta organização. Letras, espaços, números, '_' e '-'.</Text></>}
    {sheet === 'invite' && <><Text style={styles.subtitle}>Procure uma pessoa já cadastrada pelo nome ou e-mail e toque nela. Ela decide se aceita ou recusa em até 24 horas.</Text>
      {field(sheetForm, 'email', 'Nome ou e-mail da pessoa', 'Digite pelo menos 3 letras', value => isValidEmail(value) || 'Escolha uma pessoa da lista ou digite o e-mail completo.', false, 'search-outline')}
      {candidatesLoading && <ActivityIndicator color={colors.primary} accessibilityLabel="Buscando pessoas" />}
      {error(candidatesError)}
      {candidates.map(candidate => <TouchableOpacity key={candidate.email} accessibilityRole="button" accessibilityLabel={`Escolher ${candidate.name}`} style={styles.card} onPress={() => pickCandidate(candidate)}>
        <View style={styles.row}>{avatar(candidate.name)}<View style={styles.flex}><Text style={styles.cardTitle}>{candidate.name}</Text><Text style={styles.subtitle}>{candidate.email}</Text></View></View></TouchableOpacity>)}
      {!candidatesLoading && !candidatesError && !candidates.length && (inviteDraft ?? '').trim().length >= 3 && (inviteDraft ?? '').trim() !== pickedEmail.current
        && <Text style={styles.hint}>Nenhuma pessoa encontrada. Confira o nome ou digite o e-mail completo.</Text>}</>}
    {sheet === 'transfer' && <><Text style={styles.subtitle}>Escolha um integrante atual. Depois da transferência, você será membro comum.</Text>
      {members.filter(member => member.userId !== authUserId && member.role !== 'ORG_OWNER').map(member => <View key={member.userId} style={styles.card}>
        <Text style={styles.cardTitle}>{member.name}</Text><Text style={styles.subtitle}>{member.email}</Text>
        {action(`Transferir para ${member.name}`, () => transferLeadership(member), 'swap-horizontal-outline')}</View>)}
      {members.filter(member => member.userId !== authUserId && member.role !== 'ORG_OWNER').length === 0 && empty('A organização precisa ter outro integrante para receber a liderança.')}</>}
    {sheet === 'resubmit' && selectedOrg && <><Text style={styles.subtitle}>Motivo da recusa: {selectedOrg.moderationReason || 'Não informado.'}</Text>
      <Text style={styles.hint}>Corrija o que foi apontado (em Editar organização) e explique a correção. A reavaliação pode ser pedida uma única vez: se a organização for recusada de novo, só restará excluí-la.</Text>
      {field(sheetForm, 'description', 'Justificativa', 'Explique o que foi corrigido', value => (value.trim().length >= 3 && value.trim().length <= 500) || 'Escreva de 3 a 500 caracteres.', true)}</>}
    {sheet === 'member' && selectedMember && <><Text style={styles.subtitle}>{selectedMember.email}</Text>{badge(ROLE_LABELS[selectedMember.role] ?? 'Membro')}
      {action('Comissões do integrante', () => openLinks(selectedMember), 'layers-outline')}
      {canPromote && action('Elevar papel', () => changeMemberRole(selectedMember, true), 'arrow-up-circle-outline')}
      {canDemote && action('Rebaixar papel', () => changeMemberRole(selectedMember, false), 'arrow-down-circle-outline')}
      {canRemove && action('Remover integrante', () => removeMember(selectedMember), 'person-remove-outline', true)}</>}
  </>;
  const title = page === 'invitations' ? 'Convites' : tab === 'committees' || page === 'committees' ? 'Comissões' : page === 'members' ? 'Membros' : page === 'requests' ? 'Solicitações' : 'Organizações';
  const nested = page !== 'overview' || tab === 'committees';
  const footer = tab === 'join' ? <View style={styles.footer}>{error(formError)}
    <FormButton label="Solicitar acesso" icon="paper-plane-outline" onPress={handleJoin} loading={busy} />
  </View> : tab === 'create' ? <View style={styles.footer}>{error(formError)}<FormButton label="Criar organização" icon="add-circle-outline" onPress={handleCreate} loading={busy} /></View> : undefined;
  const sheetFooter = sheet === 'organization' || sheet === 'committee' || sheet === 'invite' || sheet === 'resubmit' ? <View style={styles.footer}>{error(formError)}
    <FormButton label={sheet === 'invite' ? 'Enviar convite' : sheet === 'resubmit' ? 'Reenviar para avaliação' : sheet === 'committee' && !editingCommittee ? 'Criar comissão' : 'Salvar alterações'}
      icon={sheet === 'invite' || sheet === 'resubmit' ? 'paper-plane-outline' : 'save-outline'} onPress={handleSheetSave} loading={busy} />
    <FormButton label="Cancelar" secondary onPress={closeSheet} disabled={busy} /></View> : undefined;
  const refreshControl = <RefreshControl refreshing={refreshing} onRefresh={handleRefresh} tintColor={colors.primary} colors={[colors.primary]} enabled={!syncPaused} />;
  return <View style={[styles.container, { paddingTop: insets.top + 12 }]}>
    <View style={styles.header}>{nested && <TouchableOpacity accessibilityRole="button" accessibilityLabel="Voltar" style={styles.iconButton} onPress={back} disabled={busy || confirmingAction}>
      <Ionicons name="arrow-back" size={24} color={colors.primary} /></TouchableOpacity>}
      <View style={styles.flex}><Text style={styles.title}>{title}</Text><Text style={styles.subtitle}>{nested ? selectedOrg?.name ?? 'Seus convites' : 'Entre, crie ou gerencie suas equipes'}</Text></View>
      <TouchableOpacity accessibilityRole="button" accessibilityLabel="Atualizar organizações" style={styles.iconButton} onPress={handleRefresh} disabled={syncPaused}>
        {refreshing ? <ActivityIndicator color={colors.primary} /> : <Ionicons name="refresh-outline" size={22} color={colors.primary} />}</TouchableOpacity>
    </View>
    {!nested && <View style={styles.tabs}>{(['mine', 'join', 'create', ...(isLeader ? ['manage'] : [])] as Tab[]).map(next => <TouchableOpacity key={next}
      accessibilityRole="tab" accessibilityLabel={next === 'mine' ? 'Minhas' : next === 'join' ? 'Entrar' : next === 'create' ? 'Criar' : 'Gerenciar'} accessibilityState={{ selected: tab === next }} disabled={busy || confirmingAction}
      style={[styles.tab, tab === next && styles.tabSelected]} onPress={() => switchTab(next)}><Text style={[styles.tabText, tab === next && { color: colors.primary }]}>
        {next === 'mine' ? 'Minhas' : next === 'join' ? 'Entrar' : next === 'create' ? 'Criar' : 'Gerenciar'}</Text></TouchableOpacity>)}</View>}
    {selectedOrg && (tab === 'manage' || tab === 'committees') && myOrgs.length > 1 && <ScrollView horizontal showsHorizontalScrollIndicator={false} style={styles.selector}>
      <View style={styles.row}>{myOrgs.map(org => <TouchableOpacity key={org.id} accessibilityRole="button" accessibilityLabel={`Selecionar ${org.name}`} disabled={busy || confirmingAction}
        style={[styles.filter, selectedOrg.id === org.id && styles.filterActive]} onPress={() => openOrganization(org)}><Text style={[styles.filterText, selectedOrg.id === org.id && { color: '#FFFFFF' }]}>{org.accessKey}</Text></TouchableOpacity>)}</View>
    </ScrollView>}
    <FormScrollView key={`${tab}:${page}`} contentContainerStyle={styles.content} footer={footer} refreshControl={tab === 'create' || tab === 'join' ? undefined : refreshControl}>
      {error(loadError)}{loadError && action('Tentar atualizar', handleRefresh, 'refresh-outline')}
      {(tab === 'manage' || tab === 'committees') && <>{error(detailsError)}{loadingDetails && !members.length && <ActivityIndicator color={colors.primary} accessibilityLabel="Carregando organização" />}</>}
      {!footer && error(formError)}
      {page === 'invitations' ? renderInvitations() : tab === 'mine' ? renderMine() : tab === 'join' ? <>
        <Text style={styles.cardTitle}>Entrar em uma organização</Text>
        <Text style={styles.subtitle}>Informe a chave e aguarde a aprovação dos responsáveis.</Text>
        {field(joinForm, 'accessKey', 'Chave da organização', 'NomeDaOrganizacao', validateAccessKey, false, 'key-outline')}
        {previewLoading && <ActivityIndicator color={colors.primary} accessibilityLabel="Consultando organização" />}
        {preview && <View style={styles.card}><View style={styles.row}>{avatar(preview.name)}<View style={styles.flex}>
          <Text style={styles.cardTitle}>{preview.name}</Text><Text style={styles.subtitle}>Responsável: {preview.ownerName}</Text></View></View></View>}
        {error(previewError)}
        {joinMessage ? <Text accessibilityLiveRegion="polite" style={[styles.hint, { color: colors.success }]}>{joinMessage}</Text> : null}
      </> : tab === 'create' ? <>
        {field(createForm, 'name', 'Nome da organização', 'Digite o nome da organização', validateOrganizationName, false, 'business-outline')}
        {field(createForm, 'accessKey', 'Chave de acesso', 'NomeSemEspacos', validateAccessKey, false, 'key-outline')}
        <Text style={styles.hint}>O # é adicionado automaticamente à chave.</Text>
        <Text style={styles.hint}>Nome e chave são únicos. Maiúsculas, minúsculas e espaços extras não tornam o nome diferente.</Text>
        <View style={styles.notice}><Ionicons name="time-outline" size={24} color={colors.warning} /><View style={styles.flex}><Text style={styles.cardTitle}>Em análise pelo administrador</Text>
          <Text style={styles.subtitle}>Após criar, aguarde a avaliação. Acompanhe o status em Minhas.</Text></View></View>
      </> : page === 'members' ? renderMembers() : page === 'requests' ? renderRequests() : page === 'committees' || tab === 'committees' ? renderCommittees() : renderOverview()}
    </FormScrollView>
    <Modal visible={!!sheet} transparent animationType="slide" onRequestClose={closeSheet}>
      <View style={styles.overlay}><View style={[styles.sheet, { paddingBottom: insets.bottom, borderColor: colors.border }]}>
        <View style={styles.sheetHandle} /><View style={styles.sheetHeader}><Text style={[styles.cardTitle, styles.flex]}>{sheetTitle}</Text>
          <TouchableOpacity accessibilityRole="button" accessibilityLabel="Fechar formulário" style={styles.iconButton} onPress={closeSheet} disabled={busy}><Ionicons name="close-outline" size={24} color={colors.subtext} /></TouchableOpacity></View>
        <FormScrollView key={sheet ?? 'closed'} contentContainerStyle={styles.content} footer={sheetFooter}>{renderSheet()}{!sheetFooter && error(formError)}</FormScrollView>
      </View></View><ToastHost active={!!sheet} /><ConfirmationHost active={!!sheet} />
    </Modal>
    <Modal visible={linksModalVisible} transparent animationType="slide" onRequestClose={closeLinks}>
      <View style={styles.overlay}><View style={[styles.sheet, { paddingBottom: insets.bottom }]}><View style={styles.sheetHandle} />
        <View style={styles.sheetHeader}><View style={styles.flex}><Text style={styles.cardTitle}>Comissões do integrante</Text><Text style={styles.subtitle}>{selectedMember?.name}</Text></View>
          <TouchableOpacity accessibilityRole="button" accessibilityLabel="Fechar comissões do integrante" style={styles.iconButton} onPress={closeLinks} disabled={busy}><Ionicons name="close-circle-outline" size={24} color={colors.subtext} /></TouchableOpacity></View>
        <FormScrollView contentContainerStyle={styles.content}>{error(formError)}{loadingLinks ? <ActivityIndicator color={colors.primary} /> : memberLinks.map(link => <View key={link.committeeId} style={styles.card}>
          <Text style={styles.cardTitle}>{link.committeeName}</Text><Text style={styles.subtitle}>{link.description ?? ''}</Text>
          {badge(link.membershipStatus === 'ACTIVE' ? 'Membro ativo' : link.membershipStatus === 'PENDING_USER_ACCEPTANCE' ? 'Aguardando aceite' : link.membershipStatus === 'PENDING_LEADER_APPROVAL' ? 'Pedido pendente' : 'Sem vínculo')}
          {isLeader && selectedMember && (link.membershipStatus === 'PENDING_LEADER_APPROVAL' ? <Text style={styles.hint}>Responda ao pedido em Solicitações.</Text> : link.linked && ['ACTIVE', 'PENDING_USER_ACCEPTANCE'].includes(link.membershipStatus ?? '')
            ? action(`Remover vínculo com ${link.committeeName}`, () => confirm('Remover vínculo', `Remover o vínculo de ${selectedMember.name} com ${link.committeeName}?`, 'Remover',
                () => mutate(`/committees/${link.committeeId}/members/${selectedMember.userId}`, 'DELETE', undefined, 'Vínculo removido.'), true), 'remove-circle-outline', true)
            : action(`Convidar para ${link.committeeName}`, () => mutate(`/committees/${link.committeeId}/members/${selectedMember.userId}`, 'POST', undefined,
                data => record(data) && data.status === 'ACTIVE' ? 'Integrante aprovado após seu pedido de ingresso.' : 'Convite enviado. Aguarde o aceite do integrante.'), 'person-add-outline'))}
        </View>)}{!loadingLinks && !memberLinks.length && !formError && empty('Nenhuma comissão cadastrada.')}
          {formError && action('Tentar novamente', () => selectedMember ? loadMemberLinks(selectedMember.userId) : undefined, 'refresh-outline')}
        </FormScrollView></View></View><ToastHost active={linksModalVisible} /><ConfirmationHost active={linksModalVisible} />
    </Modal>
    <PresidentSheet committee={presidentCommittee} onClose={() => setPresidentCommittee(null)} onChanged={handleRefresh} />
  </View>;
}

function createStyles(colors: ThemeColors) {
  return StyleSheet.create({
    container: { flex: 1, backgroundColor: colors.background },
    flex: { flex: 1, minWidth: 0 }, row: { flexDirection: 'row', alignItems: 'center', gap: 12 },
    header: { paddingHorizontal: 20, flexDirection: 'row', alignItems: 'center', gap: 8, marginBottom: 14 },
    title: { fontSize: 25, lineHeight: 32, fontWeight: '700', color: colors.text, flexShrink: 1 },
    subtitle: { fontSize: 13, lineHeight: 20, color: colors.subtext, flexShrink: 1 },
    iconButton: { minWidth: 48, minHeight: 48, alignItems: 'center', justifyContent: 'center' },
    tabs: { marginHorizontal: 20, marginBottom: 16, padding: 4, flexDirection: 'row', borderRadius: 16, backgroundColor: colors.inputBg },
    tab: { flex: 1, minHeight: 48, borderRadius: 12, justifyContent: 'center', alignItems: 'center', paddingHorizontal: 4 },
    tabSelected: { backgroundColor: colors.card, elevation: 2 }, tabText: { fontSize: 13, fontWeight: '600', color: colors.subtext, textAlign: 'center' },
    content: { padding: 20, paddingTop: 4, paddingBottom: 24 },
    sectionLabel: { color: colors.subtext, fontSize: 11, lineHeight: 18, fontWeight: '700', letterSpacing: 0.6, marginBottom: 10, marginTop: 8 },
    card: { padding: 16, backgroundColor: colors.card, borderWidth: 1, borderColor: colors.border, borderRadius: 16, marginBottom: 12 },
    cardTitle: { color: colors.text, fontSize: 15, lineHeight: 22, fontWeight: '700', flexShrink: 1, marginBottom: 3 },
    avatar: { width: 42, height: 42, borderRadius: 21, backgroundColor: colors.isDark ? '#292D45' : '#EBEDF8', alignItems: 'center', justifyContent: 'center' },
    avatarText: { color: colors.isDark ? '#ADB5EF' : colors.primary, fontSize: 17, fontWeight: '700' },
    badges: { flexDirection: 'row', gap: 6, flexWrap: 'wrap', marginTop: 8 }, badge: { alignSelf: 'flex-start', borderWidth: 1, borderRadius: 14, paddingHorizontal: 8, paddingVertical: 3, marginTop: 4 },
    badgeText: { fontSize: 11, lineHeight: 16, fontWeight: '600' },
    hint: { fontSize: 12, lineHeight: 18, color: colors.subtext, marginVertical: 5 },
    error: { fontSize: 13, lineHeight: 19, color: colors.danger, marginBottom: 10 },
    actions: { flexDirection: 'row', gap: 8, flexWrap: 'wrap', marginTop: 8 },
    action: { flexGrow: 1, minWidth: 130, minHeight: 48, paddingVertical: 10, paddingHorizontal: 12, borderWidth: 1, borderColor: colors.primary, borderRadius: 12,
      flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 7, marginBottom: 8 },
    actionText: { color: colors.primary, fontSize: 13, lineHeight: 18, fontWeight: '600', flexShrink: 1, textAlign: 'center' },
    empty: { alignItems: 'center', gap: 10, padding: 20, marginVertical: 10 },
    footer: { backgroundColor: colors.card, padding: 16, paddingHorizontal: 20, borderTopWidth: 1, borderColor: colors.border },
    infoRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', gap: 12, marginTop: 12 },
    value: { color: colors.text, fontSize: 13, fontWeight: '600', flexShrink: 1, textAlign: 'right' },
    reason: { marginTop: 14, paddingTop: 12, borderTopWidth: 1, borderTopColor: colors.border },
    reasonText: { color: colors.text, fontSize: 14, lineHeight: 20, marginTop: 2 },
    tiles: { flexDirection: 'row', gap: 8, marginBottom: 16 }, tile: { flex: 1, minHeight: 118, padding: 10, borderRadius: 16, borderWidth: 1, borderColor: colors.border, backgroundColor: colors.card, alignItems: 'center', justifyContent: 'center', gap: 6 },
    tileLabel: { color: colors.text, fontSize: 11, fontWeight: '600', textAlign: 'center' }, tileCount: { color: colors.text, fontSize: 22, fontWeight: '700' },
    selector: { flexGrow: 0, maxHeight: 64, paddingHorizontal: 20, marginBottom: 12 },
    filters: { marginBottom: 16 }, filter: { minHeight: 48, paddingHorizontal: 14, paddingVertical: 12, justifyContent: 'center', borderRadius: 24, borderWidth: 1, borderColor: colors.border, backgroundColor: colors.card },
    filterActive: { backgroundColor: colors.primary, borderColor: colors.primary }, filterText: { fontSize: 13, lineHeight: 18, fontWeight: '600', color: colors.text },
    notice: { backgroundColor: colors.card, padding: 16, borderWidth: 1, borderColor: colors.warning, borderRadius: 16, flexDirection: 'row', gap: 12, marginTop: 20 },
    committeeCard: { borderLeftWidth: 3, borderLeftColor: colors.primary },
    overlay: { flex: 1, justifyContent: 'flex-end', backgroundColor: 'rgba(0,0,0,0.45)', paddingTop: 48 },
    sheet: { height: '85%', maxHeight: 720, backgroundColor: colors.background, borderTopLeftRadius: 24, borderTopRightRadius: 24, borderWidth: 1, borderColor: colors.border, overflow: 'hidden' },
    sheetHandle: { width: 38, height: 4, backgroundColor: colors.border, borderRadius: 2, alignSelf: 'center', marginTop: 12 },
    sheetHeader: { flexDirection: 'row', gap: 10, alignItems: 'center', paddingHorizontal: 20, paddingVertical: 8 },
  });
}
