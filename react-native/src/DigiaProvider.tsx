import React, { useEffect, useState } from 'react';
import {
    Dimensions,
    Pressable,
    StyleSheet,
    Text,
    View,
} from 'react-native';
import {
    CoachmarkOverlay,
    CoachmarkProvider,
    createTour,
    useCoachmark,
    type Plugin,
    type TourStep,
} from '@edwardloopez/react-native-coachmark';
import { digiaGuideController, type DigiaGuideRequest } from './DigiaGuideController';
import { digiaAnchorRegistry, type AnchorLayout } from './digiaAnchorRegistry';
import type { Action, SpotlightConfig, SpotlightStep, TooltipConfig, TooltipStep } from './templateTypes';

interface DigiaProviderProps {
    children: React.ReactNode;
}

// ─── Spotlight (coachmark library, blocking + mask) ───────────────────────────

const lifecyclePlugin: Plugin = {
    onStart: () => digiaGuideController.emitExperienceEvent({ type: 'impressed' }),
    onFinish: () => digiaGuideController.emitExperienceEvent({ type: 'dismissed' }),
};

function buildSpotlightTour(request: DigiaGuideRequest, config: SpotlightConfig) {
    const steps: TourStep[] = config.steps.map((step) => ({
        id: step.anchor_key,
        title: step.title,
        description: step.body,
        shape: step.highlight_shape,
        padding: step.highlight_padding,
        radius: step.highlight_corner_radius,
        placement: step.callout_position === 'above' ? 'top'
            : step.callout_position === 'below' ? 'bottom'
            : (step.callout_position as any),
        renderTooltip: (props: any) => <SpotlightCallout step={step} callbacks={props} />,
    }));
    return createTour(
        `digia:${request.campaignKey}:${request.payloadId}`,
        steps,
        { delay: 250, showOnce: false },
    );
}

function SpotlightCallout({ step, callbacks }: { step: SpotlightStep; callbacks: any }) {
    return (
        <View style={[
            {
                backgroundColor: step.callout_background_color,
                borderRadius: step.callout_corner_radius,
                maxWidth: step.callout_max_width,
                padding: step.callout_padding,
                borderWidth: step.callout_border_width,
                borderColor: step.callout_border_color,
            },
            step.callout_shadow && styles.shadow,
        ]}>
            <Text style={{ color: step.title_color, fontSize: step.title_size, fontWeight: step.title_weight }}>
                {step.title}
            </Text>
            {!!step.body && (
                <Text style={{ marginTop: 4, color: step.body_color, fontSize: step.body_size }}>
                    {step.body}
                </Text>
            )}
            <View style={styles.actionRow}>
                {step.actions.map((action, i) => (
                    <ActionButton key={i} action={action} step={step}
                        onPress={() => handleAction(action, { onNext: callbacks.onNext, onBack: callbacks.onBack, onSkip: callbacks.onSkip })} />
                ))}
            </View>
        </View>
    );
}

// ─── Tooltip (non-blocking, no modal, no mask) ────────────────────────────────

function TooltipRunner({ request, config }: { request: DigiaGuideRequest; config: TooltipConfig }) {
    const [stepIndex, setStepIndex] = useState(0);
    const [anchorLayout, setAnchorLayout] = useState<AnchorLayout | null>(null);
    const step = config.steps[stepIndex];

    useEffect(() => {
        setAnchorLayout(null);
        return digiaAnchorRegistry.subscribe(step.anchor_key, setAnchorLayout);
    }, [step.anchor_key]);

    const dismiss = () => {
        request.onExperienceEvent({ type: 'dismissed' });
        digiaGuideController.cancel(request.payloadId);
    };
    const next = () => stepIndex < config.steps.length - 1 ? setStepIndex(stepIndex + 1) : dismiss();
    const prev = () => { if (stepIndex > 0) setStepIndex(stepIndex - 1); };

    if (!anchorLayout) return null;

    const { height: screenH, width: screenW } = Dimensions.get('window');
    const tooltipW = Math.min(step.max_width, screenW - 32);
    const anchorCenterX = anchorLayout.pageX + anchorLayout.width / 2;
    const left = Math.max(16, Math.min(anchorCenterX - tooltipW / 2, screenW - tooltipW - 16));
    const showAbove = anchorLayout.pageY + anchorLayout.height / 2 > screenH / 2;

    return (
        <View style={StyleSheet.absoluteFill} pointerEvents="box-none">
            <View
                pointerEvents="box-none"
                style={[
                    styles.tooltipBubble,
                    {
                        left,
                        width: tooltipW,
                        backgroundColor: step.background_color,
                        borderRadius: step.corner_radius,
                        borderWidth: step.border_width,
                        borderColor: step.border_color,
                        padding: step.padding,
                    },
                    showAbove
                        ? { bottom: screenH - anchorLayout.pageY + 8 }
                        : { top: anchorLayout.pageY + anchorLayout.height + 8 },
                    step.shadow && styles.shadow,
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
                <View style={styles.actionRow}>
                    {step.actions.map((action, i) => (
                        <ActionButton key={i} action={action} step={step}
                            onPress={() => handleAction(action, { onNext: next, onBack: prev, onSkip: dismiss })} />
                    ))}
                </View>
            </View>
        </View>
    );
}

// ─── Shared helpers ───────────────────────────────────────────────────────────

function handleAction(action: Action, cb: { onNext: () => void; onBack: () => void; onSkip: () => void }) {
    switch (action.type) {
        case 'next':      cb.onNext(); break;
        case 'prev':      cb.onBack(); break;
        case 'dismiss':   cb.onSkip(); break;
        case 'deep_link': cb.onSkip(); break;
    }
}

function ActionButton({ action, step, onPress }: {
    action: Action;
    step: TooltipStep | SpotlightStep;
    onPress: () => void;
}) {
    const isPrimary = action.style === 'primary';
    return (
        <Pressable onPress={onPress} style={[styles.button, isPrimary && { backgroundColor: step.button_primary_background_color }]}>
            <Text style={{ color: isPrimary ? step.button_primary_text_color : step.button_ghost_text_color, fontSize: 13, fontWeight: '600' }}>
                {action.label}
            </Text>
        </Pressable>
    );
}

// ─── Runtime dispatcher ───────────────────────────────────────────────────────

function DigiaGuideRuntime() {
    const [activeRequest, setActiveRequest] = useState<DigiaGuideRequest | null>(null);
    const { start, stop } = useCoachmark();

    useEffect(() => {
        return digiaGuideController.subscribe((event) => {
            if (event.type === 'cancel') {
                void stop();
                setActiveRequest(null);
                return;
            }
            setActiveRequest(event.request);
            if (event.request.config.template_type === 'spotlight') {
                start(buildSpotlightTour(event.request, event.request.config));
            }
        });
    }, [start, stop]);

    if (!activeRequest) return null;

    switch (activeRequest.config.template_type) {
        case 'tooltip':   return <TooltipRunner request={activeRequest} config={activeRequest.config} />;
        case 'spotlight': return null;
    }
}

// ─── Provider ─────────────────────────────────────────────────────────────────

export function DigiaProvider({ children }: DigiaProviderProps) {
    return (
        <CoachmarkProvider plugins={[lifecyclePlugin]}>
            {children}
            <DigiaGuideRuntime />
            <CoachmarkOverlay />
        </CoachmarkProvider>
    );
}

// ─── Styles ───────────────────────────────────────────────────────────────────

const styles = StyleSheet.create({
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
