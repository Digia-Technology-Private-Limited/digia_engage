Pod::Spec.new do |s|
  s.name             = 'DigiaEngageReactNative'
  s.version          = '0.1.0'
  s.summary          = 'React Native bridge for the Digia Engage SDK (Android Compose UI).'
  s.description      = <<-DESC
    Provides a React Native bridge that surfaces the Digia Engage Android
    Compose UI SDK inside React Native applications.  iOS support is stubbed
    and will be added in a future release.
  DESC

  s.homepage         = 'https://github.com/Digia-Technology-Private-Limited/digia_engage'
  s.license          = { :type => 'MIT', :file => '../LICENSE' }
  s.author           = { 'Digia Technology Private Limited' => '' }
  s.source           = { :git => 'https://github.com/Digia-Technology-Private-Limited/digia_engage.git', :tag => s.version.to_s }

  s.ios.deployment_target = '13.0'

  s.source_files = 'ios/**/*.{h,m,mm,swift}'

  s.dependency 'React-Core'
end
