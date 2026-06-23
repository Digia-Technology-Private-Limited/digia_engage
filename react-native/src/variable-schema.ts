// ─── Variable schema + raw definition parsing ─────────────────────────────────

export type VariableMap = Record<string, string>;

export type VariableSchema = {
    name: string;
    type: 'string' | 'number';
    fallbackValue: string;
};

// ─── D29: Normalise raw variable definitions ──────────────────────────────────

export const normalizeVariable = (raw: {
    name: string;
    type?: string;
    fallbackValue?: string;
    sampleValue?: string;
}): VariableSchema => ({
    name: raw.name,
    type: raw.type === 'number' ? 'number' : 'string',
    fallbackValue: raw.fallbackValue ?? raw.sampleValue ?? '',
});

// ─── parseVariableMap (flat name → string map from a raw payload) ─────────────

export const parseVariableMap = (raw: unknown): VariableMap | undefined => {
    const source = parseVariableSource(raw);
    if (!source) return undefined;

    const variables: VariableMap = {};
    for (const [key, value] of Object.entries(source)) {
        if (!/^[A-Za-z_][A-Za-z0-9_]*$/.test(key)) continue;
        if (typeof value === 'string') variables[key] = value;
        else if (typeof value === 'number' || typeof value === 'boolean') variables[key] = String(value);
    }
    return Object.keys(variables).length > 0 ? variables : undefined;
};

const parseVariableSource = (raw: unknown): Record<string, unknown> | undefined => {
    if (!raw) return undefined;
    if (typeof raw === 'string') {
        try {
            const parsed = JSON.parse(raw);
            return isPlainObject(parsed) ? parsed : undefined;
        } catch {
            return undefined;
        }
    }
    return isPlainObject(raw) ? raw : undefined;
};

const isPlainObject = (value: unknown): value is Record<string, unknown> =>
    !!value && typeof value === 'object' && !Array.isArray(value);
