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
import android.util.Log
import androidx.core.content.ContextCompat
import com.mapbox.maps.ImageHolder
import com.mapbox.maps.plugin.LocationPuck
import com.mapbox.maps.plugin.LocationPuck2D
import com.mapbox.maps.plugin.LocationPuck3D
import com.mapbox.navigation.ui.maps.puck.LocationPuckOptions
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

internal object LocationPuckFactory {
  private const val TAG = "LocationPuckFactory"

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

  fun buildOptions(
    context: Context,
    states: Map<String, Map<String, Any>>,
    onReady: (LocationPuckOptions) -> Unit
  ) {
    if (states.isEmpty()) return

    val imageSources = LinkedHashSet<String>()
    for (appearance in states.values) {
      if ((appearance["type"] as? String)?.lowercase() != "2d") continue
      for (key in listOf("topImage", "bearingImage", "shadowImage")) {
        (appearance[key] as? String)?.takeIf { it.isNotBlank() }?.let(imageSources::add)
      }
    }

    val modelSources = LinkedHashSet<String>()
    for (appearance in states.values) {
      if ((appearance["type"] as? String)?.lowercase() != "3d") continue
      (appearance["modelUri"] as? String)?.trim()?.takeIf { it.isNotEmpty() }?.let(modelSources::add)
    }

    ioExecutor.execute {
      val models = modelSources.associateWith { localModelUri(context, it) }
      buildWithModels(context, states, imageSources, models, onReady)
    }
  }

  private fun buildWithModels(
    context: Context,
    states: Map<String, Map<String, Any>>,
    imageSources: Set<String>,
    models: Map<String, String?>,
    onReady: (LocationPuckOptions) -> Unit
  ) {
    loadDrawables(context, imageSources.toList()) { drawables ->
      val builder = LocationPuckOptions.Builder(context)
      var applied = false

      states["default"]?.let { appearance ->
        makePuck(context, appearance, drawables, models)?.let {
          builder.defaultPuck(it)
          applied = true
        }
      }

      for ((key, appearance) in states) {
        if (key == "default") continue
        val puck = makePuck(context, appearance, drawables, models) ?: continue
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

  private fun makePuck(
    context: Context,
    appearance: Map<String, Any>,
    drawables: Map<String, Drawable>,
    models: Map<String, String?>
  ): LocationPuck? = when ((appearance["type"] as? String)?.lowercase()) {
    "3d" -> make3D(appearance, models)
    "2d" -> make2D(appearance, drawables)
    "tinted" -> makeTinted(context, appearance)
    "none" -> makeHidden()
    else -> null
  }

  private fun make3D(appearance: Map<String, Any>, models: Map<String, String?>): LocationPuck? {
    val source = (appearance["modelUri"] as? String)?.trim()
    if (source.isNullOrEmpty()) {
      Log.w(TAG, "3D puck requested without a modelUri; keeping the default puck.")
      return null
    }
    val modelUri = models[source]
    if (modelUri == null) {
      Log.w(TAG, "3D puck model could not be loaded from $source; keeping the default puck.")
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

    if (top == null && bearing == null && shadow == null) {
      Log.w(TAG, "2D puck images could not be loaded; keeping the default puck.")
      return null
    }

    val scale = floatValue(appearance["scale"])
    val scaleExpression = (appearance["scaleExpression"] as? String)?.takeIf { it.isNotBlank() }
      ?: scale?.let { "[\"literal\", ${it.coerceIn(0.05f, 20f)}]" }

    return LocationPuck2D(
      topImage = top?.toImageHolder(),
      bearingImage = bearing?.toImageHolder(),
      shadowImage = shadow?.toImageHolder(),
      scaleExpression = scaleExpression,
      opacity = floatValue(appearance["opacity"])?.coerceIn(0f, 1f) ?: 1f
    )
  }

  private fun makeTinted(context: Context, appearance: Map<String, Any>): LocationPuck {
    val body = HexColor.parse(appearance["color"]) ?: HexColor.parse(appearance["bearingColor"])
      ?: Color.parseColor("#263A57")
    val halo = HexColor.parse(appearance["haloColor"]) ?: Color.WHITE
    val arrow = HexColor.parse(appearance["bearingColor"]) ?: body

    val density = context.resources.displayMetrics.density
    val scale = (floatValue(appearance["scale"]) ?: 1f).coerceIn(0.2f, 4f)
    val size = (28f * density * scale).toInt().coerceAtLeast(8)

    return LocationPuck2D(
      topImage = circleImage(size, body, halo, density * scale),
      bearingImage = arrowImage((size * 1.6f).toInt().coerceAtLeast(8), arrow),
      shadowImage = circleImage(
        (size * 1.35f).toInt().coerceAtLeast(8),
        Color.argb(40, Color.red(body), Color.green(body), Color.blue(body)),
        Color.TRANSPARENT,
        0f
      ),
      scaleExpression = null,
      opacity = floatValue(appearance["opacity"])?.coerceIn(0f, 1f) ?: 1f
    )
  }

  private fun makeHidden(): LocationPuck = LocationPuck2D(
    topImage = transparentImage(),
    bearingImage = transparentImage(),
    shadowImage = transparentImage(),
    scaleExpression = null,
    opacity = 0f
  )


  private fun Drawable.toImageHolder(): ImageHolder {
    (this as? BitmapDrawable)?.bitmap?.let { return ImageHolder.from(it) }

    val width = intrinsicWidth.takeIf { it > 0 } ?: 1
    val height = intrinsicHeight.takeIf { it > 0 } ?: 1
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    setBounds(0, 0, width, height)
    draw(canvas)
    return ImageHolder.from(bitmap)
  }

  private fun transparentImage(): ImageHolder =
    ImageHolder.from(Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888))

  private fun circleImage(
    size: Int,
    fillColor: Int,
    strokeColor: Int,
    strokeWidth: Float
  ): ImageHolder {
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

    return ImageHolder.from(bitmap)
  }

  private fun arrowImage(size: Int, color: Int): ImageHolder {
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

    return ImageHolder.from(bitmap)
  }


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

  private val modelCache = HashMap<String, String>()

  private fun localModelUri(context: Context, source: String): String? {
    synchronized(modelCache) { modelCache[source]?.let { return it } }
    val lower = source.lowercase()
    val hasModelExtension = lower.endsWith(".glb") || lower.endsWith(".gltf")
    if (hasModelExtension && (lower.startsWith("asset://") || lower.startsWith("file://"))) {
      return source
    }

    val dir = File(context.cacheDir, "puck-models").apply { mkdirs() }
    val target = File(dir, "model-${Integer.toHexString(source.hashCode())}.glb")
    val file = File(dir, "${target.name}.${System.nanoTime()}.tmp")
    val copied = try {
      when {
        lower.startsWith("http://") || lower.startsWith("https://") -> {
          val connection = (URL(source).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
          }
          try {
            if (connection.responseCode !in 200..299) {
              false
            } else {
              connection.inputStream.use { input -> file.outputStream().use { input.copyTo(it) } }
              true
            }
          } finally {
            connection.disconnect()
          }
        }
        lower.startsWith("asset://") -> {
          context.assets.open(source.removePrefix("asset://")).use { input ->
            file.outputStream().use { input.copyTo(it) }
          }
          true
        }
        lower.startsWith("file://") || lower.startsWith("/") -> {
          File(source.removePrefix("file://")).inputStream().use { input ->
            file.outputStream().use { input.copyTo(it) }
          }
          true
        }
        else -> {
          val name = source.substringAfterLast('/').substringBeforeLast('.')
          val id = context.resources.getIdentifier(name, "raw", context.packageName)
          if (id == 0) {
            false
          } else {
            context.resources.openRawResource(id).use { input ->
              file.outputStream().use { input.copyTo(it) }
            }
            true
          }
        }
      }
    } catch (error: Exception) {
      Log.w(TAG, "Could not load the 3D puck model $source", error)
      false
    }

    if (!copied || file.length() == 0L || !file.renameTo(target)) {
      file.delete()
      return null
    }
    val uri = "file://${target.absolutePath}"
    synchronized(modelCache) { modelCache[source] = uri }
    return uri
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

  private fun floatValue(raw: Any?): Float? = (raw as? Number)?.toFloat()?.takeIf { it.isFinite() }

  private fun floatList(raw: Any?): List<Float>? {
    val list = (raw as? List<*>) ?: return null
    val values = list.mapNotNull { (it as? Number)?.toFloat() }
    if (values.size != list.size || values.any { !it.isFinite() }) return null
    return values.takeIf { it.isNotEmpty() }
  }
}
