const path = require('node:path')
const { promises: fs } = require('node:fs')
const {
  withProjectBuildGradle,
  withAppBuildGradle,
  withSettingsGradle,
  withAndroidManifest,
  withInfoPlist,
  withDangerousMod,
  createRunOncePlugin,
} = require('@expo/config-plugins')
const { mergeContents } = require('@expo/config-plugins/build/utils/generateCode')

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

/**
 * Wire the Mapbox Navigation SDK v3 into the app's Podfile as a Swift package.
 *
 * Mapbox ships no CocoaPods support for Navigation v3, and a podspec cannot
 * declare an SPM dependency, so the SDK has to be injected into the generated
 * Xcode projects from a `post_install` hook. `ios/spm.rb` does that work; this
 * mod only makes the Podfile call it.
 *
 * Consumers who do not use Expo prebuild must add the two lines below to their
 * Podfile by hand — that is documented in docs/v3-migration.md.
 */
const MAPBOX_SPM_REQUIRE_TAG = '@atomiqlab/react-native-mapbox-navigation-spm-require'
const MAPBOX_SPM_INSTALLER_TAG = '@atomiqlab/react-native-mapbox-navigation-spm-post_install'

function resolveSpmHelperPath(podfilePath) {
  // Resolved relative to the Podfile so the path stays correct under pnpm's
  // nested store layout, npm hoisting, and yarn workspaces alike.
  const helper = require.resolve('./ios/spm.rb')
  const relative = path.relative(path.dirname(podfilePath), helper)
  return relative.startsWith('.') ? relative : `./${relative}`
}

function applyPodfileSpmModifications(contents, podfilePath) {
  let src = contents

  src = mergeContents({
    tag: MAPBOX_SPM_REQUIRE_TAG,
    src,
    newSrc: `require_relative '${resolveSpmHelperPath(podfilePath)}'`,
    // Sits above the first target block, alongside the other requires Expo
    // and React Native generate.
    anchor: /target .+ do/,
    offset: 0,
    comment: '#',
  }).contents

  const withHook = mergeContents({
    tag: MAPBOX_SPM_INSTALLER_TAG,
    src,
    newSrc: '    $ExpoMapboxNavigation.post_install(installer)',
    anchor: /^\s*post_install do \|installer\|/m,
    offset: 1,
    comment: '#',
  })

  if (!withHook.didMerge) {
    console.warn(
      '[@atomiqlab/react-native-mapbox-navigation] Could not find a `post_install do |installer|` ' +
        'block in the iOS Podfile, so the Mapbox Navigation v3 Swift package was not injected. ' +
        'The iOS build will fail to compile until you add this to your Podfile:\n' +
        '  post_install do |installer|\n' +
        '    $ExpoMapboxNavigation.post_install(installer)\n' +
        '  end'
    )
    return src
  }

  return withHook.contents
}

function withMapboxNavigationSpm(config) {
  return withDangerousMod(config, [
    'ios',
    async (exportedConfig) => {
      const podfilePath = path.join(exportedConfig.modRequest.platformProjectRoot, 'Podfile')
      const contents = await fs.readFile(podfilePath, 'utf8')
      await fs.writeFile(podfilePath, applyPodfileSpmModifications(contents, podfilePath), 'utf8')
      return exportedConfig
    },
  ])
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
  config = withMapboxNavigationSpm(config)
  return config
}

module.exports = createRunOncePlugin(
  withMapboxNavigation,
  'react-native-mapbox-navigation-plugin',
  require('./package.json').version
)
