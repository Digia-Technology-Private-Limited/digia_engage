/**
 * DigiaAnchorView
 *
 * Android: pure JS measure() via onLayout — works with both Old and New Architecture.
 * iOS: native DigiaAnchorViewManager (RCTViewManager) auto-tracks frame natively.
 */
import React, { useCallback, useEffect, useRef } from 'react';
import { PixelRatio, Platform, View, requireNativeComponent } from 'react-native';
import type { ViewProps } from 'react-native';
import { nativeDigiaModule } from './NativeDigiaEngage';

interface Props extends ViewProps {
    anchorKey: string;
    children?: React.ReactNode;
}

const NativeAnchorView = requireNativeComponent<{ anchorKey: string } & ViewProps>('DigiaAnchorView');

export function DigiaAnchorView({ anchorKey, children, style, ...rest }: Props) {
    if (Platform.OS === 'ios') {
        return (
            <NativeAnchorView anchorKey={anchorKey} style={style} {...rest}>
                {children}
            </NativeAnchorView>
        );
    }

    // Android (and all other platforms): JS-side measure
    return <JsMeasureAnchor anchorKey={anchorKey} style={style} {...rest}>{children}</JsMeasureAnchor>;
}

function JsMeasureAnchor({ anchorKey, children, style, ...rest }: Props) {
    const ref = useRef<View>(null);

    const register = useCallback(() => {
        ref.current?.measure((_x, _y, w, h, px, py) => {
            if (w === 0 && h === 0) return;
            const r = PixelRatio.get();
            nativeDigiaModule.registerAnchor(
                anchorKey,
                Math.round(px * r), Math.round(py * r),
                Math.round(w * r),  Math.round(h * r),
            );
        });
    }, [anchorKey]);

    useEffect(() => () => { nativeDigiaModule.unregisterAnchor(anchorKey); }, [anchorKey]);

    return (
        <View ref={ref} onLayout={register} style={style} {...rest}>
            {children}
        </View>
    );
}
