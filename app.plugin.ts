import type { ExpoConfig } from 'expo/config'
import {
  type AndroidConfig,
  CodeGenerator,
  type ConfigPlugin,
  createRunOncePlugin,
  withAndroidManifest,
  withAppBuildGradle,
  withDangerousMod,
  withInfoPlist,
  withProjectBuildGradle,
  withSettingsGradle,
} from 'expo/config-plugins'
import { promises as fs } from 'node:fs'
import path from 'node:path'

import packageJson from './package.json'

export type MapboxNavigationPluginOptions = {
  /** Add `ACCESS_BACKGROUND_LOCATION` to the Android manifest. */
  backgroundLocation?: boolean
  /** Add the `audio` background mode on iOS so voice guidance keeps speaking. Defaults to `true`. */
  backgroundAudio?: boolean
  /** iOS location usage description shown in the permission prompt. */
  locationWhenInUsePermission?: string
  /** Share one Mapbox Maps SDK with `@rnmapbox/maps` when it is installed. Defaults to `true`. */
  rnmapboxMapsCompat?: boolean
}

type AndroidManifest = AndroidConfig.Manifest.AndroidManifest

const MAPBOX_VERSIONS = packageJson.mapbox
const LOG_PREFIX = '[@atomiqlab/react-native-mapbox-navigation]'
const MAPBOX_MAVEN_URL = 'https://api.mapbox.com/downloads/v2/releases/maven'

const MAPBOX_REPO_BLOCK = `    maven {
      url '${MAPBOX_MAVEN_URL}'
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
          url = uri("${MAPBOX_MAVEN_URL}")
          credentials {
            username = "mapbox"
            password = providers.gradleProperty("MAPBOX_DOWNLOADS_TOKEN").orElse(System.getenv("MAPBOX_DOWNLOADS_TOKEN") ?: "").get()
          }
          authentication {
            basic(BasicAuthentication)
          }
        }`

const RNMAPBOX_PACKAGE = '@rnmapbox/maps'

const REQUIRED_ANDROID_PERMISSIONS = [
  'android.permission.ACCESS_COARSE_LOCATION',
  'android.permission.ACCESS_FINE_LOCATION',
  'android.permission.FOREGROUND_SERVICE',
  'android.permission.FOREGROUND_SERVICE_LOCATION',
  'android.permission.POST_NOTIFICATIONS',
]

const BACKGROUND_LOCATION_PERMISSION = 'android.permission.ACCESS_BACKGROUND_LOCATION'

const DEFAULT_IOS_LOCATION_USAGE =
  'Allow $(PRODUCT_NAME) to access your location for turn-by-turn navigation.'

const PUBLIC_TOKEN_ENV = ['EXPO_PUBLIC_MAPBOX_ACCESS_TOKEN', 'MAPBOX_PUBLIC_TOKEN']
const PUBLIC_TOKEN_EXTRA = ['mapboxPublicToken', 'expoPublicMapboxAccessToken', 'mapboxAccessToken']

function isRnMapboxMapsInstalled(projectRoot: string): boolean {
  try {
    require.resolve(`${RNMAPBOX_PACKAGE}/package.json`, { paths: [projectRoot] })
    return true
  } catch {
    return false
  }
}

function resolveToken(config: ExpoConfig, envKeys: string[], extraKeys: string[]): string {
  for (const key of envKeys) {
    const raw: unknown = process.env[key]
    if (typeof raw === 'string' && raw.trim() !== '') {
      return raw.trim()
    }
  }
  const extra: Record<string, unknown> = config.extra ?? {}
  for (const key of extraKeys) {
    const value = extra[key]
    if (typeof value === 'string' && value.trim() !== '') {
      return value.trim()
    }
  }
  return ''
}

function hasMapboxTokenShape(token: string, expectedPrefix: string): boolean {
  return token.startsWith(expectedPrefix) && token.length > 20
}

function validateTokensIfPresent(config: ExpoConfig): void {
  const publicToken = resolveToken(config, PUBLIC_TOKEN_ENV, PUBLIC_TOKEN_EXTRA)
  const downloadsToken = resolveToken(config, ['MAPBOX_DOWNLOADS_TOKEN'], ['mapboxDownloadsToken'])

  if (publicToken !== '' && !hasMapboxTokenShape(publicToken, 'pk.')) {
    throw new Error(
      `${LOG_PREFIX} Invalid public token format. Expected a Mapbox public token starting with 'pk.'.`
    )
  }
  if (downloadsToken !== '' && !hasMapboxTokenShape(downloadsToken, 'sk.')) {
    throw new Error(
      `${LOG_PREFIX} Invalid downloads token format. Expected a Mapbox secret token starting with 'sk.' and DOWNLOADS:READ scope.`
    )
  }
  const providedLater =
    'This is fine if it will be provided by the build environment (EAS Secrets, Gradle properties, etc.).'
  if (publicToken === '') {
    console.warn(
      `${LOG_PREFIX} EXPO_PUBLIC_MAPBOX_ACCESS_TOKEN not found during expo config evaluation. ${providedLater}`
    )
  }
  if (downloadsToken === '') {
    console.warn(
      `${LOG_PREFIX} MAPBOX_DOWNLOADS_TOKEN not found during expo config evaluation. ${providedLater}`
    )
  }
}

function ensureAndroidPermissions(
  androidManifest: AndroidManifest,
  options: MapboxNavigationPluginOptions
): AndroidManifest {
  const { manifest } = androidManifest
  manifest['uses-permission'] ??= []
  const declared = manifest['uses-permission']
  const existing = new Set(declared.map((entry) => entry.$['android:name']))
  const permissions =
    options.backgroundLocation === true
      ? [...REQUIRED_ANDROID_PERMISSIONS, BACKGROUND_LOCATION_PERMISSION]
      : REQUIRED_ANDROID_PERMISSIONS
  for (const permission of permissions) {
    if (!existing.has(permission)) {
      declared.push({ $: { 'android:name': permission } })
    }
  }
  return androidManifest
}

function ensureSettingsGradle(src: string): string {
  if (src.includes(MAPBOX_MAVEN_URL)) {
    return src
  }
  const repositories = /dependencyResolutionManagement[\s\S]*?repositories\s*\{/mu.exec(src)
  if (repositories) {
    const marker = repositories[0]
    return src.replace(marker, `${marker}\n${MAPBOX_SETTINGS_REPO_BLOCK}\n`)
  }
  return `${src}\n\ndependencyResolutionManagement {\n  repositories {\n${MAPBOX_SETTINGS_REPO_BLOCK}\n  }\n}\n`
}

function ensureProjectBuildGradle(src: string): string {
  let out = src
  if (!out.includes('def mapboxDownloadsToken =')) {
    out =
      `def mapboxDownloadsToken = (findProperty("MAPBOX_DOWNLOADS_TOKEN") ?: System.getenv("MAPBOX_DOWNLOADS_TOKEN") ?: "")\n` +
      `  .toString()\n` +
      `  .replace('"', '')\n` +
      `  .trim()\n\n` +
      out
  }
  if (out.includes(MAPBOX_MAVEN_URL)) {
    return out
  }
  const withRepository = out.replace(
    /allprojects\s*\{\s*repositories\s*\{/mu,
    (match) => `${match}\n${MAPBOX_REPO_BLOCK}`
  )
  if (withRepository === out) {
    console.warn(
      `${LOG_PREFIX} Could not add the Mapbox Maven repository to android/build.gradle (no ` +
        '`allprojects { repositories {` block found). Add it manually, or the Mapbox SDK will fail ' +
        'to resolve at build time.'
    )
  }
  return withRepository
}

const MAPBOX_MAPS_VARIANT_MARKER = 'react-native-mapbox-navigation-mapbox-maps-variant'

function ensureMapboxMapsVariantAlignment(src: string): string {
  if (src.includes(MAPBOX_MAPS_VARIANT_MARKER)) {
    return src
  }
  return `${src}
// ${MAPBOX_MAPS_VARIANT_MARKER}
//
// Added by @atomiqlab/react-native-mapbox-navigation because @rnmapbox/maps is
// also installed. Both packages depend on the Mapbox Maps SDK, and its plain and
// -ndk27 Android artifacts carry the same classes — an app that resolves one of
// each fails checkDebugDuplicateClasses. @rnmapbox/maps selects its variant from
// targetSdkVersion (-ndk27 only at 35+) while the navigation SDK is always on
// -ndk27, so the plain coordinate is redirected to keep the whole app on one
// variant at the version Navigation v3 is built against.
allprojects {
  configurations.all {
    resolutionStrategy.dependencySubstitution {
      substitute(module('com.mapbox.maps:android'))
        .using(module('com.mapbox.maps:android-ndk27:${MAPBOX_VERSIONS.maps}'))
        .because('Mapbox Navigation SDK v3 requires the -ndk27 Maps variant; mixing variants duplicates classes')
    }
  }
}
`
}

function ensureAppBuildGradle(src: string): string {
  if (src.includes('resValue "string", "mapbox_access_token"')) {
    return src
  }
  const out = src.replace(/(versionName\s+"[^"]+"\s*\n)/mu, `$1${MAPBOX_TOKEN_LINES}\n`)
  if (out === src) {
    console.warn(
      `${LOG_PREFIX} Could not inject the Mapbox access token resource into android/app/build.gradle ` +
        '(no `versionName "..."` line found). Add `resValue "string", "mapbox_access_token", ' +
        '"<your pk. token>"` to defaultConfig manually.'
    )
  }
  return out
}

const withMapboxNavigationAndroid: ConfigPlugin<MapboxNavigationPluginOptions> = (
  config,
  options
) => {
  let next = withSettingsGradle(config, (mod) => {
    mod.modResults.contents = ensureSettingsGradle(mod.modResults.contents)
    return mod
  })
  next = withProjectBuildGradle(next, (mod) => {
    mod.modResults.contents = ensureProjectBuildGradle(mod.modResults.contents)
    if (
      options.rnmapboxMapsCompat !== false &&
      isRnMapboxMapsInstalled(mod.modRequest.projectRoot)
    ) {
      mod.modResults.contents = ensureMapboxMapsVariantAlignment(mod.modResults.contents)
    }
    return mod
  })
  next = withAppBuildGradle(next, (mod) => {
    mod.modResults.contents = ensureAppBuildGradle(mod.modResults.contents)
    return mod
  })
  return withAndroidManifest(next, (mod) => {
    mod.modResults = ensureAndroidPermissions(mod.modResults, options)
    return mod
  })
}

const MAPBOX_SPM_REQUIRE_TAG = '@atomiqlab/react-native-mapbox-navigation-spm-require'
const MAPBOX_SPM_INSTALLER_TAG = '@atomiqlab/react-native-mapbox-navigation-spm-post_install'
const MAPBOX_SPM_RNMAPBOX_TAG = '@atomiqlab/react-native-mapbox-navigation-rnmapbox-maps'

const RNMAPBOX_SPM_BLOCK = [
  '# @rnmapbox/maps resolves the Mapbox Maps SDK through CocoaPods by default,',
  '# while the Mapbox Navigation SDK v3 exists only as a Swift package. Resolving',
  "# one framework through both does not link, so 'manual' tells @rnmapbox/maps to",
  '# declare no Mapbox pods; $ExpoMapboxNavigation.post_install below then wires',
  '# its pod target up to the same Swift package this package uses',
  `# (MapboxMaps ${MAPBOX_VERSIONS.maps}).`,
  "$RNMapboxMapsSwiftPackageManager ||= 'manual'",
].join('\n')

function resolveSpmHelperPath(podfilePath: string): string {
  const helper = path.join(__dirname, 'ios', 'spm.rb')
  const relative = path.relative(path.dirname(podfilePath), helper)
  return relative.startsWith('.') ? relative : `./${relative}`
}

function applyPodfileSpmModifications(
  contents: string,
  podfilePath: string,
  rnmapboxMaps: boolean
): string {
  let src = contents
  if (rnmapboxMaps) {
    src = CodeGenerator.mergeContents({
      tag: MAPBOX_SPM_RNMAPBOX_TAG,
      src,
      newSrc: RNMAPBOX_SPM_BLOCK,
      anchor: /target .+ do/u,
      offset: 0,
      comment: '#',
    }).contents
  }
  src = CodeGenerator.mergeContents({
    tag: MAPBOX_SPM_REQUIRE_TAG,
    src,
    newSrc: `require_relative '${resolveSpmHelperPath(podfilePath)}'`,
    anchor: /target .+ do/u,
    offset: 0,
    comment: '#',
  }).contents
  const withHook = CodeGenerator.mergeContents({
    tag: MAPBOX_SPM_INSTALLER_TAG,
    src,
    newSrc: '    $ExpoMapboxNavigation.post_install(installer)',
    anchor: /^\s*post_install do \|installer\|/mu,
    offset: 1,
    comment: '#',
  })
  if (!withHook.didMerge) {
    console.warn(
      `${LOG_PREFIX} Could not find a \`post_install do |installer|\` block in the iOS Podfile, so ` +
        'the Mapbox Navigation v3 Swift package was not injected. The iOS build will fail to ' +
        'compile until you add this to your Podfile:\n' +
        '  post_install do |installer|\n' +
        '    $ExpoMapboxNavigation.post_install(installer)\n' +
        '  end'
    )
    return src
  }
  return withHook.contents
}

const withMapboxNavigationSpm: ConfigPlugin<MapboxNavigationPluginOptions> = (config, options) =>
  withDangerousMod(config, [
    'ios',
    async (mod) => {
      const podfilePath = path.join(mod.modRequest.platformProjectRoot, 'Podfile')
      const contents = await fs.readFile(podfilePath, 'utf8')
      const rnmapboxMaps =
        options.rnmapboxMapsCompat !== false && isRnMapboxMapsInstalled(mod.modRequest.projectRoot)
      await fs.writeFile(
        podfilePath,
        applyPodfileSpmModifications(contents, podfilePath, rnmapboxMaps),
        'utf8'
      )
      return mod
    },
  ])

const withMapboxNavigationIos: ConfigPlugin<MapboxNavigationPluginOptions> = (config, options) =>
  withInfoPlist(config, (mod) => {
    const infoPlist = mod.modResults
    const publicToken = resolveToken(mod, PUBLIC_TOKEN_ENV, PUBLIC_TOKEN_EXTRA)
    if (infoPlist.MBXAccessToken === undefined && publicToken !== '') {
      infoPlist.MBXAccessToken = publicToken
    }
    const usage = options.locationWhenInUsePermission
    infoPlist.NSLocationWhenInUseUsageDescription =
      usage ?? infoPlist.NSLocationWhenInUseUsageDescription ?? DEFAULT_IOS_LOCATION_USAGE
    infoPlist.NSLocationAlwaysAndWhenInUseUsageDescription =
      usage ?? infoPlist.NSLocationAlwaysAndWhenInUseUsageDescription ?? DEFAULT_IOS_LOCATION_USAGE
    const existing: string[] = Array.isArray(infoPlist.UIBackgroundModes)
      ? infoPlist.UIBackgroundModes
      : []
    const required = options.backgroundAudio === false ? ['location'] : ['location', 'audio']
    infoPlist.UIBackgroundModes = [...new Set([...existing, ...required])]
    return mod
  })

const withMapboxNavigation: ConfigPlugin<MapboxNavigationPluginOptions | undefined> = (
  config,
  options
) => {
  const resolved = options ?? {}
  validateTokensIfPresent(config)
  let next = withMapboxNavigationAndroid(config, resolved)
  next = withMapboxNavigationIos(next, resolved)
  return withMapboxNavigationSpm(next, resolved)
}

export default createRunOncePlugin(
  withMapboxNavigation,
  'react-native-mapbox-navigation-plugin',
  packageJson.version
)
