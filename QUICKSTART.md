# Quickstart

Use this if you need the shortest path to a working embedded navigation screen.

## 1. Install

```bash
npm install @atomiqlab/react-native-mapbox-navigation
```

## 2. Set Mapbox Tokens

Required during prebuild:

- `EXPO_PUBLIC_MAPBOX_ACCESS_TOKEN` (`pk.` public token)
- `MAPBOX_DOWNLOADS_TOKEN` (`sk.` secret token with `DOWNLOADS:READ`)

Example:

```bash
EXPO_PUBLIC_MAPBOX_ACCESS_TOKEN=pk.your_public_token
MAPBOX_DOWNLOADS_TOKEN=sk.your_secret_token
```

## 3. Rebuild Native Code

This package uses an Expo config plugin and native SDKs, so you must rebuild after install or config changes.

```bash
npx expo prebuild
npx expo run:android
# or
npx expo run:ios
```

## 4. Request Location Permission

Grant foreground location permission before enabling `MapboxNavigationView`.

## 5. Render the View

```tsx
import { MapboxNavigationView } from "@atomiqlab/react-native-mapbox-navigation";

<MapboxNavigationView
  enabled
  style={{ flex: 1 }}
  startOrigin={{ latitude: 37.7749, longitude: -122.4194 }}
  destination={{ latitude: 37.7847, longitude: -122.4073 }}
  shouldSimulateRoute
/>
```

## 6. Important Platform Note

- Android can fall back to device location when `startOrigin` is omitted
- iOS currently needs `startOrigin`

## Next

- For custom floating buttons, bottom-sheet overlays, and end-of-route feedback, use the canonical docs in [README.md](./README.md)
