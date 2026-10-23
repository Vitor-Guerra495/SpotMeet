// Lets the focused screen hold a tab change while it asks about unsaved changes.
// The guard returns true when it took over; it calls `proceed` once the user decides to leave.
type LeaveGuard = (proceed: () => void) => boolean;

let guard: LeaveGuard | null = null;

export function setLeaveGuard(next: LeaveGuard): () => void {
  guard = next;
  return () => { if (guard === next) guard = null; };
}

export function interceptLeave(proceed: () => void): boolean {
  return guard ? guard(proceed) : false;
}
