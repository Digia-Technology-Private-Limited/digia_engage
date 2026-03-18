import 'package:digia_engage/digia_ui.dart';
import 'package:flutter/material.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  runApp(const DigiaUIExample());
}

class DigiaUIExample extends StatelessWidget {
  const DigiaUIExample({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      home: DigiaUIAppBuilder(
        options: DigiaUIOptions(
          accessKey: '69abfbcb79d23afa245a60ee',
          flavor: Flavor.debug(),
        ),
        builder: (context, status) {
          if (status.isLoading) {
            return const Center(child: CircularProgressIndicator());
          }
          if (status.hasError) {
            return const Center(child: Text('Error'));
          }
          return DUIFactory().createInitialPage();
        },
      ),
    );
  }
}
