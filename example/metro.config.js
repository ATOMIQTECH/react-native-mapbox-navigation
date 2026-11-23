const { getDefaultConfig } = require('expo/metro-config')
const path = require('path')

const config = getDefaultConfig(__dirname)

const escapeRegExp = (s) => s.replaceAll(/[.*+?^${}()|[\]\\]/g, '\\$&')

config.resolver.blockList = [
  ...Array.from(config.resolver.blockList ?? []),
  new RegExp(`^${escapeRegExp(path.resolve(__dirname, '..', 'node_modules'))}/`),
]

config.resolver.nodeModulesPaths = [path.resolve(__dirname, './node_modules')]

const expoDir = path.dirname(require.resolve('expo/package.json', { paths: [__dirname] }))

config.resolver.extraNodeModules = {
  '@atomiqlab/react-native-mapbox-navigation': path.resolve(__dirname, '..'),
  'expo-modules-core': path.dirname(
    require.resolve('expo-modules-core/package.json', { paths: [expoDir] })
  ),
}

config.watchFolders = [path.resolve(__dirname, '..')]

config.transformer.getTransformOptions = async () => ({
  transform: {
    experimentalImportSupport: false,
    inlineRequires: true,
  },
})

module.exports = config
