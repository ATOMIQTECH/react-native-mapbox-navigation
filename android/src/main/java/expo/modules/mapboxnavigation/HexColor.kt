package expo.modules.mapboxnavigation

import android.graphics.Color

internal object HexColor {
  private val HEX_DIGITS = Regex("^[0-9a-fA-F]+$")

  fun parse(raw: Any?): Int? {
    val string = (raw as? String)?.trim()?.takeIf { it.isNotEmpty() } ?: return null

    var digits = string.removePrefix("#")
    if (digits.length == 3) digits = digits.map { "$it$it" }.joinToString("")

    if ((digits.length == 6 || digits.length == 8) && HEX_DIGITS.matches(digits)) {
      val value = digits.toLong(16)
      return if (digits.length == 8) {
        (((value and 0xFF) shl 24) or (value ushr 8)).toInt()
      } else {
        (0xFF000000L or value).toInt()
      }
    }

    if (string.startsWith("#")) return null

    return runCatching { Color.parseColor(string) }.getOrNull()
  }
}
