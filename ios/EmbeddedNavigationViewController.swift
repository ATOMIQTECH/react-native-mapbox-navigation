import MapboxMaps
import MapboxNavigationUIKit
import UIKit

final class EmbeddedNavigationViewController: NavigationViewController {
  var onViewDidAppear: (() -> Void)?
  var extraOrnamentBottomMargin: CGFloat = 0 {
    didSet {
      guard extraOrnamentBottomMargin != oldValue else { return }
      appliedOrnamentMargins = nil
      view.setNeedsLayout()
    }
  }
  private var appliedOrnamentMargins: (logo: CGPoint, attribution: CGPoint)?

  override func viewDidLayoutSubviews() {
    super.viewDidLayoutSubviews()
    Task { @MainActor [weak self] in
      self?.offsetOrnaments()
    }
  }

  private func offsetOrnaments() {
    guard extraOrnamentBottomMargin > 0 || appliedOrnamentMargins != nil,
          let mapView = navigationMapView?.mapView else { return }
    var options = mapView.ornaments.options
    if let applied = appliedOrnamentMargins,
       options.logo.margins == applied.logo,
       options.attributionButton.margins == applied.attribution {
      return
    }
    options.logo.margins.y += extraOrnamentBottomMargin
    options.attributionButton.margins.y += extraOrnamentBottomMargin
    mapView.ornaments.options = options
    appliedOrnamentMargins = (options.logo.margins, options.attributionButton.margins)
  }

  override func dismiss(animated flag: Bool, completion: (() -> Void)? = nil) {
    if presentedViewController != nil {
      super.dismiss(animated: flag, completion: completion)
    } else {
      completion?()
    }
  }

  override func viewDidAppear(_ animated: Bool) {
    super.viewDidAppear(animated)
    onViewDidAppear?()
  }
}

final class EmptyBannerViewController: UIViewController, NavigationComponent {
  private let height: CGFloat

  init(height: CGFloat = 0) {
    self.height = height
    super.init(nibName: nil, bundle: nil)
  }

  required init?(coder: NSCoder) {
    self.height = 0
    super.init(coder: coder)
  }

  override func loadView() {
    let view = UIView()
    view.backgroundColor = .clear
    view.isUserInteractionEnabled = false
    let collapse = view.heightAnchor.constraint(equalToConstant: height)
    collapse.priority = .defaultHigh
    collapse.isActive = true
    self.view = view
  }
}
