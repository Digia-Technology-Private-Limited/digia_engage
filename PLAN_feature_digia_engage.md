# Plan: feature/digia-engage — Core SDK (digia_engage)
Branch: `feature/digia-engage`
No commits — author commits manually.

---

## Context

The core SDK currently:
- Initializes via `DigiaUI.initialize()` (old builder platform, fetches component configs)
- Routes campaigns by `payload.content["command"]` + `payload.content["viewId"]`
- Has no `CampaignStore`, no `AnchorRegistry`, no campaign fetch from Digia backend
- Has no tooltip/guide rendering

All of this is replaced. The old `viewId + command` flow is removed entirely.

Scope is **Android only** for this iteration. iOS architecture is designed but not implemented.
The design must not make iOS harder — abstraction boundaries should be platform-neutral.

---

## Part 1 — Android SDK (`android/digia-ui/`)

### 1.1 `DigiaConfig.kt`

Add `baseUrl: String? = null` field. When null, the SDK uses the environment default.
When set, it overrides (for development/testing only — not exposed to production callers).

Add constants:
```kotlin
internal object DigiaEndpoints {
    const val PRODUCTION = "https://api.digia.tech"   // update when prod URL is known
    const val SANDBOX    = "http://10.0.2.2:3000"     // Android emulator → localhost
}
```

The `environment` field on `DigiaConfig` maps to these constants in `CampaignFetcher`.

---

### 1.2 Data model — campaign domain classes

Create a new package `com.digia.engage.internal.model` with the following **immutable
data classes** (no Android/Compose imports — pure data).

**`WidgetConfig.kt`**
```kotlin
data class TooltipWidgetConfig(
    val body: String,
    val actions: List<WidgetAction>,
)

data class WidgetAction(
    val type: ActionType,
    val label: String,
)

enum class ActionType { DISMISS, NEXT, PREV }
```

JSON parsing via `org.json.JSONObject` (already available) — no external serialization
library. Add a `TooltipWidgetConfig.fromJson(JSONObject): TooltipWidgetConfig?` companion
factory. Return null on malformed input.

**`GuideStepModel.kt`**
```kotlin
data class GuideStepModel(
    val id: String,
    val sequenceOrder: Int,
    val anchorKey: String,
    val displayStyle: String,         // "tooltip" | "spotlight"
    val widgetConfig: TooltipWidgetConfig,
    val advanceTrigger: String,       // "tap" | "auto"
    val autoDelayMs: Int?,
)
```

**`GuideConfigModel.kt`**
```kotlin
data class GuideConfigModel(
    val id: String,
    val multiStep: Boolean,
    val steps: List<GuideStepModel>,
)
```

**`CampaignModel.kt`**
```kotlin
data class CampaignModel(
    val id: String,
    val campaignKey: String,
    val campaignType: String,          // "guide" | "nudge" | "inline"
    val guideConfig: GuideConfigModel?,
    // nudgeConfig and inlineConfig are null for this iteration
)
```

Add a `CampaignModel.fromJson(JSONObject): CampaignModel?` factory that parses the
backend response shape, returning null on malformed/missing fields.

---

### 1.3 `CampaignFetcher.kt`

**File:** `com.digia.engage.internal.CampaignFetcher`

A plain Kotlin object (no framework). Makes a single blocking HTTP POST to
`/engage/sdk/getCampaigns` with `X-Api-Key` header. Returns `List<CampaignModel>`.
Throws `IOException` on network failure.

```kotlin
internal class CampaignFetcher(private val config: DigiaConfig) {

    fun fetch(): List<CampaignModel> {
        val baseUrl = config.baseUrl
            ?: if (config.environment == DigiaEnvironment.SANDBOX) DigiaEndpoints.SANDBOX
               else DigiaEndpoints.PRODUCTION

        val url = URL("$baseUrl/engage/sdk/getCampaigns")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("X-Api-Key", config.apiKey)
            setRequestProperty("Content-Type", "application/json")
            connectTimeout = 10_000
            readTimeout = 10_000
            doOutput = true
            outputStream.write("{}".toByteArray(Charsets.UTF_8))
        }

        val code = connection.responseCode
        if (code != 200) throw IOException("getCampaigns failed: HTTP $code")

        val body = connection.inputStream.bufferedReader().readText()
        return parseCampaigns(JSONArray(body))
    }

    private fun parseCampaigns(arr: JSONArray): List<CampaignModel> {
        val result = mutableListOf<CampaignModel>()
        for (i in 0 until arr.length()) {
            CampaignModel.fromJson(arr.getJSONObject(i))?.let(result::add)
        }
        return result
    }
}
```

Use `java.net.HttpURLConnection` — no Retrofit, no OkHttp, no additional dependencies.
This keeps the SDK dependency-free.

---

### 1.4 `CampaignStore.kt`

**File:** `com.digia.engage.internal.CampaignStore`

In-memory store. Thread-safe. Keyed by `campaign_key`.

```kotlin
internal class CampaignStore {
    private val campaigns = ConcurrentHashMap<String, CampaignModel>()

    fun populate(list: List<CampaignModel>) {
        campaigns.clear()
        list.forEach { campaigns[it.campaignKey] = it }
    }

    fun find(campaignKey: String): CampaignModel? = campaigns[campaignKey]

    fun isEmpty(): Boolean = campaigns.isEmpty()
}
```

---

### 1.5 `AnchorRegistry.kt`

**File:** `com.digia.engage.internal.AnchorRegistry`

Thread-safe map from `anchor_key` → screen rect (px).

```kotlin
internal data class ScreenRect(val x: Int, val y: Int, val width: Int, val height: Int)

internal class AnchorRegistry {
    private val anchors = ConcurrentHashMap<String, ScreenRect>()

    fun register(key: String, rect: ScreenRect) { anchors[key] = rect }
    fun unregister(key: String)                  { anchors.remove(key) }
    fun find(key: String): ScreenRect?           = anchors[key]
}
```

---

### 1.6 `GuideOrchestrator.kt`

**File:** `com.digia.engage.internal.GuideOrchestrator`

Manages active guide session state: which campaign, which step index.

```kotlin
internal data class ActiveGuideState(
    val campaign: CampaignModel,
    val stepIndex: Int,
) {
    val currentStep: GuideStepModel
        get() = campaign.guideConfig!!.steps[stepIndex]

    val hasNext: Boolean
        get() = stepIndex < campaign.guideConfig!!.steps.lastIndex
}

internal class GuideOrchestrator {
    private val _state = MutableStateFlow<ActiveGuideState?>(null)
    val state: StateFlow<ActiveGuideState?> = _state.asStateFlow()

    fun start(campaign: CampaignModel) {
        require(campaign.campaignType == "guide" && campaign.guideConfig != null)
        require(campaign.guideConfig.steps.isNotEmpty())
        _state.value = ActiveGuideState(campaign, 0)
    }

    fun advance() {
        val current = _state.value ?: return
        _state.value = if (current.hasNext) current.copy(stepIndex = current.stepIndex + 1)
                       else null
    }

    fun dismiss() { _state.value = null }
}
```

---

### 1.7 `DigiaInstance.kt` — rewrite `initialize()` and `routeCampaign()`

**File:** `com.digia.engage.internal.DigiaInstance`

**Remove entirely:**
- `DigiaUI.initialize(options)` and all `DigiaUIManager` / `DUIFactory` / `BottomSheetManager`
  / `DialogManager` usages
- `_isRenderEngineReady` StateFlow (no longer needed)
- `ensureRenderEngineInitialized()` method
- `isNudgePayload()` helper (old command-based logic)
- All imports from `com.digia.digiaui.init.*` and `com.digia.digiaui.framework.*`

**Add:**
- `private val campaignStore = CampaignStore()`
- `private val anchorRegistry = AnchorRegistry()`
- `private val guideOrchestrator = GuideOrchestrator()`
- Expose `guideOrchestrator.state` as `val guideState = guideOrchestrator.state`

**Rewrite `initialize()`:**
```kotlin
fun initialize(context: Context, config: DigiaConfig) {
    if (!initializationStarted.compareAndSet(false, true)) return
    _sdkState.value = SDKState.INITIALIZING

    scope.launch(Dispatchers.IO) {
        try {
            val fetcher = CampaignFetcher(config)
            val campaigns = fetcher.fetch()           // blocking — stays on IO dispatcher
            campaignStore.populate(campaigns)
            scope.launch(Dispatchers.Main.immediate) {
                initialized.set(true)
                _isUiReady.value = true
                _sdkState.value = SDKState.READY
                registerLifecycleObserver()
                pluginRegistry.runHealthCheck()
                flushPendingPayloadIfAny()
            }.join()
        } catch (t: Throwable) {
            initializationStarted.set(false)
            scope.launch(Dispatchers.Main.immediate) {
                _sdkState.value = SDKState.FAILED
                _isUiReady.value = false
            }
            logWarning("Digia.initialize failed: ${t.message}")
        }
    }
}
```

**Rewrite `routeCampaign()`:**
```kotlin
private fun routeCampaign(payload: InAppPayload) {
    val campaignKey = (payload.content["campaign_key"] as? String)?.trim()
    if (campaignKey.isNullOrBlank()) {
        logWarning("payload dropped: missing campaign_key: ${payload.id}")
        return
    }

    val campaign = campaignStore.find(campaignKey)
    if (campaign == null) {
        logWarning("payload dropped: no campaign found for key '$campaignKey'")
        analyticsClient.recordHealthEvent(
            projectId = "",    // not needed for local health event
            eventType = "campaign_key_mismatch",
            detail    = mapOf("campaign_key" to campaignKey),
        )
        return
    }

    when (campaign.campaignType) {
        "guide" -> routeGuide(campaign)
        else    -> logWarning("campaign type '${campaign.campaignType}' not yet supported")
    }
}

private fun routeGuide(campaign: CampaignModel) {
    guideOrchestrator.start(campaign)
}
```

**Add anchor registration methods (called from RN bridge):**
```kotlin
fun registerAnchor(key: String, x: Int, y: Int, width: Int, height: Int) {
    anchorRegistry.register(key, ScreenRect(x, y, width, height))
}

fun unregisterAnchor(key: String) {
    anchorRegistry.unregister(key)
}

fun findAnchor(key: String): ScreenRect? = anchorRegistry.find(key)
```

---

### 1.8 `DigiaComposableApi.kt` — rewrite `DigiaHost`, add `DigiaAnchorView`

**File:** `com.digia.engage.DigiaComposableApi.kt`

**Remove entirely:** `DigiaSlot` composable (uses DUIFactory — defer inline campaigns).
Keep `DigiaScreen` composable unchanged.

**Rewrite `DigiaHost`:**
```kotlin
@Composable
fun DigiaHost(content: @Composable () -> Unit) {
    val guideState by DigiaInstance.guideState.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        content()
        guideState?.let { state ->
            TooltipOverlay(
                state    = state,
                registry = DigiaInstance.anchorRegistrySnapshot(),
                onAdvance = { DigiaInstance.advanceGuide() },
                onDismiss = { DigiaInstance.dismissGuide() },
            )
        }
    }
}
```

Add helpers on `DigiaInstance`:
```kotlin
fun anchorRegistrySnapshot(): AnchorRegistry = anchorRegistry
fun advanceGuide()  { guideOrchestrator.advance() }
fun dismissGuide()  {
    guideOrchestrator.dismiss()
    displayCoordinator.onOverlayEvent(DigiaExperienceEvent.Dismissed,
        InAppPayload(id = guideOrchestrator.state.value?.campaign?.campaignKey ?: return,
                     content = emptyMap(), cepContext = emptyMap()))
}
```

---

### 1.9 `TooltipOverlay.kt` — new Compose tooltip renderer

**New file:** `com.digia.engage.internal.ui.TooltipOverlay.kt`

A Compose `Popup`-based tooltip that renders at anchor coordinates.

```kotlin
@Composable
internal fun TooltipOverlay(
    state: ActiveGuideState,
    registry: AnchorRegistry,
    onAdvance: () -> Unit,
    onDismiss: () -> Unit,
) {
    val step = state.currentStep
    val anchorRect = registry.find(step.anchorKey) ?: return  // anchor not on screen — render nothing

    val density = LocalDensity.current
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp

    // Convert px coords to dp for Compose layout
    val anchorRectDp = with(density) {
        DpRect(
            left   = anchorRect.x.toDp(),
            top    = anchorRect.y.toDp(),
            right  = (anchorRect.x + anchorRect.width).toDp(),
            bottom = (anchorRect.y + anchorRect.height).toDp(),
        )
    }

    Popup(
        alignment   = Alignment.TopStart,
        offset      = IntOffset(0, 0),
        properties  = PopupProperties(focusable = false, dismissOnClickOutside = false),
    ) {
        TooltipBubble(
            widgetConfig  = step.widgetConfig,
            anchorRect    = anchorRectDp,
            screenWidth   = screenWidth,
            hasNext       = state.hasNext,
            onActionTap   = { action ->
                when (action.type) {
                    ActionType.DISMISS -> onDismiss()
                    ActionType.NEXT    -> if (state.hasNext) onAdvance() else onDismiss()
                    ActionType.PREV    -> { /* future */ }
                }
            },
        )
    }
}

@Composable
private fun TooltipBubble(
    widgetConfig : TooltipWidgetConfig,
    anchorRect   : DpRect,
    screenWidth  : Dp,
    hasNext      : Boolean,
    onActionTap  : (WidgetAction) -> Unit,
) {
    // Positioning logic:
    // Default: place below the anchor (top = anchorRect.bottom + 8.dp)
    // If bottom of tooltip would overflow screen height → place above anchor
    val tooltipWidth = minOf(280.dp, screenWidth - 32.dp)
    val horizontalOffset = clampHorizontal(anchorRect, tooltipWidth, screenWidth)
    val placeBelow = anchorRect.bottom < (LocalConfiguration.current.screenHeightDp * 0.65).dp

    val yOffset = if (placeBelow) anchorRect.bottom + 8.dp
                  else anchorRect.top - 8.dp  // actual height subtracted after measurement

    Box(
        modifier = Modifier
            .offset(x = horizontalOffset, y = yOffset)
            .width(tooltipWidth)
            .shadow(elevation = 8.dp, shape = RoundedCornerShape(12.dp))
            .background(Color.White, shape = RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text  = widgetConfig.body,
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF1A1A2E),
            )
            Row(
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.fillMaxWidth()
            ) {
                widgetConfig.actions.forEach { action ->
                    TextButton(onClick = { onActionTap(action) }) {
                        Text(text = action.label, color = Color(0xFF4945FF))
                    }
                }
            }
        }
    }
}

private fun clampHorizontal(anchorRect: DpRect, tooltipWidth: Dp, screenWidth: Dp): Dp {
    val anchorMidX = (anchorRect.left + anchorRect.right) / 2
    val idealLeft  = anchorMidX - tooltipWidth / 2
    return idealLeft.coerceIn(16.dp, screenWidth - tooltipWidth - 16.dp)
}
```

Import note: uses `androidx.compose.ui.window.Popup` and `PopupProperties`.
No external dependencies.

---

## Part 2 — React Native bridge (`react-native/`)

### 2.1 `DigiaModule.kt` — add `registerAnchor` native method

**File:** `react-native/android/src/main/java/com/digia/engage/rn/DigiaModule.kt`

Add:
```kotlin
@ReactMethod
fun registerAnchor(key: String, x: Int, y: Int, width: Int, height: Int) {
    DigiaInstance.registerAnchor(key, x, y, width, height)
}

@ReactMethod
fun unregisterAnchor(key: String) {
    DigiaInstance.unregisterAnchor(key)
}
```

---

### 2.2 `DigiaAnchorViewManager.kt` — new native view manager

**New file:** `react-native/android/src/main/java/com/digia/engage/rn/DigiaAnchorViewManager.kt`

A `FrameLayout` that:
- Accepts a `anchorKey` prop
- On `onAttachedToWindow` + `OnGlobalLayoutListener`, calls `getLocationOnScreen()`
  to get absolute screen coordinates
- Calls `DigiaInstance.registerAnchor(anchorKey, x, y, width, height)` (px values)
- On `onDetachedFromWindow`, calls `DigiaInstance.unregisterAnchor(anchorKey)`

```kotlin
internal class DigiaAnchorContainerView(context: Context) : FrameLayout(context) {

    var anchorKey: String = ""

    private val globalLayoutListener = ViewTreeObserver.OnGlobalLayoutListener {
        reportPosition()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        viewTreeObserver.addOnGlobalLayoutListener(globalLayoutListener)
        reportPosition()
    }

    override fun onDetachedFromWindow() {
        viewTreeObserver.removeOnGlobalLayoutListener(globalLayoutListener)
        if (anchorKey.isNotBlank()) DigiaInstance.unregisterAnchor(anchorKey)
        super.onDetachedFromWindow()
    }

    private fun reportPosition() {
        val key = anchorKey.takeIf { it.isNotBlank() } ?: return
        val loc = IntArray(2)
        getLocationOnScreen(loc)
        DigiaInstance.registerAnchor(key, loc[0], loc[1], width, height)
    }
}

internal class DigiaAnchorViewManager : SimpleViewManager<DigiaAnchorContainerView>() {

    override fun getName(): String = VIEW_NAME

    override fun createViewInstance(context: ThemedReactContext) =
        DigiaAnchorContainerView(context)

    @ReactProp(name = "anchorKey")
    fun setAnchorKey(view: DigiaAnchorContainerView, key: String?) {
        view.anchorKey = key.orEmpty()
    }

    companion object { const val VIEW_NAME = "DigiaAnchorView" }
}
```

---

### 2.3 `DigiaPackage.kt` — register new view manager

**File:** `react-native/android/src/main/java/com/digia/engage/rn/DigiaPackage.kt`

Add `DigiaAnchorViewManager()` to `createViewManagers()`.

---

### 2.4 `NativeDigiaEngage.ts` — add `registerAnchor` to Spec

**File:** `react-native/src/NativeDigiaEngage.ts`

Add to the `Spec` interface:
```typescript
registerAnchor(key: string, x: number, y: number, width: number, height: number): void;
unregisterAnchor(key: string): void;
```

Add stubs to `nativeDigiaModule` fallback.

---

### 2.5 `DigiaAnchorView.tsx` — new React Native component

**New file:** `react-native/src/DigiaAnchorView.tsx`

```tsx
import React, { useCallback, useRef } from 'react';
import { requireNativeComponent, View, ViewProps } from 'react-native';

interface NativeDigiaAnchorProps extends ViewProps {
    anchorKey: string;
}

const NativeAnchorView = requireNativeComponent<NativeDigiaAnchorProps>('DigiaAnchorView');

interface Props extends ViewProps {
    anchorKey: string;
    children: React.ReactNode;
}

/**
 * Wraps a UI element to register it as an anchor point for Digia Guide experiences.
 * The anchor_key must match the value configured on the GuideStep in the Digia dashboard.
 *
 * Usage:
 * ```tsx
 * <DigiaAnchorView anchorKey="pdp_wishlist_btn">
 *   <WishlistButton />
 * </DigiaAnchorView>
 * ```
 */
export function DigiaAnchorView({ anchorKey, children, style, ...rest }: Props) {
    return (
        <NativeAnchorView anchorKey={anchorKey} style={style} {...rest}>
            {children}
        </NativeAnchorView>
    );
}
```

---

### 2.6 `DigiaHostView.tsx` — simplify (remove old overlay logic)

**File:** `react-native/src/DigiaHostView.tsx`

The Android SDK now manages the overlay entirely via the Compose `DigiaHost` composable
(mounted in `DigiaModule.kt` via `mountDigiaHost()`). The RN `DigiaHostView` is still
needed to trigger the native mount — but its props surface simplifies.

Ensure it still renders its children as a transparent passthrough with `flex: 1`.

---

### 2.7 `index.ts` — export `DigiaAnchorView`

**File:** `react-native/src/index.ts`

Add:
```typescript
export { DigiaAnchorView } from './DigiaAnchorView';
```

---

## Part 3 — iOS (architecture only, no implementation this iteration)

The iOS SDK (`iOS/` + `react-native/ios/`) will follow the same pattern:
- `AnchorRegistry` singleton (Swift)
- `DigiaAnchorUIView : UIView` that overrides `layoutSubviews()`, calls
  `convert(bounds, to: window)` to get screen rect, registers with `AnchorRegistry`
- `DigiaAnchorViewManager : RCTViewManager` exposes `DigiaAnchorView` to RN
- `TooltipOverlay.swift` renders a SwiftUI `ZStack` + `VStack` tooltip at anchor coords

No iOS code changes required in this iteration. Note the design so it isn't surprised later.

---

## Files changed summary

| File | Change |
|------|--------|
| `android/.../DigiaConfig.kt` | Add `baseUrl` override + env endpoint constants |
| `android/.../internal/model/` | **New package** — `CampaignModel`, `GuideConfigModel`, `GuideStepModel`, `TooltipWidgetConfig`, `WidgetAction` |
| `android/.../internal/CampaignFetcher.kt` | **New** — HTTP fetch, no external deps |
| `android/.../internal/CampaignStore.kt` | **New** — `ConcurrentHashMap` campaign cache |
| `android/.../internal/AnchorRegistry.kt` | **New** — `ConcurrentHashMap` anchor position cache |
| `android/.../internal/GuideOrchestrator.kt` | **New** — guide session state machine |
| `android/.../internal/DigiaInstance.kt` | Rewrite `initialize()` and `routeCampaign()`, remove DUIFactory |
| `android/.../internal/ui/TooltipOverlay.kt` | **New** — Compose tooltip renderer |
| `android/.../DigiaComposableApi.kt` | Rewrite `DigiaHost`, remove `DigiaSlot` (deferred) |
| `react-native/android/.../DigiaModule.kt` | Add `registerAnchor`/`unregisterAnchor` |
| `react-native/android/.../DigiaAnchorViewManager.kt` | **New** — native anchor view |
| `react-native/android/.../DigiaPackage.kt` | Register `DigiaAnchorViewManager` |
| `react-native/src/NativeDigiaEngage.ts` | Add anchor methods to Spec |
| `react-native/src/DigiaAnchorView.tsx` | **New** — RN component |
| `react-native/src/DigiaHostView.tsx` | Minor simplification |
| `react-native/src/index.ts` | Export `DigiaAnchorView` |

---

## Dependency changes

No new Android dependencies added. Uses only:
- `java.net.HttpURLConnection` (std lib) for campaign fetch
- `org.json` (already available via Android SDK) for JSON parsing
- `androidx.compose.*` (already a dependency) for tooltip rendering
- `kotlinx.coroutines.*` (already a dependency) for async init

---

## `DiagnosticReport` / `healthCheck()` updates

After the init rewrite, `DigiaInstance`'s `runHealthCheck()` path should verify:
- `campaignStore.isEmpty()` → log warning if no campaigns were fetched
- `pluginRegistry` health check unchanged

---

## Key invariants to preserve

1. `Digia.initialize()` is idempotent — second call returns immediately (AtomicBoolean guard).
2. Campaign triggered before SDK ready → queued in `pendingPayload`, flushed on ready.
3. `routeCampaign()` always runs on `Dispatchers.Main.immediate`.
4. `AnchorRegistry` and `CampaignStore` are never null after construction.
5. `TooltipOverlay` renders nothing if anchor key is not in registry — no crash.
