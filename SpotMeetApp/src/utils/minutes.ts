import { parseLocal, formatClock, formatMonthDay } from './meetings';

export type MinutesStatus = 'DRAFT' | 'IN_APPROVAL' | 'APPROVED' | 'REJECTED';

export interface MinutesPerson { userId: number; userName: string; }

/** Answer of a present person; approved is null while the person has not answered. */
export interface MinutesAnswer { userId: number; userName: string; approved: boolean | null; reason: string | null; respondedAt: string | null; }

export interface Minutes {
  meetingId: number; status: MinutesStatus | null;
  topics: string | null; decisions: string | null; notes: string | null; writtenByName: string | null;
  updatedAt: string | null; submittedAt: string | null; closedAt: string | null;
  present: MinutesPerson[]; absent: MinutesPerson[];
  totalApproved: number; totalRejected: number; totalPending: number;
  viewerPresent: boolean; canAnswer: boolean; myAnswer: MinutesAnswer | null; answers: MinutesAnswer[] | null;
}

export const MINUTES_LABELS: Record<MinutesStatus, string> = {
  DRAFT: 'Rascunho', IN_APPROVAL: 'Em aprovação', APPROVED: 'Aprovada', REJECTED: 'Reprovada',
};

export const MAX_TOPICS = 4000;
export const MAX_NOTES = 2000;
export const MAX_REASON = 300;

/** "30 de outubro, às 11:20", as the design writes the moments of the minutes. */
export function formatMoment(value: string | null): string {
  if (!value) return '';
  const date = parseLocal(value);
  return `${formatMonthDay(date)}, às ${formatClock(date)}`;
}

/** "4 aprovaram, 1 reprovou, 2 sem resposta." */
export function countText(minutes: Pick<Minutes, 'totalApproved' | 'totalRejected' | 'totalPending'>): string {
  const { totalApproved: yes, totalRejected: no, totalPending: pending } = minutes;
  return `${yes} ${yes === 1 ? 'aprovou' : 'aprovaram'}, ${no} ${no === 1 ? 'reprovou' : 'reprovaram'}, ${pending} sem resposta.`;
}

/** Why a closed minutes was rejected: majority against, tie or no answer. */
export function rejectionText(minutes: Pick<Minutes, 'totalApproved' | 'totalRejected'>): string {
  if (minutes.totalApproved === 0 && minutes.totalRejected === 0) return 'Sem respostas, a ata não é aprovada.';
  if (minutes.totalApproved === minutes.totalRejected) return 'Houve empate entre as respostas.';
  return 'A maioria de quem respondeu reprovou.';
}

const record = (value: unknown): value is Record<string, unknown> => value !== null && typeof value === 'object' && !Array.isArray(value);
const positive = (value: unknown) => typeof value === 'number' && Number.isSafeInteger(value) && value > 0;
const textOrNull = (value: unknown) => (typeof value === 'string' ? value : null);
const count = (value: unknown) => (typeof value === 'number' && Number.isSafeInteger(value) && value >= 0 ? value : 0);

function readPeople(value: unknown): MinutesPerson[] {
  if (!Array.isArray(value)) return [];
  return value.filter(row => record(row) && positive(row.userId) && typeof row.userName === 'string')
    .map(row => ({ userId: row.userId as number, userName: row.userName as string }));
}

function readAnswer(value: unknown): MinutesAnswer | null {
  if (!record(value) || !positive(value.userId) || typeof value.userName !== 'string') return null;
  return { userId: value.userId as number, userName: value.userName, approved: typeof value.approved === 'boolean' ? value.approved : null,
    reason: textOrNull(value.reason), respondedAt: textOrNull(value.respondedAt) };
}

/** Server data is checked before it reaches the screen, like the other lists of the app. */
export function readMinutes(data: unknown): Minutes {
  if (!record(data) || !positive(data.meetingId)) throw new Error('Invalid minutes response');
  const status = typeof data.status === 'string' && data.status in MINUTES_LABELS ? data.status as MinutesStatus : null;
  return {
    meetingId: data.meetingId as number, status,
    topics: textOrNull(data.topics), decisions: textOrNull(data.decisions), notes: textOrNull(data.notes), writtenByName: textOrNull(data.writtenByName),
    updatedAt: textOrNull(data.updatedAt), submittedAt: textOrNull(data.submittedAt), closedAt: textOrNull(data.closedAt),
    present: readPeople(data.present), absent: readPeople(data.absent),
    totalApproved: count(data.totalApproved), totalRejected: count(data.totalRejected), totalPending: count(data.totalPending),
    viewerPresent: data.viewerPresent === true, canAnswer: data.canAnswer === true, myAnswer: readAnswer(data.myAnswer),
    answers: Array.isArray(data.answers) ? data.answers.map(readAnswer).filter((row): row is MinutesAnswer => row !== null) : null,
  };
}
