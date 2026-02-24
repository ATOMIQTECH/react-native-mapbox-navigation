# Quick Start

Current package version: `1.1.6`

## 1. Install

```bash
npm install @atomiqlab/react-native-mapbox-navigation
npx expo install expo-build-properties
```

## 2. Configure plugin

In `app.json` / `app.config.js`:

```json
{
  "expo": {
    "plugins": [
      "@atomiqlab/react-native-mapbox-navigation"
    ]
  }
}
```

## 3. Set tokens

- Public token: `EXPO_PUBLIC_MAPBOX_ACCESS_TOKEN=pk...`
- Downloads token: `MAPBOX_DOWNLOADS_TOKEN=sk...` (`DOWNLOADS:READ` required)

## 4. Regenerate native projects

```bash
npx expo prebuild --clean
```

## 5. Start navigation

```ts
import { startNavigation } from '@atomiqlab/react-native-mapbox-navigation';

await startNavigation({
  destination: { latitude: 37.7847, longitude: -122.4073, name: 'Downtown' },
  startOrigin: { latitude: 37.7749, longitude: -122.4194 },
  shouldSimulateRoute: true,
  cameraMode: 'following',
});
```
