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

## Need Complex React UI Inside Full-screen Bottom Banner

`startNavigation` full-screen bottom banner is native-only (no direct React Node injection).
Use embedded `MapboxNavigationView` if you want Mapbox’s Drop-In UI inside your own screen, and overlay your own React UI carefully (overlays can cover the Mapbox bottom sheet/buttons).

## Embedded View Restarts / Loops

Common causes:

- Two sessions are competing (full-screen + embedded mounted at the same time). Call `stopNavigation()` before switching modes.
- Dev-only double mounting (e.g. React Strict Mode) can cause quick start/stop cycles.
- Location permission missing on Android can prevent embedded navigation from stabilizing.

If you still see repeated restarts after the above, capture `onError` events and share the `code`/`message` so we can trace the native lifecycle path.

## Android Embedded App Closes / Crashes On Mount

If the app closes as soon as `MapboxNavigationView` mounts on Android, it is almost always due to missing location permission.
Request `ACCESS_FINE_LOCATION` before enabling embedded navigation.

Also make sure you’re actually running a build that includes the latest native changes:

- If you upgraded the package version, rebuild native (`npx expo prebuild --clean` then `expo run:android`).
- If you’re testing against a local package checkout, reinstall it in your app and rebuild.

## Android Embedded View Is Blank / Dark Screen

If `startNavigation()` works but `MapboxNavigationView` is blank on Android, it is usually one of:

- **Layout/measurement:** the underlying map can be measured at a tiny fallback size like `64x64`.
- **SurfaceView rendering in RN:** Mapbox Maps can render via `SurfaceView` in some builds, which can appear blank behind `ReactRootView`. The embedded view uses Mapbox’s Drop-In `NavigationView` and applies best-effort `SurfaceView` z-order tweaks when detected.

Fix checklist:

- Rebuild native so you’re not running stale code: `npx expo prebuild --clean` then `expo run:android`.
- Ensure `enabled={true}` is only set **after** location permission is granted.
- Capture logs and look for `MapboxNavigationView` lines (token, permission, route request, and map composition):

```bash
adb logcat -c
adb logcat --pid=$(adb shell pidof -s com.your.app) -v time | rg -n "MapboxNavigationView\\(|mapbox_access_token resolved|startIfReady|requestRoutes|onRoutesReady|LOCATION_PERMISSION_REQUIRED|NAVIGATION_SESSION_CONFLICT|NAVIGATION_INIT_FAILED|ROUTE_ERROR|NO_ROUTE|mapView composition|AndroidRuntime|FATAL EXCEPTION"
```

If you see Mapbox messages like `ViewportDataSourceProcessor` complaining about padding not fitting a `64x64` map, it confirms the issue is view sizing rather than permission.

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
