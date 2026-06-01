type AnchorLayout = { pageX: number; pageY: number; width: number; height: number }
type Listener = (layout: AnchorLayout) => void
type MeasureCallback = () => void

const _layouts = new Map<string, AnchorLayout>()
const _listeners = new Map<string, Listener>()
const _measureCallbacks = new Map<string, MeasureCallback>()

const setLayout = (key: string, layout: AnchorLayout) => {
    _layouts.set(key, layout)
    _listeners.get(key)?.(layout)
}

const getLayout = (key: string): AnchorLayout | undefined => _layouts.get(key)

const subscribe = (key: string, listener: Listener): () => void => {
    _listeners.set(key, listener)
    const existing = _layouts.get(key)
    if (existing) listener(existing)
    return () => { if (_listeners.get(key) === listener) _listeners.delete(key) }
}

const remove = (key: string) => {
    _layouts.delete(key)
    _listeners.delete(key)
    _measureCallbacks.delete(key)
}

const registerMeasure = (key: string, cb: MeasureCallback) => {
    _measureCallbacks.set(key, cb)
}

const unregisterMeasure = (key: string, cb: MeasureCallback) => {
    if (_measureCallbacks.get(key) === cb) _measureCallbacks.delete(key)
}

const remeasure = (key: string) => {
    _measureCallbacks.get(key)?.()
}

export type { AnchorLayout }
export const digiaAnchorRegistry = { setLayout, getLayout, subscribe, remove, registerMeasure, unregisterMeasure, remeasure }
