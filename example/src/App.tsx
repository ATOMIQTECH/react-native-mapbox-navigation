import { SafeAreaProvider } from 'react-native-safe-area-context'

import Navigation from './Navigation'

export default function App() {
  return (
    <SafeAreaProvider>
      <Navigation />
    </SafeAreaProvider>
  )
}
