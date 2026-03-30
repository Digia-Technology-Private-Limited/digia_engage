/**
 * DigiaEngageModule.m
 *
 * ObjC bridge file that exports Swift implementations to the React Native
 * runtime (both Old Architecture bridge and New Architecture TurboModules).
 *
 * Hybrid strategy (iOS):
 * ──────────────────────
 * • New Architecture: The module is automatically wrapped as a TurboModule
 *   through React Native's interop layer.  install_modules_dependencies()
 *   in the podspec links the New Architecture infrastructure, and RN wraps
 *   the RCTEventEmitter-based Swift class for JSI access.
 *
 * • Old Architecture: The module is resolved via the classic bridge using
 *   the RCT_EXTERN_MODULE / RCT_EXTERN_METHOD macros below.
 *
 * All real logic lives in the Swift files alongside this one:
 *   DigiaModule.swift           — NativeModule (RCTEventEmitter subclass)
 *   RNEventBridgePlugin.swift   — DigiaCEPPlugin bridge
 *   DigiaHostViewManager.swift  — ViewManager for <DigiaHostView>
 *   DigiaSlotViewManager.swift  — ViewManager for <DigiaSlotView>
 */

#import <React/RCTBridgeModule.h>
#import <React/RCTEventEmitter.h>
#import <React/RCTViewManager.h>

#ifdef RCT_NEW_ARCH_ENABLED
#import <DigiaEngageSpec/DigiaEngageSpec.h>
#endif

// ── NativeModule ──────────────────────────────────────────────────────────────

// RCT_EXTERN_MODULE wires the Swift class DigiaModule (which inherits
// RCTEventEmitter) to the React Native bridge under the name "DigiaEngageModule".

@interface RCT_EXTERN_MODULE(DigiaEngageModule, RCTEventEmitter)

RCT_EXTERN_METHOD(initialize:(NSString *)apiKey
                  environment:(NSString *)environment
                  logLevel:(NSString *)logLevel
                  resolve:(RCTPromiseResolveBlock)resolve
                  reject:(RCTPromiseRejectBlock)reject)

RCT_EXTERN_METHOD(registerBridge)

RCT_EXTERN_METHOD(setCurrentScreen:(NSString *)name)

RCT_EXTERN_METHOD(triggerCampaign:(NSString *)id
                  content:(NSDictionary *)content
                  cepContext:(NSDictionary *)cepContext)

RCT_EXTERN_METHOD(invalidateCampaign:(NSString *)campaignId)

@end


// ── ViewManagers ──────────────────────────────────────────────────────────────

@interface RCT_EXTERN_MODULE(DigiaHostView, RCTViewManager)
@end

@interface RCT_EXTERN_MODULE(DigiaSlotView, RCTViewManager)
RCT_EXPORT_VIEW_PROPERTY(placementKey, NSString)
@end
