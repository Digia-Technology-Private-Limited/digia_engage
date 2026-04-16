/**
 * DigiaSlotView
 *
 * Renders inline campaign content at a placement position.
 * Auto-sizes to match native content height via `onContentSizeChange`;
 * pass an explicit `height` in `style` to fix the size instead.
 */

import React, { useCallback, useState } from 'react';
import {
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
    onContentSizeChange?: (event: { nativeEvent: { height: number } }) => void;
    collapsable?: boolean;
}

const NativeDigiaSlotView =
    Platform.OS === 'android' || Platform.OS === 'ios'
        ? requireNativeComponent<NativeDigiaSlotViewProps>('DigiaSlotView')
        : null;

export function DigiaSlotView({ placementKey, style }: DigiaSlotViewProps) {
    const [contentHeight, setContentHeight] = useState(0);

    const onContentSizeChange = useCallback(
        (event: { nativeEvent: { height: number } }) => {
            const h = event.nativeEvent.height ?? 0;
            setContentHeight(Math.max(0, h));
        },
        [],
    );

    if ((Platform.OS === 'android' || Platform.OS === 'ios') && NativeDigiaSlotView) {
        const flatStyle = StyleSheet.flatten(style) || {};
        const hasExplicitHeight = flatStyle.height !== undefined;

        if (hasExplicitHeight) {
            return (
                <NativeDigiaSlotView
                    placementKey={placementKey}
                    style={[{ width: '100%' }, style]}
                    {...(Platform.OS === 'android' ? { collapsable: false } : {})}
                />
            );
        }

        // 1dp bootstrap ensures a real layout pass before any campaign arrives.
        const bootstrapHeight = Math.max(contentHeight, 1);

        return (
            <NativeDigiaSlotView
                placementKey={placementKey}
                style={[{ width: '100%', height: bootstrapHeight }, style]}
                onContentSizeChange={onContentSizeChange}
                {...(Platform.OS === 'android' ? { collapsable: false } : {})}
            />
        );
    }

    return null;
}
