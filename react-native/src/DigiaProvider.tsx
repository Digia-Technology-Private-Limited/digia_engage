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
import { digiaGuideController, type DigiaGuideRequest } from './DigiaGuideController';
import { digiaAnchorRegistry, type AnchorLayout } from './digiaAnchorRegistry';
import type { Action, SpotlightConfig, SpotlightStep, TooltipConfig, TooltipStep } from './templateTypes';

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

function GuideArrow({
    placement,
    color,
    borderColor,
    size,
}: {
    placement: string;
    color: string;
    borderColor: string;
    size: number;
}) {
    const s1 = size + 1;

    if (placement === 'bottom' || placement === 'below') {
        // bubble below anchor → arrow at TOP pointing ▲ up
        return (
            <View style={[arrowS.wrap, { top: -s1, left: 0, right: 0 }]}>
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
            <View style={[arrowS.wrap, { bottom: -s1, left: 0, right: 0 }]}>
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
            <View style={[arrowS.wrap, { left: -s1, top: 0, bottom: 0 }]}>
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
            <View style={[arrowS.wrap, { right: -s1, top: 0, bottom: 0 }]}>
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

// ─── Shared action helpers ────────────────────────────────────────────────────

function handleAction(action: Action, cb: { onNext: () => void; onBack: () => void; onSkip: () => void }) {
    switch (action.type) {
        case 'next':      cb.onNext(); break;
        case 'prev':      cb.onBack(); break;
        case 'dismiss':   cb.onSkip(); break;
        case 'deep_link': cb.onSkip(); break;
    }
}

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
    return (
        <Pressable
            onPress={onPress}
            style={[s.button, isPrimary && { backgroundColor: btnPrimaryBg }]}
        >
            <Text style={{ color: isPrimary ? btnPrimaryText : btnGhostText, fontSize: 13, fontWeight: '600' }}>
                {action.label}
            </Text>
        </Pressable>
    );
}

// ─── Tooltip overlay ──────────────────────────────────────────────────────────

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
    const { width: screenW } = useWindowDimensions();
    const opacityAnim = useRef(new Animated.Value(1)).current;
    const pendingFadeIn = useRef(false);

    const arrowSize = step.arrowSize ?? 8;
    const showArrow = step.showArrow !== false;
    const gap = showArrow ? arrowSize + 4 : 8;
    const isSticky = config.sticky !== false;

    useEffect(() => {
        setLayout(null);
        setFloatPos(null);
        log('tooltip subscribing to anchorKey=', step.anchorKey);
        return digiaAnchorRegistry.subscribe(step.anchorKey, (l) => {
            log('tooltip got layout anchorKey=', step.anchorKey, l);
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
            log('tooltip pos=', { x, y }, 'resolved=', placement);
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

    const dismiss = useCallback(() => {
        Animated.timing(opacityAnim, { toValue: 0, duration: 150, useNativeDriver: true }).start(() => {
            request.onExperienceEvent({ type: 'dismissed' });
            digiaGuideController.cancel(request.payloadId);
        });
    }, [request, opacityAnim]);

    const stepTo = useCallback((newIndex: number | null) => {
        Animated.timing(opacityAnim, { toValue: 0, duration: 150, useNativeDriver: true }).start(() => {
            if (newIndex === null) {
                request.onExperienceEvent({ type: 'dismissed' });
                digiaGuideController.cancel(request.payloadId);
            } else {
                pendingFadeIn.current = true;
                setStepIndex(newIndex);
            }
        });
    }, [opacityAnim, request]);

    const next = useCallback(() => stepTo(stepIndex < config.steps.length - 1 ? stepIndex + 1 : null), [stepIndex, config.steps.length, stepTo]);
    const prev = useCallback(() => { if (stepIndex > 0) stepTo(stepIndex - 1); }, [stepIndex, stepTo]);

    const tooltipW = Math.min(step.maxWidth, screenW - 32);

    return (
        <Modal transparent statusBarTranslucent animationType="none" visible>
            <Animated.View style={[StyleSheet.absoluteFill, { opacity: opacityAnim }]} pointerEvents="box-none">
                {/* Backdrop — only when NOT sticky */}
                {!isSticky && (
                    <Pressable style={StyleSheet.absoluteFill} onPress={dismiss} />
                )}
                {floatPos ? (
                    <View
                        pointerEvents="box-none"
                        onLayout={(e) => {
                            const { width, height } = e.nativeEvent.layout;
                            if (floatingSize?.w !== width || floatingSize?.h !== height) {
                                setFloatingSize({ w: width, h: height });
                            }
                        }}
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
                            />
                        )}
                        <Text style={{ color: step.titleColor, fontSize: step.titleSize, fontWeight: step.titleWeight }}>
                            {step.title}
                        </Text>
                        {!!step.body && (
                            <Text style={{ marginTop: 4, color: step.bodyColor, fontSize: step.bodySize }}>
                                {step.body}
                            </Text>
                        )}
                        <View style={s.actionRow}>
                            {step.actions.map((action, i) => (
                                <ActionButton
                                    key={i}
                                    action={action}
                                    btnPrimaryBg={step.buttonPrimaryBackgroundColor}
                                    btnPrimaryText={step.buttonPrimaryTextColor}
                                    btnGhostText={step.buttonGhostTextColor}
                                    onPress={() => handleAction(action, { onNext: next, onBack: prev, onSkip: dismiss })}
                                />
                            ))}
                        </View>
                    </View>
                ) : (
                    <View
                        pointerEvents="none"
                        onLayout={(e) => setFloatingSize({ w: e.nativeEvent.layout.width, h: e.nativeEvent.layout.height })}
                        style={[s.tooltipBubble, { left: -9999, top: -9999, width: tooltipW, padding: step.padding }]}
                    >
                        <Text style={{ fontSize: step.titleSize }}>{step.title}</Text>
                        {!!step.body && <Text style={{ fontSize: step.bodySize }}>{step.body}</Text>}
                        <View style={s.actionRow}>
                            {step.actions.map((a, i) => (
                                <View key={i} style={s.button}>
                                    <Text style={{ fontSize: 13 }}>{a.label}</Text>
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
    layout,
    onNext,
    onBack,
    onSkip,
}: {
    step: SpotlightStep;
    layout: AnchorLayout;
    onNext: () => void;
    onBack: () => void;
    onSkip: () => void;
}) {
    const { width: screenW } = useWindowDimensions();
    const [floatPos, setFloatPos] = useState<FloatPos | null>(null);
    const [resolvedPlacement, setResolvedPlacement] = useState<string>('below');
    const [floatingSize, setFloatingSize] = useState<{ w: number; h: number } | null>(null);
    const calloutW = Math.min(step.calloutMaxWidth, screenW - 32);

    const arrowSize = step.arrowSize ?? 8;
    const showArrow = step.showArrow !== false;
    const gap = (step.calloutGap ?? 8) + (showArrow ? arrowSize : 0);

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
            console.log('[Digia:spotlight] floatPos=', { x, y }, 'resolved=', placement);
            setFloatPos({ x, y });
            setResolvedPlacement(placement as string);
        });
    }, [layout, floatingSize, step.calloutPosition, calloutW, step.highlightPadding, gap]);

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
                <Text style={{ fontSize: step.titleSize }}>{step.title}</Text>
                {!!step.body && <Text style={{ marginTop: 4, fontSize: step.bodySize }}>{step.body}</Text>}
                <View style={s.actionRow}>
                    {step.actions.map((a, i) => (
                        <View key={i} style={s.button}>
                            <Text style={{ fontSize: 13 }}>{a.label}</Text>
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
                />
            )}
            <Text style={{ color: step.titleColor, fontSize: step.titleSize, fontWeight: step.titleWeight }}>
                {step.title}
            </Text>
            {!!step.body && (
                <Text style={{ marginTop: 4, color: step.bodyColor, fontSize: step.bodySize }}>
                    {step.body}
                </Text>
            )}
            <View style={s.actionRow}>
                {step.actions.map((action, i) => (
                    <ActionButton
                        key={i}
                        action={action}
                        btnPrimaryBg={step.buttonPrimaryBackgroundColor}
                        btnPrimaryText={step.buttonPrimaryTextColor}
                        btnGhostText={step.buttonGhostTextColor}
                        onPress={() => handleAction(action, { onNext, onBack, onSkip })}
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
    const { width: screenW, height: screenH } = useWindowDimensions();
    const screenDims = Dimensions.get('screen');
    const opacityAnim = useRef(new Animated.Value(1)).current;
    const pendingFadeIn = useRef(false);

    useEffect(() => {
        setLayout(null);
        log('spotlight subscribing to anchorKey=', step.anchorKey);
        return digiaAnchorRegistry.subscribe(step.anchorKey, (l) => {
            log('spotlight got layout anchorKey=', step.anchorKey, l);
            log('[debug] screenH(window)=', screenH, 'screenH(screen)=', screenDims.height, 'anchor.pageY=', l.pageY);
            setLayout(l);
        });
    }, [step.anchorKey]);

    useEffect(() => {
        if (layout && pendingFadeIn.current) {
            pendingFadeIn.current = false;
            Animated.timing(opacityAnim, { toValue: 1, duration: 180, useNativeDriver: true }).start();
        }
    }, [layout, opacityAnim]);

    const dismiss = useCallback(() => {
        Animated.timing(opacityAnim, { toValue: 0, duration: 150, useNativeDriver: true }).start(() => {
            request.onExperienceEvent({ type: 'dismissed' });
            digiaGuideController.cancel(request.payloadId);
        });
    }, [request, opacityAnim]);

    const stepTo = useCallback((newIndex: number | null) => {
        Animated.timing(opacityAnim, { toValue: 0, duration: 150, useNativeDriver: true }).start(() => {
            if (newIndex === null) {
                request.onExperienceEvent({ type: 'dismissed' });
                digiaGuideController.cancel(request.payloadId);
            } else {
                pendingFadeIn.current = true;
                setStepIndex(newIndex);
            }
        });
    }, [opacityAnim, request]);

    const next = useCallback(() => stepTo(stepIndex < config.steps.length - 1 ? stepIndex + 1 : null), [stepIndex, config.steps.length, stepTo]);
    const prev = useCallback(() => { if (stepIndex > 0) stepTo(stepIndex - 1); }, [stepIndex, stepTo]);

    const handleBackdropPress = useCallback(() => {
        const behavior = config.outsideTapBehavior ?? 'next';
        if (behavior === 'nothing') return;
        if (behavior === 'next') next();
        if (behavior === 'dismiss') dismiss();
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
                            layout={layout}
                            onNext={next}
                            onBack={prev}
                            onSkip={dismiss}
                        />
                    </>
                )}
            </Animated.View>
        </Modal>
    );
}

// ─── Guide runtime dispatcher ─────────────────────────────────────────────────

const log = (...args: any[]) => __DEV__ && console.log('[DigiaHost]', ...args);

function DigiaGuideRuntime() {
    const [activeRequest, setActiveRequest] = useState<DigiaGuideRequest | null>(null);

    useEffect(() => {
        log('DigiaGuideRuntime mounted — subscribing to guide controller');
        return digiaGuideController.subscribe((event) => {
            if (event.type === 'cancel') {
                log('guide cancelled payloadId=', event.payloadId);
                setActiveRequest(null);
                return;
            }
            log('guide start campaignKey=', event.request.campaignKey, 'type=', event.request.config.templateType);
            setActiveRequest(event.request);
        });
    }, []);

    if (!activeRequest) return null;

    switch (activeRequest.config.templateType) {
        case 'tooltip':
            log('rendering TooltipOverlay');
            return <TooltipOverlay request={activeRequest} config={activeRequest.config} />;
        case 'spotlight':
            log('rendering SpotlightOverlay');
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
