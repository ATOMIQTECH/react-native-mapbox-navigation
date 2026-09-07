package expo.modules.mapboxnavigation

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.content.res.Resources
import android.util.Log
import androidx.core.content.ContextCompat
import com.mapbox.maps.plugin.LocationPuck
import com.mapbox.maps.plugin.LocationPuck2D
import com.mapbox.maps.plugin.LocationPuck3D
import com.mapbox.navigation.ui.maps.puck.LocationPuckOptions
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * Builds Mapbox [LocationPuck] instances from the normalized `locationPuck`
 * payload produced by the JS `resolveLocationPuck` helper.
 *
 * Drop-In consumes pucks through [LocationPuckOptions], which accepts a
 * different puck per navigation state. Remote images referenced by a
 * `require()`'d React Native asset (a packager URL in development) or by an
 * explicit `https://` URL are fetched off the main thread and cached.
 */
internal object LocationPuckFactory {
  private const val TAG = "LocationPuckFactory"

  /** Navigation states a puck can be bound to, mirroring the JS keys. */
  private val STATE_KEYS = listOf(
    "default",
    "freeDrive",
    "destinationPreview",
    "routePreview",
    "activeNavigation",
    "arrival",
    "idle"
  )

  private val ioExecutor = Executors.newFixedThreadPool(3) { runnable ->
    Thread(runnable, "rn-mapbox-puck-assets").apply { isDaemon = true }
  }
  private val mainHandler = Handler(Looper.getMainLooper())
  private val drawableCache = HashMap<String, Drawable>()

  /** Split a normalized payload into per-state appearance maps. */
  fun parseStates(payload: Map<String, Any>?): Map<String, Map<String, Any>> {
    if (payload == null) return emptyMap()

    val result = LinkedHashMap<String, Map<String, Any>>()
    for (key in STATE_KEYS) {
      @Suppress("UNCHECKED_CAST")
      val entry = payload[key] as? Map<String, Any> ?: continue
      result[key] = entry
    }
    return result
  }

  /**
   * Resolve every referenced asset, then build [LocationPuckOptions].
   *
   * [onReady] is always invoked on the main thread. It is not called at all
   * when nothing usable was configured, which leaves the SDK defaults in place.
   */
  fun buildOptions(
    context: Context,
    states: Map<String, Map<String, Any>>,
    onReady: (LocationPuckOptions) -> Unit
  ) {
    if (states.isEmpty()) return

    // Collect every image reference up front so shared assets are fetched once.
    val imageSources = LinkedHashSet<String>()
    for (appearance in states.values) {
      if ((appearance["type"] as? String)?.lowercase() != "2d") continue
      for (key in listOf("topImage", "bearingImage", "shadowImage")) {
        (appearance[key] as? String)?.takeIf { it.isNotBlank() }?.let(imageSources::add)
      }
    }

    loadDrawables(context, imageSources.toList()) { drawables ->
      val builder = LocationPuckOptions.Builder(context)
      var applied = false

      states["default"]?.let { appearance ->
        makePuck(context, appearance, drawables)?.let {
          builder.defaultPuck(it)
          applied = true
        }
      }

      // Per-state overrides. `defaultPuck` above already seeded every state, so
      // these only need to replace the ones explicitly configured.
      for ((key, appearance) in states) {
        if (key == "default") continue
        val puck = makePuck(context, appearance, drawables) ?: continue
        when (key) {
          "freeDrive" -> builder.freeDrivePuck(puck)
          "destinationPreview" -> builder.destinationPreviewPuck(puck)
          "routePreview" -> builder.routePreviewPuck(puck)
          "activeNavigation" -> builder.activeNavigationPuck(puck)
          "arrival" -> builder.arrivalPuck(puck)
          "idle" -> builder.idlePuck(puck)
          else -> continue
        }
        applied = true
      }

      if (!applied) return@loadDrawables
      mainHandler.post { onReady(builder.build()) }
    }
  }

  // ---------------------------------------------------------------------------
  // Puck construction
  // ---------------------------------------------------------------------------

  private fun makePuck(
    context: Context,
    appearance: Map<String, Any>,
    drawables: Map<String, Drawable>
  ): LocationPuck? = when ((appearance["type"] as? String)?.lowercase()) {
    "3d" -> make3D(appearance)
    "2d" -> make2D(appearance, drawables)
    "tinted" -> makeTinted(context, appearance)
    "none" -> makeHidden()
    // "default" and anything unrecognised fall through to the SDK default.
    else -> null
  }

  private fun make3D(appearance: Map<String, Any>): LocationPuck? {
    val modelUri = (appearance["modelUri"] as? String)?.trim()
    if (modelUri.isNullOrEmpty()) {
      Log.w(TAG, "3D puck requested without a modelUri; keeping the default puck.")
      return null
    }

    return LocationPuck3D(
      modelUri = modelUri,
      modelScale = floatList(appearance["modelScale"]) ?: listOf(1f, 1f, 1f),
      modelRotation = floatList(appearance["modelRotation"]) ?: listOf(0f, 0f, 0f),
      modelTranslation = floatList(appearance["modelTranslation"]) ?: listOf(0f, 0f, 0f),
      modelScaleExpression = (appearance["scaleExpression"] as? String)?.takeIf { it.isNotBlank() },
      modelOpacity = floatValue(appearance["opacity"])?.coerceIn(0f, 1f) ?: 1f
    )
  }

  private fun make2D(
    appearance: Map<String, Any>,
    drawables: Map<String, Drawable>
  ): LocationPuck? {
    fun drawableFor(key: String): Drawable? =
      (appearance[key] as? String)?.takeIf { it.isNotBlank() }?.let { drawables[it] }

    val top = drawableFor("topImage")
    val bearing = drawableFor("bearingImage")
    val shadow = drawableFor("shadowImage")

    // Nothing loaded — keep the SDK puck rather than rendering an invisible one.
    if (top == null && bearing == null && shadow == null) {
      Log.w(TAG, "2D puck images could not be loaded; keeping the default puck.")
      return null
    }

    val scale = floatValue(appearance["scale"])
    val scaleExpression = (appearance["scaleExpression"] as? String)?.takeIf { it.isNotBlank() }
      // LocationPuck2D only scales via an expression, so express a plain
      // numeric scale as a constant literal.
      ?: scale?.let { "[\"literal\", ${it.coerceIn(0.05f, 20f)}]" }

    return LocationPuck2D(
      topImage = top,
      bearingImage = bearing,
      shadowImage = shadow,
      scaleExpression = scaleExpression,
      opacity = floatValue(appearance["opacity"])?.coerceIn(0f, 1f) ?: 1f
    )
  }

  /**
   * Recolour the standard puck shape.
   *
   * Android's [LocationPuck2D] takes drawables rather than colours, so the
   * puck is drawn here: a filled halo circle with a directional arrow on top.
   */
  private fun makeTinted(context: Context, appearance: Map<String, Any>): LocationPuck {
    val body = parseColor(appearance["color"]) ?: parseColor(appearance["bearingColor"])
      ?: Color.parseColor("#263A57")
    val halo = parseColor(appearance["haloColor"]) ?: Color.WHITE
    val arrow = parseColor(appearance["bearingColor"]) ?: body

    val density = context.resources.displayMetrics.density
    val scale = (floatValue(appearance["scale"]) ?: 1f).coerceIn(0.2f, 4f)
    val size = (28f * density * scale).toInt().coerceAtLeast(8)

    val resources = context.resources
    return LocationPuck2D(
      topImage = circleDrawable(resources, size, body, halo, density * scale),
      bearingImage = arrowDrawable(resources, (size * 1.6f).toInt().coerceAtLeast(8), arrow),
      shadowImage = circleDrawable(
        resources,
        (size * 1.35f).toInt().coerceAtLeast(8),
        Color.argb(40, Color.red(body), Color.green(body), Color.blue(body)),
        Color.TRANSPARENT,
        0f
      ),
      scaleExpression = null,
      opacity = floatValue(appearance["opacity"])?.coerceIn(0f, 1f) ?: 1f
    )
  }

  /**
   * Fully transparent puck.
   *
   * The location component has to stay enabled for the navigation camera to
   * track the user, so the puck is hidden by drawing nothing instead of by
   * disabling the component.
   */
  private fun makeHidden(): LocationPuck = LocationPuck2D(
    topImage = ColorDrawable(Color.TRANSPARENT),
    bearingImage = ColorDrawable(Color.TRANSPARENT),
    shadowImage = ColorDrawable(Color.TRANSPARENT),
    scaleExpression = null,
    opacity = 0f
  )

  // ---------------------------------------------------------------------------
  // Drawing helpers
  // ---------------------------------------------------------------------------

  private fun circleDrawable(
    resources: Resources,
    size: Int,
    fillColor: Int,
    strokeColor: Int,
    strokeWidth: Float
  ): Drawable {
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val radius = size / 2f
    val inset = strokeWidth / 2f

    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = fillColor
      style = Paint.Style.FILL
    }
    canvas.drawCircle(radius, radius, radius - inset, fill)

    if (strokeColor != Color.TRANSPARENT && strokeWidth > 0f) {
      val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = strokeColor
        style = Paint.Style.STROKE
        this.strokeWidth = strokeWidth
      }
      canvas.drawCircle(radius, radius, radius - inset, stroke)
    }

    return BitmapDrawable(resources, bitmap)
  }

  /** An upward-pointing triangle; Mapbox rotates it to the course bearing. */
  private fun arrowDrawable(resources: Resources, size: Int, color: Int): Drawable {
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      this.color = color
      style = Paint.Style.FILL
    }

    val width = size.toFloat()
    val path = Path().apply {
      moveTo(width / 2f, width * 0.08f)
      lineTo(width * 0.82f, width * 0.72f)
      lineTo(width / 2f, width * 0.56f)
      lineTo(width * 0.18f, width * 0.72f)
      close()
    }
    canvas.drawPath(path, paint)

    return BitmapDrawable(resources, bitmap)
  }

  // ---------------------------------------------------------------------------
  // Asset loading
  // ---------------------------------------------------------------------------

  /**
   * Load every source to a [Drawable], invoking [onReady] once all attempts
   * finish. Sources that fail are simply absent from the result map.
   */
  private fun loadDrawables(
    context: Context,
    sources: List<String>,
    onReady: (Map<String, Drawable>) -> Unit
  ) {
    if (sources.isEmpty()) {
      onReady(emptyMap())
      return
    }

    val results = HashMap<String, Drawable>()
    val remaining = AtomicInteger(sources.size)

    for (source in sources) {
      val cached = synchronized(drawableCache) { drawableCache[source] }
      if (cached != null) {
        synchronized(results) { results[source] = cached }
        if (remaining.decrementAndGet() == 0) onReady(results)
        continue
      }

      ioExecutor.execute {
        val drawable = runCatching { loadDrawable(context, source) }
          .onFailure { Log.w(TAG, "Failed to load puck image: $source", it) }
          .getOrNull()

        if (drawable != null) {
          synchronized(drawableCache) { drawableCache[source] = drawable }
          synchronized(results) { results[source] = drawable }
        }
        if (remaining.decrementAndGet() == 0) onReady(results)
      }
    }
  }

  private fun loadDrawable(context: Context, source: String): Drawable? {
    val trimmed = source.trim()

    return when {
      trimmed.startsWith("http://") || trimmed.startsWith("https://") ->
        downloadBitmap(trimmed)?.let { BitmapDrawable(context.resources, it) }

      trimmed.startsWith("asset://") ->
        context.assets.open(trimmed.removePrefix("asset://")).use { stream ->
          BitmapFactory.decodeStream(stream)?.let { BitmapDrawable(context.resources, it) }
        }

      trimmed.startsWith("file://") || trimmed.startsWith("/") -> {
        val path = trimmed.removePrefix("file://")
        if (File(path).exists()) {
          BitmapFactory.decodeFile(path)?.let { BitmapDrawable(context.resources, it) }
        } else {
          null
        }
      }

      else -> {
        // A bare name: a drawable resource shipped with the host app.
        val name = trimmed.substringAfterLast('/').substringBeforeLast('.')
        val id = context.resources.getIdentifier(name, "drawable", context.packageName)
        if (id != 0) ContextCompat.getDrawable(context, id) else null
      }
    }
  }

  private fun downloadBitmap(url: String): Bitmap? {
    val connection = (URL(url).openConnection() as HttpURLConnection).apply {
      connectTimeout = 15_000
      readTimeout = 20_000
      instanceFollowRedirects = true
      doInput = true
    }

    return try {
      if (connection.responseCode !in 200..299) {
        Log.w(TAG, "Puck image request failed (${connection.responseCode}): $url")
        null
      } else {
        connection.inputStream.use(BitmapFactory::decodeStream)
      }
    } finally {
      connection.disconnect()
    }
  }

  // ---------------------------------------------------------------------------
  // Value helpers
  // ---------------------------------------------------------------------------

  private fun floatValue(raw: Any?): Float? = (raw as? Number)?.toFloat()?.takeIf { it.isFinite() }

  private fun floatList(raw: Any?): List<Float>? {
    val list = (raw as? List<*>) ?: return null
    val values = list.mapNotNull { (it as? Number)?.toFloat() }
    if (values.size != list.size || values.any { !it.isFinite() }) return null
    return values.takeIf { it.isNotEmpty() }
  }

  private fun parseColor(raw: Any?): Int? {
    val hex = (raw as? String)?.trim()?.takeIf { it.startsWith("#") } ?: return null
    return runCatching { Color.parseColor(hex) }.getOrNull()
  }
}
