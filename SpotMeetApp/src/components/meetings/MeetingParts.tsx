import React from 'react';
import { Text, View } from 'react-native';
import UserAvatar from '../profile/UserAvatar';
import { useMeetingStyles } from './meetingStyles';
import { dateBlock, meetingDates, meetingTimes, type Meeting } from '../../utils/meetings';

/** Date block, title and "Financeiro · Sexta, 30 de outubro · 10:00 às 11:00", at the top of the minutes and vote screens. */
export function MeetingHeader({ meeting, children }: { meeting: Meeting; children?: React.ReactNode }) {
  const { styles } = useMeetingStyles();
  const block = dateBlock(meeting.startsAt);
  return <View style={[styles.card, styles.row, { alignItems: 'flex-start' }]}>
    <View style={styles.dateBlock}>
      <Text style={styles.dateBlockSmall}>{block.weekday}</Text><Text style={styles.dateBlockDay}>{block.day}</Text><Text style={styles.dateBlockSmall}>{block.month}</Text>
    </View>
    <View style={styles.flex}>
      <Text style={styles.cardTitle}>{meeting.title}</Text>
      <Text style={styles.subtitle}>{meeting.committeeName} · {meetingDates(meeting)} · {meetingTimes(meeting)}</Text>
      {children}
    </View>
  </View>;
}

/** People in two columns, with photo or initials, as in the present and absent lists of the design. */
export function PeopleGrid({ people }: { people: { userId: number; userName: string }[] }) {
  const { colors, styles } = useMeetingStyles();
  return <View style={{ flexDirection: 'row', flexWrap: 'wrap', marginBottom: 12 }}>
    {people.map(person => <View key={person.userId} style={[styles.row, { width: '50%', gap: 8, paddingVertical: 5, paddingRight: 6 }]}>
      <UserAvatar userId={person.userId} name={person.userName} style={[styles.avatar, { width: 32, height: 32, borderRadius: 16 }]} textStyle={[styles.avatarText, { fontSize: 12 }]} />
      <Text style={[styles.subtitle, styles.flex, { color: colors.text }]} numberOfLines={1}>{person.userName}</Text>
    </View>)}
  </View>;
}
