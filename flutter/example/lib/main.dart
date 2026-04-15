import 'package:digia_engage/digia_engage.dart';
import 'package:digia_engage/digia_ui.dart';
import 'package:flutter/material.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();

  await Digia.initialize(
    DigiaConfig(apiKey: '69d3dc5e4d3eed4271b8c259'),
  );

  runApp(const ExampleApp());
}

class ExampleApp extends StatelessWidget {
  const ExampleApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'digia_engage Example',
      navigatorObservers: [DigiaNavigatorObserver()],
      builder: (context, child) => DigiaHost(child: child!),
      home: DUIFactory().createInitialPage(),
    );
  }
}
