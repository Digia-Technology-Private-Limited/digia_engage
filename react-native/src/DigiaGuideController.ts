import type { GuideLifecycleEvent } from './types';
import type { TemplateConfig } from './templateTypes';
import type { VariableMap } from './interpolate';

export interface DigiaGuideRequest {
    payloadId: string;
    campaignKey: string;
    /** Digia backend UUID for this campaign (from the campaign store _id field). */
    campaignId: string;
    variables?: VariableMap;
    config: TemplateConfig;
    onExperienceEvent: (event: GuideLifecycleEvent) => void;
}

type DigiaGuideControllerEvent =
    | { type: 'start'; request: DigiaGuideRequest }
    | { type: 'cancel'; payloadId: string };

type DigiaGuideListener = (event: DigiaGuideControllerEvent) => void;

class DigiaGuideController {
    private _listener: DigiaGuideListener | null = null;
    private _queue: DigiaGuideRequest[] = [];
    private _activeRequest: DigiaGuideRequest | null = null;

    subscribe(listener: DigiaGuideListener): () => void {
        this._listener = listener;
        if (this._queue.length > 0) {
            const request = this._queue.shift()!;
            this._activeRequest = request;
            listener({ type: 'start', request });
        }
        return () => { if (this._listener === listener) this._listener = null; };
    }

    start(request: DigiaGuideRequest): boolean {
        if (!this._listener) {
            this._queue.push(request);
            return false;
        }
        this._activeRequest = request;
        this._listener({ type: 'start', request });
        return true;
    }

    cancel(payloadId: string): void {
        this._queue = this._queue.filter(r => r.payloadId !== payloadId);
        if (this._activeRequest?.payloadId === payloadId) {
            this._listener?.({ type: 'cancel', payloadId });
            this._activeRequest = null;
            this._dispatchNext();
        }
    }

    private _dispatchNext(): void {
        if (this._queue.length === 0 || !this._listener) return;
        const request = this._queue.shift()!;
        this._activeRequest = request;
        this._listener({ type: 'start', request });
    }
}

export const digiaGuideController = new DigiaGuideController();
