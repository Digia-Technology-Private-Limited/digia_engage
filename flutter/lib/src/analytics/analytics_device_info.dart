import 'dart:io';

import 'package:device_info_plus/device_info_plus.dart';
import 'package:flutter/foundation.dart';

/// Helper class for capturing device and OS information
class AnalyticsDeviceInfo {
  /// Retrieves device manufacturer and model information
  static Future<Map<String, String?>> getDeviceInfo() async {
    try {
      if (kIsWeb) {
        return {'device_make': null, 'device_model': null};
      }

      final deviceInfoPlugin = DeviceInfoPlugin();

      if (Platform.isAndroid) {
        final androidInfo = await deviceInfoPlugin.androidInfo;
        return {
          'device_make': androidInfo.manufacturer,
          'device_model': androidInfo.model,
        };
      } else if (Platform.isIOS) {
        final iosInfo = await deviceInfoPlugin.iosInfo;
        return {
          'device_make': 'Apple',
          'device_model': iosInfo.model,
        };
      } else if (Platform.isMacOS) {
        final macOsInfo = await deviceInfoPlugin.macOsInfo;
        return {
          'device_make': 'Apple',
          'device_model': macOsInfo.model,
        };
      }

      return {'device_make': null, 'device_model': null};
    } catch (e) {
      debugPrint('[Digia Analytics] Failed to get device info: $e');
      return {'device_make': null, 'device_model': null};
    }
  }

  /// Formats OS version string with major version and OS name
  static String formatOsVersion() {
    if (kIsWeb) {
      return 'web';
    }

    try {
      final version = Platform.operatingSystemVersion;

      if (Platform.isAndroid) {
        // Android version is typically "x.y.z"
        // Extract just the major version
        final parts = version.split('.');
        return parts.isNotEmpty ? 'Android ${parts[0]}' : version;
      } else if (Platform.isIOS) {
        // iOS version is typically "x.y.z"
        // Extract major version
        final parts = version.split('.');
        return parts.isNotEmpty ? 'iOS ${parts[0]}' : version;
      } else if (Platform.isMacOS) {
        final parts = version.split('.');
        return parts.isNotEmpty ? 'macOS ${parts[0]}' : version;
      }

      return version;
    } catch (e) {
      return Platform.operatingSystemVersion;
    }
  }

  /// Gets the device platform identifier
  static String getDevicePlatform() {
    if (kIsWeb) return 'web';
    if (Platform.isIOS) return 'ios';
    if (Platform.isAndroid) return 'android';
    if (Platform.isMacOS) return 'macos';
    if (Platform.isWindows) return 'windows';
    if (Platform.isLinux) return 'linux';
    return 'unknown';
  }
}
