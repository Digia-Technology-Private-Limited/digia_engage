/**
 * DigiaAnchorView
 *
 * Registers a native Android DigiaAnchorView (FrameLayout) in AnchorRegistry by anchorKey.
 * When a SHOW_TOOLTIP or SHOW_SPOTLIGHT campaign fires, the native SDK looks up this view
 * via AnchorRegistry and uses getLocationOnScreen() for accurate pixel-perfect coordinates.
 *
 * Usage:
 *   <DigiaAnchorView anchorKey="pdp_add_to_cart" style={{ alignSelf: 'flex-start' }}>
 *     <TouchableOpacity ...>Add to Cart</TouchableOpacity>
 *   </DigiaAnchorView>
 */

import { requireNativeComponent, type ViewProps } from 'react-native';

interface DigiaAnchorViewProps extends ViewProps {
    anchorKey: string;
    /** Corner radius in dp — used to round the spotlight cutout to match the wrapped button. */
    cornerRadius?: number;
}

export const DigiaAnchorView = requireNativeComponent<DigiaAnchorViewProps>('DigiaAnchorView');
