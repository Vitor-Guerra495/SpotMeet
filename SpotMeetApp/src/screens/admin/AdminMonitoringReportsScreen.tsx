import React, { useState, useCallback, useEffect, useMemo } from 'react';
import { View, Text, StyleSheet, ScrollView, TouchableOpacity, ActivityIndicator, RefreshControl } from 'react-native';
import { Ionicons, type IoniconsIconName } from '@react-native-vector-icons/ionicons';
import { useFocusEffect } from '@react-navigation/native';
import { useAuth } from '../../context/AuthContext';
import { useTheme, type ThemeColors } from '../../context/ThemeContext';
import { API_BASE_URL } from '../../config/api';
import { useLiveSync } from '../../hooks/useLiveSync';
import { useRefreshRequest } from '../../hooks/useRefreshRequest';
import { AdminHeader, AdminCard, AdminAction, AdminError, AdminEmpty, AdminSheet, AdminMetric, StatusBadge } from '../../components/admin/AdminUi';
import { isAdminRole, isRecord, recordValue, textValue, parseStatus, parseReport, parseDetails, formatNumber, formatPercent, formatBytes, formatUptime, formatTimestamp, auditLabel,
  type SystemStatus, type AdminReport, type SystemDetails, type ResourceInfo } from '../../utils/adminData';

type Detail = 'DATABASE' | 'NETWORK' | 'RESOURCES' | 'API';
const TITLES: Record<Detail, string> = { DATABASE: 'Banco de dados', NETWORK: 'Rede', RESOURCES: 'Recursos', API: 'API' };
const TABLES: Record<string, string> = { users: 'Usuários', organizations: 'Organizações', organization_members: 'Integrantes de organizações', access_requests: 'Pedidos de acesso',
  organization_invitations: 'Convites de organização', committees: 'Comissões', committee_members: 'Integrantes de comissões', audit_logs: 'Registros de auditoria' };

export default function AdminMonitoringReportsScreen() {
  const { token, role, getSession } = useAuth(); const { colors } = useTheme();
  const styles = useMemo(() => createStyles(colors), [colors]), version = getSession().version;
  const [status, setStatus] = useState<SystemStatus | null>(null), [report, setReport] = useState<AdminReport | null>(null);
  const [loading, setLoading] = useState(true), [refreshing, setRefreshing] = useState(false), [error, setError] = useState('');
  const [selected, setSelected] = useState<Detail | null>(null), [details, setDetails] = useState<SystemDetails | null>(null);
  const [detailsLoading, setDetailsLoading] = useState(false), [detailsError, setDetailsError] = useState('');
  const isAdmin = isAdminRole(role), refreshRequest = useRefreshRequest(role);
  const detailsRequest = useRefreshRequest(`${role}:${selected ?? ''}`);
  useEffect(() => {
    setStatus(null); setReport(null); setDetails(null); setSelected(null); setError(''); setDetailsError('');
    setLoading(!!token && isAdmin); setRefreshing(false); setDetailsLoading(false);
  }, [token, role, version, isAdmin, API_BASE_URL]);

  const deny = () => { refreshRequest.cancel(); detailsRequest.cancel(); setStatus(null); setReport(null); setDetails(null); setSelected(null); setDetailsLoading(false); setError('Acesso negado pelo servidor. Esta área exige permissão administrativa.'); };
  const loadData = useCallback((silent = false) => refreshRequest.run(async ({ signal, isCurrent, isCurrentScope }) => {
    if (!token || !isAdmin) return;
    if (!silent) setLoading(true);
    try {
      const headers = { Authorization: `Bearer ${token}` };
      const [statusResponse, reportResponse] = await Promise.all([
        fetch(`${API_BASE_URL}/admin/system/status`, { headers, signal }), fetch(`${API_BASE_URL}/admin/reports`, { headers, signal }),
      ]);
      if (!isCurrent()) return;
      if (statusResponse.status === 401 || reportResponse.status === 401) return;
      if (statusResponse.status === 403 || reportResponse.status === 403) { deny(); return; }
      if (!statusResponse.ok || !reportResponse.ok) throw new Error('Monitoring unavailable');
      const nextStatus = parseStatus(await statusResponse.json()), nextReport = parseReport(await reportResponse.json());
      if (isCurrent()) { setStatus(nextStatus); setReport(nextReport); setError(''); }
    } catch { if (isCurrent()) setError('Não foi possível atualizar o monitoramento. Os últimos dados recebidos foram preservados.'); }
    finally { if (isCurrentScope()) { setLoading(false); setRefreshing(false); } }
  }), [token, isAdmin, refreshRequest, detailsRequest.cancel]);
  useFocusEffect(useCallback(() => { void loadData(true); }, [loadData]));
  useLiveSync(useCallback(() => loadData(true), [loadData]), { intervalMs: 4000, enabled: !!token && isAdmin,
    eventTypes: ['ADMIN_ORGS_MUTATED', 'ORGANIZATIONS_MUTATED', 'MEMBERS_MUTATED', 'COMMITTEES_MUTATED', 'MONITORING_REFRESH', 'VISIBILITY_RESTORED'] });
  const refresh = () => { if (refreshRequest.isActive()) { setRefreshing(true); void loadData(true); } };
  const loadDetails = useCallback(() => detailsRequest.run(async ({ signal, isCurrent, isCurrentScope }) => {
    if (!token || !isAdmin || !selected || selected === 'NETWORK') return;
    setDetailsLoading(true);
    try {
      const res = await fetch(`${API_BASE_URL}/admin/system/details`, { headers: { Authorization: `Bearer ${token}` }, signal });
      if (!isCurrent()) return;
      if (res.status === 401) return;
      if (res.status === 403) { deny(); return; }
      if (!res.ok) throw new Error('Details unavailable');
      const next = parseDetails(await res.json());
      if (isCurrent()) { setDetails(next); setDetailsError(''); }
    } catch { if (isCurrent()) setDetailsError('Não foi possível consultar os detalhes. Tente novamente.'); }
    finally { if (isCurrentScope()) setDetailsLoading(false); }
  }), [token, isAdmin, selected, detailsRequest, refreshRequest.cancel]);
  useEffect(() => { if (selected) void loadDetails(); }, [selected, loadDetails]);
  const open = (kind: Detail) => {
    if (!isAdmin || !refreshRequest.isActive()) return;
    detailsRequest.cancel(); setDetails(null); setDetailsError(''); setDetailsLoading(kind !== 'NETWORK'); setSelected(kind);
  };
  const close = () => { if (detailsRequest.isActive()) { detailsRequest.cancel(); setSelected(null); setDetails(null); setDetailsError(''); setDetailsLoading(false); } };
  const tiles: { kind: Detail; icon: IoniconsIconName; state: string | null | undefined; summary: string }[] = [
    { kind: 'DATABASE', icon: 'server-outline', state: status?.database.status, summary: status?.database.details ?? 'Sem leitura do banco' },
    { kind: 'NETWORK', icon: 'globe-outline', state: status?.network.status, summary: status?.network.details ?? 'Sem leitura do conector HTTP' },
    { kind: 'RESOURCES', icon: 'hardware-chip-outline', state: status?.memory.status, summary: `Heap: ${formatPercent(status?.memory.usagePercent)} do máximo` },
  ];
  const db = details?.database ?? {}, api = details?.api ?? {}, pool = recordValue(db.pool), tableRows = recordValue(db.tableRows);
  const requestMetrics = api.requestMetricsAvailable === true;
  const routes = Array.isArray(api.topRoutes) ? api.topRoutes.filter(isRecord).slice(0, 3) : [];
  if (!isAdmin) return <View style={styles.container}><AdminHeader title="Monitoramento" subtitle="Área administrativa" /><AdminEmpty text="Acesso restrito a administradores." /></View>;
  return <View style={styles.container}>
    <AdminHeader title="Monitoramento" subtitle="Estado do servidor e atividades recentes" onRefresh={refresh} refreshing={refreshing} disabled={refreshing} />
    <ScrollView contentContainerStyle={styles.content} refreshControl={<RefreshControl refreshing={refreshing} onRefresh={refresh} tintColor={colors.primary} colors={[colors.primary]} />}>
      <AdminError message={error} />{error && <AdminAction label="Tentar novamente" onPress={refresh} icon="refresh-outline" />}
      {loading && !status && !report && <ActivityIndicator color={colors.primary} />}
      <View style={styles.row}><StatusBadge value={status?.overallStatus} /><Text style={styles.caption}>Consulta leve a cada 4 segundos</Text></View>
      <Text style={styles.caption}>Última leitura: {formatTimestamp(status?.timestamp)}</Text>
      <View style={styles.tiles}>{tiles.map(tile => <TouchableOpacity key={tile.kind} accessibilityRole="button" accessibilityLabel={`Detalhes de ${TITLES[tile.kind]}`} onPress={() => open(tile.kind)} style={styles.tile}>
        <Ionicons name={tile.icon} size={24} color={colors.isDark ? '#ADB5EF' : colors.primary} /><Text style={styles.tileTitle}>{TITLES[tile.kind]}</Text><View style={styles.tileState}><StatusBadge value={tile.state} /></View>
      </TouchableOpacity>)}</View>
      {tiles.filter(tile => tile.kind !== 'NETWORK').map(tile => <Text key={tile.kind} style={styles.caption}>{TITLES[tile.kind]}: {tile.summary}</Text>)}
      <AdminCard><View style={styles.usersHeader}><View style={styles.usersTitle}><Ionicons name="people-outline" size={26} color={colors.isDark ? '#ADB5EF' : colors.primary} />
        <Text style={[styles.cardTitle, styles.flex]}>Usuários cadastrados</Text></View><Text style={styles.hero}>{formatNumber(report?.totalUsers)}</Text></View>
        <AdminMetric label="E-mails verificados" value={formatNumber(report?.verifiedUsers)} />
        <AdminMetric label="Usuários comuns" value={formatNumber(report?.usersByRole.USER)} /><AdminMetric label="Administradores" value={formatNumber(report?.usersByRole.ADMIN)} />
        <AdminMetric label="Superadministradores" value={formatNumber(report?.usersByRole.SYSADMIN)} />
      </AdminCard>
      <AdminCard><Text style={styles.cardTitle}>Visão geral</Text><AdminMetric label="Organizações" value={formatNumber(report?.totalOrganizations)} />
        <AdminMetric label="Aprovadas" value={formatNumber(report?.approvedOrganizations)} /><AdminMetric label="Pendentes" value={formatNumber(report?.pendingOrganizations)} />
        <AdminMetric label="Comissões" value={formatNumber(report?.totalCommittees)} /><AdminMetric label="Registros de auditoria" value={formatNumber(report?.totalAuditLogs)} />
        <Text style={styles.caption}>Relatório de {formatTimestamp(report?.generatedAt)}</Text>
      </AdminCard>
      <AdminCard><Text style={styles.cardTitle}>API</Text><AdminMetric label="Versão" value={status?.version ?? 'Indisponível'} /><AdminMetric label="Tempo em execução" value={formatUptime(status?.uptimeSeconds)} />
        <AdminAction label="Detalhes da API" onPress={() => open('API')} icon="pulse-outline" />
      </AdminCard>
      <Text style={styles.cardTitle}>Atividades recentes</Text>
      {report?.recentActivities.map(activity => <AdminCard key={activity.id}><Text style={styles.activityTitle}>{auditLabel(activity.action)}</Text>
        <Text style={styles.caption}>{activity.executorName ?? 'Executor não informado'}{activity.executorEmailMasked ? ` (${activity.executorEmailMasked})` : ''}</Text>
        {activity.sanitizedDetails && <Text style={styles.caption}>{activity.sanitizedDetails}</Text>}<Text style={styles.caption}>{formatTimestamp(activity.createdAt)}</Text>
      </AdminCard>)}
      {report && !report.recentActivities.length && <AdminEmpty text="Nenhuma atividade registrada." />}
    </ScrollView>
    <AdminSheet title={selected ? `Detalhes: ${TITLES[selected]}` : 'Detalhes'} visible={!!selected} onClose={close}>
      <AdminError message={detailsError} />{detailsLoading && <ActivityIndicator color={colors.primary} />}
      {selected === 'NETWORK' ? <AdminCard><StatusBadge value={status?.network.status} /><Text style={styles.caption}>{status?.network.details ?? 'Leitura indisponível.'}</Text>
        <Text style={styles.caption}>A Rede representa o conector HTTP da API. O reinício fecha e reabre esse conector. Ele não reinicia o Wi-Fi nem a rede do aparelho.</Text>
        <Text style={styles.caption}>Última leitura: {formatTimestamp(status?.timestamp)}</Text>
      </AdminCard> : <><Text style={styles.caption}>Detalhes consultados sob demanda. Leitura de {formatTimestamp(details?.timestamp)}</Text>
        <AdminAction label="Atualizar detalhes" onPress={loadDetails} disabled={detailsLoading} icon="refresh-outline" />
        {details && selected === 'DATABASE' && <><AdminCard><StatusBadge value={textValue(db.status)} /><Text style={styles.caption}>{textValue(db.details) ?? 'Detalhes indisponíveis.'}</Text>
          <AdminMetric label="Banco" value={textValue(db.name) ?? 'Indisponível'} /><AdminMetric label="PostgreSQL" value={textValue(db.version) ?? 'Indisponível'} />
          <AdminMetric label="Tamanho" value={formatBytes(db.sizeBytes)} /><AdminMetric label="Tempo de consulta" value={formatNumber(db.responseTimeMs, ' ms')} />
        </AdminCard><AdminCard><Text style={styles.cardTitle}>Conexões do backend</Text>
          {([['active', 'Ativas'], ['idle', 'Livres'], ['waiting', 'Em espera'], ['total', 'Total'], ['maximum', 'Limite']] as const).map(([key, label]) => <AdminMetric key={key} label={label} value={formatNumber(pool[key])} />)}
        </AdminCard><AdminCard><Text style={styles.cardTitle}>Linhas por tabela</Text>{Object.entries(TABLES).map(([key, label]) => <AdminMetric key={key} label={label} value={formatNumber(tableRows[key])} />)}</AdminCard></>}
        {details && selected === 'RESOURCES' && <><ResourceDetails resources={details.resources} />
          <Text style={styles.caption}>O heap é a memória usada pelo servidor Java. Ele sobe a cada requisição, inclusive as consultas deste painel, e cai quando a JVM faz a coleta de memória, sozinha ou pelo botão de coleta. Esse sobe e desce é normal.</Text></>}
        {details && selected === 'API' && <><AdminCard><Text style={styles.cardTitle}>Execução e requisições</Text><AdminMetric label="Versão" value={textValue(api.version) ?? 'Indisponível'} /><AdminMetric label="Tempo em execução" value={formatUptime(api.uptimeSeconds)} />
          <Text style={styles.caption}>Contadores desde {formatTimestamp(api.countersSince)}. Reiniciam com o processo do servidor. A leitura pode variar durante requisições em andamento.</Text>
          {!requestMetrics && <Text style={styles.caption}>Métricas de requisições indisponíveis.</Text>}
          {([['totalRequests', 'Requisições'], ['clientErrors', 'Erros 4xx'], ['serverErrors', 'Erros 5xx']] as const).map(([key, label]) => <AdminMetric key={key} label={label} value={formatNumber(requestMetrics ? api[key] : null)} />)}
          <AdminMetric label="Resposta média" value={formatNumber(requestMetrics ? api.averageResponseTimeMs : null, ' ms')} /><AdminMetric label="Requisições ativas" value={formatNumber(api.activeRequests)} />
          <AdminMetric label="Threads da JVM" value={formatNumber(api.jvmThreads)} /><AdminMetric label="Threads do Tomcat" value={formatNumber(api.tomcatCurrentThreads)} /><AdminMetric label="Threads ocupadas" value={formatNumber(api.tomcatBusyThreads)} />
        </AdminCard><AdminCard><Text style={styles.cardTitle}>Rotas mais utilizadas</Text><Text style={styles.caption}>Até três rotas agrupadas pelo modelo do endpoint. Consultas de monitoramento não entram nesta lista.</Text>
          {requestMetrics && routes.map((route, index) => <View key={index} style={styles.route}><Text style={styles.activityTitle}>{textValue(route.method) ?? ''} {textValue(route.route) ?? 'Rota indisponível'}</Text>
            <AdminMetric label="Requisições" value={formatNumber(route.requests)} /><AdminMetric label="Resposta média" value={formatNumber(route.averageResponseTimeMs, ' ms')} /></View>)}
          {(!requestMetrics || !routes.length) && <Text style={styles.caption}>{requestMetrics ? 'Sem requisições registradas para o ranking.' : 'Ranking indisponível.'}</Text>}
        </AdminCard></>}
      </>}
    </AdminSheet>
  </View>;
}
function ResourceDetails({ resources }: { resources: ResourceInfo }) {
  const { colors } = useTheme();
  return <AdminCard><StatusBadge value={resources.status} /><AdminMetric label="Heap utilizado" value={formatNumber(resources.usedMb, ' MiB')} />
    <AdminMetric label="Heap reservado" value={formatNumber(resources.totalMb, ' MiB')} /><AdminMetric label="Livre no reservado" value={formatNumber(resources.freeMb, ' MiB')} />
    <AdminMetric label="Heap máximo" value={formatNumber(resources.maxMb, ' MiB')} /><AdminMetric label="Uso do máximo" value={formatPercent(resources.usagePercent)} />
    <AdminMetric label="CPU do processo" value={formatPercent(resources.processCpuPercent)} /><AdminMetric label="Processadores" value={formatNumber(resources.processors)} />
    <Text style={{ marginTop: 8, color: colors.subtext, fontSize: 12, lineHeight: 19 }}>{resources.details ?? ''}</Text>
  </AdminCard>;
}
function createStyles(colors: ThemeColors) { return StyleSheet.create({
  container: { flex: 1, backgroundColor: colors.background }, content: { paddingHorizontal: 20, paddingBottom: 24 }, flex: { flex: 1, minWidth: 0 },
  row: { flexDirection: 'row', gap: 10, alignItems: 'center', marginBottom: 10, flexWrap: 'wrap' },
  caption: { color: colors.subtext, fontSize: 12, lineHeight: 19, marginVertical: 4, flexShrink: 1 }, cardTitle: { color: colors.text, fontSize: 16, lineHeight: 23, fontWeight: '700', marginBottom: 8 },
  hero: { color: colors.isDark ? '#ADB5EF' : colors.primary, fontSize: 34, lineHeight: 42, fontWeight: '700' },
  tiles: { flexDirection: 'row', alignItems: 'stretch', gap: 8, marginVertical: 12 }, tile: { flex: 1, minWidth: 0, borderRadius: 14, borderWidth: 1, borderColor: colors.border, backgroundColor: colors.card, padding: 10, alignItems: 'center', gap: 8 },
  tileState: { marginTop: 'auto', paddingTop: 4, alignSelf: 'center', maxWidth: '100%' },
  usersHeader: { alignItems: 'center', marginBottom: 16, gap: 8 }, usersTitle: { flexDirection: 'row', alignItems: 'center', gap: 10, alignSelf: 'stretch' },
  tileTitle: { color: colors.text, fontSize: 13, lineHeight: 19, fontWeight: '600', textAlign: 'center' },
  activityTitle: { color: colors.text, fontSize: 14, lineHeight: 21, fontWeight: '600', flexShrink: 1 }, route: { borderTopWidth: 1, borderColor: colors.border, paddingTop: 12, marginTop: 12 },
}); }
