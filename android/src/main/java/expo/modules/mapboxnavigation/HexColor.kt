package expo.modules.mapboxnavigation

import android.graphics.Color

/**
 * The single place a consumer-supplied colour string becomes an ARGB int.
 *
 * Three separate props accept hex from JS — `colors`, the location puck's
 * `appearance`, and a navigation marker's `color` / `badgeColor` — and each
 * used to call [Color.parseColor] directly, which reads eight digits as
 * `#AARRGGBB`. iOS reads the same string as `#RRGGBBAA`, so one palette gave
 * two different themes, and 3-digit hex was accepted on iOS but rejected here
 * ([Color.parseColor] takes only the 7- and 9-character forms) despite being
 * documented as portable.
 *
 * So `#`-prefixed input is parsed here instead, as `#RGB` / `#RRGGBB` /
 * `#RRGGBBAA` — alpha last, the CSS convention, matching iOS's `HexColor`.
 * Three digits expand by doubling each nibble (`#1AF` → `#11AAFF`), and the
 * leading `#` is optional.
 *
 * A string carrying no `#` and no hex falls through to [Color.parseColor],
 * which also accepts the CSS-ish colour names ("red", "magenta"). Nothing
 * documents that and iOS has no equivalent, so it is not portable — but it has
 * always worked on this platform, and silently dropping it would break a
 * consumer relying on it for no gain. A malformed `#` string is rejected
 * outright rather than falling through; see the comment on that branch.
 */
internal object HexColor {
  private val HEX_DIGITS = Regex("^[0-9a-fA-F]+$")

  /** Parse a colour string, or null if it is not one. */
  fun parse(raw: Any?): Int? {
    val string = (raw as? String)?.trim()?.takeIf { it.isNotEmpty() } ?: return null

    var digits = string.removePrefix("#")
    if (digits.length == 3) digits = digits.map { "$it$it" }.joinToString("")

    if ((digits.length == 6 || digits.length == 8) && HEX_DIGITS.matches(digits)) {
      val value = digits.toLong(16)
      return if (digits.length == 8) {
        // `#RRGGBBAA` in, `0xAARRGGBB` out: move the trailing alpha byte to
        // the front, which is the order every Android colour API wants.
        (((value and 0xFF) shl 24) or (value ushr 8)).toInt()
      } else {
        (0xFF000000L or value).toInt()
      }
    }

    // A leading `#` means the consumer meant hex, so a malformed one is a typo
    // rather than a name to look up. Worth being explicit about: colour names
    // never carry a `#`, and `Color.parseColor` parses the digits with
    // `Long.parseLong`, which accepts a sign — so passing it `#+fffff` yields a
    // colour instead of the error the string deserves.
    if (string.startsWith("#")) return null

    return runCatching { Color.parseColor(string) }.getOrNull()
  }
}
