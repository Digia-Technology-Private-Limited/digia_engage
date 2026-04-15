/**
 * DigiaHostView
 *
 * Transparent full-screen overlay that hosts Digia dialogs and bottom sheets.
 * Place at the root of your component tree so overlays stack above all content.
 */

import React from 'react';
import {
    Platform,
    StyleSheet,
    requireNativeComponent,
    type StyleProp,
    type ViewStyle,
} from 'react-native';

interface DigiaHostViewProps {
    style?: StyleProp<ViewStyle>;
}

interface NativeDigiaHostViewProps {
    style?: ViewStyle;
    pointerEvents?: 'auto' | 'none' | 'box-none' | 'box-only';
}

const NativeDigiaHostView =
    Platform.OS === 'android' || Platform.OS === 'ios'
        ? requireNativeComponent<NativeDigiaHostViewProps>('DigiaHostView')
        : null;

export function DigiaHostView({ style }: DigiaHostViewProps) {
    if ((Platform.OS === 'android' || Platform.OS === 'ios') && NativeDigiaHostView) {
        return (
            <NativeDigiaHostView
                pointerEvents="none"
                style={StyleSheet.flatten([StyleSheet.absoluteFillObject, style])}
            />
        );
    }

    return null;
}
