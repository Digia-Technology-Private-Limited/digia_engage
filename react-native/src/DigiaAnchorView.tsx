import React, { useCallback, useEffect, useRef } from 'react';
import { Platform, View, requireNativeComponent } from 'react-native';
import type { ViewProps } from 'react-native';
import { CoachmarkAnchor } from '@edwardloopez/react-native-coachmark';
import { Digia } from './Digia';
import { nativeDigiaModule } from './NativeDigiaEngage';
import { digiaAnchorRegistry } from './digiaAnchorRegistry';

interface Props extends ViewProps {
    anchorKey: string;
    children?: React.ReactNode;
}

const NativeAnchorView = Platform.OS === 'ios' && typeof requireNativeComponent === 'function'
    ? requireNativeComponent<{ anchorKey: string } & ViewProps>('DigiaAnchorView')
    : View;

export function DigiaAnchorView({ anchorKey, children, style, ...rest }: Props) {
    useEffect(() => {
        Digia.registerAnchor(anchorKey);
        return () => {
            Digia.unregisterAnchor(anchorKey);
            digiaAnchorRegistry.remove(anchorKey);
        };
    }, [anchorKey]);

    // padding=0, radius=0 — TourStep controls cutout size at tour-start time
    return (
        <CoachmarkAnchor id={anchorKey} shape="rect" padding={0} radius={0} style={style} {...rest}>
            {Platform.OS === 'ios' ? (
                <NativeAnchorView anchorKey={anchorKey}>
                    {children}
                </NativeAnchorView>
            ) : (
                <JsMeasureAnchor anchorKey={anchorKey}>
                    {children}
                </JsMeasureAnchor>
            )}
        </CoachmarkAnchor>
    );
}

function JsMeasureAnchor({ anchorKey, children, style, ...rest }: Props) {
    const ref = useRef<View>(null);

    const measure = useCallback(() => {
        ref.current?.measure((_x, _y, width, height, pageX, pageY) => {
            if (width === 0 && height === 0) return;
            nativeDigiaModule.registerAnchor(anchorKey, Math.round(pageX), Math.round(pageY), Math.round(width), Math.round(height));
            digiaAnchorRegistry.setLayout(anchorKey, { pageX, pageY, width, height });
        });
    }, [anchorKey]);

    useEffect(() => () => { nativeDigiaModule.unregisterAnchor(anchorKey); }, [anchorKey]);

    return (
        <View ref={ref} onLayout={measure} style={style} {...rest}>
            {children}
        </View>
    );
}
