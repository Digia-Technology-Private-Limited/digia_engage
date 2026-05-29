import type { FrequencyState } from './types';

// eslint-disable-next-line @typescript-eslint/naming-convention
const STORE_META_KEY = 'digia:freq:__meta__';

type AsyncStorageAdapter = {
    getItem(key: string): Promise<string | null>;
    setItem(key: string, value: string): Promise<void>;
    removeItem(key: string): Promise<void>;
    getAllKeys(): Promise<readonly string[]>;
    multiRemove(keys: string[]): Promise<void>;
};

let _storage: AsyncStorageAdapter | null = null;

const _loadStorage = (): AsyncStorageAdapter | null => {
    try {
        // eslint-disable-next-line @typescript-eslint/no-var-requires
        const mod = require('@react-native-async-storage/async-storage');
        return mod.default ?? mod;
    } catch {
        console.warn('[Digia] AsyncStorage unavailable — frequency state is in-memory only (resets on app restart)');
        return null;
    }
};

const getStorage = (): AsyncStorageAdapter | null => {
    if (_storage === undefined) {
        _storage = _loadStorage();
    }
    return _storage;
};

const _sessionStore = new Map<string, FrequencyState>();

const storeKey = (campaignKey: string) => `digia:freq:${campaignKey}`;

export const frequencyStore = {
    async checkProjectId(projectId: string): Promise<void> {
        const storage = getStorage();
        if (!storage) return;
        try {
            const stored = await storage.getItem(STORE_META_KEY);
            const meta = stored ? (JSON.parse(stored) as { projectId: string }) : null;
            if (meta && meta.projectId !== projectId) {
                const keys = await storage.getAllKeys();
                const digiaKeys = keys.filter((k) => k.startsWith('digia:freq:'));
                if (digiaKeys.length > 0) await storage.multiRemove([...digiaKeys]);
            }
            await storage.setItem(STORE_META_KEY, JSON.stringify({ projectId }));
        } catch {
            // non-fatal
        }
    },

    async get(campaignKey: string, isSession: boolean): Promise<FrequencyState | null> {
        if (isSession) return _sessionStore.get(campaignKey) ?? null;
        const storage = getStorage();
        if (!storage) return _sessionStore.get(campaignKey) ?? null;
        try {
            const raw = await storage.getItem(storeKey(campaignKey));
            return raw ? (JSON.parse(raw) as FrequencyState) : null;
        } catch {
            return null;
        }
    },

    async set(campaignKey: string, state: FrequencyState, isSession: boolean): Promise<void> {
        _sessionStore.set(campaignKey, state);
        if (isSession) return;
        const storage = getStorage();
        if (!storage) return;
        try {
            await storage.setItem(storeKey(campaignKey), JSON.stringify(state));
        } catch {
            // non-fatal: state already updated in-memory above
        }
    },
};
