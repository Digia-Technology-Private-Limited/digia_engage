Digia — CEP Plugin Interface DesignDate: February 25, 2026
Scope: DigiaCEPPlugin protocol, bidirectional communication architecture, DigiaHost internals, Flutter / Compose / SwiftUI code samples
Status: Finalized — ready for implementation



Architectural Overview

Digia and the CEP plugin communicate in two directions:


CEP → Digia:    "Here is a campaign payload, render it"
Digia → CEP:    "User viewed / clicked / dismissed"
                "User is on screen X"


These two directions are modelled as two separate interfaces — DigiaCEPPlugin and DigiaCEPDelegate — rather than a single interface. This follows the Interface Segregation Principle: the plugin has responsibilities in both directions, and conflating them into one interface would obscure the data flow and make testing harder.


How Bidirectional Communication Works

Digia.register(plugin)
    │
    └── internally calls: plugin.setup(delegate: DigiaInstance)
                                              │
                                              └── DigiaInstance implements DigiaCEPDelegate
                                                  Plugin holds a WEAK reference to it
                                                  Plugin calls delegate when CEP fires


Digia passes itself as the delegate during setup(). The plugin never imports DigiaInstance — it only knows about DigiaCEPDelegate, which is defined in core. The weak reference on the plugin side prevents a retain cycle: Digia owns the plugin, plugin must not own Digia back.



The Four Types

1. DigiaCEPDelegate

Implemented by Digia core. Plugin calls into this when the CEP fires a campaign.


2. DigiaCEPPlugin

Implemented by each CEP plugin. Digia calls into this.


3. InAppPayload

The translation contract. Plugin maps CEP-specific types into this neutral struct. Digia's rendering engine never imports CleverTap or MoEngage types — it only ever sees InAppPayload.


4. DigiaExperienceEvent

Lifecycle events emitted by rendered experiences. Digia sends these back to the plugin so the plugin can forward them to the CEP's analytics API.



Naming Rationale: InAppPayload not DigiaPayload or TemplatePayload

* Not TemplatePayload: "Template" is CleverTap's vocabulary. MoEngage calls the same concept "Self-Handled In-App." WebEngage uses different terminology. Naming the cross-CEP translation object after one CEP's terminology leaks a mental model that breaks when writing MoEngage or WebEngage plugins.
* Not DigiaPayload: The Digia prefix signals a public developer-facing type. InAppPayload crosses package boundaries (plugin authors construct it) but is not part of the app developer's integration surface. It belongs to the plugin author's surface.
* InAppPayload: CEP-agnostic, maps to Digia's marketing language ("In-App Messages"), reads naturally in context.



Flutter — Complete Interface Definitions

// ─── DigiaCEPDelegate ────────────────────────────────────────────────────────
// Implemented by Digia core internally.
// Plugin holds a WeakReference to this and calls into it when CEP fires.
// Plugin authors never implement this — they only call it.

abstract class DigiaCEPDelegate {
  /// Called when the CEP has evaluated a campaign and it is ready to render.
  /// Digia will route the payload to DigiaHost for display.
  void onCampaignTriggered(InAppPayload payload);

  /// Called when a previously delivered payload is no longer valid.
  /// Digia will dismiss the experience if it is currently visible.
  void onCampaignInvalidated(String payloadId);
}



// ─── DigiaCEPPlugin ──────────────────────────────────────────────────────────
// Implemented by each CEP plugin package.
// Digia core calls into this. Plugin authors implement this.

abstract class DigiaCEPPlugin {
  /// Unique identifier for this plugin.
  /// Used in logs and diagnostics.
  /// Convention: lowercase CEP name — "clevertap", "moengage", "webengage"
  String get identifier;

  /// Called by Digia immediately after Digia.register(plugin).
  ///
  /// Plugin must:
  /// 1. Store the delegate (clear any previously held reference first)
  /// 2. Register for the CEP's in-app callback
  ///    (CleverTap: setTemplatePresenterCallback)
  ///    (MoEngage:  getSelfHandledInAppMessage)
  /// 3. Begin listening for payloads
  void setup(DigiaCEPDelegate delegate);

  /// Called when Digia.setCurrentScreen() is invoked by the app developer.
  ///
  /// Plugin must forward to the CEP's own screen tracking API:
  ///    CleverTap: recordScreenView(name)
  ///    MoEngage:  trackScreen(name)
  ///    WebEngage: webengage.screen(name)
  void forwardScreen(String name);

  /// Called by Digia when a rendered experience emits a lifecycle event.
  ///
  /// Plugin must translate and forward to the CEP's analytics API:
  ///    CleverTap: syncTemplate (viewed/clicked) / dismissTemplate
  ///    MoEngage:  trackSelfHandledImpression / etc.
  ///
  /// payload.cepContext carries CEP-specific identifiers the plugin
  /// wrote during _mapToInAppPayload() — use them here for correlation.
  void notifyEvent(DigiaExperienceEvent event, InAppPayload payload);

  /// Called when Digia is shutting down or a new plugin is being registered
  /// in place of this one.
  ///
  /// Plugin must:
  /// 1. Deregister all CEP callbacks
  /// 2. Clear the delegate reference
  ///
  /// Failure to implement teardown correctly causes dangling callbacks
  /// that fire into a dead delegate after plugin replacement.
  void teardown();
}



// ─── InAppPayload ─────────────────────────────────────────────────────────────
// The translation contract between CEP-specific data and Digia's rendering engine.
//
// Plugin authors are responsible for mapping their CEP's native types
// into this struct inside their _mapToInAppPayload() private method.
// Digia core never imports CleverTap, MoEngage, or WebEngage types.

class InAppPayload {
  /// Unique identifier for this campaign instance.
  /// Used for deduplication, invalidation, and event correlation.
  final String id;

  /// Raw content map from the CEP campaign.
  /// Digia's rendering engine reads this to construct the experience.
  /// Schema is defined by the Digia dashboard campaign format.
  final Map<String, dynamic> content;

  /// CEP-specific metadata, opaque to Digia core.
  ///
  /// Plugin writes whatever identifiers it needs here during mapping.
  /// Plugin reads them back in notifyEvent() to call the correct CEP API.
  ///
  /// Example (CleverTap):  {'templateName': 'onboarding_modal'}
  /// Example (MoEngage):   {'campaignId': 'abc123', 'campaignName': 'summer'}
  final Map<String, dynamic> cepContext;

  const InAppPayload({
    required this.id,
    required this.content,
    required this.cepContext,
  });
}



// ─── DigiaExperienceEvent ────────────────────────────────────────────────────
// Lifecycle events emitted by DigiaHost when a rendered experience
// transitions state. Digia passes these to the plugin via notifyEvent().

sealed class DigiaExperienceEvent {
  const DigiaExperienceEvent();
}

/// The experience became visible to the user.
/// Map to: CleverTap syncTemplate (viewed), MoEngage trackImpression
class ExperienceImpressed extends DigiaExperienceEvent {
  const ExperienceImpressed();
}

/// The user interacted with an actionable element.
/// Map to: CleverTap syncTemplate (clicked), MoEngage trackClick
class ExperienceClicked extends DigiaExperienceEvent {
  /// Identifier of the element clicked, if defined in the campaign artifact.
  /// Null if the entire experience surface was the tap target.
  final String? elementId;
  const ExperienceClicked({this.elementId});
}

/// The experience was dismissed — by the user or programmatically.
/// Map to: CleverTap dismissTemplate, MoEngage trackDismissed
class ExperienceDismissed extends DigiaExperienceEvent {
  const ExperienceDismissed();
}




DigiaHost — Internal Architecture

The Connection Problem

DigiaHost is a widget in the Flutter widget tree. DigiaInstance is a plain Dart object with no BuildContext. They need to talk in both directions:


DigiaInstance  →  DigiaHost:     "Show this experience"
DigiaHost      →  DigiaInstance: "User clicked / dismissed"
DigiaInstance  →  Plugin:        "Forward this event to CEP"



Solution: DigiaOverlayController (ChangeNotifier)

DigiaInstance holds a DigiaOverlayController. DigiaHost is handed the same controller at mount time and listens to it. This mirrors how Flutter's own ScrollController, AnimationController, and TextEditingController work — a controller object coordinates between the imperative world (SDK) and the declarative world (widget tree).


DigiaInstance
    │
    └── _controller: DigiaOverlayController
            │
            ├── DigiaInstance calls: _controller.show(payload)
            │                        _controller.dismiss(payloadId)
            │
            └── DigiaHost listens:  rebuilds when controller state changes
                    │
                    └── _DigiaOverlayWidget renders
                            │
                            └── user interacts
                                    └── controller.onEvent(event, payload)
                                            └── DigiaInstance.handleEvent
                                                    └── plugin.notifyEvent(event, payload)
                                                            └── CleverTap.syncTemplate(...)



DigiaOverlayController

// Internal to digia_core. Never exposed to app developers.

class DigiaOverlayController extends ChangeNotifier {
  InAppPayload? _activePayload;
  InAppPayload? get activePayload => _activePayload;

  // Called by DigiaInstance when a new experience is ready to render.
  void show(InAppPayload payload) {
    _activePayload = payload;
    notifyListeners();
  }

  // Called by DigiaInstance when an experience is invalidated,
  // or by DigiaHost when the user dismisses.
  void dismiss() {
    _activePayload = null;
    notifyListeners();
  }

  // Set by DigiaInstance at init time.
  // DigiaHost calls this when user interacts.
  // DigiaInstance handles it and forwards to the plugin.
  void Function(DigiaExperienceEvent, InAppPayload)? onEvent;
}



DigiaInstance — Relevant Internals

// Internal singleton. Never exposed to app developers.
// The public Digia facade delegates every call to this object.

class DigiaInstance implements DigiaCEPDelegate, WidgetsBindingObserver {
  static final DigiaInstance _instance = DigiaInstance._();
  DigiaInstance._();
  static DigiaInstance get instance => _instance;

  DigiaCEPPlugin? _activePlugin;

  // Shared with DigiaHost. Created once, lives for the app lifetime.
  final DigiaOverlayController _controller = DigiaOverlayController();

  // Exposed as a getter so DigiaHost can subscribe to it at mount time.
  DigiaOverlayController get controller => _controller;

  void initialize(DigiaConfig config) {
    WidgetsBinding.instance.addObserver(this);

    // Wire the event callback — when DigiaHost reports a user
    // interaction, this routes it to the active plugin.
    _controller.onEvent = (event, payload) {
      _activePlugin?.notifyEvent(event, payload);
    };
  }

  void register(DigiaCEPPlugin plugin) {
    _activePlugin?.teardown();   // always tear down before replacing
    _activePlugin = plugin;
    plugin.setup(this);          // passes self as DigiaCEPDelegate
  }

  void setCurrentScreen(String name) {
    _activePlugin?.forwardScreen(name);
  }

  // ─── DigiaCEPDelegate ────────────────────────────────────────────────────
  // These are called by the plugin — never by app code.

  @override
  void onCampaignTriggered(InAppPayload payload) {
    // Routes payload to DigiaHost via the shared controller.
    _controller.show(payload);
  }

  @override
  void onCampaignInvalidated(String payloadId) {
    if (_controller.activePayload?.id == payloadId) {
      _controller.dismiss();
    }
  }

  // ─── WidgetsBindingObserver ───────────────────────────────────────────────

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.detached) {
      _activePlugin?.teardown();
      _activePlugin = null;
    }
  }
}



DigiaHost — Widget Implementation

// Public API. Developer places this once at the app root.
//
// MaterialApp(
//   builder: (context, child) => DigiaHost(child: child!),
// )

class DigiaHost extends StatefulWidget {
  final Widget child;
  const DigiaHost({required this.child, super.key});

  @override
  State<DigiaHost> createState() => _DigiaHostState();
}

class _DigiaHostState extends State<DigiaHost> {
  // Reference to the shared controller. DigiaInstance owns it;
  // DigiaHost only listens to it.
  late final DigiaOverlayController _controller;

  @override
  void initState() {
    super.initState();
    _controller = DigiaInstance.instance.controller;
    _controller.addListener(_onControllerChanged);

    // Notify SDK that DigiaHost is now mounted.
    // SDK can log a warning if the first payload arrives
    // before DigiaHost was ever mounted.
    DigiaInstance.instance.onHostMounted();
  }

  @override
  void dispose() {
    _controller.removeListener(_onControllerChanged);
    DigiaInstance.instance.onHostUnmounted();
    super.dispose();
  }

  void _onControllerChanged() {
    // Rebuild whenever the controller's activePayload changes.
    setState(() {});
  }

  @override
  Widget build(BuildContext context) {
    return Stack(
      children: [
        // App content sits below — always present.
        widget.child,

        // Experience overlay renders above when a payload is active.
        if (_controller.activePayload != null)
          _DigiaOverlayWidget(
            payload: _controller.activePayload!,
            onEvent: (event) {
              // 1. Route event back to DigiaInstance → plugin.
              _controller.onEvent?.call(event, _controller.activePayload!);

              // 2. Dismiss overlay on interaction or explicit dismiss.
              if (event is ExperienceClicked || event is ExperienceDismissed) {
                _controller.dismiss();
              }
            },
          ),
      ],
    );
  }
}



_DigiaOverlayWidget — Rendering + Event Emission

// Internal widget. Renders the campaign artifact and emits
// lifecycle events back to DigiaHost via the onEvent callback.

class _DigiaOverlayWidget extends StatefulWidget {
  final InAppPayload payload;
  final void Function(DigiaExperienceEvent) onEvent;

  const _DigiaOverlayWidget({
    required this.payload,
    required this.onEvent,
  });

  @override
  State<_DigiaOverlayWidget> createState() => _DigiaOverlayWidgetState();
}

class _DigiaOverlayWidgetState extends State<_DigiaOverlayWidget> {
  @override
  void initState() {
    super.initState();
    // Emit ExperienceImpressed on first frame — after layout is complete.
    WidgetsBinding.instance.addPostFrameCallback((_) {
      widget.onEvent(const ExperienceImpressed());
    });
  }

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      // Tapping the scrim (outside the card) dismisses the experience.
      onTap: () => widget.onEvent(const ExperienceDismissed()),
      child: ColoredBox(
        color: Colors.black54,
        child: Center(
          child: _buildContent(),
        ),
      ),
    );
  }

  Widget _buildContent() {
    // In production, this delegates to Digia's server-driven UI
    // rendering engine, which reads payload.content and constructs
    // the correct widget tree from the campaign artifact.
    //
    // Shown here with a simplified static layout for clarity.
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Text(widget.payload.content['title'] as String? ?? ''),

            // Primary CTA — emits ExperienceClicked with elementId
            ElevatedButton(
              onPressed: () => widget.onEvent(
                const ExperienceClicked(elementId: 'primary_cta'),
              ),
              child: Text(widget.payload.content['cta'] as String? ?? 'OK'),
            ),

            // Dismiss — emits ExperienceDismissed
            TextButton(
              onPressed: () => widget.onEvent(const ExperienceDismissed()),
              child: const Text('Dismiss'),
            ),
          ],
        ),
      ),
    );
  }
}




CleverTap Plugin — Implementation Skeleton (Flutter)

// digia_clevertap/flutter/lib/src/clevertap_plugin.dart

class CleverTapPlugin implements DigiaCEPPlugin {
  final CleverTapInstance _cleverTap;
  DigiaCEPDelegate? _delegate;

  CleverTapPlugin({required CleverTapInstance instance})
      : _cleverTap = instance;

  @override
  String get identifier => 'clevertap';

  @override
  void setup(DigiaCEPDelegate delegate) {
    _delegate = delegate;

    _cleverTap.setTemplatePresenterCallback((template) {
      final payload = _mapToInAppPayload(template);
      _delegate?.onCampaignTriggered(payload);
    });
  }

  @override
  void forwardScreen(String name) {
    _cleverTap.recordScreenView(name);
  }

  @override
  void notifyEvent(DigiaExperienceEvent event, InAppPayload payload) {
    final templateName = payload.cepContext['templateName'] as String;

    switch (event) {
      case ExperienceImpressed():
        _cleverTap.syncTemplate(templateName);
      case ExperienceClicked(:final elementId):
        _cleverTap.syncTemplate(templateName);
      case ExperienceDismissed():
        _cleverTap.dismissTemplate(templateName);
    }
  }

  @override
  void teardown() {
    _cleverTap.setTemplatePresenterCallback(null);
    _delegate = null;
  }

  InAppPayload _mapToInAppPayload(CleverTapTemplate template) {
    return InAppPayload(
      id: template.name,
      content: template.arguments,
      cepContext: {'templateName': template.name},
    );
  }
}




Compose (Android) — DigiaOverlayController with StateFlow

ChangeNotifier does not exist in Kotlin. The equivalent is MutableStateFlow<InAppPayload?>. DigiaInstance holds it as a plain Kotlin object — no Composable context required. DigiaHost collects it via collectAsState(), which triggers recomposition automatically when a new payload arrives.

Critical: CEP callbacks are not guaranteed to fire on the main thread. All emissions to _activePayload must be dispatched to the main thread before touching the StateFlow. This is handled inside DigiaInstance, not inside the plugin — the plugin calls delegate.onCampaignTriggered() freely; DigiaInstance owns the threading responsibility.


// ─── DigiaOverlayController ──────────────────────────────────────────────────
// Internal to digia_core. Never exposed to app developers.

class DigiaOverlayController {
    private val _activePayload = MutableStateFlow<InAppPayload?>(null)
    val activePayload: StateFlow<InAppPayload?> = _activePayload.asStateFlow()

    // Called by DigiaInstance when a new experience is ready.
    // Must be called on the main thread.
    fun show(payload: InAppPayload) {
        _activePayload.value = payload
    }

    // Called by DigiaInstance on invalidation,
    // or by DigiaHost when the user dismisses.
    fun dismiss() {
        _activePayload.value = null
    }

    // Set by DigiaInstance at init time.
    // DigiaHost calls this when a user interaction event occurs.
    var onEvent: ((DigiaExperienceEvent, InAppPayload) -> Unit)? = null
}



// ─── DigiaInstance — relevant internals ──────────────────────────────────────

class DigiaInstance : DigiaCEPDelegate, DefaultLifecycleObserver {
    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    val controller = DigiaOverlayController()

    fun initialize(context: Context, config: DigiaConfig) {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)

        controller.onEvent = { event, payload ->
            activePlugin?.notifyEvent(event, payload)
        }
    }

    // ─── DigiaCEPDelegate ─────────────────────────────────────────────────────

    override fun onCampaignTriggered(payload: InAppPayload) {
        // CEP callback may arrive on a background thread.
        // Dispatch to main before touching StateFlow.
        mainScope.launch {
            controller.show(payload)
        }
    }

    override fun onCampaignInvalidated(payloadId: String) {
        mainScope.launch {
            if (controller.activePayload.value?.id == payloadId) {
                controller.dismiss()
            }
        }
    }
}



// ─── DigiaHost — Composable ───────────────────────────────────────────────────
// Public API. Developer wraps their root composable once.
//
// setContent {
//     DigiaHost {
//         MyAppNavHost()
//     }
// }

@Composable
fun DigiaHost(content: @Composable () -> Unit) {
    val controller = DigiaInstance.instance.controller

    // collectAsState() subscribes to the StateFlow and triggers
    // recomposition whenever activePayload changes.
    val payload by controller.activePayload.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        // App content — always present below.
        content()

        // Overlay renders above when a payload is active.
        payload?.let { activePayload ->
            DigiaOverlayWidget(
                payload = activePayload,
                onEvent = { event ->
                    controller.onEvent?.invoke(event, activePayload)

                    if (event is ExperienceClicked || event is ExperienceDismissed) {
                        controller.dismiss()
                    }
                }
            )
        }
    }
}



// ─── DigiaOverlayWidget ───────────────────────────────────────────────────────
// Internal composable. Renders the campaign artifact and emits
// lifecycle events back to DigiaHost via onEvent.

@Composable
internal fun DigiaOverlayWidget(
    payload: InAppPayload,
    onEvent: (DigiaExperienceEvent) -> Unit
) {
    // Emit ExperienceImpressed once on first composition.
    LaunchedEffect(payload.id) {
        onEvent(ExperienceImpressed)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.54f))
            // Tapping the scrim dismisses the experience.
            .clickable { onEvent(ExperienceDismissed) },
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .padding(24.dp)
                // Consume clicks inside the card — don't propagate to scrim.
                .clickable(enabled = false) {}
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // In production, delegates to Digia's server-driven UI
                // rendering engine which reads payload.content.
                Text(text = payload.content["title"] as? String ?: "")

                Spacer(modifier = Modifier.height(16.dp))

                // Primary CTA
                Button(
                    onClick = {
                        onEvent(ExperienceClicked(elementId = "primary_cta"))
                    }
                ) {
                    Text(payload.content["cta"] as? String ?: "OK")
                }

                // Dismiss
                TextButton(onClick = { onEvent(ExperienceDismissed) }) {
                    Text("Dismiss")
                }
            }
        }
    }
}


Note on LaunchedEffect(payload.id): Keying on payload.id rather than Unit ensures the impressed event fires once per unique payload — not once for the lifetime of the composable. If a new campaign replaces an existing one without dismissal, the impressed event fires again correctly for the new payload.



SwiftUI (iOS) — DigiaOverlayController with ObservableObject

ChangeNotifier does not exist in Swift. The equivalent is ObservableObject with a @Published property. SwiftUI automatically re-renders any view that holds an @ObservedObject reference to the controller when @Published properties change.

Critical: Same threading requirement as Compose. CEP callbacks must publish on the main thread. This is enforced with @MainActor on DigiaOverlayController — the compiler then prevents any off-main mutation without an explicit await.


// ─── DigiaOverlayController ──────────────────────────────────────────────────
// Internal to digia_core. Never exposed to app developers.
// @MainActor enforces that all mutations happen on the main thread.

@MainActor
final class DigiaOverlayController: ObservableObject {
    @Published private(set) var activePayload: InAppPayload? = nil

    // Called by DigiaInstance when a new experience is ready.
    func show(_ payload: InAppPayload) {
        activePayload = payload
    }

    // Called by DigiaInstance on invalidation,
    // or by DigiaHost when the user dismisses.
    func dismiss() {
        activePayload = nil
    }

    // Set by DigiaInstance at init time.
    // DigiaHost calls this when a user interaction event occurs.
    var onEvent: ((DigiaExperienceEvent, InAppPayload) -> Void)?
}



// ─── DigiaInstance — relevant internals ──────────────────────────────────────

@MainActor
final class DigiaInstance: DigiaCEPDelegate {
    static let instance = DigiaInstance()
    private init() {}

    let controller = DigiaOverlayController()
    private var activePlugin: (any DigiaCEPPlugin)?

    func initialize(config: DigiaConfig) {
        controller.onEvent = { [weak self] event, payload in
            self?.activePlugin?.notifyEvent(event, for: payload)
        }
        // willTerminateNotification observer setup — see teardown() section
    }

    // ─── DigiaCEPDelegate ─────────────────────────────────────────────────────
    // nonisolated allows the plugin to call these from any thread.
    // Task { @MainActor in } hops to main before touching the controller.

    nonisolated func onCampaignTriggered(_ payload: InAppPayload) {
        Task { @MainActor in
            DigiaInstance.instance.controller.show(payload)
        }
    }

    nonisolated func onCampaignInvalidated(payloadId: String) {
        Task { @MainActor in
            if DigiaInstance.instance.controller.activePayload?.id == payloadId {
                DigiaInstance.instance.controller.dismiss()
            }
        }
    }
}



// ─── DigiaHost — View ────────────────────────────────────────────────────────
// Public API. Developer wraps their root view once.
//
// var body: some Scene {
//     WindowGroup {
//         DigiaHost {
//             ContentView()
//         }
//     }
// }

public struct DigiaHost<Content: View>: View {
    // @ObservedObject subscribes to the controller's @Published properties.
    // SwiftUI re-renders DigiaHost whenever activePayload changes.
    @ObservedObject private var controller: DigiaOverlayController
    private let content: Content

    public init(@ViewBuilder content: () -> Content) {
        self.controller = DigiaInstance.instance.controller
        self.content = content()
    }

    public var body: some View {
        ZStack {
            // App content — always present below.
            content

            // Overlay renders above when a payload is active.
            if let payload = controller.activePayload {
                DigiaOverlayView(
                    payload: payload,
                    onEvent: { event in
                        controller.onEvent?(event, payload)

                        if case .clicked = event { controller.dismiss() }
                        if case .dismissed = event { controller.dismiss() }
                    }
                )
            }
        }
    }
}



// ─── DigiaOverlayView ────────────────────────────────────────────────────────
// Internal view. Renders the campaign artifact and emits
// lifecycle events back to DigiaHost via onEvent.

struct DigiaOverlayView: View {
    let payload: InAppPayload
    let onEvent: (DigiaExperienceEvent) -> Void

    var body: some View {
        ZStack {
            // Scrim — tapping outside the card dismisses.
            Color.black.opacity(0.54)
                .ignoresSafeArea()
                .onTapGesture { onEvent(.dismissed) }

            // Card content
            VStack(spacing: 16) {
                // In production, delegates to Digia's server-driven UI
                // rendering engine which reads payload.content.
                Text(payload.content["title"] as? String ?? "")
                    .font(.headline)

                // Primary CTA
                Button {
                    onEvent(.clicked(elementId: "primary_cta"))
                } label: {
                    Text(payload.content["cta"] as? String ?? "OK")
                }
                .buttonStyle(.borderedProminent)

                // Dismiss
                Button("Dismiss") {
                    onEvent(.dismissed)
                }
                .buttonStyle(.plain)
            }
            .padding(24)
            .background(Color(.systemBackground))
            .cornerRadius(12)
            .padding(32)
            // Consume taps inside the card — don't propagate to scrim.
            .contentShape(Rectangle())
            .onTapGesture {}
        }
        .onAppear {
            // Emit impressed when the view first appears.
            onEvent(.impressed)
        }
    }
}


Note on nonisolated + Task { @MainActor in }: This is the correct Swift concurrency pattern for receiving callbacks from non-isolated (potentially background-threaded) CEP SDKs and safely dispatching to a @MainActor-isolated controller. The alternative — wrapping the delegate in DispatchQueue.main.async — works but opts out of Swift's concurrency safety guarantees.



DigiaOverlayController — Cross-Platform Equivalence

Concern
	Flutter
	Compose (Android)
	SwiftUI (iOS)

Controller type
	ChangeNotifier
	class with StateFlow
	ObservableObject with @Published

Host subscription
	addListener()
	collectAsState()
	@ObservedObject

Triggered rerender
	setState()
	Recomposition
	SwiftUI diff

Main thread enforcement
	Dart is single-threaded
	Dispatchers.Main via mainScope.launch
	@MainActor + Task { @MainActor in }

Impressed event timing
	addPostFrameCallback
	LaunchedEffect(payload.id)
	.onAppear





Full Data Flow — End to End

App startup:
  Digia.initialize(DigiaConfig(apiKey: 'prod_xxxx'))
      └── DigiaInstance wires _controller.onEvent callback

  Digia.register(CleverTapPlugin(instance: ctInstance))
      └── plugin.setup(delegate: DigiaInstance)
              └── CleverTap.setTemplatePresenterCallback(...)

Widget tree mounts:
  MaterialApp(builder: (context, child) => DigiaHost(child: child!))
      └── DigiaHost.initState()
              └── _controller = DigiaInstance.instance.controller
              └── _controller.addListener(_onControllerChanged)

Screen navigation:
  Digia.setCurrentScreen("checkout")
      └── plugin.forwardScreen("checkout")
              └── CleverTap.recordScreenView("checkout")

CEP campaign fires:
  CleverTap evaluates → fires callback
      └── plugin._mapToInAppPayload(template) → InAppPayload
              └── delegate.onCampaignTriggered(payload)          [CEP → Digia]
                      └── DigiaInstance._controller.show(payload)
                              └── notifyListeners()
                                      └── DigiaHost._onControllerChanged()
                                              └── setState() → rebuild
                                                      └── _DigiaOverlayWidget mounts
                                                              └── postFrameCallback fires
                                                                      └── onEvent(ExperienceImpressed)
                                                                              └── controller.onEvent(...)
                                                                                      └── plugin.notifyEvent(Impressed, payload)  [Digia → CEP]
                                                                                              └── CleverTap.syncTemplate(...)

User clicks CTA:
  _DigiaOverlayWidget emits ExperienceClicked(elementId: 'primary_cta')
      └── DigiaHost.onEvent callback
              └── _controller.onEvent(Clicked, payload)        [Digia → CEP]
                      └── plugin.notifyEvent(Clicked, payload)
                              └── CleverTap.syncTemplate(...)
              └── _controller.dismiss()
                      └── notifyListeners()
                              └── DigiaHost rebuilds — overlay removed from Stack

User taps scrim:
  _DigiaOverlayWidget emits ExperienceDismissed
      └── same path → plugin.notifyEvent(Dismissed, payload)   [Digia → CEP]
              └── CleverTap.dismissTemplate(...)
      └── _controller.dismiss() → overlay removed




Where to Call teardown() — All Three Platforms

Flutter

class DigiaInstance with WidgetsBindingObserver {

  void register(DigiaCEPPlugin newPlugin) {
    _activePlugin?.teardown();   // tear down before replacing
    _activePlugin = newPlugin;
    newPlugin.setup(this);
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.detached) {
      _activePlugin?.teardown();
      _activePlugin = null;
    }
  }
}


AppLifecycleState.detached is correct — not paused. paused fires on every background transition. detached fires only on permanent process destruction.



Android (Compose)

class DigiaInstance : DefaultLifecycleObserver {

  fun register(newPlugin: DigiaCEPPlugin) {
    activePlugin?.teardown()     // tear down before replacing
    activePlugin = newPlugin
    newPlugin.setup(this)
  }

  fun initialize(context: Context, config: DigiaConfig) {
    ProcessLifecycleOwner.get().lifecycle.addObserver(this)
  }

  override fun onDestroy(owner: LifecycleOwner) {
    activePlugin?.teardown()
    activePlugin = null
  }
}


ProcessLifecycleOwner.onDestroy maps to app process termination — not individual Activity destruction. Never hook teardown() to Activity lifecycle; rotation and back press would incorrectly tear down the plugin.



iOS (SwiftUI)

final class DigiaInstance {
  private var terminationObserver: NSObjectProtocol?

  func register(_ newPlugin: any DigiaCEPPlugin) {
    activePlugin?.teardown()     // tear down before replacing
    activePlugin = newPlugin
    newPlugin.setup(delegate: self)
  }

  func initialize(config: DigiaConfig) {
    terminationObserver = NotificationCenter.default.addObserver(
      forName: UIApplication.willTerminateNotification,
      object: nil,
      queue: .main
    ) { [weak self] _ in
      self?.activePlugin?.teardown()
      self?.activePlugin = nil
    }
  }

  deinit {
    if let observer = terminationObserver {
      NotificationCenter.default.removeObserver(observer)
    }
  }
}


willTerminateNotification is correct — not didEnterBackgroundNotification. Background fires on every app switch. [weak self] is non-negotiable — without it NotificationCenter retains DigiaInstance indefinitely.



teardown() Summary

Platform
	Trigger 1
	Trigger 2
	Hook

Flutter
	New plugin registered
	App permanently destroyed
	AppLifecycleState.detached

Android
	New plugin registered
	App process killed
	ProcessLifecycleOwner.onDestroy

iOS
	New plugin registered
	App will terminate
	UIApplication.willTerminateNotification



Trigger 1 (new plugin registered) is identical across all platforms — it lives inside DigiaInstance.register() before the new plugin is set up.



Decisions Deferred

* DigiaSlot / inline payload routing — InAppPayload will gain a placementKey field when inline content is scoped in. DigiaOverlayController will gain a parallel Map<String, InAppPayload> keyed by placement key.
* Multi-plugin support — registering two plugins simultaneously during a CEP migration. register() currently replaces; future version may support an array.
* Plugin health / diagnostics API — a DigiaCEPPlugin.healthCheck() method that Digia calls to verify the plugin is correctly connected before showing any experiences.
* Experience queue — currently only one active payload at a time. A queue with priority support is deferred post-MVP.


End of Digia CEP Plugin Interface Design
Generated: February 25, 2026
