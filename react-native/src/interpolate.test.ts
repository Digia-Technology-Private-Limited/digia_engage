// @ts-nocheck — no test-runner types installed; run with jest or vitest
import { buildVariableContext, interpolate, normalizeVariable } from './interpolate';
import vectors from './expr-test-vectors.json';

type VarEntry = { type: 'string' | 'number'; value?: string };
type FallbackMap = Record<string, string>;
type VarsMap = Record<string, VarEntry | FallbackMap>;

interface TestVector {
    description: string;
    expr: string;
    vars: VarsMap;
    out: string;
}

describe('interpolate — expression test vectors', () => {
    (vectors as TestVector[]).forEach((vector) => {
        it(vector.description, () => {
            const { vars, expr, out } = vector;

            // Extract _fallback map (if present)
            const fallbackEntry = vars['_fallback'] as FallbackMap | undefined;

            // Build schemas and cepVars, skipping the _fallback meta-key
            const schemas = Object.entries(vars)
                .filter(([key]) => key !== '_fallback')
                .map(([name, entry]) => {
                    const varEntry = entry as VarEntry;
                    return normalizeVariable({
                        name,
                        type: varEntry.type,
                        fallbackValue: fallbackEntry?.[name] ?? '',
                    });
                });

            const cepVars: Record<string, string> = {};
            for (const [name, entry] of Object.entries(vars)) {
                if (name === '_fallback') continue;
                const varEntry = entry as VarEntry;
                if (varEntry.value !== undefined) {
                    cepVars[name] = varEntry.value;
                }
            }

            const context = buildVariableContext(schemas, cepVars);
            const result = interpolate(`{{ ${expr} }}`, context);

            expect(result).toBe(out);
        });
    });
});
