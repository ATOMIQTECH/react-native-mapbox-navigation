
require 'json'

$ExpoMapboxNavigation = Object.new

EXPO_MAPBOX_NAVIGATION_POD_TARGET = 'ExpoMapboxNavigationNative'

EXPO_MAPBOX_NAVIGATION_SPM_URL = 'https://github.com/mapbox/mapbox-navigation-ios.git'

EXPO_MAPBOX_NAVIGATION_VERSIONS =
  begin
    versions = JSON.parse(File.read(File.join(__dir__, '..', 'package.json')))['mapbox']
    missing = %w[navigation maps turf].reject { |key| versions.is_a?(Hash) && versions[key] }
    unless missing.empty?
      raise "package.json has no `mapbox` versions for: #{missing.join(', ')}"
    end

    versions
  rescue StandardError => e
    raise "[react-native-mapbox-navigation] Could not read the Mapbox SDK versions from this " \
          "package's package.json (#{e.message}). The install looks incomplete — reinstall the " \
          'package and re-run `pod install`.'
  end

EXPO_MAPBOX_NAVIGATION_DEFAULT_VERSION = EXPO_MAPBOX_NAVIGATION_VERSIONS['navigation']

EXPO_MAPBOX_NAVIGATION_MAPS_VERSION = EXPO_MAPBOX_NAVIGATION_VERSIONS['maps']
EXPO_MAPBOX_NAVIGATION_TURF_VERSION = EXPO_MAPBOX_NAVIGATION_VERSIONS['turf']

EXPO_MAPBOX_NAVIGATION_MAPS_SPM_URL = 'https://github.com/mapbox/mapbox-maps-ios.git'

def $ExpoMapboxNavigation._packages
  nav_version = $ExpoMapboxNavigationVersion || EXPO_MAPBOX_NAVIGATION_DEFAULT_VERSION
  [
    {
      url: EXPO_MAPBOX_NAVIGATION_SPM_URL,
      requirement: { kind: 'exactVersion', version: nav_version },
      products: %w[MapboxNavigationCore MapboxNavigationUIKit MapboxDirections],
    },
    {
      url: EXPO_MAPBOX_NAVIGATION_MAPS_SPM_URL,
      requirement: { kind: 'exactVersion', version: EXPO_MAPBOX_NAVIGATION_MAPS_VERSION },
      products: %w[MapboxMaps],
    },
    {
      url: 'https://github.com/mapbox/turf-swift.git',
      requirement: { kind: 'exactVersion', version: EXPO_MAPBOX_NAVIGATION_TURF_VERSION },
      products: %w[Turf],
    },
  ]
end

def $ExpoMapboxNavigation._ensure_package_reference(project, url, requirement)
  pkg_class = Xcodeproj::Project::Object::XCRemoteSwiftPackageReference

  pkg = project.root_object.package_references.find do |p|
    p.class == pkg_class && p.repositoryURL == url
  end
  unless pkg.nil?
    pkg.requirement = requirement unless pkg.requirement == requirement
    return pkg
  end

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

EXPO_MAPBOX_NAVIGATION_RNMAPBOX_POD_TARGET = 'rnmapbox-maps'

EXPO_MAPBOX_NAVIGATION_RNMAPBOX_PRODUCTS = %w[MapboxMaps Turf].freeze

def $ExpoMapboxNavigation._integrate_rnmapbox_maps(pods_project, packages)
  target = pods_project.targets.find do |t|
    t.name == EXPO_MAPBOX_NAVIGATION_RNMAPBOX_POD_TARGET
  end
  return false if target.nil?

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

def $ExpoMapboxNavigation._use_collision_safe_uuids(project)
  generate = Xcodeproj::Project.instance_method(:generate_available_uuid_list)
  project.define_singleton_method(:generate_available_uuid_list) do |count = 100|
    generate.bind(self).call(count)
  end
  project.instance_variable_set(:@generated_uuids, [])
  project.instance_variable_set(:@available_uuids, [])
end

def $ExpoMapboxNavigation.post_install(installer)
  packages = self._packages

  pods_project = installer.pods_project
  self._use_collision_safe_uuids(pods_project)
  pod_target = pods_project.targets.find { |t| t.name == EXPO_MAPBOX_NAVIGATION_POD_TARGET }

  if pod_target.nil?
    puts "!!! [react-native-mapbox-navigation] Could not find the '#{EXPO_MAPBOX_NAVIGATION_POD_TARGET}' " \
         'pod target in Pods.xcodeproj. The Mapbox Navigation SDK will not be linked and the build will fail. ' \
         'This usually means the pod was not installed — check that the package is in your dependencies.'
  end

  packages.each do |package|
    self._ensure_package_reference(pods_project, package[:url], package[:requirement])
    package[:products].each do |product|
      self._add_spm_build_order_dependency(
        pods_project, pod_target, package[:url], package[:requirement], product
      )
    end
  end

  self._add_swift_include_path(pod_target)

  rnmapbox_integrated = self._integrate_rnmapbox_maps(pods_project, packages)

  pods_project.save

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
