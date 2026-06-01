import React, { useCallback, useEffect, useRef } from 'react';
import { View } from 'react-native';
import type { ViewProps } from 'react-native';
import { Digia } from './Digia';
import { nativeDigiaModule } from './NativeDigiaEngage';
import { digiaAnchorRegistry } from './digiaAnchorRegistry';

interface Props extends ViewProps {
    anchorKey: string;
    children?: React.ReactNode;
}

export function DigiaAnchorView({ anchorKey, children, style, ...rest }: Props) {
    useEffect(() => {
        Digia.registerAnchor(anchorKey);
        return () => {
            Digia.unregisterAnchor(anchorKey);
            digiaAnchorRegistry.remove(anchorKey);
        };
    }, [anchorKey]);

    return (
        <JsMeasureAnchor anchorKey={anchorKey} style={style} {...rest}>
            {children}
        </JsMeasureAnchor>
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

    useEffect(() => {
        digiaAnchorRegistry.registerMeasure(anchorKey, measure);
        return () => {
            digiaAnchorRegistry.unregisterMeasure(anchorKey, measure);
            nativeDigiaModule.unregisterAnchor(anchorKey);
        };
    }, [anchorKey, measure]);

    return (
        <View ref={ref} onLayout={measure} style={style} {...rest}>
            {children}
        </View>
    );
}
