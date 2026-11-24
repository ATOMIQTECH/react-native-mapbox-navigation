require 'json'

package = JSON.parse(File.read(File.join(__dir__, '..', 'package.json')))

Pod::Spec.new do |s|
  s.name           = 'ExpoMapboxNavigationNative'
  s.version        = package['version']
  s.summary        = 'Native Mapbox Navigation module for Expo'
  s.description    = 'A custom Expo module that wraps the native Mapbox Navigation SDKs for iOS and Android.'
  s.license        = { :type => 'MIT' }
  s.author         = { 'ATOMIQ Ltd' => 'info@atomiq.rw' }
  s.homepage       = 'https://github.com/ATOMIQTECH/react-native-mapbox-navigation'
  s.platform       = :ios, '14.0'
  s.swift_version  = '5.9'
  s.source         = { :git => 'https://github.com/ATOMIQTECH/react-native-mapbox-navigation.git', :tag => s.version.to_s }
  s.static_framework = false

  s.dependency 'ExpoModulesCore'


  s.pod_target_xcconfig = {
    'DEFINES_MODULE' => 'YES',
    'SWIFT_COMPILATION_MODE' => 'wholemodule'
  }

  s.source_files = "**/*.{h,m,mm,swift,hpp,cpp}"
end
