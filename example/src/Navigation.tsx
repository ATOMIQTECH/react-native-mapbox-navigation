import { createStaticNavigation, type StaticParamList } from '@react-navigation/native'
import { createNativeStackNavigator } from '@react-navigation/native-stack'

import AppearanceScenarioScreen from './screens/AppearanceScenarioScreen'
import CoreScenarioScreen from './screens/CoreScenarioScreen'
import EventsScenarioScreen from './screens/EventsScenarioScreen'
import HomeScreen from './screens/HomeScreen'
import OverlayScenarioScreen from './screens/OverlayScenarioScreen'
import RuntimeScenarioScreen from './screens/RuntimeScenarioScreen'

const RootStack = createNativeStackNavigator({
  initialRouteName: 'Home',
  screenOptions: {
    headerStyle: {
      backgroundColor: '#020617',
    },
    headerTintColor: '#f8fafc',
    headerTitleStyle: {
      fontWeight: '700',
    },
    contentStyle: {
      backgroundColor: '#020617',
    },
  },
  screens: {
    Home: {
      screen: HomeScreen,
      options: {
        title: 'Module Test Lab',
      },
    },
    CoreScenario: {
      screen: CoreScenarioScreen,
      options: {
        title: 'Core Navigation',
      },
    },
    OverlayScenario: {
      screen: OverlayScenarioScreen,
      options: {
        title: 'Overlay + Feedback',
      },
    },
    RuntimeScenario: {
      screen: RuntimeScenarioScreen,
      options: {
        title: 'Runtime APIs',
      },
    },
    EventsScenario: {
      screen: EventsScenarioScreen,
      options: {
        title: 'Event Listeners',
      },
    },
    AppearanceScenario: {
      screen: AppearanceScenarioScreen,
      options: {
        title: 'Appearance + Route',
      },
    },
  },
})

const Navigation = createStaticNavigation(RootStack)

type RootStackParamList = StaticParamList<typeof RootStack>

declare global {
  namespace ReactNavigation {
    // eslint-disable-next-line @typescript-eslint/no-empty-object-type
    interface RootParamList extends RootStackParamList {}
  }
}

export default Navigation
