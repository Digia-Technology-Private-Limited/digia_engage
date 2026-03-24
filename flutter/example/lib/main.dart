import 'package:digia_engage/digia_engage.dart';
import 'package:flutter/material.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();

  await Digia.initialize(
    DigiaConfig(apiKey: 'YOUR_DIGIA_API_KEY', flavor: Flavor.debug()),
  );

  // Register your CEP plugin (e.g. MoEngage, CleverTap) when ready.
  // Digia.register(MyCEPPlugin());

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
      home: const HomeScreen(),
    );
  }
}

class HomeScreen extends StatelessWidget {
  const HomeScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('digia_engage Example')),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: const [
          Text(
            'Inline campaign slots — add placement keys from Digia Studio',
            style: TextStyle(fontSize: 16),
          ),
          SizedBox(height: 16),
          DigiaSlot('hero_banner'),
          SizedBox(height: 16),
          DigiaSlot('mid_banner'),
        ],
      ),
    );
  }
}
