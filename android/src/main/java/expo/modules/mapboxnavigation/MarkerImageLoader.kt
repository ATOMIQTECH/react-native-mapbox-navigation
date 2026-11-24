package expo.modules.mapboxnavigation

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import android.util.LruCache
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

internal object MarkerImageLoader {
  private const val TAG = "MarkerImageLoader"
  private const val CACHE_BYTES = 16 * 1024 * 1024

  private val cache = object : LruCache<String, Bitmap>(CACHE_BYTES) {
    override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
  }
  private val waiting = HashMap<String, MutableList<() -> Unit>>()
  private val executor = Executors.newSingleThreadExecutor()
  private val mainHandler = Handler(Looper.getMainLooper())

  fun cached(uri: String): Bitmap? = cache.get(uri)

  fun load(context: Context, uri: String, done: () -> Unit) {
    if (cache.get(uri) != null) {
      done()
      return
    }
    waiting[uri]?.let { callbacks ->
      callbacks.add(done)
      return
    }
    waiting[uri] = mutableListOf(done)
    val appContext = context.applicationContext
    executor.execute {
      val bitmap = runCatching { decode(appContext, uri) }
        .onFailure { Log.w(TAG, "Could not load marker image $uri", it) }
        .getOrNull()
      mainHandler.post {
        bitmap?.let { cache.put(uri, it) }
        val callbacks = waiting.remove(uri).orEmpty()
        if (bitmap != null) callbacks.forEach { it() }
      }
    }
  }

  private fun decode(context: Context, uri: String): Bitmap? {
    val trimmed = uri.trim()
    return when {
      trimmed.startsWith("data:") -> {
        val bytes = Base64.decode(trimmed.substringAfter(','), Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
      }
      trimmed.startsWith("http://") || trimmed.startsWith("https://") -> download(trimmed)
      trimmed.startsWith("asset://") ->
        context.assets.open(trimmed.removePrefix("asset://")).use(BitmapFactory::decodeStream)
      trimmed.startsWith("file://") || trimmed.startsWith("/") -> {
        val path = trimmed.removePrefix("file://")
        if (File(path).exists()) BitmapFactory.decodeFile(path) else null
      }
      else -> decodeResource(context, trimmed)
    }
  }

  private fun decodeResource(context: Context, name: String): Bitmap? {
    val resourceName = name.substringAfterLast('/').substringBeforeLast('.')
    val drawable = context.resources.getIdentifier(resourceName, "drawable", context.packageName)
    if (drawable != 0) return BitmapFactory.decodeResource(context.resources, drawable)
    val raw = context.resources.getIdentifier(resourceName, "raw", context.packageName)
    if (raw != 0) return context.resources.openRawResource(raw).use(BitmapFactory::decodeStream)
    return null
  }

  private fun download(url: String): Bitmap? {
    val connection = (URL(url).openConnection() as HttpURLConnection).apply {
      connectTimeout = 15_000
      readTimeout = 20_000
      instanceFollowRedirects = true
    }
    return try {
      if (connection.responseCode !in 200..299) null
      else connection.inputStream.use(BitmapFactory::decodeStream)
    } finally {
      connection.disconnect()
    }
  }
}
