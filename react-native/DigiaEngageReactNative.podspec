Pod::Spec.new do |s|
  s.name             = 'DigiaEngageReactNative'
  s.version          = '0.1.0'
  s.summary          = 'React Native bridge for the Digia Engage SDK (iOS & Android).'
  s.description      = <<-DESC
    Provides a React Native bridge that surfaces the Digia Engage SDK inside
    React Native applications.  Supports both iOS (SwiftUI) and Android
    (Jetpack Compose) using the New Architecture (TurboModules / Fabric).
  DESC

  s.homepage         = 'https://github.com/Digia-Technology-Private-Limited/digia_engage'
  s.license          = { :type => 'MIT', :file => '../LICENSE' }
  s.author           = { 'Digia Technology Private Limited' => '' }
  s.source           = { :git => 'https://github.com/Digia-Technology-Private-Limited/digia_engage.git', :tag => s.version.to_s }

  # DigiaEngage iOS SDK requires iOS 16+ (SwiftUI features used internally).
  s.ios.deployment_target = '16.0'

  s.source_files = 'ios/**/*.{h,m,mm,swift}'

  # Swift version must match the Digia iOS SDK.
  s.swift_version = '5.9'

  s.dependency 'React-Core'

  # ── Digia Engage iOS SDK ──────────────────────────────────────────────────
  # Published Swift Package: https://swiftpackageindex.com/Digia-Technology-Private-Limited/digia_engage_iOS
  # Source: https://github.com/Digia-Technology-Private-Limited/digia_engage_iOS.git
  #
  # When integrating via CocoaPods, add the git source to your Podfile:
  #   pod 'DigiaEngage', :git => 'https://github.com/Digia-Technology-Private-Limited/digia_engage_iOS.git',
  #                       :tag => '1.0.0-beta.1'
  s.dependency 'DigiaEngage', '1.0.0-beta.1'

  # ── New Architecture (Fabric / TurboModules) support ─────────────────────
  # install_modules_dependencies wires the pod into both Old Architecture
  # (bridge) and New Architecture (JSI / Fabric) automatically when the host
  # app has `use_frameworks!` / `use_react_native!` with :fabric_enabled.
  install_modules_dependencies(s)
end
