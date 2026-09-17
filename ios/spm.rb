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
# A third job, for apps that also use @rnmapbox/maps: that package wants the
# Maps SDK as a *pod*, and one app resolving MapboxMaps through both CocoaPods
# and SPM does not link. `_integrate_rnmapbox_maps` folds its pod target into
# the SPM graph resolved here, so installing both packages needs no Podfile
# edits from the consumer.
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

# The native SDK versions, read from package.json's `mapbox` block rather than
# written here. Three files need them — this one, android/build.gradle and
# app.plugin.js — and keeping them in one place is what stops the Maps version
# in particular from drifting: app.plugin.js hands it to @rnmapbox/maps, so a
# stale copy silently puts the two packages on different Maps SDKs.
EXPO_MAPBOX_NAVIGATION_VERSIONS =
  begin
    versions = JSON.parse(File.read(File.join(__dir__, '..', 'package.json')))['mapbox']
    missing = %w[navigation maps turf].reject { |key| versions.is_a?(Hash) && versions[key] }
    unless missing.empty?
      raise "package.json has no `mapbox` versions for: #{missing.join(', ')}"
    end

    versions
  rescue StandardError => e
    # Without this the failure surfaces as a `NoMethodError` on `nil` from
    # somewhere inside `pod install`, which says nothing about the cause.
    raise "[react-native-mapbox-navigation] Could not read the Mapbox SDK versions from this " \
          "package's package.json (#{e.message}). The install looks incomplete — reinstall the " \
          'package and re-run `pod install`.'
  end

# Overridable from the Podfile with `$ExpoMapboxNavigationVersion = '3.31.0'`
# for consumers who need to move ahead of us; `upToNextMajorVersion` keeps them
# inside v3.
EXPO_MAPBOX_NAVIGATION_DEFAULT_VERSION = EXPO_MAPBOX_NAVIGATION_VERSIONS['navigation']

# The Maps SDK version Navigation v3 is built against. Published in package.json
# so the config plugin can hand the same value to @rnmapbox/maps.
EXPO_MAPBOX_NAVIGATION_MAPS_VERSION = EXPO_MAPBOX_NAVIGATION_VERSIONS['maps']
EXPO_MAPBOX_NAVIGATION_TURF_VERSION = EXPO_MAPBOX_NAVIGATION_VERSIONS['turf']

EXPO_MAPBOX_NAVIGATION_MAPS_SPM_URL = 'https://github.com/mapbox/mapbox-maps-ios.git'

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
      url: EXPO_MAPBOX_NAVIGATION_MAPS_SPM_URL,
      requirement: {
        kind: 'upToNextMajorVersion', minimumVersion: EXPO_MAPBOX_NAVIGATION_MAPS_VERSION
      },
      products: %w[MapboxMaps],
    },
    {
      url: 'https://github.com/mapbox/turf-swift.git',
      requirement: {
        kind: 'upToNextMajorVersion', minimumVersion: EXPO_MAPBOX_NAVIGATION_TURF_VERSION
      },
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
#   * The **pod target** takes (1) only, and never goes through this function.
#     It finds the modules via `SWIFT_INCLUDE_PATHS` (see
#     `_add_swift_include_path`) and gets its build ordering from a non-linking
#     `PBXTargetDependency` (see `_add_spm_build_order_dependency`). It must
#     take neither (2) nor (3): a static pod library archives its package
#     product dependencies into its own `.a`, so the app links them a second
#     time and the build fails with a wall of `duplicate symbol` errors.
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

# Make the pod target *wait* for the Swift packages without linking them.
#
# `SWIFT_INCLUDE_PATHS` (below) tells the compiler where to find the SPM
# `.swiftmodule` files, but nothing told the build system to produce them
# first. With no dependency of any kind from the pod target to the packages,
# their relative order is unconstrained — and on a cold build the pod target
# can start before the packages have been built, failing with
# "no such module 'MapboxMaps'". Locally this almost always won the race
# because DerivedData already held the modules from an earlier build, which is
# why it only ever showed up on clean CI machines.
#
# A `PBXTargetDependency` that carries a `productRef` instead of a `target` is
# how Xcode models "depend on this package product" separately from linking it.
# It lives in the target's `dependencies` list, NOT in
# `package_product_dependencies`, so it orders the build without reintroducing
# the `duplicate symbol` failures described above — those come from the static
# library archiving its package product dependencies.
def $ExpoMapboxNavigation._add_spm_build_order_dependency(project, target, url, requirement, product_name)
  return if target.nil?

  ref_class = Xcodeproj::Project::Object::XCSwiftPackageProductDependency
  pkg = self._ensure_package_reference(project, url, requirement)

  already = target.dependencies.any? do |dep|
    dep.product_ref &&
      dep.product_ref.package == pkg &&
      dep.product_ref.product_name == product_name
  end
  return if already

  ref = project.new(ref_class)
  ref.package = pkg
  ref.product_name = product_name

  dependency = project.new(Xcodeproj::Project::Object::PBXTargetDependency)
  dependency.product_ref = ref
  target.dependencies << dependency
end

# @rnmapbox/maps interoperability.
#
# Most apps that navigate also render a plain map, so @rnmapbox/maps alongside
# this package is the common case rather than an edge one. Both need the Mapbox
# Maps SDK, and they reach for it through different resolvers: Navigation v3
# exists only as a Swift package, while @rnmapbox/maps declares `MapboxMaps` as
# a *pod*. An app that resolves the same framework through CocoaPods and SPM at
# once does not link.
#
# The config plugin settles that by writing `$RNMapboxMapsSwiftPackageManager =
# 'manual'` into the Podfile whenever @rnmapbox/maps is installed. 'manual' means
# two things in @rnmapbox/maps: its podspec declares no Mapbox pods at all (the
# `unless $RNMapboxMapsSwiftPackageManager` guard drops both `MapboxMaps` and
# `Turf`), and its own `post_install` returns before touching the Xcode project.
# So the whole job lands here, on the SPM packages this file already resolves —
# and, because @rnmapbox/maps then writes nothing, it does not matter whether its
# `post_install` runs before or after ours. That ordering is decided by the order
# the two packages happen to sit in the app's `plugins` array, which is not
# something a consumer should have to get right.
EXPO_MAPBOX_NAVIGATION_RNMAPBOX_POD_TARGET = 'rnmapbox-maps'

# The SPM products the @rnmapbox/maps Swift sources import and no longer get as
# pods. `MapboxMobileEvents` is also imported but sits behind `#if
# canImport(...)`, so it needs nothing from us.
EXPO_MAPBOX_NAVIGATION_RNMAPBOX_PRODUCTS = %w[MapboxMaps Turf].freeze

# Give the @rnmapbox/maps pod target the same SPM MapboxMaps this package uses.
#
# Deliberately the *compile-only* treatment our own pod target gets — include
# path plus a non-linking build-order dependency, and no entry in
# `package_product_dependencies`. @rnmapbox/maps' own SPM helper attaches a
# product dependency instead, which is right for dynamic frameworks and wrong
# twice over for a static pod: the compiler gets no module search path
# ("no such module 'MapboxMaps'"), and Xcode archives the dependency into
# `librnmapbox-maps.a` so the app links those objects a second time.
#
# The static case is the one that matters. Expo's precompiled-modules pipeline
# keeps Mapbox pods static ("Disabling USE_FRAMEWORKS for ... rnmapbox-maps")
# even when the app asks for dynamic frameworks, so `use_frameworks!` is not an
# escape hatch. On a genuinely dynamic target both changes below are inert,
# because the frameworks are found first.
#
# Returns true when the target was found and wired up.
def $ExpoMapboxNavigation._integrate_rnmapbox_maps(pods_project, packages)
  target = pods_project.targets.find do |t|
    t.name == EXPO_MAPBOX_NAVIGATION_RNMAPBOX_POD_TARGET
  end
  return false if target.nil?

  # Normally a no-op: with 'manual' set, @rnmapbox/maps attached nothing. It
  # matters for consumers who set the Hash form of
  # `$RNMapboxMapsSwiftPackageManager` themselves and whose Podfile therefore
  # still runs `$RNMapboxMaps.post_install` — see `_warn_on_duplicate_maps`.
  self._detach_spm_product_dependencies(target, EXPO_MAPBOX_NAVIGATION_RNMAPBOX_PRODUCTS)

  packages.each do |package|
    wanted = package[:products] & EXPO_MAPBOX_NAVIGATION_RNMAPBOX_PRODUCTS
    wanted.each do |product|
      self._add_spm_build_order_dependency(
        pods_project, target, package[:url], package[:requirement], product
      )
    end
  end

  self._add_swift_include_path(target)
  true
end

# Drop linking product dependencies for `product_names` from `target`, leaving
# the project's package references alone so SPM still resolves them.
def $ExpoMapboxNavigation._detach_spm_product_dependencies(target, product_names)
  return if target.nil?

  refs = target.package_product_dependencies.select do |ref|
    product_names.include?(ref.product_name)
  end
  return if refs.empty?

  frameworks_phase = target.frameworks_build_phase
  unless frameworks_phase.nil?
    frameworks_phase.files.dup.each do |build_file|
      frameworks_phase.files.delete(build_file) if refs.include?(build_file.product_ref)
    end
  end

  target.package_product_dependencies.delete_if { |ref| refs.include?(ref) }
end

# Report a MapboxMaps pod we could not absorb into the SPM graph.
#
# `_integrate_rnmapbox_maps` handles @rnmapbox/maps, which is the only package
# in common use that declares MapboxMaps as a pod. Anything else that does —
# or a consumer who has overridden `$RNMapboxMapsSwiftPackageManager` with the
# Hash form, which leaves @rnmapbox/maps declaring its own Maps pod — still ends
# up with the same framework from two resolvers, and that we cannot fix from
# here.
def $ExpoMapboxNavigation._warn_on_duplicate_maps(installer, rnmapbox_integrated)
  maps_pod = installer.pod_targets.find { |p| p.name == 'MapboxMaps' }
  return if maps_pod.nil?

  spm_setting = $RNMapboxMapsSwiftPackageManager

  puts ''
  puts '!!! [react-native-mapbox-navigation] MapboxMaps is being resolved by BOTH CocoaPods and SwiftPackageManager.'
  puts "!!!   CocoaPods is installing MapboxMaps #{maps_pod.version} as a pod, while the Mapbox"
  puts '!!!   Navigation SDK v3 pulls MapboxMaps in over SPM. Two copies of the same framework'
  puts '!!!   will not link correctly.'
  puts '!!!'

  if rnmapbox_integrated && spm_setting.is_a?(Hash)
    puts '!!!   Your Podfile sets $RNMapboxMapsSwiftPackageManager to a Hash, which keeps'
    puts '!!!   @rnmapbox/maps declaring its own MapboxMaps pod. Remove that assignment and let'
    puts "!!!   this package's config plugin set it to 'manual' instead — it then wires the"
    puts "!!!   @rnmapbox/maps pod target up to the same Swift package, at MapboxMaps"
    puts "!!!   #{EXPO_MAPBOX_NAVIGATION_MAPS_VERSION}, with no Podfile edits of your own."
  else
    puts '!!!   Some pod in this project declares MapboxMaps as a CocoaPods dependency. Find it'
    puts '!!!   with `grep -rl MapboxMaps ios/Pods/Local\ Podspecs` and move it to SPM, or drop'
    puts '!!!   it — Navigation v3 requires the Swift package at'
    puts "!!!   #{EXPO_MAPBOX_NAVIGATION_MAPS_VERSION}, which this package already resolves for you."
  end

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
  # packages), a non-linking build-order dependency, and the include path — but
  # deliberately NOT an entry in `package_product_dependencies`.
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
    package[:products].each do |product|
      self._add_spm_build_order_dependency(
        pods_project, pod_target, package[:url], package[:requirement], product
      )
    end
  end

  self._add_swift_include_path(pod_target)

  # 1b. The @rnmapbox/maps pod target, when that package is installed, so its
  #     Swift compiles against the same MapboxMaps instead of a second copy.
  rnmapbox_integrated = self._integrate_rnmapbox_maps(pods_project, packages)

  pods_project.save

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

  self._warn_on_duplicate_maps(installer, rnmapbox_integrated)
end
