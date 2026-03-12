/**
 * DigiaSlotView
 *
 * A React Native view that renders inline campaign content (banners, cards,
 * widgets) at a specific placement position inside your screen layout.
 *
 * On Android this mounts a Jetpack Compose `DigiaSlot` composable that
 * observes the slot payload for the given placement key and renders the
 * matching campaign component. On iOS it is a transparent no-op.
 *
 * ─── Usage ───────────────────────────────────────────────────────────────────
 * ```tsx
 * import { DigiaSlotView } from '@digia/engage-react-native';
 *
 * // Fixed height (you control the space)
 * <DigiaSlotView
 *   placementKey="hero_banner"
 *   style={{ width: '100%', height: 200 }}
 * />
 *
 * // Inside a scroll view
 * <ScrollView>
 *   <ProductList />
 *   <DigiaSlotView
 *     placementKey="pdp_mid_banner"
 *     style={{ width: '100%', height: 120 }}
 *   />
 *   <RelatedProducts />
 * </ScrollView>
 * ```
 *
 * The `placementKey` must match the key the marketer selects when creating
 * inline content on the Digia dashboard. The view collapses to nothing when
 * no campaign is active for that key.
 *
 * ─── Sizing ──────────────────────────────────────────────────────────────────
 * React Native's layout system controls the dimensions of this view.
 * Provide an explicit `height` (or flex) via the `style` prop so that the
 * native Compose layer has space to render. When no campaign is active the
 * Compose `DigiaSlot` renders nothing inside that space.
 * ─────────────────────────────────────────────────────────────────────────────
 */

import React from 'react';
import {
    Platform,
    UIManager,
    View,
    requireNativeComponent,
    type StyleProp,
    type ViewStyle,
} from 'react-native';

interface DigiaSlotViewProps {
    /** Placement key that links this view to a Digia dashboard slot. */
    placementKey: string;
    style?: StyleProp<ViewStyle>;
}

// ── Android ──────────────────────────────────────────────────────────────────
// The native Kotlin ViewManager (DigiaSlotViewManager) registers itself under
// 'DigiaSlotView'. It creates an AbstractComposeView that hosts the
// DigiaSlot composable and forwards the `placementKey` prop via @ReactProp.
//
// Guard with UIManager.hasViewManagerConfig so that in Expo Go or before the
// first `npx expo run:android` the component degrades to a transparent View
// rather than throwing "View config not found" at render time (Fabric/New Arch
// resolves the native config lazily and throws on first render, not on
// requireNativeComponent).
const _slotViewAvailable =
    Platform.OS === 'android' &&
    !!UIManager.hasViewManagerConfig?.('DigiaSlotView');

const NativeDigiaSlotView = _slotViewAvailable
    ? requireNativeComponent<DigiaSlotViewProps>('DigiaSlotView')
    : null;

// ── DigiaSlotView ─────────────────────────────────────────────────────────────

export function DigiaSlotView({ placementKey, style }: DigiaSlotViewProps) {
    if (Platform.OS === 'android' && NativeDigiaSlotView) {
        return (
            <NativeDigiaSlotView
                placementKey={placementKey}
                style={style}
            />
        );
    }

    // iOS / other platforms: transparent placeholder
    return <View style={style} />;
}
