import React, { useCallback, useEffect, useRef, useState } from 'react';
import {
    Dimensions,
    Modal,
    Pressable,
    StyleSheet,
    Text,
    View,
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

function makeVirtualRef(layout: AnchorLayout) {
    return {
        getBoundingClientRect: () => ({
            x: layout.pageX, y: layout.pageY,
            width: layout.width, height: layout.height,
            top: layout.pageY, left: layout.pageX,
            bottom: layout.pageY + layout.height,
            right: layout.pageX + layout.width,
        }),
    };
}

type FloatPos = { x: number; y: number };

async function computeFloat(
    layout: AnchorLayout,
    floatingW: number,
    floatingH: number,
    placement: string,
    gap: number,
): Promise<FloatPos> {
    const fpPlacement = (
        placement === 'above' ? 'top'
        : placement === 'below' ? 'bottom'
        : placement === 'auto' ? 'bottom'
        : placement
    ) as any;

    const { x, y } = await computePosition(
        makeVirtualRef(layout),
        { w: floatingW, h: floatingH },
        {
            platform: rnCorePlatform as any,
            placement: fpPlacement,
            middleware: [offset(gap), flip(), shift({ padding: 16 })],
        },
    );
    return { x, y };
}

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
    const [floatingSize, setFloatingSize] = useState<{ w: number; h: number } | null>(null);
    const step = config.steps[stepIndex];
    const { width: screenW } = Dimensions.get('window');

    // Subscribe to anchor layout
    useEffect(() => {
        setLayout(null);
        setFloatPos(null);
        log('tooltip subscribing to anchorKey=', step.anchor_key);
        return digiaAnchorRegistry.subscribe(step.anchor_key, (l) => {
            log('tooltip got layout anchorKey=', step.anchor_key, l);
            setLayout(l);
        });
    }, [step.anchor_key]);

    // Recompute float position whenever layout or floating size changes
    useEffect(() => {
        if (!layout || !floatingSize) return;
        const tooltipW = Math.min(step.max_width, screenW - 32);
        computeFloat(layout, Math.min(tooltipW, floatingSize.w), floatingSize.h, step.placement, 8)
            .then((pos) => {
                log('tooltip computed pos=', pos, 'placement=', step.placement);
                setFloatPos(pos);
            });
    }, [layout, floatingSize, step.placement, step.max_width, screenW]);

    const dismiss = useCallback(() => {
        request.onExperienceEvent({ type: 'dismissed' });
        digiaGuideController.cancel(request.payloadId);
    }, [request]);

    const next = useCallback(() => stepIndex < config.steps.length - 1 ? setStepIndex(stepIndex + 1) : dismiss(), [stepIndex, config.steps.length, dismiss]);
    const prev = useCallback(() => { if (stepIndex > 0) setStepIndex(stepIndex - 1); }, [stepIndex]);

    const tooltipW = Math.min(step.max_width, screenW - 32);

    return (
        <Modal transparent statusBarTranslucent animationType="fade" visible>
            <View style={StyleSheet.absoluteFill} pointerEvents="box-none">
                {/* Invisible backdrop tap to dismiss */}
                <Pressable style={StyleSheet.absoluteFill} onPress={dismiss} />
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
                                backgroundColor: step.background_color,
                                borderRadius: step.corner_radius,
                                borderWidth: step.border_width,
                                borderColor: step.border_color,
                                padding: step.padding,
                            },
                            step.shadow && s.shadow,
                        ]}
                    >
                        <Text style={{ color: step.title_color, fontSize: step.title_size, fontWeight: step.title_weight }}>
                            {step.title}
                        </Text>
                        {!!step.body && (
                            <Text style={{ marginTop: 4, color: step.body_color, fontSize: step.body_size }}>
                                {step.body}
                            </Text>
                        )}
                        <View style={s.actionRow}>
                            {step.actions.map((action, i) => (
                                <ActionButton
                                    key={i}
                                    action={action}
                                    btnPrimaryBg={step.button_primary_background_color}
                                    btnPrimaryText={step.button_primary_text_color}
                                    btnGhostText={step.button_ghost_text_color}
                                    onPress={() => handleAction(action, { onNext: next, onBack: prev, onSkip: dismiss })}
                                />
                            ))}
                        </View>
                    </View>
                ) : (
                    // Hidden measurement pass — renders off-screen to get floating size
                    <View
                        pointerEvents="none"
                        onLayout={(e) => setFloatingSize({ w: e.nativeEvent.layout.width, h: e.nativeEvent.layout.height })}
                        style={[s.tooltipBubble, { left: -9999, top: -9999, width: tooltipW, padding: step.padding }]}
                    >
                        <Text style={{ fontSize: step.title_size }}>{step.title}</Text>
                        {!!step.body && <Text style={{ fontSize: step.body_size }}>{step.body}</Text>}
                        <View style={s.actionRow}>
                            {step.actions.map((a, i) => (
                                <View key={i} style={s.button}>
                                    <Text style={{ fontSize: 13 }}>{a.label}</Text>
                                </View>
                            ))}
                        </View>
                    </View>
                )}
            </View>
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
        // SVG circle as path (two arcs)
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
    const { width: screenW } = Dimensions.get('window');
    const [floatPos, setFloatPos] = useState<FloatPos | null>(null);
    const [floatingSize, setFloatingSize] = useState<{ w: number; h: number } | null>(null);
    const calloutW = Math.min(step.callout_max_width, screenW - 32);

    useEffect(() => {
        if (!floatingSize) return;
        computeFloat(layout, Math.min(calloutW, floatingSize.w), floatingSize.h, step.callout_position, 12)
            .then(setFloatPos);
    }, [layout, floatingSize, step.callout_position, calloutW]);

    const calloutStyle = {
        backgroundColor: step.callout_background_color,
        borderRadius: step.callout_corner_radius,
        padding: step.callout_padding,
        borderWidth: step.callout_border_width,
        borderColor: step.callout_border_color,
        width: calloutW,
    };

    if (!floatPos) {
        // Measure pass
        return (
            <View
                pointerEvents="none"
                onLayout={(e) => setFloatingSize({ w: e.nativeEvent.layout.width, h: e.nativeEvent.layout.height })}
                style={[calloutStyle, { position: 'absolute', left: -9999, top: -9999 }]}
            >
                <Text style={{ fontSize: step.title_size }}>{step.title}</Text>
                {!!step.body && <Text style={{ fontSize: step.body_size }}>{step.body}</Text>}
            </View>
        );
    }

    return (
        <View
            style={[
                calloutStyle,
                { position: 'absolute', left: floatPos.x, top: floatPos.y },
                step.callout_shadow && s.shadow,
            ]}
        >
            <Text style={{ color: step.title_color, fontSize: step.title_size, fontWeight: step.title_weight }}>
                {step.title}
            </Text>
            {!!step.body && (
                <Text style={{ marginTop: 4, color: step.body_color, fontSize: step.body_size }}>
                    {step.body}
                </Text>
            )}
            <View style={s.actionRow}>
                {step.actions.map((action, i) => (
                    <ActionButton
                        key={i}
                        action={action}
                        btnPrimaryBg={step.button_primary_background_color}
                        btnPrimaryText={step.button_primary_text_color}
                        btnGhostText={step.button_ghost_text_color}
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
    const { width: screenW, height: screenH } = Dimensions.get('window');

    useEffect(() => {
        setLayout(null);
        log('spotlight subscribing to anchorKey=', step.anchor_key);
        return digiaAnchorRegistry.subscribe(step.anchor_key, (l) => {
            log('spotlight got layout anchorKey=', step.anchor_key, l);
            setLayout(l);
        });
    }, [step.anchor_key]);

    const dismiss = useCallback(() => {
        request.onExperienceEvent({ type: 'dismissed' });
        digiaGuideController.cancel(request.payloadId);
    }, [request]);

    const next = useCallback(() => stepIndex < config.steps.length - 1 ? setStepIndex(stepIndex + 1) : dismiss(), [stepIndex, config.steps.length, dismiss]);
    const prev = useCallback(() => { if (stepIndex > 0) setStepIndex(stepIndex - 1); }, [stepIndex]);

    const pad = step.highlight_padding;
    const cutoutX = layout ? layout.pageX - pad : 0;
    const cutoutY = layout ? layout.pageY - pad : 0;
    const cutoutW = layout ? layout.width + pad * 2 : 0;
    const cutoutH = layout ? layout.height + pad * 2 : 0;
    const screenPath = `M0,0 L${screenW},0 L${screenW},${screenH} L0,${screenH} Z`;
    const cutoutPath = layout
        ? buildCutoutPath(cutoutX, cutoutY, cutoutW, cutoutH, step.highlight_corner_radius, step.highlight_shape)
        : '';

    return (
        <Modal transparent statusBarTranslucent animationType="fade" visible>
            <View style={StyleSheet.absoluteFill} pointerEvents="box-none">
                {layout && (
                    <>
                        {/* Scrim with cutout */}
                        <Svg
                            width={screenW}
                            height={screenH}
                            style={StyleSheet.absoluteFill}
                            pointerEvents="none"
                        >
                            <Path
                                fillRule="evenodd"
                                d={`${screenPath} ${cutoutPath}`}
                                fill={step.overlay_color}
                                fillOpacity={step.overlay_opacity}
                            />
                            {step.highlight_glow_width > 0 && (
                                <Path
                                    d={cutoutPath}
                                    fill="none"
                                    stroke={step.highlight_glow_color}
                                    strokeWidth={step.highlight_glow_width}
                                />
                            )}
                        </Svg>
                        {/* Tap outside to dismiss */}
                        <Pressable style={StyleSheet.absoluteFill} onPress={dismiss} />
                        {/* Callout */}
                        <SpotlightCallout
                            step={step}
                            layout={layout}
                            onNext={next}
                            onBack={prev}
                            onSkip={dismiss}
                        />
                    </>
                )}
            </View>
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
            log('guide start campaignKey=', event.request.campaignKey, 'type=', event.request.config.template_type);
            setActiveRequest(event.request);
        });
    }, []);

    if (!activeRequest) return null;

    switch (activeRequest.config.template_type) {
        case 'tooltip':
            log('rendering TooltipOverlay');
            return <TooltipOverlay request={activeRequest} config={activeRequest.config} />;
        case 'spotlight':
            log('rendering SpotlightOverlay');
            return <SpotlightOverlay request={activeRequest} config={activeRequest.config} />;
    }
}

// ─── DigiaHost — drop-in sibling, no children or wrapper needed ──────────────
// DigiaHostView (native Compose overlay for nudges) is intentionally NOT
// included here — it intercepts Android touches when placed as a sibling.

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
