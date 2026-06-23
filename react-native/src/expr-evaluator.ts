import type { VariableContext } from './variable-context';

// Recursive descent over a flat token list of `number` operands and operator
// characters. Two precedence levels (`+ -` below `* /`), left-associative, with
// chained unary minus folded into the operand. No parentheses. Operands resolve
// to numbers during tokenization; a `null` anywhere collapses the result to "".

type ExprToken = number | '+' | '-' | '*' | '/';

const tokenize = (expr: string, context: VariableContext): ExprToken[] | null => {
    const tokens: ExprToken[] = [];
    let i = 0;

    while (i < expr.length) {
        const ch = expr[i];

        if (ch === ' ' || ch === '\t') {
            i++;
        } else if (ch === '+' || ch === '-' || ch === '*' || ch === '/') {
            tokens.push(ch);
            i++;
        } else if ((ch >= '0' && ch <= '9') || ch === '.') {
            let num = '';
            while (i < expr.length && ((expr[i] >= '0' && expr[i] <= '9') || expr[i] === '.')) {
                num += expr[i++];
            }
            const n = parseFloat(num);
            if (isNaN(n)) return null;
            tokens.push(n);
        } else if (ch >= 'a' && ch <= 'z') {
            let id = '';
            while (i < expr.length && ((expr[i] >= 'a' && expr[i] <= 'z') || (expr[i] >= '0' && expr[i] <= '9') || expr[i] === '_')) {
                id += expr[i++];
            }
            if (context.types[id] !== 'number') return null;
            const n = parseFloat(context.values[id] ?? '');
            if (isNaN(n)) return null;
            tokens.push(n);
        } else {
            return null;
        }
    }

    return tokens;
};

export const evalArithmetic = (expr: string, context: VariableContext): string | null => {
    const tokens = tokenize(expr, context);
    if (!tokens) return null;

    let pos = 0;
    const peek = (): ExprToken | undefined => tokens[pos];

    const factor = (): number | null => {
        let negate = false;
        while (peek() === '-') { negate = !negate; pos++; }
        const operand = peek();
        if (typeof operand !== 'number') return null;
        pos++;
        return negate ? -operand : operand;
    };

    const term = (): number | null => {
        let value = factor();
        while (value !== null && (peek() === '*' || peek() === '/')) {
            const op = tokens[pos++];
            const rhs = factor();
            if (rhs === null || (op === '/' && rhs === 0)) return null;
            value = op === '*' ? value * rhs : value / rhs;
        }
        return value;
    };

    const expression = (): number | null => {
        let value = term();
        while (value !== null && (peek() === '+' || peek() === '-')) {
            const op = tokens[pos++];
            const rhs = term();
            if (rhs === null) return null;
            value = op === '+' ? value + rhs : value - rhs;
        }
        return value;
    };

    const result = expression();
    if (result === null || pos !== tokens.length || !isFinite(result)) return null;

    const rounded = Math.round(result * 10000) / 10000;
    return rounded.toFixed(4).replace(/\.?0+$/, '');
};
