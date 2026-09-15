require 'json'

package = JSON.parse(File.read(File.join(__dir__, '..', 'package.json')))

Pod::Spec.new do |s|
  # Must not collide with Mapbox's internal pod named "MapboxNavigationNative".
  s.name           = 'ExpoMapboxNavigationNative'
  s.version        = package['version']
  s.summary        = 'Native Mapbox Navigation module for Expo'
  s.description    = 'A custom Expo module that wraps the native Mapbox Navigation SDKs for iOS and Android.'
  s.license        = { :type => 'MIT' }
  s.author         = { 'ATOMIQ Ltd' => 'info@atomiq.rw' }
  s.homepage       = 'https://github.com/ATOMIQTECH/react-native-mapbox-navigation'
  s.platform       = :ios, '14.0'
  # Navigation SDK v3 requires Swift 5.9 / Xcode 16.
  s.swift_version  = '5.9'
  s.source         = { :git => 'https://github.com/ATOMIQTECH/react-native-mapbox-navigation.git', :tag => s.version.to_s }
  # Must be dynamic: the Mapbox Navigation v3 frameworks arrive over SPM and
  # are dynamically linked, so a static wrapper around them fails to link.
  s.static_framework = false

  s.dependency 'ExpoModulesCore'

  # NOTE: the Mapbox Navigation SDK is deliberately NOT declared here.
  #
  # Mapbox dropped CocoaPods support in Navigation v3 — the `MapboxNavigation`
  # pod stops at 2.22.0 and no v3 pods exist. The SDK is instead injected as a
  # Swift package by `ios/spm.rb`, which the Expo config plugin wires into the
  # app's Podfile. See docs/v3-migration.md.

  # Swift/Objective-C compatibility
  s.pod_target_xcconfig = {
    'DEFINES_MODULE' => 'YES',
    'SWIFT_COMPILATION_MODE' => 'wholemodule'
  }

  s.source_files = "**/*.{h,m,mm,swift,hpp,cpp}"
end
