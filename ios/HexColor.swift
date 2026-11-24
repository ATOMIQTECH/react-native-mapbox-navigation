import UIKit

enum HexColor {
  static func parse(_ raw: Any?) -> UIColor? {
    guard let string = raw as? String else { return nil }

    var digits = string.trimmingCharacters(in: .whitespacesAndNewlines)
    if digits.hasPrefix("#") { digits = String(digits.dropFirst()) }
    if digits.count == 3 { digits = digits.map { "\($0)\($0)" }.joined() }

    guard digits.count == 6 || digits.count == 8,
          digits.allSatisfy(\.isHexDigit),
          let value = UInt64(digits, radix: 16) else {
      return nil
    }

    let hasAlpha = digits.count == 8
    return UIColor(
      red:   CGFloat((value >> (hasAlpha ? 24 : 16)) & 0xFF) / 255,
      green: CGFloat((value >> (hasAlpha ? 16 : 8)) & 0xFF) / 255,
      blue:  CGFloat((value >> (hasAlpha ? 8 : 0)) & 0xFF) / 255,
      alpha: hasAlpha ? CGFloat(value & 0xFF) / 255 : 1
    )
  }
}
