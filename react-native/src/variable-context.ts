import type { VariableSchema } from './variable-schema';

export type VariableContext = {
    values: Record<string, string>;
    types: Record<string, 'string' | 'number'>;
};

// CEP → fallbackValue → "". A non-empty CEP value wins; empty falls through.
export const buildVariableContext = (
    schemas: VariableSchema[],
    cepVars: Record<string, string> | undefined,
): VariableContext => {
    const values: Record<string, string> = {};
    const types: Record<string, 'string' | 'number'> = {};

    for (const schema of schemas) {
        const cep = cepVars?.[schema.name];
        values[schema.name] = (cep !== undefined && cep !== '') ? cep : schema.fallbackValue;
        types[schema.name] = schema.type;
    }

    return { values, types };
};
