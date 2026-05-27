/**
 * DigiaSlotView
 *
 * Renders inline campaign content at a placement position.
 * Auto-sizes to match native content height via `onContentSizeChange`;
 * pass an explicit `height` in `style` to fix the size instead.
 */

import React, { useCallback, useEffect, useState } from 'react';
import {
    DeviceEventEmitter,
    Platform,
    StyleSheet,
    requireNativeComponent,
    type StyleProp,
    type ViewStyle,
} from 'react-native';

interface DigiaSlotViewProps {
    placementKey: string;
    style?: StyleProp<ViewStyle>;
}

interface NativeDigiaSlotViewProps extends DigiaSlotViewProps {
    onContentSizeChange?: (event: { nativeEvent: { height: number; width: number } }) => void;
    collapsable?: boolean;
}

const NativeDigiaSlotView =
    Platform.OS === 'android' || Platform.OS === 'ios'
        ? requireNativeComponent<NativeDigiaSlotViewProps>('DigiaSlotView')
        : null;

export function DigiaSlotView({ placementKey, style }: DigiaSlotViewProps) {
    const [contentHeight, setContentHeight] = useState(0);
    const [contentWidth, setContentWidth] = useState<number | null>(null);

    useEffect(() => {
        console.log('[DigiaSlotView:debug] mounted placementKey=' + placementKey);
        const sub = DeviceEventEmitter.addListener(
            'digiaSlotWidth',
            (data: { slotKey: string; width: number | null }) => {
                console.log('[DigiaSlotView:debug] digiaSlotWidth event received', JSON.stringify(data), 'myKey=' + placementKey, 'match=' + (data.slotKey === placementKey));
                if (data.slotKey === placementKey) {
                    setContentWidth(data.width && data.width > 0 ? data.width : null);
                }
            },
        );
        return () => sub.remove();
    }, [placementKey]);

    const onContentSizeChange = useCallback(
        (event: { nativeEvent: { height: number; width: number } }) => {
            const h = event.nativeEvent.height ?? 0;
            const w = event.nativeEvent.width ?? 0;
            console.log('[DigiaSlotView:debug] onContentSizeChange placementKey=' + placementKey + ' h=' + h + ' w=' + w);
            setContentHeight(Math.max(0, h));
            setContentWidth(w > 0 ? w : null);
        },
        [placementKey],
    );

    if ((Platform.OS === 'android' || Platform.OS === 'ios') && NativeDigiaSlotView) {
        const flatStyle = StyleSheet.flatten(style) || {};
        const hasExplicitHeight = flatStyle.height !== undefined;
        const resolvedWidth = contentWidth ?? '100%';

        if (hasExplicitHeight) {
            return (
                <NativeDigiaSlotView
                    placementKey={placementKey}
                    style={[{ width: resolvedWidth }, style]}
                    {...(Platform.OS === 'android' ? { collapsable: false } : {})}
                />
            );
        }

        // 1dp bootstrap ensures a real layout pass before any campaign arrives.
        const bootstrapHeight = Math.max(contentHeight, 1);

        return (
            <NativeDigiaSlotView
                placementKey={placementKey}
                style={[{ width: resolvedWidth, height: bootstrapHeight }, style]}
                onContentSizeChange={onContentSizeChange}
                {...(Platform.OS === 'android' ? { collapsable: false } : {})}
            />
        );
    }

    return null;
}
