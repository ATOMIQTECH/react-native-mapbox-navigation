/**
 * Every code `onError` can carry.
 *
 * Recoverable — navigation carries on, or the package retries by itself:
 * - `NO_LOCATION_FIX`: no position yet; guidance starts once one arrives.
 * - `ROUTE_DISCARDED_NO_WINDOW`: retried once the view is attached.
 * - `REROUTE_FAILED`: the running route stays; the new target is retried.
 * - `INVALID_ROUTE_PROFILE`: routed with `driving-traffic` instead.
 * - `INVALID_ROAD_CLASS`, `ROAD_CLASS_NOT_SUPPORTED_BY_PROFILE`,
 *   `EXCLUDED_LOCATIONS_NOT_SUPPORTED_BY_PROFILE`, `TOO_MANY_EXCLUDED_LOCATIONS`,
 *   `VEHICLE_NOT_SUPPORTED_BY_PROFILE`: that option is ignored.
 * - `MAP_STYLE_CONFIG_UNSUPPORTED`: `mapStyleConfig` is ignored for this style.
 * - `NO_ACTIVITY`: Android; waits for a host activity.
 *
 * Fatal — no guidance until the app acts:
 * - `MISSING_ACCESS_TOKEN`, `LOCATION_PERMISSION_REQUIRED`,
 *   `START_ORIGIN_UNAVAILABLE`: configuration or permission.
 * - `NO_ROUTE`, `ROUTE_ERROR`: the first route could not be calculated.
 * - `NAVIGATION_INIT_FAILED`, `NO_HOST_VIEW_CONTROLLER`: the native session did not start.
 * - `NAVIGATION_SESSION_CONFLICT`: another view owns the session.
 * - `GUIDANCE_NOT_PROGRESSING`: fixes arrive but the navigator ignores them.
 */
export type NavigationErrorCode =
  | 'NO_LOCATION_FIX'
  | 'ROUTE_DISCARDED_NO_WINDOW'
  | 'REROUTE_FAILED'
  | 'INVALID_ROUTE_PROFILE'
  | 'INVALID_ROAD_CLASS'
  | 'ROAD_CLASS_NOT_SUPPORTED_BY_PROFILE'
  | 'EXCLUDED_LOCATIONS_NOT_SUPPORTED_BY_PROFILE'
  | 'TOO_MANY_EXCLUDED_LOCATIONS'
  | 'VEHICLE_NOT_SUPPORTED_BY_PROFILE'
  | 'MAP_STYLE_CONFIG_UNSUPPORTED'
  | 'NO_ACTIVITY'
  | 'MISSING_ACCESS_TOKEN'
  | 'LOCATION_PERMISSION_REQUIRED'
  | 'START_ORIGIN_UNAVAILABLE'
  | 'NO_ROUTE'
  | 'ROUTE_ERROR'
  | 'NAVIGATION_INIT_FAILED'
  | 'NO_HOST_VIEW_CONTROLLER'
  | 'NAVIGATION_SESSION_CONFLICT'
  | 'GUIDANCE_NOT_PROGRESSING'

/** Error payload emitted by native layer. */
export type NavigationError = {
  /** Machine-readable error code. See `NavigationErrorCode`. */
  code: NavigationErrorCode
  /** Developer-readable error details. Not for display, and not stable. */
  message: string
  /**
   * `true` when navigation carries on or the package retries by itself, so the app
   * should at most inform the user. `false` when nothing happens until the app acts.
   * A code this version does not know is reported as `false`.
   */
  recoverable: boolean
}
