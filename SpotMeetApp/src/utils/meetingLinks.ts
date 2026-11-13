import { Linking } from 'react-native';

// Address opened by the "Abrir no SpotMeet" page of the e-mails: spotmeet://reunioes/{id}.
const PATTERN = /^spotmeet:\/\/reunioes\/(\d+)/i;

let pending: number | null = null;
let initialRead = false;
const listeners = new Set<(meetingId: number) => void>();

export function parseMeetingLink(url?: string | null): number | null {
  const match = url ? PATTERN.exec(url) : null;
  return match ? Number(match[1]) : null;
}

/** Meeting waiting to be opened; it stays until the Reuniões screen shows it, even across the login. */
export function takePendingMeeting(): number | null {
  const meetingId = pending;
  pending = null;
  return meetingId;
}

export function onMeetingLink(listener: (meetingId: number) => void): () => void {
  listeners.add(listener);
  return () => { listeners.delete(listener); };
}

/** Listens to the links that open the app; onOpen moves the user to the Reuniões tab. */
export function startMeetingLinks(onOpen: () => void): () => void {
  const handle = (url?: string | null) => {
    const meetingId = parseMeetingLink(url);
    if (!meetingId) return;
    pending = meetingId;
    onOpen();
    listeners.forEach(listener => listener(meetingId));
  };
  // The link that started the app is read once; later calls only listen.
  if (!initialRead) {
    initialRead = true;
    void Linking.getInitialURL().then(handle).catch(() => undefined);
  }
  const subscription = Linking.addEventListener('url', event => handle(event.url));
  return () => subscription.remove();
}
