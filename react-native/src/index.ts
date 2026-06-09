/**
 * @digia/engage-react-native
 *
 * React Native bridge for the Digia Engage SDK.
 *
 * The SDK surfaces Digia Compose UI inside React Native via:
 *   • `Digia`          – SDK lifecycle (initialize, setCurrentScreen)
 *   • `DigiaHostView`  – Transparent native overlay view that hosts Compose dialogs/
 *                        bottom-sheets managed by the Digia CEP engine.
 */

export { Digia } from './Digia';
export { DigiaHostView } from './DigiaHostView';
export { DigiaHost } from './DigiaProvider';
export { DigiaSlotView } from './DigiaSlotView';
export { DigiaAnchorView } from './DigiaAnchorView';
export type { DigiaAnchorViewRef } from './DigiaAnchorView';
export type { ActionContext, ActionResult, CampaignType, DigiaAction, DigiaConfig, DigiaDelegate, DigiaExperienceEvent, DigiaPlugin, InAppBrowserAdapter, InAppPayload, OnAction } from './types';
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
