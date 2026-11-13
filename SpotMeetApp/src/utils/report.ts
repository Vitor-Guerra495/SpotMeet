import { toLocalDate } from './meetings';

export type ReportPeriod = 'month' | 'quarter' | 'year' | 'all' | 'custom';

export interface ReportRow {
  meetingId: number; title: string; committeeId: number; committeeName: string; startsAt: string;
  enteredAt: string; leftAt: string | null; durationMinutes: number | null;
}

export interface ReportCommittee { committeeId: number; committeeName: string; }

export interface AttendanceReport {
  totalMeetings: number; totalMinutes: number; totalCommittees: number; missingExit: boolean;
  rows: ReportRow[]; committees: ReportCommittee[];
}

export const PERIOD_LABELS: Record<ReportPeriod, string> = {
  month: 'Este mês', quarter: 'Últimos 3 meses', year: 'Este ano', all: 'Todo o período', custom: 'Personalizado',
};

/** First and last day of the period; "all" has no limits. */
export function periodRange(period: ReportPeriod, today: Date, custom?: { from: Date; to: Date }): { from: string | null; to: string | null } {
  if (period === 'all') return { from: null, to: null };
  if (period === 'custom' && custom) return { from: toLocalDate(custom.from), to: toLocalDate(custom.to) };
  const to = toLocalDate(today);
  if (period === 'month') return { from: toLocalDate(new Date(today.getFullYear(), today.getMonth(), 1)), to };
  if (period === 'year') return { from: toLocalDate(new Date(today.getFullYear(), 0, 1)), to };
  // Same day three months before; when that month is shorter (31/05), its last day (28/02 or 29/02).
  const lastDay = new Date(today.getFullYear(), today.getMonth() - 2, 0).getDate();
  return { from: toLocalDate(new Date(today.getFullYear(), today.getMonth() - 3, Math.min(today.getDate(), lastDay))), to };
}

/** "7 h 58 min", "58 min" or "0 min". */
export function formatTotal(minutes: number): string {
  if (minutes < 60) return `${minutes} min`;
  return `${Math.floor(minutes / 60)} h ${String(minutes % 60).padStart(2, '0')} min`;
}

const record = (value: unknown): value is Record<string, unknown> => value !== null && typeof value === 'object' && !Array.isArray(value);
const positive = (value: unknown) => typeof value === 'number' && Number.isSafeInteger(value) && value > 0;
const count = (value: unknown) => (typeof value === 'number' && Number.isSafeInteger(value) && value >= 0 ? value : 0);

export function readReport(data: unknown): AttendanceReport {
  if (!record(data) || !Array.isArray(data.rows)) throw new Error('Invalid report');
  const rows = data.rows.filter(row => record(row) && positive(row.meetingId) && typeof row.title === 'string' && typeof row.startsAt === 'string'
    && typeof row.enteredAt === 'string').map(row => {
    const value = row as Record<string, unknown>;
    return {
      meetingId: value.meetingId as number, title: value.title as string, committeeId: count(value.committeeId),
      committeeName: typeof value.committeeName === 'string' ? value.committeeName : '', startsAt: value.startsAt as string,
      enteredAt: value.enteredAt as string, leftAt: typeof value.leftAt === 'string' ? value.leftAt : null,
      durationMinutes: typeof value.durationMinutes === 'number' ? value.durationMinutes : null,
    };
  });
  const committees = Array.isArray(data.committees) ? data.committees.filter(row => record(row) && positive(row.committeeId) && typeof row.committeeName === 'string')
    .map(row => ({ committeeId: (row as Record<string, unknown>).committeeId as number, committeeName: (row as Record<string, unknown>).committeeName as string })) : [];
  return { totalMeetings: count(data.totalMeetings), totalMinutes: count(data.totalMinutes), totalCommittees: count(data.totalCommittees),
    missingExit: data.missingExit === true, rows, committees };
}
