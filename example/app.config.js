const mapboxDownloadsToken = process.env.MAPBOX_DOWNLOADS_TOKEN || "";

module.exports = {
  expo: {
    name: "example",
    slug: "example",
    version: "1.0.0",
    orientation: "portrait",
    icon: "./assets/icon.png",
    userInterfaceStyle: "light",
    splash: {
      image: "./assets/splash-icon.png",
      resizeMode: "contain",
      backgroundColor: "#ffffff"
    },
    ios: {
      bundleIdentifier: "com.tujyane.example",
      supportsTablet: true
    },
    android: {
      adaptiveIcon: {
        "backgroundColor": "#E6F4FE",
        "foregroundImage": "./assets/android-icon-foreground.png",
        "backgroundImage": "./assets/android-icon-background.png",
        "monochromeImage": "./assets/android-icon-monochrome.png"
      },
      predictiveBackGestureEnabled: false,
      package: "com.tujyane.example"
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
};
