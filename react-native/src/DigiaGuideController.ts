import type { DigiaExperienceEvent } from './types';
import type { TemplateConfig } from './templateTypes';

export interface DigiaGuideRequest {
    payloadId: string;
    campaignKey: string;
    config: TemplateConfig;
    onExperienceEvent: (event: DigiaExperienceEvent) => void;
}

type DigiaGuideControllerEvent =
    | { type: 'start'; request: DigiaGuideRequest }
    | { type: 'cancel'; payloadId: string };

type DigiaGuideListener = (event: DigiaGuideControllerEvent) => void;

class DigiaGuideController {
    private _listener: DigiaGuideListener | null = null;
    private _pendingStart: DigiaGuideRequest | null = null;
    private _activeRequest: DigiaGuideRequest | null = null;

    subscribe(listener: DigiaGuideListener): () => void {
        this._listener = listener;
        if (this._pendingStart) {
            const request = this._pendingStart;
            this._pendingStart = null;
            listener({ type: 'start', request });
        }
        return () => { if (this._listener === listener) this._listener = null; };
    }

    start(request: DigiaGuideRequest): boolean {
        this._activeRequest = request;
        if (!this._listener) {
            this._pendingStart = request;
            return false;
        }
        this._listener({ type: 'start', request });
        return true;
    }

    cancel(payloadId: string): void {
        if (this._pendingStart?.payloadId === payloadId) this._pendingStart = null;
        if (this._activeRequest?.payloadId === payloadId) {
            this._listener?.({ type: 'cancel', payloadId });
            this._activeRequest = null;
        }
    }

    emitExperienceEvent(event: DigiaExperienceEvent): void {
        if (!this._activeRequest) return;
        this._activeRequest.onExperienceEvent(event);
        if (event.type === 'dismissed') this._activeRequest = null;
    }
}

export const digiaGuideController = new DigiaGuideController();
