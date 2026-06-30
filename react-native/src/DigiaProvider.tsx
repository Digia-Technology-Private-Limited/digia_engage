import React, { createContext, useCallback, useContext, useEffect, useRef, useState } from 'react';
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
import { digiaHealthReporter, HealthEventType } from './DigiaHealthReporter';
import { digiaActionHandler, type ActionCallbacks } from './actionHandler';
import type { DismissReason } from './types';
import type { Action, SpotlightConfig, SpotlightStep, TooltipConfig } from './templateTypes';
import { buildVariableContext, interpolate, type VariableContext } from './interpolate';

// ─── Variable context ─────────────────────────────────────────────────────────
// Provides the active campaign's VariableContext to all descendant components.
// Avoids threading the context through every prop chain.

const DigiaVariableCtx = createContext<VariableContext | undefined>(undefined);

const TextWithVariables = ({ children, ...props }: Omit<React.ComponentProps<typeof Text>, 'children'> & { children: string }) => {
    const ctx = useContext(DigiaVariableCtx);
    return <Text {...props}>{interpolate(children, ctx)}</Text>;
};

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
    borderWidth,
    size,
    arrowOffset,
}: {
    placement: string;
    color: string;
    borderColor: string;
    borderWidth: number;
    size: number;
    arrowOffset?: number;
}) {
    const bw = Math.max(0, borderWidth);
    const inset = Math.round(bw * (Math.SQRT2 - 1));
    const outer = size + inset;
    const ov = bw;

    // Horizontal: used for top / bottom placements (left offset within bubble width)
    const hWrap = (edge: 'top' | 'bottom') =>
        arrowOffset !== undefined
            ? { [edge]: -outer, left: arrowOffset - outer, width: outer * 2 }
            : { [edge]: -outer, left: 0, right: 0 };

    // Vertical: used for left / right placements (top offset within bubble height)
    const vWrap = (edge: 'left' | 'right') =>
        arrowOffset !== undefined
            ? { [edge]: -outer, top: arrowOffset - outer, height: outer * 2 }
            : { [edge]: -outer, top: 0, bottom: 0 };

    if (placement === 'bottom' || placement === 'below') {
        // bubble below anchor → arrow at TOP pointing ▲ up
        return (
            <View style={[arrowS.wrap, hWrap('top')]}>
                <View style={{ position: 'relative', width: outer * 2, height: outer }}>
                    {bw > 0 && (
                        <View style={{ position: 'absolute', bottom: 0, left: 0, width: 0, height: 0, borderStyle: 'solid', borderLeftWidth: outer, borderRightWidth: outer, borderBottomWidth: outer, borderTopWidth: 0, borderLeftColor: 'transparent', borderRightColor: 'transparent', borderBottomColor: borderColor }} />
                    )}
                    <View style={{ position: 'absolute', bottom: -ov, left: inset, width: 0, height: 0, borderStyle: 'solid', borderLeftWidth: size, borderRightWidth: size, borderBottomWidth: size, borderTopWidth: 0, borderLeftColor: 'transparent', borderRightColor: 'transparent', borderBottomColor: color }} />
                </View>
            </View>
        );
    }
    if (placement === 'top' || placement === 'above') {
        // bubble above anchor → arrow at BOTTOM pointing ▼ down
        return (
            <View style={[arrowS.wrap, hWrap('bottom')]}>
                <View style={{ position: 'relative', width: outer * 2, height: outer }}>
                    {bw > 0 && (
                        <View style={{ position: 'absolute', top: 0, left: 0, width: 0, height: 0, borderStyle: 'solid', borderLeftWidth: outer, borderRightWidth: outer, borderTopWidth: outer, borderBottomWidth: 0, borderLeftColor: 'transparent', borderRightColor: 'transparent', borderTopColor: borderColor }} />
                    )}
                    <View style={{ position: 'absolute', top: -ov, left: inset, width: 0, height: 0, borderStyle: 'solid', borderLeftWidth: size, borderRightWidth: size, borderTopWidth: size, borderBottomWidth: 0, borderLeftColor: 'transparent', borderRightColor: 'transparent', borderTopColor: color }} />
                </View>
            </View>
        );
    }
    if (placement === 'right') {
        // bubble right of anchor → arrow at LEFT pointing ◀ left
        return (
            <View style={[arrowS.wrap, vWrap('left')]}>
                <View style={{ position: 'relative', width: outer, height: outer * 2 }}>
                    {bw > 0 && (
                        <View style={{ position: 'absolute', right: 0, top: 0, width: 0, height: 0, borderStyle: 'solid', borderTopWidth: outer, borderBottomWidth: outer, borderRightWidth: outer, borderLeftWidth: 0, borderTopColor: 'transparent', borderBottomColor: 'transparent', borderRightColor: borderColor }} />
                    )}
                    <View style={{ position: 'absolute', right: -ov, top: inset, width: 0, height: 0, borderStyle: 'solid', borderTopWidth: size, borderBottomWidth: size, borderRightWidth: size, borderLeftWidth: 0, borderTopColor: 'transparent', borderBottomColor: 'transparent', borderRightColor: color }} />
                </View>
            </View>
        );
    }
    if (placement === 'left') {
        // bubble left of anchor → arrow at RIGHT pointing ▶ right
        return (
            <View style={[arrowS.wrap, vWrap('right')]}>
                <View style={{ position: 'relative', width: outer, height: outer * 2 }}>
                    {bw > 0 && (
                        <View style={{ position: 'absolute', left: 0, top: 0, width: 0, height: 0, borderStyle: 'solid', borderTopWidth: outer, borderBottomWidth: outer, borderLeftWidth: outer, borderRightWidth: 0, borderTopColor: 'transparent', borderBottomColor: 'transparent', borderLeftColor: borderColor }} />
                    )}
                    <View style={{ position: 'absolute', left: -ov, top: inset, width: 0, height: 0, borderStyle: 'solid', borderTopWidth: size, borderBottomWidth: size, borderLeftWidth: size, borderRightWidth: 0, borderTopColor: 'transparent', borderBottomColor: 'transparent', borderLeftColor: color }} />
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
    btnPrimaryBg,
    btnPrimaryText,
    btnGhostText,
    onPress,
}: {
    action: Action;
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
            <TextWithVariables style={{ color: isPrimary ? btnPrimaryText : btnGhostText, fontSize: 13, fontWeight: '600', fontFamily }}>
                {action.label}
            </TextWithVariables>
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
    const [readyStep, setReadyStep] = useState(-1);
    const ready = readyStep === stepIndex;
    const step = config.steps[stepIndex];
    const { width: screenW } = useWindowDimensions();
    const opacityAnim = useRef(new Animated.Value(1)).current;
    const pendingFadeIn = useRef(false);
    // Experience Viewed fires once per guide showing — not again on returning to step 0.
    const viewedFired = useRef(false);
    // Tracks the last step index for which Step Viewed fired, to avoid re-firing on reposition.
    const stepViewedFor = useRef(-1);
    // True once Completed has fired (the user tapped a CTA *inside* on the last/only step).
    // Used so the teardown dismiss() doesn't also emit step_dismissed for a completion.
    const completedFired = useRef(false);
    const fontFamily = Digia.fontFamily;

    const arrowSize = step.arrowSize ?? 8;
    const showArrow = step.showArrow !== false;
    const gap = showArrow ? arrowSize + 4 : 8;

    // Wait for the step's delayInMs before marking it ready. Re-runs on every step entry.
    useEffect(() => {
        const delay = step.delayInMs ?? 0;
        if (delay <= 0) {
            setReadyStep(stepIndex);
            return;
        }
        const timer = setTimeout(() => setReadyStep(stepIndex), delay);
        return () => clearTimeout(timer);
    }, [stepIndex]); // eslint-disable-line react-hooks/exhaustive-deps

    useEffect(() => {
        setLayout(null);
        setFloatPos(null);
        if (!ready) return;
        if (!digiaAnchorRegistry.isRegistered(step.anchorKey)) {
            // eslint-disable-next-line no-console
            console.warn(`[Digia] campaign dropped — anchor_key "${step.anchorKey}" is not registered on this screen (campaign_key=${request.campaignKey}, step=${stepIndex})`);
            digiaHealthReporter.report(HealthEventType.anchor_not_on_screen, {
                campaign_key: request.campaignKey,
                reason: 'anchor_key_not_registered',
                anchor_key: step.anchorKey,
                step_index: stepIndex,
            });
            digiaGuideController.cancel(request.payloadId);
            return;
        }
        let skipCached = false;
        const unsub = digiaAnchorRegistry.subscribe(step.anchorKey, (l) => {
            if (!skipCached) return;
            if (l.width === 0 || l.height === 0) {
                digiaGuideController.cancel(request.payloadId);
                return;
            }
            const { width: screenW, height: screenH } = Dimensions.get('window');
            if (l.pageY + l.height <= 0 || l.pageY >= screenH || l.pageX + l.width <= 0 || l.pageX >= screenW) {
                digiaGuideController.cancel(request.payloadId);
                return;
            }
            setLayout(l);
        });
        skipCached = true;
        digiaAnchorRegistry.remeasure(step.anchorKey);
        return unsub;
    }, [step.anchorKey, ready]); // eslint-disable-line react-hooks/exhaustive-deps

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

    // Fire viewed/step_viewed only once the tooltip is actually positioned and visible
    // (floatPos set) — not merely when the step becomes ready, since the anchor may still
    // resolve off-screen and cancel the campaign before anything is drawn.
    useEffect(() => {
        if (!floatPos) return;
        const isMultiStep = config.steps.length > 1;
        if (!viewedFired.current) {
            viewedFired.current = true;
            request.onExperienceEvent({ type: 'viewed', stepIndex, stepTotal: config.steps.length, anchorKey: step.anchorKey, displayStyle: 'tooltip' });
        }
        if (isMultiStep && stepViewedFor.current !== stepIndex) {
            stepViewedFor.current = stepIndex;
            request.onExperienceEvent({ type: 'step_viewed', stepIndex, stepTotal: config.steps.length, anchorKey: step.anchorKey, displayStyle: 'tooltip' });
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [stepIndex, floatPos]);

    // Fires dismissed analytics then closes — used for every dismissal (scrim, back gesture, close CTA).
    // Dismissal is never a completion: closing the overlay (any way) is an abandonment.
    // Completion is owned solely by handleActionPress (a CTA tapped *inside* the last/only step).
    const dismiss = useCallback((reason: DismissReason = 'scrim_tap') => {
        Animated.timing(opacityAnim, { toValue: 0, duration: 150, useNativeDriver: true }).start(() => {
            const isMultiStep = config.steps.length > 1;
            request.onExperienceEvent({ type: 'dismissed', stepIndex, stepTotal: config.steps.length, anchorKey: step.anchorKey, displayStyle: 'tooltip', dismissReason: reason });
            // step_dismissed marks abandoning a particular step. Skip it when this dismiss is the
            // teardown that follows a completion (the last step was completed, not abandoned).
            if (isMultiStep && !completedFired.current) {
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

    const next = useCallback(() => {
        if (stepIndex < config.steps.length - 1) {
            stepTo(stepIndex + 1);
        } else {
            // Past the last step there is nowhere to advance — tear down. This is NOT a
            // completion by itself: if the user got here by tapping a CTA inside, completedFired
            // is already set (so dismiss() skips step_dismissed); if they got here via an
            // outside tap (outsideTapBehavior:'next'), it is a plain dismiss.
            dismiss();
        }
    }, [stepIndex, config.steps.length, stepTo, dismiss]);
    const prev = useCallback(() => { if (stepIndex > 0) stepTo(stepIndex - 1); }, [stepIndex, stepTo]);

    const actionCallbacks = useCallback((): ActionCallbacks => ({
        onNext: next,
        onBack: prev,
        onDismissSelf: () => dismiss('user_close'),
        onDismissAll: () => dismiss('user_close'),
    }), [next, prev, dismiss]);

    const tooltipW = Math.min(step.maxWidth, screenW - 32);

    const arrowOffset = (showArrow && floatPos && layout && floatingSize)
        ? calcArrowOffset(resolvedPlacement, layout, floatPos, tooltipW, floatingSize.h, step.cornerRadius, arrowSize)
        : undefined;

    const handleBackdropPress = useCallback(() => {
        const isSticky = config.sticky !== false;
        if (isSticky && step.actions.length > 0) return;
        const behavior = config.outsideTapBehavior ?? 'next';
        if (behavior === 'nothing') return;
        if (behavior === 'next') next();
        if (behavior === 'dismiss') dismiss();
    }, [config.sticky, config.outsideTapBehavior, step.actions.length, next, dismiss]);

    const tooltipVarCtx = buildVariableContext(request.variableSchemas ?? [], request.variables);
    return (
        <DigiaVariableCtx.Provider value={tooltipVarCtx}>
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
                                        color={step.backgroundColor}
                                        borderColor={step.borderColor}
                                        borderWidth={step.borderWidth}
                                        size={arrowSize}
                                        arrowOffset={arrowOffset}
                                    />
                                )}
                                <TextWithVariables style={{ color: step.titleColor, fontSize: step.titleSize, fontWeight: step.titleWeight, fontFamily }}>
                                    {step.title}
                                </TextWithVariables>
                                {!!step.body && (
                                    <TextWithVariables style={{ marginTop: 4, color: step.bodyColor, fontSize: step.bodySize, fontFamily }}>
                                        {step.body}
                                    </TextWithVariables>
                                )}
                                <View style={s.actionRow}>
                                    {step.actions.map((action, i) => (
                                        <ActionButton
                                            key={i}
                                            action={action}
                                            btnPrimaryBg={step.buttonPrimaryBackgroundColor}
                                            btnPrimaryText={step.buttonPrimaryTextColor}
                                            btnGhostText={step.buttonGhostTextColor}
                                            onPress={() => {
                                                const isLastStep = stepIndex === config.steps.length - 1;
                                                const actionUrl = 'url' in action ? (action as { url: string }).url : undefined;
                                                // Guides only have Step Clicked in the matrix (no Experience Clicked) —
                                                // fire it once per CTA tap, single- and multi-step alike.
                                                request.onExperienceEvent({ type: 'step_clicked', stepIndex, stepTotal: config.steps.length, anchorKey: step.anchorKey, displayStyle: 'tooltip', ctaLabel: action.label, actionType: action.type, actionUrl });
                                                // Completion = the user engaged a CTA *inside* the tooltip on the last
                                                // (or only) step. Single- and multi-step alike; 'back' isn't a finish.
                                                if (isLastStep && action.type !== 'back') {
                                                    completedFired.current = true;
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
                            <TextWithVariables style={{ fontSize: step.titleSize, fontFamily }}>{step.title}</TextWithVariables>
                            {!!step.body && <TextWithVariables style={{ fontSize: step.bodySize, fontFamily }}>{step.body}</TextWithVariables>}
                            <View style={s.actionRow}>
                                {step.actions.map((a, i) => (
                                    <View key={i} style={s.button}>
                                        <TextWithVariables style={{ fontSize: 13, fontFamily }}>{a.label}</TextWithVariables>
                                    </View>
                                ))}
                            </View>
                        </View>
                    )}
                </Animated.View>
            </Modal>
        </DigiaVariableCtx.Provider>
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
    layout,
    onActionPress,
}: {
    step: SpotlightStep;
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
                <TextWithVariables style={{ fontSize: step.titleSize, fontFamily }}>{step.title}</TextWithVariables>
                {!!step.body && <TextWithVariables style={{ marginTop: 4, fontSize: step.bodySize, fontFamily }}>{step.body}</TextWithVariables>}
                <View style={s.actionRow}>
                    {step.actions.map((a, i) => (
                        <View key={i} style={s.button}>
                            <TextWithVariables style={{ fontSize: 13, fontFamily }}>{a.label}</TextWithVariables>
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
                    color={step.calloutBackgroundColor}
                    borderColor={step.calloutBorderColor}
                    borderWidth={step.calloutBorderWidth}
                    size={arrowSize}
                    arrowOffset={arrowOffset}
                />
            )}
            <TextWithVariables style={{ color: step.titleColor, fontSize: step.titleSize, fontWeight: step.titleWeight, fontFamily }}>
                {step.title}
            </TextWithVariables>
            {!!step.body && (
                <TextWithVariables style={{ marginTop: 4, color: step.bodyColor, fontSize: step.bodySize, fontFamily }}>
                    {step.body}
                </TextWithVariables>
            )}
            <View style={s.actionRow}>
                {step.actions.map((action, i) => (
                    <ActionButton
                        key={i}
                        action={action}
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
    const [readyStep, setReadyStep] = useState(-1);
    const ready = readyStep === stepIndex;
    const step = config.steps[stepIndex];
    const { width: screenW, height: screenH } = useWindowDimensions();
    const opacityAnim = useRef(new Animated.Value(1)).current;
    const pendingFadeIn = useRef(false);
    // Experience Viewed fires once per guide showing — not again on returning to step 0.
    const viewedFired = useRef(false);
    // Tracks the last step index for which Step Viewed fired, to avoid re-firing on remeasure.
    const stepViewedFor = useRef(-1);
    // True once Completed has fired (the user tapped a CTA *inside* on the last/only step).
    // Used so the teardown dismiss() doesn't also emit step_dismissed for a completion.
    const completedFired = useRef(false);

    // Wait for the step's delayInMs before marking it ready. Re-runs on every step entry.
    useEffect(() => {
        const delay = step.delayInMs ?? 0;
        if (delay <= 0) {
            setReadyStep(stepIndex);
            return;
        }
        const timer = setTimeout(() => setReadyStep(stepIndex), delay);
        return () => clearTimeout(timer);
    }, [stepIndex]); // eslint-disable-line react-hooks/exhaustive-deps

    useEffect(() => {
        setLayout(null);
        if (!ready) return; // hold off measuring/showing until the delay has elapsed
        if (!digiaAnchorRegistry.isRegistered(step.anchorKey)) {
            // eslint-disable-next-line no-console
            console.warn(`[Digia] campaign dropped — anchor_key "${step.anchorKey}" is not registered on this screen (campaign_key=${request.campaignKey}, step=${stepIndex})`);
            digiaHealthReporter.report(HealthEventType.anchor_not_on_screen, {
                campaign_key: request.campaignKey,
                reason: 'anchor_key_not_registered',
                anchor_key: step.anchorKey,
                step_index: stepIndex,
            });
            digiaGuideController.cancel(request.payloadId);
            return;
        }
        let skipCached = false;
        const unsub = digiaAnchorRegistry.subscribe(step.anchorKey, (l) => {
            if (!skipCached) return;
            if (l.width === 0 || l.height === 0) {
                digiaGuideController.cancel(request.payloadId);
                return;
            }
            const { width: screenW, height: screenH } = Dimensions.get('window');
            if (l.pageY + l.height <= 0 || l.pageY >= screenH || l.pageX + l.width <= 0 || l.pageX >= screenW) {
                digiaGuideController.cancel(request.payloadId);
                return;
            }
            setLayout(l);
        });
        skipCached = true;
        digiaAnchorRegistry.remeasure(step.anchorKey);
        return unsub;
    }, [step.anchorKey, ready]); // eslint-disable-line react-hooks/exhaustive-deps

    useEffect(() => {
        if (layout && pendingFadeIn.current) {
            pendingFadeIn.current = false;
            Animated.timing(opacityAnim, { toValue: 1, duration: 180, useNativeDriver: true }).start();
        }
    }, [layout, opacityAnim]);

    // Fire viewed/step_viewed only once the spotlight cutout is actually laid out and visible
    // (layout set) — not merely when the step becomes ready, since the anchor may still resolve
    // off-screen and cancel the campaign before anything is drawn.
    useEffect(() => {
        if (!layout) return;
        const isMultiStep = config.steps.length > 1;
        if (!viewedFired.current) {
            viewedFired.current = true;
            request.onExperienceEvent({ type: 'viewed', stepIndex, stepTotal: config.steps.length, anchorKey: step.anchorKey, displayStyle: 'spotlight' });
        }
        if (isMultiStep && stepViewedFor.current !== stepIndex) {
            stepViewedFor.current = stepIndex;
            request.onExperienceEvent({ type: 'step_viewed', stepIndex, stepTotal: config.steps.length, anchorKey: step.anchorKey, displayStyle: 'spotlight' });
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [stepIndex, layout]);

    // Dismissal is never a completion: closing the overlay (any way) is an abandonment.
    // Completion is owned solely by handleActionPress (a CTA tapped *inside* the last/only step).
    const dismiss = useCallback((reason: DismissReason = 'scrim_tap') => {
        Animated.timing(opacityAnim, { toValue: 0, duration: 150, useNativeDriver: true }).start(() => {
            const isMultiStep = config.steps.length > 1;
            request.onExperienceEvent({ type: 'dismissed', stepIndex, stepTotal: config.steps.length, anchorKey: step.anchorKey, displayStyle: 'spotlight', dismissReason: reason });
            // step_dismissed marks abandoning a particular step. Skip it when this dismiss is the
            // teardown that follows a completion (the last step was completed, not abandoned).
            if (isMultiStep && !completedFired.current) {
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

    const next = useCallback(() => {
        if (stepIndex < config.steps.length - 1) {
            stepTo(stepIndex + 1);
        } else {
            // Past the last step there is nowhere to advance — tear down. This is NOT a
            // completion by itself: if the user got here by tapping a CTA inside, completedFired
            // is already set (so dismiss() skips step_dismissed); if they got here via an
            // outside tap (outsideTapBehavior:'next'), it is a plain dismiss.
            dismiss();
        }
    }, [stepIndex, config.steps.length, stepTo, dismiss]);
    const prev = useCallback(() => { if (stepIndex > 0) stepTo(stepIndex - 1); }, [stepIndex, stepTo]);

    const actionCallbacks = useCallback((): ActionCallbacks => ({
        onNext: next,
        onBack: prev,
        onDismissSelf: () => dismiss('user_close'),
        onDismissAll: () => dismiss('user_close'),
    }), [next, prev, dismiss]);

    const handleActionPress = useCallback((action: Action) => {
        const isLastStep = stepIndex === config.steps.length - 1;
        const actionUrl = 'url' in action ? (action as { url: string }).url : undefined;
        // Guides only have Step Clicked in the matrix (no Experience Clicked) —
        // fire it once per CTA tap, single- and multi-step alike.
        request.onExperienceEvent({ type: 'step_clicked', stepIndex, stepTotal: config.steps.length, anchorKey: step.anchorKey, displayStyle: 'spotlight', ctaLabel: action.label, actionType: action.type, actionUrl });
        // Completion = the user engaged a CTA *inside* the spotlight on the last
        // (or only) step. Single- and multi-step alike; 'back' isn't a finish.
        if (isLastStep && action.type !== 'back') {
            completedFired.current = true;
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

    const spotlightVarCtx = buildVariableContext(request.variableSchemas ?? [], request.variables);
    return (
        <DigiaVariableCtx.Provider value={spotlightVarCtx}>
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
                                layout={layout}
                                onActionPress={handleActionPress}
                            />
                        </>
                    )}
                </Animated.View>
            </Modal>
        </DigiaVariableCtx.Provider>
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
//
// Place once at the app root. Accepts optional children (wrap mode) or can be
// used standalone (<DigiaHost />) as a sibling alongside other root elements.
//
// Renders ONLY the JS guide / tooltip / spotlight runtime (DigiaGuideRuntime).
// Native overlays (nudges, dialogs, bottom sheets, surveys) render through the
// single native overlay host that DigiaModule mounts imperatively after
// Digia.initialize() — that host owns its own touch handling and claims touches
// only while an overlay is active. Mounting a native host here as well would
// render every overlay twice (one stacked behind the other), and a
// React-tree host is not reliably touch-correct under the New Architecture.

export function DigiaHost({ children }: { children?: React.ReactNode }) {
    if (children != null) {
        return (
            <>
                {children}
                <DigiaGuideRuntime />
            </>
        );
    }

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