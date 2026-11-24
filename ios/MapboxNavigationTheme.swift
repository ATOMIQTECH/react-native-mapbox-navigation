import MapboxNavigationCore
import MapboxNavigationUIKit
import UIKit

enum MapboxNavigationTheme {
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

  static func configure(with colors: [String: Any]?) {
    palette = Palette(colors: colors)
  }

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
      for container in [InstructionsBannerView.self, InstructionsCardView.self, NextBannerView.self] {
        ManeuverView.appearance(for: traitCollection, whenContainedInInstancesOf: [container])
          .primaryColor = turnIcon
      }
      ManeuverView.appearance(for: traitCollection).primaryColor = turnIcon
      LaneView.appearance(for: traitCollection).primaryColor = turnIcon
    }

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
      BottomPaddingView.appearance(for: traitCollection).backgroundColor = background
    }

    if let text = palette.tripProgressText {
      DistanceRemainingLabel.appearance(for: traitCollection).normalTextColor = text
      ArrivalTimeLabel.appearance(for: traitCollection).normalTextColor = text
      let time = TimeRemainingLabel.appearance(for: traitCollection)
      time.normalTextColor = text
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
    case let button as CancelButton:
      palette.tripProgressIcon.map { button.tintColor = $0 }
    case let button as FloatingButton:
      palette.floatingButtonBackground.map { button.backgroundColor = $0 }
      palette.floatingButtonIcon.map { button.tintColor = $0 }
    case let banner as BottomBannerView:
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

final class ThemedDayStyle: DayStyle {
  override func apply() {
    super.apply()
    MapboxNavigationTheme.applyAppearance()
  }
}

final class ThemedNightStyle: NightStyle {
  override func apply() {
    super.apply()
    MapboxNavigationTheme.applyAppearance()
  }
}
