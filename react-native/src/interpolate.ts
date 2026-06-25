import { evalArithmetic } from './expr-evaluator';
import { buildVariableContext, type VariableContext } from './variable-context';
import { normalizeVariable, parseVariableMap, type VariableMap, type VariableSchema } from './variable-schema';

export { buildVariableContext, normalizeVariable, parseVariableMap };
export type { VariableContext, VariableMap, VariableSchema };

const IDENTIFIER_RE = /^[a-z][a-z0-9_]*$/;
const PLACEHOLDER_OPEN = '{{';

export const interpolate = (text: string, context: VariableContext | undefined): string => {
    if (!text.includes(PLACEHOLDER_OPEN)) return text;

    return text.replace(/\{\{([\s\S]*?)\}\}/g, (_match, inner: string) => {
        const trimmed = inner.trim();

        if (IDENTIFIER_RE.test(trimmed)) {
            return context?.values[trimmed] ?? '';
        }

        if (!context) return '';
        return evalArithmetic(trimmed, context) ?? '';
    });
};

// Backward-compat shim: flat map, plain substitution only (no type info).
export const interpolateVariables = (
    text: string,
    variables: VariableMap | undefined,
): string => {
    if (!variables) return text;
    return interpolate(text, { values: { ...variables }, types: {} });
};
