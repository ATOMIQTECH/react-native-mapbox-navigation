# Troubleshooting

## Prebuild Fails With a Token Error

The config plugin validates token shape during prebuild.

Check that:

- `EXPO_PUBLIC_MAPBOX_ACCESS_TOKEN` exists and starts with `pk.`
- `MAPBOX_DOWNLOADS_TOKEN` exists and starts with `sk.`
- the downloads token has `DOWNLOADS:READ`

## Android Build Cannot Resolve Mapbox Dependencies

The plugin injects the Mapbox Maven repository, but native builds still need the downloads token available in the environment or Gradle properties at build time.

Check:

- `MAPBOX_DOWNLOADS_TOKEN` is exported in the shell running the build
- you rebuilt after adding the package

## `@rnmapbox/maps` Is Installed and the Build Fails on Mapbox Maps

Installing both packages needs no configuration of your own — the config plugin
shares one Mapbox Maps SDK between them. What it cannot do is apply itself to a
native project that already exists, so re-run prebuild after adding either
package:

```bash
npx expo prebuild --clean
```

If a failure survives that, match the symptom:

- **`pod install` prints `MapboxMaps is being resolved by BOTH CocoaPods and SwiftPackageManager`.** Something still declares MapboxMaps as a pod. Check your `Podfile` for a `$RNMapboxMapsSwiftPackageManager` assignment of your own — the Hash form keeps `@rnmapbox/maps` on CocoaPods, so remove it and let the plugin set `'manual'`. The warning itself names whichever case it detected.
- **iOS fails with `no such module 'MapboxMaps'` in an `@rnmapbox/maps` file.** The Podfile is missing `$ExpoMapboxNavigation.post_install(installer)`, which is what points that pod target at the Swift package. Prebuild writes it; a hand-managed `ios/` directory needs it added by hand.
- **iOS fails with a wall of `duplicate symbol` errors mentioning Mapbox.** A pod target is linking the Swift package instead of only compiling against it. This is what the plugin's wiring prevents, so it usually means a third `post_install` hook is attaching package product dependencies — remove it, or set `rnmapboxMapsCompat: false` and take over the whole job.
- **Android fails in `checkDebugDuplicateClasses` on `com.mapbox.maps` classes.** The app has both the plain and `-ndk27` Maps artifacts. Confirm your `android/build.gradle` still carries the plugin's `react-native-mapbox-navigation-mapbox-maps-variant` block; `expo prebuild --clean` restores it.

A correct iOS install has **no** Mapbox pods at all — `grep -i mapbox ios/Podfile.lock` should show only `ExpoMapboxNavigationNative` and `rnmapbox-maps` themselves.

## The View Shows `LOCATION_PERMISSION_REQUIRED`

The library does not request permission for you. Request foreground location access before enabling the view.

Recommended:

- use `expo-location`
- only set `enabled={true}` after permission is granted

## iOS Does Not Start Routing

Current behavior: iOS requires `startOrigin`.

If routing never starts:

- confirm `startOrigin` is defined
- confirm `destination` is defined
- confirm location permission is granted

## Android Starts But Uses the Wrong Origin

If `startOrigin` is omitted, Android can fall back to device location. Pass an explicit `startOrigin` if you need both platforms to use the same route origin.

## Custom Floating Buttons Do Not Appear

Check:

- `floatingButtons`, `renderFloatingButtons`, or `floatingButtonsComponent` is provided
- your overlay is not hidden by custom `floatingButtonsContainerStyle`
- the trip has not already arrived, because custom floating buttons hide automatically by default

If you want them to stay after arrival, set `hideFloatingButtonsOnArrival={false}`.

## The Bottom Sheet Does Not Behave the Same on iOS and Android

That is expected:

- Android uses `collapsed` and `expanded`
- iOS uses `hidden` and `expanded`

Collapsed requests on iOS map to the hidden-style behavior.

## Arrival Feedback Modal Does Not Show

Check:

- `showsEndOfRouteFeedback` is set, or
- `renderEndOfRouteFeedback` / `endOfRouteFeedbackComponent` is provided

Also verify the route actually reaches arrival and that navigation was not canceled before the trip completed.

## Session Ends or Refuses To Start

Only one embedded navigation session can run at a time.

Check:

- you do not have multiple enabled `MapboxNavigationView` instances mounted
- you call `stopNavigation()` before starting another embedded session
- `onError` for `NAVIGATION_SESSION_CONFLICT`

## A Prop Seems Ignored

Current known limitations:

- `androidActionButtons` is effectively ignored in embedded mode
- `showsReportFeedback` is not a reliable embedded cross-platform toggle
- `getNavigationSettings().isNavigating` should not be treated as authoritative
