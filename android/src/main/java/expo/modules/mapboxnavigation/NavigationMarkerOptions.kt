package expo.modules.mapboxnavigation

internal object NavigationMarkerOptions {
  fun variant(raw: String?): String = when (normalized(raw)) {
    "primary" -> "primary"
    "success" -> "success"
    "warning" -> "warning"
    "danger" -> "danger"
    "muted" -> "muted"
    else -> "default"
  }

  fun size(raw: String?): String = when (normalized(raw)) {
    "small" -> "small"
    "large" -> "large"
    else -> "medium"
  }

  fun style(raw: String?): String = if (normalized(raw) == "dot") "dot" else "pin"

  private fun normalized(raw: String?): String? = raw?.trim()?.lowercase()
}
