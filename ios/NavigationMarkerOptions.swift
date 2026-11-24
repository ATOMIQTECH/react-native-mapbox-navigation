import Foundation

enum NavigationMarkerOptions {
  static func variant(_ raw: String?) -> String {
    switch normalized(raw) {
    case "primary": return "primary"
    case "success": return "success"
    case "warning": return "warning"
    case "danger":  return "danger"
    case "muted":   return "muted"
    default:        return "default"
    }
  }

  static func size(_ raw: String?) -> String {
    switch normalized(raw) {
    case "small": return "small"
    case "large": return "large"
    default:      return "medium"
    }
  }

  static func style(_ raw: String?) -> String {
    normalized(raw) == "dot" ? "dot" : "pin"
  }

  private static func normalized(_ raw: String?) -> String? {
    raw?.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
  }
}
