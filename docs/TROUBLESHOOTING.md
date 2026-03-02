# Troubleshooting

## Android map is blank

- Ensure `mapbox_access_token` is present in Android resources (via Expo prebuild/config plugin).
- Ensure location permission is granted.
- Ensure only one embedded session is active at a time.

## Route preview appears but guidance UI is incomplete

- Keep `showsManeuverView`, `showsTripProgress`, and `showsActionButtons` enabled.
- Avoid overlay styles that fully cover top maneuver banner or native bottom info panel.

## Custom sheet cannot be swiped up

- Use `bottomSheet.mode = "overlay"`.
- Increase `revealGestureHotzoneHeight`.
- If taps are blocked on right-side native buttons, keep `revealGestureRightExclusionWidth` at `0` or a small value.

## Session ends unexpectedly

- Do not mount multiple `MapboxNavigationView` instances as enabled at the same time.
- Check `onError` for `NAVIGATION_SESSION_CONFLICT`.

## v2.0.0 migration

Removed APIs:
- `startNavigation(...)`
- `stopNavigation()`
- `isNavigating()`

Use `MapboxNavigationView` (embedded) for all navigation flows.
