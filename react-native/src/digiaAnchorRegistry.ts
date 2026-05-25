type AnchorLayout = { pageX: number; pageY: number; width: number; height: number }
type Listener = (layout: AnchorLayout) => void

const _layouts = new Map<string, AnchorLayout>()
const _listeners = new Map<string, Listener>()

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
}

export type { AnchorLayout }
export const digiaAnchorRegistry = { setLayout, getLayout, subscribe, remove }
