export type Action =
    | { type: 'dismiss';   label: string; style: 'primary' | 'secondary' | 'ghost' }
    | { type: 'next';      label: string; style: 'primary' | 'secondary' | 'ghost' }
    | { type: 'prev';      label: string; style: 'primary' | 'secondary' | 'ghost' }
    | { type: 'deep_link'; label: string; style: 'primary' | 'secondary' | 'ghost'; destination: string }

export type TooltipStep = {
    anchorKey: string
    title: string
    body: string
    placement: 'top' | 'bottom' | 'left' | 'right' | 'auto'
    backgroundColor: string
    borderColor: string
    borderWidth: number
    cornerRadius: number
    shadow: boolean
    maxWidth: number
    padding: number
    showArrow: boolean
    arrowColor?: string
    arrowBorderColor?: string
    arrowSize?: number
    titleColor: string
    titleSize: number
    titleWeight: '400' | '600' | '700'
    bodyColor: string
    bodySize: number
    buttonPrimaryBackgroundColor: string
    buttonPrimaryTextColor: string
    buttonGhostTextColor: string
    actions: Action[]
}

export type SpotlightStep = {
    anchorKey: string
    title: string
    body: string
    calloutPosition: 'above' | 'below' | 'left' | 'right' | 'auto'
    calloutGap?: number
    overlayColor: string
    overlayOpacity: number
    highlightShape: 'rect' | 'circle' | 'pill'
    highlightCornerRadius: number
    highlightPadding: number
    highlightGlowColor: string
    highlightGlowWidth: number
    calloutBackgroundColor: string
    calloutCornerRadius: number
    calloutMaxWidth: number
    calloutPadding: number
    calloutShadow: boolean
    calloutBorderColor: string
    calloutBorderWidth: number
    showArrow?: boolean
    arrowColor?: string
    arrowBorderColor?: string
    arrowSize?: number
    titleColor: string
    titleSize: number
    titleWeight: '400' | '600' | '700'
    bodyColor: string
    bodySize: number
    buttonPrimaryBackgroundColor: string
    buttonPrimaryTextColor: string
    buttonGhostTextColor: string
    actions: Action[]
}

export type TooltipConfig = {
    templateType: 'tooltip'
    templateId: string | null
    steps: TooltipStep[]
    sticky?: boolean
}

export type SpotlightConfig = {
    templateType: 'spotlight'
    templateId: string | null
    steps: SpotlightStep[]
    outsideTapBehavior?: 'dismiss' | 'next' | 'nothing'
}

export type CarouselItem = { imageUrl: string; deepLink?: string }
export type CarouselIndicatorConfig = {
    showIndicator: boolean
    dotHeight: number
    dotWidth: number
    spacing: number
    dotColor: string
    activeDotColor: string
    indicatorEffectType: 'slide' | 'expanding' | 'worm' | 'scale' | 'jumping' | 'scrolling'
}
export type CarouselConfig = {
    templateType: 'carousel'
    slotKey: string
    items: CarouselItem[]
    height: number
    width?: number
    autoPlay: boolean
    autoPlayInterval: number
    animationDuration: number
    infiniteScroll: boolean
    viewportFraction: number
    indicator: CarouselIndicatorConfig
}

export type SurveyTemplateConfig = {
    templateType: 'survey'
    templateId: string
    surveyName: string
    uiTemplateId: string | null
    settings: Record<string, unknown>
    blocks: Record<string, unknown>[]
    nodes: Record<string, unknown>[]
    rootNodeId: string
}

export type TemplateConfig = TooltipConfig | SpotlightConfig | CarouselConfig | SurveyTemplateConfig
