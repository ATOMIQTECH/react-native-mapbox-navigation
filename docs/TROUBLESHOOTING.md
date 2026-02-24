# Troubleshooting

Applies to package version: `1.1.6`

## Build Fails Downloading Mapbox Dependencies

Check `MAPBOX_DOWNLOADS_TOKEN`:

- It must be a secret `sk...` token.
- It must include `DOWNLOADS:READ`.
- It must be available to native build steps (`gradle` and `pod install`).

## Navigation Does Not Start

- Confirm destination coordinates are valid.
- Confirm location permissions are granted.
- Confirm `mapbox_access_token` resolves correctly in native resources/config.

## iOS Starts in Preview-Like Camera

- Use `cameraMode: "following"`.
- Avoid forcing fixed camera behavior unless needed.
- Keep SDK adaptive camera enabled for turn-by-turn behavior.

## No Banner/Progress Events

- Ensure listeners are registered before `startNavigation`.
- Verify active guidance is started (especially with simulation settings).

## Android Uses External Navigation App

Current package behavior is native Mapbox navigation and should stay in-app.
If not, verify you are running the latest package version and rebuilt native code:

```bash
npx expo prebuild --clean
```

## Prebuild Fails With Missing Token Error

The config plugin now validates tokens early. Ensure one of these provides a public token:

- `EXPO_PUBLIC_MAPBOX_ACCESS_TOKEN`
- `MAPBOX_PUBLIC_TOKEN`
- `expo.extra.mapboxPublicToken`

And provide downloads token:

- `MAPBOX_DOWNLOADS_TOKEN` or `expo.extra.mapboxDownloadsToken`

## Route Fetch Errors (401/403/429)

Listen to `onError` / `addErrorListener` and check `code`:

- `MAPBOX_TOKEN_INVALID` (invalid/expired token)
- `MAPBOX_TOKEN_FORBIDDEN` (missing scopes)
- `MAPBOX_RATE_LIMITED` (request throttling)

For Android dependency download failures during build, verify `MAPBOX_DOWNLOADS_TOKEN` has `DOWNLOADS:READ` scope.
