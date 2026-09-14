# Swift Package Manager integration for the Mapbox Navigation SDK v3.
#
# Mapbox dropped CocoaPods support entirely in Navigation SDK v3: the
# `MapboxNavigation` pod stops at 2.22.0 and no `MapboxNavigationCore` /
# `MapboxNavigationUIKit` pods were ever published. A podspec cannot declare an
# SPM dependency, so the only way to ship v3 from a CocoaPods-integrated
# React Native / Expo project is to inject Swift package references into the
# generated Xcode projects from a `post_install` hook.
#
# The approach here follows @rnmapbox/maps, which has shipped the same
# technique for the Maps SDK for years. The important detail — and the one that
# is easy to get wrong — is that the package product must be attached to BOTH:
#
#   1. our own pod target inside Pods.xcodeproj, so the Swift sources in this
#      package can `import MapboxNavigationCore` and actually compile; and
#   2. every user target in the app project, so the frameworks are linked into
#      the final binary at runtime.
#
# Attaching to only (1) compiles but crashes at launch with a missing dylib;
# attaching to only (2) fails to compile this package.
#
# Usage (the Expo config plugin writes this into the Podfile automatically):
#
#   require_relative '../node_modules/@atomiqlab/react-native-mapbox-navigation/ios/spm.rb'
#
#   post_install do |installer|
#     $ExpoMapboxNavigation.post_install(installer)
#   end

require 'json'

$ExpoMapboxNavigation = Object.new

# The pod target that contains this package's Swift sources. Must match
# `s.name` in ExpoMapboxNavigationNative.podspec.
EXPO_MAPBOX_NAVIGATION_POD_TARGET = 'ExpoMapboxNavigationNative'

EXPO_MAPBOX_NAVIGATION_SPM_URL = 'https://github.com/mapbox/mapbox-navigation-ios.git'

# Pinned to a known-good v3 line. Overridable from the Podfile with
# `$ExpoMapboxNavigationVersion = '3.31.0'` for consumers who need to move
# ahead of us; `upToNextMajorVersion` keeps them inside v3.
EXPO_MAPBOX_NAVIGATION_DEFAULT_VERSION = '3.30.1'

# Every SPM product this package's Swift sources `import`, grouped by the
# package that vends it.
#
# All five have to be linked explicitly. SPM only lets a target import modules
# whose products it links directly — a transitive dependency of a linked product
# is *not* importable. `MapboxMaps` and `Turf` reach us transitively through
# `MapboxNavigationCore`, so omitting them fails with
# "no such module 'MapboxMaps'" even though the package graph resolved them.
#
#   mapbox-navigation-ios
#     MapboxNavigationCore  — navigator, routing, NavigationMapView
#     MapboxNavigationUIKit — NavigationViewController, NavigationOptions, StyleManager
#     MapboxDirections      — Waypoint, RouteOptions, Route, DirectionsError
#   mapbox-maps-ios
#     MapboxMaps            — MapView, puck configurations, ViewAnnotation
#   turf-swift
#     Turf                  — Point, used for annotation geometry
#
# The maps and turf versions are floors, not pins. The Navigation SDK's own
# Package.swift depends on them with `exact:` constraints, so SPM resolves the
# intersection and the Navigation SDK always wins — `upToNextMajorVersion` here
# just keeps us from resolving something older than we have tested.
def $ExpoMapboxNavigation._packages
  nav_version = $ExpoMapboxNavigationVersion || EXPO_MAPBOX_NAVIGATION_DEFAULT_VERSION
  [
    {
      url: EXPO_MAPBOX_NAVIGATION_SPM_URL,
      requirement: { kind: 'upToNextMajorVersion', minimumVersion: nav_version },
      products: %w[MapboxNavigationCore MapboxNavigationUIKit MapboxDirections],
    },
    {
      url: 'https://github.com/mapbox/mapbox-maps-ios.git',
      requirement: { kind: 'upToNextMajorVersion', minimumVersion: '11.30.1' },
      products: %w[MapboxMaps],
    },
    {
      url: 'https://github.com/mapbox/turf-swift.git',
      requirement: { kind: 'upToNextMajorVersion', minimumVersion: '4.0.0' },
      products: %w[Turf],
    },
  ]
end

# Idempotently add `product_name` from the Swift package at `url` to `target`.
#
# Three objects are required, and missing the third is the subtle one:
#
#   1. an `XCRemoteSwiftPackageReference` on the project — which package;
#   2. an `XCSwiftPackageProductDependency` on the target — which product;
#   3. a `PBXBuildFile` carrying that product dependency in the target's
#      **Frameworks build phase** — which actually links it.
#
# `link:` controls whether (3) is created, and it must differ per target:
#
#   * The **pod target** takes (1) and (2) but NOT (3). It needs the module on
#     its include path to compile — that comes from `SWIFT_INCLUDE_PATHS`, see
#     `_add_swift_include_path` — and it needs (2) so the build system builds
#     the packages before it. It must not *link* them: a static pod library
#     that links its dependencies makes the app link them a second time, and
#     the build fails with a wall of `duplicate symbol` errors.
#   * Every **user target** takes all three. The app is what actually links.
#
# Re-running `pod install` must not accumulate duplicates, so every lookup
# checks for an existing entry before creating one.
# Idempotently ensure the project references the Swift package at `url`.
def $ExpoMapboxNavigation._ensure_package_reference(project, url, requirement)
  pkg_class = Xcodeproj::Project::Object::XCRemoteSwiftPackageReference

  pkg = project.root_object.package_references.find do |p|
    p.class == pkg_class && p.repositoryURL == url
  end
  return pkg unless pkg.nil?

  pkg = project.new(pkg_class)
  pkg.repositoryURL = url
  pkg.requirement = requirement
  project.root_object.package_references << pkg
  pkg
end

def $ExpoMapboxNavigation._add_spm_to_target(project, target, url, requirement, product_name, link: true)
  return if target.nil?

  ref_class = Xcodeproj::Project::Object::XCSwiftPackageProductDependency

  pkg = self._ensure_package_reference(project, url, requirement)

  ref = target.package_product_dependencies.find do |r|
    r.class == ref_class && r.package == pkg && r.product_name == product_name
  end

  if ref.nil?
    ref = project.new(ref_class)
    ref.package = pkg
    ref.product_name = product_name
    target.package_product_dependencies << ref
  end

  # (3) Link it — only for targets that own the final binary.
  return unless link

  frameworks_phase = target.frameworks_build_phase
  return if frameworks_phase.nil?

  already_linked = frameworks_phase.files.any? do |build_file|
    build_file.product_ref == ref
  end
  return if already_linked

  build_file = project.new(Xcodeproj::Project::Object::PBXBuildFile)
  build_file.product_ref = ref
  frameworks_phase.files << build_file
end

# Warn when MapboxMaps is about to be resolved by both CocoaPods and SPM.
#
# Navigation v3 pulls MapboxMaps in through SPM (nav 3.30.1 pins it to exactly
# 11.30.1). If another pod — @rnmapbox/maps being the common case — also
# declares MapboxMaps as a *pod*, the app ends up with two copies of the same
# framework from two resolvers, which fails to link or crashes with duplicate
# class warnings. There is no way for us to fix that from here, so we detect it
# and say precisely what to do.
def $ExpoMapboxNavigation._warn_on_duplicate_maps(installer)
  maps_pod = installer.pod_targets.find { |p| p.name == 'MapboxMaps' }
  return if maps_pod.nil?

  puts ''
  puts '!!! [react-native-mapbox-navigation] MapboxMaps is being resolved by BOTH CocoaPods and SwiftPackageManager.'
  puts "!!!   CocoaPods is installing MapboxMaps #{maps_pod.version} as a pod, while the Mapbox"
  puts '!!!   Navigation SDK v3 pulls MapboxMaps in over SPM. Two copies of the same framework'
  puts '!!!   will not link correctly.'
  puts '!!!'
  puts '!!!   If this is @rnmapbox/maps, move it to SPM as well by setting this in your Podfile:'
  puts '!!!     $RNMapboxMapsSwiftPackageManager = {'
  puts '!!!       url: "https://github.com/mapbox/mapbox-maps-ios.git",'
  puts '!!!       requirement: { kind: "upToNextMajorVersion", minimumVersion: "11.30.1" },'
  puts '!!!       product_name: "MapboxMaps"'
  puts '!!!     }'
  puts '!!!'
  puts '!!!   and remove any $RNMapboxMapsVersion pin. Navigation v3 requires MapboxMaps 11.30.1,'
  puts '!!!   which satisfies rnmapbox\'s own "~> 11.16.2" constraint, so one resolver can serve both.'
  puts ''
end

# Make SPM-built Swift modules importable from a *static* pod target.
#
# SPM products that are plain Swift source targets — `MapboxMaps` is one — build
# to `<Products>/MapboxMaps.swiftmodule` plus a `.o` when the consuming pod is a
# static library. Xcode only adds `-F <target>/PackageFrameworks` for the
# product dependency, which is the right search path when products build as
# dynamic frameworks but is empty in the static case, so the compiler never sees
# the module and fails with "no such module 'MapboxMaps'".
#
# Adding the shared products root to `SWIFT_INCLUDE_PATHS` fixes the static case
# and is harmless in the dynamic one, where the frameworks are found first. This
# matters because consumers differ: an app using `use_frameworks!` (anything
# with @rnmapbox/maps, which forces dynamic linkage) gets frameworks, while a
# default Expo app gets static pods.
#
# `$(BUILT_PRODUCTS_DIR)` is the wrong variable here and silently does nothing:
# CocoaPods sets `CONFIGURATION_BUILD_DIR` per pod target, so
# `BUILT_PRODUCTS_DIR` resolves to this pod's *own* subdirectory
# (`…/Debug-iphonesimulator/ExpoMapboxNavigationNative`) rather than the shared
# root that SPM writes its modules into. `SYMROOT` is the shared base, so the
# products root has to be spelled out from it.
EXPO_MAPBOX_NAVIGATION_SHARED_PRODUCTS_DIR =
  '$(SYMROOT)/$(CONFIGURATION)$(EFFECTIVE_PLATFORM_NAME)'

def $ExpoMapboxNavigation._add_swift_include_path(target)
  return if target.nil?

  target.build_configurations.each do |config|
    paths = config.build_settings['SWIFT_INCLUDE_PATHS'] || ['$(inherited)']
    paths = [paths] unless paths.is_a?(Array)
    paths = ['$(inherited)'] + paths unless paths.include?('$(inherited)')
    next if paths.include?(EXPO_MAPBOX_NAVIGATION_SHARED_PRODUCTS_DIR)

    config.build_settings['SWIFT_INCLUDE_PATHS'] =
      paths + [EXPO_MAPBOX_NAVIGATION_SHARED_PRODUCTS_DIR]
  end
end

def $ExpoMapboxNavigation.post_install(installer)
  packages = self._packages

  # 1. Our own pod target, so this package's Swift compiles.
  pods_project = installer.pods_project
  pod_target = pods_project.targets.find { |t| t.name == EXPO_MAPBOX_NAVIGATION_POD_TARGET }

  if pod_target.nil?
    puts "!!! [react-native-mapbox-navigation] Could not find the '#{EXPO_MAPBOX_NAVIGATION_POD_TARGET}' " \
         'pod target in Pods.xcodeproj. The Mapbox Navigation SDK will not be linked and the build will fail. ' \
         'This usually means the pod was not installed — check that the package is in your dependencies.'
  end

  # The pod target gets the package *reference* (so the project resolves the
  # packages) and the include path — but deliberately NOT a product dependency.
  #
  # Xcode archives a static library target's package product dependencies *into*
  # the resulting `.a`. With the dependency attached, our
  # `libExpoMapboxNavigationNative.a` ended up containing `MapboxMaps.o`,
  # `MapboxNavigationCore.o` and friends, while the app linked the standalone
  # objects too — producing hundreds of `duplicate symbol` errors. Dropping just
  # the link phase was not enough; the dependency itself is what pulls them in.
  #
  # Compiling only needs the `.swiftmodule` on the search path, which
  # `_add_swift_include_path` provides, so the pod target needs nothing more.
  packages.each do |package|
    self._ensure_package_reference(pods_project, package[:url], package[:requirement])
  end

  self._add_swift_include_path(pod_target)

  # 2. Every user target, so the frameworks link into the app binary.
  installer.aggregate_targets.group_by(&:user_project).each do |project, targets|
    targets.each do |target|
      target.user_targets.each do |user_target|
        packages.each do |package|
          package[:products].each do |product|
            self._add_spm_to_target(project, user_target, package[:url], package[:requirement], product)
          end
        end
      end
      project.save
    end
  end

  self._warn_on_duplicate_maps(installer)
end
