/// Survey schema delivered by the **getCampaigns API** for a
/// `campaignType == "survey"` campaign. A 1:1 Dart port of the Android
/// `SurveyConfigModel.kt` (itself a mirror of the dashboard `Survey` type).
///
/// Shape:
/// ```
/// Survey
///   ├── blocks: content library (reusable question/content definitions)
///   ├── nodes:  graph positions, each pointing at one block + owning branching
///   ├── rootNodeId: entry node
///   └── settings: display, pagination, timer, auto-advance, choose-button
/// ```
///
/// Pure data — no Flutter imports, so the model and the branching runtime stay
/// unit-testable. Colours are kept as hex strings (or ARGB ints for the theme)
/// and parsed at render time by the UI layer.
library;

// ── enums ─────────────────────────────────────────────────────────────────────

enum SurveyBlockType {
  // Prompts
  singleSelect,
  multiSelect,
  rating,
  nps,
  npsEmoji,
  npsSmiley,
  reaction,
  thisOrThat,
  tierList,
  upvote,
  // Form fields
  shortText,
  longText,
  number,
  email,
  date,
  // Content
  welcome,
  textMedia,
  resultPage;

  bool get isContent =>
      this == welcome || this == textMedia || this == resultPage;

  bool get isMultiSelect =>
      this == multiSelect || this == tierList || this == upvote;

  /// Any of the NPS skins (numeric grid + face scales).
  bool get isNps => this == nps || this == npsEmoji || this == npsSmiley;

  bool get isChoice =>
      this == singleSelect ||
      this == multiSelect ||
      this == reaction ||
      this == thisOrThat ||
      this == tierList ||
      this == upvote;

  bool get isText =>
      this == shortText ||
      this == longText ||
      this == number ||
      this == email ||
      this == date;

  /// Single-pick blocks that can sensibly advance themselves once an answer
  /// lands. Multi-select / text inputs always need the explicit Next CTA.
  bool get isAutoAdvanceCandidate =>
      this == singleSelect || this == rating || isNps || this == reaction;
}

enum BoolOp { and, or }

enum ConditionOperator {
  equals,
  notEquals,
  contains,
  notContains,
  includesAll,
  includesAny,
  isExactly,
  greaterThan,
  lessThan,
  isBetween,
  isAnswered,
  isNotAnswered,
}

enum BranchingType { linear, byCondition, byParent }

enum BranchTargetKind { next, node, url, end }

enum MediaPosition { top, inline, background }

enum AnswerLayout { row, column, grid }

enum SurveyFontWeight { regular, medium, semibold, bold }

enum SurveyTextAlign { left, center, right }

enum SurveyDisplayType { dialog, bottomSheet }

enum DialogWidthPreset { small, medium, large, custom }

enum BottomSheetHeightMode { wrap, half, full, custom }

enum PaginationStyle { continuous, segmented }

/// Nav-button layout: a horizontal row vs full-width buttons stacked top-down.
enum CtaLayout { inline, stacked }

/// Horizontal distribution of the inline CTA buttons.
enum CtaArrangement { spaceBetween, spaceEvenly, center, start, end }

// ── styling primitives ────────────────────────────────────────────────────────

/// Empty [colorHex] inherits the theme default. [sizePx] of 0 inherits the
/// element default.
class ElementStyle {
  /// Font size in px; 0 means "inherit the element default".
  final double sizePx;
  final SurveyFontWeight weight;
  final SurveyTextAlign align;
  final String colorHex;

  const ElementStyle({
    this.sizePx = 0,
    this.weight = SurveyFontWeight.regular,
    this.align = SurveyTextAlign.left,
    this.colorHex = '',
  });

  static ElementStyle fromJson(Map<String, dynamic>? json) {
    if (json == null) return const ElementStyle();
    return ElementStyle(
      sizePx: (SurveyParse.optDouble(json, 'size') ?? 0).clamp(0, double.infinity).toDouble(),
      weight: SurveyParse.fontWeight(json['weight'] as String?),
      align: SurveyParse.textAlign(json['align'] as String?),
      colorHex: SurveyParse.str(json, 'color'),
    );
  }
}

class RichText {
  final String text;
  final ElementStyle style;

  const RichText(this.text, {this.style = const ElementStyle()});

  static RichText? fromJson(Map<String, dynamic>? json) {
    if (json == null) return null;
    return RichText(
      SurveyParse.str(json, 'text'),
      style: ElementStyle.fromJson(SurveyParse.obj(json, 'style')),
    );
  }
}

// ── block content ─────────────────────────────────────────────────────────────

class SurveyOption {
  final String id;
  final String label;

  /// Optional secondary line — surfaced when `showAnswerDescriptions` is on.
  final String? description;

  /// Optional thumbnail — surfaced when `showAnswerMedia` is on.
  final BlockMedia? media;

  const SurveyOption({
    required this.id,
    required this.label,
    this.description,
    this.media,
  });

  static SurveyOption? fromJson(Map<String, dynamic> json) {
    final id = SurveyParse.str(json, 'id');
    if (id.isEmpty) return null;
    final label =
        SurveyParse.str(json, 'label').isNotEmpty ? SurveyParse.str(json, 'label') : SurveyParse.str(json, 'value', id);
    final mediaJson = SurveyParse.obj(json, 'media');
    final media = mediaJson == null ? null : BlockMedia.fromJson(mediaJson);
    final description = SurveyParse.str(json, 'description');
    return SurveyOption(
      id: id,
      label: label,
      description: description.isEmpty ? null : description,
      media: (media != null && media.hasUrl) ? media : null,
    );
  }
}

class BlockMedia {
  final String url;
  final String alt;
  final MediaPosition position;
  final String boxFit;

  const BlockMedia({
    required this.url,
    required this.alt,
    required this.position,
    this.boxFit = 'cover',
  });

  bool get hasUrl => url.isNotEmpty;

  static const empty =
      BlockMedia(url: '', alt: '', position: MediaPosition.top);

  static BlockMedia fromJson(Map<String, dynamic>? json) {
    if (json == null) return empty;
    return BlockMedia(
      url: SurveyParse.str(json, 'url'),
      alt: SurveyParse.str(json, 'alt'),
      position: SurveyParse.mediaPosition(json['position'] as String?),
      boxFit: SurveyParse.str(json, 'boxFit', 'cover'),
    );
  }
}

// ── NPS styling ───────────────────────────────────────────────────────────────

/// One value per sentiment band: detractors 0–6, passives 7–8, promoters 9–10.
class NpsSentiment {
  final String detractors;
  final String passives;
  final String promoters;

  const NpsSentiment({
    required this.detractors,
    required this.passives,
    required this.promoters,
  });

  static NpsSentiment fromJson(Map<String, dynamic>? json, NpsSentiment defaults) {
    if (json == null) return defaults;
    String pick(String key, String def) {
      final v = SurveyParse.str(json, key);
      return v.isEmpty ? def : v;
    }

    return NpsSentiment(
      detractors: pick('detractors', defaults.detractors),
      passives: pick('passives', defaults.passives),
      promoters: pick('promoters', defaults.promoters),
    );
  }
}

/// One editable point on a face scale (`nps_emoji` / `nps_smiley`).
class NpsFace {
  final String emoji;
  final String label;

  const NpsFace(this.emoji, this.label);

  static NpsFace? fromJson(Map<String, dynamic> json) {
    final emoji = SurveyParse.str(json, 'emoji');
    if (emoji.isEmpty) return null;
    return NpsFace(emoji, SurveyParse.str(json, 'label'));
  }
}

/// Tile appearance for the numeric grid (also the selected tile).
class NpsTileStyle {
  /// "square" | "circle"; circle rounds the tile fully.
  final String shape;
  final double borderRadius;
  final double borderWidth;
  final String borderColorHex;
  final String backgroundColorHex;

  const NpsTileStyle({
    required this.shape,
    required this.borderRadius,
    required this.borderWidth,
    required this.borderColorHex,
    required this.backgroundColorHex,
  });

  bool get isCircle => shape == 'circle';

  static const defaultStyle = NpsTileStyle(
    shape: 'square',
    borderRadius: 8,
    borderWidth: 1,
    borderColorHex: '',
    backgroundColorHex: '',
  );

  static NpsTileStyle fromJson(Map<String, dynamic>? json) {
    if (json == null) return defaultStyle;
    return NpsTileStyle(
      shape: SurveyParse.str(json, 'shape', 'square'),
      borderRadius: SurveyParse.optDouble(json, 'borderRadius') ?? 8,
      borderWidth: SurveyParse.optDouble(json, 'borderWidth') ?? 1,
      borderColorHex: SurveyParse.str(json, 'borderColor'),
      backgroundColorHex: SurveyParse.str(json, 'backgroundColor'),
    );
  }
}

/// Presentation styling shared across the NPS variants. Empty colours inherit.
class NpsStyle {
  final String shape;
  final double borderRadius;
  final double borderWidth;
  final String borderColorHex;
  final String backgroundColorHex;
  final NpsTileStyle selectedTile;
  final ElementStyle textStyle;
  final NpsSentiment scaleColors;
  final NpsSentiment tierEmojis;
  final String selectedBgColorHex;
  final List<NpsFace> faces;
  final bool showFaceLabels;

  const NpsStyle({
    required this.shape,
    required this.borderRadius,
    required this.borderWidth,
    required this.borderColorHex,
    required this.backgroundColorHex,
    required this.selectedTile,
    required this.textStyle,
    required this.scaleColors,
    required this.tierEmojis,
    required this.selectedBgColorHex,
    required this.faces,
    required this.showFaceLabels,
  });

  bool get isCircle => shape == 'circle';

  static const defaultScale =
      NpsSentiment(detractors: '#F2675A', passives: '#D7C928', promoters: '#55B82E');
  static const defaultTiers =
      NpsSentiment(detractors: '😡', passives: '😐', promoters: '😍');

  static NpsStyle? fromJson(Map<String, dynamic>? json) {
    if (json == null) return null;
    String orDefault(String key, String def) {
      final v = SurveyParse.str(json, key);
      return v.isEmpty ? def : v;
    }

    return NpsStyle(
      shape: SurveyParse.str(json, 'shape', 'square'),
      borderRadius: SurveyParse.optDouble(json, 'borderRadius') ?? 8,
      borderWidth: SurveyParse.optDouble(json, 'borderWidth') ?? 1,
      borderColorHex: orDefault('borderColor', '#E4E6EB'),
      backgroundColorHex: orDefault('backgroundColor', '#F4F5F8'),
      selectedTile: NpsTileStyle.fromJson(SurveyParse.obj(json, 'selectedTile')),
      textStyle: ElementStyle.fromJson(SurveyParse.obj(json, 'textStyle')),
      scaleColors: NpsSentiment.fromJson(SurveyParse.obj(json, 'scaleColors'), defaultScale),
      tierEmojis: NpsSentiment.fromJson(SurveyParse.obj(json, 'tierEmojis'), defaultTiers),
      selectedBgColorHex: orDefault('selectedBgColor', '#FFFFFF'),
      faces: SurveyParse.mapArray(SurveyParse.list(json, 'faces'), NpsFace.fromJson),
      showFaceLabels: SurveyParse.boolean(json, 'showFaceLabels', true),
    );
  }
}

// ── branching ─────────────────────────────────────────────────────────────────

/// A single test against one node's answer.
class Condition {
  /// null = tests the owning node's own answer; non-null = earlier node's.
  final String? nodeId;
  final ConditionOperator operator;
  final List<String> values;

  const Condition({
    required this.nodeId,
    required this.operator,
    required this.values,
  });

  static Condition? fromJson(Map<String, dynamic> json) {
    final operator = SurveyParse.operator(json['operator'] as String?);
    if (operator == null) return null;
    return Condition(
      nodeId: SurveyParse.optId(json, 'nodeId'),
      operator: operator,
      values: SurveyParse.stringArray(SurveyParse.list(json, 'values')),
    );
  }
}

class ConditionGroup {
  final BoolOp operator;
  final List<Condition> conditions;

  const ConditionGroup({required this.operator, required this.conditions});

  static ConditionGroup? fromJson(Map<String, dynamic>? json) {
    if (json == null) return null;
    final arr = SurveyParse.list(json, 'conditions');
    if (arr == null) return null;
    final conditions = SurveyParse.mapArray(arr, Condition.fromJson);
    if (conditions.isEmpty) return null;
    return ConditionGroup(
      operator: SurveyParse.boolOp(json['operator'] as String?, BoolOp.and),
      conditions: conditions,
    );
  }
}

class ConditionExpr {
  final BoolOp operator;
  final List<ConditionGroup> groups;

  const ConditionExpr({required this.operator, required this.groups});

  static ConditionExpr? fromJson(Map<String, dynamic>? json) {
    if (json == null) return null;
    final arr = SurveyParse.list(json, 'groups');
    if (arr == null) return null;
    final groups = SurveyParse.mapArray(arr, ConditionGroup.fromJson);
    if (groups.isEmpty) return null;
    return ConditionExpr(
      operator: SurveyParse.boolOp(json['operator'] as String?, BoolOp.and),
      groups: groups,
    );
  }
}

class BranchTarget {
  final BranchTargetKind kind;
  final String? nodeId;
  final String url;

  const BranchTarget({required this.kind, required this.nodeId, required this.url});

  static const next = BranchTarget(kind: BranchTargetKind.next, nodeId: null, url: '');
  static const end = BranchTarget(kind: BranchTargetKind.end, nodeId: null, url: '');

  static BranchTarget fromJson(Map<String, dynamic>? json) {
    if (json == null) return next;
    return BranchTarget(
      kind: SurveyParse.targetKind(json['kind'] as String?),
      nodeId: SurveyParse.optId(json, 'nodeId'),
      url: SurveyParse.str(json, 'url'),
    );
  }
}

class BranchRule {
  final String id;
  final ConditionExpr whenExpr;
  final BranchTarget target;

  const BranchRule({required this.id, required this.whenExpr, required this.target});

  static BranchRule? fromJson(Map<String, dynamic> json) {
    final id = SurveyParse.str(json, 'id');
    if (id.isEmpty) return null;
    final whenExpr = ConditionExpr.fromJson(SurveyParse.obj(json, 'when'));
    if (whenExpr == null) return null;
    return BranchRule(
      id: id,
      whenExpr: whenExpr,
      target: BranchTarget.fromJson(SurveyParse.obj(json, 'target')),
    );
  }
}

class NodeBranching {
  final BranchingType type;
  final List<BranchRule> rules;

  /// Used only for [BranchingType.byParent].
  final String? parentNodeId;
  final BranchTarget defaultTarget;

  const NodeBranching({
    required this.type,
    required this.rules,
    required this.parentNodeId,
    required this.defaultTarget,
  });

  static const linearNext = NodeBranching(
    type: BranchingType.linear,
    rules: [],
    parentNodeId: null,
    defaultTarget: BranchTarget.next,
  );

  static NodeBranching fromJson(Map<String, dynamic>? json) {
    if (json == null) return linearNext;
    return NodeBranching(
      type: SurveyParse.branchingType(json['type'] as String?),
      rules: SurveyParse.mapArray(SurveyParse.list(json, 'rules'), BranchRule.fromJson),
      parentNodeId: SurveyParse.optId(json, 'parentNodeId'),
      defaultTarget: BranchTarget.fromJson(SurveyParse.obj(json, 'defaultTarget')),
    );
  }
}

// ── block ─────────────────────────────────────────────────────────────────────

class SurveyBlock {
  final String id;
  final SurveyBlockType type;
  final RichText title;
  final RichText? body;
  final List<SurveyOption> options;

  /// Shared text style applied to every answer option (select-style blocks).
  final ElementStyle? optionStyle;

  /// Presentation styling for the NPS variants.
  final NpsStyle? npsStyle;
  final bool required;

  /// When true the block is kept but skipped at runtime (e.g. hidden welcome).
  final bool hidden;
  final bool showMedia;
  final BlockMedia media;

  /// Show the block category tag/pill above the content.
  final bool showTag;
  final bool showAnswerMedia;
  final bool showAnswerDescriptions;
  final bool shuffle;
  final bool allowOther;
  final bool flexibleHeight;
  final AnswerLayout answerLayout;

  /// Block surface background; an empty string inherits the survey surface.
  final String backgroundColorHex;

  /// NUMBER-block constraints. `null` means unbounded on that side.
  final double? numberMin;
  final double? numberMax;

  /// Conditional visibility. When non-null, the node is skipped if it is false.
  final ConditionExpr? showWhen;

  const SurveyBlock({
    required this.id,
    required this.type,
    required this.title,
    required this.body,
    required this.options,
    required this.optionStyle,
    required this.npsStyle,
    required this.required,
    required this.hidden,
    required this.showMedia,
    required this.media,
    required this.showTag,
    required this.showAnswerMedia,
    required this.showAnswerDescriptions,
    required this.shuffle,
    required this.allowOther,
    required this.flexibleHeight,
    required this.answerLayout,
    required this.backgroundColorHex,
    required this.numberMin,
    required this.numberMax,
    required this.showWhen,
  });

  static SurveyBlock? fromJson(Map<String, dynamic> json) {
    final id = SurveyParse.str(json, 'id');
    if (id.isEmpty) return null;
    final type = SurveyParse.blockType(json['type'] as String?);
    if (type == null) return null;
    final options = SurveyParse.mapArray(SurveyParse.list(json, 'options'), SurveyOption.fromJson);
    final optionStyleJson = SurveyParse.obj(json, 'optionStyle');
    return SurveyBlock(
      id: id,
      type: type,
      title: RichText.fromJson(SurveyParse.obj(json, 'title')) ?? const RichText(''),
      body: RichText.fromJson(SurveyParse.obj(json, 'body')),
      options: options.isEmpty ? SurveyParse.fallbackOptions(type) : options,
      optionStyle: optionStyleJson == null ? null : ElementStyle.fromJson(optionStyleJson),
      npsStyle: NpsStyle.fromJson(SurveyParse.obj(json, 'npsStyle')),
      required: SurveyParse.boolean(json, 'required', false),
      hidden: SurveyParse.boolean(json, 'hidden', false),
      showMedia: SurveyParse.boolean(json, 'showMedia', false),
      media: BlockMedia.fromJson(SurveyParse.obj(json, 'media')),
      showTag: SurveyParse.boolean(json, 'showTag', true),
      showAnswerMedia: SurveyParse.boolean(json, 'showAnswerMedia', false),
      showAnswerDescriptions: SurveyParse.boolean(json, 'showAnswerDescriptions', false),
      shuffle: SurveyParse.boolean(json, 'shuffle', false),
      allowOther: SurveyParse.boolean(json, 'allowOther', false),
      flexibleHeight: SurveyParse.boolean(json, 'flexibleHeight', false),
      answerLayout: SurveyParse.answerLayout(json['answerLayout'] as String?),
      backgroundColorHex: SurveyParse.str(json, 'backgroundColor'),
      numberMin: SurveyParse.optDouble(json, 'min'),
      numberMax: SurveyParse.optDouble(json, 'max'),
      showWhen: ConditionExpr.fromJson(SurveyParse.obj(json, 'showWhen')),
    );
  }
}

// ── node ──────────────────────────────────────────────────────────────────────

class SurveyNode {
  final String id;
  final String blockId;
  final NodeBranching branching;

  const SurveyNode({required this.id, required this.blockId, required this.branching});

  static SurveyNode? fromJson(Map<String, dynamic> json) {
    final id = SurveyParse.str(json, 'id');
    if (id.isEmpty) return null;
    final blockId = SurveyParse.str(json, 'blockId');
    if (blockId.isEmpty) return null;
    return SurveyNode(
      id: id,
      blockId: blockId,
      branching: NodeBranching.fromJson(SurveyParse.obj(json, 'branching')),
    );
  }
}

// ── settings ──────────────────────────────────────────────────────────────────

class DialogProps {
  final DialogWidthPreset width;
  final int customWidth;
  final int cornerRadius;
  final double backdropOpacity;
  final bool backdropDismissible;
  final bool showCloseButton;

  const DialogProps({
    required this.width,
    required this.customWidth,
    required this.cornerRadius,
    required this.backdropOpacity,
    required this.backdropDismissible,
    required this.showCloseButton,
  });

  static const defaults = DialogProps(
    width: DialogWidthPreset.medium,
    customWidth: 0,
    cornerRadius: 20,
    backdropOpacity: 0.4,
    backdropDismissible: true,
    showCloseButton: true,
  );

  static DialogProps fromJson(Map<String, dynamic>? json) {
    if (json == null) return defaults;
    return DialogProps(
      width: SurveyParse.dialogWidth(json['width'] as String?),
      customWidth: SurveyParse.optInt(json, 'customWidth', 0),
      cornerRadius: SurveyParse.optInt(json, 'cornerRadius', 20),
      backdropOpacity:
          (SurveyParse.optDouble(json, 'backdropOpacity') ?? 0.4).clamp(0, 1).toDouble(),
      backdropDismissible: SurveyParse.boolean(json, 'backdropDismissible', true),
      showCloseButton: SurveyParse.boolean(json, 'showCloseButton', true),
    );
  }
}

class BottomSheetProps {
  final BottomSheetHeightMode heightMode;

  /// Viewport-height %. Used only when [heightMode] == custom.
  final int customHeight;
  final int cornerRadius;
  final bool showHandle;
  final bool draggable;
  final bool backdropDismissible;

  const BottomSheetProps({
    required this.heightMode,
    required this.customHeight,
    required this.cornerRadius,
    required this.showHandle,
    required this.draggable,
    required this.backdropDismissible,
  });

  static const defaults = BottomSheetProps(
    heightMode: BottomSheetHeightMode.wrap,
    customHeight: 0,
    cornerRadius: 20,
    showHandle: true,
    draggable: true,
    backdropDismissible: true,
  );

  static BottomSheetProps fromJson(Map<String, dynamic>? json) {
    if (json == null) return defaults;
    return BottomSheetProps(
      heightMode: SurveyParse.sheetHeight(json['heightMode'] as String?),
      customHeight: SurveyParse.optInt(json, 'customHeight', 0),
      cornerRadius: SurveyParse.optInt(json, 'cornerRadius', 20),
      showHandle: SurveyParse.boolean(json, 'showHandle', true),
      draggable: SurveyParse.boolean(json, 'draggable', true),
      backdropDismissible: SurveyParse.boolean(json, 'backdropDismissible', true),
    );
  }
}

class SurveyDisplay {
  final SurveyDisplayType type;
  final DialogProps dialog;
  final BottomSheetProps bottomSheet;

  const SurveyDisplay({
    required this.type,
    required this.dialog,
    required this.bottomSheet,
  });

  bool get dismissible => switch (type) {
        SurveyDisplayType.dialog => dialog.backdropDismissible,
        SurveyDisplayType.bottomSheet =>
          bottomSheet.backdropDismissible || bottomSheet.draggable,
      };

  static const defaults = SurveyDisplay(
    type: SurveyDisplayType.bottomSheet,
    dialog: DialogProps.defaults,
    bottomSheet: BottomSheetProps.defaults,
  );

  static SurveyDisplay fromJson(Map<String, dynamic>? json) {
    if (json == null) return defaults;
    return SurveyDisplay(
      type: SurveyParse.displayType(json['type'] as String?),
      dialog: DialogProps.fromJson(SurveyParse.obj(json, 'dialog')),
      bottomSheet: BottomSheetProps.fromJson(SurveyParse.obj(json, 'bottomSheet')),
    );
  }
}

/// Visual styling for the progress indicator. Empty colours inherit defaults.
class ProgressIndicatorStyle {
  final String activeColorHex;
  final String trackColorHex;
  final double height;
  final double cornerRadius;

  const ProgressIndicatorStyle({
    required this.activeColorHex,
    required this.trackColorHex,
    required this.height,
    required this.cornerRadius,
  });

  static const defaults = ProgressIndicatorStyle(
    activeColorHex: '',
    trackColorHex: '',
    height: 3,
    cornerRadius: 2,
  );

  static ProgressIndicatorStyle fromJson(Map<String, dynamic>? json) {
    if (json == null) return defaults;
    return ProgressIndicatorStyle(
      activeColorHex: SurveyParse.str(json, 'activeColor'),
      trackColorHex: SurveyParse.str(json, 'trackColor'),
      height: (SurveyParse.optDouble(json, 'height') ?? 3).clamp(1, double.infinity).toDouble(),
      cornerRadius:
          (SurveyParse.optDouble(json, 'cornerRadius') ?? 2).clamp(0, double.infinity).toDouble(),
    );
  }
}

class PaginationSettings {
  final bool numberOfPages;
  final bool progressbar;
  final bool onlyShowOnQuestionBlock;
  final bool backButton;
  final PaginationStyle paginationStyle;
  final ProgressIndicatorStyle progressIndicatorStyle;

  const PaginationSettings({
    required this.numberOfPages,
    required this.progressbar,
    required this.onlyShowOnQuestionBlock,
    required this.backButton,
    required this.paginationStyle,
    required this.progressIndicatorStyle,
  });

  static const defaults = PaginationSettings(
    numberOfPages: false,
    progressbar: true,
    onlyShowOnQuestionBlock: true,
    backButton: true,
    paginationStyle: PaginationStyle.continuous,
    progressIndicatorStyle: ProgressIndicatorStyle.defaults,
  );

  static PaginationSettings fromJson(Map<String, dynamic>? json) {
    if (json == null) return defaults;
    return PaginationSettings(
      numberOfPages: SurveyParse.boolean(json, 'numberOfPages', false),
      progressbar: SurveyParse.boolean(json, 'progressbar', true),
      onlyShowOnQuestionBlock: SurveyParse.boolean(json, 'onlyShowOnQuestionBlock', true),
      backButton: SurveyParse.boolean(json, 'backButton', true),
      paginationStyle: SurveyParse.paginationStyle(json['paginationStyle'] as String?),
      progressIndicatorStyle:
          ProgressIndicatorStyle.fromJson(SurveyParse.obj(json, 'progressIndicatorStyle')),
    );
  }
}

class SurveyTimerSettings {
  final bool enabled;
  final bool pauseOnNonTimerBlock;
  final int timeLimitSeconds;
  final int warningAtSeconds;
  final bool autoPauseBetweenBlocks;

  const SurveyTimerSettings({
    required this.enabled,
    required this.pauseOnNonTimerBlock,
    required this.timeLimitSeconds,
    required this.warningAtSeconds,
    required this.autoPauseBetweenBlocks,
  });

  static const defaults = SurveyTimerSettings(
    enabled: false,
    pauseOnNonTimerBlock: false,
    timeLimitSeconds: 0,
    warningAtSeconds: 0,
    autoPauseBetweenBlocks: false,
  );

  static SurveyTimerSettings fromJson(Map<String, dynamic>? json) {
    if (json == null) return defaults;
    return SurveyTimerSettings(
      enabled: SurveyParse.boolean(json, 'timer', false),
      pauseOnNonTimerBlock: SurveyParse.boolean(json, 'pauseOnNonTimerBlock', false),
      timeLimitSeconds: SurveyParse.optInt(json, 'timeLimit', 0).clamp(0, 1 << 30),
      warningAtSeconds: SurveyParse.optInt(json, 'warningAt', 0).clamp(0, 1 << 30),
      autoPauseBetweenBlocks: SurveyParse.boolean(json, 'autoPauseBetweenBlocks', false),
    );
  }
}

/// Styling, layout, and labels for the navigation CTA buttons.
/// Empty [bgColorHex] / [textColorHex] inherit the theme accent / white.
class CtaSettings {
  final CtaLayout layout;
  final CtaArrangement arrangement;
  final String nextLabel;
  final String backLabel;
  final String doneLabel;
  final String startLabel;
  final String bgColorHex;
  final String textColorHex;
  final int cornerRadius;

  const CtaSettings({
    required this.layout,
    required this.arrangement,
    required this.nextLabel,
    required this.backLabel,
    required this.doneLabel,
    required this.startLabel,
    required this.bgColorHex,
    required this.textColorHex,
    required this.cornerRadius,
  });

  static const defaults = CtaSettings(
    layout: CtaLayout.stacked,
    arrangement: CtaArrangement.spaceBetween,
    nextLabel: 'Next',
    backLabel: 'Back',
    doneLabel: 'Done',
    startLabel: 'Start',
    bgColorHex: '',
    textColorHex: '',
    cornerRadius: 8,
  );

  static CtaSettings fromJson(Map<String, dynamic>? json) {
    if (json == null) return defaults;
    String orDefault(String key, String def) {
      final v = SurveyParse.str(json, key);
      return v.isEmpty ? def : v;
    }

    return CtaSettings(
      layout: SurveyParse.ctaLayout(json['layout'] as String?),
      arrangement: SurveyParse.ctaArrangement(json['arrangement'] as String?),
      nextLabel: orDefault('nextLabel', defaults.nextLabel),
      backLabel: orDefault('backLabel', defaults.backLabel),
      doneLabel: orDefault('doneLabel', defaults.doneLabel),
      startLabel: orDefault('startLabel', defaults.startLabel),
      bgColorHex: SurveyParse.str(json, 'bgColor'),
      textColorHex: SurveyParse.str(json, 'textColor'),
      cornerRadius: SurveyParse.optInt(json, 'cornerRadius', defaults.cornerRadius).clamp(0, 48),
    );
  }
}

class SurveySettings {
  final PaginationSettings pagination;
  final bool autoAdvance;
  final bool chooseButton;
  final CtaSettings cta;
  final SurveyTimerSettings timer;
  final SurveyDisplay display;

  const SurveySettings({
    required this.pagination,
    required this.autoAdvance,
    required this.chooseButton,
    required this.cta,
    required this.timer,
    required this.display,
  });

  static const defaults = SurveySettings(
    pagination: PaginationSettings.defaults,
    autoAdvance: false,
    chooseButton: true,
    cta: CtaSettings.defaults,
    timer: SurveyTimerSettings.defaults,
    display: SurveyDisplay.defaults,
  );

  static SurveySettings fromJson(Map<String, dynamic>? json) {
    if (json == null) return defaults;
    return SurveySettings(
      pagination: PaginationSettings.fromJson(SurveyParse.obj(json, 'pagination')),
      autoAdvance: SurveyParse.boolean(json, 'autoAdvance', false),
      chooseButton: SurveyParse.boolean(json, 'chooseButton', true),
      cta: CtaSettings.fromJson(SurveyParse.obj(json, 'cta')),
      timer: SurveyTimerSettings.fromJson(SurveyParse.obj(json, 'surveyTimer')),
      display: SurveyDisplay.fromJson(SurveyParse.obj(json, 'display')),
    );
  }
}

// ── theme ─────────────────────────────────────────────────────────────────────

/// Theme colours as ARGB ints (kept framework-free so the model unit-tests).
class SurveyTheme {
  final int accentColor;
  final int backgroundColor;

  const SurveyTheme(this.accentColor, this.backgroundColor);

  static const _defaultAccent = 0xFF2D6CDF;
  static const _defaultBackground = 0xFFFFFFFF;
  static const defaults = SurveyTheme(_defaultAccent, _defaultBackground);

  static SurveyTheme fromJson(Map<String, dynamic>? json) {
    if (json == null) return defaults;
    return SurveyTheme(
      SurveyParse.color(json['accentColor'] as String?, _defaultAccent),
      SurveyParse.color(json['backgroundColor'] as String?, _defaultBackground),
    );
  }
}

// ── top-level model ───────────────────────────────────────────────────────────

class SurveyConfigModel {
  final String id;
  final String? name;
  final List<SurveyBlock> blocks;
  final List<SurveyNode> nodes;
  final String? rootNodeId;
  final SurveySettings settings;
  final SurveyTheme theme;
  final String? uiTemplateId;
  final int timeDelayMs;

  /// O(1) block lookup keyed by block id.
  final Map<String, SurveyBlock> blocksById;

  SurveyConfigModel({
    required this.id,
    required this.name,
    required this.blocks,
    required this.nodes,
    required this.rootNodeId,
    required this.settings,
    required this.theme,
    required this.uiTemplateId,
    required this.timeDelayMs,
  }) : blocksById = {for (final b in blocks) b.id: b};

  SurveyNode? nodeById(String? id) {
    if (id == null) return null;
    for (final n in nodes) {
      if (n.id == id) return n;
    }
    return null;
  }

  SurveyBlock? blockFor(SurveyNode node) => blocksById[node.blockId];

  SurveyNode? rootNode() => nodeById(rootNodeId) ?? (nodes.isEmpty ? null : nodes.first);

  /// The welcome screen shown before the node flow, if present and not hidden.
  /// Welcome blocks are fixed intro chrome, not graph nodes.
  SurveyBlock? welcomeBlock() {
    for (final b in blocks) {
      if (b.type == SurveyBlockType.welcome && !b.hidden) return b;
    }
    return null;
  }

  static SurveyConfigModel? fromJson(Map<String, dynamic> json, String fallbackId) {
    final blocksArr = SurveyParse.list(json, 'blocks');
    final nodesArr = SurveyParse.list(json, 'nodes');
    if (blocksArr == null || nodesArr == null) return null;
    final blocks = SurveyParse.mapArray(blocksArr, SurveyBlock.fromJson);
    // Welcome screens are intro chrome rendered before the flow, never graph
    // nodes. Drop any legacy welcome node so the flow starts at the first real
    // block.
    final blockTypeById = {for (final b in blocks) b.id: b.type};
    final nodes = SurveyParse.mapArray(nodesArr, SurveyNode.fromJson)
        .where((n) => blockTypeById[n.blockId] != SurveyBlockType.welcome)
        .toList();
    if (blocks.isEmpty || nodes.isEmpty) return null;

    String firstNonBlank(List<String> values, String fallback) {
      for (final v in values) {
        if (v.isNotEmpty) return v;
      }
      return fallback;
    }

    final id = firstNonBlank(
      [SurveyParse.str(json, 'id'), SurveyParse.str(json, '_id'), SurveyParse.str(json, 'templateId')],
      fallbackId,
    );
    final name = firstNonBlank(
      [SurveyParse.str(json, 'name'), SurveyParse.str(json, 'surveyName'), SurveyParse.str(json, 'title')],
      '',
    );

    return SurveyConfigModel(
      id: id,
      name: name.isEmpty ? null : name,
      blocks: blocks,
      nodes: nodes,
      rootNodeId: SurveyParse.optId(json, 'rootNodeId'),
      settings: SurveySettings.fromJson(SurveyParse.obj(json, 'settings')),
      theme: SurveyTheme.fromJson(SurveyParse.obj(json, 'theme')),
      uiTemplateId: SurveyParse.optId(json, 'uiTemplateId'),
      timeDelayMs: SurveyParse.optInt(json, 'timeDelayMs', 0).clamp(0, 10000),
    );
  }
}

// ── parsing helpers ───────────────────────────────────────────────────────────

/// Lenient JSON readers mirroring Android's `SurveyParse` (`JSONObject.optX`).
abstract final class SurveyParse {
  static List<T> mapArray<T>(List<dynamic>? arr, T? Function(Map<String, dynamic>) mapper) {
    if (arr == null) return const [];
    final out = <T>[];
    for (final raw in arr) {
      if (raw is Map) {
        final mapped = mapper(raw.cast<String, dynamic>());
        if (mapped != null) out.add(mapped);
      }
    }
    return out;
  }

  static List<String> stringArray(List<dynamic>? arr) {
    if (arr == null) return const [];
    final out = <String>[];
    for (final raw in arr) {
      final s = raw?.toString() ?? '';
      if (s.isNotEmpty) out.add(s);
    }
    return out;
  }

  /// Reads a string field; missing / explicit-null / "null" coerce to [fallback].
  static String str(Map<String, dynamic> json, String key, [String fallback = '']) {
    final v = json[key];
    if (v == null) return fallback;
    final s = v is String ? v : v.toString();
    return s.isEmpty ? fallback : s;
  }

  static Map<String, dynamic>? obj(Map<String, dynamic> json, String key) {
    final v = json[key];
    return v is Map ? v.cast<String, dynamic>() : null;
  }

  static List<dynamic>? list(Map<String, dynamic> json, String key) {
    final v = json[key];
    return v is List ? v : null;
  }

  /// Optional id-like field; a missing key, explicit null, or literal "null" is
  /// treated as absent (mirrors the Android `optId` "null"-string guard).
  static String? optId(Map<String, dynamic> json, String key) {
    final v = json[key];
    if (v == null) return null;
    final s = v is String ? v : v.toString();
    if (s.isEmpty || s == 'null') return null;
    return s;
  }

  static double? optDouble(Map<String, dynamic> json, String key) {
    final v = json[key];
    if (v is num) return v.toDouble();
    if (v is String) return double.tryParse(v);
    return null;
  }

  static int optInt(Map<String, dynamic> json, String key, int fallback) {
    final v = json[key];
    if (v is num) return v.toInt();
    if (v is String) return int.tryParse(v) ?? fallback;
    return fallback;
  }

  static bool boolean(Map<String, dynamic> json, String key, bool fallback) {
    final v = json[key];
    if (v is bool) return v;
    if (v is String) {
      if (v.toLowerCase() == 'true') return true;
      if (v.toLowerCase() == 'false') return false;
    }
    return fallback;
  }

  static List<SurveyOption> fallbackOptions(SurveyBlockType type) {
    if (type != SurveyBlockType.reaction) return const [];
    const emojis = ['🔥', '💪', '😅', '😴', '😖'];
    return [
      for (var i = 0; i < emojis.length; i++)
        SurveyOption(id: 'reaction_$i', label: emojis[i]),
    ];
  }

  static String? _norm(String? value) =>
      value?.trim().toLowerCase().replaceAll('-', '_');

  static SurveyBlockType? blockType(String? value) => switch (_norm(value)) {
        'single_select' || 'single_choice' || 'single' => SurveyBlockType.singleSelect,
        'multi_select' ||
        'multiple_select' ||
        'multiple_choice' ||
        'multi' ||
        'multiple' =>
          SurveyBlockType.multiSelect,
        'rating' || 'star' || 'likert_scale' => SurveyBlockType.rating,
        'nps' || 'nps_gauge' || 'nps_slider' => SurveyBlockType.nps,
        'nps_emoji' => SurveyBlockType.npsEmoji,
        'nps_smiley' => SurveyBlockType.npsSmiley,
        'reaction' || 'smiley' || 'smiley_scale' || 'csat' => SurveyBlockType.reaction,
        'this_or_that' => SurveyBlockType.thisOrThat,
        'tier_list' => SurveyBlockType.tierList,
        'upvote' => SurveyBlockType.upvote,
        'short_text' || 'input' || 'single_input' => SurveyBlockType.shortText,
        'long_text' || 'open_text' || 'text' => SurveyBlockType.longText,
        'number' || 'numeric' => SurveyBlockType.number,
        'email' => SurveyBlockType.email,
        'date' => SurveyBlockType.date,
        'welcome' => SurveyBlockType.welcome,
        'text_media' || 'content' => SurveyBlockType.textMedia,
        'result_page' || 'thank_you' || 'thankyou' || 'completed' => SurveyBlockType.resultPage,
        _ => null,
      };

  static ConditionOperator? operator(String? value) => switch (_norm(value)) {
        'equals' || 'is' || 'equal' => ConditionOperator.equals,
        'not_equals' || 'is_not' || 'not_equal' => ConditionOperator.notEquals,
        'contains' || 'answer_contains' => ConditionOperator.contains,
        'not_contains' || 'answer_does_not_contain' => ConditionOperator.notContains,
        'includes_all' => ConditionOperator.includesAll,
        'includes_any' || 'any' => ConditionOperator.includesAny,
        'is_exactly' || 'all' => ConditionOperator.isExactly,
        'greater_than' || 'gt' => ConditionOperator.greaterThan,
        'less_than' || 'lt' => ConditionOperator.lessThan,
        'is_between' || 'between' => ConditionOperator.isBetween,
        'is_answered' || 'known' || 'has_any_value' || 'question_is_answered' =>
          ConditionOperator.isAnswered,
        'is_not_answered' || 'not_known' || 'question_is_not_answered' =>
          ConditionOperator.isNotAnswered,
        _ => null,
      };

  static BoolOp boolOp(String? value, BoolOp fallback) => switch (value?.trim().toLowerCase()) {
        'and' => BoolOp.and,
        'or' => BoolOp.or,
        _ => fallback,
      };

  static BranchTargetKind targetKind(String? value) => switch (value?.trim().toLowerCase()) {
        'node' => BranchTargetKind.node,
        'url' => BranchTargetKind.url,
        'end' => BranchTargetKind.end,
        _ => BranchTargetKind.next,
      };

  static BranchingType branchingType(String? value) => switch (value?.trim().toLowerCase()) {
        'by_condition' => BranchingType.byCondition,
        'by_parent' => BranchingType.byParent,
        _ => BranchingType.linear,
      };

  static MediaPosition mediaPosition(String? value) => switch (value?.trim().toLowerCase()) {
        'inline' => MediaPosition.inline,
        'background' => MediaPosition.background,
        _ => MediaPosition.top,
      };

  static AnswerLayout answerLayout(String? value) => switch (value?.trim().toLowerCase()) {
        'row' => AnswerLayout.row,
        'grid' => AnswerLayout.grid,
        _ => AnswerLayout.column,
      };

  static SurveyFontWeight fontWeight(String? value) => switch (_norm(value)) {
        'medium' => SurveyFontWeight.medium,
        'semibold' => SurveyFontWeight.semibold,
        'bold' => SurveyFontWeight.bold,
        _ => SurveyFontWeight.regular,
      };

  static SurveyTextAlign textAlign(String? value) => switch (value?.trim().toLowerCase()) {
        'center' => SurveyTextAlign.center,
        'right' => SurveyTextAlign.right,
        _ => SurveyTextAlign.left,
      };

  static SurveyDisplayType displayType(String? value) => switch (value?.trim().toLowerCase()) {
        'dialog' || 'center' => SurveyDisplayType.dialog,
        _ => SurveyDisplayType.bottomSheet,
      };

  static DialogWidthPreset dialogWidth(String? value) => switch (value?.trim().toLowerCase()) {
        'small' => DialogWidthPreset.small,
        'large' => DialogWidthPreset.large,
        'custom' => DialogWidthPreset.custom,
        _ => DialogWidthPreset.medium,
      };

  static BottomSheetHeightMode sheetHeight(String? value) => switch (value?.trim().toLowerCase()) {
        'half' => BottomSheetHeightMode.half,
        'full' => BottomSheetHeightMode.full,
        'custom' => BottomSheetHeightMode.custom,
        _ => BottomSheetHeightMode.wrap,
      };

  static PaginationStyle paginationStyle(String? value) => switch (value?.trim().toLowerCase()) {
        'segmented' => PaginationStyle.segmented,
        _ => PaginationStyle.continuous,
      };

  static CtaLayout ctaLayout(String? value) => switch (value?.trim().toLowerCase()) {
        'inline' || 'row' => CtaLayout.inline,
        _ => CtaLayout.stacked,
      };

  static CtaArrangement ctaArrangement(String? value) => switch (value?.trim().toLowerCase()) {
        'space_evenly' => CtaArrangement.spaceEvenly,
        'center' => CtaArrangement.center,
        'start' => CtaArrangement.start,
        'end' => CtaArrangement.end,
        _ => CtaArrangement.spaceBetween,
      };

  /// Parses `#RGB` / `#RRGGBB` / `#AARRGGBB` to an ARGB int; invalid → [fallback].
  static int color(String? value, int fallback) {
    var hex = (value ?? '').trim();
    if (hex.isEmpty) return fallback;
    if (hex.startsWith('#')) hex = hex.substring(1);
    if (hex.length == 3) hex = hex.split('').map((c) => '$c$c').join();
    if (hex.length == 6) hex = 'FF$hex';
    if (hex.length != 8) return fallback;
    return int.tryParse(hex, radix: 16) ?? fallback;
  }
}
