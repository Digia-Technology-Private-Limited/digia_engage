Digia SDK — API Design & Architecture DecisionsDate: February 25, 2026 
Scope: Public API surface, architectural pattern, and rationale for Flutter, Compose (Android), and SwiftUI SDKs
Status: Finalized — ready for implementation

Product Context

Digia is an in-app engagement platform for Product Managers and Product Marketing Managers. It enables non-technical users to ship dynamic in-app experiences without app updates or developer bandwidth.
Two product types:

* In-App Messages — overlays: dialogs, bottom sheets, PIPs, fullscreen. Managed globally via DigiaHost.
* Inline Content — banners and widgets rendered inline inside scrolling content. Managed per-placement via DigiaSlot.

CEP Integration Model:

* CEP tools (CleverTap, MoEngage, WebEngage) own user identity, event tracking, and campaign evaluation.
* Digia owns the UI rendering layer exclusively.
* CEP plugins are separate installable packages outside the core Digia repo (digia_clevertap, digia_moengage, digia_webengage).
* In MVP, Digia.register() is mandatory. When Digia ships its own campaign management system, register() becomes optional.


Architectural Pattern: Static Facade with Internal Singleton

The Problem It Solves

SDKs that expose their singleton directly (e.g. CleverTap.getDefaultInstance(context)?) force developers to null-check on every call, hold references across the codebase, and understand internal SDK lifecycle just to track an event. SDKs that use builder + helper classes (e.g. MoEngage's MoEHelper.getInstance(context).trackEvent(...)) require learning two classes, two import paths, and grew into that shape due to organic design debt — not intentional architecture.

The Pattern

The singleton is an implementation detail, not a public API. It lives inside the SDK, is never exposed to the developer, and is never null-checked by the developer. All calls go through the Digia type directly as static methods.

Developer sees:          Digia.initialize(...)
                         Digia.register(...)
                         Digia.setCurrentScreen(...)

Developer never sees:    DigiaInstance.shared
                         DigiaSdk.getInstance()
                         Digia.shared?.doSomething()




Internal Structure

Digia (public, static facade)
└── DigiaInstance (internal singleton, never public)
    ├── DigiaConfig              — initialization parameters
    ├── DigiaCEPPlugin           — plugin protocol/interface




Why This Pattern Earns Developer Trust

* Zero ceremony: every call is Digia.verb(), one import, no instance management.
* No optional chaining: calls never fail silently or require null guards.
* Scales forward: when Digia adds its own campaign system, Digia.campaigns can be added as a namespaced accessor (Firebase-style) without breaking any existing integration.
* Testable internally: DigiaInstance can be fully unit tested; the facade delegates to it.


Complete Public API Surface

1. Initialization

Called once at application startup. Digia starts internal setup (networking, pre-fetch queue) immediately. The SDK enters a degraded mode if register() is not subsequently called — all calls are accepted and logged, but no experiences are displayed. A clear, actionable log message is emitted.

// Swift
Digia.initialize(DigiaConfig(apiKey: "prod_xxxx"))




// Kotlin
Digia.initialize(context, DigiaConfig(apiKey = "prod_xxxx"))




// Dart
Digia.initialize(DigiaConfig(apiKey: 'prod_xxxx'));



DigiaConfig

Parameter
	Type
	Required
	Description

apiKey
	String
	✅
	Environment-specific key from Digia dashboard

logLevel
	DigiaLogLevel
	❌
	.none, .error, .verbose. Default: .error

environment
	DigiaEnvironment
	❌
	.production, .sandbox. Default: .production



2. CEP Plugin Registration

Called after initialize() and after the CEP SDK is ready. Decoupled from initialization deliberately — CEP SDKs initialize asynchronously and may be owned by a different team.

// Swift — CleverTap
Digia.register(CleverTapPlugin(instance: CleverTap.sharedInstance()))

// Swift — MoEngage
Digia.register(MoEngagePlugin(instance: MoEngage.sharedInstance()))




// Kotlin — CleverTap
Digia.register(CleverTapPlugin(CleverTap.getDefaultInstance(context)))

// Kotlin — MoEngage
Digia.register(MoEngagePlugin(MoEngage.getInstance()))




// Dart — CleverTap
Digia.register(CleverTapPlugin(instance: cleverTapInstance));



DigiaCEPPlugin (Protocol / Interface)
Each CEP plugin conforms to this protocol. The core Digia SDK depends only on this abstraction — not on any specific CEP SDK.

// Swift
public protocol DigiaCEPPlugin {
    var identifier: String { get }
    func setup(delegate: DigiaCEPDelegate)
    func forwardScreen(name: String)
}




// Kotlin
interface DigiaCEPPlugin {
    val identifier: String
    fun setup(delegate: DigiaCEPDelegate)
    fun forwardScreen(name: String)
}




// Dart
abstract class DigiaCEPPlugin {
  String get identifier;
  void setup(DigiaCEPDelegate delegate);
  void forwardScreen(String name);
}




3. Screen Tracking

Called at each screen appearance. Digia internally forwards this to the registered CEP plugin (which calls its own recordScreenView / trackScreen equivalent) and records it for Digia's own targeting context. Developer replaces any existing CEP screen tracking call with this single call.

// Swift
Digia.setCurrentScreen("checkout")




// Kotlin
Digia.setCurrentScreen("checkout")




// Dart
Digia.setCurrentScreen('checkout');



Placement in code:

Platform
	Where to call

Flutter
	Inside NavigatorObserver.didPush (automatic) + DigiaScreen widget for edge cases

Android Compose
	DigiaScreen("checkout") composable — one line per screen

iOS UIKit
	Automatic via method swizzling on viewDidAppear — zero developer effort

iOS SwiftUI
	.digiaScreen("checkout") view modifier — one line per screen



4. DigiaHost — Global In-App Message Host

Wraps the application root. Listens for campaign payloads from the registered CEP plugin and renders overlay experiences (dialogs, bottom sheets, PIPs, fullscreen) above all app content. Must be placed once at the root. Placing it multiple times or below the navigation root has undefined behavior — the SDK logs a warning.
Marketing name mapping: "In-App Messages" → DigiaHost

// SwiftUI
var body: some View {
    DigiaHost {
        ContentView()
    }
}




// Compose
setContent {
    DigiaHost {
        MyAppNavHost()
    }
}




// Flutter
MaterialApp(
  navigatorObservers: [DigiaNavigatorObserver()],
  builder: (context, child) => DigiaHost(child: child!),
  home: HomeScreen(),
)




5. DigiaSlot — Per-Placement Inline Content

Placed by developers at specific positions in scrolling content or screen layouts. Renders inline experiences (banners, cards, widgets) designed by marketers in the Digia dashboard.
The placement key is the link between the developer's code and the marketer's campaign — the marketer selects the same key in the dashboard when creating content. The component is self-sizing by default — dimensions come from the campaign artifact. Explicit sizing is available for cases where the marketer has defined fixed dimensions or the layout requires a constraint.
Marketing name mapping: "Inline Content" → DigiaSlot

// SwiftUI — self-sizing
DigiaSlot("hero_banner")

// SwiftUI — with explicit height
DigiaSlot("hero_banner")
    .frame(height: 200)




// Compose — self-sizing
DigiaSlot(placementKey = "hero_banner")

// Compose — with explicit height
DigiaSlot(
    placementKey = "hero_banner",
    modifier = Modifier.height(200.dp)
)




// Flutter — self-sizing
DigiaSlot('hero_banner')

// Flutter — with explicit height
SizedBox(
    height: 200,
    child: DigiaSlot('hero_banner'),
)



Design decision on sizing: Self-sizing is the default and the marketer controls dimensions from the Digia dashboard. The developer can override using each platform's native sizing mechanism — Modifier in Compose, .frame() in SwiftUI, SizedBox in Flutter — rather than a Digia-specific constraints API. This keeps the component idiomatic and avoids a proprietary layout system.

Full Integration Example

Flutter

// main.dart
void main() {
  Digia.initialize(DigiaConfig(apiKey: 'prod_xxxx'));
  Digia.register(CleverTapPlugin(instance: cleverTapInstance));
  runApp(MyApp());
}

class MyApp extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      navigatorObservers: [DigiaNavigatorObserver()],
      builder: (context, child) => DigiaHost(child: child!),
      home: HomeScreen(),
    );
  }
}

// Any screen with inline content
class HomeScreen extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return ListView(
      children: [
        HeroSection(),
        DigiaSlot('home_hero_banner'),
        ProductGrid(),
        DigiaSlot('home_mid_banner'),
      ],
    );
  }
}




Android (Compose)

// Application.kt
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Digia.initialize(this, DigiaConfig(apiKey = "prod_xxxx"))
        Digia.register(CleverTapPlugin(CleverTap.getDefaultInstance(this)))
    }
}

// MainActivity.kt
setContent {
    DigiaHost {
        MyAppNavHost()
    }
}

// HomeScreen.kt
@Composable
fun HomeScreen() {
    DigiaScreen("home")
    LazyColumn {
        item { HeroSection() }
        item { DigiaSlot(placementKey = "home_hero_banner") }
        item { ProductGrid() }
        item { DigiaSlot(placementKey = "home_mid_banner") }
    }
}




iOS (SwiftUI)

// DigiApp.swift
@main
struct MyApp: App {
    init() {
        Digia.initialize(DigiaConfig(apiKey: "prod_xxxx"))
        Digia.register(CleverTapPlugin(instance: CleverTap.sharedInstance()))
    }
    var body: some Scene {
        WindowGroup {
            DigiaHost {
                ContentView()
            }
        }
    }
}

// HomeView.swift
struct HomeView: View {
    var body: some View {
        ScrollView {
            VStack {
                HeroSection()
                DigiaSlot("home_hero_banner")
                ProductGrid()
                DigiaSlot("home_mid_banner")
            }
        }
        .digiaScreen("home")
    }
}




API Surface Summary

Call
	Platform
	When
	Notes

Digia.initialize(config)
	All
	App startup, once
	Accepts DigiaConfig

Digia.register(plugin)
	All
	After CEP is ready
	Required in MVP

Digia.setCurrentScreen(name)
	All
	Each screen appearance
	Forwards to CEP internally

DigiaHost { }
	All
	App root, once
	Handles overlay IAM

DigiaSlot(placementKey)
	All
	Per inline placement
	Self-sizing by default

DigiaNavigatorObserver()
	Flutter only
	MaterialApp setup
	Auto screen tracking

DigiaScreen(name)
	Flutter / Compose
	Per screen (edge cases)
	Manual screen tracking

.digiaScreen(name)
	SwiftUI only
	Per view
	View modifier



CEP Plugin Packages (Separate Repos)

Package
	Wraps
	Install

digia_clevertap
	CleverTap iOS + Android SDK
	pub.dev / Maven / CocoaPods

digia_moengage
	MoEngage iOS + Android SDK
	pub.dev / Maven / CocoaPods

digia_webengage
	WebEngage iOS + Android SDK
	pub.dev / Maven / CocoaPods


Core digia package has zero dependency on any CEP SDK. It depends only on the DigiaCEPPlugin protocol defined within itself.

Decisions Deferred

* DigiaSlot sizing constraints API — explicit DigiaConstraints object for aspect ratios and responsive sizing. Deferred post-MVP.
* React Native SDK — pure JS wrapper, priority 4 after Flutter, Android, iOS.
* Digia Campaign Management System — when built, Digia.register() becomes optional and Digia can operate fully standalone.

End of Digia SDK API Design Knowledge FileGenerated: February 25, 2026
