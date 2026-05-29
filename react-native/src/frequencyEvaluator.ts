import type { FrequencyPolicy, FrequencyState, FrequencyEvalResult } from './types';

const WINDOW_MS: Record<string, number> = {
    day:   86_400_000,
    week:  7 * 86_400_000,
    month: 30 * 86_400_000,
};

export const isSessionPolicy = (policy: FrequencyPolicy): boolean =>
    policy.max_per_window?.window === 'session';

/**
 * Pure eligibility function. No side effects.
 *
 * Semantics for max_per_window { count, window }:
 *   - "count" shows are allowed, measured from first_shown_at.
 *   - Once the window duration has elapsed since first_shown_at, permanently blocked (reason: 'window').
 *   - Once shown_count >= count, permanently blocked (reason: 'max_total').
 *   - 'session' window is checked by the caller via in-memory state — same logic applies.
 */
export const evaluate = (
    policy: FrequencyPolicy,
    state: FrequencyState | null,
    now: number,
): FrequencyEvalResult => {
    if (!state) return { allow: true, reason: null };

    if (state.stopped_at !== null) {
        return { allow: false, reason: 'stopped' };
    }

    if (policy.max_total !== null && state.shown_count >= policy.max_total) {
        return { allow: false, reason: 'max_total' };
    }

    if (policy.max_per_window !== null) {
        const { count, window: win } = policy.max_per_window;
        const windowMs = WINDOW_MS[win];

        if (windowMs !== undefined && state.first_shown_at !== null) {
            if (now - state.first_shown_at > windowMs) {
                return { allow: false, reason: 'window' };
            }
        }

        if (state.shown_count >= count) {
            return { allow: false, reason: 'max_total' };
        }
    }

    return { allow: true, reason: null };
};

export const hasPolicy = (policy: FrequencyPolicy | null | undefined): policy is FrequencyPolicy =>
    policy !== null &&
    policy !== undefined &&
    (policy.max_total !== null || policy.max_per_window !== null || policy.stop_on !== null);
