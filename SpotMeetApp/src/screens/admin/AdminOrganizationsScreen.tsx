import React, { useState, useEffect, useCallback } from 'react';
import {
  View,
  Text,
  StyleSheet,
  TouchableOpacity,
  FlatList,
  TextInput,
  ActivityIndicator,
  Platform,
  RefreshControl,
  KeyboardAvoidingView,
} from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useFocusEffect } from '@react-navigation/native';
import { useAuth } from '../../context/AuthContext';
import { API_BASE_URL } from '../../config/api';
import { showAlert, confirmAction } from '../../utils/alerts';
import { useLiveSync } from '../../hooks/useLiveSync';
import { notifySync } from '../../utils/syncBus';

export interface OrganizationItem {
  id: number;
  name: string;
  accessKey: string;
  ownerId: number | null;
  ownerName: string;
  ownerEmailMasked: string;
  cnpjMasked: string;
  approved: boolean;
  status?: string;
  createdAt: string;
  totalMembers: number;
  totalCommittees: number;
}

type AdminTab = 'all' | 'pending';

// Portuguese labels for organization status values returned by the API
const STATUS_LABELS: Record<string, string> = {
  APPROVED: 'Aprovada',
  REJECTED: 'Recusada',
  BLOCKED: 'Bloqueada',
  PENDING: 'Pendente',
};

export default function AdminOrganizationsScreen({ route }: any) {
  const insets = useSafeAreaInsets();
  const { token, role } = useAuth();
  const initialTab: AdminTab = route.params?.initialTab || 'all';

  const [activeTab, setActiveTab] = useState<AdminTab>(initialTab);
  const [organizations, setOrganizations] = useState<OrganizationItem[]>([]);
  const [pendingOrgs, setPendingOrgs] = useState<OrganizationItem[]>([]);
  const [search, setSearch] = useState('');
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [processingId, setProcessingId] = useState<number | null>(null);

  const isAdmin = role === 'ADMIN' || role === 'SYSADMIN';

  // Syncs when the route passes a new param
  useEffect(() => {
    if (route.params?.initialTab) {
      setActiveTab(route.params.initialTab);
    }
  }, [route.params?.initialTab]);

  // Loads all organizations and pending ones in parallel
  const loadData = useCallback(async (silent = false) => {
    if (!token || !isAdmin) {
      setLoading(false);
      return;
    }
    if (!silent) setLoading(true);

    try {
      const headers = {
        Authorization: `Bearer ${token}`,
        'Content-Type': 'application/json',
      };

      const [resAll, resPending] = await Promise.all([
        fetch(`${API_BASE_URL}/admin/organizations`, { method: 'GET', headers }),
        fetch(`${API_BASE_URL}/admin/organizations/pending`, { method: 'GET', headers }),
      ]);

      if (resAll.status === 403 || resAll.status === 401) {
        if (!silent) showAlert('Acesso Negado', 'Permissão de Administrador necessária.');
        return;
      }

      if (resAll.ok) {
        const allData: OrganizationItem[] = await resAll.json();
        setOrganizations(allData);
      }

      if (resPending.ok) {
        const pendingData: OrganizationItem[] = await resPending.json();
        setPendingOrgs(pendingData);
      }
    } catch (err: any) {
      if (!silent) {
        const msg = err.message?.includes('fetch') || err.message?.includes('Network')
          ? 'Não foi possível conectar ao servidor. Verifique se o backend está em execução.'
          : err.message || 'Erro ao carregar organizações.';
        showAlert('Erro', msg);
      }
    } finally {
      if (!silent) setLoading(false);
      setRefreshing(false);
    }
  }, [token, isAdmin]);

  useFocusEffect(
    useCallback(() => {
      loadData();
    }, [loadData])
  );

  // Silent real-time sync on the active tab
  useLiveSync(
    useCallback(() => {
      loadData(true);
    }, [loadData]),
    {
      intervalMs: 4000,
      eventTypes: ['ADMIN_ORGS_MUTATED', 'ORGANIZATIONS_MUTATED', 'VISIBILITY_RESTORED'],
    }
  );

  // Actions: toggle overall status (approve / block)
  const toggleOverallStatus = (org: OrganizationItem) => {
    const newStatus = !org.approved;
    const actionText = newStatus ? 'aprovar' : 'bloquear';

    confirmAction(
      'Confirmar Ação',
      `Deseja realmente ${actionText} a organização "${org.name}"?`,
      async () => {
        setProcessingId(org.id);
        try {
          const res = await fetch(`${API_BASE_URL}/admin/organizations/${org.id}/status`, {
            method: 'PATCH',
            headers: {
              Authorization: `Bearer ${token}`,
              'Content-Type': 'application/json',
            },
            body: JSON.stringify({
              approved: newStatus,
              justification: 'Alteração executada via Painel SysAdmin Unificado',
            }),
          });

          if (!res.ok) {
            const errorMsg = await res.text();
            throw new Error(errorMsg || 'Falha ao atualizar o status da organização.');
          }

          const updatedOrg: OrganizationItem = await res.json();
          setOrganizations((prev) =>
            prev.map((item) => (item.id === org.id ? updatedOrg : item))
          );
          notifySync('ADMIN_ORGS_MUTATED', { orgId: org.id });
          notifySync('ORGANIZATIONS_MUTATED', { orgId: org.id });
          showAlert(
            'Sucesso',
            `Status da organização atualizado para: ${newStatus ? 'Aprovada' : 'Bloqueada'}`
          );
          loadData();
        } catch (err: any) {
          showAlert('Erro', err.message || 'Não foi possível alterar o status.');
        } finally {
          setProcessingId(null);
        }
      },
      newStatus ? 'Aprovar' : 'Bloquear',
      !newStatus
    );
  };

  // Actions: approve in the LGPD queue
  const handleApproveQueue = (org: OrganizationItem) => {
    confirmAction(
      'Confirmar Aprovação',
      `Deseja APROVAR a organização "${org.name}" (${org.accessKey})?\nOs usuários poderão ingressar e criar comissões.`,
      async () => {
        setProcessingId(org.id);
        try {
          const res = await fetch(`${API_BASE_URL}/admin/organizations/${org.id}/approve`, {
            method: 'POST',
            headers: {
              Authorization: `Bearer ${token}`,
              'Content-Type': 'application/json',
            },
          });

          if (!res.ok) {
            const errorMsg = await res.text();
            throw new Error(errorMsg || 'Falha ao aprovar organização.');
          }

          notifySync('ADMIN_ORGS_MUTATED', { orgId: org.id });
          notifySync('ORGANIZATIONS_MUTATED', { orgId: org.id });
          setPendingOrgs((prev) => prev.filter((o) => o.id !== org.id));
          setOrganizations((prev) =>
            prev.map((o) => (o.id === org.id ? { ...o, approved: true, status: 'APPROVED' } : o))
          );
          showAlert('Sucesso', `A organização "${org.name}" foi aprovada e já está ativa.`);
          await loadData(true);
        } catch (err: any) {
          showAlert('Erro', err.message || 'Não foi possível aprovar a organização.');
        } finally {
          setProcessingId(null);
        }
      },
      'Aprovar'
    );
  };

  // Actions: reject in the LGPD queue
  const handleRejectQueue = (org: OrganizationItem) => {
    confirmAction(
      'Confirmar Recusa',
      `Deseja RECUSAR a organização "${org.name}" (${org.accessKey})?`,
      async () => {
        setProcessingId(org.id);
        try {
          const res = await fetch(`${API_BASE_URL}/admin/organizations/${org.id}/reject`, {
            method: 'POST',
            headers: {
              Authorization: `Bearer ${token}`,
              'Content-Type': 'application/json',
            },
            body: JSON.stringify({ reason: 'Rejeitada pela moderação do SysAdmin' }),
          });

          if (!res.ok) {
            const errorMsg = await res.text();
            throw new Error(errorMsg || 'Falha ao recusar organização.');
          }

          notifySync('ADMIN_ORGS_MUTATED', { orgId: org.id });
          notifySync('ORGANIZATIONS_MUTATED', { orgId: org.id });
          setPendingOrgs((prev) => prev.filter((o) => o.id !== org.id));
          setOrganizations((prev) =>
            prev.map((o) => (o.id === org.id ? { ...o, approved: false, status: 'REJECTED' } : o))
          );
          showAlert('Organização Recusada', `A solicitação da organização "${org.name}" foi recusada.`);
          await loadData(true);
        } catch (err: any) {
          showAlert('Erro', err.message || 'Não foi possível recusar a organização.');
        } finally {
          setProcessingId(null);
        }
      },
      'Recusar',
      true
    );
  };

  const filteredList = (activeTab === 'all' ? organizations : pendingOrgs).filter((org) => {
    const term = search.toLowerCase().trim();
    if (!term) return true;
    return (
      org.name.toLowerCase().includes(term) ||
      org.accessKey.toLowerCase().includes(term) ||
      (org.ownerName && org.ownerName.toLowerCase().includes(term))
    );
  });

  return (
    <KeyboardAvoidingView behavior="padding" style={styles.container}>
      {/* Header */}
      <View style={[styles.header, { paddingTop: insets.top + 12 }]}>
        <View style={styles.headerLeft}>
          <View style={styles.badgeSysAdmin}>
            <Ionicons name="shield-checkmark" size={18} color="#FFF" />
          </View>
          <View>
            <Text style={styles.headerTitle}>Gestão de Organizações</Text>
            <Text style={styles.headerSubtitle}>Painel Unificado do SysAdmin</Text>
          </View>
        </View>
        <TouchableOpacity
          style={styles.refreshHeaderBtn}
          onPress={() => {
            setRefreshing(true);
            loadData();
          }}
          disabled={refreshing || loading}
        >
          <Ionicons name="refresh" size={20} color="#5C6BC0" />
        </TouchableOpacity>
      </View>

      {/* Segmented internal tab selector */}
      <View style={styles.segmentContainer}>
        <TouchableOpacity
          style={[styles.segmentBtn, activeTab === 'all' && styles.segmentBtnActive]}
          onPress={() => setActiveTab('all')}
        >
          <Ionicons
            name="business"
            size={16}
            color={activeTab === 'all' ? '#FFF' : '#A0A0A0'}
            style={{ marginRight: 6 }}
          />
          <Text style={[styles.segmentBtnText, activeTab === 'all' && styles.segmentBtnTextActive]}>
            Todas ({organizations.length})
          </Text>
        </TouchableOpacity>

        <TouchableOpacity
          style={[styles.segmentBtn, activeTab === 'pending' && styles.segmentBtnActive]}
          onPress={() => setActiveTab('pending')}
        >
          <Ionicons
            name="time"
            size={16}
            color={activeTab === 'pending' ? '#FFF' : '#A0A0A0'}
            style={{ marginRight: 6 }}
          />
          <Text style={[styles.segmentBtnText, activeTab === 'pending' && styles.segmentBtnTextActive]}>
            Pendentes LGPD ({pendingOrgs.length})
          </Text>
        </TouchableOpacity>
      </View>

      {/* Compliance banner */}
      <View style={styles.lgpdBanner}>
        <Ionicons name="lock-closed" size={16} color="#81C784" style={{ marginRight: 8 }} />
        <Text style={styles.lgpdBannerText}>
          {activeTab === 'all'
            ? 'Conformidade LGPD Ativa: e-mails e CNPJs são protegidos com anonimização.'
            : 'Fila de Moderação LGPD: análise de novas organizações solicitadas por gestores.'}
        </Text>
      </View>

      {/* Search bar */}
      <View style={styles.searchContainer}>
        <Ionicons name="search" size={18} color="#A0A0A0" style={styles.searchIcon} />
        <TextInput
          style={styles.searchInput}
          placeholder="Buscar por nome, #chave ou responsável..."
          placeholderTextColor="#A0A0A0"
          value={search}
          onChangeText={setSearch}
        />
        {search.length > 0 && (
          <TouchableOpacity onPress={() => setSearch('')}>
            <Ionicons name="close-circle" size={18} color="#A0A0A0" />
          </TouchableOpacity>
        )}
      </View>

      {/* List */}
      {loading ? (
        <View style={styles.centerContainer}>
          <ActivityIndicator size="large" color="#5C6BC0" />
          <Text style={styles.loadingText}>Carregando dados com segurança...</Text>
        </View>
      ) : (
        <FlatList
          data={filteredList}
          keyExtractor={(item) => String(item.id)}
          refreshControl={
            <RefreshControl
              refreshing={refreshing}
              onRefresh={() => {
                setRefreshing(true);
                loadData();
              }}
              tintColor="#5C6BC0"
            />
          }
          contentContainerStyle={styles.listContent}
          ListEmptyComponent={
            <View style={styles.emptyContainer}>
              <Ionicons
                name={activeTab === 'pending' ? 'checkmark-done-circle' : 'business-outline'}
                size={48}
                color="#555"
              />
              <Text style={styles.emptyTitle}>
                {activeTab === 'pending' ? 'Fila Vazia!' : 'Nenhuma organização'}
              </Text>
              <Text style={styles.emptySubtitle}>
                {activeTab === 'pending'
                  ? 'Nenhuma organização aguardando aprovação no momento.'
                  : 'Nenhum resultado encontrado para a busca.'}
              </Text>
            </View>
          }
          renderItem={({ item }) => {
            const isProcessing = processingId === item.id;

            return (
              <View style={styles.card}>
                <View style={styles.cardHeader}>
                  <View style={styles.orgInfoMain}>
                    <Text style={styles.orgName}>{item.name}</Text>
                    <Text style={styles.orgAccessKey}>{item.accessKey}</Text>
                  </View>
                  <View
                    style={[
                      styles.statusBadge,
                      item.approved
                        ? styles.statusApproved
                        : item.status === 'REJECTED' || item.status === 'BLOCKED'
                        ? styles.statusBlocked
                        : styles.statusPending,
                    ]}
                  >
                    <Ionicons
                      name={
                        item.approved
                          ? 'checkmark-circle'
                          : item.status === 'REJECTED'
                          ? 'close-circle'
                          : item.status === 'BLOCKED'
                          ? 'ban'
                          : 'time'
                      }
                      size={14}
                      color="#FFF"
                      style={{ marginRight: 4 }}
                    />
                    <Text style={styles.statusText}>
                      {item.approved
                        ? STATUS_LABELS.APPROVED
                        : item.status === 'REJECTED'
                        ? STATUS_LABELS.REJECTED
                        : item.status === 'BLOCKED'
                        ? STATUS_LABELS.BLOCKED
                        : STATUS_LABELS.PENDING}
                    </Text>
                  </View>
                </View>

                {/* LGPD-sanitized information */}
                <View style={styles.infoRow}>
                  <Ionicons name="person-circle-outline" size={16} color="#A0A0A0" style={{ marginRight: 6 }} />
                  <Text style={styles.infoLabel}>Responsável:</Text>
                  <Text style={styles.infoValue}>{item.ownerName || 'Não atribuído'}</Text>
                </View>

                <View style={styles.infoRow}>
                  <Ionicons name="mail-outline" size={16} color="#A0A0A0" style={{ marginRight: 6 }} />
                  <Text style={styles.infoLabel}>E-mail LGPD:</Text>
                  <Text style={styles.infoValueMasked}>{item.ownerEmailMasked || '***@***.***'}</Text>
                </View>

                {item.cnpjMasked ? (
                  <View style={styles.infoRow}>
                    <Ionicons name="document-text-outline" size={16} color="#A0A0A0" style={{ marginRight: 6 }} />
                    <Text style={styles.infoLabel}>CNPJ Sanitizado:</Text>
                    <Text style={styles.infoValueMasked}>{item.cnpjMasked}</Text>
                  </View>
                ) : null}

                {/* Quick metrics */}
                <View style={styles.statsRow}>
                  <View style={styles.statPill}>
                    <Ionicons name="people-outline" size={14} color="#5C6BC0" style={{ marginRight: 4 }} />
                    <Text style={styles.statText}>{item.totalMembers ?? 0} Membros</Text>
                  </View>
                  <View style={styles.statPill}>
                    <Ionicons name="layers-outline" size={14} color="#5C6BC0" style={{ marginRight: 4 }} />
                    <Text style={styles.statText}>{item.totalCommittees ?? 0} Comissões</Text>
                  </View>
                </View>

                {/* Moderation actions */}
                <View style={styles.actionsContainer}>
                  {activeTab === 'pending' ? (
                    <View style={styles.actionRowDuo}>
                      <TouchableOpacity
                        style={[styles.btnActionDuo, styles.btnApprove, isProcessing && styles.btnDisabled]}
                        onPress={() => handleApproveQueue(item)}
                        disabled={isProcessing}
                      >
                        {isProcessing ? (
                          <ActivityIndicator size="small" color="#FFF" />
                        ) : (
                          <>
                            <Ionicons name="checkmark" size={16} color="#FFF" style={{ marginRight: 4 }} />
                            <Text style={styles.actionBtnText}>Aprovar</Text>
                          </>
                        )}
                      </TouchableOpacity>

                      <TouchableOpacity
                        style={[styles.btnActionDuo, styles.btnBlock, isProcessing && styles.btnDisabled]}
                        onPress={() => handleRejectQueue(item)}
                        disabled={isProcessing}
                      >
                        <Ionicons name="close" size={16} color="#FFF" style={{ marginRight: 4 }} />
                        <Text style={styles.actionBtnText}>Recusar</Text>
                      </TouchableOpacity>
                    </View>
                  ) : (
                    <TouchableOpacity
                      style={[
                        styles.actionBtn,
                        item.approved ? styles.btnBlock : styles.btnApprove,
                        isProcessing && styles.btnDisabled,
                      ]}
                      onPress={() => toggleOverallStatus(item)}
                      disabled={isProcessing}
                    >
                      {isProcessing ? (
                        <ActivityIndicator size="small" color="#FFF" />
                      ) : (
                        <>
                          <Ionicons
                            name={item.approved ? 'lock-closed' : 'checkmark-circle'}
                            size={16}
                            color="#FFF"
                            style={{ marginRight: 6 }}
                          />
                          <Text style={styles.actionBtnText}>
                            {item.approved ? 'Bloquear Organização' : 'Aprovar / Ativar Organização'}
                          </Text>
                        </>
                      )}
                    </TouchableOpacity>
                  )}
                </View>
              </View>
            );
          }}
        />
      )}
    </KeyboardAvoidingView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#121212',
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 16,
    paddingTop: Platform.OS === 'ios' ? 50 : 20,
    paddingBottom: 14,
    backgroundColor: '#1E1E1E',
    borderBottomWidth: 1,
    borderBottomColor: '#2D2D2D',
  },
  headerLeft: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  badgeSysAdmin: {
    width: 36,
    height: 36,
    borderRadius: 18,
    backgroundColor: '#5C6BC0',
    alignItems: 'center',
    justifyContent: 'center',
    marginRight: 12,
  },
  headerTitle: {
    fontSize: 18,
    fontWeight: 'bold',
    color: '#FFF',
  },
  headerSubtitle: {
    fontSize: 12,
    color: '#A0A0A0',
  },
  refreshHeaderBtn: {
    padding: 8,
    borderRadius: 8,
    backgroundColor: '#262626',
    alignItems: 'center',
    justifyContent: 'center',
    borderWidth: 1,
    borderColor: '#383838',
  },
  segmentContainer: {
    flexDirection: 'row',
    backgroundColor: '#1E1E1E',
    padding: 6,
    marginHorizontal: 16,
    marginTop: 14,
    borderRadius: 10,
    borderWidth: 1,
    borderColor: '#2D2D2D',
  },
  segmentBtn: {
    flex: 1,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    paddingVertical: 10,
    borderRadius: 8,
  },
  segmentBtnActive: {
    backgroundColor: '#5C6BC0',
  },
  segmentBtnText: {
    color: '#A0A0A0',
    fontSize: 13,
    fontWeight: '600',
  },
  segmentBtnTextActive: {
    color: '#FFF',
    fontWeight: 'bold',
  },
  lgpdBanner: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#1B2E1E',
    borderColor: '#2E7D32',
    borderWidth: 1,
    marginHorizontal: 16,
    marginTop: 12,
    paddingHorizontal: 12,
    paddingVertical: 10,
    borderRadius: 8,
  },
  lgpdBannerText: {
    fontSize: 12,
    color: '#A5D6A7',
    flex: 1,
    lineHeight: 16,
  },
  searchContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#1E1E1E',
    marginHorizontal: 16,
    marginVertical: 12,
    paddingHorizontal: 14,
    borderRadius: 8,
    height: 44,
    borderWidth: 1,
    borderColor: '#333',
  },
  searchIcon: {
    marginRight: 8,
  },
  searchInput: {
    flex: 1,
    color: '#FFF',
    fontSize: 14,
  },
  centerContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    padding: 20,
  },
  loadingText: {
    marginTop: 12,
    color: '#A0A0A0',
    fontSize: 14,
  },
  listContent: {
    paddingHorizontal: 16,
    paddingBottom: 24,
  },
  emptyContainer: {
    alignItems: 'center',
    justifyContent: 'center',
    paddingVertical: 40,
  },
  emptyTitle: {
    color: '#FFF',
    fontSize: 16,
    fontWeight: 'bold',
    marginTop: 10,
  },
  emptySubtitle: {
    color: '#777',
    fontSize: 13,
    marginTop: 4,
    textAlign: 'center',
  },
  card: {
    backgroundColor: '#1E1E1E',
    borderRadius: 10,
    padding: 16,
    marginBottom: 14,
    borderWidth: 1,
    borderColor: '#2D2D2D',
  },
  cardHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'flex-start',
    marginBottom: 12,
  },
  orgInfoMain: {
    flex: 1,
    marginRight: 10,
  },
  orgName: {
    fontSize: 16,
    fontWeight: 'bold',
    color: '#FFF',
  },
  orgAccessKey: {
    fontSize: 13,
    color: '#5C6BC0',
    marginTop: 2,
    fontWeight: '600',
  },
  statusBadge: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 8,
    paddingVertical: 4,
    borderRadius: 12,
  },
  statusApproved: {
    backgroundColor: '#2E7D32',
  },
  statusBlocked: {
    backgroundColor: '#C62828',
  },
  statusPending: {
    backgroundColor: '#F57C00',
  },
  statusText: {
    color: '#FFF',
    fontSize: 11,
    fontWeight: 'bold',
  },
  infoRow: {
    flexDirection: 'row',
    alignItems: 'center',
    marginTop: 6,
  },
  infoLabel: {
    fontSize: 13,
    color: '#A0A0A0',
    marginRight: 6,
  },
  infoValue: {
    fontSize: 13,
    color: '#FFF',
    fontWeight: '500',
  },
  infoValueMasked: {
    fontSize: 13,
    color: '#90CAF9',
    fontFamily: Platform.OS === 'ios' ? 'Courier' : 'monospace',
  },
  statsRow: {
    flexDirection: 'row',
    marginTop: 12,
    marginBottom: 8,
    gap: 8,
  },
  statPill: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#282828',
    paddingHorizontal: 10,
    paddingVertical: 5,
    borderRadius: 6,
  },
  statText: {
    color: '#DDD',
    fontSize: 12,
  },
  actionsContainer: {
    marginTop: 10,
    borderTopWidth: 1,
    borderTopColor: '#2D2D2D',
    paddingTop: 10,
  },
  actionBtn: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    paddingVertical: 10,
    borderRadius: 6,
  },
  actionRowDuo: {
    flexDirection: 'row',
    gap: 10,
  },
  btnActionDuo: {
    flex: 1,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    paddingVertical: 10,
    borderRadius: 6,
  },
  btnApprove: {
    backgroundColor: '#2E7D32',
  },
  btnBlock: {
    backgroundColor: '#C62828',
  },
  actionBtnText: {
    color: '#FFF',
    fontSize: 13,
    fontWeight: 'bold',
  },
  btnDisabled: {
    opacity: 0.6,
  },
});
