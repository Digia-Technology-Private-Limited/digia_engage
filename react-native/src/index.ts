/**
 * @digia/engage-react-native
 *
 * React Native bridge for the Digia Engage SDK.
 *
 * The SDK surfaces Digia Compose UI inside React Native via:
 *   • `Digia`      – SDK lifecycle (initialize, setCurrentScreen)
 *   • `DigiaHost`  – Place once at the app root. Hosts JS guide/tooltip overlays
 *                    and the native Compose overlay for dialogs/bottom-sheets.
 *                    Use as <DigiaHost /> standalone or <DigiaHost>{children}</DigiaHost>.
 */

export { Digia } from './Digia';
export { DigiaHost } from './DigiaProvider';
export { DigiaSlotView } from './DigiaSlotView';
export { DigiaAnchorView } from './DigiaAnchorView';
export type { DigiaAnchorViewRef } from './DigiaAnchorView';
export type { ActionContext, ActionResult, CEPTriggerPayload, CampaignType, DigiaAction, DigiaConfig, DigiaDelegate, DigiaExperienceEvent, DigiaPlugin, InAppBrowserAdapter, OnAction } from './types';
export { defaultInAppBrowser } from './defaultInAppBrowser';
export { DigiaHealthReporter, HealthEventType, digiaHealthReporter } from './DigiaHealthReporter';
export type {
    Action,
    CarouselConfig,
    SpotlightConfig,
    SpotlightStep,
    SurveyTemplateConfig,
    TemplateConfig,
    TooltipConfig,
    TooltipStep,
} from './templateTypes';
