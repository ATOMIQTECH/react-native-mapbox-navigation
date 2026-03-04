const mapboxDownloadsToken = process.env.MAPBOX_DOWNLOADS_TOKEN || "";

module.exports = {
  expo: {
    name: "example-mapbox",
    slug: "example-mapbox",
    version: "1.0.0",
    orientation: "portrait",
    userInterfaceStyle: "light",
    ios: {
      bundleIdentifier: "com.expomapboxnavigationexample",
      supportsTablet: true
    },
    android: {
      predictiveBackGestureEnabled: false,
      package: "com.expomapboxnavigationexample"
    },
    plugins: [
      [
        "expo-build-properties",
        {
          android: {
            extraMavenRepos: [
              {
                url: "https://api.mapbox.com/downloads/v2/releases/maven",
                credentials: {
                  username: "mapbox",
                  password: mapboxDownloadsToken,
                },
                authentication: "basic",
              },
            ],
          },
        },
      ],
      "../app.plugin.js",
    ],
  },
}