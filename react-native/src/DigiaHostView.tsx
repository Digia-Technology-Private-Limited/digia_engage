/**
 * DigiaHostView
 *
 * A transparent React Native view that attaches the Digia Android Compose
 * overlay layer (dialogs, bottom sheets) on top of your app's content.
 *
 * ─── Usage ───────────────────────────────────────────────────────────────────
 * Place `<DigiaHostView>` at the **root** of your component tree so that
 * Digia dialogs and bottom sheets can stack on top of all your content.
 *
 * ```tsx
 * import React from 'react';
 * import { StyleSheet, View } from 'react-native';
 * import { DigiaHostView } from '@digia/engage-react-native';
 *
 * export default function App() {
 *   return (
 *     <View style={styles.root}>
 *       <DigiaHostView style={StyleSheet.absoluteFill} />
 *       {/ * your navigation / app content here * /}
 *     </View>
 *   );
 * }
 *
 * const styles = StyleSheet.create({ root: { flex: 1 } });
 * ```
 *
 * On Android this mounts a Jetpack Compose `DigiaHost` composable that
 * manages dialog + bottom-sheet presentation triggered by CEP plugins.
 * On iOS the view is a transparent no-op until iOS support is implemented.
 * ─────────────────────────────────────────────────────────────────────────────
 */

import React from 'react';
import {
    Platform,
    StyleSheet,
    UIManager,
    View,
    requireNativeComponent,
    type ViewStyle,
} from 'react-native';

interface DigiaHostViewProps {
    style?: ViewStyle;
}

// ── Android ──────────────────────────────────────────────────────────────────
// The native Kotlin ViewManager registers itself under 'DigiaHostView'.
// It creates an AbstractComposeView that hosts the DigiaHost composable,
// which renders the dialog/bottom-sheet overlay layer.
//
// Guard with UIManager.hasViewManagerConfig so that in Expo Go or before the
// first `npx expo run:android` the component degrades to a transparent View
// rather than throwing "View config not found" at render time (Fabric/New Arch
// resolves the native config lazily and throws on first render, not on
// requireNativeComponent).
const _hostViewAvailable =
    Platform.OS === 'android' &&
    !!UIManager.hasViewManagerConfig?.('DigiaHostView');

const NativeDigiaHostView = _hostViewAvailable
    ? requireNativeComponent<DigiaHostViewProps>('DigiaHostView')
    : null;

// ── DigiaHostView ─────────────────────────────────────────────────────────────

export function DigiaHostView({ style }: DigiaHostViewProps) {
    if (Platform.OS === 'android' && NativeDigiaHostView) {
        return (
            <NativeDigiaHostView
                style={[StyleSheet.absoluteFill, style]}
            // pointerEvents="none" is handled inside the native view so touches
            // pass through when no overlay is visible.
            />
        );
    }

    // iOS / other platforms: transparent placeholder
    return <View style={[StyleSheet.absoluteFill, styles.transparent, style]} />;
}

const styles = StyleSheet.create({
    transparent: {
        backgroundColor: 'transparent',
    },
});
