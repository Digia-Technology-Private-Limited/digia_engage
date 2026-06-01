package com.digia.engage.internal.model

import android.graphics.Color
import org.json.JSONArray
import org.json.JSONObject

/**
 * Survey schema delivered by the **getCampaigns API** for a
 * `campaign_type == "survey"` campaign. This is a 1:1 mirror of the dashboard
 * `Survey` type (see dashboard `src/types/survey.types.ts`).
 *
 * Shape:
 * ```
 * Survey
 *   ├── blocks: content library (reusable question/content definitions)
 *   ├── nodes:  graph positions, each pointing at one block + owning branching
 *   ├── rootNodeId: entry node
 *   └── settings: display, pagination, timer, auto-advance, choose-button
 * ```
 *
 * Block-vs-node split mirrors the dashboard exactly: the same block can be
 * referenced by many nodes, each with its own branching graph.
 *
 * Naming convention: JSON uses camelCase throughout (post dashboard migration).
 */

// ── enums ───────────────────────────────────────────────────────────────────

enum class SurveyBlockType {
    // Prompts
    SINGLE_SELECT, MULTI_SELECT, RATING, NPS, REACTION,
    THIS_OR_THAT, TIER_LIST, UPVOTE,
    // Form fields
    SHORT_TEXT, LONG_TEXT, NUMBER, EMAIL, DATE,
    // Content
    WELCOME, TEXT_MEDIA, RESULT_PAGE,
    ;

    val isContent: Boolean
        get() = this == WELCOME || this == TEXT_MEDIA || this == RESULT_PAGE

    val isMultiSelect: Boolean
        get() = this == MULTI_SELECT || this == TIER_LIST || this == UPVOTE

    val isChoice: Boolean
        get() = this == SINGLE_SELECT || this == MULTI_SELECT || this == REACTION ||
            this == THIS_OR_THAT || this == TIER_LIST || this == UPVOTE

    val isText: Boolean
        get() = this == SHORT_TEXT || this == LONG_TEXT || this == NUMBER ||
            this == EMAIL || this == DATE

    /**
     * Single-pick blocks that can sensibly advance themselves once an answer
     * lands. Multi-select / text inputs always need the explicit Next CTA.
     */
    val isAutoAdvanceCandidate: Boolean
        get() = this == SINGLE_SELECT || this == RATING || this == NPS ||
            this == REACTION
}

enum class BoolOp { AND, OR }

enum class ConditionOperator {
    EQUALS, NOT_EQUALS, CONTAINS, NOT_CONTAINS,
    INCLUDES_ALL, INCLUDES_ANY, IS_EXACTLY,
    GREATER_THAN, LESS_THAN, IS_BETWEEN,
    IS_ANSWERED, IS_NOT_ANSWERED,
}

enum class BranchingType { LINEAR, BY_CONDITION, BY_PARENT }

enum class BranchTargetKind { NEXT, NODE, URL, END }

enum class MediaPosition { TOP, INLINE, BACKGROUND }

enum class AnswerLayout { ROW, COLUMN, GRID }

enum class TextSize { SM, MD, LG, XL }

enum class FontWeight { REGULAR, MEDIUM, BOLD }

enum class TextAlign { LEFT, CENTER, RIGHT }

enum class SurveyDisplayType { DIALOG, BOTTOM_SHEET }

enum class DialogWidthPreset { SMALL, MEDIUM, LARGE, CUSTOM }

enum class BottomSheetHeightMode { WRAP, HALF, FULL, CUSTOM }

enum class PaginationStyle { CONTINUOUS, SEGMENTED }

/** Nav-button layout: a horizontal row vs full-width buttons stacked top-down. */
enum class CtaLayout { INLINE, STACKED }

/** Horizontal distribution of the inline CTA buttons. */
enum class CtaArrangement { SPACE_BETWEEN, SPACE_EVENLY, CENTER, START, END }

// ── styling primitives ──────────────────────────────────────────────────────

/** Empty colorHex inherits theme default. */
data class ElementStyle(
    val size: TextSize = TextSize.MD,
    val weight: FontWeight = FontWeight.REGULAR,
    val align: TextAlign = TextAlign.LEFT,
    val colorHex: String = "",
) {
    companion object {
        fun fromJson(json: JSONObject?): ElementStyle {
            if (json == null) return ElementStyle()
            return ElementStyle(
                size = SurveyParse.textSize(json.optString("size")),
                weight = SurveyParse.fontWeight(json.optString("weight")),
                align = SurveyParse.textAlign(json.optString("align")),
                colorHex = json.optString("color", ""),
            )
        }
    }
}

data class RichText(
    val text: String,
    val style: ElementStyle = ElementStyle(),
) {
    companion object {
        fun fromJson(json: JSONObject?): RichText? {
            if (json == null) return null
            val text = json.optString("text", "")
            return RichText(text = text, style = ElementStyle.fromJson(json.optJSONObject("style")))
        }
    }
}

// ── block content ───────────────────────────────────────────────────────────

data class SurveyOption(
    val id: String,
    val label: String,
    /** Optional secondary line — surfaced when the block's `show_answer_descriptions` is on. */
    val description: String? = null,
    /** Optional thumbnail — surfaced when the block's `show_answer_media` is on. */
    val media: BlockMedia? = null,
) {
    companion object {
        fun fromJson(json: JSONObject): SurveyOption? {
            val id = json.optString("id", "").takeIf { it.isNotBlank() } ?: return null
            val label = json.optString("label", "").ifBlank { json.optString("value", id) }
            val media = json.optJSONObject("media")?.let { BlockMedia.fromJson(it) }
                ?.takeIf { it.hasUrl }
            return SurveyOption(
                id = id,
                label = label,
                description = json.optString("description", "").takeIf { it.isNotBlank() },
                media = media,
            )
        }
    }
}

data class BlockMedia(
    val url: String,
    val alt: String,
    val position: MediaPosition,
) {
    val hasUrl: Boolean get() = url.isNotBlank()

    companion object {
        val EMPTY = BlockMedia(url = "", alt = "", position = MediaPosition.TOP)

        fun fromJson(json: JSONObject?): BlockMedia {
            if (json == null) return EMPTY
            return BlockMedia(
                url = json.optString("url", ""),
                alt = json.optString("alt", ""),
                position = SurveyParse.mediaPosition(json.optString("position")),
            )
        }
    }
}

// ── branching ───────────────────────────────────────────────────────────────

/** A single test against one node's answer. */
data class Condition(
    /** null = tests the owning node's own answer; non-null = earlier node's answer. */
    val nodeId: String?,
    val operator: ConditionOperator,
    val values: List<String>,
) {
    companion object {
        fun fromJson(json: JSONObject): Condition? {
            val operator = SurveyParse.operator(json.optString("operator")) ?: return null
            return Condition(
                nodeId = json.optString("nodeId", "").takeIf { it.isNotBlank() },
                operator = operator,
                values = SurveyParse.stringArray(json.optJSONArray("values")),
            )
        }
    }
}

data class ConditionGroup(
    val operator: BoolOp,
    val conditions: List<Condition>,
) {
    companion object {
        fun fromJson(json: JSONObject?): ConditionGroup? {
            if (json == null) return null
            val conditionsArr = json.optJSONArray("conditions") ?: return null
            val conditions = SurveyParse.mapJsonArray(conditionsArr, Condition::fromJson)
            if (conditions.isEmpty()) return null
            return ConditionGroup(
                operator = SurveyParse.boolOp(json.optString("operator"), BoolOp.AND),
                conditions = conditions,
            )
        }
    }
}

data class ConditionExpr(
    val operator: BoolOp,
    val groups: List<ConditionGroup>,
) {
    companion object {
        fun fromJson(json: JSONObject?): ConditionExpr? {
            if (json == null) return null
            val groupsArr = json.optJSONArray("groups") ?: return null
            val groups = SurveyParse.mapJsonArray(groupsArr, ConditionGroup::fromJson)
            if (groups.isEmpty()) return null
            return ConditionExpr(
                operator = SurveyParse.boolOp(json.optString("operator"), BoolOp.AND),
                groups = groups,
            )
        }
    }
}

data class BranchTarget(
    val kind: BranchTargetKind,
    val nodeId: String?,
    val url: String,
) {
    companion object {
        val NEXT = BranchTarget(BranchTargetKind.NEXT, null, "")
        val END = BranchTarget(BranchTargetKind.END, null, "")

        fun fromJson(json: JSONObject?): BranchTarget {
            if (json == null) return NEXT
            val kind = SurveyParse.targetKind(json.optString("kind"))
            return BranchTarget(
                kind = kind,
                nodeId = json.optString("nodeId", "").takeIf { it.isNotBlank() },
                url = json.optString("url", ""),
            )
        }
    }
}

data class BranchRule(
    val id: String,
    val whenExpr: ConditionExpr,
    val target: BranchTarget,
) {
    companion object {
        fun fromJson(json: JSONObject): BranchRule? {
            val id = json.optString("id", "").takeIf { it.isNotBlank() } ?: return null
            val whenExpr = ConditionExpr.fromJson(json.optJSONObject("when")) ?: return null
            return BranchRule(
                id = id,
                whenExpr = whenExpr,
                target = BranchTarget.fromJson(json.optJSONObject("target")),
            )
        }
    }
}

data class NodeBranching(
    val type: BranchingType,
    val rules: List<BranchRule>,
    /** Used only for [BranchingType.BY_PARENT]. */
    val parentNodeId: String?,
    val defaultTarget: BranchTarget,
) {
    companion object {
        val LINEAR_NEXT = NodeBranching(
            type = BranchingType.LINEAR,
            rules = emptyList(),
            parentNodeId = null,
            defaultTarget = BranchTarget.NEXT,
        )

        fun fromJson(json: JSONObject?): NodeBranching {
            if (json == null) return LINEAR_NEXT
            val rules = SurveyParse.mapJsonArray(json.optJSONArray("rules"), BranchRule::fromJson)
            return NodeBranching(
                type = SurveyParse.branchingType(json.optString("type")),
                rules = rules,
                parentNodeId = json.optString("parentNodeId", "").takeIf { it.isNotBlank() },
                defaultTarget = BranchTarget.fromJson(json.optJSONObject("defaultTarget")),
            )
        }
    }
}

// ── block ───────────────────────────────────────────────────────────────────

data class SurveyBlock(
    val id: String,
    val type: SurveyBlockType,
    val title: RichText,
    val body: RichText?,
    val options: List<SurveyOption>,
    val required: Boolean,
    /** When true the block is kept in the survey but skipped at runtime (e.g. a hidden welcome screen). */
    val hidden: Boolean,
    val showMedia: Boolean,
    val media: BlockMedia,
    val showAnswerMedia: Boolean,
    val showAnswerDescriptions: Boolean,
    val shuffle: Boolean,
    val allowOther: Boolean,
    val flexibleHeight: Boolean,
    val answerLayout: AnswerLayout,
    /** NUMBER-block constraints. `null` means unbounded on that side. */
    val numberMin: Double?,
    val numberMax: Double?,
    /** Conditional visibility. When non-null, the node is skipped if it evaluates false. */
    val showWhen: ConditionExpr?,
) {
    companion object {
        fun fromJson(json: JSONObject): SurveyBlock? {
            val id = json.optString("id", "").takeIf { it.isNotBlank() } ?: return null
            val type = SurveyParse.blockType(json.optString("type")) ?: return null
            val title = RichText.fromJson(json.optJSONObject("title")) ?: RichText("")
            val options = SurveyParse.mapJsonArray(json.optJSONArray("options"), SurveyOption::fromJson)
                .ifEmpty { SurveyParse.fallbackOptions(type) }
            return SurveyBlock(
                id = id,
                type = type,
                title = title,
                body = RichText.fromJson(json.optJSONObject("body")),
                options = options,
                required = json.optBoolean("required", false),
                hidden = json.optBoolean("hidden", false),
                showMedia = json.optBoolean("showMedia", false),
                media = BlockMedia.fromJson(json.optJSONObject("media")),
                showAnswerMedia = json.optBoolean("showAnswerMedia", false),
                showAnswerDescriptions = json.optBoolean("showAnswerDescriptions", false),
                shuffle = json.optBoolean("shuffle", false),
                allowOther = json.optBoolean("allowOther", false),
                flexibleHeight = json.optBoolean("flexibleHeight", false),
                answerLayout = SurveyParse.answerLayout(json.optString("answerLayout")),
                numberMin = SurveyParse.optDouble(json, "min"),
                numberMax = SurveyParse.optDouble(json, "max"),
                showWhen = ConditionExpr.fromJson(json.optJSONObject("showWhen")),
            )
        }
    }
}

// ── node ────────────────────────────────────────────────────────────────────

data class SurveyNode(
    val id: String,
    val blockId: String,
    val branching: NodeBranching,
) {
    companion object {
        fun fromJson(json: JSONObject): SurveyNode? {
            val id = json.optString("id", "").takeIf { it.isNotBlank() } ?: return null
            val blockId = json.optString("blockId", "").takeIf { it.isNotBlank() }
                ?: return null
            return SurveyNode(
                id = id,
                blockId = blockId,
                branching = NodeBranching.fromJson(json.optJSONObject("branching")),
            )
        }
    }
}

// ── settings ────────────────────────────────────────────────────────────────

data class DialogProps(
    val width: DialogWidthPreset,
    val customWidth: Int,
    val cornerRadius: Int,
    val backdropOpacity: Float,
    val backdropDismissible: Boolean,
    val showCloseButton: Boolean,
) {
    companion object {
        val DEFAULT = DialogProps(
            width = DialogWidthPreset.MEDIUM,
            customWidth = 0,
            cornerRadius = 20,
            backdropOpacity = 0.4f,
            backdropDismissible = true,
            showCloseButton = true,
        )

        fun fromJson(json: JSONObject?): DialogProps {
            if (json == null) return DEFAULT
            return DialogProps(
                width = SurveyParse.dialogWidth(json.optString("width")),
                customWidth = json.optInt("customWidth", 0),
                cornerRadius = json.optInt("cornerRadius", 20),
                backdropOpacity = json.optDouble("backdropOpacity", 0.4).toFloat()
                    .coerceIn(0f, 1f),
                backdropDismissible = json.optBoolean("backdropDismissible", true),
                showCloseButton = json.optBoolean("showCloseButton", true),
            )
        }
    }
}

data class BottomSheetProps(
    val heightMode: BottomSheetHeightMode,
    /** Viewport-height %. Used only when [heightMode] == [BottomSheetHeightMode.CUSTOM]. */
    val customHeight: Int,
    val cornerRadius: Int,
    val showHandle: Boolean,
    val draggable: Boolean,
    val backdropDismissible: Boolean,
) {
    companion object {
        val DEFAULT = BottomSheetProps(
            heightMode = BottomSheetHeightMode.WRAP,
            customHeight = 0,
            cornerRadius = 20,
            showHandle = true,
            draggable = true,
            backdropDismissible = true,
        )

        fun fromJson(json: JSONObject?): BottomSheetProps {
            if (json == null) return DEFAULT
            return BottomSheetProps(
                heightMode = SurveyParse.sheetHeight(json.optString("heightMode")),
                customHeight = json.optInt("customHeight", 0),
                cornerRadius = json.optInt("cornerRadius", 20),
                showHandle = json.optBoolean("showHandle", true),
                draggable = json.optBoolean("draggable", true),
                backdropDismissible = json.optBoolean("backdropDismissible", true),
            )
        }
    }
}

data class SurveyDisplay(
    val type: SurveyDisplayType,
    val dialog: DialogProps,
    val bottomSheet: BottomSheetProps,
) {
    val dismissible: Boolean
        get() = when (type) {
            SurveyDisplayType.DIALOG -> dialog.backdropDismissible
            SurveyDisplayType.BOTTOM_SHEET ->
                bottomSheet.backdropDismissible || bottomSheet.draggable
        }

    companion object {
        val DEFAULT = SurveyDisplay(
            type = SurveyDisplayType.BOTTOM_SHEET,
            dialog = DialogProps.DEFAULT,
            bottomSheet = BottomSheetProps.DEFAULT,
        )

        fun fromJson(json: JSONObject?): SurveyDisplay {
            if (json == null) return DEFAULT
            return SurveyDisplay(
                type = SurveyParse.displayType(json.optString("type")),
                dialog = DialogProps.fromJson(json.optJSONObject("dialog")),
                bottomSheet = BottomSheetProps.fromJson(json.optJSONObject("bottomSheet")),
            )
        }
    }
}

data class PaginationSettings(
    val numberOfPages: Boolean,
    val progressbar: Boolean,
    val onlyShowOnQuestionBlock: Boolean,
    val backButton: Boolean,
    val paginationStyle: PaginationStyle,
) {
    companion object {
        val DEFAULT = PaginationSettings(
            numberOfPages = false,
            progressbar = true,
            onlyShowOnQuestionBlock = true,
            backButton = true,
            paginationStyle = PaginationStyle.CONTINUOUS,
        )

        fun fromJson(json: JSONObject?): PaginationSettings {
            if (json == null) return DEFAULT
            return PaginationSettings(
                numberOfPages = json.optBoolean("numberOfPages", false),
                progressbar = json.optBoolean("progressbar", true),
                onlyShowOnQuestionBlock = json.optBoolean("onlyShowOnQuestionBlock", true),
                backButton = json.optBoolean("backButton", true),
                paginationStyle = SurveyParse.paginationStyle(json.optString("paginationStyle")),
            )
        }
    }
}

data class SurveyTimerSettings(
    val enabled: Boolean,
    val pauseOnNonTimerBlock: Boolean,
    val timeLimitSeconds: Int,
    val warningAtSeconds: Int,
    val autoPauseBetweenBlocks: Boolean,
) {
    companion object {
        val DEFAULT = SurveyTimerSettings(
            enabled = false,
            pauseOnNonTimerBlock = false,
            timeLimitSeconds = 0,
            warningAtSeconds = 0,
            autoPauseBetweenBlocks = false,
        )

        fun fromJson(json: JSONObject?): SurveyTimerSettings {
            if (json == null) return DEFAULT
            return SurveyTimerSettings(
                enabled = json.optBoolean("timer", false),
                pauseOnNonTimerBlock = json.optBoolean("pauseOnNonTimerBlock", false),
                timeLimitSeconds = json.optInt("timeLimit", 0).coerceAtLeast(0),
                warningAtSeconds = json.optInt("warningAt", 0).coerceAtLeast(0),
                autoPauseBetweenBlocks = json.optBoolean("autoPauseBetweenBlocks", false),
            )
        }
    }
}

/**
 * Styling, layout, and labels for the navigation CTA buttons (Next / Back, the
 * welcome "Start", the terminal "Done"). Mirrors the dashboard `CtaSettings`.
 * Empty [bgColorHex] / [textColorHex] inherit the theme accent / white.
 */
data class CtaSettings(
    val layout: CtaLayout,
    val arrangement: CtaArrangement,
    val nextLabel: String,
    val backLabel: String,
    val doneLabel: String,
    val startLabel: String,
    val bgColorHex: String,
    val textColorHex: String,
    val cornerRadius: Int,
) {
    companion object {
        val DEFAULT = CtaSettings(
            layout = CtaLayout.STACKED,
            arrangement = CtaArrangement.SPACE_BETWEEN,
            nextLabel = "Next",
            backLabel = "Back",
            doneLabel = "Done",
            startLabel = "Start",
            bgColorHex = "",
            textColorHex = "",
            cornerRadius = 8,
        )

        fun fromJson(json: JSONObject?): CtaSettings {
            if (json == null) return DEFAULT
            return CtaSettings(
                layout = SurveyParse.ctaLayout(json.optString("layout")),
                arrangement = SurveyParse.ctaArrangement(json.optString("arrangement")),
                nextLabel = json.optString("nextLabel", "").ifBlank { DEFAULT.nextLabel },
                backLabel = json.optString("backLabel", "").ifBlank { DEFAULT.backLabel },
                doneLabel = json.optString("doneLabel", "").ifBlank { DEFAULT.doneLabel },
                startLabel = json.optString("startLabel", "").ifBlank { DEFAULT.startLabel },
                bgColorHex = json.optString("bgColor", ""),
                textColorHex = json.optString("textColor", ""),
                cornerRadius = json.optInt("cornerRadius", DEFAULT.cornerRadius).coerceIn(0, 48),
            )
        }
    }
}

data class SurveySettings(
    val pagination: PaginationSettings,
    val autoAdvance: Boolean,
    val chooseButton: Boolean,
    val cta: CtaSettings,
    val timer: SurveyTimerSettings,
    val display: SurveyDisplay,
) {
    companion object {
        val DEFAULT = SurveySettings(
            pagination = PaginationSettings.DEFAULT,
            autoAdvance = false,
            chooseButton = true,
            cta = CtaSettings.DEFAULT,
            timer = SurveyTimerSettings.DEFAULT,
            display = SurveyDisplay.DEFAULT,
        )

        fun fromJson(json: JSONObject?): SurveySettings {
            if (json == null) return DEFAULT
            return SurveySettings(
                pagination = PaginationSettings.fromJson(json.optJSONObject("pagination")),
                autoAdvance = json.optBoolean("autoAdvance", false),
                chooseButton = json.optBoolean("chooseButton", true),
                cta = CtaSettings.fromJson(json.optJSONObject("cta")),
                timer = SurveyTimerSettings.fromJson(json.optJSONObject("surveyTimer")),
                display = SurveyDisplay.fromJson(json.optJSONObject("display")),
            )
        }
    }
}

// ── theme (SDK-only convenience) ────────────────────────────────────────────

data class SurveyTheme(val accentColor: Int, val backgroundColor: Int) {
    companion object {
        private val DEFAULT_ACCENT = Color.parseColor("#2D6CDF")
        private val DEFAULT_BACKGROUND = Color.parseColor("#FFFFFF")
        val DEFAULT = SurveyTheme(DEFAULT_ACCENT, DEFAULT_BACKGROUND)

        fun fromJson(json: JSONObject?): SurveyTheme {
            if (json == null) return DEFAULT
            return SurveyTheme(
                accentColor = SurveyParse.color(json.optString("accentColor"), DEFAULT_ACCENT),
                backgroundColor = SurveyParse.color(json.optString("backgroundColor"), DEFAULT_BACKGROUND),
            )
        }
    }
}

// ── top-level model ─────────────────────────────────────────────────────────

data class SurveyConfigModel(
    val id: String,
    val name: String?,
    val blocks: List<SurveyBlock>,
    val nodes: List<SurveyNode>,
    val rootNodeId: String?,
    val settings: SurveySettings,
    val theme: SurveyTheme,
    val uiTemplateId: String?,
    val timeDelayMs: Int,
) {
    /** O(1) block lookup keyed by block id. */
    val blocksById: Map<String, SurveyBlock> = blocks.associateBy { it.id }

    fun nodeById(id: String?): SurveyNode? =
        id?.let { target -> nodes.firstOrNull { it.id == target } }

    fun blockFor(node: SurveyNode): SurveyBlock? = blocksById[node.blockId]

    fun rootNode(): SurveyNode? = nodeById(rootNodeId) ?: nodes.firstOrNull()

    /**
     * The welcome screen shown before the node flow, if present and not hidden.
     * Welcome blocks are fixed intro chrome, not graph nodes.
     */
    fun welcomeBlock(): SurveyBlock? =
        blocks.firstOrNull { it.type == SurveyBlockType.WELCOME && !it.hidden }

    companion object {
        fun fromJson(json: JSONObject, fallbackId: String): SurveyConfigModel? {
            val blocksArr = json.optJSONArray("blocks") ?: return null
            val nodesArr = json.optJSONArray("nodes") ?: return null
            val blocks = SurveyParse.mapJsonArray(blocksArr, SurveyBlock::fromJson)
            // Welcome screens are intro chrome rendered before the flow, never
            // graph nodes. Drop any legacy welcome node so the flow starts at
            // the first real block.
            val blockTypeById = blocks.associate { it.id to it.type }
            val nodes = SurveyParse.mapJsonArray(nodesArr, SurveyNode::fromJson)
                .filter { blockTypeById[it.blockId] != SurveyBlockType.WELCOME }
            if (blocks.isEmpty() || nodes.isEmpty()) return null

            val id = json.optString("id", "")
                .ifBlank { json.optString("_id", "") }
                .ifBlank { json.optString("templateId", "") }
                .ifBlank { fallbackId }

            val rootNodeId = json.optString("rootNodeId", "").takeIf { it.isNotBlank() }
            val name = json.optString("name", "")
                .ifBlank { json.optString("surveyName", "") }
                .ifBlank { json.optString("title", "") }
                .takeIf { it.isNotBlank() }

            val uiTemplateId = json.optString("uiTemplateId", "").takeIf { it.isNotBlank() }

            return SurveyConfigModel(
                id = id,
                name = name,
                blocks = blocks,
                nodes = nodes,
                rootNodeId = rootNodeId,
                settings = SurveySettings.fromJson(json.optJSONObject("settings")),
                theme = SurveyTheme.fromJson(json.optJSONObject("theme")),
                uiTemplateId = uiTemplateId,
                timeDelayMs = json.optInt("timeDelayMs", 0).coerceIn(0, 10_000),
            )
        }
    }
}

// ── parsing helpers ─────────────────────────────────────────────────────────

internal object SurveyParse {

    fun <T : Any> mapJsonArray(arr: JSONArray?, mapper: (JSONObject) -> T?): List<T> {
        if (arr == null) return emptyList()
        val out = mutableListOf<T>()
        for (i in 0 until arr.length()) {
            arr.optJSONObject(i)?.let(mapper)?.let(out::add)
        }
        return out
    }

    fun stringArray(arr: JSONArray?): List<String> {
        if (arr == null) return emptyList()
        val out = mutableListOf<String>()
        for (i in 0 until arr.length()) {
            arr.optString(i, "").takeIf { it.isNotBlank() }?.let(out::add)
        }
        return out
    }

    fun optStringSnakeOrCamel(json: JSONObject, snake: String, camel: String): String? {
        val raw = when {
            json.has(snake) && !json.isNull(snake) -> json.optString(snake, "")
            json.has(camel) && !json.isNull(camel) -> json.optString(camel, "")
            else -> ""
        }
        return raw.takeIf { it.isNotBlank() && it != "null" }
    }

    /** Read an optional numeric field; null / missing / non-numeric => null. */
    fun optDouble(json: JSONObject, key: String): Double? {
        if (!json.has(key) || json.isNull(key)) return null
        val raw = json.opt(key) ?: return null
        return when (raw) {
            is Number -> raw.toDouble()
            is String -> raw.toDoubleOrNull()
            else -> null
        }
    }

    fun fallbackOptions(type: SurveyBlockType): List<SurveyOption> =
        if (type == SurveyBlockType.REACTION) {
            listOf("🔥", "💪", "😅", "😴", "😖").mapIndexed { i, emoji ->
                SurveyOption(id = "reaction_$i", label = emoji)
            }
        } else emptyList()

    fun blockType(value: String?): SurveyBlockType? =
        when (value?.trim()?.lowercase()?.replace("-", "_")) {
            "single_select", "single_choice", "single" -> SurveyBlockType.SINGLE_SELECT
            "multi_select", "multiple_select", "multiple_choice", "multi", "multiple" ->
                SurveyBlockType.MULTI_SELECT
            "rating", "star", "likert_scale" -> SurveyBlockType.RATING
            "nps" -> SurveyBlockType.NPS
            "reaction", "smiley", "smiley_scale", "csat" -> SurveyBlockType.REACTION
            "this_or_that" -> SurveyBlockType.THIS_OR_THAT
            "tier_list" -> SurveyBlockType.TIER_LIST
            "upvote" -> SurveyBlockType.UPVOTE
            "short_text", "input", "single_input" -> SurveyBlockType.SHORT_TEXT
            "long_text", "open_text", "text" -> SurveyBlockType.LONG_TEXT
            "number", "numeric" -> SurveyBlockType.NUMBER
            "email" -> SurveyBlockType.EMAIL
            "date" -> SurveyBlockType.DATE
            "welcome" -> SurveyBlockType.WELCOME
            "text_media", "content" -> SurveyBlockType.TEXT_MEDIA
            "result_page", "thank_you", "thankyou", "completed" -> SurveyBlockType.RESULT_PAGE
            else -> null
        }

    fun operator(value: String?): ConditionOperator? =
        when (value?.trim()?.lowercase()?.replace("-", "_")) {
            "equals", "is", "equal" -> ConditionOperator.EQUALS
            "not_equals", "is_not", "not_equal" -> ConditionOperator.NOT_EQUALS
            "contains", "answer_contains" -> ConditionOperator.CONTAINS
            "not_contains", "answer_does_not_contain" -> ConditionOperator.NOT_CONTAINS
            "includes_all" -> ConditionOperator.INCLUDES_ALL
            "includes_any", "any" -> ConditionOperator.INCLUDES_ANY
            "is_exactly", "all" -> ConditionOperator.IS_EXACTLY
            "greater_than", "gt" -> ConditionOperator.GREATER_THAN
            "less_than", "lt" -> ConditionOperator.LESS_THAN
            "is_between", "between" -> ConditionOperator.IS_BETWEEN
            "is_answered", "known", "has_any_value", "question_is_answered" ->
                ConditionOperator.IS_ANSWERED
            "is_not_answered", "not_known", "question_is_not_answered" ->
                ConditionOperator.IS_NOT_ANSWERED
            else -> null
        }

    fun boolOp(value: String?, default: BoolOp): BoolOp =
        when (value?.trim()?.lowercase()) {
            "and" -> BoolOp.AND
            "or" -> BoolOp.OR
            else -> default
        }

    fun targetKind(value: String?): BranchTargetKind =
        when (value?.trim()?.lowercase()) {
            "node" -> BranchTargetKind.NODE
            "url" -> BranchTargetKind.URL
            "end" -> BranchTargetKind.END
            else -> BranchTargetKind.NEXT
        }

    fun branchingType(value: String?): BranchingType =
        when (value?.trim()?.lowercase()) {
            "by_condition" -> BranchingType.BY_CONDITION
            "by_parent" -> BranchingType.BY_PARENT
            else -> BranchingType.LINEAR
        }

    fun mediaPosition(value: String?): MediaPosition =
        when (value?.trim()?.lowercase()) {
            "inline" -> MediaPosition.INLINE
            "background" -> MediaPosition.BACKGROUND
            else -> MediaPosition.TOP
        }

    fun answerLayout(value: String?): AnswerLayout =
        when (value?.trim()?.lowercase()) {
            "row" -> AnswerLayout.ROW
            "grid" -> AnswerLayout.GRID
            else -> AnswerLayout.COLUMN
        }

    fun textSize(value: String?): TextSize =
        when (value?.trim()?.lowercase()) {
            "sm" -> TextSize.SM
            "lg" -> TextSize.LG
            "xl" -> TextSize.XL
            else -> TextSize.MD
        }

    fun fontWeight(value: String?): FontWeight =
        when (value?.trim()?.lowercase()) {
            "medium" -> FontWeight.MEDIUM
            "bold" -> FontWeight.BOLD
            else -> FontWeight.REGULAR
        }

    fun textAlign(value: String?): TextAlign =
        when (value?.trim()?.lowercase()) {
            "center" -> TextAlign.CENTER
            "right" -> TextAlign.RIGHT
            else -> TextAlign.LEFT
        }

    fun displayType(value: String?): SurveyDisplayType =
        when (value?.trim()?.lowercase()) {
            "dialog", "center" -> SurveyDisplayType.DIALOG
            else -> SurveyDisplayType.BOTTOM_SHEET
        }

    fun dialogWidth(value: String?): DialogWidthPreset =
        when (value?.trim()?.lowercase()) {
            "small" -> DialogWidthPreset.SMALL
            "large" -> DialogWidthPreset.LARGE
            "custom" -> DialogWidthPreset.CUSTOM
            else -> DialogWidthPreset.MEDIUM
        }

    fun sheetHeight(value: String?): BottomSheetHeightMode =
        when (value?.trim()?.lowercase()) {
            "half" -> BottomSheetHeightMode.HALF
            "full" -> BottomSheetHeightMode.FULL
            "custom" -> BottomSheetHeightMode.CUSTOM
            else -> BottomSheetHeightMode.WRAP
        }

    fun paginationStyle(value: String?): PaginationStyle =
        when (value?.trim()?.lowercase()) {
            "segmented" -> PaginationStyle.SEGMENTED
            else -> PaginationStyle.CONTINUOUS
        }

    fun ctaLayout(value: String?): CtaLayout =
        when (value?.trim()?.lowercase()) {
            "inline", "row" -> CtaLayout.INLINE
            else -> CtaLayout.STACKED
        }

    fun ctaArrangement(value: String?): CtaArrangement =
        when (value?.trim()?.lowercase()) {
            "space_evenly" -> CtaArrangement.SPACE_EVENLY
            "center" -> CtaArrangement.CENTER
            "start" -> CtaArrangement.START
            "end" -> CtaArrangement.END
            else -> CtaArrangement.SPACE_BETWEEN
        }

    fun color(value: String?, default: Int): Int {
        if (value.isNullOrBlank()) return default
        return try {
            Color.parseColor(value)
        } catch (_: Exception) {
            default
        }
    }
}
