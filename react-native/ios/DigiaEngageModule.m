/**
 * DigiaEngageModule (iOS stub)
 *
 * iOS support is not yet implemented.  This stub registers the module so that
 * the JS layer does not crash when imported on iOS.  All methods are no-ops.
 */
#import <React/RCTBridgeModule.h>
#import <React/RCTViewManager.h>

// ── NativeModule stub ─────────────────────────────────────────────────────────

@interface DigiaEngageModule : NSObject <RCTBridgeModule>
@end

@implementation DigiaEngageModule

RCT_EXPORT_MODULE(DigiaEngageModule)

RCT_EXPORT_METHOD(initialize:(NSString *)apiKey
                  environment:(NSString *)environment
                  logLevel:(NSString *)logLevel
                  resolve:(RCTPromiseResolveBlock)resolve
                  reject:(RCTPromiseRejectBlock)reject)
{
    // iOS not yet implemented – resolve immediately so JS doesn't hang.
    resolve(nil);
}

RCT_EXPORT_METHOD(setCurrentScreen:(NSString *)name)
{
    // no-op
}

RCT_EXPORT_METHOD(openNavigation:(nullable NSString *)startPageId
                  pageArgs:(NSDictionary *)pageArgs)
{
    // no-op
}

RCT_EXPORT_METHOD(triggerCampaign:(NSString *)campaignId
                  content:(NSDictionary *)content
                  cepContext:(NSDictionary *)cepContext)
{
    // no-op — iOS Digia SDK not yet available
}

RCT_EXPORT_METHOD(invalidateCampaign:(NSString *)campaignId)
{
    // no-op
}

@end


// ── ViewManager stub ──────────────────────────────────────────────────────────

@interface DigiaHostViewManager : RCTViewManager
@end

@implementation DigiaHostViewManager

RCT_EXPORT_MODULE(DigiaHostView)

- (UIView *)view {
    // Return a transparent placeholder view
    UIView *v = [[UIView alloc] init];
    v.userInteractionEnabled = NO;
    return v;
}

@end
