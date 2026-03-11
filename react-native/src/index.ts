/**
 * @digia/engage-react-native
 *
 * React Native bridge for the Digia Engage SDK.
 *
 * The SDK surfaces Digia Compose UI inside React Native via:
 *   • `Digia`          – SDK lifecycle (initialize, setCurrentScreen, openNavigation)
 *   • `DigiaHostView`  – Transparent native overlay view that hosts Compose dialogs/
 *                        bottom-sheets managed by the Digia CEP engine.
 */

export { Digia } from './Digia';
export { DigiaHostView } from './DigiaHostView';
export { DigiaSlotView } from './DigiaSlotView';
export type { DigiaConfig, DigiaNavigationOptions, DigiaPlugin } from './types';
