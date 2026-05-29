/**
 * DigiaAnchorView
 *
 * Registers a native Android DigiaAnchorView (FrameLayout) in AnchorRegistry by anchorKey.
 * When a SHOW_TOOLTIP or SHOW_SPOTLIGHT campaign fires, the native SDK looks up this view
 * via AnchorRegistry and uses getLocationOnScreen() for accurate pixel-perfect coordinates.
 *
 * Also reports layout into the JS digiaAnchorRegistry so JS-rendered guides (tooltip/spotlight)
 * can position themselves relative to this anchor.
 *
 * Usage:
 *   <DigiaAnchorView anchorKey="pdp_add_to_cart" style={{ alignSelf: 'flex-start' }}>
 *     <TouchableOpacity ...>Add to Cart</TouchableOpacity>
 *   </DigiaAnchorView>
 */

import React, { useCallback, useRef } from 'react';
import { requireNativeComponent, View, type ViewProps, type LayoutChangeEvent } from 'react-native';
import { digiaAnchorRegistry } from './digiaAnchorRegistry';

interface DigiaAnchorViewProps extends ViewProps {
    anchorKey: string;
    /** Corner radius in dp — used to round the spotlight cutout to match the wrapped button. */
    cornerRadius?: number;
}

const NativeDigiaAnchorView = requireNativeComponent<DigiaAnchorViewProps>('DigiaAnchorView');

export const DigiaAnchorView = ({ anchorKey, onLayout, ...rest }: DigiaAnchorViewProps) => {
    const viewRef = useRef<View>(null);

    const handleLayout = useCallback((e: LayoutChangeEvent) => {
        onLayout?.(e);
        // Use measure() for absolute screen coordinates (onLayout gives relative coords)
        viewRef.current?.measure((_x, _y, width, height, pageX, pageY) => {
            if (width > 0 && height > 0) {
                digiaAnchorRegistry.setLayout(anchorKey, { pageX, pageY, width, height });
            }
        });
    }, [anchorKey, onLayout]);

    return (
        <NativeDigiaAnchorView
            ref={viewRef as any}
            anchorKey={anchorKey}
            onLayout={handleLayout}
            {...rest}
        />
    );
};
