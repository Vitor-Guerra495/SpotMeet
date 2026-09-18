import React, { useState, useCallback } from 'react';
import {
  View, Text, StyleSheet, ScrollView, ActivityIndicator,
  RefreshControl, Platform,
} from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { showAlert } from '../../utils/alerts';
import { useFocusEffect } from '@react-navigation/native';
import { useAuth } from '../../context/AuthContext';
import { API_BASE_URL } from '../../config/api';
import { useLiveSync } from '../../hooks/useLiveSync';

interface SubsystemInfo {
  name: string;
  status: string;
  details: string;
}

interface MemoryInfo {
  totalMb: number;
  usedMb: number;
  freeMb: number;
  processors: number;
}

interface SystemStatus {
  overallStatus: string;
  version: string;
  uptimeSeconds: number;
  timestamp: string;
  database: SubsystemInfo;
  network: SubsystemInfo;
  memory: MemoryInfo;
}

interface AuditActivity {
  id: number;
  action: string;
  executorName: string;
  executorEmailMasked: string;
  sanitizedDetails: string;
  createdAt: string;
}

interface AggregatedReports {
  totalUsers: number;
  verifiedUsers: number;
  usersByRole: { [key: string]: number };
  totalOrganizations: number;
  approvedOrganizations: number;
  pendingOrganizations: number;
  totalCommittees: number;
  totalAuditLogs: number;
  recentActivities: AuditActivity[];
  generatedAt: string;
}

// Portuguese labels for subsystem status values returned by the API
const SUBSYSTEM_STATUS_LABELS: Record<string, string> = {
  OPERATIONAL: 'OPERACIONAL',
  DEGRADED: 'DEGRADADO',
  ONLINE: 'ONLINE',
  ACTIVE: 'ATIVO',
  WARNING: 'ALERTA',
  FAILURE: 'FALHA',
};

// Portuguese labels for audit log actions (falls back to the raw value)
const ACTION_LABELS: Record<string, string> = {
  USER_REGISTERED: 'USUARIO_CADASTRADO',
  ACCOUNT_VERIFIED: 'CONTA_VERIFICADA',
  VERIFICATION_RESENT: 'VERIFICACAO_REENVIADA',
  PASSWORD_RECOVERY_REQUESTED: 'SOLICITACAO_RECUPERACAO_SENHA',
  PASSWORD_RESET: 'SENHA_REDEFINIDA',
  PROFILE_UPDATED: 'PERFIL_ATUALIZADO',
  LGPD_EMAIL_CHANGE_REQUESTED: 'LGPD_SOLICITACAO_TROCA_EMAIL',
  LGPD_EMAIL_CHANGED: 'LGPD_EMAIL_ALTERADO_SUCESSO',
  ORGANIZATION_CREATED: 'ORG_CRIADA',
  ORGANIZATION_DELETED: 'ORGANIZACAO_EXCLUIDA',
  ACCESS_REQUESTED: 'ACESSO_SOLICITADO',
  ACCESS_APPROVED: 'ACESSO_APROVADO',
  ACCESS_REJECTED: 'ACESSO_REJEITADO',
  ACCESS_REVOKED: 'ACESSO_REVOGADO',
  ROLE_PROMOTED_VICE_OWNER: 'CARGO_ELEVADO_VICELIDER',
  ROLE_PROMOTED_SUBOWNER: 'CARGO_ELEVADO_SUBDONO',
  ROLE_DEMOTED_SUBOWNER: 'CARGO_REBAIXADO_SUBDONO',
  ROLE_DEMOTED_MEMBER: 'CARGO_REBAIXADO_MEMBRO',
  COMMITTEE_CREATED: 'COMISSAO_CRIADA',
  COMMITTEE_DELETED: 'COMISSAO_EXCLUIDA',
  COMMITTEE_INVITATION_SENT: 'COMISSAO_CONVITE_ENVIADO',
  COMMITTEE_INVITATION_ACCEPTED: 'COMISSAO_CONVITE_ACEITO',
  COMMITTEE_INVITATION_DECLINED: 'COMISSAO_CONVITE_RECUSADO',
  COMMITTEE_JOIN_REQUESTED: 'COMISSAO_ENTRADA_SOLICITADA',
  COMMITTEE_JOIN_APPROVED: 'COMISSAO_SOLICITACAO_APROVADA',
  COMMITTEE_JOIN_REJECTED: 'COMISSAO_SOLICITACAO_REJEITADA',
  COMMITTEE_MEMBER_REMOVED: 'COMISSAO_INTEGRANTE_REMOVIDO',
  ADMIN_ORGANIZATIONS_LISTED: 'ADMIN_LISTAR_ORGANIZACOES',
  ADMIN_PENDING_ORGANIZATIONS_LISTED: 'ADMIN_LISTAR_PENDENTES',
  ADMIN_ORGANIZATION_APPROVED: 'ADMIN_ORG_APROVADA',
  ADMIN_ORGANIZATION_REJECTED: 'ADMIN_ORG_REJEITADA',
  ADMIN_ORGANIZATION_STATUS_CHANGED: 'ADMIN_ORG_STATUS_ALTERADO',
  ADMIN_REPORTS_VIEWED: 'ADMIN_RELATORIOS_CONSULTADOS',
  ADMIN_SYSTEM_RESTART: 'ADMIN_SISTEMA_REINICIAR',
  ADMIN_SYSTEM_FULL_RESET: 'ADMIN_SISTEMA_RESET_TOTAL',
};

const statusLabel = (value: string | undefined, fallback: string) =>
  value ? (SUBSYSTEM_STATUS_LABELS[value] || value) : fallback;

const actionLabel = (value: string) => ACTION_LABELS[value] || value;

export default function AdminMonitoringReportsScreen() {
  const insets = useSafeAreaInsets();
  const { token, role } = useAuth();
  const [status, setStatus] = useState<SystemStatus | null>(null);
  const [report, setReport] = useState<AggregatedReports | null>(null);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);

  const isAdmin = role === 'ADMIN' || role === 'SYSADMIN';

  const formatUptime = (seconds: number) => {
    const hrs = Math.floor(seconds / 3600);
    const mins = Math.floor((seconds % 3600) / 60);
    const secs = seconds % 60;
    return `${hrs}h ${mins}m ${secs}s`;
  };

  const loadData = useCallback(async (silent = false) => {
    if (!token || !isAdmin) {
      setLoading(false);
      return;
    }
    if (!silent) setLoading(true);

    try {
      const headers = {
        'Authorization': `Bearer ${token}`,
        'Content-Type': 'application/json',
      };

      const [resStatus, resReports] = await Promise.all([
        fetch(`${API_BASE_URL}/admin/system/status`, { method: 'GET', headers }),
        fetch(`${API_BASE_URL}/admin/reports`, { method: 'GET', headers }),
      ]);

      if (resStatus.ok) {
        const statusData: SystemStatus = await resStatus.json();
        setStatus(statusData);
      } else {
        const errorText = await resStatus.text();
        console.warn('Status do sistema indisponível:', errorText);
      }

      if (resReports.ok) {
        const reportData: AggregatedReports = await resReports.json();
        setReport(reportData);
      } else {
        const errorText = await resReports.text();
        console.warn('Relatórios indisponíveis:', errorText);
      }
    } catch (err: any) {
      if (!silent) {
        const msg = err.message?.includes('fetch') || err.message?.includes('Network')
          ? 'Não foi possível conectar ao servidor (localhost:8080). Verifique se o backend está iniciado.'
          : (err.message || 'Falha ao carregar dados de monitoramento.');
        showAlert('Atenção', msg);
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

  // Real-time sync of infrastructure metrics and audit while the tab is active
  useLiveSync(
    useCallback(() => {
      loadData(true);
    }, [loadData]),
    {
      intervalMs: 4000,
      eventTypes: [
        'MONITORING_REFRESH',
        'ADMIN_ORGS_MUTATED',
        'ORGANIZATIONS_MUTATED',
        'VISIBILITY_RESTORED',
      ],
    }
  );

  if (loading) {
    return (
      <View style={styles.centerContainer}>
        <ActivityIndicator size="large" color="#5C6BC0" />
        <Text style={styles.loadingText}>Carregando métricas e status seguro...</Text>
      </View>
    );
  }

  return (
    <View style={styles.container}>
      {/* Header */}
      <View style={[styles.header, { paddingTop: insets.top + 12 }]}>
        <View style={styles.headerLeft}>
          <View style={styles.iconContainer}>
            <Ionicons name="stats-chart" size={18} color="#FFF" />
          </View>
          <View>
            <Text style={styles.headerTitle}>Monitoramento & Relatórios</Text>
            <Text style={styles.headerSubtitle}>Métricas Agregadas & Conformidade</Text>
          </View>
        </View>
        <View style={styles.healthTag}>
          <View style={styles.greenPulse} />
          <Text style={styles.healthText}>{statusLabel(status?.overallStatus, 'OPERACIONAL')}</Text>
        </View>
      </View>

      <ScrollView
        contentContainerStyle={styles.scrollContent}
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
      >
        {/* Data protection banner */}
        <View style={styles.secBanner}>
          <Ionicons name="shield-checkmark" size={16} color="#81C784" style={{ marginRight: 8 }} />
          <Text style={styles.secBannerText}>
            Relatórios em Conformidade LGPD: Dados sensíveis agregados e identificadores individuais ofuscados.
          </Text>
        </View>

        {/* Section: subsystem status */}
        <Text style={styles.sectionTitle}>Status dos Subsistemas</Text>

        <View style={styles.cardsGrid}>
          {/* Database */}
          <View style={styles.subsystemCard}>
            <View style={styles.subsystemHeader}>
              <Ionicons name="server" size={18} color="#4FC3F7" />
              <Text style={styles.subsystemName}>Banco</Text>
              <View style={styles.statusPillActive}>
                <Text style={styles.statusPillText}>{statusLabel(status?.database?.status, 'ONLINE')}</Text>
              </View>
            </View>
            <Text style={styles.subsystemDetail}>{status?.database?.details || 'PostgreSQL Conectado'}</Text>
          </View>

          {/* Network */}
          <View style={styles.subsystemCard}>
            <View style={styles.subsystemHeader}>
              <Ionicons name="wifi" size={18} color="#4DB6AC" />
              <Text style={styles.subsystemName}>Rede</Text>
              <View style={styles.statusPillActive}>
                <Text style={styles.statusPillText}>{statusLabel(status?.network?.status, 'ONLINE')}</Text>
              </View>
            </View>
            <Text style={styles.subsystemDetail}>{status?.network?.details || 'Servidor respondendo às requisições REST'}</Text>
          </View>

          {/* JVM resources */}
          <View style={styles.subsystemCard}>
            <View style={styles.subsystemHeader}>
              <Ionicons name="hardware-chip" size={18} color="#BA68C8" />
              <Text style={styles.subsystemName}>Recursos</Text>
              <View style={styles.statusPillActive}>
                <Text style={styles.statusPillText}>Uptime: {status ? formatUptime(status.uptimeSeconds) : '--'}</Text>
              </View>
            </View>
            <Text style={styles.subsystemDetail}>
              RAM Usada: {status?.memory?.usedMb || 0} MB / {status?.memory?.totalMb || 0} MB ({status?.memory?.processors || 0} CPUs)
            </Text>
          </View>
        </View>

        {/* Section: aggregated SpotMeet metrics */}
        <Text style={styles.sectionTitle}>Métricas do Ecossistema</Text>

        <View style={styles.statsContainer}>
          <View style={styles.statBox}>
            <Text style={styles.statNumber}>{report?.totalUsers ?? 0}</Text>
            <Text style={styles.statLabel}>Usuários Totais</Text>
            <Text style={styles.statSub}>{report?.verifiedUsers ?? 0} verificados</Text>
          </View>

          <View style={styles.statBox}>
            <Text style={styles.statNumber}>{report?.totalOrganizations ?? 0}</Text>
            <Text style={styles.statLabel}>Organizações</Text>
            <Text style={styles.statSub}>{report?.approvedOrganizations ?? 0} ativas</Text>
          </View>

          <View style={styles.statBox}>
            <Text style={styles.statNumber}>{report?.totalCommittees ?? 0}</Text>
            <Text style={styles.statLabel}>Comissões</Text>
            <Text style={styles.statSub}>Equipes ativas</Text>
          </View>

          <View style={styles.statBox}>
            <Text style={styles.statNumber}>{report?.totalAuditLogs ?? 0}</Text>
            <Text style={styles.statLabel}>Auditorias</Text>
            <Text style={styles.statSub}>Eventos rastreados</Text>
          </View>
        </View>

        {/* Role distribution */}
        <View style={styles.distributionCard}>
          <Text style={styles.distributionTitle}>Distribuição de Perfis no Sistema</Text>
          <View style={styles.distRow}>
            <View style={styles.distItem}>
              <View style={[styles.dot, { backgroundColor: '#5C6BC0' }]} />
              <Text style={styles.distText}>Comum: {report?.usersByRole?.['USER'] ?? 0}</Text>
            </View>
            <View style={styles.distItem}>
              <View style={[styles.dot, { backgroundColor: '#FFB74D' }]} />
              <Text style={styles.distText}>Admin: {report?.usersByRole?.['ADMIN'] ?? 0}</Text>
            </View>
            <View style={styles.distItem}>
              <View style={[styles.dot, { backgroundColor: '#E53935' }]} />
              <Text style={styles.distText}>SysAdmin: {report?.usersByRole?.['SYSADMIN'] ?? 0}</Text>
            </View>
          </View>
        </View>

        {/* Recent audit history (anonymized) */}
        <Text style={styles.sectionTitle}>Auditoria Recente (Logs Anonimizados)</Text>

        {report?.recentActivities && report.recentActivities.length > 0 ? (
          report.recentActivities.map((log) => (
            <View key={log.id} style={styles.logCard}>
              <View style={styles.logHeader}>
                <Text style={styles.logAction}>{actionLabel(log.action)}</Text>
                <Text style={styles.logDate}>
                  {log.createdAt ? log.createdAt.replace('T', ' ').substring(0, 19) : ''}
                </Text>
              </View>
              <Text style={styles.logAuthor}>
                Autor: {log.executorName} ({log.executorEmailMasked})
              </Text>
              {log.sanitizedDetails ? (
                <Text style={styles.logDetails}>{log.sanitizedDetails}</Text>
              ) : null}
            </View>
          ))
        ) : (
          <View style={styles.emptyLog}>
            <Text style={styles.emptyLogText}>Nenhum registro de auditoria no período.</Text>
          </View>
        )}
      </ScrollView>
    </View>
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
    paddingHorizontal: 20,
    paddingTop: 50,
    paddingBottom: 15,
    backgroundColor: '#1E1E1E',
    borderBottomWidth: 1,
    borderBottomColor: '#2C2C2C',
  },
  headerLeft: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  iconContainer: {
    width: 36,
    height: 36,
    borderRadius: 8,
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
  healthTag: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#1B2E1E',
    paddingHorizontal: 10,
    paddingVertical: 5,
    borderRadius: 12,
    borderWidth: 1,
    borderColor: '#2E7D32',
  },
  greenPulse: {
    width: 8,
    height: 8,
    borderRadius: 4,
    backgroundColor: '#4CAF50',
    marginRight: 6,
  },
  healthText: {
    color: '#81C784',
    fontSize: 11,
    fontWeight: 'bold',
  },
  centerContainer: {
    flex: 1,
    backgroundColor: '#121212',
    justifyContent: 'center',
    alignItems: 'center',
  },
  loadingText: {
    marginTop: 12,
    color: '#A0A0A0',
    fontSize: 14,
  },
  scrollContent: {
    padding: 16,
    paddingBottom: 30,
  },
  secBanner: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#1B2E1E',
    borderColor: '#2E7D32',
    borderWidth: 1,
    paddingHorizontal: 12,
    paddingVertical: 10,
    borderRadius: 8,
    marginBottom: 16,
  },
  secBannerText: {
    fontSize: 12,
    color: '#A5D6A7',
    flex: 1,
    lineHeight: 16,
  },
  sectionTitle: {
    fontSize: 16,
    fontWeight: 'bold',
    color: '#FFF',
    marginBottom: 12,
    marginTop: 8,
  },
  cardsGrid: {
    gap: 10,
    marginBottom: 16,
  },
  subsystemCard: {
    backgroundColor: '#1E1E1E',
    borderRadius: 8,
    padding: 14,
    borderWidth: 1,
    borderColor: '#2D2D2D',
  },
  subsystemHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 6,
  },
  subsystemName: {
    fontSize: 14,
    fontWeight: 'bold',
    color: '#FFF',
    marginLeft: 8,
    flex: 1,
  },
  statusPillActive: {
    backgroundColor: '#263238',
    paddingHorizontal: 8,
    paddingVertical: 3,
    borderRadius: 6,
  },
  statusPillText: {
    fontSize: 11,
    color: '#4FC3F7',
    fontWeight: '600',
  },
  subsystemDetail: {
    fontSize: 12,
    color: '#A0A0A0',
    lineHeight: 16,
  },
  statsContainer: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 10,
    marginBottom: 16,
  },
  statBox: {
    flex: 1,
    minWidth: '45%',
    backgroundColor: '#1E1E1E',
    borderRadius: 8,
    padding: 14,
    alignItems: 'center',
    borderWidth: 1,
    borderColor: '#2D2D2D',
  },
  statNumber: {
    fontSize: 22,
    fontWeight: 'bold',
    color: '#5C6BC0',
  },
  statLabel: {
    fontSize: 13,
    color: '#FFF',
    marginTop: 4,
    fontWeight: '600',
  },
  statSub: {
    fontSize: 11,
    color: '#888',
    marginTop: 2,
  },
  distributionCard: {
    backgroundColor: '#1E1E1E',
    borderRadius: 8,
    padding: 14,
    marginBottom: 16,
    borderWidth: 1,
    borderColor: '#2D2D2D',
  },
  distributionTitle: {
    fontSize: 13,
    color: '#A0A0A0',
    marginBottom: 10,
    fontWeight: '600',
  },
  distRow: {
    flexDirection: 'row',
    justifyContent: 'space-around',
  },
  distItem: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  dot: {
    width: 8,
    height: 8,
    borderRadius: 4,
    marginRight: 6,
  },
  distText: {
    fontSize: 12,
    color: '#DDD',
  },
  logCard: {
    backgroundColor: '#1A1A1A',
    borderRadius: 8,
    padding: 12,
    marginBottom: 8,
    borderLeftWidth: 3,
    borderLeftColor: '#5C6BC0',
  },
  logHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 4,
  },
  logAction: {
    fontSize: 13,
    fontWeight: 'bold',
    color: '#FFF',
  },
  logDate: {
    fontSize: 11,
    color: '#888',
  },
  logAuthor: {
    fontSize: 12,
    color: '#90CAF9',
    marginBottom: 4,
    fontFamily: Platform.OS === 'ios' ? 'Courier' : 'monospace',
  },
  logDetails: {
    fontSize: 12,
    color: '#AAA',
  },
  emptyLog: {
    padding: 20,
    alignItems: 'center',
  },
  emptyLogText: {
    color: '#777',
    fontSize: 13,
  },
});
