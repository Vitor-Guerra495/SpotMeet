export type MeetingStatus = 'SCHEDULED' | 'IN_PROGRESS' | 'ENDED' | 'CANCELED';
export type MeetingPriority = 'LOW' | 'MEDIUM' | 'HIGH';
export type MeetingModality = 'IN_PERSON' | 'ONLINE' | 'HYBRID';
export type InvitationResponse = 'PENDING' | 'ACCEPTED' | 'DECLINED';
export type AttendanceSituation = 'PRESENT' | 'EXIT_NOT_RECORDED' | 'ABSENT';
export type Frequency = 'DAILY' | 'WEEKLY' | 'MONTHLY' | 'YEARLY';

export interface MeetingParticipant {
  userId: number; userName: string; response: InvitationResponse; responseComment: string | null;
  enteredAt: string | null; enteredByName: string | null; leftAt: string | null; leftByName: string | null;
}

/** Repetition rule. weekDays uses 1 = Monday ... 7 = Sunday; no end date and no occurrences means it never ends. */
export interface Recurrence {
  frequency: Frequency; interval: number; weekDays: number[]; monthlyByWeekday: boolean;
  endDate: string | null; occurrences: number | null;
}

export interface MeetingAttachment { id: number; fileName: string; contentType: string; sizeBytes: number; }

export interface Meeting {
  id: number; committeeId: number; committeeName: string; organizationId: number; title: string;
  agenda: string | null; location: string | null; link: string | null; priority: MeetingPriority;
  modality: MeetingModality | null; allDay: boolean;
  startsAt: string; endsAt: string; status: MeetingStatus; createdByUserId: number | null; createdByName: string;
  cancelReason: string | null; seriesId: number | null; recurrence: Recurrence | null; attachment: MeetingAttachment | null;
  attendanceListGeneratedAt: string | null; myResponse: InvitationResponse | null; myEnteredAt: string | null; myLeftAt: string | null;
  totalInvited: number; totalAccepted: number; totalDeclined: number; totalPending: number;
  participants: MeetingParticipant[] | null;
  /** Who declined, with the reason; the server fills it only for the president of the committee. */
  declines: MeetingParticipant[];
  /** Situation of the minutes and whether the viewer still has to approve or reject them. */
  minutesStatus: 'DRAFT' | 'IN_APPROVAL' | 'APPROVED' | 'REJECTED' | null; minutesToAnswer: boolean;
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
export const MODALITY_LABELS: Record<MeetingModality, string> = { IN_PERSON: 'Presencial', ONLINE: 'Online', HYBRID: 'Híbrida' };
export const STATUS_LABELS: Record<MeetingStatus, string> = {
  SCHEDULED: 'Agendada', IN_PROGRESS: 'Em andamento', ENDED: 'Encerrada', CANCELED: 'Cancelada',
};
export const RESPONSE_LABELS: Record<InvitationResponse, string> = {
  PENDING: 'Pendente', ACCEPTED: 'Aceitou', DECLINED: 'Recusou',
};
export const SITUATION_LABELS: Record<AttendanceSituation, string> = {
  PRESENT: 'Presente', EXIT_NOT_RECORDED: 'Presente, saída não registrada', ABSENT: 'Ausente',
};

export const MAX_ATTACHMENT_BYTES = 10 * 1024 * 1024;
const LINK_PATTERN = /^https?:\/\/\S+$/i;
// Same rule as the server: a host with a dot; the https:// is added when missing.
const ADDRESS_PATTERN = /^(https?:\/\/)?[^\s/?#:@]+\.[^\s/?#:@]+(:\d{1,5})?([/?#]\S*)?$/i;

const WEEKDAYS = ['domingo', 'segunda', 'terça', 'quarta', 'quinta', 'sexta', 'sábado'];
const MONTHS = ['janeiro', 'fevereiro', 'março', 'abril', 'maio', 'junho', 'julho', 'agosto', 'setembro', 'outubro', 'novembro', 'dezembro'];
const ORDINALS = ['primeira', 'segunda', 'terceira', 'quarta', 'última'];
const ORDINALS_MALE = ['primeiro', 'segundo', 'terceiro', 'quarto', 'último'];

const pad = (value: number) => String(value).padStart(2, '0');
export const capitalize = (text: string) => text.charAt(0).toUpperCase() + text.slice(1);

/** The server sends local date-times without a zone; they become a Date of the phone clock without shifting. */
export function parseLocal(value: string): Date {
  const match = /^(\d{4})-(\d{2})-(\d{2})(?:T(\d{2}):(\d{2})(?::(\d{2}))?)?/.exec(value);
  if (!match) return new Date(NaN);
  const [year, month, day, hour, minute, second] = match.slice(1).map(part => Number(part ?? 0));
  return new Date(year, month - 1, day, hour, minute, second);
}

export function toLocalIso(date: Date): string {
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}:00`;
}

export function toLocalDate(date: Date): string {
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

export const sameDay = (a: Date, b: Date) => a.getFullYear() === b.getFullYear() && a.getMonth() === b.getMonth() && a.getDate() === b.getDate();

export function weekdayName(date: Date): string { return WEEKDAYS[date.getDay()]; }

/** "segunda, 2 de novembro", with the year only when it is not the current one. */
export function formatDay(date: Date): string {
  const text = `${weekdayName(date)}, ${date.getDate()} de ${MONTHS[date.getMonth()]}`;
  return date.getFullYear() === new Date().getFullYear() ? text : `${text} de ${date.getFullYear()}`;
}

/** "15 de dezembro" */
export function formatMonthDay(date: Date): string { return `${date.getDate()} de ${MONTHS[date.getMonth()]}`; }

export function formatClock(date: Date): string { return `${pad(date.getHours())}:${pad(date.getMinutes())}`; }

/** Date block of the list cards: TER, 03, NOV. */
export function dateBlock(value: string) {
  const date = parseLocal(value);
  return { weekday: WEEKDAYS[date.getDay()].slice(0, 3).toUpperCase(), day: pad(date.getDate()), month: MONTHS[date.getMonth()].slice(0, 3).toUpperCase() };
}

/** "Segunda, 2 de novembro" or "Segunda, 2 de novembro a quarta, 4 de novembro". */
export function meetingDates(meeting: Pick<Meeting, 'startsAt' | 'endsAt'>): string {
  const start = parseLocal(meeting.startsAt);
  const end = parseLocal(meeting.endsAt);
  return capitalize(sameDay(start, end) ? formatDay(start) : `${formatDay(start)} a ${formatDay(end)}`);
}

/** "16:00 às 17:00", "Dia inteiro" or, across days, "22:00 (02/11) às 01:00 (03/11)". */
export function meetingTimes(meeting: Pick<Meeting, 'startsAt' | 'endsAt' | 'allDay'>): string {
  if (meeting.allDay) return 'Dia inteiro';
  const start = parseLocal(meeting.startsAt);
  const end = parseLocal(meeting.endsAt);
  if (sameDay(start, end)) return `${formatClock(start)} às ${formatClock(end)}`;
  const short = (date: Date) => `${pad(date.getDate())}/${pad(date.getMonth() + 1)}`;
  return `${formatClock(start)} (${short(start)}) às ${formatClock(end)} (${short(end)})`;
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

export function formatSize(bytes: number): string {
  if (bytes < 1024 * 1024) return `${Math.max(1, Math.round(bytes / 1024))} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1).replace('.', ',')} MB`;
}

export function validateLink(value: string): true | string {
  const text = value.trim();
  if (!text) return true;
  if (text.length > 300) return 'Use até 300 caracteres.';
  return ADDRESS_PATTERN.test(text) ? true : 'Informe um endereço válido, como meet.google.com/abc-defg-hij.';
}

export function isOpenableLink(value: string | null): value is string {
  return !!value && LINK_PATTERN.test(value);
}

/** Link as people type it: without https:// and www. */
export function displayLink(value: string): string {
  return value.replace(/^https?:\/\//i, '').replace(/^www\./i, '');
}

export function formatDuration(minutes: number | null): string {
  if (minutes === null) return '';
  return minutes >= 60 ? `${Math.floor(minutes / 60)} h ${pad(minutes % 60)} min` : `${minutes} min`;
}

const male = (weekday: number) => weekday === 0 || weekday === 6;
const isoToJs = (iso: number) => iso % 7;

function nthWeekday(anchor: Date): string {
  const nth = Math.min(Math.floor((anchor.getDate() - 1) / 7), 4);
  return `${(male(anchor.getDay()) ? ORDINALS_MALE : ORDINALS)[nth]} ${weekdayName(anchor)}`;
}

function joinNames(names: string[]): string {
  return names.length === 1 ? names[0] : `${names.slice(0, -1).join(', ')} e ${names[names.length - 1]}`;
}

/** Same text as the e-mails (MeetingText on the server): "toda segunda e quarta, até 15 de dezembro". */
export function recurrenceText(rule: Recurrence, anchor: Date): string {
  const every = Math.max(1, rule.interval || 1);
  let text: string;
  if (rule.frequency === 'DAILY') {
    text = every === 1 ? 'todos os dias' : `a cada ${every} dias`;
  } else if (rule.frequency === 'WEEKLY') {
    const days = (rule.weekDays.length ? [...rule.weekDays] : [anchor.getDay() || 7]).sort((a, b) => a - b);
    if (every === 1 && days.join(',') === '1,2,3,4,5') {
      text = 'todos os dias úteis (segunda a sexta)';
    } else {
      const allMale = days.every(day => male(isoToJs(day)));
      const names = joinNames(days.map(day => WEEKDAYS[isoToJs(day)]));
      text = every === 1 ? `${allMale ? 'todo' : 'toda'} ${names}` : `a cada ${every} semanas, ${allMale ? 'no' : 'na'} ${names}`;
    }
  } else if (rule.frequency === 'MONTHLY') {
    const target = rule.monthlyByWeekday ? nthWeekday(anchor) : `dia ${anchor.getDate()}`;
    const article = rule.monthlyByWeekday && !male(anchor.getDay()) ? 'na' : 'no';
    if (every === 1) text = rule.monthlyByWeekday ? `${male(anchor.getDay()) ? 'todo' : 'toda'} ${target} do mês` : `todo ${target}`;
    else text = `a cada ${every} meses, ${article} ${target}`;
  } else {
    text = every === 1 ? `todo ano, em ${formatMonthDay(anchor)}` : `a cada ${every} anos, em ${formatMonthDay(anchor)}`;
  }
  if (rule.endDate) text += `, até ${formatMonthDay(parseLocal(rule.endDate))}`;
  else if (rule.occurrences) text += `, ${rule.occurrences} ${rule.occurrences === 1 ? 'vez' : 'vezes'}`;
  return text;
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
    && maybeText(value.agenda) && maybeText(value.location) && maybeText(value.link) && maybeText(value.cancelReason)
    && (value.participants === null || value.participants === undefined
      || (Array.isArray(value.participants) && value.participants.every(validParticipant)));
}

function readRecurrence(value: unknown): Recurrence | null {
  if (!record(value) || !text(value.frequency)) return null;
  return {
    frequency: value.frequency as Frequency, interval: positive(value.interval) ? value.interval as number : 1,
    weekDays: Array.isArray(value.weekDays) ? value.weekDays.filter(positive) as number[] : [],
    monthlyByWeekday: value.monthlyByWeekday === true, endDate: text(value.endDate) ? value.endDate as string : null,
    occurrences: positive(value.occurrences) ? value.occurrences as number : null,
  };
}

function readAttachment(value: unknown): MeetingAttachment | null {
  if (!record(value) || !positive(value.id) || !text(value.fileName) || !text(value.contentType)) return null;
  return { id: value.id as number, fileName: value.fileName as string, contentType: value.contentType as string,
    sizeBytes: typeof value.sizeBytes === 'number' ? value.sizeBytes : 0 };
}

/** Server data is checked before it reaches the screen, like the other lists of the app. */
export function readMeeting(data: unknown): Meeting {
  if (!validMeeting(data)) throw new Error('Invalid meeting response');
  const raw = data as unknown as Record<string, unknown>;
  return { ...data, participants: data.participants ?? null, myResponse: data.myResponse ?? null,
    attendanceListGeneratedAt: data.attendanceListGeneratedAt ?? null, allDay: raw.allDay === true,
    myEnteredAt: text(raw.myEnteredAt) ? raw.myEnteredAt as string : null, myLeftAt: text(raw.myLeftAt) ? raw.myLeftAt as string : null,
    modality: text(raw.modality) ? raw.modality as MeetingModality : null, cancelReason: data.cancelReason ?? null,
    seriesId: positive(raw.seriesId) ? raw.seriesId as number : null, recurrence: readRecurrence(raw.recurrence),
    attachment: readAttachment(raw.attachment), createdByUserId: positive(raw.createdByUserId) ? raw.createdByUserId as number : null,
    minutesStatus: text(raw.minutesStatus) ? raw.minutesStatus as Meeting['minutesStatus'] : null, minutesToAnswer: raw.minutesToAnswer === true,
    declines: Array.isArray(raw.declines) ? raw.declines.filter(validParticipant) : [] } as Meeting;
}

export function readMeetings(data: unknown): Meeting[] {
  if (!Array.isArray(data)) throw new Error('Invalid meeting list');
  return data.map(readMeeting);
}

export function readAttachmentResponse(data: unknown): MeetingAttachment {
  const attachment = readAttachment(data);
  if (!attachment) throw new Error('Invalid attachment response');
  return attachment;
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
