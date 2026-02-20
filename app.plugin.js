const {
  withProjectBuildGradle,
  withAppBuildGradle,
  withAndroidManifest,
  withInfoPlist,
  createRunOncePlugin,
} = require("@expo/config-plugins");

const MAPBOX_REPO_BLOCK = `    maven {
      url 'https://api.mapbox.com/downloads/v2/releases/maven'
      authentication {
        basic(BasicAuthentication)
      }
      credentials {
        username = "mapbox"
        password = mapboxDownloadsToken
      }
    }`;

const MAPBOX_TOKEN_LINES = `        def mapboxPublicToken = project.findProperty("MAPBOX_PUBLIC_TOKEN") ?: System.getenv("EXPO_PUBLIC_MAPBOX_ACCESS_TOKEN") ?: ""
        resValue "string", "mapbox_access_token", mapboxPublicToken`;

const REQUIRED_ANDROID_PERMISSIONS = [
  "android.permission.ACCESS_COARSE_LOCATION",
  "android.permission.ACCESS_FINE_LOCATION",
  "android.permission.ACCESS_BACKGROUND_LOCATION",
  "android.permission.FOREGROUND_SERVICE",
  "android.permission.FOREGROUND_SERVICE_LOCATION",
  "android.permission.POST_NOTIFICATIONS",
];

const DEFAULT_IOS_LOCATION_USAGE =
  "Allow $(PRODUCT_NAME) to access your location for turn-by-turn navigation.";

function ensureAndroidPermissions(androidManifest) {
  const manifest = androidManifest.manifest;
  if (!manifest["uses-permission"]) {
    manifest["uses-permission"] = [];
  }

  const existingPermissions = new Set(
    manifest["uses-permission"]
      .map((entry) => entry?.$?.["android:name"])
      .filter(Boolean)
  );

  REQUIRED_ANDROID_PERMISSIONS.forEach((permission) => {
    if (!existingPermissions.has(permission)) {
      manifest["uses-permission"].push({
        $: {
          "android:name": permission,
        },
      });
    }
  });

  return androidManifest;
}

function ensureProjectBuildGradle(src) {
  let out = src;

  if (!out.includes("def mapboxDownloadsToken =")) {
    out =
      `def mapboxDownloadsToken = (findProperty("MAPBOX_DOWNLOADS_TOKEN") ?: System.getenv("MAPBOX_DOWNLOADS_TOKEN") ?: "")\n` +
      `  .toString()\n` +
      `  .replace('"', '')\n` +
      `  .trim()\n\n` +
      out;
  }

  if (!out.includes("https://api.mapbox.com/downloads/v2/releases/maven")) {
    out = out.replace(
      /allprojects\s*\{\s*repositories\s*\{\s*google\(\)\s*mavenCentral\(\)/m,
      (match) => `${match}\n${MAPBOX_REPO_BLOCK}`
    );
  }

  return out;
}

function ensureAppBuildGradle(src) {
  if (src.includes('resValue "string", "mapbox_access_token"')) {
    return src;
  }

  return src.replace(
    /(versionName\s+"[^"]+"\s*\n)/m,
    `$1${MAPBOX_TOKEN_LINES}\n`
  );
}

function withMapboxNavigationAndroid(config) {
  config = withProjectBuildGradle(config, (config) => {
    config.modResults.contents = ensureProjectBuildGradle(
      config.modResults.contents
    );
    return config;
  });

  config = withAppBuildGradle(config, (config) => {
    config.modResults.contents = ensureAppBuildGradle(config.modResults.contents);
    return config;
  });

  config = withAndroidManifest(config, (config) => {
    config.modResults = ensureAndroidPermissions(config.modResults);
    return config;
  });

  return config;
}

function withMapboxNavigationIos(config) {
  return withInfoPlist(config, (config) => {
    const infoPlist = config.modResults;
    const mapboxPublicToken =
      process.env.EXPO_PUBLIC_MAPBOX_ACCESS_TOKEN ||
      process.env.MAPBOX_PUBLIC_TOKEN ||
      "";

    if (!infoPlist.MBXAccessToken && mapboxPublicToken) {
      infoPlist.MBXAccessToken = mapboxPublicToken;
    }

    if (!infoPlist.NSLocationWhenInUseUsageDescription) {
      infoPlist.NSLocationWhenInUseUsageDescription = DEFAULT_IOS_LOCATION_USAGE;
    }

    if (!infoPlist.NSLocationAlwaysAndWhenInUseUsageDescription) {
      infoPlist.NSLocationAlwaysAndWhenInUseUsageDescription = DEFAULT_IOS_LOCATION_USAGE;
    }

    const existingModes = Array.isArray(infoPlist.UIBackgroundModes)
      ? infoPlist.UIBackgroundModes
      : [];
    const mergedModes = new Set([...existingModes, "location", "audio"]);
    infoPlist.UIBackgroundModes = Array.from(mergedModes);

    return config;
  });
}

const withMapboxNavigation = (config) => {
  config = withMapboxNavigationAndroid(config);
  config = withMapboxNavigationIos(config);
  return config;
};

module.exports = createRunOncePlugin(
  withMapboxNavigation,
  "react-native-mapbox-navigation-plugin",
  "1.1.1"
);
