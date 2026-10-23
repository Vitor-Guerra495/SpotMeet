export type MeetingStatus = 'SCHEDULED' | 'IN_PROGRESS' | 'ENDED' | 'CANCELED';
export type MeetingPriority = 'LOW' | 'MEDIUM' | 'HIGH';
export type InvitationResponse = 'PENDING' | 'ACCEPTED' | 'DECLINED';
export type AttendanceSituation = 'PRESENT' | 'EXIT_NOT_RECORDED' | 'ABSENT';

export interface MeetingParticipant {
  userId: number; userName: string; response: InvitationResponse; responseComment: string | null;
  enteredAt: string | null; enteredByName: string | null; leftAt: string | null; leftByName: string | null;
}

export interface Meeting {
  id: number; committeeId: number; committeeName: string; organizationId: number; title: string;
  agenda: string | null; location: string | null; link: string | null; priority: MeetingPriority;
  startsAt: string; endsAt: string; status: MeetingStatus; createdByName: string;
  attendanceListGeneratedAt: string | null; myResponse: InvitationResponse | null;
  totalInvited: number; totalAccepted: number; totalDeclined: number; totalPending: number;
  participants: MeetingParticipant[] | null;
}

export interface AttendanceRow {
  userId: number; userName: string; response: InvitationResponse; responseComment: string | null;
  enteredAt: string | null; enteredByName: string | null; leftAt: string | null; leftByName: string | null;
  durationMinutes: number | null; situation: AttendanceSituation;
}

export interface AttendanceList {
  meetingId: number; title: string; committeeName: string; startsAt: string; endsAt: string;
  generatedAt: string | null; generatedByName: string | null;
  totalInvited: number; totalPresent: number; totalAbsent: number; rows: AttendanceRow[];
}

export interface PresidedCommittee { committeeId: number; committeeName: string; organizationId: number; }
export interface CommitteeMemberRow { userId: number; userName: string; role: 'COMMITTEE_ADMIN' | 'MEMBER'; }

export const PRIORITY_LABELS: Record<MeetingPriority, string> = { LOW: 'Baixa', MEDIUM: 'Média', HIGH: 'Alta' };
export const STATUS_LABELS: Record<MeetingStatus, string> = {
  SCHEDULED: 'Agendada', IN_PROGRESS: 'Em andamento', ENDED: 'Encerrada', CANCELED: 'Cancelada',
};
export const RESPONSE_LABELS: Record<InvitationResponse, string> = {
  PENDING: 'Sem resposta', ACCEPTED: 'Aceitou', DECLINED: 'Recusou',
};
export const SITUATION_LABELS: Record<AttendanceSituation, string> = {
  PRESENT: 'Presente', EXIT_NOT_RECORDED: 'Presente, saída não registrada', ABSENT: 'Ausente',
};

export const MAX_DURATION_HOURS = 24;
export const DATE_TIME_PLACEHOLDER = 'dd/mm/aaaa hh:mm';
const DATE_TIME_PATTERN = /^(\d{2})\/(\d{2})\/(\d{4}) (\d{2}):(\d{2})$/;
const LINK_PATTERN = /^https?:\/\/\S+$/i;

const pad = (value: number) => String(value).padStart(2, '0');

/** Formats the digits typed so far as dd/mm/aaaa hh:mm. */
export function maskDateTime(input: string): string {
  const digits = input.replace(/\D/g, '').slice(0, 12);
  const parts = [digits.slice(0, 2), digits.slice(2, 4), digits.slice(4, 8)].filter(Boolean).join('/');
  return digits.length > 8 ? `${parts} ${digits.slice(8, 10)}${digits.length > 10 ? `:${digits.slice(10)}` : ''}` : parts;
}

/** Converts dd/mm/aaaa hh:mm into the local ISO text the server expects, or null when it is not a real date. */
export function parseDateTime(text: string): string | null {
  const match = DATE_TIME_PATTERN.exec(text.trim());
  if (!match) return null;
  const [day, month, year, hour, minute] = match.slice(1).map(Number);
  const date = new Date(year, month - 1, day, hour, minute);
  const real = date.getFullYear() === year && date.getMonth() === month - 1 && date.getDate() === day
    && date.getHours() === hour && date.getMinutes() === minute;
  return real ? `${year}-${pad(month)}-${pad(day)}T${pad(hour)}:${pad(minute)}:00` : null;
}

/** The server sends local date-times without a zone, so they are read as text and never shifted. */
export function formatDateTime(value: string | null): string {
  const match = value ? /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})/.exec(value) : null;
  return match ? `${match[3]}/${match[2]}/${match[1]} ${match[4]}:${match[5]}` : '';
}

export function formatTime(value: string | null): string {
  const match = value ? /T(\d{2}):(\d{2})/.exec(value) : null;
  return match ? `${match[1]}:${match[2]}` : '--:--';
}

export function toInputText(value: string): string { return formatDateTime(value); }

export function validateLink(value: string): true | string {
  const text = value.trim();
  if (!text) return true;
  if (text.length > 300) return 'Use até 300 caracteres.';
  return LINK_PATTERN.test(text) ? true : 'O link deve começar com http:// ou https://.';
}

export function isOpenableLink(value: string | null): value is string {
  return !!value && LINK_PATTERN.test(value);
}

export function formatDuration(minutes: number | null): string {
  if (minutes === null) return '';
  return minutes >= 60 ? `${Math.floor(minutes / 60)} h ${pad(minutes % 60)} min` : `${minutes} min`;
}

const record = (value: unknown): value is Record<string, unknown> => value !== null && typeof value === 'object' && !Array.isArray(value);
const positive = (value: unknown) => typeof value === 'number' && Number.isSafeInteger(value) && value > 0;
const text = (value: unknown) => typeof value === 'string';
const maybeText = (value: unknown) => value === null || value === undefined || typeof value === 'string';

function validParticipant(value: unknown): boolean {
  return record(value) && positive(value.userId) && text(value.userName) && text(value.response)
    && maybeText(value.responseComment) && maybeText(value.enteredAt) && maybeText(value.leftAt);
}

function validMeeting(value: unknown): value is Meeting {
  return record(value) && positive(value.id) && positive(value.committeeId) && text(value.title) && text(value.committeeName)
    && text(value.startsAt) && text(value.endsAt) && text(value.status) && text(value.priority)
    && maybeText(value.agenda) && maybeText(value.location) && maybeText(value.link)
    && (value.participants === null || value.participants === undefined
      || (Array.isArray(value.participants) && value.participants.every(validParticipant)));
}

/** Server data is checked before it reaches the screen, like the other lists of the app. */
export function readMeeting(data: unknown): Meeting {
  if (!validMeeting(data)) throw new Error('Invalid meeting response');
  return { ...data, participants: data.participants ?? null, myResponse: data.myResponse ?? null,
    attendanceListGeneratedAt: data.attendanceListGeneratedAt ?? null } as Meeting;
}

export function readMeetings(data: unknown): Meeting[] {
  if (!Array.isArray(data)) throw new Error('Invalid meeting list');
  return data.map(readMeeting);
}

export function readPresided(data: unknown): PresidedCommittee[] {
  if (!Array.isArray(data) || !data.every(row => record(row) && positive(row.committeeId) && text(row.committeeName))) {
    throw new Error('Invalid committee list');
  }
  return data as PresidedCommittee[];
}

export function readCommitteeMembers(data: unknown): CommitteeMemberRow[] {
  if (!Array.isArray(data) || !data.every(row => record(row) && positive(row.userId) && text(row.userName) && text(row.role))) {
    throw new Error('Invalid member list');
  }
  return data as CommitteeMemberRow[];
}

export function readAttendanceList(data: unknown): AttendanceList {
  if (!record(data) || !positive(data.meetingId) || !text(data.title) || !Array.isArray(data.rows)
    || !data.rows.every(row => record(row) && positive(row.userId) && text(row.userName) && text(row.situation))) {
    throw new Error('Invalid attendance list');
  }
  return data as unknown as AttendanceList;
}

export function errorText(status: number, body: string, fallback: string): string {
  if (status >= 500) return fallback;
  if (status === 401) return 'Sua sessão foi encerrada. Entre novamente.';
  if (!body) return fallback;
  try {
    const value: unknown = JSON.parse(body);
    if (record(value)) {
      if (typeof value.message === 'string') return value.message;
      if (record(value.errors)) {
        const first = Object.values(value.errors).find((item): item is string => typeof item === 'string');
        if (first) return first;
      }
    }
    return fallback;
  } catch {
    return body.length <= 500 && !/[<>]/.test(body) ? body : fallback;
  }
}
