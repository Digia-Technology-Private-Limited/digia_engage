export type VariableMap = Record<string, string>;

const PLACEHOLDER_PATTERN = /\{\{\s*([A-Za-z_][A-Za-z0-9_]*)\s*\}\}/g;

export function interpolateVariables(
    text: string,
    variables: VariableMap | undefined,
): string {
    if (!variables) return text;
    return text.replace(PLACEHOLDER_PATTERN, (match, name: string) => {
        const value = variables[name];
        return value === undefined ? '' : value;
    });
}

export function parseVariableMap(raw: unknown): VariableMap | undefined {
    const source = parseVariableSource(raw);
    if (!source) return undefined;

    const variables: VariableMap = {};
    for (const [key, value] of Object.entries(source)) {
        if (!/^[A-Za-z_][A-Za-z0-9_]*$/.test(key)) continue;
        if (typeof value === 'string') variables[key] = value;
        else if (typeof value === 'number' || typeof value === 'boolean') variables[key] = String(value);
    }
    return Object.keys(variables).length > 0 ? variables : undefined;
}

function parseVariableSource(raw: unknown): Record<string, unknown> | undefined {
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
}

function isPlainObject(value: unknown): value is Record<string, unknown> {
    return !!value && typeof value === 'object' && !Array.isArray(value);
}
