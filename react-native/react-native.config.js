/**
 * react-native.config.js
 *
 * Configures React Native CLI auto-linking for @digia/engage-react-native.
 * This tells the RN CLI where to find the Android library module and the
 * iOS podspec so that `npx react-native link` / auto-linking works without
 * any manual steps in host app project files.
 */
module.exports = {
    dependency: {
        platforms: {
            android: {
                // Path to the Android library module (relative to this package root)
                sourceDir: './android',
                packageImportPath: 'import com.digia.engage.rn.DigiaPackage;',
                packageInstance: 'new DigiaPackage()',
            },
            ios: {
                podspecPath: './DigiaEngageReactNative.podspec',
            },
        },
    },
};
