# Troubleshooting

## Android map is blank

- Ensure `mapbox_access_token` is present in Android resources (via Expo prebuild/config plugin).
- Ensure location permission is granted.
- Ensure only one embedded session is active at a time.

## Route preview appears but guidance UI is incomplete

- Keep `showsManeuverView` enabled for native top maneuver instructions.
- In overlay mode the native bottom info panel is intentionally suppressed.

## Custom sheet cannot be swiped up

- Use `bottomSheet.mode = "overlay"`.
- Android uses collapsed/expanded states.
- iOS uses hidden/expanded behavior.

## Session ends unexpectedly

- Do not mount multiple `MapboxNavigationView` instances as enabled at the same time.
- Check `onError` for `NAVIGATION_SESSION_CONFLICT`.
- Use `stopNavigation()` to end the active embedded session programmatically.

## v2.0.0 migration

Removed APIs:
- `startNavigation(...)`
- `isNavigating()`

Use `MapboxNavigationView` (embedded) for all navigation flows.
