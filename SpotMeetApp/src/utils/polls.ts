export type VoteChoice = 'YES' | 'NO' | 'ABSTAIN';
export type PollResult = 'APPROVED' | 'REJECTED' | 'TIE' | 'NO_VOTES';

export interface PollVote { userId: number; userName: string; choice: VoteChoice; justification: string | null; votedAt: string | null; }
export interface PollPerson { userId: number; userName: string; }

/** Counts and votes are null for the participants while the vote is open; the president sees them live. */
export interface Poll {
  id: number; question: string; description: string | null; openedByName: string; openedAt: string;
  closed: boolean; closedAt: string | null; closedByName: string | null; closedAutomatically: boolean;
  eligible: number; totalVotes: number; canVote: boolean; myVote: PollVote | null; result: PollResult | null;
  yes: number | null; no: number | null; abstain: number | null; votes: PollVote[] | null; notVoted: PollPerson[] | null;
}

export const CHOICE_LABELS: Record<VoteChoice, string> = { YES: 'Sim', NO: 'Não', ABSTAIN: 'Abstenção' };
export const MAX_QUESTION = 150;
export const MAX_DESCRIPTION = 500;
export const MAX_JUSTIFICATION = 280;

/**
 * Whole percentages that always add up to 100: each value is rounded down and the points left
 * go to the largest remainders (5, 2 and 1 of 8 give 63%, 25% and 12%).
 */
export function percentages(counts: number[]): number[] {
  const total = counts.reduce((sum, value) => sum + value, 0);
  if (!total) return counts.map(() => 0);
  const exact = counts.map(value => (value * 100) / total);
  const result = exact.map(Math.floor);
  let left = 100 - result.reduce((sum, value) => sum + value, 0);
  const order = exact.map((value, index) => ({ index, rest: value - Math.floor(value) })).sort((a, b) => b.rest - a.rest || a.index - b.index);
  for (const item of order) { if (left <= 0) break; result[item.index] += 1; left -= 1; }
  return result;
}

const plural = (value: number, one: string, many: string) => `${value} ${value === 1 ? one : many}`;

/** Title of the result: "Aprovada por 5 a 2, 1 abstenção", "Empate", "Sem votos". */
export function resultTitle(poll: Poll): string {
  const yes = poll.yes ?? 0, no = poll.no ?? 0, abstain = poll.abstain ?? 0;
  const tail = abstain ? `, ${plural(abstain, 'abstenção', 'abstenções')}` : '';
  if (poll.result === 'APPROVED') return `Aprovada por ${yes} a ${no}${tail}`;
  if (poll.result === 'REJECTED') return `Rejeitada por ${no} a ${yes}${tail}`;
  if (poll.result === 'TIE') return 'Empate';
  return 'Sem votos';
}

/** Short text of the result for the card of the meeting: "Aprovada por 5 a 2, 1 abstenção" or "Empate: 4 a 4". */
export function resultShort(poll: Poll): string {
  if (poll.result === 'TIE') return `Empate: ${poll.yes ?? 0} a ${poll.no ?? 0}`;
  return resultTitle(poll);
}

/** "5 votos Sim, 2 Não e 1 abstenção." */
export function resultDetail(poll: Poll): string {
  if (poll.result === 'NO_VOTES') return 'Ninguém votou. A votação foi encerrada sem nenhum voto e não tem resultado.';
  const yes = poll.yes ?? 0, no = poll.no ?? 0, abstain = poll.abstain ?? 0;
  const parts = `${plural(yes, 'voto Sim', 'votos Sim')}${abstain ? `, ${no} Não e ${plural(abstain, 'abstenção', 'abstenções')}` : ` e ${no} Não`}.`;
  return poll.result === 'TIE' ? `${parts.slice(0, -1)}. Nenhuma opção venceu.` : parts;
}

const record = (value: unknown): value is Record<string, unknown> => value !== null && typeof value === 'object' && !Array.isArray(value);
const positive = (value: unknown) => typeof value === 'number' && Number.isSafeInteger(value) && value > 0;
const textOrNull = (value: unknown) => (typeof value === 'string' ? value : null);
const countOrNull = (value: unknown) => (typeof value === 'number' && Number.isSafeInteger(value) && value >= 0 ? value : null);
const isChoice = (value: unknown): value is VoteChoice => value === 'YES' || value === 'NO' || value === 'ABSTAIN';

function readVote(value: unknown): PollVote | null {
  if (!record(value) || !positive(value.userId) || typeof value.userName !== 'string' || !isChoice(value.choice)) return null;
  return { userId: value.userId as number, userName: value.userName, choice: value.choice,
    justification: textOrNull(value.justification), votedAt: textOrNull(value.votedAt) };
}

function readPoll(value: unknown): Poll {
  if (!record(value) || !positive(value.id) || typeof value.question !== 'string') throw new Error('Invalid poll');
  const result = value.result === 'APPROVED' || value.result === 'REJECTED' || value.result === 'TIE' || value.result === 'NO_VOTES' ? value.result : null;
  return {
    id: value.id as number, question: value.question, description: textOrNull(value.description),
    openedByName: textOrNull(value.openedByName) ?? '', openedAt: textOrNull(value.openedAt) ?? '',
    closed: value.closed === true, closedAt: textOrNull(value.closedAt), closedByName: textOrNull(value.closedByName),
    closedAutomatically: value.closedAutomatically === true, eligible: countOrNull(value.eligible) ?? 0, totalVotes: countOrNull(value.totalVotes) ?? 0,
    canVote: value.canVote === true, myVote: readVote(value.myVote), result,
    yes: countOrNull(value.yes), no: countOrNull(value.no), abstain: countOrNull(value.abstain),
    votes: Array.isArray(value.votes) ? value.votes.map(readVote).filter((row): row is PollVote => row !== null) : null,
    notVoted: Array.isArray(value.notVoted) ? value.notVoted.filter(row => record(row) && positive(row.userId) && typeof row.userName === 'string')
      .map(row => ({ userId: row.userId as number, userName: row.userName as string })) : null,
  };
}

export function readPolls(data: unknown): Poll[] {
  if (!Array.isArray(data)) throw new Error('Invalid poll list');
  return data.map(readPoll);
}
