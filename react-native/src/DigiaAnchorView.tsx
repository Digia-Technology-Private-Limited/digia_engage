import React, { forwardRef, useCallback, useEffect, useImperativeHandle, useRef } from 'react';
import { View } from 'react-native';
import type { ViewProps } from 'react-native';
import { Digia } from './Digia';
import { nativeDigiaModule } from './NativeDigiaEngage';
import { digiaAnchorRegistry } from './digiaAnchorRegistry';

export interface DigiaAnchorViewRef {
    /**
     * Force the anchor to (re)measure its current on-screen position and publish
     * it into the registry.
     *
     * Call this once any entrance/layout animation wrapping the anchor has
     * settled (e.g. from a Reanimated `entering` `.withCallback`). The SDK stays
     * animation-library-agnostic: it never inspects the animation, it just
     * captures the position when you tell it the anchor has come to rest.
     *
     * When `deferMeasurement` is set, the anchor publishes NOTHING to the
     * registry until the first `remeasure()` call. A guide that triggers while
     * the animation is still mid-flight therefore finds no layout and stays
     * fully invisible — making a stale, mis-anchored paint impossible.
     */
    remeasure: () => void;
}

interface Props extends ViewProps {
    anchorKey: string;
    children?: React.ReactNode;
    /**
     * Withhold this anchor's layout from the registry until `remeasure()` is
     * called via ref. Use when the anchor (or an ancestor) plays an entrance
     * animation, so the tooltip cannot paint at a transient position. Defaults
     * to `false`, preserving the measure-on-layout behaviour for normal anchors.
     */
    deferMeasurement?: boolean;
}

export const DigiaAnchorView = forwardRef<DigiaAnchorViewRef, Props>(
    function DigiaAnchorView({ anchorKey, children, style, deferMeasurement, ...rest }, ref) {
        useEffect(() => {
            Digia.registerAnchor(anchorKey);
            return () => {
                Digia.unregisterAnchor(anchorKey);
                digiaAnchorRegistry.remove(anchorKey);
            };
        }, [anchorKey]);

        return (
            <JsMeasureAnchor
                anchorKey={anchorKey}
                deferMeasurement={deferMeasurement}
                forwardedRef={ref}
                style={style}
                {...rest}
            >
                {children}
            </JsMeasureAnchor>
        );
    },
);

interface MeasureProps extends Props {
    forwardedRef: React.ForwardedRef<DigiaAnchorViewRef>;
}

function JsMeasureAnchor({ anchorKey, children, style, deferMeasurement, forwardedRef, ...rest }: MeasureProps) {
    const ref = useRef<View>(null);
    // While deferred, the gate stays closed until the first remeasure() call, so
    // measure() — including the registry's mount-time remeasure from a triggered
    // guide — publishes nothing and the tooltip cannot anchor to a transient spot.
    const released = useRef(!deferMeasurement);

    const measure = useCallback(() => {
        if (!released.current) return;
        ref.current?.measure((_x, _y, width, height, pageX, pageY) => {
            if (width === 0 && height === 0) return;
            nativeDigiaModule.registerAnchor(anchorKey, Math.round(pageX), Math.round(pageY), Math.round(width), Math.round(height));
            digiaAnchorRegistry.setLayout(anchorKey, { pageX, pageY, width, height });
        });
    }, [anchorKey]);

    const remeasure = useCallback(() => {
        // Lift the gate (first call) and measure on the frame after the
        // animation's final commit, so the captured position is the resting one.
        released.current = true;
        requestAnimationFrame(() => requestAnimationFrame(measure));
    }, [measure]);

    useImperativeHandle(forwardedRef, () => ({ remeasure }), [remeasure]);

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
