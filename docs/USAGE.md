# Usage

`README.md` is the canonical API reference. This file focuses on behavior that is easy to miss when integrating the library.

## Embedded-Only Model

`2.x` removed the full-screen navigation APIs. All navigation runs through `MapboxNavigationView`.

Only one embedded session should be active at a time. If multiple enabled views mount together, expect `NAVIGATION_SESSION_CONFLICT`.

## Origin Handling

- Android can preview and start with the device location when `startOrigin` is omitted
- iOS currently requires `startOrigin`

For a consistent cross-platform setup, pass `startOrigin` after you capture the current location.

## Overlay Architecture

The library uses React overlays for app-owned UI:

- `bottomSheet` renders a React sheet over the native map
- `floatingButtons` render a React action rail over the native map
- `showsEndOfRouteFeedback` renders a React rating modal at arrival

This means:

- custom floating buttons do not require `bottomSheet`
- the end-of-route rating modal is package-managed, not a native Mapbox modal
- overlay props are intentionally handled in JS and are not forwarded to native view props

## Native Floating Buttons

Use `nativeFloatingButtons` when you need to keep, remove, or partially trim the built-in native action buttons.

Recommended pattern:

- keep native buttons visible unless you have a concrete reason to hide one
- add custom React floating buttons with `floatingButtonsComponent`
- only hide the exact native buttons you do not want

## End-of-Route Feedback

Three supported modes:

- disabled: omit `showsEndOfRouteFeedback`
- default modal: set `showsEndOfRouteFeedback`
- custom modal: provide `renderEndOfRouteFeedback` or `endOfRouteFeedbackComponent`

`hideFloatingButtonsOnArrival` defaults to `true`, so custom floating buttons disappear automatically when the trip ends.

## Props That Are Not Reliable Today

These types still exist, but you should not build product behavior around them yet:

- `androidActionButtons`
  Present for compatibility, effectively ignored in embedded mode
- `showsReportFeedback`
  Not a reliable cross-platform embedded toggle
- `getNavigationSettings().isNavigating`
  Currently not authoritative session state

## Recommended Integration Pattern

1. Request permission with `expo-location`
2. Capture device location
3. Pass `startOrigin` and `destination`
4. Mount `MapboxNavigationView` with `enabled={true}`
5. Add app-owned overlays with `floatingButtonsComponent`, `bottomSheetComponent`, or both
6. Handle `onError` and `onArrive`

## Example App

The example app in `example/` demonstrates:

- Expo location permission flow
- custom floating buttons
- selective native floating-button visibility
- package-managed end-of-route feedback
