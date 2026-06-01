import React, { useCallback, useEffect, useRef, useState } from 'react';
import {
    Animated,
    Dimensions,
    Modal,
    Pressable,
    StyleSheet,
    Text,
    View,
    useWindowDimensions,
} from 'react-native';
import { computePosition, flip, offset, shift } from '@floating-ui/core';
import Svg, { Path } from 'react-native-svg';
import { Digia } from './Digia';
import { digiaGuideController, type DigiaGuideRequest } from './DigiaGuideController';
import { digiaAnchorRegistry, type AnchorLayout } from './digiaAnchorRegistry';
import { digiaActionHandler, type ActionCallbacks } from './actionHandler';
import type { DismissReason } from './types';
import type { Action, SpotlightConfig, SpotlightStep, TooltipConfig, TooltipStep } from './templateTypes';
import { interpolateVariables, type VariableMap } from './interpolate';

// ─── @floating-ui/core platform adapter ──────────────────────────────────────

const rnCorePlatform = {
    getElementRects: ({ reference, floating }: any) => {
        const r = typeof reference.getBoundingClientRect === 'function'
            ? reference.getBoundingClientRect()
            : { x: 0, y: 0, width: 0, height: 0 };
        return {
            reference: { x: r.x ?? r.left ?? 0, y: r.y ?? r.top ?? 0, width: r.width, height: r.height },
            floating: { x: 0, y: 0, width: floating.w ?? 0, height: floating.h ?? 0 },
        };
    },
    getDimensions: (element: any) => ({ width: element.w ?? element.width ?? 0, height: element.h ?? element.height ?? 0 }),
    getClippingRect: () => {
        const { width, height } = Dimensions.get('window');
        return { x: 0, y: 0, width, height, top: 0, left: 0, bottom: height, right: width };
    },
    isElement: () => false,
};

function makeVirtualRef(layout: AnchorLayout, padding = 0) {
    return {
        getBoundingClientRect: () => ({
            x: layout.pageX - padding, y: layout.pageY - padding,
            width: layout.width + padding * 2, height: layout.height + padding * 2,
            top: layout.pageY - padding, left: layout.pageX - padding,
            bottom: layout.pageY + layout.height + padding,
            right: layout.pageX + layout.width + padding,
        }),
    };
}

type FloatPos = { x: number; y: number };

// ─── Arrow component ──────────────────────────────────────────────────────────
//
// arrowOffset: pixel distance from the start of the side (left for top/bottom,
// top for left/right) to the arrow tip center.  When provided the arrow points
// at the anchor; when omitted it falls back to centering.

function GuideArrow({
    placement,
    color,
    borderColor,
    size,
    arrowOffset,
}: {
    placement: string;
    color: string;
    borderColor: string;
    size: number;
    arrowOffset?: number;
}) {
    const s1 = size + 1;

    // Horizontal: used for top / bottom placements (left offset within bubble width)
    const hWrap = (edge: 'top' | 'bottom') =>
        arrowOffset !== undefined
            ? { [edge]: -s1, left: arrowOffset - s1, width: s1 * 2 }
            : { [edge]: -s1, left: 0, right: 0 };

    // Vertical: used for left / right placements (top offset within bubble height)
    const vWrap = (edge: 'left' | 'right') =>
        arrowOffset !== undefined
            ? { [edge]: -s1, top: arrowOffset - s1, height: s1 * 2 }
            : { [edge]: -s1, top: 0, bottom: 0 };

    if (placement === 'bottom' || placement === 'below') {
        // bubble below anchor → arrow at TOP pointing ▲ up
        return (
            <View style={[arrowS.wrap, hWrap('top')]}>
                <View style={{ position: 'relative', width: s1 * 2, height: s1, alignItems: 'center' }}>
                    <View style={{ position: 'absolute', top: 0, width: 0, height: 0, borderStyle: 'solid', borderLeftWidth: s1, borderRightWidth: s1, borderBottomWidth: s1, borderTopWidth: 0, borderLeftColor: 'transparent', borderRightColor: 'transparent', borderBottomColor: borderColor }} />
                    <View style={{ position: 'absolute', top: 1, width: 0, height: 0, borderStyle: 'solid', borderLeftWidth: size, borderRightWidth: size, borderBottomWidth: size, borderTopWidth: 0, borderLeftColor: 'transparent', borderRightColor: 'transparent', borderBottomColor: color }} />
                </View>
            </View>
        );
    }
    if (placement === 'top' || placement === 'above') {
        // bubble above anchor → arrow at BOTTOM pointing ▼ down
        return (
            <View style={[arrowS.wrap, hWrap('bottom')]}>
                <View style={{ position: 'relative', width: s1 * 2, height: s1, alignItems: 'center' }}>
                    <View style={{ position: 'absolute', top: 0, width: 0, height: 0, borderStyle: 'solid', borderLeftWidth: s1, borderRightWidth: s1, borderTopWidth: s1, borderBottomWidth: 0, borderLeftColor: 'transparent', borderRightColor: 'transparent', borderTopColor: borderColor }} />
                    <View style={{ position: 'absolute', top: 0, width: 0, height: 0, borderStyle: 'solid', borderLeftWidth: size, borderRightWidth: size, borderTopWidth: size, borderBottomWidth: 0, borderLeftColor: 'transparent', borderRightColor: 'transparent', borderTopColor: color }} />
                </View>
            </View>
        );
    }
    if (placement === 'right') {
        // bubble right of anchor → arrow at LEFT pointing ◀ left
        return (
            <View style={[arrowS.wrap, vWrap('left')]}>
                <View style={{ position: 'relative', width: s1, height: s1 * 2, justifyContent: 'center' }}>
                    <View style={{ position: 'absolute', left: 0, width: 0, height: 0, borderStyle: 'solid', borderTopWidth: s1, borderBottomWidth: s1, borderRightWidth: s1, borderLeftWidth: 0, borderTopColor: 'transparent', borderBottomColor: 'transparent', borderRightColor: borderColor }} />
                    <View style={{ position: 'absolute', left: 1, width: 0, height: 0, borderStyle: 'solid', borderTopWidth: size, borderBottomWidth: size, borderRightWidth: size, borderLeftWidth: 0, borderTopColor: 'transparent', borderBottomColor: 'transparent', borderRightColor: color }} />
                </View>
            </View>
        );
    }
    if (placement === 'left') {
        // bubble left of anchor → arrow at RIGHT pointing ▶ right
        return (
            <View style={[arrowS.wrap, vWrap('right')]}>
                <View style={{ position: 'relative', width: s1, height: s1 * 2, justifyContent: 'center' }}>
                    <View style={{ position: 'absolute', right: 0, width: 0, height: 0, borderStyle: 'solid', borderTopWidth: s1, borderBottomWidth: s1, borderLeftWidth: s1, borderRightWidth: 0, borderTopColor: 'transparent', borderBottomColor: 'transparent', borderLeftColor: borderColor }} />
                    <View style={{ position: 'absolute', right: 1, width: 0, height: 0, borderStyle: 'solid', borderTopWidth: size, borderBottomWidth: size, borderLeftWidth: size, borderRightWidth: 0, borderTopColor: 'transparent', borderBottomColor: 'transparent', borderLeftColor: color }} />
                </View>
            </View>
        );
    }
    return null;
}

const arrowS = StyleSheet.create({
    wrap: { position: 'absolute', alignItems: 'center', justifyContent: 'center' },
});

// ─── Arrow offset helper ──────────────────────────────────────────────────────
// Returns the pixel distance from the start of the bubble side (left for
// top/bottom, top for left/right) to where the arrow tip should point,
// clamped so the arrow stays inside the bubble's rounded corners.

function calcArrowOffset(
    placement: string,
    layout: AnchorLayout,
    floatPos: FloatPos,
    bubbleW: number,
    bubbleH: number,
    cornerRadius: number,
    arrowSize: number,
): number {
    const minPad = cornerRadius + arrowSize + 2;
    const isHoriz = placement === 'top' || placement === 'bottom' || placement === 'above' || placement === 'below';
    if (isHoriz) {
        const anchorCenterX = layout.pageX + layout.width / 2;
        const raw = anchorCenterX - floatPos.x;
        return Math.max(minPad, Math.min(bubbleW - minPad, raw));
    }
    const anchorCenterY = layout.pageY + layout.height / 2;
    const raw = anchorCenterY - floatPos.y;
    return Math.max(minPad, Math.min(bubbleH - minPad, raw));
}

// ─── Shared action helpers ────────────────────────────────────────────────────

function ActionButton({
    action,
    variables,
    btnPrimaryBg,
    btnPrimaryText,
    btnGhostText,
    onPress,
}: {
    action: Action;
    variables?: VariableMap;
    btnPrimaryBg: string;
    btnPrimaryText: string;
    btnGhostText: string;
    onPress: () => void;
}) {
    const isPrimary = action.style === 'primary';
    const fontFamily = Digia.fontFamily;
    return (
        <Pressable
            onPress={onPress}
            style={[s.button, isPrimary && { backgroundColor: btnPrimaryBg }]}
        >
            <Text style={{ color: isPrimary ? btnPrimaryText : btnGhostText, fontSize: 13, fontWeight: '600', fontFamily }}>
                {interpolateVariables(action.label, variables)}
            </Text>
        </Pressable>
    );
}

// ─── Tooltip overlay ──────────────────────────────────────────────────────────
// Rendered WITHOUT a Modal so sticky tooltips do not block underlying scrolls.
// DigiaHost must be placed at the app root level (after NavigationContainer)
// for absoluteFill to cover the full screen.

function TooltipOverlay({
    request,
    config,
}: {
    request: DigiaGuideRequest;
    config: TooltipConfig;
}) {
    const [stepIndex, setStepIndex] = useState(0);
    const [layout, setLayout] = useState<AnchorLayout | null>(null);
    const [floatPos, setFloatPos] = useState<FloatPos | null>(null);
    const [resolvedPlacement, setResolvedPlacement] = useState<string>('bottom');
    const [floatingSize, setFloatingSize] = useState<{ w: number; h: number } | null>(null);
    const step = config.steps[stepIndex];
    const variables = request.variables;
    const { width: screenW } = useWindowDimensions();
    const opacityAnim = useRef(new Animated.Value(1)).current;
    const pendingFadeIn = useRef(false);
    const fontFamily = Digia.fontFamily;

    const arrowSize = step.arrowSize ?? 8;
    const showArrow = step.showArrow !== false;
    const gap = showArrow ? arrowSize + 4 : 8;

    useEffect(() => {
        setLayout(null);
        setFloatPos(null);
        return digiaAnchorRegistry.subscribe(step.anchorKey, (l) => {
            setLayout(l);
        });
    }, [step.anchorKey]);

    useEffect(() => {
        if (!layout || !floatingSize) return;
        const tooltipW = Math.min(step.maxWidth, screenW - 32);
        const fpPlacement = (step.placement === 'auto' ? 'bottom' : step.placement) as any;
        computePosition(
            makeVirtualRef(layout),
            { w: Math.min(tooltipW, floatingSize.w), h: floatingSize.h },
            {
                platform: rnCorePlatform as any,
                placement: fpPlacement,
                middleware: [offset(gap), flip(), shift({ padding: 16 })],
            },
        ).then(({ x, y, placement }) => {
            setFloatPos({ x, y });
            setResolvedPlacement(placement as string);
        });
    }, [layout, floatingSize, step.placement, step.maxWidth, screenW, gap]);

    useEffect(() => {
        if (floatPos && pendingFadeIn.current) {
            pendingFadeIn.current = false;
            Animated.timing(opacityAnim, { toValue: 1, duration: 180, useNativeDriver: true }).start();
        }
    }, [floatPos, opacityAnim]);

    // Fire viewed/step_viewed when the step renders.
    useEffect(() => {
        const isMultiStep = config.steps.length > 1;
        if (stepIndex === 0) {
            request.onExperienceEvent({ type: 'viewed', stepIndex: 0, stepTotal: config.steps.length, anchorKey: step.anchorKey, displayStyle: 'tooltip' });
        }
        if (isMultiStep) {
            request.onExperienceEvent({ type: 'step_viewed', stepIndex, stepTotal: config.steps.length, anchorKey: step.anchorKey, displayStyle: 'tooltip' });
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [stepIndex]);

    // Closes guide without firing analytics — used after CTA actions have already fired clicked events.
    const closeFromCTA = useCallback(() => {
        Animated.timing(opacityAnim, { toValue: 0, duration: 150, useNativeDriver: true }).start(() => {
            digiaGuideController.cancel(request.payloadId);
        });
    }, [opacityAnim, request]);

    // Fires dismissed analytics then closes — used for non-CTA dismissals (scrim, back gesture).
    const dismiss = useCallback((reason: DismissReason = 'scrim_tap') => {
        Animated.timing(opacityAnim, { toValue: 0, duration: 150, useNativeDriver: true }).start(() => {
            const isMultiStep = config.steps.length > 1;
            request.onExperienceEvent({ type: 'dismissed', stepIndex, stepTotal: config.steps.length, anchorKey: step.anchorKey, displayStyle: 'tooltip', dismissReason: reason });
            if (isMultiStep) {
                request.onExperienceEvent({ type: 'step_dismissed', stepIndex, stepTotal: config.steps.length, anchorKey: step.anchorKey, displayStyle: 'tooltip', dismissReason: reason });
            }
            digiaGuideController.cancel(request.payloadId);
        });
    }, [request, opacityAnim, step, config, stepIndex]);

    const stepTo = useCallback((newIndex: number | null) => {
        Animated.timing(opacityAnim, { toValue: 0, duration: 150, useNativeDriver: true }).start(() => {
            if (newIndex === null) {
                digiaGuideController.cancel(request.payloadId);
            } else {
                pendingFadeIn.current = true;
                setStepIndex(newIndex);
            }
        });
    }, [opacityAnim, request]);

    const next = useCallback(() => stepTo(stepIndex < config.steps.length - 1 ? stepIndex + 1 : null), [stepIndex, config.steps.length, stepTo]);
    const prev = useCallback(() => { if (stepIndex > 0) stepTo(stepIndex - 1); }, [stepIndex, stepTo]);

    const actionCallbacks = useCallback((): ActionCallbacks => ({
        onNext: next,
        onBack: prev,
        onDismissSelf: closeFromCTA,
        onDismissAll: closeFromCTA,
    }), [next, prev, closeFromCTA]);

    const tooltipW = Math.min(step.maxWidth, screenW - 32);

    const arrowOffset = (showArrow && floatPos && layout && floatingSize)
        ? calcArrowOffset(resolvedPlacement, layout, floatPos, tooltipW, floatingSize.h, step.cornerRadius, arrowSize)
        : undefined;

    const handleBackdropPress = useCallback(() => {
        const behavior = config.outsideTapBehavior ?? 'next';
        if (behavior === 'nothing') return;
        if (behavior === 'next') next();
        if (behavior === 'dismiss') dismiss();
    }, [config.outsideTapBehavior, next, dismiss]);

    return (
        <Modal transparent statusBarTranslucent animationType="none" visible>
            {/* pointerEvents="box-none": container passes touches through; only children intercept.
                This prevents the invisible measurement pass from blocking the screen. */}
            <Animated.View style={[StyleSheet.absoluteFill, { opacity: opacityAnim }]} pointerEvents="box-none">
                {floatPos ? (
                    <>
                        {/* Full-screen backdrop: blocks all touches once bubble is positioned */}
                        <Pressable style={StyleSheet.absoluteFill} onPress={handleBackdropPress} />
                        {/* Bubble as Pressable so tapping the bubble body also advances */}
                        <Pressable
                            onLayout={(e) => {
                                const { width, height } = e.nativeEvent.layout;
                                if (floatingSize?.w !== width || floatingSize?.h !== height) {
                                    setFloatingSize({ w: width, h: height });
                                }
                            }}
                            onPress={handleBackdropPress}
                            style={[
                                s.tooltipBubble,
                                {
                                    left: floatPos.x,
                                    top: floatPos.y,
                                    width: tooltipW,
                                    backgroundColor: step.backgroundColor,
                                    borderRadius: step.cornerRadius,
                                    borderWidth: step.borderWidth,
                                    borderColor: step.borderColor,
                                    padding: step.padding,
                                },
                                step.shadow && s.shadow,
                            ]}
                        >
                            {showArrow && (
                                <GuideArrow
                                    placement={resolvedPlacement}
                                    color={step.arrowColor ?? step.backgroundColor}
                                    borderColor={step.arrowBorderColor ?? step.borderColor}
                                    size={arrowSize}
                                    arrowOffset={arrowOffset}
                                />
                            )}
                            <Text style={{ color: step.titleColor, fontSize: step.titleSize, fontWeight: step.titleWeight, fontFamily }}>
                                {interpolateVariables(step.title, variables)}
                            </Text>
                            {!!step.body && (
                                <Text style={{ marginTop: 4, color: step.bodyColor, fontSize: step.bodySize, fontFamily }}>
                                    {interpolateVariables(step.body, variables)}
                                </Text>
                            )}
                            <View style={s.actionRow}>
                                {step.actions.map((action, i) => (
                                    <ActionButton
                                        key={i}
                                        action={action}
                                        variables={variables}
                                        btnPrimaryBg={step.buttonPrimaryBackgroundColor}
                                        btnPrimaryText={step.buttonPrimaryTextColor}
                                        btnGhostText={step.buttonGhostTextColor}
                                        onPress={() => {
                                            const isMultiStep = config.steps.length > 1;
                                            const isLastStep = stepIndex === config.steps.length - 1;
                                            const actionUrl = 'url' in action ? (action as { url: string }).url : undefined;
                                            request.onExperienceEvent({ type: 'clicked', stepIndex, stepTotal: config.steps.length, anchorKey: step.anchorKey, displayStyle: 'tooltip', ctaLabel: action.label, actionType: action.type, actionUrl });
                                            if (isMultiStep) {
                                                request.onExperienceEvent({ type: 'step_clicked', stepIndex, stepTotal: config.steps.length, anchorKey: step.anchorKey, displayStyle: 'tooltip', ctaLabel: action.label, actionType: action.type, actionUrl });
                                            }
                                            if (isMultiStep && isLastStep && action.type !== 'back') {
                                                request.onExperienceEvent({ type: 'completed', stepIndex, stepTotal: config.steps.length, anchorKey: step.anchorKey, displayStyle: 'tooltip' });
                                            }
                                            void digiaActionHandler.execute(action, {
                                                campaign_id: request.payloadId,
                                                campaign_key: request.campaignKey,
                                                campaign_type: 'guide',
                                                source: { kind: 'button', button_label: action.label },
                                                step_index: stepIndex,
                                                step_total: config.steps.length,
                                            }, actionCallbacks());
                                        }}
                                    />
                                ))}
                            </View>
                        </Pressable>
                    </>
                ) : (
                    // Off-screen measurement pass to determine bubble size before positioning.
                    <View
                        pointerEvents="none"
                        onLayout={(e) => setFloatingSize({ w: e.nativeEvent.layout.width, h: e.nativeEvent.layout.height })}
                        style={[s.tooltipBubble, { left: -9999, top: -9999, width: tooltipW, padding: step.padding }]}
                    >
                        <Text style={{ fontSize: step.titleSize, fontFamily }}>{interpolateVariables(step.title, variables)}</Text>
                        {!!step.body && <Text style={{ fontSize: step.bodySize, fontFamily }}>{interpolateVariables(step.body, variables)}</Text>}
                        <View style={s.actionRow}>
                            {step.actions.map((a, i) => (
                                <View key={i} style={s.button}>
                                    <Text style={{ fontSize: 13, fontFamily }}>{interpolateVariables(a.label, variables)}</Text>
                                </View>
                            ))}
                        </View>
                    </View>
                )}
            </Animated.View>
        </Modal>
    );
}

// ─── Spotlight overlay ────────────────────────────────────────────────────────

function buildCutoutPath(
    x: number, y: number, w: number, h: number,
    radius: number, shape: string,
): string {
    if (shape === 'circle') {
        const cx = x + w / 2;
        const cy = y + h / 2;
        const r = Math.max(w, h) / 2;
        return `M${cx - r},${cy} a${r},${r} 0 1,0 ${r * 2},0 a${r},${r} 0 1,0 -${r * 2},0 Z`;
    }
    if (shape === 'pill') {
        const r = h / 2;
        return `M${x + r},${y} L${x + w - r},${y} Q${x + w},${y} ${x + w},${y + r} L${x + w},${y + h - r} Q${x + w},${y + h} ${x + w - r},${y + h} L${x + r},${y + h} Q${x},${y + h} ${x},${y + h - r} L${x},${y + r} Q${x},${y} ${x + r},${y} Z`;
    }
    const r = Math.max(0, radius);
    if (r === 0) {
        return `M${x},${y} L${x + w},${y} L${x + w},${y + h} L${x},${y + h} Z`;
    }
    return `M${x + r},${y} L${x + w - r},${y} Q${x + w},${y} ${x + w},${y + r} L${x + w},${y + h - r} Q${x + w},${y + h} ${x + w - r},${y + h} L${x + r},${y + h} Q${x},${y + h} ${x},${y + h - r} L${x},${y + r} Q${x},${y} ${x + r},${y} Z`;
}

function SpotlightCallout({
    step,
    variables,
    layout,
    onActionPress,
}: {
    step: SpotlightStep;
    variables?: VariableMap;
    layout: AnchorLayout;
    onActionPress: (action: Action) => void;
}) {
    const { width: screenW } = useWindowDimensions();
    const [floatPos, setFloatPos] = useState<FloatPos | null>(null);
    const [resolvedPlacement, setResolvedPlacement] = useState<string>('below');
    const [floatingSize, setFloatingSize] = useState<{ w: number; h: number } | null>(null);
    const calloutW = Math.min(step.calloutMaxWidth, screenW - 32);

    const arrowSize = step.arrowSize ?? 8;
    const showArrow = step.showArrow !== false;
    const gap = (step.calloutGap ?? 8) + (showArrow ? arrowSize : 0);
    const fontFamily = Digia.fontFamily;

    useEffect(() => {
        if (!floatingSize) return;
        const fpPlacement = (
            step.calloutPosition === 'above' ? 'top'
                : step.calloutPosition === 'below' ? 'bottom'
                    : step.calloutPosition === 'auto' ? 'bottom'
                        : step.calloutPosition
        ) as any;
        computePosition(
            makeVirtualRef(layout, step.highlightPadding),
            { w: Math.min(calloutW, floatingSize.w), h: floatingSize.h },
            {
                platform: rnCorePlatform as any,
                placement: fpPlacement,
                middleware: [offset(gap), flip(), shift({ padding: 16 })],
            },
        ).then(({ x, y, placement }) => {
            // console.log('[Digia:spotlight] floatPos=', { x, y }, 'resolved=', placement);
            setFloatPos({ x, y });
            setResolvedPlacement(placement as string);
        });
    }, [layout, floatingSize, step.calloutPosition, calloutW, step.highlightPadding, gap]);

    // Compute arrow offset: point arrow tip at anchor center.
    const arrowOffset = (showArrow && floatPos && floatingSize)
        ? calcArrowOffset(resolvedPlacement, layout, floatPos, calloutW, floatingSize.h, step.calloutCornerRadius, arrowSize)
        : undefined;

    const calloutStyle = {
        backgroundColor: step.calloutBackgroundColor,
        borderRadius: step.calloutCornerRadius,
        padding: step.calloutPadding,
        borderWidth: step.calloutBorderWidth,
        borderColor: step.calloutBorderColor,
        width: calloutW,
    };

    if (!floatPos) {
        return (
            <View
                pointerEvents="none"
                onLayout={(e) => setFloatingSize({ w: e.nativeEvent.layout.width, h: e.nativeEvent.layout.height })}
                style={[calloutStyle, { position: 'absolute', left: -9999, top: -9999 }]}
            >
                <Text style={{ fontSize: step.titleSize, fontFamily }}>{interpolateVariables(step.title, variables)}</Text>
                {!!step.body && <Text style={{ marginTop: 4, fontSize: step.bodySize, fontFamily }}>{interpolateVariables(step.body, variables)}</Text>}
                <View style={s.actionRow}>
                    {step.actions.map((a, i) => (
                        <View key={i} style={s.button}>
                            <Text style={{ fontSize: 13, fontFamily }}>{interpolateVariables(a.label, variables)}</Text>
                        </View>
                    ))}
                </View>
            </View>
        );
    }

    return (
        <View
            style={[
                calloutStyle,
                { position: 'absolute', left: floatPos.x, top: floatPos.y },
                step.calloutShadow && s.shadow,
            ]}
        >
            {showArrow && (
                <GuideArrow
                    placement={resolvedPlacement}
                    color={step.arrowColor ?? step.calloutBackgroundColor}
                    borderColor={step.arrowBorderColor ?? step.calloutBorderColor}
                    size={arrowSize}
                    arrowOffset={arrowOffset}
                />
            )}
            <Text style={{ color: step.titleColor, fontSize: step.titleSize, fontWeight: step.titleWeight, fontFamily }}>
                {interpolateVariables(step.title, variables)}
            </Text>
            {!!step.body && (
                <Text style={{ marginTop: 4, color: step.bodyColor, fontSize: step.bodySize, fontFamily }}>
                    {interpolateVariables(step.body, variables)}
                </Text>
            )}
            <View style={s.actionRow}>
                {step.actions.map((action, i) => (
                    <ActionButton
                        key={i}
                        action={action}
                        variables={variables}
                        btnPrimaryBg={step.buttonPrimaryBackgroundColor}
                        btnPrimaryText={step.buttonPrimaryTextColor}
                        btnGhostText={step.buttonGhostTextColor}
                        onPress={() => { onActionPress(action); }}
                    />
                ))}
            </View>
        </View>
    );
}

function SpotlightOverlay({
    request,
    config,
}: {
    request: DigiaGuideRequest;
    config: SpotlightConfig;
}) {
    const [stepIndex, setStepIndex] = useState(0);
    const [layout, setLayout] = useState<AnchorLayout | null>(null);
    const step = config.steps[stepIndex];
    const variables = request.variables;
    const { width: screenW, height: screenH } = useWindowDimensions();
    const opacityAnim = useRef(new Animated.Value(1)).current;
    const pendingFadeIn = useRef(false);

    useEffect(() => {
        setLayout(null);
        const unsub = digiaAnchorRegistry.subscribe(step.anchorKey, (l) => {
            setLayout(l);
        });
        digiaAnchorRegistry.remeasure(step.anchorKey);
        return unsub;
    }, [step.anchorKey]);

    useEffect(() => {
        if (layout && pendingFadeIn.current) {
            pendingFadeIn.current = false;
            Animated.timing(opacityAnim, { toValue: 1, duration: 180, useNativeDriver: true }).start();
        }
    }, [layout, opacityAnim]);

    // Fire viewed/step_viewed when the step renders.
    useEffect(() => {
        const isMultiStep = config.steps.length > 1;
        if (stepIndex === 0) {
            request.onExperienceEvent({ type: 'viewed', stepIndex: 0, stepTotal: config.steps.length, anchorKey: step.anchorKey, displayStyle: 'spotlight' });
        }
        if (isMultiStep) {
            request.onExperienceEvent({ type: 'step_viewed', stepIndex, stepTotal: config.steps.length, anchorKey: step.anchorKey, displayStyle: 'spotlight' });
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [stepIndex]);

    const closeFromCTA = useCallback(() => {
        Animated.timing(opacityAnim, { toValue: 0, duration: 150, useNativeDriver: true }).start(() => {
            digiaGuideController.cancel(request.payloadId);
        });
    }, [opacityAnim, request]);

    const dismiss = useCallback((reason: DismissReason = 'scrim_tap') => {
        Animated.timing(opacityAnim, { toValue: 0, duration: 150, useNativeDriver: true }).start(() => {
            const isMultiStep = config.steps.length > 1;
            request.onExperienceEvent({ type: 'dismissed', stepIndex, stepTotal: config.steps.length, anchorKey: step.anchorKey, displayStyle: 'spotlight', dismissReason: reason });
            if (isMultiStep) {
                request.onExperienceEvent({ type: 'step_dismissed', stepIndex, stepTotal: config.steps.length, anchorKey: step.anchorKey, displayStyle: 'spotlight', dismissReason: reason });
            }
            digiaGuideController.cancel(request.payloadId);
        });
    }, [request, opacityAnim, step, config, stepIndex]);

    const stepTo = useCallback((newIndex: number | null) => {
        Animated.timing(opacityAnim, { toValue: 0, duration: 150, useNativeDriver: true }).start(() => {
            if (newIndex === null) {
                digiaGuideController.cancel(request.payloadId);
            } else {
                pendingFadeIn.current = true;
                setStepIndex(newIndex);
            }
        });
    }, [opacityAnim, request]);

    const next = useCallback(() => stepTo(stepIndex < config.steps.length - 1 ? stepIndex + 1 : null), [stepIndex, config.steps.length, stepTo]);
    const prev = useCallback(() => { if (stepIndex > 0) stepTo(stepIndex - 1); }, [stepIndex, stepTo]);

    const actionCallbacks = useCallback((): ActionCallbacks => ({
        onNext: next,
        onBack: prev,
        onDismissSelf: closeFromCTA,
        onDismissAll: closeFromCTA,
    }), [next, prev, closeFromCTA]);

    const handleActionPress = useCallback((action: Action) => {
        const isMultiStep = config.steps.length > 1;
        const isLastStep = stepIndex === config.steps.length - 1;
        const actionUrl = 'url' in action ? (action as { url: string }).url : undefined;
        request.onExperienceEvent({ type: 'clicked', stepIndex, stepTotal: config.steps.length, anchorKey: step.anchorKey, displayStyle: 'spotlight', ctaLabel: action.label, actionType: action.type, actionUrl });
        if (isMultiStep) {
            request.onExperienceEvent({ type: 'step_clicked', stepIndex, stepTotal: config.steps.length, anchorKey: step.anchorKey, displayStyle: 'spotlight', ctaLabel: action.label, actionType: action.type, actionUrl });
        }
        if (isMultiStep && isLastStep && action.type !== 'back') {
            request.onExperienceEvent({ type: 'completed', stepIndex, stepTotal: config.steps.length, anchorKey: step.anchorKey, displayStyle: 'spotlight' });
        }
        digiaActionHandler.execute(action, {
            campaign_id: request.payloadId,
            campaign_key: request.campaignKey,
            campaign_type: 'guide',
            source: { kind: 'button', button_label: action.label },
            step_index: stepIndex,
            step_total: config.steps.length,
        }, actionCallbacks());
    }, [request, stepIndex, config, step, actionCallbacks]);

    const handleBackdropPress = useCallback(() => {
        const behavior = config.outsideTapBehavior ?? 'next';
        if (behavior === 'nothing') return;
        if (behavior === 'next') next();
        if (behavior === 'dismiss') dismiss('scrim_tap');
    }, [config.outsideTapBehavior, next, dismiss]);

    const pad = step.highlightPadding;
    const cutoutX = layout ? layout.pageX - pad : 0;
    const cutoutY = layout ? layout.pageY - pad : 0;
    const cutoutW = layout ? layout.width + pad * 2 : 0;
    const cutoutH = layout ? layout.height + pad * 2 : 0;
    const screenPath = `M0,0 L${screenW},0 L${screenW},${screenH} L0,${screenH} Z`;
    const cutoutPath = layout
        ? buildCutoutPath(cutoutX, cutoutY, cutoutW, cutoutH, step.highlightCornerRadius, step.highlightShape)
        : '';

    return (
        <Modal transparent statusBarTranslucent animationType="none" visible>
            <Animated.View style={[StyleSheet.absoluteFill, { opacity: opacityAnim }]} pointerEvents="box-none">
                {layout && (
                    <>
                        <Svg
                            width={screenW}
                            height={screenH}
                            style={StyleSheet.absoluteFill}
                            pointerEvents="none"
                        >
                            <Path
                                fillRule="evenodd"
                                d={`${screenPath} ${cutoutPath}`}
                                fill={step.overlayColor}
                                fillOpacity={step.overlayOpacity}
                            />
                            {step.highlightGlowWidth > 0 && (
                                <Path
                                    d={cutoutPath}
                                    fill="none"
                                    stroke={step.highlightGlowColor}
                                    strokeWidth={step.highlightGlowWidth}
                                />
                            )}
                        </Svg>
                        {/* Backdrop with configurable outside-tap behaviour */}
                        <Pressable style={StyleSheet.absoluteFill} onPress={handleBackdropPress} />
                        <SpotlightCallout
                            step={step}
                            variables={variables}
                            layout={layout}
                            onActionPress={handleActionPress}
                        />
                    </>
                )}
            </Animated.View>
        </Modal>
    );
}

// ─── Guide runtime dispatcher ─────────────────────────────────────────────────

function DigiaGuideRuntime() {
    const [activeRequest, setActiveRequest] = useState<DigiaGuideRequest | null>(null);

    useEffect(() => {
        return digiaGuideController.subscribe((event) => {
            if (event.type === 'cancel') {
                setActiveRequest(null);
                return;
            }
            setActiveRequest(event.request);
        });
    }, []);

    if (!activeRequest) return null;

    switch (activeRequest.config.templateType) {
        case 'tooltip':
            return <TooltipOverlay request={activeRequest} config={activeRequest.config} />;
        case 'spotlight':
            return <SpotlightOverlay request={activeRequest} config={activeRequest.config} />;
    }
}

// ─── DigiaHost ────────────────────────────────────────────────────────────────

export function DigiaHost() {
    return <DigiaGuideRuntime />;
}

// ─── Styles ───────────────────────────────────────────────────────────────────

const s = StyleSheet.create({
    tooltipBubble: {
        position: 'absolute',
    },
    shadow: {
        shadowColor: '#000',
        shadowOpacity: 0.15,
        shadowRadius: 12,
        shadowOffset: { width: 0, height: 4 },
        elevation: 8,
    },
    actionRow: {
        marginTop: 12,
        flexDirection: 'row',
        justifyContent: 'flex-end',
        gap: 8,
    },
    button: {
        minHeight: 32,
        minWidth: 60,
        alignItems: 'center',
        justifyContent: 'center',
        borderRadius: 8,
        paddingHorizontal: 12,
    },
});
