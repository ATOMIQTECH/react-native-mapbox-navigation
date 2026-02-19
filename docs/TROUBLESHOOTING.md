# Troubleshooting

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
