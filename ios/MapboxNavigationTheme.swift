import MapboxNavigationCore
import MapboxNavigationUIKit
import UIKit

/**
 Applies the chrome half of the `colors` prop to the embedded navigation UI.

 The route line is a set of plain properties on `NavigationMapView`, so
 `MapboxNavigationView` sets those directly. The chrome is not: every colour in
 the maneuver banner and the floating buttons is reached through `UIAppearance`
 proxies, which `Style.apply()` populates and `StyleManager` invokes on the
 view controller's behalf. That means two things this type has to handle.

 First, appearance proxies are consulted when a view is *created*, so a
 `ThemedStyle` has to be installed before the `NavigationViewController` builds
 its banner — hence `configure(with:)` being called while assembling
 `NavigationOptions`.

 Second, proxies do not retroactively touch views that already exist, so a
 `colors` change after mount would otherwise be invisible until a remount.
 `refresh(in:)` closes that gap by walking the live hierarchy, which is why the
 prop can be documented as applying live on both platforms.

 Appearance proxies are process-global, so the palette lives in a static: two
 embedded navigation views in one process would share it. That is already true
 of Mapbox's own `DayStyle`, and this view is a singleton in practice — the
 session registry allows one active owner.
 */
enum MapboxNavigationTheme {
  /// The chrome entries of the `colors` prop, as parsed `UIColor`s.
  struct Palette {
    var maneuverBackground: UIColor?
    var maneuverSubBackground: UIColor?
    var maneuverText: UIColor?
    var maneuverSecondaryText: UIColor?
    var maneuverDistanceText: UIColor?
    var maneuverTurnIcon: UIColor?
    var routeLine: UIColor?
    var routeLineCasing: UIColor?
    var routeLineAlternative: UIColor?
    var routeLineAlternativeCasing: UIColor?
    var restrictedRoad: UIColor?
    var maneuverArrow: UIColor?
    var maneuverArrowStroke: UIColor?
    var tripProgressBackground: UIColor?
    var tripProgressText: UIColor?
    var tripProgressIcon: UIColor?
    var floatingButtonBackground: UIColor?
    var floatingButtonIcon: UIColor?

    var isEmpty: Bool {
      maneuverBackground == nil && maneuverSubBackground == nil
        && maneuverText == nil && maneuverSecondaryText == nil
        && maneuverDistanceText == nil && maneuverTurnIcon == nil
        && routeLine == nil && routeLineCasing == nil
        && routeLineAlternative == nil && routeLineAlternativeCasing == nil
        && restrictedRoad == nil && maneuverArrow == nil
        && maneuverArrowStroke == nil
        && tripProgressBackground == nil && tripProgressText == nil
        && tripProgressIcon == nil
        && floatingButtonBackground == nil && floatingButtonIcon == nil
    }

    init(colors: [String: Any]?) {
      func color(_ key: String) -> UIColor? {
        HexColor.parse(colors?[key])
      }
      maneuverBackground = color("maneuverBackground")
      maneuverSubBackground = color("maneuverSubBackground") ?? maneuverBackground
      maneuverText = color("maneuverText")
      maneuverSecondaryText = color("maneuverSecondaryText")
      maneuverDistanceText = color("maneuverDistanceText")
      maneuverTurnIcon = color("maneuverTurnIcon")
      routeLine = color("routeLine")
      routeLineCasing = color("routeLineCasing")
      routeLineAlternative = color("routeLineAlternative")
      routeLineAlternativeCasing = color("routeLineAlternativeCasing")
      restrictedRoad = color("restrictedRoad")
      maneuverArrow = color("maneuverArrow")
      maneuverArrowStroke = color("maneuverArrowStroke")
      tripProgressBackground = color("tripProgressBackground")
      tripProgressText = color("tripProgressText")
      tripProgressIcon = color("tripProgressIcon")
      floatingButtonBackground = color("floatingButtonBackground")
      floatingButtonIcon = color("floatingButtonIcon")
    }
  }

  private(set) static var palette = Palette(colors: nil)

  /// Record the palette so `ThemedDayStyle` / `ThemedNightStyle` can apply it.
  static func configure(with colors: [String: Any]?) {
    palette = Palette(colors: colors)
  }

  /**
   Push the palette onto the `UIAppearance` proxies.

   Called from `ThemedDayStyle.apply()` after `super.apply()`, so these land on
   top of Mapbox's defaults rather than being overwritten by them.

   `whenContainedInInstancesOf` mirrors how `DayStyle` scopes the label colours:
   the same label classes are reused by the banner, the instruction cards and
   the steps list, and the SDK styles them per container. Setting the unscoped
   proxy alone loses to the SDK's more specific one, so each container is named
   explicitly.

   The trait collections have to match the ones the SDK registered under, or
   these proxies sit beside Mapbox's rather than on top of them. `Style`'s own
   `traitCollection` is internal, so it cannot be read from here — but
   `DayStyle.apply()` registers separately for `.phone` and `.pad`, so
   reconstructing those two is equivalent.
   */
  static func applyAppearance() {
    let palette = palette
    guard !palette.isEmpty else { return }

    for idiom in [UIUserInterfaceIdiom.phone, .pad] {
      applyAppearance(for: UITraitCollection(userInterfaceIdiom: idiom), palette: palette)
    }
  }

  private static func applyAppearance(
    for traitCollection: UITraitCollection,
    palette: Palette
  ) {
    if let background = palette.maneuverBackground {
      InstructionsBannerView.appearance(for: traitCollection).backgroundColor = background
      TopBannerView.appearance(for: traitCollection).backgroundColor = background
    }

    if let subBackground = palette.maneuverSubBackground {
      NextBannerView.appearance(for: traitCollection).backgroundColor = subBackground
      LanesView.appearance(for: traitCollection).backgroundColor = subBackground
    }

    if let text = palette.maneuverText {
      for container in [InstructionsBannerView.self, InstructionsCardView.self] {
        PrimaryLabel.appearance(for: traitCollection, whenContainedInInstancesOf: [container])
          .normalTextColor = text
      }
      PrimaryLabel.appearance(for: traitCollection).normalTextColor = text
    }

    if let secondary = palette.maneuverSecondaryText {
      for container in [InstructionsBannerView.self, InstructionsCardView.self] {
        SecondaryLabel.appearance(for: traitCollection, whenContainedInInstancesOf: [container])
          .normalTextColor = secondary
      }
      SecondaryLabel.appearance(for: traitCollection).normalTextColor = secondary
      NextInstructionLabel.appearance(for: traitCollection, whenContainedInInstancesOf: [NextBannerView.self])
        .normalTextColor = secondary
      NextInstructionLabel.appearance(for: traitCollection).normalTextColor = secondary
    }

    if let distance = palette.maneuverDistanceText {
      for container in [InstructionsBannerView.self, InstructionsCardView.self] {
        let proxy = DistanceLabel.appearance(for: traitCollection, whenContainedInInstancesOf: [container])
        proxy.valueTextColor = distance
        proxy.unitTextColor = distance
      }
      DistanceLabel.appearance(for: traitCollection).valueTextColor = distance
      DistanceLabel.appearance(for: traitCollection).unitTextColor = distance
    }

    if let turnIcon = palette.maneuverTurnIcon {
      // Only the emphasised strokes: `secondaryColor` is deliberately left
      // alone so a fork still shows which branch is not being taken. Android
      // can only manage a flat tint, which the public type documents.
      for container in [InstructionsBannerView.self, InstructionsCardView.self, NextBannerView.self] {
        ManeuverView.appearance(for: traitCollection, whenContainedInInstancesOf: [container])
          .primaryColor = turnIcon
      }
      ManeuverView.appearance(for: traitCollection).primaryColor = turnIcon
      LaneView.appearance(for: traitCollection).primaryColor = turnIcon
    }

    // The map's route colours go through the proxies as well as being assigned
    // directly on the instance (see `applyRouteLineColors`). Direct assignment
    // alone does not survive a style change: `DayStyle.apply()` writes these
    // same proxies, and `StyleManager.forceRefreshAppearance()` detaches and
    // re-adds the window's views, which makes UIKit re-apply appearance to the
    // live `NavigationMapView` and overwrite anything set on the instance.
    // Writing the proxies after `super.apply()` is what makes the override
    // durable — observed on device as a themed route line reverting to Mapbox
    // blue when the map switched to its night style.
    //
    // Wrapped in a main-actor Task because `NavigationMapView` is `@MainActor`
    // and `Style.apply()` is not; this is exactly what `DayStyle` does with the
    // same proxies.
    Task { @MainActor in
      let proxy = NavigationMapView.appearance(for: traitCollection)
      palette.routeLine.map { proxy.routeColor = $0 }
      palette.routeLineCasing.map { proxy.routeCasingColor = $0 }
      palette.routeLineAlternative.map { proxy.routeAlternateColor = $0 }
      palette.routeLineAlternativeCasing.map { proxy.routeAlternateCasingColor = $0 }
      palette.restrictedRoad.map { proxy.routeRestrictedAreaColor = $0 }
      palette.maneuverArrow.map { proxy.maneuverArrowColor = $0 }
      palette.maneuverArrowStroke.map { proxy.maneuverArrowStrokeColor = $0 }
    }

    if let background = palette.tripProgressBackground {
      BottomBannerView.appearance(for: traitCollection).backgroundColor = background
      // Note: the container the banner controller is presented in also needs
      // colouring, but `BannerContainerView` is the class of *both* the top and
      // bottom containers, so it cannot be done through a proxy without
      // painting the maneuver area with the trip-progress colour. `refresh`
      // handles the bottom one by identity instead.
      // The padding view fills the safe-area inset below the bar; leaving it
      // at the SDK white would show as a stripe under a recoloured bar.
      BottomPaddingView.appearance(for: traitCollection).backgroundColor = background
    }

    if let text = palette.tripProgressText {
      DistanceRemainingLabel.appearance(for: traitCollection).normalTextColor = text
      ArrivalTimeLabel.appearance(for: traitCollection).normalTextColor = text
      let time = TimeRemainingLabel.appearance(for: traitCollection)
      time.normalTextColor = text
      // `TimeRemainingLabel` picks one of these per congestion level and
      // ignores `normalTextColor` while a route is loaded, so setting the base
      // colour alone would leave the ETA green. Flattening all five is what
      // makes this key behave the way the type documents — and the same way
      // Android does, where the bar has no traffic tinting at all.
      time.trafficUnknownColor = text
      time.trafficLowColor = text
      time.trafficModerateColor = text
      time.trafficHeavyColor = text
      time.trafficSevereColor = text
    }

    if let icon = palette.tripProgressIcon {
      CancelButton.appearance(for: traitCollection).tintColor = icon
    }

    if let background = palette.floatingButtonBackground {
      FloatingButton.appearance(for: traitCollection).backgroundColor = background
    }
    if let icon = palette.floatingButtonIcon {
      FloatingButton.appearance(for: traitCollection).tintColor = icon
    }
  }

  /**
   Recolour the views that already exist.

   Appearance proxies only run at view creation, so this is what makes a
   `colors` change after mount visible. Matching on the concrete SDK view types
   is deliberate: they are all `open`/`public`, and the styling properties are
   the same `@objc dynamic` ones the proxies write, so a view recoloured here
   ends up in exactly the state it would have been created in.
   */
  /**
   Recolour a live `NavigationViewController`.

   Walks its hierarchy, then paints the bottom banner container by identity —
   `BannerContainerView` is the class of both the top and bottom containers, so
   it can only be told apart through `NavigationView`'s public properties.
   */
  @MainActor
  static func refresh(in viewController: NavigationViewController) {
    guard !palette.isEmpty else { return }
    refresh(in: viewController.view)
    palette.tripProgressBackground.map {
      viewController.navigationView.bottomBannerContainerView.backgroundColor = $0
    }
  }

  @MainActor
  static func refresh(in view: UIView) {
    let palette = palette
    guard !palette.isEmpty else { return }

    switch view {
    case let banner as InstructionsBannerView:
      palette.maneuverBackground.map { banner.backgroundColor = $0 }
    case let banner as NextBannerView:
      palette.maneuverSubBackground.map { banner.backgroundColor = $0 }
    case let lanes as LanesView:
      palette.maneuverSubBackground.map { lanes.backgroundColor = $0 }
    case let label as PrimaryLabel:
      palette.maneuverText.map { label.normalTextColor = $0 }
    case let label as NextInstructionLabel:
      // The "then" row. A sibling of `SecondaryLabel` rather than a subclass,
      // so it needs its own case, and both take the same colour.
      palette.maneuverSecondaryText.map { label.normalTextColor = $0 }
    case let label as SecondaryLabel:
      palette.maneuverSecondaryText.map { label.normalTextColor = $0 }
    case let label as DistanceLabel:
      if let distance = palette.maneuverDistanceText {
        label.valueTextColor = distance
        label.unitTextColor = distance
      }
    case let maneuver as ManeuverView:
      palette.maneuverTurnIcon.map { maneuver.primaryColor = $0 }
    case let lane as LaneView:
      palette.maneuverTurnIcon.map { lane.primaryColor = $0 }
    // Before `FloatingButton`: `CancelButton` is a sibling under `Button`, but
    // listing it first keeps the trip-progress glyph on its own key rather than
    // inheriting the floating-button colours.
    case let button as CancelButton:
      palette.tripProgressIcon.map { button.tintColor = $0 }
    case let button as FloatingButton:
      palette.floatingButtonBackground.map { button.backgroundColor = $0 }
      palette.floatingButtonIcon.map { button.tintColor = $0 }
    case let banner as BottomBannerView:
      // Also matches `BottomPaddingView`, which subclasses it.
      palette.tripProgressBackground.map { banner.backgroundColor = $0 }
    case let label as TimeRemainingLabel:
      if let text = palette.tripProgressText {
        label.normalTextColor = text
        label.trafficUnknownColor = text
        label.trafficLowColor = text
        label.trafficModerateColor = text
        label.trafficHeavyColor = text
        label.trafficSevereColor = text
      }
    case let label as DistanceRemainingLabel:
      palette.tripProgressText.map { label.normalTextColor = $0 }
    case let label as ArrivalTimeLabel:
      palette.tripProgressText.map { label.normalTextColor = $0 }
    default:
      break
    }

    for subview in view.subviews {
      refresh(in: subview)
    }
  }
}

/// `DayStyle` with the `colors` palette layered on top.
final class ThemedDayStyle: DayStyle {
  override func apply() {
    super.apply()
    MapboxNavigationTheme.applyAppearance()
  }
}

/// `NightStyle` with the `colors` palette layered on top.
final class ThemedNightStyle: NightStyle {
  override func apply() {
    super.apply()
    MapboxNavigationTheme.applyAppearance()
  }
}
