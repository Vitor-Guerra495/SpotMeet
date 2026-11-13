import React from 'react';
import { Text, TextInput, TouchableOpacity, View } from 'react-native';
import { Ionicons } from '@react-native-vector-icons/ionicons';
import FormButton from '../forms/FormButton';
import { useMeetingStyles } from '../meetings/meetingStyles';
import { HELP_TOPICS, PROFILE_HINTS, PROFILE_LABELS, searchTopics, type HelpProfile, type HelpTopic } from '../../utils/helpTopics';

const PROFILES: HelpProfile[] = ['all', 'member', 'president', 'owner', 'admin'];

interface Props {
  /** Open topic, search and open profile are kept by the screen, so coming back from a topic keeps the list as it was. */
  topicId: string | null;
  onOpenTopic: (topicId: string | null) => void;
  query: string;
  onQuery: (query: string) => void;
  openProfile: HelpProfile | null;
  onOpenProfile: (profile: HelpProfile | null) => void;
}

/** "Ajuda": the user manual by profile, with a search and one page per topic. Text only, no images. */
export default function HelpCenter({ topicId, onOpenTopic, query, onQuery: setQuery, openProfile, onOpenProfile: setOpenProfile }: Props) {
  const { colors, styles } = useMeetingStyles();
  const accent = colors.isDark ? '#ADB5EF' : colors.primary;

  const topicRow = (topic: HelpTopic, showProfile = false) => <TouchableOpacity key={topic.id} accessibilityRole="button" accessibilityLabel={topic.title}
    style={[styles.personRow, styles.row, { minHeight: 52 }]} onPress={() => onOpenTopic(topic.id)}>
    <View style={styles.flex}>
      <Text style={styles.optionText}>{topic.title}</Text>
      {showProfile ? <Text style={styles.subtitle}>{PROFILE_LABELS[topic.profile]}</Text> : null}
    </View>
    <Ionicons name="chevron-forward" size={20} color={colors.subtext} />
  </TouchableOpacity>;

  const topic = topicId ? HELP_TOPICS.find(item => item.id === topicId) : null;
  if (topic) {
    return <View>
      <Text style={styles.sheetTitle}>{topic.title}</Text>
      <Text style={[styles.link, { marginTop: 4 }]}>{topic.profile === 'all' ? 'Todos' : PROFILE_LABELS[topic.profile]}</Text>
      <Text style={[styles.infoText, { marginVertical: 12 }]}>{topic.summary}</Text>
      <Text style={styles.sectionLabel}>PASSO A PASSO</Text>
      {topic.steps.map((step, index) => <View key={index} style={[styles.row, { alignItems: 'flex-start', marginBottom: 10 }]}>
        <View style={[styles.dayCircle, { width: 28, height: 28, borderRadius: 14, backgroundColor: colors.primary }]}>
          <Text style={{ color: '#FFFFFF', fontWeight: '700', fontSize: 13 }}>{index + 1}</Text>
        </View>
        <Text style={styles.infoText}>{step}</Text>
      </View>)}
      {topic.note && <View style={[styles.notice, { marginTop: 6, marginBottom: 14 }]}>
        <Ionicons name="information-circle-outline" size={22} color={accent} />
        <View style={styles.flex}><Text style={styles.cardTitle}>{topic.note.title}</Text><Text style={styles.infoText}>{topic.note.text}</Text></View>
      </View>}
      <Text style={styles.sectionLabel}>VEJA TAMBÉM</Text>
      {topic.related.map(id => HELP_TOPICS.find(item => item.id === id)).filter((item): item is HelpTopic => !!item).map(item => topicRow(item))}
    </View>;
  }

  const results = searchTopics(query);
  return <View>
    <Text style={styles.fieldLabel}>BUSCAR UM TÓPICO</Text>
    <View style={[styles.pickerFrame, { marginBottom: 14 }]}>
      <Ionicons name="search-outline" size={20} color={colors.subtext} />
      <TextInput accessibilityLabel="Buscar um tópico" value={query} onChangeText={setQuery} placeholder="Ex: ata, convite, votação" placeholderTextColor={colors.subtext}
        style={[styles.pickerText, { paddingVertical: 12 }]} autoCorrect={false} returnKeyType="search" />
      {query ? <TouchableOpacity accessibilityRole="button" accessibilityLabel="Limpar busca" onPress={() => setQuery('')}>
        <Ionicons name="close-circle" size={20} color={colors.subtext} /></TouchableOpacity> : null}
    </View>
    {query.trim() ? results.length ? <>
      <Text style={[styles.subtitle, { marginBottom: 6 }]}>{results.length === 1 ? '1 tópico encontrado.' : `${results.length} tópicos encontrados.`}</Text>
      {results.map(item => topicRow(item, true))}
    </> : <View style={styles.empty}>
      <Text style={[styles.cardTitle, { textAlign: 'center' }]}>Nenhum tópico encontrado.</Text>
      <Text style={[styles.subtitle, { textAlign: 'center' }]}>Tente outra palavra, como ata, convite ou votação.</Text>
      <FormButton label="Limpar Busca" secondary onPress={() => setQuery('')} />
    </View> : PROFILES.map(profile => {
      const topics = HELP_TOPICS.filter(item => item.profile === profile);
      const expanded = openProfile === profile;
      return <View key={profile} style={styles.card}>
        <TouchableOpacity accessibilityRole="button" accessibilityState={{ expanded }} style={[styles.row, { minHeight: 44 }]}
          onPress={() => setOpenProfile(expanded ? null : profile)}>
          <View style={styles.flex}>
            <Text style={styles.cardTitle}>{PROFILE_LABELS[profile]}</Text>
            <Text style={styles.subtitle}>{topics.length} tópicos · {PROFILE_HINTS[profile]}</Text>
          </View>
          <Ionicons name={expanded ? 'chevron-up' : 'chevron-down'} size={20} color={colors.subtext} />
        </TouchableOpacity>
        {expanded && topics.map(item => topicRow(item))}
      </View>;
    })}
  </View>;
}
