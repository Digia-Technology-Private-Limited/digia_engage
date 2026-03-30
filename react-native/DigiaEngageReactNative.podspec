Pod::Spec.new do |s|
  s.name             = 'DigiaEngageReactNative'
  s.version          = '0.1.0'
  s.summary          = 'React Native bridge for the Digia Engage SDK (iOS & Android).'
  s.description      = <<-DESC
    Provides a React Native bridge that surfaces the Digia Engage SDK inside
    React Native applications.  Supports both iOS (SwiftUI) and Android
    (Jetpack Compose).  Works on both Old Architecture (bridge) and New
    Architecture (TurboModules / JSI) — on Android, the module is resolved as
    a true TurboModule when the host enables New Arch; on iOS, the interop
    layer automatically wraps the bridge module for TurboModule access.
  DESC

  s.license          = { :type => 'MIT', :file => '../LICENSE' }

  # DigiaEngage iOS SDK requires iOS 16+ (SwiftUI features used internally).
  s.ios.deployment_target = '16.0'

  s.source_files = 'ios/**/*.{h,m,mm,swift}'

  # Swift version must match the Digia iOS SDK.
  s.swift_version = '5.9'

  s.dependency 'React-Core'

  # ── Digia Engage iOS SDK ──────────────────────────────────────────────────
  # Available on SPM: https://swiftpackageindex.com/Digia-Technology-Private-Limited/digia_engage_iOS
  # CocoaPods: host app Podfile must declare the git source (see README).
  s.dependency 'DigiaEngage', '1.0.0-beta.1'

  # ── New Architecture (TurboModules) support ──────────────────────────────
  # Links New Architecture dependencies when the host app enables it.
  # On New Arch the Swift module is wrapped as a TurboModule via interop;
  # on Old Arch this is a no-op and the classic bridge is used.
  install_modules_dependencies(s)
end
