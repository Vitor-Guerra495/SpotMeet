export const isRecord = (value: unknown): value is Record<string, unknown> => value !== null && typeof value === 'object' && !Array.isArray(value);
export const isAdminRole = (role: string | null) => role === 'ADMIN' || role === 'SYSADMIN';
export const numberValue = (value: unknown): number | null => typeof value === 'number' && Number.isFinite(value) && value >= 0 ? value : null;
export const textValue = (value: unknown): string | null => typeof value === 'string' && value.trim() ? value : null;
export const recordValue = (value: unknown): Record<string, unknown> => isRecord(value) ? value : {};

export interface AdminOrganization {
  id: number; name: string; accessKey: string; ownerId: number | null; ownerName: string;
  ownerEmailMasked: string | null; cnpjMasked: string | null; approved: boolean; status: string;
  moderationReason: string | null; createdAt: string | null; totalMembers: number | null; totalCommittees: number | null;
  resubmissionNote: string | null; resubmittedAt: string | null;
}
export interface AdminMember { userId: number; name: string; email: string; role: string; }
export interface SubsystemInfo { status: string | null; details: string | null; }
export interface ResourceInfo extends SubsystemInfo {
  totalMb: number | null; usedMb: number | null; freeMb: number | null; maxMb: number | null;
  processors: number | null; usagePercent: number | null; processCpuPercent: number | null;
}
export interface SystemStatus {
  overallStatus: string; version: string | null; uptimeSeconds: number | null; timestamp: string | null;
  database: SubsystemInfo; network: SubsystemInfo; memory: ResourceInfo;
}
export interface AuditActivity { id: number; action: string; executorName: string | null; executorEmailMasked: string | null; sanitizedDetails: string | null; createdAt: string | null; }
export interface AdminReport {
  totalUsers: number | null; verifiedUsers: number | null; totalOrganizations: number | null; approvedOrganizations: number | null;
  pendingOrganizations: number | null; totalCommittees: number | null; totalAuditLogs: number | null;
  usersByRole: Record<string, unknown>; recentActivities: AuditActivity[]; generatedAt: string | null;
}
export interface SystemDetails { timestamp: string | null; database: Record<string, unknown>; api: Record<string, unknown>; resources: ResourceInfo; }

const identifier = (value: unknown): value is number => typeof value === 'number' && Number.isSafeInteger(value) && value > 0;
function rows(value: unknown): Record<string, unknown>[] {
  if (!Array.isArray(value) || !value.every(isRecord)) throw new Error('Invalid list response');
  return value;
}

export function parseOrganizations(value: unknown): AdminOrganization[] {
  return rows(value).map(row => {
    if (!identifier(row.id) || !textValue(row.name) || !textValue(row.accessKey) || typeof row.approved !== 'boolean') throw new Error('Invalid organization');
    return { id: row.id, name: row.name as string, accessKey: row.accessKey as string,
      ownerId: identifier(row.ownerId) ? row.ownerId : null, ownerName: textValue(row.ownerName) ?? 'Responsável não informado',
      ownerEmailMasked: textValue(row.ownerEmailMasked), cnpjMasked: textValue(row.cnpjMasked),
      approved: row.approved, status: textValue(row.status) ?? 'UNKNOWN',
      moderationReason: textValue(row.moderationReason), createdAt: textValue(row.createdAt), totalMembers: numberValue(row.totalMembers), totalCommittees: numberValue(row.totalCommittees),
      resubmissionNote: textValue(row.resubmissionNote), resubmittedAt: textValue(row.resubmittedAt) };
  });
}
export function parseMembers(value: unknown): AdminMember[] {
  return rows(value).map(row => {
    if (!identifier(row.userId) || !textValue(row.name) || !textValue(row.email) || !textValue(row.role)) throw new Error('Invalid member');
    return { userId: row.userId, name: row.name as string, email: row.email as string, role: row.role as string };
  });
}
export function parseResources(value: unknown): ResourceInfo {
  const data = recordValue(value);
  return { status: textValue(data.status), details: textValue(data.details), totalMb: numberValue(data.totalMb), usedMb: numberValue(data.usedMb), freeMb: numberValue(data.freeMb),
    maxMb: numberValue(data.maxMb), processors: numberValue(data.processors), usagePercent: numberValue(data.usagePercent), processCpuPercent: numberValue(data.processCpuPercent) };
}
export function parseStatus(value: unknown): SystemStatus {
  if (!isRecord(value) || !textValue(value.overallStatus) || !isRecord(value.database) || !isRecord(value.network) || !isRecord(value.memory)) throw new Error('Invalid status');
  const subsystem = (data: Record<string, unknown>) => ({ status: textValue(data.status), details: textValue(data.details) });
  return { overallStatus: value.overallStatus as string, version: textValue(value.version), uptimeSeconds: numberValue(value.uptimeSeconds), timestamp: textValue(value.timestamp),
    database: subsystem(value.database), network: subsystem(value.network), memory: parseResources(value.memory) };
}
export function parseReport(value: unknown): AdminReport {
  if (!isRecord(value) || !Array.isArray(value.recentActivities)) throw new Error('Invalid report');
  const recentActivities = rows(value.recentActivities).map(row => {
    if (!identifier(row.id) || !textValue(row.action)) throw new Error('Invalid audit activity');
    return { id: row.id, action: row.action as string, executorName: textValue(row.executorName), executorEmailMasked: textValue(row.executorEmailMasked), sanitizedDetails: textValue(row.sanitizedDetails), createdAt: textValue(row.createdAt) };
  });
  return { totalUsers: numberValue(value.totalUsers), verifiedUsers: numberValue(value.verifiedUsers), totalOrganizations: numberValue(value.totalOrganizations), approvedOrganizations: numberValue(value.approvedOrganizations),
    pendingOrganizations: numberValue(value.pendingOrganizations), totalCommittees: numberValue(value.totalCommittees), totalAuditLogs: numberValue(value.totalAuditLogs),
    usersByRole: recordValue(value.usersByRole), recentActivities, generatedAt: textValue(value.generatedAt) };
}
export function parseDetails(value: unknown): SystemDetails {
  if (!isRecord(value) || !isRecord(value.database) || !isRecord(value.api) || !isRecord(value.resources)) throw new Error('Invalid system details');
  return { timestamp: textValue(value.timestamp), database: value.database, api: value.api, resources: parseResources(value.resources) };
}
export function formatNumber(value: unknown, suffix = ''): string {
  const number = numberValue(value);
  return number === null ? 'Indisponível' : `${number.toLocaleString('pt-BR', { maximumFractionDigits: 2 })}${suffix}`;
}
export function formatPercent(value: unknown): string { const number = numberValue(value); return number === null || number > 100 ? 'Indisponível' : formatNumber(number, '%'); }
export function formatBytes(value: unknown): string {
  const bytes = numberValue(value); if (bytes === null) return 'Indisponível';
  return bytes >= 1073741824 ? formatNumber(bytes / 1073741824, ' GiB') : formatNumber(bytes / 1048576, ' MiB');
}
export function formatUptime(value: unknown): string {
  const seconds = numberValue(value); if (seconds === null) return 'Indisponível';
  return `${Math.floor(seconds / 3600)}h ${Math.floor(seconds % 3600 / 60)}m ${Math.floor(seconds % 60)}s`;
}
export function formatTimestamp(value: unknown): string {
  if (typeof value !== 'string') return 'Horário indisponível';
  if (/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d+)?$/.test(value)) {
    return `${value.slice(8, 10)}/${value.slice(5, 7)}/${value.slice(0, 4)} ${value.slice(11, 19)} (servidor)`;
  }
  const date = new Date(value);
  return Number.isFinite(date.getTime()) ? date.toLocaleString('pt-BR') : 'Horário indisponível';
}

export function apiError(res: Response, text: string): string {
  if (res.status >= 500) return 'Não foi possível concluir a operação. Tente novamente.';
  if (res.status === 401) return 'Sua sessão foi encerrada. Entre novamente.';
  if (res.status === 403) return 'Acesso negado pelo servidor.';
  try { const data: unknown = JSON.parse(text); if (isRecord(data) && textValue(data.message)) return data.message as string; } catch {
    if (text && text.length <= 500 && !/[<>]/.test(text)) return text;
  }
  return 'Não foi possível concluir a operação. Tente novamente.';
}

export const AUDIT_LABELS: Record<string, string> = {
  USER_REGISTERED: 'Usuário cadastrado', ACCOUNT_VERIFIED: 'Conta verificada', VERIFICATION_RESENT: 'Verificação reenviada',
  PASSWORD_RECOVERY_REQUESTED: 'Recuperação de senha solicitada', PASSWORD_RESET: 'Senha redefinida', PASSWORD_CHANGED: 'Senha alterada', PROFILE_UPDATED: 'Perfil atualizado',
  LGPD_EMAIL_CHANGE_REQUESTED: 'Troca de e-mail solicitada', LGPD_EMAIL_CHANGE_STEP1_CONFIRMED: 'E-mail atual confirmado', LGPD_EMAIL_CHANGE_CODE_RESENT: 'Código do novo e-mail reenviado', LGPD_EMAIL_CHANGED: 'E-mail alterado',
  ORGANIZATION_CREATED: 'Organização criada', ORGANIZATION_UPDATED: 'Organização atualizada', ORGANIZATION_DELETED: 'Organização excluída', ORGANIZATION_RESUBMITTED: 'Organização reenviada para avaliação',ORGANIZATION_LEADER_CHANGED: 'Liderança transferida',
  ORGANIZATION_INVITATION_SENT: 'Convite de organização enviado', ORGANIZATION_INVITATION_ACCEPTED: 'Convite de organização aceito', ORGANIZATION_INVITATION_REJECTED: 'Convite de organização recusado',
  ACCESS_REQUESTED: 'Acesso solicitado', ACCESS_APPROVED: 'Acesso aprovado', ACCESS_REJECTED: 'Acesso recusado', ACCESS_REVOKED: 'Acesso revogado',
  ROLE_PROMOTED_SUBOWNER: 'Integrante elevado a subdono', ROLE_PROMOTED_VICE_OWNER: 'Integrante elevado a vice-líder', ROLE_DEMOTED_SUBOWNER: 'Papel alterado para subdono', ROLE_DEMOTED_MEMBER: 'Papel alterado para membro',
  COMMITTEE_CREATED: 'Comissão criada', COMMITTEE_UPDATED: 'Comissão atualizada', COMMITTEE_DELETED: 'Comissão excluída', COMMITTEE_INVITATION_SENT: 'Convite de comissão enviado',
  COMMITTEE_INVITATION_ACCEPTED: 'Convite de comissão aceito', COMMITTEE_INVITATION_DECLINED: 'Convite de comissão recusado', COMMITTEE_JOIN_REQUESTED: 'Ingresso em comissão solicitado',
  COMMITTEE_JOIN_APPROVED: 'Ingresso em comissão aprovado', COMMITTEE_JOIN_REJECTED: 'Ingresso em comissão recusado', COMMITTEE_MEMBER_REMOVED: 'Integrante removido da comissão',
  ADMIN_ORGANIZATION_APPROVED: 'Organização aprovada', ADMIN_ORGANIZATION_REJECTED: 'Organização recusada', ADMIN_ORGANIZATION_STATUS_CHANGED: 'Estado da organização alterado',
  ADMIN_SYSTEM_RESTART: 'Reinício de subsistema solicitado', ADMIN_SYSTEM_FULL_RESET: 'Reset total executado',
  ADMIN_ORGANIZATIONS_LISTED: 'Organizações consultadas', ADMIN_PENDING_ORGANIZATIONS_LISTED: 'Pendências consultadas', ADMIN_REPORTS_VIEWED: 'Relatórios consultados',
};
export function auditLabel(action: string): string { return Object.prototype.hasOwnProperty.call(AUDIT_LABELS, action) ? AUDIT_LABELS[action] : 'Operação registrada'; }
