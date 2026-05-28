import type { InAppBrowserAdapter } from './types';

// Lazy accessor — throws at first access if the package is not installed,
// giving the developer a clear error at init time rather than silently
// falling back to Linking.openURL.
type InAppBrowserModule = { open(url: string, options?: Record<string, unknown>): Promise<void> };
let _module: InAppBrowserModule | null = null;

const loadModule = (): InAppBrowserModule => {
    if (_module) return _module;
    try {
        // Dynamic require at runtime — not available at build time.
        // eslint-disable-next-line @typescript-eslint/no-var-requires
        const pkg = (globalThis as any).require('react-native-inappbrowser-reborn');
        if (!pkg) throw new Error('not installed');
        _module = (pkg.InAppBrowser ?? pkg.default?.InAppBrowser ?? pkg) as InAppBrowserModule;
        return _module;
    } catch {
        throw new Error(
            '[Digia] defaultInAppBrowser requires react-native-inappbrowser-reborn. ' +
            'Run: npm install react-native-inappbrowser-reborn',
        );
    }
};

export const defaultInAppBrowser: InAppBrowserAdapter = {
    open: async (url: string) => {
        const browser = loadModule();
        await browser!.open(url);
    },
};
