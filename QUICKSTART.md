# Quick Start

Current package version: `1.1.6`

## 1. Install

```bash
npm install @atomiqlab/react-native-mapbox-navigation
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

## 5. Render embedded navigation

```ts
import { MapboxNavigationView } from '@atomiqlab/react-native-mapbox-navigation';

<MapboxNavigationView
  enabled
  style={{ flex: 1 }}
  startOrigin={{ latitude: 37.7749, longitude: -122.4194 }}
  destination={{ latitude: 37.7847, longitude: -122.4073, name: 'Downtown' }}
  shouldSimulateRoute
  bottomSheet={{
    enabled: true,
    mode: 'overlay',
    initialState: 'hidden',
    builtInQuickActions: ['overview', 'recenter', 'toggleMute', 'stop'],
  }}
/>;
```

For overlay bottom-sheet customization and embedded usage, see [docs/USAGE.md](docs/USAGE.md).
