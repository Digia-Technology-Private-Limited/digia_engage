import 'dart:io';

import 'package:digia_engage/src/sdk_version.dart';
import 'package:flutter_test/flutter_test.dart';

/// Guards against `lib/src/version.dart` drifting from `pubspec.yaml`.
///
/// `bump_version.sh` regenerates `version.dart` from the pubspec version, so the
/// two must always match. This test fails loudly if a manual edit breaks that
/// invariant (which is how 1.4.0 / 1.7.0 drifted apart historically).
void main() {
  test('packageVersion matches pubspec.yaml version', () {
    final pubspec = File('pubspec.yaml').readAsLinesSync();
    final versionLine = pubspec.firstWhere(
      (line) => line.startsWith('version:'),
      orElse: () => '',
    );
    expect(versionLine, isNotEmpty, reason: 'no version: line in pubspec.yaml');

    final pubspecVersion = versionLine.substring('version:'.length).trim();

    expect(
      packageVersion,
      pubspecVersion,
      reason: 'lib/src/version.dart ($packageVersion) is out of sync with '
          'pubspec.yaml ($pubspecVersion). Run bump_version.sh or update '
          'version.dart to match.',
    );
  });
}
