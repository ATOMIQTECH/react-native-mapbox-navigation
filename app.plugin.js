const {
  withProjectBuildGradle,
  withAppBuildGradle,
  withSettingsGradle,
  withAndroidManifest,
  withInfoPlist,
  createRunOncePlugin,
} = require('@expo/config-plugins')

const MAPBOX_REPO_BLOCK = `    maven {
      url 'https://api.mapbox.com/downloads/v2/releases/maven'
      authentication {
        basic(BasicAuthentication)
      }
      credentials {
        username = "mapbox"
        password = mapboxDownloadsToken
      }
    }`

const MAPBOX_TOKEN_LINES = `        def mapboxPublicToken = project.findProperty("MAPBOX_PUBLIC_TOKEN") ?: System.getenv("MAPBOX_PUBLIC_TOKEN") ?: System.getenv("EXPO_PUBLIC_MAPBOX_ACCESS_TOKEN") ?: ""
        resValue "string", "mapbox_access_token", mapboxPublicToken`

const MAPBOX_SETTINGS_REPO_BLOCK = `        maven {
          url = uri("https://api.mapbox.com/downloads/v2/releases/maven")
          credentials {
            username = "mapbox"
            password = providers.gradleProperty("MAPBOX_DOWNLOADS_TOKEN").orElse(System.getenv("MAPBOX_DOWNLOADS_TOKEN") ?: "").get()
          }
          authentication {
            basic(BasicAuthentication)
          }
        }`

const REQUIRED_ANDROID_PERMISSIONS = [
  'android.permission.ACCESS_COARSE_LOCATION',
  'android.permission.ACCESS_FINE_LOCATION',
  'android.permission.FOREGROUND_SERVICE',
  'android.permission.FOREGROUND_SERVICE_LOCATION',
  'android.permission.POST_NOTIFICATIONS',
]

/**
 * Requesting ACCESS_BACKGROUND_LOCATION triggers a Google Play policy review
 * and a prominent-disclosure requirement, so it is opt-in rather than added to
 * every app that installs this package.
 *
 * Enable with `["@atomiqlab/react-native-mapbox-navigation", { backgroundLocation: true }]`.
 */
const BACKGROUND_LOCATION_PERMISSION = 'android.permission.ACCESS_BACKGROUND_LOCATION'

const DEFAULT_IOS_LOCATION_USAGE =
  'Allow $(PRODUCT_NAME) to access your location for turn-by-turn navigation.'

function resolveToken(config, envKeys = [], extraKeys = []) {
  for (const key of envKeys) {
    const value = process.env[key]?.trim()
    if (value) {
      return value
    }
  }

  const extra = config?.extra ?? {}
  for (const key of extraKeys) {
    const value = typeof extra[key] === 'string' ? extra[key].trim() : ''
    if (value) {
      return value
    }
  }

  return ''
}

function validateMapboxTokenShape(token, expectedPrefix) {
  return token.startsWith(expectedPrefix) && token.length > 20
}

/**
 * Validate tokens when present, warn when absent.
 *
 * Tokens are intentionally not required at config-eval time so that CI/build
 * environments that inject them via build-time env vars (EAS Secrets, Gradle
 * properties, etc.) can prebuild without having the tokens available locally.
 */
function validateTokensIfPresent(config) {
  const publicToken = resolveToken(
    config,
    ['EXPO_PUBLIC_MAPBOX_ACCESS_TOKEN', 'MAPBOX_PUBLIC_TOKEN'],
    ['mapboxPublicToken', 'expoPublicMapboxAccessToken', 'mapboxAccessToken']
  )
  const downloadsToken = resolveToken(
    config,
    ['MAPBOX_DOWNLOADS_TOKEN'],
    ['mapboxDownloadsToken']
  )

  if (publicToken && !validateMapboxTokenShape(publicToken, 'pk.')) {
    throw new Error(
      "[@atomiqlab/react-native-mapbox-navigation] Invalid public token format. Expected a Mapbox public token starting with 'pk.'."
    )
  }

  if (downloadsToken && !validateMapboxTokenShape(downloadsToken, 'sk.')) {
    throw new Error(
      "[@atomiqlab/react-native-mapbox-navigation] Invalid downloads token format. Expected a Mapbox secret token starting with 'sk.' and DOWNLOADS:READ scope."
    )
  }

  if (!publicToken) {
    console.warn(
      '[@atomiqlab/react-native-mapbox-navigation] EXPO_PUBLIC_MAPBOX_ACCESS_TOKEN not found during expo config evaluation. ' +
        'This is fine if it will be provided by the build environment (EAS Secrets, Gradle properties, etc.).'
    )
  }

  if (!downloadsToken) {
    console.warn(
      '[@atomiqlab/react-native-mapbox-navigation] MAPBOX_DOWNLOADS_TOKEN not found during expo config evaluation. ' +
        'This is fine if it will be provided by the build environment (EAS Secrets, Gradle properties, etc.).'
    )
  }
}

function ensureAndroidPermissions(androidManifest, options = {}) {
  const manifest = androidManifest.manifest
  if (!manifest['uses-permission']) {
    manifest['uses-permission'] = []
  }

  const existingPermissions = new Set(
    manifest['uses-permission'].map((entry) => entry?.$?.['android:name']).filter(Boolean)
  )

  const permissions = options.backgroundLocation
    ? [...REQUIRED_ANDROID_PERMISSIONS, BACKGROUND_LOCATION_PERMISSION]
    : REQUIRED_ANDROID_PERMISSIONS

  permissions.forEach((permission) => {
    if (!existingPermissions.has(permission)) {
      manifest['uses-permission'].push({
        $: {
          'android:name': permission,
        },
      })
    }
  })

  return androidManifest
}

function ensureSettingsGradle(src) {
  let out = src
  if (out.includes('https://api.mapbox.com/downloads/v2/releases/maven')) {
    return out
  }

  const drmReposMatch = /dependencyResolutionManagement[\s\S]*?repositories\s*\{/m.exec(out)
  if (drmReposMatch) {
    const marker = drmReposMatch[0]
    out = out.replace(marker, `${marker}\n${MAPBOX_SETTINGS_REPO_BLOCK}\n`)
    return out
  }

  // Fallback for uncommon settings.gradle shapes: append a full block.
  out += `\n\ndependencyResolutionManagement {\n  repositories {\n${MAPBOX_SETTINGS_REPO_BLOCK}\n  }\n}\n`

  return out
}

function ensureProjectBuildGradle(src) {
  let out = src

  if (!out.includes('def mapboxDownloadsToken =')) {
    out =
      `def mapboxDownloadsToken = (findProperty("MAPBOX_DOWNLOADS_TOKEN") ?: System.getenv("MAPBOX_DOWNLOADS_TOKEN") ?: "")\n` +
      `  .toString()\n` +
      `  .replace('"', '')\n` +
      `  .trim()\n\n` +
      out
  }

  if (!out.includes('https://api.mapbox.com/downloads/v2/releases/maven')) {
    const before = out
    // Match `allprojects { repositories {` without depending on which
    // repositories the template happens to list first.
    out = out.replace(
      /allprojects\s*\{\s*repositories\s*\{/m,
      (match) => `${match}\n${MAPBOX_REPO_BLOCK}`
    )

    if (out === before) {
      console.warn(
        '[@atomiqlab/react-native-mapbox-navigation] Could not add the Mapbox Maven repository to ' +
          'android/build.gradle (no `allprojects { repositories {` block found). Add it manually, ' +
          'or the Mapbox SDK will fail to resolve at build time.'
      )
    }
  }

  return out
}

function ensureAppBuildGradle(src) {
  if (src.includes('resValue "string", "mapbox_access_token"')) {
    return src
  }

  const out = src.replace(/(versionName\s+"[^"]+"\s*\n)/m, `$1${MAPBOX_TOKEN_LINES}\n`)

  if (out === src) {
    console.warn(
      '[@atomiqlab/react-native-mapbox-navigation] Could not inject the Mapbox access token resource ' +
        'into android/app/build.gradle (no `versionName "..."` line found). Add ' +
        '`resValue "string", "mapbox_access_token", "<your pk. token>"` to defaultConfig manually.'
    )
  }

  return out
}

function withMapboxNavigationAndroid(config, options) {
  config = withSettingsGradle(config, (config) => {
    config.modResults.contents = ensureSettingsGradle(config.modResults.contents)
    return config
  })

  config = withProjectBuildGradle(config, (config) => {
    config.modResults.contents = ensureProjectBuildGradle(config.modResults.contents)
    return config
  })

  config = withAppBuildGradle(config, (config) => {
    config.modResults.contents = ensureAppBuildGradle(config.modResults.contents)
    return config
  })

  config = withAndroidManifest(config, (config) => {
    config.modResults = ensureAndroidPermissions(config.modResults, options)
    return config
  })

  return config
}

function withMapboxNavigationIos(config, options) {
  return withInfoPlist(config, (config) => {
    const infoPlist = config.modResults
    const mapboxPublicToken = resolveToken(
      config,
      ['EXPO_PUBLIC_MAPBOX_ACCESS_TOKEN', 'MAPBOX_PUBLIC_TOKEN'],
      ['mapboxPublicToken', 'expoPublicMapboxAccessToken', 'mapboxAccessToken']
    )

    if (!infoPlist.MBXAccessToken && mapboxPublicToken) {
      infoPlist.MBXAccessToken = mapboxPublicToken
    }

    if (!infoPlist.NSLocationWhenInUseUsageDescription) {
      infoPlist.NSLocationWhenInUseUsageDescription = DEFAULT_IOS_LOCATION_USAGE
    }

    if (!infoPlist.NSLocationAlwaysAndWhenInUseUsageDescription) {
      infoPlist.NSLocationAlwaysAndWhenInUseUsageDescription = DEFAULT_IOS_LOCATION_USAGE
    }

    if (options.locationWhenInUsePermission) {
      infoPlist.NSLocationWhenInUseUsageDescription = options.locationWhenInUsePermission
      infoPlist.NSLocationAlwaysAndWhenInUseUsageDescription = options.locationWhenInUsePermission
    }

    const existingModes = Array.isArray(infoPlist.UIBackgroundModes)
      ? infoPlist.UIBackgroundModes
      : []
    const modes = new Set(existingModes)
    modes.add('location')
    // Background audio keeps spoken instructions playing when the app is
    // backgrounded. It is opt-out because it must be justified at review.
    if (options.backgroundAudio !== false) {
      modes.add('audio')
    }
    infoPlist.UIBackgroundModes = Array.from(modes)

    return config
  })
}

/**
 * @typedef {object} MapboxNavigationPluginOptions
 * @property {boolean} [backgroundLocation=false]
 *   Add `ACCESS_BACKGROUND_LOCATION` on Android. Requires a Google Play policy
 *   declaration and prominent in-app disclosure, so it is off by default.
 * @property {boolean} [backgroundAudio=true]
 *   Keep the iOS `audio` background mode so spoken guidance continues while
 *   backgrounded. Set to `false` if your app does not need it at review time.
 * @property {string} [locationWhenInUsePermission]
 *   Override the iOS location usage description shown in the permission prompt.
 */

/** @param {MapboxNavigationPluginOptions} [options] */
const withMapboxNavigation = (config, options = {}) => {
  const resolvedOptions = options ?? {}
  validateTokensIfPresent(config)
  config = withMapboxNavigationAndroid(config, resolvedOptions)
  config = withMapboxNavigationIos(config, resolvedOptions)
  return config
}

module.exports = createRunOncePlugin(
  withMapboxNavigation,
  'react-native-mapbox-navigation-plugin',
  require('./package.json').version
)
