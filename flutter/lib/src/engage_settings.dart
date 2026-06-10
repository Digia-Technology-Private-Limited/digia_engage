import 'package:uuid/uuid.dart';

import 'preferences_store.dart';

class EngageSettings {
  static final EngageSettings _instance = EngageSettings._();
  static const String _uuidKey = 'uuid';

  EngageSettings._();

  static EngageSettings get instance => _instance;

  String getUuid() {
    String? uuid = PreferencesStore.instance.read<String>(_uuidKey);
    if (uuid == null) {
      uuid = const Uuid().v4();
      PreferencesStore.instance.write(_uuidKey, uuid);
    }
    return uuid;
  }
}
