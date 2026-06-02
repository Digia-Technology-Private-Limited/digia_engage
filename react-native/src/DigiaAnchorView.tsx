import React, { forwardRef, useCallback, useEffect, useImperativeHandle, useRef } from 'react';
import { View } from 'react-native';
import type { ViewProps } from 'react-native';
import { Digia } from './Digia';
import { nativeDigiaModule } from './NativeDigiaEngage';
import { digiaAnchorRegistry } from './digiaAnchorRegistry';

export interface DigiaAnchorViewRef {
    /**
     * Tell the anchor that the entrance/layout animation wrapping it has
     * finished. The anchor then captures its final, resting position and
     * publishes it to the registry so tooltips can attach to it.
     *
     * Attaching a ref is itself the signal that this anchor is animated: until
     * the first `animationCompleted()` call the anchor publishes NOTHING, so a
     * guide that triggers mid-animation simply finds no layout and stays
     * invisible instead of painting at a transient (wrong) position.
     *
     * If you never attach a ref, none of this applies — the anchor measures on
     * layout exactly as a static anchor always has.
     */
    animationCompleted: () => void;
}

interface Props extends ViewProps {
    anchorKey: string;
    children?: React.ReactNode;
}

export const DigiaAnchorView = forwardRef<DigiaAnchorViewRef, Props>(
    function DigiaAnchorView({ anchorKey, children, style, ...rest }, ref) {
        // The native View we read the on-screen position from.
        const viewRef = useRef<View>(null);

        // A ref attached by the consumer means "this anchor is animated, wait
        // for me to call animationCompleted()". No ref means a plain static
        // anchor that should measure as soon as it lays out.
        const isAnimated = ref != null;

        // The gate that decides whether measure() is allowed to publish.
        // Static anchors start open (true) and behave exactly as before.
        // Animated anchors start closed (false) and open on animationCompleted().
        const canMeasure = useRef(!isAnimated);

        // Read the View's current position and publish it to the registry.
        // No-op while the gate is closed (animation still in flight).
        const measure = useCallback(() => {
            if (!canMeasure.current) return;
            viewRef.current?.measure((_x, _y, width, height, pageX, pageY) => {
                if (width === 0 && height === 0) return;
                nativeDigiaModule.registerAnchor(anchorKey, Math.round(pageX), Math.round(pageY), Math.round(width), Math.round(height));
                digiaAnchorRegistry.setLayout(anchorKey, { pageX, pageY, width, height });
            });
        }, [anchorKey]);

        // What the consumer calls from their animation's completion callback.
        // Open the gate, then measure on the frame after the animation's final
        // commit so the position we capture is the resting one.
        const animationCompleted = useCallback(() => {
            canMeasure.current = true;
            requestAnimationFrame(() => requestAnimationFrame(measure));
        }, [measure]);

        // Expose animationCompleted() on the ref. When no ref is attached this
        // is a no-op, so static anchors carry zero extra behaviour.
        useImperativeHandle(ref, () => ({ animationCompleted }), [animationCompleted]);

        // Register the anchor (and its measure callback) with the SDK on mount,
        // and tear everything down on unmount.
        useEffect(() => {
            Digia.registerAnchor(anchorKey);
            digiaAnchorRegistry.registerMeasure(anchorKey, measure);
            return () => {
                Digia.unregisterAnchor(anchorKey);
                digiaAnchorRegistry.unregisterMeasure(anchorKey, measure);
                nativeDigiaModule.unregisterAnchor(anchorKey);
                digiaAnchorRegistry.remove(anchorKey);
            };
        }, [anchorKey, measure]);

        return (
            <View ref={viewRef} onLayout={measure} style={style} {...rest}>
                {children}
            </View>
        );
    },
);
