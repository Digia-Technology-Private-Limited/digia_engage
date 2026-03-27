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
 * On iOS this mounts a SwiftUI `DigiaHost` via UIHostingController that
 * manages the same overlay layer above React Native content.
 * ─────────────────────────────────────────────────────────────────────────────
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

// requireNativeComponent expects a plain ViewStyle, not StyleProp.
interface NativeDigiaHostViewProps {
    style?: ViewStyle;
}

// Fabric (New Architecture) resolves view configs lazily — no UIManager
// guard needed. requireNativeComponent is called unconditionally on iOS and Android.
const NativeDigiaHostView =
    Platform.OS === 'android' || Platform.OS === 'ios'
        ? requireNativeComponent<NativeDigiaHostViewProps>('DigiaHostView')
        : null;

// ── DigiaHostView ─────────────────────────────────────────────────────────────

export function DigiaHostView({ style }: DigiaHostViewProps) {
    if ((Platform.OS === 'android' || Platform.OS === 'ios') && NativeDigiaHostView) {
        // The native DigiaHost (Compose on Android, SwiftUI on iOS) renders
        // dialogs / bottom sheets that float above the view hierarchy on their
        // own — the host view only needs to be mounted in the tree, not take up
        // any screen space.
        return (
            <NativeDigiaHostView
                style={StyleSheet.flatten([styles.host, style])}
            />
        );
    }

    // Other platforms: no-op.
    return null;
}

const styles = StyleSheet.create({
    host: {
        width: 0,
        height: 0,
        overflow: 'hidden',
    },
});
