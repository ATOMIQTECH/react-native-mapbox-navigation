import CoreLocation
import MapboxDirections

enum RouteTarget {
  static let minimumChange: CLLocationDistance = 15

  static func isSamePlace(_ a: CLLocationCoordinate2D, _ b: CLLocationCoordinate2D) -> Bool {
    CLLocation(latitude: a.latitude, longitude: a.longitude)
      .distance(from: CLLocation(latitude: b.latitude, longitude: b.longitude))
      < minimumChange
  }

  static func isSameWaypointList(_ a: [Waypoint], _ b: [Waypoint]) -> Bool {
    a.count == b.count && zip(a, b).allSatisfy {
      isSamePlace($0.coordinate, $1.coordinate)
        && $0.allowsArrivingOnOppositeSide == $1.allowsArrivingOnOppositeSide
    }
  }
}
