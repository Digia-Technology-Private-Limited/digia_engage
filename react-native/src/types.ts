/**
 * Interface that every Digia CEP plugin must implement.
 *
 * Mirrors DigiaCEPPlugin on Android/Flutter.
 * Register plugins via `Digia.register(plugin)` — do NOT call setup() directly.
 */
export interface DigiaPlugin {
    readonly identifier: string;
    /** Called by Digia.register() — do not call manually. */
    setup(): void;
    /** Called by Digia.setCurrentScreen() — do not call manually. */
    forwardScreen(name: string): void;
    /** Called by Digia.unregister() or when tearing down the app. */
    teardown(): void;
}

/**
 * Configuration for initialising the Digia Engage SDK.
 */
export interface DigiaConfig {
    /** The API key provided by Digia. */
    apiKey: string;
    /**
     * The target environment.
     * @default 'production'
     */
    environment?: 'production' | 'sandbox';
    /**
     * Log verbosity.
     * @default 'error'
     */
    logLevel?: 'none' | 'error' | 'verbose';
}

/**
 * Arguments passed to openNavigation.
 */
export interface DigiaNavigationOptions {
    /** The starting page ID defined in your Digia DSL config. */
    startPageId?: string;
    /** Additional key/value arguments forwarded to the page. */
    pageArgs?: Record<string, string>;
}
