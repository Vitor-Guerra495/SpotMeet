import React, { useState, useCallback, useEffect, useMemo } from 'react';
import {
  View, Text, TextInput, TouchableOpacity, StyleSheet,
  ScrollView, ActivityIndicator, Alert, Platform, Modal,
  KeyboardAvoidingView,
} from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useFocusEffect } from '@react-navigation/native';
import { useAuth } from '../context/AuthContext';
import { useTheme, ThemeColors } from '../context/ThemeContext';
import { API_BASE_URL } from '../config/api';
import { showAlert as alert } from '../utils/alerts';
import { useLiveSync } from '../hooks/useLiveSync';
import { notifySync } from '../utils/syncBus';

// Types

interface MyOrganization {
  id: number;
  name: string;
  accessKey: string;
  ownerId: number;
  ownerName: string;
  createdAt: string;
  myRole?: string;
  approved?: boolean;
  status?: string;
}

interface Member {
  userId: number;
  name: string;
  email: string;
  role: string;
}

interface OrganizationAccessRequest {
  id: number;
  userId: number;
  userName: string;
  userEmail: string;
  status: string;
  requestedAt: string;
}

interface CommitteeJoinRequest {
  id: number;
  userId: number;
  userName: string;
  userEmail: string;
  committeeId: number;
  committeeName: string;
  organizationId: number;
  status: string;
  requestedAt: string;
}

interface Committee {
  id: number;
  name: string;
  description: string;
  organizationId: number;
  organizationName: string;
  totalActiveMembers: number;
  totalPending: number;
  createdAt: string;
}

type CommitteeMembershipStatus = 'PENDING_USER_ACCEPTANCE' | 'PENDING_LEADER_APPROVAL' | 'ACTIVE' | 'DECLINED';

interface UserCommitteeLink {
  committeeId: number;
  committeeName: string;
  description: string;
  linked: boolean;
  membershipStatus: CommitteeMembershipStatus | null;
  role: string | null;
  membershipId: number | null;
}

interface PendingInvitation {
  id: number;
  userId: number;
  committeeId: number;
  committeeName: string;
  organizationId: number;
  status: string;
  requestedAt: string;
  invitedByName: string;
}

// Portuguese labels for organization roles returned by the API
const ROLE_LABELS: Record<string, string> = {
  ORG_OWNER: 'Dono / Líder',
  ORG_VICE_OWNER: 'Vice-Líder',
  ORG_SUBOWNER: 'Subdono',
  MEMBER: 'Membro',
};

// Platform helpers and safe decoding (Mobile & Web)

function extractMessage(text: string, fallback: string): string {
  if (!text) return fallback;
  try {
    const json = JSON.parse(text);
    return json.message || json.error || fallback;
  } catch {
    return text.trim() || fallback;
  }
}

/**
 * Pure JavaScript JWT e-mail decoder.
 * Works natively on Android (Hermes), iOS (JavaScriptCore) and Web without external dependencies.
 */
function safeDecodeEmail(token: string | null): string | null {
  if (!token) return null;
  try {
    const parts = token.split('.');
    if (parts.length < 2) return null;
    let base64 = parts[1].replace(/-/g, '+').replace(/_/g, '/');
    while (base64.length % 4) {
      base64 += '=';
    }
    const chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/=';
    let output = '';
    for (let bc = 0, bs = 0, buffer, idx = 0; buffer = base64.charAt(idx++); ~buffer && (bs = bc % 4 ? bs * 64 + buffer : buffer, bc++ % 4) ? output += String.fromCharCode(255 & bs >> (-2 * bc & 6)) : 0) {
      buffer = chars.indexOf(buffer);
    }
    const json = JSON.parse(decodeURIComponent(escape(output)));
    return json.sub || null;
  } catch {
    return null;
  }
}

// Main component

export default function OrganizationScreen() {
  const insets = useSafeAreaInsets();
  const { token, email: authEmail, userId: authUserId } = useAuth();
  const myEmail = authEmail || safeDecodeEmail(token);
  const { colors } = useTheme();
  const styles = useMemo(() => createStyles(colors), [colors]);

  // Internal tabs: 'join' | 'create' | 'manage'
  const [tab, setTab] = useState<'join' | 'create' | 'manage'>('join');

  // Organization data
  const [myOrgs, setMyOrgs] = useState<MyOrganization[]>([]);
  const [selectedOrg, setSelectedOrg] = useState<MyOrganization | null>(null);
  const [members, setMembers] = useState<Member[]>([]);
  const [pendingOrgRequests, setPendingOrgRequests] = useState<OrganizationAccessRequest[]>([]);
  const [committeeJoinRequests, setCommitteeJoinRequests] = useState<CommitteeJoinRequest[]>([]);
  const [loadingData, setLoadingData] = useState(false);
  const [loadingDeleteOrg, setLoadingDeleteOrg] = useState(false);

  // Committees module
  const [committees, setCommittees] = useState<Committee[]>([]);
  const [loadingCommittees, setLoadingCommittees] = useState(false);
  const [showCommitteeForm, setShowCommitteeForm] = useState(false);
  const [newCommitteeName, setNewCommitteeName] = useState('');
  const [newCommitteeDescription, setNewCommitteeDescription] = useState('');
  const [loadingCreateCommittee, setLoadingCreateCommittee] = useState(false);
  // When set, the committee form is editing this committee instead of creating a new one
  const [editingCommitteeId, setEditingCommitteeId] = useState<number | null>(null);

  // Links modal per active member
  const [linksModalVisible, setLinksModalVisible] = useState(false);
  const [selectedMember, setSelectedMember] = useState<Member | null>(null);
  const [memberLinks, setMemberLinks] = useState<UserCommitteeLink[]>([]);
  const [loadingLinksModal, setLoadingLinksModal] = useState(false);

  // Pending invitations of the logged user
  const [myInvitations, setMyInvitations] = useState<PendingInvitation[]>([]);

  // Form: request access to an organization
  const [joinAccessKey, setJoinAccessKey] = useState('');
  const [loadingJoin, setLoadingJoin] = useState(false);

  // Form: create organization
  const [orgName, setOrgName] = useState('');
  // Inline organization edit (owner / vice owner)
  const [showEditOrgForm, setShowEditOrgForm] = useState(false);
  const [editOrgName, setEditOrgName] = useState('');
  const [editOrgAccessKey, setEditOrgAccessKey] = useState('');
  const [loadingEditOrg, setLoadingEditOrg] = useState(false);
  const [orgAccessKey, setOrgAccessKey] = useState('');
  const [loadingCreate, setLoadingCreate] = useState(false);

  // Hierarchy and permissions of the logged user
  const myRole = selectedOrg?.myRole
    || members.find((m) => (myEmail && m.email === myEmail) || (authUserId && m.userId === authUserId))?.role
    || 'MEMBER';

  const isOwner = myRole === 'ORG_OWNER';
  const isViceOwner = myRole === 'ORG_VICE_OWNER';
  const isSubowner = myRole === 'ORG_SUBOWNER';
  const isOwnerOrVice = isOwner || isViceOwner;
  const isRegularMember = !isOwner && !isViceOwner && !isSubowner;

  // Full reset of the local management state
  const resetLocalState = useCallback(() => {
    setSelectedOrg(null);
    setMembers([]);
    setPendingOrgRequests([]);
    setCommitteeJoinRequests([]);
    setCommittees([]);
  }, []);

  // Loads the user's organizations
  const loadMyOrgs = useCallback(async (silent = false) => {
    if (!token) return;
    if (!silent) setLoadingData(true);
    try {
      const res = await fetch(`${API_BASE_URL}/organizations/mine`, {
        headers: { Authorization: `Bearer ${token}` },
      });
      if (res.ok) {
        const data: MyOrganization[] = await res.json();
        setMyOrgs(data);
        if (data.length > 0) {
          setSelectedOrg((prev) => {
            if (!prev) return data[0];
            const stillExists = data.find((o) => o.id === prev.id);
            if (!stillExists) return data[0];
            // State stabilization: only update the reference when something actually changed
            if (
              prev.id === stillExists.id &&
              prev.name === stillExists.name &&
              prev.accessKey === stillExists.accessKey &&
              prev.myRole === stillExists.myRole &&
              prev.approved === stillExists.approved &&
              prev.status === stillExists.status
            ) {
              return prev;
            }
            return stillExists;
          });
        } else {
          resetLocalState();
        }
      } else {
        setMyOrgs([]);
        resetLocalState();
      }
    } catch (err) {
      console.warn('Erro ao carregar organizações:', err);
      if (!silent) {
        setMyOrgs([]);
        resetLocalState();
      }
    } finally {
      if (!silent) setLoadingData(false);
    }
  }, [token, resetLocalState]);

  // Loads pending committee invitations for the logged user
  const loadMyInvitations = useCallback(async () => {
    if (!token) return;
    try {
      const res = await fetch(`${API_BASE_URL}/committees/invitations`, {
        headers: { Authorization: `Bearer ${token}` },
      });
      if (res.ok) {
        setMyInvitations(await res.json());
      }
    } catch {
      console.warn('Erro ao buscar convites de comissões.');
    }
  }, [token]);

  // Loads members and approval requests of the organization
  const loadOrgDetails = useCallback(async (org: MyOrganization, silent = false) => {
    if (!token || !org) return;
    if (!silent) setLoadingData(true);
    try {
      const requests: Promise<Response>[] = [
        fetch(`${API_BASE_URL}/organizations/${org.id}/members`, {
          headers: { Authorization: `Bearer ${token}` },
        }),
      ];

      // Owner and Vice-Owner load organization access requests
      const role = org.myRole || 'MEMBER';
      const canSeePendingOrg = role === 'ORG_OWNER' || role === 'ORG_VICE_OWNER';
      if (canSeePendingOrg) {
        requests.push(
          fetch(`${API_BASE_URL}/organizations/${org.id}/access-requests`, {
            headers: { Authorization: `Bearer ${token}` },
          })
        );
      }

      // Owner, Vice-Owner and Subowner load committee join requests
      const canSeePendingCommittees = role === 'ORG_OWNER' || role === 'ORG_VICE_OWNER' || role === 'ORG_SUBOWNER';
      if (canSeePendingCommittees) {
        requests.push(
          fetch(`${API_BASE_URL}/organizations/${org.id}/committees/join-requests`, {
            headers: { Authorization: `Bearer ${token}` },
          })
        );
      }

      const responses = await Promise.all(requests);

      if (responses[0] && responses[0].ok) {
        setMembers(await responses[0].json());
      } else {
        // On 403, 400 or 404 the user was removed or the org was deleted
        if (responses[0] && (responses[0].status === 403 || responses[0].status === 400 || responses[0].status === 404)) {
          resetLocalState();
          return;
        }
        setMembers([]);
      }

      let idx = 1;
      if (canSeePendingOrg && responses[idx]) {
        if (responses[idx].ok) {
          setPendingOrgRequests(await responses[idx].json());
        } else {
          setPendingOrgRequests([]);
        }
        idx++;
      } else {
        setPendingOrgRequests([]);
      }

      if (canSeePendingCommittees && responses[idx]) {
        if (responses[idx].ok) {
          setCommitteeJoinRequests(await responses[idx].json());
        } else {
          setCommitteeJoinRequests([]);
        }
      } else {
        setCommitteeJoinRequests([]);
      }
    } catch (err) {
      console.warn('Erro ao carregar detalhes da organização:', err);
    } finally {
      if (!silent) setLoadingData(false);
    }
  }, [token, resetLocalState]);

  // Loads the organization committees
  const loadOrgCommittees = useCallback(async (orgId: number, silent = false) => {
    if (!token) return;
    if (!silent) setLoadingCommittees(true);
    try {
      const res = await fetch(`${API_BASE_URL}/organizations/${orgId}/committees`, {
        headers: { Authorization: `Bearer ${token}` },
      });
      if (res.ok) {
        setCommittees(await res.json());
      } else {
        setCommittees([]);
      }
    } catch {
      console.warn('Erro ao carregar comissões.');
    } finally {
      if (!silent) setLoadingCommittees(false);
    }
  }, [token]);

  // Reloads list and invitations when the screen gains focus
  useFocusEffect(
    useCallback(() => {
      loadMyOrgs();
      loadMyInvitations();
    }, [loadMyOrgs, loadMyInvitations])
  );

  // Reloads details only when switching to the manage tab or changing organization
  useEffect(() => {
    if (tab === 'manage' && selectedOrg?.id) {
      loadOrgDetails(selectedOrg);
      loadOrgCommittees(selectedOrg.id);
      loadMyInvitations();
    }
  }, [tab, selectedOrg?.id, loadOrgDetails, loadOrgCommittees, loadMyInvitations]);

  // Silent real-time refresh for the active tab (Mobile & Web)
  useLiveSync(
    useCallback(async () => {
      await loadMyOrgs(true);
      await loadMyInvitations();
      if (tab === 'manage' && selectedOrg?.id) {
        await loadOrgDetails(selectedOrg, true);
        await loadOrgCommittees(selectedOrg.id, true);
      }
    }, [loadMyOrgs, loadMyInvitations, tab, selectedOrg, loadOrgDetails, loadOrgCommittees]),
    {
      intervalMs: 4000,
      eventTypes: [
        'ORGANIZATIONS_MUTATED',
        'MEMBERS_MUTATED',
        'REQUESTS_MUTATED',
        'COMMITTEES_MUTATED',
        'ADMIN_ORGS_MUTATED',
        'VISIBILITY_RESTORED',
      ],
    }
  );

  // Actions: hierarchy and roles (with immediate sync)

  const handlePromoteMember = async (m: Member) => {
    if (!selectedOrg) return;
    const nextRole = m.role === 'MEMBER' ? 'Subdono' : 'Vice-Líder';

    const execute = async () => {
      try {
        const res = await fetch(`${API_BASE_URL}/organizations/${selectedOrg.id}/members/${m.userId}/promote`, {
          method: 'POST',
          headers: { Authorization: `Bearer ${token}` },
        });
        const text = await res.text();
        if (res.ok) {
          alert('Sucesso', `${m.name} foi elevado para ${nextRole}!`);
          notifySync('MEMBERS_MUTATED', { orgId: selectedOrg.id });
          // Refreshes permissions, session and data immediately
          await loadMyOrgs();
          if (selectedOrg) {
            await loadOrgDetails(selectedOrg);
            await loadOrgCommittees(selectedOrg.id);
          }
        } else {
          alert('Acesso Negado', extractMessage(text, 'Falha ao elevar cargo.'));
        }
      } catch {
        alert('Erro de conexão', 'Tente novamente.');
      }
    };

    if (Platform.OS === 'web') {
      if (window.confirm(`Deseja elevar ${m.name} para ${nextRole}?`)) {
        await execute();
      }
    } else {
      Alert.alert('Confirmar Elevação', `Deseja elevar ${m.name} para ${nextRole}?`, [
        { text: 'Cancelar', style: 'cancel' },
        { text: 'Elevar', onPress: execute },
      ]);
    }
  };

  const handleDemoteMember = async (m: Member) => {
    if (!selectedOrg) return;
    const previousRole = m.role === 'ORG_VICE_OWNER' ? 'Subdono' : 'Membro';

    const execute = async () => {
      try {
        const res = await fetch(`${API_BASE_URL}/organizations/${selectedOrg.id}/members/${m.userId}/demote`, {
          method: 'POST',
          headers: { Authorization: `Bearer ${token}` },
        });
        const text = await res.text();
        if (res.ok) {
          alert('Sucesso', `${m.name} foi rebaixado para ${previousRole}.`);
          notifySync('MEMBERS_MUTATED', { orgId: selectedOrg.id });
          // Refreshes permissions, session and data immediately
          await loadMyOrgs();
          if (selectedOrg) {
            await loadOrgDetails(selectedOrg);
            await loadOrgCommittees(selectedOrg.id);
          }
        } else {
          alert('Erro', extractMessage(text, 'Falha ao rebaixar cargo.'));
        }
      } catch {
        alert('Erro de conexão', 'Tente novamente.');
      }
    };

    if (Platform.OS === 'web') {
      if (window.confirm(`Deseja rebaixar ${m.name} para ${previousRole}?`)) {
        await execute();
      }
    } else {
      Alert.alert('Confirmar Rebaixamento', `Deseja rebaixar ${m.name} para ${previousRole}?`, [
        { text: 'Cancelar', style: 'cancel' },
        { text: 'Rebaixar', style: 'destructive', onPress: execute },
      ]);
    }
  };

  // Actions: organization

  const handleRequestAccess = async () => {
    const accessKey = joinAccessKey.trim();
    if (!accessKey.startsWith('#') || accessKey.length < 3) {
      alert('Chave inválida', 'A chave deve começar com "#" e ter ao menos 2 caracteres. Exemplo: #Minerva');
      return;
    }
    setLoadingJoin(true);
    try {
      const res = await fetch(
        `${API_BASE_URL}/access-requests?accessKey=${encodeURIComponent(accessKey)}`,
        {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            Authorization: `Bearer ${token}`,
          },
          body: JSON.stringify({ accessKey }),
        }
      );
      const text = await res.text();
      if (res.status === 201) {
        alert('Solicitação enviada!', `Sua solicitação para "${accessKey}" foi enviada com sucesso!\nAguarde a aprovação do dono.`);
        setJoinAccessKey('');
        notifySync('REQUESTS_MUTATED');
      } else if (res.status === 404) {
        alert('Não encontrada', `Nenhuma organização com a chave "${accessKey}". Lembre-se que é case-sensitive (#Minerva ≠ #minerva).`);
      } else {
        alert('Atenção', extractMessage(text, 'Não foi possível enviar a solicitação.'));
      }
    } catch {
      alert('Erro de conexão', 'Verifique se o servidor está acessível.');
    } finally {
      setLoadingJoin(false);
    }
  };

  const handleCreateOrg = async () => {
    const name = orgName.trim();
    const accessKey = orgAccessKey.trim();
    if (!name) {
      alert('Atenção', 'Informe o nome da organização.');
      return;
    }
    if (!accessKey.match(/^#[A-Za-z0-9_\-]{2,50}$/)) {
      alert('Chave inválida', 'A chave deve começar com "#" seguida de 2 a 50 caracteres (letras, números, _ ou -). Exemplo: #Minerva');
      return;
    }
    setLoadingCreate(true);
    try {
      const res = await fetch(`${API_BASE_URL}/organizations`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${token}`,
        },
        body: JSON.stringify({ name, accessKey }),
      });
      const text = await res.text();
      if (res.status === 201) {
        const newOrg: MyOrganization = JSON.parse(text);
        alert('Sucesso', `Organização "${name}" criada com sucesso!\nChave de acesso: ${accessKey}`);
        setOrgName('');
        setOrgAccessKey('');
        setMyOrgs((prev) => [...prev, newOrg]);
        setSelectedOrg(newOrg);
        setTab('manage');
        notifySync('ORGANIZATIONS_MUTATED', { orgId: newOrg.id });
        notifySync('ADMIN_ORGS_MUTATED');
      } else {
        alert('Erro ao Criar', extractMessage(text, 'Não foi possível criar a organização.'));
      }
    } catch {
      alert('Erro de conexão', 'Verifique se o servidor está acessível.');
    } finally {
      setLoadingCreate(false);
    }
  };

  const handleApproveAccessRequest = async (requestId: number) => {
    if (!selectedOrg) return;
    try {
      const res = await fetch(`${API_BASE_URL}/access-requests/${requestId}/approve`, {
        method: 'POST',
        headers: { Authorization: `Bearer ${token}` },
      });
      const text = await res.text();
      if (res.ok) {
        alert('Sucesso', 'Membro aprovado na organização!');
        notifySync('REQUESTS_MUTATED', { orgId: selectedOrg.id });
        notifySync('MEMBERS_MUTATED', { orgId: selectedOrg.id });
        await loadOrgDetails(selectedOrg);
      } else {
        alert('Erro', extractMessage(text, 'Falha ao aprovar solicitação.'));
      }
    } catch {
      alert('Erro de conexão', 'Tente novamente.');
    }
  };

  const handleRejectAccessRequest = async (requestId: number) => {
    if (!selectedOrg) return;
    try {
      const res = await fetch(`${API_BASE_URL}/access-requests/${requestId}/reject`, {
        method: 'POST',
        headers: { Authorization: `Bearer ${token}` },
      });
      const text = await res.text();
      if (res.ok) {
        alert('Sucesso', 'Solicitação da organização rejeitada.');
        notifySync('REQUESTS_MUTATED', { orgId: selectedOrg.id });
        await loadOrgDetails(selectedOrg);
      } else {
        alert('Erro', extractMessage(text, 'Falha ao rejeitar solicitação.'));
      }
    } catch {
      alert('Erro de conexão', 'Tente novamente.');
    }
  };

  const handleRevokeMember = async (memberId: number) => {
    if (!selectedOrg) return;

    const targetMember = members.find((m) => m.userId === memberId);
    const isMyself = (myEmail && targetMember?.email === myEmail) || (authUserId && memberId === authUserId);

    const execute = async () => {
      try {
        const res = await fetch(`${API_BASE_URL}/organizations/${selectedOrg.id}/members/${memberId}`, {
          method: 'DELETE',
          headers: { Authorization: `Bearer ${token}` },
        });
        const text = await res.text();
        if (res.ok) {
          notifySync('MEMBERS_MUTATED', { orgId: selectedOrg.id });
          notifySync('ORGANIZATIONS_MUTATED', { orgId: selectedOrg.id });
          if (isMyself) {
            alert('Acesso Revogado', 'Você foi removido desta organização.');
            resetLocalState();
            await loadMyOrgs();
          } else {
            alert('Sucesso', 'Acesso do membro revogado da organização.');
            await loadOrgDetails(selectedOrg);
            await loadOrgCommittees(selectedOrg.id);
          }
        } else {
          alert('Erro', extractMessage(text, 'Falha ao revogar acesso.'));
        }
      } catch {
        alert('Erro de conexão', 'Tente novamente.');
      }
    };

    if (Platform.OS === 'web') {
      if (window.confirm('Tem certeza que deseja remover este membro da organização?')) {
        await execute();
      }
    } else {
      Alert.alert('Confirmar', 'Remover este membro da organização?', [
        { text: 'Cancelar', style: 'cancel' },
        { text: 'Remover', style: 'destructive', onPress: execute },
      ]);
    }
  };

  const openEditOrganization = () => {
    if (!selectedOrg) return;
    setEditOrgName(selectedOrg.name);
    setEditOrgAccessKey(selectedOrg.accessKey);
    setShowEditOrgForm(true);
  };

  const handleUpdateOrganization = async () => {
    if (!selectedOrg) return;
    const name = editOrgName.trim();
    const accessKey = editOrgAccessKey.trim();
    if (!name) {
      alert('Atenção', 'Informe o nome da organização.');
      return;
    }
    if (!accessKey.match(/^#[A-Za-z0-9_\-]{2,50}$/)) {
      alert('Chave inválida', 'A chave deve começar com "#" seguida de 2 a 50 caracteres (letras, números, _ ou -). Exemplo: #Minerva');
      return;
    }

    setLoadingEditOrg(true);
    try {
      const res = await fetch(`${API_BASE_URL}/organizations/${selectedOrg.id}`, {
        method: 'PUT',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${token}`,
        },
        body: JSON.stringify({ name, accessKey }),
      });
      const text = await res.text();
      if (res.ok) {
        const updated: MyOrganization = JSON.parse(text);
        const keyChanged = updated.accessKey !== selectedOrg.accessKey;
        alert(
          'Sucesso',
          keyChanged
            ? `Organização atualizada.\nNova chave de acesso: ${updated.accessKey}\nA chave anterior deixa de funcionar para novas solicitações.`
            : 'Organização atualizada.'
        );
        setShowEditOrgForm(false);
        setMyOrgs((prev) => prev.map((o) => (o.id === updated.id ? { ...o, ...updated } : o)));
        setSelectedOrg((prev) => (prev ? { ...prev, ...updated } : prev));
        notifySync('ORGANIZATIONS_MUTATED', { orgId: updated.id });
        notifySync('ADMIN_ORGS_MUTATED');
      } else {
        alert('Erro ao Salvar', extractMessage(text, 'Não foi possível atualizar a organização.'));
      }
    } catch {
      alert('Erro de conexão', 'Verifique se o servidor está acessível.');
    } finally {
      setLoadingEditOrg(false);
    }
  };

  const handleDeleteOrganization = async () => {
    if (!selectedOrg) return;
    const deletedOrgName = selectedOrg.name;

    const execute = async () => {
      setLoadingDeleteOrg(true);
      try {
        const res = await fetch(`${API_BASE_URL}/organizations/${selectedOrg.id}`, {
          method: 'DELETE',
          headers: { Authorization: `Bearer ${token}` },
        });
        const text = await res.text();
        if (res.ok) {
          alert('Organização Excluída', `A organização "${deletedOrgName}" e todos os seus dados foram permanentemente apagados.`);
          notifySync('ORGANIZATIONS_MUTATED');
          notifySync('ADMIN_ORGS_MUTATED');
          // Immediate reset of all local state
          resetLocalState();
          setMyOrgs([]);
          await loadMyOrgs();
          // Stays on the 'manage' tab to show the default empty screen
          setTab('manage');
        } else {
          alert('Acesso Negado', extractMessage(text, 'Não foi possível apagar a organização.'));
        }
      } catch {
        alert('Erro de conexão', 'Verifique o servidor.');
      } finally {
        setLoadingDeleteOrg(false);
      }
    };

    if (Platform.OS === 'web') {
      if (window.confirm(`TEM CERTEZA ABSOLUTA?\n\nDeseja APAGAR TODA A ORGANIZAÇÃO "${deletedOrgName}"?\nEsta ação é irreversível e excluirá todas as comissões, membros e dados vinculados.`)) {
        await execute();
      }
    } else {
      Alert.alert(
        'APAGAR TODA A ORGANIZAÇÃO',
        `Deseja realmente apagar toda a organização "${deletedOrgName}"?\n\nEsta ação é irreversível e apagará todas as comissões, vínculos e solicitações associadas.`,
        [
          { text: 'Cancelar', style: 'cancel' },
          { text: 'APAGAR ORGANIZAÇÃO', style: 'destructive', onPress: execute },
        ]
      );
    }
  };

  // Actions: committees (owner / subowner / member)

  const handleCreateCommittee = async () => {
    if (!selectedOrg) return;
    const name = newCommitteeName.trim();
    if (!name) {
      alert('Atenção', 'Informe o nome da comissão.');
      return;
    }
    if (name.includes(' ')) {
      alert('Nome Inválido', 'O nome da comissão não pode conter espaços. Use "_" ou "-". Exemplo: Financeiro_TI');
      return;
    }
    if (!name.match(/^[A-Za-z0-9_-]{2,50}$/)) {
      alert('Nome Inválido', 'Use de 2 a 50 caracteres alfanuméricos, "_" ou "-".');
      return;
    }

    setLoadingCreateCommittee(true);
    try {
      const res = await fetch(`${API_BASE_URL}/organizations/${selectedOrg.id}/committees`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${token}`,
        },
        body: JSON.stringify({
          name,
          description: newCommitteeDescription.trim() || null,
        }),
      });

      const text = await res.text();
      if (res.status === 201) {
        alert('Sucesso', `Comissão "${name}" criada com sucesso!`);
        closeCommitteeForm();
        notifySync('COMMITTEES_MUTATED', { orgId: selectedOrg.id });
        await loadOrgCommittees(selectedOrg.id);
      } else {
        alert('Erro ao Criar', extractMessage(text, 'Falha ao criar comissão.'));
      }
    } catch {
      alert('Erro de conexão', 'Verifique o servidor.');
    } finally {
      setLoadingCreateCommittee(false);
    }
  };

  // Opens the shared form in edit mode, prefilled with the committee data
  const openEditCommittee = (committee: Committee) => {
    setEditingCommitteeId(committee.id);
    setNewCommitteeName(committee.name);
    setNewCommitteeDescription(committee.description || '');
    setShowCommitteeForm(true);
  };

  const closeCommitteeForm = () => {
    setShowCommitteeForm(false);
    setEditingCommitteeId(null);
    setNewCommitteeName('');
    setNewCommitteeDescription('');
  };

  const handleUpdateCommittee = async () => {
    if (!selectedOrg || editingCommitteeId === null) return;
    const name = newCommitteeName.trim();
    if (!name.match(/^[A-Za-z0-9_-]{2,50}$/)) {
      alert('Nome Inválido', 'Use de 2 a 50 caracteres alfanuméricos, "_" ou "-" (sem espaços).');
      return;
    }

    setLoadingCreateCommittee(true);
    try {
      const res = await fetch(`${API_BASE_URL}/committees/${editingCommitteeId}`, {
        method: 'PUT',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${token}`,
        },
        body: JSON.stringify({
          name,
          description: newCommitteeDescription.trim() || null,
        }),
      });

      const text = await res.text();
      if (res.ok) {
        alert('Sucesso', `Comissão "${name}" atualizada.`);
        closeCommitteeForm();
        notifySync('COMMITTEES_MUTATED', { orgId: selectedOrg.id });
        await loadOrgCommittees(selectedOrg.id);
      } else {
        alert('Erro ao Salvar', extractMessage(text, 'Falha ao atualizar comissão.'));
      }
    } catch {
      alert('Erro de conexão', 'Verifique o servidor.');
    } finally {
      setLoadingCreateCommittee(false);
    }
  };

  const handleDeleteCommittee = async (committeeId: number, committeeName: string) => {
    if (!selectedOrg) return;
    const execute = async () => {
      try {
        const res = await fetch(`${API_BASE_URL}/committees/${committeeId}`, {
          method: 'DELETE',
          headers: { Authorization: `Bearer ${token}` },
        });
        if (res.ok) {
          alert('Sucesso', `Comissão "${committeeName}" excluída.`);
          notifySync('COMMITTEES_MUTATED', { orgId: selectedOrg.id });
          await loadOrgCommittees(selectedOrg.id);
        } else {
          const text = await res.text();
          alert('Erro', extractMessage(text, 'Falha ao excluir comissão.'));
        }
      } catch {
        alert('Erro de conexão', 'Tente novamente.');
      }
    };

    if (Platform.OS === 'web') {
      if (window.confirm(`Excluir a comissão "${committeeName}" e todos os seus vínculos?`)) {
        await execute();
      }
    } else {
      Alert.alert('Confirmar Exclusão', `Deseja realmente excluir a comissão "${committeeName}"?`, [
        { text: 'Cancelar', style: 'cancel' },
        { text: 'Excluir', style: 'destructive', onPress: execute },
      ]);
    }
  };

  const handleRespondCommitteeJoinRequest = async (membershipId: number, approve: boolean) => {
    try {
      const res = await fetch(`${API_BASE_URL}/committees/join-requests/${membershipId}/respond?approve=${approve}`, {
        method: 'POST',
        headers: { Authorization: `Bearer ${token}` },
      });
      const text = await res.text();
      if (res.ok) {
        alert('Sucesso', approve ? 'Membro aprovado na comissão!' : 'Solicitação para comissão rejeitada.');
        if (selectedOrg) {
          notifySync('COMMITTEES_MUTATED', { orgId: selectedOrg.id });
          await loadOrgDetails(selectedOrg);
          await loadOrgCommittees(selectedOrg.id);
        }
      } else {
        alert('Erro', extractMessage(text, 'Falha ao responder solicitação.'));
      }
    } catch {
      alert('Erro de conexão', 'Tente novamente.');
    }
  };

  // Actions: links modal per active member

  const loadMemberLinks = async (userId: number) => {
    if (!selectedOrg || !token) return;
    setLoadingLinksModal(true);
    try {
      const res = await fetch(`${API_BASE_URL}/organizations/${selectedOrg.id}/members/${userId}/committees`, {
        headers: { Authorization: `Bearer ${token}` },
      });
      if (res.ok) {
        setMemberLinks(await res.json());
      } else {
        setMemberLinks([]);
      }
    } catch {
      console.warn('Erro ao carregar vínculos do integrante.');
    } finally {
      setLoadingLinksModal(false);
    }
  };

  const openLinksModal = (m: Member) => {
    setSelectedMember(m);
    setLinksModalVisible(true);
    loadMemberLinks(m.userId);
  };

  const handleAddMemberToCommittee = async (committeeId: number) => {
    if (!selectedMember || !selectedOrg) return;
    try {
      const res = await fetch(`${API_BASE_URL}/committees/${committeeId}/members/${selectedMember.userId}`, {
        method: 'POST',
        headers: { Authorization: `Bearer ${token}` },
      });
      const text = await res.text();
      if (res.status === 201) {
        alert('Convite Enviado', `O integrante ${selectedMember.name} foi adicionado à comissão e agora precisará aceitar o convite.`);
        notifySync('COMMITTEES_MUTATED', { orgId: selectedOrg.id });
        await loadMemberLinks(selectedMember.userId);
        await loadOrgCommittees(selectedOrg.id);
      } else {
        alert('Atenção', extractMessage(text, 'Falha ao adicionar à comissão.'));
      }
    } catch {
      alert('Erro de conexão', 'Tente novamente.');
    }
  };

  const handleRemoveMemberFromCommittee = async (committeeId: number) => {
    if (!selectedMember || !selectedOrg) return;
    try {
      const res = await fetch(`${API_BASE_URL}/committees/${committeeId}/members/${selectedMember.userId}`, {
        method: 'DELETE',
        headers: { Authorization: `Bearer ${token}` },
      });
      const text = await res.text();
      if (res.ok) {
        alert('Sucesso', 'Vínculo do integrante com a comissão foi revogado.');
        notifySync('COMMITTEES_MUTATED', { orgId: selectedOrg.id });
        await loadMemberLinks(selectedMember.userId);
        await loadOrgCommittees(selectedOrg.id);
      } else {
        alert('Erro', extractMessage(text, 'Falha ao remover vínculo.'));
      }
    } catch {
      alert('Erro de conexão', 'Tente novamente.');
    }
  };

  const handleRequestCommitteeJoin = async (committeeId: number) => {
    try {
      const res = await fetch(`${API_BASE_URL}/committees/${committeeId}/join-requests`, {
        method: 'POST',
        headers: { Authorization: `Bearer ${token}` },
      });
      const text = await res.text();
      if (res.status === 201) {
        alert('Solicitação Enviada!', 'Seu pedido para ingressar nesta comissão foi enviado para aprovação dos líderes.');
        if (selectedMember) {
          await loadMemberLinks(selectedMember.userId);
        }
        if (selectedOrg) {
          notifySync('COMMITTEES_MUTATED', { orgId: selectedOrg.id });
          await loadOrgCommittees(selectedOrg.id);
        }
      } else {
        alert('Atenção', extractMessage(text, 'Falha ao solicitar entrada.'));
      }
    } catch {
      alert('Erro de conexão', 'Tente novamente.');
    }
  };

  // Actions: committee invitations of the logged user

  const handleRespondCommitteeInvitation = async (committeeId: number, accept: boolean) => {
    try {
      const res = await fetch(`${API_BASE_URL}/committees/${committeeId}/invitations/respond?accept=${accept}`, {
        method: 'POST',
        headers: { Authorization: `Bearer ${token}` },
      });
      const text = await res.text();
      if (res.ok) {
        alert('Sucesso', accept ? 'Você agora é membro ativo da comissão!' : 'Convite recusado.');
        notifySync('COMMITTEES_MUTATED');
        await loadMyInvitations();
        if (selectedOrg) {
          await loadOrgCommittees(selectedOrg.id);
        }
      } else {
        alert('Erro', extractMessage(text, 'Falha ao responder convite.'));
      }
    } catch {
      alert('Erro de conexão', 'Tente novamente.');
    }
  };

  // Render: "Join organization" tab

  const renderJoin = () => (
    <View style={styles.card}>
      <Text style={styles.cardTitle}>Solicitar Entrada</Text>
      <Text style={styles.cardSubtitle}>
        Informe a chave exata da organização que deseja ingressar.
        A busca é estritamente case-sensitive: <Text style={styles.accessKeyExample}>#Minerva</Text> ≠ <Text style={styles.accessKeyExample}>#minerva</Text>
      </Text>

      <Text style={styles.label}>Chave da Organização</Text>
      <TextInput
        style={styles.input}
        placeholder="#NomeDaOrganizacao"
        placeholderTextColor="#666"
        autoCapitalize="none"
        autoCorrect={false}
        value={joinAccessKey}
        onChangeText={setJoinAccessKey}
      />

      <TouchableOpacity
        style={[styles.primaryButton, loadingJoin && styles.buttonDisabled]}
        onPress={handleRequestAccess}
        disabled={loadingJoin}
      >
        {loadingJoin ? <ActivityIndicator color="white" /> : (
          <>
            <Ionicons name="paper-plane-outline" size={18} color="white" style={{ marginRight: 8 }} />
            <Text style={styles.primaryButtonText}>Enviar Solicitação</Text>
          </>
        )}
      </TouchableOpacity>

      {myOrgs.length > 0 && (
        <View style={{ marginTop: 24 }}>
          <Text style={styles.sectionTitle}>Suas Organizações ({myOrgs.length})</Text>
          {myOrgs.map((org) => (
            <View key={org.id} style={styles.orgItem}>
              <View style={{ flex: 1 }}>
                <Text style={styles.orgName}>{org.name}</Text>
                <Text style={styles.orgAccessKey}>{org.accessKey}</Text>
                <Text style={styles.orgOwner}>Dono: {org.ownerName}</Text>
              </View>
              <TouchableOpacity
                style={styles.manageBtn}
                onPress={() => {
                  setSelectedOrg(org);
                  setTab('manage');
                }}
              >
                <Ionicons name="settings-outline" size={20} color="#5C6BC0" />
              </TouchableOpacity>
            </View>
          ))}
        </View>
      )}
    </View>
  );

  // Render: "Create organization" tab

  const renderCreate = () => (
    <View style={styles.card}>
      <Text style={styles.cardTitle}>Criar Organização</Text>
      <Text style={styles.cardSubtitle}>
        Você será o dono (ORG_OWNER) com permissão para gerenciar a organização e suas comissões.
      </Text>

      <Text style={styles.label}>Nome da Organização</Text>
      <TextInput
        style={styles.input}
        placeholder="Ex: Equipe Minerva"
        placeholderTextColor="#666"
        value={orgName}
        onChangeText={setOrgName}
      />

      <Text style={styles.label}>Chave de Acesso</Text>
      <TextInput
        style={styles.input}
        placeholder="#NomeSemEspacos"
        placeholderTextColor="#666"
        autoCapitalize="none"
        autoCorrect={false}
        value={orgAccessKey}
        onChangeText={setOrgAccessKey}
      />
      <Text style={styles.hint}>
        A chave é permanente e case-sensitive. Escolha com cuidado.{'\n'}
        Formato obrigatório: # seguido de 2–50 caracteres (letras, números, _ ou -).
      </Text>

      <TouchableOpacity
        style={[styles.primaryButton, loadingCreate && styles.buttonDisabled]}
        onPress={handleCreateOrg}
        disabled={loadingCreate}
      >
        {loadingCreate ? <ActivityIndicator color="white" /> : (
          <>
            <Ionicons name="add-circle-outline" size={18} color="white" style={{ marginRight: 8 }} />
            <Text style={styles.primaryButtonText}>Criar Organização</Text>
          </>
        )}
      </TouchableOpacity>
    </View>
  );

  // Render: "Manage" tab

  const renderManage = () => {
    // Visual reset: with no organization or after removal, render the default empty state
    if (!selectedOrg || myOrgs.length === 0) {
      return (
        <View style={styles.card}>
          <Text style={styles.cardTitle}>Painel de Gestão</Text>
          <Text style={styles.cardSubtitle}>
            Você ainda não possui nenhuma organização ativa. Crie uma na aba "Criar" ou solicite entrada em "Entrar" para gerenciar membros e comissões.
          </Text>
        </View>
      );
    }

    return (
      <View>
        {/* Pending invitations banner for the logged user */}
        {myInvitations.length > 0 && (
          <View style={styles.invitationBanner}>
            <View style={{ flexDirection: 'row', alignItems: 'center', marginBottom: 8 }}>
              <Ionicons name="mail-unread-outline" size={20} color="#FFB300" style={{ marginRight: 8 }} />
              <Text style={styles.invitationBannerTitle}>Convites de Comissões para Você ({myInvitations.length})</Text>
            </View>
            {myInvitations.map((c) => (
              <View key={c.id} style={styles.invitationItem}>
                <View style={{ flex: 1 }}>
                  <Text style={styles.invitationItemName}>{c.committeeName}</Text>
                  <Text style={styles.invitationItemSub}>Convidado por: {c.invitedByName || 'Líder'}</Text>
                </View>
                <View style={{ flexDirection: 'row', gap: 8 }}>
                  <TouchableOpacity
                    style={[styles.invitationActionBtn, { backgroundColor: '#388E3C' }]}
                    onPress={() => handleRespondCommitteeInvitation(c.committeeId, true)}
                  >
                    <Ionicons name="checkmark" size={16} color="white" />
                    <Text style={styles.invitationBtnText}>Aceitar</Text>
                  </TouchableOpacity>
                  <TouchableOpacity
                    style={[styles.invitationActionBtn, { backgroundColor: '#C62828' }]}
                    onPress={() => handleRespondCommitteeInvitation(c.committeeId, false)}
                  >
                    <Ionicons name="close" size={16} color="white" />
                    <Text style={styles.invitationBtnText}>Recusar</Text>
                  </TouchableOpacity>
                </View>
              </View>
            ))}
          </View>
        )}

        {/* Organization selector (when there is more than one) */}
        {myOrgs.length > 1 && (
          <ScrollView horizontal showsHorizontalScrollIndicator={false} style={{ marginBottom: 12 }}>
            {myOrgs.map((org) => (
              <TouchableOpacity
                key={org.id}
                style={[styles.orgChip, selectedOrg.id === org.id && styles.orgChipSelected]}
                onPress={() => setSelectedOrg(org)}
              >
                <Text style={[styles.orgChipText, selectedOrg.id === org.id && styles.orgChipTextSelected]}>
                  {org.accessKey}
                </Text>
              </TouchableOpacity>
            ))}
          </ScrollView>
        )}

        {/* Organization info card */}
        <View style={styles.card}>
          <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'flex-start', gap: 8 }}>
            <View style={{ flex: 1, minWidth: 0, marginRight: 8 }}>
              <Text style={styles.cardTitle} numberOfLines={1} ellipsizeMode="tail">{selectedOrg.name}</Text>
              <Text style={styles.orgAccessKey}>{selectedOrg.accessKey}</Text>
              <Text style={styles.orgOwner} numberOfLines={1} ellipsizeMode="tail">Dono: {selectedOrg.ownerName}</Text>
              <Text style={[styles.roleBadge, { marginTop: 4 }]}>
                Seu papel: <Text style={{ color: colors.primary, fontWeight: 'bold' }}>
                  {isOwner ? ROLE_LABELS.ORG_OWNER :
                   isViceOwner ? ROLE_LABELS.ORG_VICE_OWNER :
                   isSubowner ? ROLE_LABELS.ORG_SUBOWNER : 'Membro Comum'}
                </Text>
              </Text>
            </View>
            <View style={{ flexDirection: 'row', alignItems: 'center', gap: 4 }}>
              {/* Owners and Vice-Owners can edit the organization data */}
              {isOwnerOrVice && (
                <TouchableOpacity
                  style={styles.refreshBtn}
                  onPress={() => (showEditOrgForm ? setShowEditOrgForm(false) : openEditOrganization())}
                >
                  <Ionicons name={showEditOrgForm ? 'close-outline' : 'create-outline'} size={18} color="#5C6BC0" />
                </TouchableOpacity>
              )}
              <TouchableOpacity
                style={styles.refreshBtn}
                onPress={() => {
                  loadMyOrgs();
                  loadOrgDetails(selectedOrg);
                  loadOrgCommittees(selectedOrg.id);
                  loadMyInvitations();
                }}
              >
                <Ionicons name="refresh-outline" size={18} color="#5C6BC0" />
              </TouchableOpacity>
            </View>
          </View>

          {/* Expandable organization edit form */}
          {showEditOrgForm && isOwnerOrVice && (
            <View style={styles.formNewCommittee}>
              <Text style={styles.label}>Nome da Organização</Text>
              <TextInput
                style={styles.input}
                placeholder="Ex: Minerva Consultoria"
                placeholderTextColor="#666"
                value={editOrgName}
                onChangeText={setEditOrgName}
              />

              <Text style={styles.label}>Chave de Acesso</Text>
              <TextInput
                style={styles.input}
                placeholder="#Minerva"
                placeholderTextColor="#666"
                autoCapitalize="none"
                autoCorrect={false}
                value={editOrgAccessKey}
                onChangeText={setEditOrgAccessKey}
              />
              <Text style={styles.hint}>
                A chave é case-sensitive. Ao trocá-la, a chave antiga deixa de funcionar para novas solicitações de acesso.
              </Text>

              <TouchableOpacity
                style={[styles.primaryButton, loadingEditOrg && styles.buttonDisabled]}
                onPress={handleUpdateOrganization}
                disabled={loadingEditOrg}
              >
                {loadingEditOrg ? <ActivityIndicator color="white" /> : (
                  <>
                    <Ionicons name="save-outline" size={18} color="white" style={{ marginRight: 8 }} />
                    <Text style={styles.primaryButtonText}>Salvar Alterações</Text>
                  </>
                )}
              </TouchableOpacity>
            </View>
          )}
        </View>

        {loadingData && <ActivityIndicator color="#5C6BC0" style={{ marginVertical: 16 }} />}

        {/* Blocked or pending organization notice */}
        {(selectedOrg.approved === false || selectedOrg.status === 'BLOCKED' || selectedOrg.status === 'PENDING' || selectedOrg.status === 'REJECTED') ? (
          <View style={styles.cardBlocked}>
            <Ionicons
              name={selectedOrg.status === 'PENDING' ? 'time-outline' : 'ban'}
              size={42}
              color={selectedOrg.status === 'PENDING' ? '#FFB74D' : '#EF5350'}
            />
            <Text style={styles.blockedTitle}>
              {selectedOrg.status === 'PENDING'
                ? 'Organização Pendente de Aprovação'
                : 'Organização Bloqueada pelo Administrador'}
            </Text>
            <Text style={styles.blockedDescription}>
              {selectedOrg.status === 'PENDING'
                ? 'Esta organização foi criada e está aguardando validação pela equipe do SysAdmin. A gestão de comissões e membros será liberada automaticamente após a aprovação.'
                : 'Esta organização foi bloqueada pela administração do SpotMeet. Todas as operações estão suspensas. Para regularizar a situação, entre em contato com o suporte ou administrador do sistema.'}
            </Text>

            {/* Only the delete option is allowed */}
            {isOwnerOrVice && (
              <TouchableOpacity
                style={styles.btnDeleteBlocked}
                onPress={handleDeleteOrganization}
                disabled={loadingDeleteOrg}
              >
                <Ionicons name="trash-outline" size={18} color="white" style={{ marginRight: 8 }} />
                <Text style={styles.btnDeleteText}>
                  {loadingDeleteOrg ? 'Excluindo...' : 'Excluir Organização'}
                </Text>
              </TouchableOpacity>
            )}
          </View>
        ) : (
          <>
        {isOwnerOrVice && (
          <View style={styles.card}>
            <Text style={styles.cardTitle}>
              Pedidos de Entrada na Organização ({pendingOrgRequests.length})
            </Text>
            {pendingOrgRequests.length === 0 ? (
              <Text style={styles.cardSubtitle}>Nenhum pedido de entrada na organização pendente.</Text>
            ) : (
              pendingOrgRequests.map((s) => (
                <View key={s.id} style={styles.memberRow}>
                  <View style={styles.memberInfo}>
                    <Text style={styles.memberName} numberOfLines={1} ellipsizeMode="tail">{s.userName}</Text>
                    <Text style={styles.memberEmail} numberOfLines={1} ellipsizeMode="tail">{s.userEmail}</Text>
                  </View>
                  <View style={styles.actionButtons}>
                    <TouchableOpacity
                      style={[styles.actionBtn, styles.approveBtn]}
                      onPress={() => handleApproveAccessRequest(s.id)}
                    >
                      <Ionicons name="checkmark" size={16} color="white" />
                    </TouchableOpacity>
                    <TouchableOpacity
                      style={[styles.actionBtn, styles.rejectBtn]}
                      onPress={() => handleRejectAccessRequest(s.id)}
                    >
                      <Ionicons name="close" size={16} color="white" />
                    </TouchableOpacity>
                  </View>
                </View>
              ))
            )}
          </View>
        )}

        {/* 2. Committee approvals area (Owner, Vice-Owner and Subowner) */}
        {!isRegularMember && (
          <View style={styles.card}>
            <Text style={styles.cardTitle}>
              Solicitações de Entrada em Comissões ({committeeJoinRequests.length})
            </Text>
            {committeeJoinRequests.length === 0 ? (
              <Text style={styles.cardSubtitle}>Nenhuma solicitação de comissão pendente de aprovação.</Text>
            ) : (
              committeeJoinRequests.map((sc) => (
                <View key={sc.id} style={styles.memberRow}>
                  <View style={styles.memberInfo}>
                    <Text style={styles.memberName} numberOfLines={1} ellipsizeMode="tail">{sc.userName}</Text>
                    <Text style={styles.memberEmail} numberOfLines={1} ellipsizeMode="tail">{sc.userEmail}</Text>
                    <Text style={[styles.roleTag, { color: '#2196F3', marginTop: 2 }]} numberOfLines={1} ellipsizeMode="tail">
                      Comissão: <Text style={{ fontWeight: 'bold' }}>{sc.committeeName}</Text>
                    </Text>
                  </View>
                  <View style={styles.actionButtons}>
                    <TouchableOpacity
                      style={[styles.actionBtn, styles.approveBtn]}
                      onPress={() => handleRespondCommitteeJoinRequest(sc.id, true)}
                    >
                      <Ionicons name="checkmark" size={16} color="white" />
                    </TouchableOpacity>
                    <TouchableOpacity
                      style={[styles.actionBtn, styles.rejectBtn]}
                      onPress={() => handleRespondCommitteeJoinRequest(sc.id, false)}
                    >
                      <Ionicons name="close" size={16} color="white" />
                    </TouchableOpacity>
                  </View>
                </View>
              ))
            )}
          </View>
        )}

        {/* 3. Active members (with role promotion and links) */}
        <View style={styles.card}>
          <Text style={styles.cardTitle}>
            Integrantes Ativos ({members.length})
          </Text>
          {members.length === 0 ? (
            <Text style={styles.cardSubtitle}>Nenhum membro ativo.</Text>
          ) : (
            members.map((m) => (
              <View key={m.userId} style={styles.memberRow}>
                <View style={styles.memberInfo}>
                  <View style={styles.memberHeaderRow}>
                    <Text style={styles.memberName} numberOfLines={1} ellipsizeMode="tail">{m.name}</Text>
                    <View style={[
                      styles.roleBadgePill,
                      m.role === 'ORG_OWNER' && styles.roleBadgeOwner,
                      m.role === 'ORG_VICE_OWNER' && styles.roleBadgeVice,
                      m.role === 'ORG_SUBOWNER' && styles.roleBadgeSubowner,
                      m.role === 'MEMBER' && styles.roleBadgeMember,
                    ]}>
                      <Text style={[
                        styles.roleBadgePillText,
                        m.role === 'ORG_OWNER' && { color: '#E91E63' },
                        m.role === 'ORG_VICE_OWNER' && { color: '#FF9800' },
                        m.role === 'ORG_SUBOWNER' && { color: '#2196F3' },
                        m.role === 'MEMBER' && { color: colors.subtext },
                      ]}>
                        {ROLE_LABELS[m.role] || ROLE_LABELS.MEMBER}
                      </Text>
                    </View>
                  </View>
                  <Text style={styles.memberEmail} numberOfLines={1} ellipsizeMode="tail">{m.email}</Text>
                </View>
                <View style={styles.actionButtons}>
                  {/* Promotion: Member -> Subowner (allowed for Owners and Vice-Owners) */}
                  {m.role === 'MEMBER' && isOwnerOrVice && (
                    <TouchableOpacity
                      style={[styles.actionBtn, { backgroundColor: '#1976D2' }]}
                      onPress={() => handlePromoteMember(m)}
                    >
                      <Ionicons name="arrow-up-circle-outline" size={17} color="white" />
                    </TouchableOpacity>
                  )}

                  {/* Promotion: Subowner -> Vice-Owner (restricted to the Owner only) */}
                  {m.role === 'ORG_SUBOWNER' && isOwner && (
                    <TouchableOpacity
                      style={[styles.actionBtn, { backgroundColor: '#FFA000' }]}
                      onPress={() => handlePromoteMember(m)}
                    >
                      <Ionicons name="arrow-up-circle-outline" size={17} color="white" />
                    </TouchableOpacity>
                  )}

                  {/* Demotion: Vice-Owner -> Subowner (Owner only) */}
                  {m.role === 'ORG_VICE_OWNER' && isOwner && (
                    <TouchableOpacity
                      style={[styles.actionBtn, { backgroundColor: '#5D4037' }]}
                      onPress={() => handleDemoteMember(m)}
                    >
                      <Ionicons name="arrow-down-circle-outline" size={17} color="white" />
                    </TouchableOpacity>
                  )}

                  {/* Demotion: Subowner -> Member (Owner or Vice-Owner) */}
                  {m.role === 'ORG_SUBOWNER' && isOwnerOrVice && (
                    <TouchableOpacity
                      style={[styles.actionBtn, { backgroundColor: '#5D4037' }]}
                      onPress={() => handleDemoteMember(m)}
                    >
                      <Ionicons name="arrow-down-circle-outline" size={17} color="white" />
                    </TouchableOpacity>
                  )}

                  {/* Committees button (opens the modal) */}
                  <TouchableOpacity
                    style={[styles.actionBtn, styles.committeeLinkBtn]}
                    onPress={() => openLinksModal(m)}
                  >
                    <Ionicons name="layers-outline" size={16} color="white" />
                  </TouchableOpacity>

                  {/* Revoke member from organization button */}
                  {m.role !== 'ORG_OWNER' && (
                    (isOwner && m.email !== myEmail) ||
                    (isViceOwner && m.role !== 'ORG_VICE_OWNER' && m.email !== myEmail) ||
                    (isSubowner && m.role === 'MEMBER')
                  ) && (
                    <TouchableOpacity
                      style={[styles.actionBtn, styles.revokeBtn]}
                      onPress={() => handleRevokeMember(m.userId)}
                    >
                      <Ionicons name="person-remove-outline" size={15} color="white" />
                    </TouchableOpacity>
                  )}
                </View>
              </View>
            ))
          )}
        </View>

        {/* 4. Committees section (right below active members) */}
        <View style={styles.card}>
          <View style={styles.cardHeaderWithBtn}>
            <Text style={styles.cardTitleFlex} numberOfLines={1} ellipsizeMode="tail">
              Comissões ({committees.length})
            </Text>
            {/* Owners, Vice-Owners and Subowners can create committees */}
            {!isRegularMember && (
              <TouchableOpacity
                style={styles.btnNewCommittee}
                onPress={() => (showCommitteeForm ? closeCommitteeForm() : setShowCommitteeForm(true))}
              >
                <Ionicons name={showCommitteeForm ? 'close-outline' : 'add-outline'} size={17} color="white" />
                <Text style={styles.btnNewCommitteeText}>{showCommitteeForm ? 'Fechar' : '+ Nova Comissão'}</Text>
              </TouchableOpacity>
            )}
          </View>

          {/* Expandable committee creation form */}
          {showCommitteeForm && !isRegularMember && (
            <View style={styles.formNewCommittee}>
              {editingCommitteeId !== null && (
                <Text style={styles.hint}>Editando a comissão selecionada.</Text>
              )}
              <Text style={styles.label}>Nome da Comissão (sem espaços)</Text>
              <TextInput
                style={styles.input}
                placeholder="Ex: Financeiro_TI, RH-Geral"
                placeholderTextColor="#666"
                autoCapitalize="none"
                autoCorrect={false}
                value={newCommitteeName}
                onChangeText={setNewCommitteeName}
              />
              <Text style={styles.hint}>Use letras, números, '_' ou '-' (sem espaços).</Text>

              <Text style={styles.label}>Descrição (opcional)</Text>
              <TextInput
                style={styles.input}
                placeholder="Finalidade desta comissão..."
                placeholderTextColor="#666"
                value={newCommitteeDescription}
                onChangeText={setNewCommitteeDescription}
              />

              <TouchableOpacity
                style={[styles.primaryButton, loadingCreateCommittee && styles.buttonDisabled]}
                onPress={editingCommitteeId !== null ? handleUpdateCommittee : handleCreateCommittee}
                disabled={loadingCreateCommittee}
              >
                {loadingCreateCommittee ? <ActivityIndicator color="white" /> : (
                  <>
                    <Ionicons
                      name={editingCommitteeId !== null ? 'save-outline' : 'add-circle-outline'}
                      size={18}
                      color="white"
                      style={{ marginRight: 8 }}
                    />
                    <Text style={styles.primaryButtonText}>
                      {editingCommitteeId !== null ? 'Salvar Alterações' : 'Criar Comissão'}
                    </Text>
                  </>
                )}
              </TouchableOpacity>
            </View>
          )}

          {loadingCommittees && <ActivityIndicator color="#5C6BC0" style={{ marginVertical: 12 }} />}

          {/* Committees list */}
          {committees.length === 0 ? (
            <Text style={styles.cardSubtitle}>
              Nenhuma comissão cadastrada.
            </Text>
          ) : (
            committees.map((c) => (
              <View key={c.id} style={styles.committeeCard}>
                <View style={{ flex: 1 }}>
                  <View style={{ flexDirection: 'row', alignItems: 'center' }}>
                    <Text style={styles.committeeName}>{c.name}</Text>
                  </View>
                  {c.description ? <Text style={styles.committeeDescription}>{c.description}</Text> : null}
                  <View style={styles.committeeBadges}>
                    <View style={styles.badgeActive}>
                      <Ionicons name="people-outline" size={13} color="#4CAF50" style={{ marginRight: 4 }} />
                      <Text style={styles.badgeTextActive}>{c.totalActiveMembers} ativos</Text>
                    </View>
                    {c.totalPending > 0 && (
                      <View style={styles.badgePending}>
                        <Ionicons name="time-outline" size={13} color="#FFB300" style={{ marginRight: 4 }} />
                        <Text style={styles.badgeTextPending}>{c.totalPending} pendentes</Text>
                      </View>
                    )}
                  </View>
                </View>

                {/* Committee actions: edit (leaders), delete (Owner and Vice-Owner) or request to join (regular member) */}
                {!isRegularMember && (
                  <TouchableOpacity
                    style={styles.btnEditCommittee}
                    onPress={() => openEditCommittee(c)}
                  >
                    <Ionicons name="create-outline" size={18} color="#5C6BC0" />
                  </TouchableOpacity>
                )}
                {isOwnerOrVice ? (
                  <TouchableOpacity
                    style={styles.btnDeleteCommittee}
                    onPress={() => handleDeleteCommittee(c.id, c.name)}
                  >
                    <Ionicons name="trash-outline" size={18} color="#F44336" />
                  </TouchableOpacity>
                ) : isRegularMember ? (
                  <TouchableOpacity
                    style={[styles.modalActionBtn, { backgroundColor: '#1976D2' }]}
                    onPress={() => handleRequestCommitteeJoin(c.id)}
                  >
                    <Ionicons name="paper-plane-outline" size={14} color="white" style={{ marginRight: 4 }} />
                    <Text style={styles.modalActionBtnText}>Solicitar</Text>
                  </TouchableOpacity>
                ) : null}
              </View>
            ))
          )}
        </View>

        {/* 5. Organization deletion (Owners and Vice-Owners only) */}
        {isOwnerOrVice && (
          <View style={[styles.card, { borderColor: '#D32F2F', borderWidth: 1, marginTop: 12 }]}>
            <Text style={[styles.cardTitle, { color: '#EF5350' }]}>
              Zona de Perigo
            </Text>
            <Text style={styles.cardSubtitle}>
              A exclusão da organização é permanente e irreversível. Todos os vínculos, comissões, solicitações e dados dos integrantes serão permanentemente apagados.
            </Text>

            <TouchableOpacity
              style={[styles.btnDeleteWholeOrg, loadingDeleteOrg && styles.buttonDisabled]}
              onPress={handleDeleteOrganization}
              disabled={loadingDeleteOrg}
            >
              {loadingDeleteOrg ? (
                <ActivityIndicator color="white" />
              ) : (
                <>
                  <Ionicons name="trash" size={18} color="white" style={{ marginRight: 8 }} />
                  <Text style={styles.btnDeleteWholeOrgText}>APAGAR TODA A ORGANIZAÇÃO</Text>
                </>
              )}
            </TouchableOpacity>
          </View>
        )}

        {/* 6. Links modal per active member */}
        <Modal
          visible={linksModalVisible}
          transparent
          animationType="fade"
          onRequestClose={() => setLinksModalVisible(false)}
        >
          <View style={styles.modalOverlay}>
            <View style={styles.modalContainer}>
              <View style={styles.modalHeader}>
                <View style={{ flex: 1 }}>
                  <Text style={styles.modalTitle}>Comissões do Integrante</Text>
                  {selectedMember && (
                    <Text style={styles.modalSubtitle}>{selectedMember.name} ({selectedMember.email})</Text>
                  )}
                </View>
                <TouchableOpacity
                  style={styles.modalCloseBtn}
                  onPress={() => setLinksModalVisible(false)}
                >
                  <Ionicons name="close-circle-outline" size={26} color="#A0A0A0" />
                </TouchableOpacity>
              </View>

              {loadingLinksModal ? (
                <ActivityIndicator color="#5C6BC0" style={{ marginVertical: 30 }} />
              ) : (
                <ScrollView style={{ maxHeight: 400 }} showsVerticalScrollIndicator={false}>
                  {memberLinks.length === 0 ? (
                    <Text style={styles.modalEmpty}>
                      Nenhuma comissão cadastrada nesta organização.
                    </Text>
                  ) : (
                    memberLinks
                      // Regular members only see the committees other users are linked to
                      .filter((v) => {
                        if (isRegularMember && selectedMember?.email !== myEmail) {
                          return v.linked && v.membershipStatus === 'ACTIVE';
                        }
                        return true;
                      })
                      .map((v) => (
                        <View key={v.committeeId} style={styles.modalCommitteeItem}>
                          <View style={{ flex: 1, marginRight: 8 }}>
                            <Text style={styles.modalCommitteeName}>{v.committeeName}</Text>
                            {v.description ? (
                              <Text style={styles.modalCommitteeDescription} numberOfLines={1}>{v.description}</Text>
                            ) : null}

                            {/* Membership status */}
                            {v.linked && v.membershipStatus === 'ACTIVE' ? (
                              <View style={styles.tagStatusActive}>
                                <Text style={styles.tagStatusActiveText}>Membro Ativo</Text>
                              </View>
                            ) : v.linked && v.membershipStatus === 'PENDING_USER_ACCEPTANCE' ? (
                              <View style={styles.tagStatusPending}>
                                <Text style={styles.tagStatusPendingText}>Aguardando aceite do usuário</Text>
                              </View>
                            ) : v.linked && v.membershipStatus === 'PENDING_LEADER_APPROVAL' ? (
                              <View style={styles.tagStatusRequested}>
                                <Text style={styles.tagStatusRequestedText}>Solicitou entrada</Text>
                              </View>
                            ) : (
                              <View style={styles.tagStatusUnlinked}>
                                <Text style={styles.tagStatusUnlinkedText}>Não vinculado</Text>
                              </View>
                            )}
                          </View>

                          {/* Modal actions: Owner, Vice-Owner or Subowner manage the links */}
                          {!isRegularMember ? (
                            v.linked && (v.membershipStatus === 'ACTIVE' || v.membershipStatus === 'PENDING_USER_ACCEPTANCE') ? (
                              <TouchableOpacity
                                style={[styles.modalActionBtn, { backgroundColor: '#C62828' }]}
                                onPress={() => handleRemoveMemberFromCommittee(v.committeeId)}
                              >
                                <Ionicons name="remove-circle-outline" size={16} color="white" style={{ marginRight: 4 }} />
                                <Text style={styles.modalActionBtnText}>
                                  {v.membershipStatus === 'ACTIVE' ? 'Remover' : 'Cancelar'}
                                </Text>
                              </TouchableOpacity>
                            ) : (
                              <TouchableOpacity
                                style={[styles.modalActionBtn, { backgroundColor: '#5C6BC0' }]}
                                onPress={() => handleAddMemberToCommittee(v.committeeId)}
                              >
                                <Ionicons name="add-circle-outline" size={16} color="white" style={{ marginRight: 4 }} />
                                <Text style={styles.modalActionBtnText}>Adicionar</Text>
                              </TouchableOpacity>
                            )
                          ) : (
                            /* A regular member viewing their own links can request to join */
                            selectedMember?.email === myEmail && !v.linked && (
                              <TouchableOpacity
                                style={[styles.modalActionBtn, { backgroundColor: '#1976D2' }]}
                                onPress={() => handleRequestCommitteeJoin(v.committeeId)}
                              >
                                <Ionicons name="paper-plane-outline" size={14} color="white" style={{ marginRight: 4 }} />
                                <Text style={styles.modalActionBtnText}>Solicitar</Text>
                              </TouchableOpacity>
                            )
                          )}
                        </View>
                      ))
                  )}
                  {isRegularMember && selectedMember?.email !== myEmail &&
                   memberLinks.filter((v) => v.linked && v.membershipStatus === 'ACTIVE').length === 0 && (
                    <Text style={styles.modalEmpty}>
                      Este integrante ainda não está vinculado a nenhuma comissão ativa.
                    </Text>
                  )}
                </ScrollView>
              )}
            </View>
          </View>
        </Modal>
        </>
        )}
      </View>
    );
  };

  return (
    <KeyboardAvoidingView behavior="padding" style={[styles.container, { paddingTop: insets.top + 12 }]}>
      {/* Header */}
      <View style={styles.header}>
        <View style={styles.headerLeft}>
          <View style={styles.videoIcon}>
            <Ionicons name="business" size={18} color="white" />
          </View>
          <Text style={styles.headerTitle}>Gestão de Organizações</Text>
        </View>
      </View>

      {/* Internal tab navigation */}
      <View style={styles.tabBar}>
        {(['join', 'create', 'manage'] as const).map((t) => (
          <TouchableOpacity
            key={t}
            style={[styles.tabItem, tab === t && styles.tabItemActive]}
            onPress={() => setTab(t)}
          >
            <Text style={[styles.tabText, tab === t && styles.tabTextActive]}>
              {t === 'join' ? 'Entrar' : t === 'create' ? 'Criar' : 'Gerenciar'}
            </Text>
          </TouchableOpacity>
        ))}
      </View>

      <ScrollView
        showsVerticalScrollIndicator={false}
        contentContainerStyle={{ paddingBottom: 40 }}
      >
        {tab === 'join' && renderJoin()}
        {tab === 'create' && renderCreate()}
        {tab === 'manage' && renderManage()}
      </ScrollView>
    </KeyboardAvoidingView>
  );
}

// Dynamic styles (light / dark)

function createStyles(colors: ThemeColors) {
  const S = colors.primary;

  return StyleSheet.create({
    container: { flex: 1, backgroundColor: colors.background, paddingHorizontal: Platform.OS === 'web' ? 20 : 14, paddingTop: Platform.OS === 'web' ? 40 : 12, maxWidth: 700, width: '100%', alignSelf: 'center' },
    header: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 },
    headerLeft: { flexDirection: 'row', alignItems: 'center' },
    videoIcon: { backgroundColor: S, padding: 8, borderRadius: 15, marginRight: 10 },
    headerTitle: { color: colors.text, fontSize: 20, fontWeight: 'bold' },

    tabBar: { flexDirection: 'row', backgroundColor: colors.card, borderRadius: 12, marginBottom: 16, padding: 4, borderWidth: 1, borderColor: colors.border },
    tabItem: { flex: 1, paddingVertical: 10, alignItems: 'center', borderRadius: 10 },
    tabItemActive: { backgroundColor: S },
    tabText: { color: colors.subtext, fontWeight: 'bold', fontSize: 13 },
    tabTextActive: { color: 'white' },

    card: { backgroundColor: colors.card, borderRadius: 15, padding: 16, marginBottom: 16, borderWidth: 1, borderColor: colors.border },
    cardTitle: { color: colors.text, fontSize: 16, fontWeight: 'bold', marginBottom: 6 },
    cardTitleFlex: { color: colors.text, fontSize: 16, fontWeight: 'bold', flexShrink: 1, marginRight: 6 },
    cardHeaderWithBtn: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 8, marginBottom: 12 },
    cardSubtitle: { color: colors.subtext, fontSize: 13, marginBottom: 12, lineHeight: 20 },
    sectionTitle: { color: colors.text, fontSize: 14, fontWeight: 'bold', marginBottom: 12 },

    label: { color: colors.text, fontSize: 14, fontWeight: 'bold', marginBottom: 8, marginTop: 12 },
    input: { backgroundColor: colors.inputBg, color: colors.text, borderRadius: 10, padding: 14, borderWidth: 1, borderColor: colors.border, marginBottom: 4 },
    hint: { color: colors.subtext, fontSize: 12, marginBottom: 12, lineHeight: 18 },

    primaryButton: { backgroundColor: S, borderRadius: 10, paddingVertical: 14, flexDirection: 'row', justifyContent: 'center', alignItems: 'center', marginTop: 10 },
    primaryButtonText: { color: 'white', fontWeight: 'bold', fontSize: 15 },
    buttonDisabled: { opacity: 0.6 },

    accessKeyExample: { color: S, fontWeight: 'bold' },

    orgItem: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', backgroundColor: colors.itemBg, borderRadius: 10, padding: 14, marginBottom: 10, borderWidth: 1, borderColor: colors.border },
    orgName: { color: colors.text, fontWeight: 'bold', fontSize: 15 },
    orgAccessKey: { color: S, fontSize: 13, marginTop: 2, fontWeight: 'bold' },
    orgOwner: { color: colors.subtext, fontSize: 12, marginTop: 2 },
    roleBadge: { color: colors.subtext, fontSize: 12 },
    manageBtn: { padding: 8, backgroundColor: colors.isDark ? '#222' : '#E0E0E0', borderRadius: 8 },
    refreshBtn: { padding: 8, backgroundColor: colors.inputBg, borderRadius: 8 },

    orgChip: { backgroundColor: colors.inputBg, borderRadius: 20, paddingHorizontal: 16, paddingVertical: 8, marginRight: 8, borderWidth: 1, borderColor: colors.border },
    orgChipSelected: { backgroundColor: S, borderColor: S },
    orgChipText: { color: colors.subtext, fontWeight: 'bold', fontSize: 13 },
    orgChipTextSelected: { color: 'white' },

    memberRow: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', paddingVertical: 10, borderBottomWidth: 1, borderBottomColor: colors.border },
    memberInfo: { flex: 1, marginRight: 14, minWidth: 0, justifyContent: 'center' },
    memberHeaderRow: { flexDirection: 'row', alignItems: 'center', gap: 6, flexWrap: 'wrap', marginBottom: 2 },
    memberName: { color: colors.text, fontWeight: 'bold', fontSize: 14 },
    memberEmail: { color: colors.subtext, fontSize: 12 },
    roleTag: { color: S, fontSize: 11, fontWeight: 'bold', marginTop: 3 },
    roleBadgePill: { paddingHorizontal: 6, paddingVertical: 2, borderRadius: 6, borderWidth: 1 },
    roleBadgePillText: { fontSize: 10, fontWeight: 'bold' },
    roleBadgeOwner: { backgroundColor: 'rgba(233, 30, 99, 0.12)', borderColor: 'rgba(233, 30, 99, 0.4)' },
    roleBadgeVice: { backgroundColor: 'rgba(255, 152, 0, 0.12)', borderColor: 'rgba(255, 152, 0, 0.4)' },
    roleBadgeSubowner: { backgroundColor: 'rgba(33, 150, 243, 0.12)', borderColor: 'rgba(33, 150, 243, 0.4)' },
    roleBadgeMember: { backgroundColor: colors.isDark ? '#2A2A2A' : '#ECEFF1', borderColor: colors.border },

    actionButtons: { flexDirection: 'row', gap: 4, alignItems: 'center', flexShrink: 0 },
    actionBtn: { width: 30, height: 30, borderRadius: 15, justifyContent: 'center', alignItems: 'center' },
    approveBtn: { backgroundColor: '#388E3C' },
    rejectBtn: { backgroundColor: '#C62828' },
    revokeBtn: { backgroundColor: '#BF360C' },
    committeeLinkBtn: { backgroundColor: '#5C6BC0' },

    // Committees section
    btnNewCommittee: { flexDirection: 'row', alignItems: 'center', backgroundColor: S, paddingHorizontal: 10, paddingVertical: 6, borderRadius: 8, flexShrink: 0 },
    btnNewCommitteeText: { color: 'white', fontWeight: 'bold', fontSize: 12, marginLeft: 4 },
    formNewCommittee: { backgroundColor: colors.itemBg, padding: 14, borderRadius: 10, marginBottom: 14, borderWidth: 1, borderColor: colors.border },
    committeeCard: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', backgroundColor: colors.itemBg, padding: 14, borderRadius: 10, marginBottom: 8, borderWidth: 1, borderColor: colors.border },
    committeeName: { color: colors.text, fontWeight: 'bold', fontSize: 15 },
    committeeDescription: { color: colors.subtext, fontSize: 12, marginTop: 2 },
    committeeBadges: { flexDirection: 'row', gap: 8, marginTop: 6 },
    badgeActive: { flexDirection: 'row', alignItems: 'center', backgroundColor: 'rgba(76, 175, 80, 0.15)', paddingHorizontal: 8, paddingVertical: 3, borderRadius: 6 },
    badgeTextActive: { color: '#4CAF50', fontSize: 11, fontWeight: 'bold' },
    badgePending: { flexDirection: 'row', alignItems: 'center', backgroundColor: 'rgba(255, 179, 0, 0.15)', paddingHorizontal: 8, paddingVertical: 3, borderRadius: 6 },
    badgeTextPending: { color: '#FFB300', fontSize: 11, fontWeight: 'bold' },
    btnDeleteCommittee: { padding: 8 },
    btnEditCommittee: { padding: 8 },

    // Delete organization button
    btnDeleteWholeOrg: {
      backgroundColor: '#D32F2F',
      borderRadius: 10,
      paddingVertical: 14,
      flexDirection: 'row',
      justifyContent: 'center',
      alignItems: 'center',
      marginTop: 8,
    },
    btnDeleteWholeOrgText: {
      color: 'white',
      fontWeight: 'bold',
      fontSize: 14,
      letterSpacing: 0.5,
    },

    // Invitation banner for the logged user
    invitationBanner: { backgroundColor: colors.isDark ? 'rgba(255, 179, 0, 0.12)' : 'rgba(255, 179, 0, 0.18)', borderWidth: 1, borderColor: '#FFB300', borderRadius: 12, padding: 16, marginBottom: 16 },
    invitationBannerTitle: { color: '#F57C00', fontWeight: 'bold', fontSize: 15 },
    invitationItem: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', backgroundColor: colors.card, padding: 12, borderRadius: 8, marginTop: 8, borderWidth: 1, borderColor: colors.border },
    invitationItemName: { color: colors.text, fontWeight: 'bold', fontSize: 14 },
    invitationItemSub: { color: colors.subtext, fontSize: 12, marginTop: 2 },
    invitationActionBtn: { flexDirection: 'row', alignItems: 'center', paddingHorizontal: 10, paddingVertical: 6, borderRadius: 6 },
    invitationBtnText: { color: 'white', fontWeight: 'bold', fontSize: 12, marginLeft: 4 },

    // Links modal
    modalOverlay: { flex: 1, backgroundColor: 'rgba(0,0,0,0.75)', justifyContent: 'center', alignItems: 'center', padding: 20 },
    modalContainer: { backgroundColor: colors.card, width: '100%', maxWidth: 550, borderRadius: 16, padding: 20, borderWidth: 1, borderColor: colors.border },
    modalHeader: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16, borderBottomWidth: 1, borderBottomColor: colors.border, paddingBottom: 12 },
    modalTitle: { color: colors.text, fontSize: 18, fontWeight: 'bold' },
    modalSubtitle: { color: S, fontSize: 13, marginTop: 2 },
    modalCloseBtn: { padding: 4 },
    modalEmpty: { color: colors.subtext, fontStyle: 'italic', textAlign: 'center', marginVertical: 20 },
    modalCommitteeItem: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', backgroundColor: colors.itemBg, padding: 12, borderRadius: 10, marginBottom: 8, borderWidth: 1, borderColor: colors.border },
    modalCommitteeName: { color: colors.text, fontWeight: 'bold', fontSize: 14 },
    modalCommitteeDescription: { color: colors.subtext, fontSize: 12, marginTop: 2 },
    modalActionBtn: { flexDirection: 'row', alignItems: 'center', paddingHorizontal: 12, paddingVertical: 8, borderRadius: 8 },
    modalActionBtnText: { color: 'white', fontWeight: 'bold', fontSize: 12 },

    tagStatusActive: { alignSelf: 'flex-start', backgroundColor: 'rgba(76, 175, 80, 0.15)', paddingHorizontal: 6, paddingVertical: 2, borderRadius: 4, marginTop: 4 },
    tagStatusActiveText: { color: '#4CAF50', fontSize: 11, fontWeight: 'bold' },
    tagStatusPending: { alignSelf: 'flex-start', backgroundColor: 'rgba(255, 179, 0, 0.15)', paddingHorizontal: 6, paddingVertical: 2, borderRadius: 4, marginTop: 4 },
    tagStatusPendingText: { color: '#FFB300', fontSize: 11, fontWeight: 'bold' },
    tagStatusRequested: { alignSelf: 'flex-start', backgroundColor: 'rgba(33, 150, 243, 0.15)', paddingHorizontal: 6, paddingVertical: 2, borderRadius: 4, marginTop: 4 },
    tagStatusRequestedText: { color: '#2196F3', fontSize: 11, fontWeight: 'bold' },
    tagStatusUnlinked: { alignSelf: 'flex-start', backgroundColor: 'rgba(160, 160, 160, 0.15)', paddingHorizontal: 6, paddingVertical: 2, borderRadius: 4, marginTop: 4 },
    tagStatusUnlinkedText: { color: colors.subtext, fontSize: 11 },

    // Blocked or pending organization card
    cardBlocked: {
      backgroundColor: colors.isDark ? '#1E1414' : '#FFEBEE',
      borderColor: '#C62828',
      borderWidth: 1,
      borderRadius: 14,
      padding: 20,
      alignItems: 'center',
      marginBottom: 16,
    },
    blockedTitle: {
      color: colors.isDark ? '#FF8A80' : '#C62828',
      fontSize: 16,
      fontWeight: 'bold',
      marginTop: 10,
      textAlign: 'center',
    },
    blockedDescription: {
      color: colors.isDark ? '#CCCCCC' : '#424242',
      fontSize: 13,
      lineHeight: 20,
      marginTop: 8,
      textAlign: 'center',
      marginBottom: 16,
    },
    btnDeleteBlocked: {
      backgroundColor: '#C62828',
      borderRadius: 8,
      paddingVertical: 12,
      paddingHorizontal: 20,
      flexDirection: 'row',
      alignItems: 'center',
      justifyContent: 'center',
      width: '100%',
    },
    btnDeleteText: {
      color: 'white',
      fontWeight: 'bold',
      fontSize: 14,
    },
  });
}