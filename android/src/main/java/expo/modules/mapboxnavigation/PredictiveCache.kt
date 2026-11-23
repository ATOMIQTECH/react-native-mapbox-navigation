package expo.modules.mapboxnavigation

import android.util.Log
import com.mapbox.maps.MapView
import com.mapbox.navigation.base.options.PredictiveCacheLocationOptions
import com.mapbox.navigation.base.options.PredictiveCacheMapsOptions
import com.mapbox.navigation.base.options.PredictiveCacheNavigationOptions
import com.mapbox.navigation.base.options.PredictiveCacheOptions
import com.mapbox.navigation.core.MapboxNavigation
import com.mapbox.navigation.ui.maps.PredictiveCacheController

internal object PredictiveCache {
  private const val TAG = "PredictiveCache"

  fun start(nav: MapboxNavigation, mapView: MapView): PredictiveCacheController? =
    runCatching {
      PredictiveCacheController(nav, options()).also { controller ->
        controller.createStyleMapControllers(mapView.mapboxMap)
      }
    }.onFailure { Log.w(TAG, "Predictive cache unavailable", it) }.getOrNull()

  fun stop(controller: PredictiveCacheController, mapView: MapView?) {
    mapView?.let { runCatching { controller.removeMapControllers(it.mapboxMap) } }
    runCatching { controller.onDestroy() }
  }

  private fun options(): PredictiveCacheOptions {
    val navigationLocation = PredictiveCacheLocationOptions.Builder()
      .currentLocationRadiusInMeters(3_000)
      .routeBufferRadiusInMeters(1_000)
      .destinationLocationRadiusInMeters(3_000)
      .loadPredictiveCacheForAlternativeRoutes(false)
      .build()
    val mapsLocation = PredictiveCacheLocationOptions.Builder()
      .currentLocationRadiusInMeters(2_000)
      .routeBufferRadiusInMeters(500)
      .destinationLocationRadiusInMeters(2_000)
      .loadPredictiveCacheForAlternativeRoutes(false)
      .build()
    return PredictiveCacheOptions.Builder()
      .predictiveCacheNavigationOptions(
        PredictiveCacheNavigationOptions.Builder()
          .predictiveCacheLocationOptions(navigationLocation)
          .build()
      )
      .predictiveCacheMapsOptionsList(
        listOf(
          PredictiveCacheMapsOptions.Builder()
            .predictiveCacheLocationOptions(mapsLocation)
            .build()
        )
      )
      .build()
  }
}
