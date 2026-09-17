import UIKit

/**
 The single place a consumer-supplied colour string becomes a `UIColor`.

 Three separate props accept hex from JS — `colors`, the location puck's
 `appearance`, and a navigation marker's `color` / `badgeColor` — and each used
 to carry its own parser, which is how they drifted apart: one honoured the
 alpha byte, one truncated it with `prefix(6)`, and only one accepted the
 3-digit form. They all route through here now, so a string means the same
 thing wherever it is passed, and the same thing as Android's `HexColor.parse`.

 Eight digits are read as `#RRGGBBAA` — alpha last, the CSS convention, which
 is what a React Native developer writing `'#14532D80'` means by it. Android's
 `Color.parseColor` reads that byte order the other way round (`#AARRGGBB`),
 which is why the Kotlin side no longer hands it `#`-prefixed input either.

 The leading `#` is optional, and three digits expand by doubling each nibble
 (`#1AF` → `#11AAFF`) as CSS does. Anything else is nil: a bad colour string is
 a consumer typo, not a reason to fail a render, and every caller treats nil as
 "keep the SDK default".
 */
enum HexColor {
  /// Parse `#RGB`, `#RRGGBB` or `#RRGGBBAA` — the `#` optional — into a colour.
  static func parse(_ raw: Any?) -> UIColor? {
    guard let string = raw as? String else { return nil }

    var digits = string.trimmingCharacters(in: .whitespacesAndNewlines)
    if digits.hasPrefix("#") { digits = String(digits.dropFirst()) }
    if digits.count == 3 { digits = digits.map { "\($0)\($0)" }.joined() }

    // `UInt64(_:radix:)` would accept a leading sign, so the digits are
    // checked rather than inferred from the conversion succeeding.
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
