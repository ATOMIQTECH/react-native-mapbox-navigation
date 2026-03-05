import { useNavigation } from '@react-navigation/native'
import { Pressable, ScrollView, StatusBar, StyleSheet, Text, View } from 'react-native'
import { SafeAreaView } from 'react-native-safe-area-context'

type ScenarioRoute =
  | 'CoreScenario'
  | 'OverlayScenario'
  | 'RuntimeScenario'
  | 'EventsScenario'
  | 'AppearanceScenario'

const SCENARIOS: { route: ScenarioRoute; title: string; description: string }[] = [
  {
    route: 'CoreScenario',
    title: 'Core Navigation',
    description: 'Baseline embedded guidance, progress, banner, camera and arrival callbacks.',
  },
  {
    route: 'OverlayScenario',
    title: 'Overlay + Feedback',
    description: 'Custom bottom sheet, built-in quick actions, floating buttons, and rating flow.',
  },
  {
    route: 'RuntimeScenario',
    title: 'Runtime APIs',
    description:
      'Test setMuted, setVoiceVolume, setDistanceUnit, setLanguage and stop/resume APIs.',
  },
  {
    route: 'EventsScenario',
    title: 'Event Listeners',
    description: 'Validate add*Listener helper subscriptions and event delivery counts.',
  },
  {
    route: 'AppearanceScenario',
    title: 'Appearance + Route',
    description:
      'Toggle trip banner/progress UI, alternatives, theme, waypoints and style presets.',
  },
]

export default function HomeScreen() {
  const navigation = useNavigation<any>()

  return (
    <SafeAreaView style={styles.screen}>
      <StatusBar barStyle='light-content' />
      <ScrollView contentContainerStyle={styles.content}>
        <View style={styles.header}>
          <Text style={styles.title}>Mapbox Module Test Lab</Text>
          <Text style={styles.subtitle}>
            Use each scenario to validate a separate area of the native module.
          </Text>
        </View>
        {SCENARIOS.map((scenario) => (
          <Pressable
            key={scenario.route}
            onPress={() => {
              navigation.navigate(scenario.route)
            }}
            style={styles.card}
          >
            <Text style={styles.cardTitle}>{scenario.title}</Text>
            <Text style={styles.cardDescription}>{scenario.description}</Text>
          </Pressable>
        ))}
      </ScrollView>
    </SafeAreaView>
  )
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    backgroundColor: '#020617',
  },
  content: {
    paddingHorizontal: 16,
    paddingTop: 14,
    paddingBottom: 24,
    gap: 12,
  },
  header: {
    gap: 8,
    paddingBottom: 8,
  },
  title: {
    color: '#f8fafc',
    fontSize: 28,
    fontWeight: '800',
  },
  subtitle: {
    color: '#bfdbfe',
    fontSize: 14,
    lineHeight: 20,
  },
  card: {
    borderRadius: 18,
    borderWidth: 1,
    borderColor: 'rgba(148,163,184,0.26)',
    backgroundColor: 'rgba(15,23,42,0.88)',
    paddingHorizontal: 14,
    paddingVertical: 14,
    gap: 6,
  },
  cardTitle: {
    color: '#ffffff',
    fontSize: 16,
    fontWeight: '800',
  },
  cardDescription: {
    color: '#cbd5e1',
    fontSize: 13,
    lineHeight: 18,
  },
})
