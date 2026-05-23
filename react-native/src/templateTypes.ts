export type Action =
    | { type: 'dismiss';   label: string; style: 'primary' | 'secondary' | 'ghost' }
    | { type: 'next';      label: string; style: 'primary' | 'secondary' | 'ghost' }
    | { type: 'prev';      label: string; style: 'primary' | 'secondary' | 'ghost' }
    | { type: 'deep_link'; label: string; style: 'primary' | 'secondary' | 'ghost'; destination: string }

export type TooltipStep = {
    anchor_key: string
    title: string
    body: string
    placement: 'top' | 'bottom' | 'left' | 'right' | 'auto'
    background_color: string
    border_color: string
    border_width: number
    corner_radius: number
    shadow: boolean
    max_width: number
    padding: number
    show_arrow: boolean
    title_color: string
    title_size: number
    title_weight: '400' | '600' | '700'
    body_color: string
    body_size: number
    button_primary_background_color: string
    button_primary_text_color: string
    button_ghost_text_color: string
    actions: Action[]
}

export type SpotlightStep = {
    anchor_key: string
    title: string
    body: string
    callout_position: 'above' | 'below' | 'left' | 'right' | 'auto'
    overlay_color: string
    overlay_opacity: number
    highlight_shape: 'rect' | 'circle' | 'pill'
    highlight_corner_radius: number
    highlight_padding: number
    highlight_glow_color: string
    highlight_glow_width: number
    callout_background_color: string
    callout_corner_radius: number
    callout_max_width: number
    callout_padding: number
    callout_shadow: boolean
    callout_border_color: string
    callout_border_width: number
    title_color: string
    title_size: number
    title_weight: '400' | '600' | '700'
    body_color: string
    body_size: number
    button_primary_background_color: string
    button_primary_text_color: string
    button_ghost_text_color: string
    actions: Action[]
}

export type TooltipConfig   = { template_type: 'tooltip';   template_id: string | null; steps: TooltipStep[]   }
export type SpotlightConfig = { template_type: 'spotlight'; template_id: string | null; steps: SpotlightStep[] }
export type TemplateConfig  = TooltipConfig | SpotlightConfig
