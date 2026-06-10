import 'package:cached_network_image/cached_network_image.dart';
import 'package:flutter/material.dart';

import '../survey_config.dart';
import '../survey_logic_handler.dart';
import 'survey_tokens.dart';

/// Synthetic option id for a choice question's "Other" entry.
const String otherChoiceId = '__other__';

/// Dispatches a question block to its type-specific widget. Mirrors the Android
/// `SurveyQuestionContent`. [onAnswer] is called whenever the answer changes.
class SurveyQuestionContent extends StatelessWidget {
  final SurveyBlock block;

  /// Owning node id. Used to key the stateful question widgets so the same
  /// block reused across several nodes gets isolated, freshly-hydrated state
  /// instead of leaking the previous node's selection.
  final String nodeId;
  final SurveyAnswer? answer;
  final Color accent;
  final ValueChanged<SurveyAnswer> onAnswer;

  const SurveyQuestionContent({
    required this.block,
    required this.nodeId,
    required this.answer,
    required this.accent,
    required this.onAnswer,
    super.key,
  });

  @override
  Widget build(BuildContext context) {
    switch (block.type) {
      case SurveyBlockType.rating:
        return _StarRatingQuestion(range: 5, accent: accent, answer: answer, onAnswer: onAnswer);
      case SurveyBlockType.nps:
        return _NpsQuestion(accent: accent, style: block.npsStyle, answer: answer, onAnswer: onAnswer);
      case SurveyBlockType.npsEmoji:
        return _NpsFaceQuestion(
            accent: accent, style: block.npsStyle, faceSize: 28, answer: answer, onAnswer: onAnswer);
      case SurveyBlockType.npsSmiley:
        return _NpsFaceQuestion(
            accent: accent, style: block.npsStyle, faceSize: 30, answer: answer, onAnswer: onAnswer);
      case SurveyBlockType.reaction:
        return _ReactionQuestion(block: block, accent: accent, answer: answer, onAnswer: onAnswer);
      case SurveyBlockType.thisOrThat:
        return _ThisOrThatQuestion(block: block, accent: accent, answer: answer, onAnswer: onAnswer);
      case SurveyBlockType.tierList:
        return _TierListQuestion(
            key: ValueKey('tier_$nodeId'),
            block: block,
            accent: accent,
            answer: answer,
            onAnswer: onAnswer);
      case SurveyBlockType.singleSelect:
      case SurveyBlockType.multiSelect:
      case SurveyBlockType.upvote:
        return _ChoiceCardQuestion(
            key: ValueKey('choice_$nodeId'),
            block: block,
            accent: accent,
            answer: answer,
            onAnswer: onAnswer);
      case SurveyBlockType.shortText:
        return _TextQuestion(
            key: ValueKey('text_$nodeId'),
            accent: accent,
            answer: answer,
            onAnswer: onAnswer,
            keyboard: TextInputType.text,
            singleLine: true,
            placeholder: 'Type your answer…');
      case SurveyBlockType.longText:
        return _TextQuestion(
            key: ValueKey('text_$nodeId'),
            accent: accent,
            answer: answer,
            onAnswer: onAnswer,
            keyboard: TextInputType.multiline,
            singleLine: false,
            placeholder: 'Type your answer…',
            minHeight: 100);
      case SurveyBlockType.number:
        return _TextQuestion(
            key: ValueKey('text_$nodeId'),
            accent: accent,
            answer: answer,
            onAnswer: onAnswer,
            keyboard: const TextInputType.numberWithOptions(decimal: true, signed: true),
            singleLine: true,
            placeholder: '0',
            maxWidth: 200,
            validator: (input) => _validateNumber(input, block.numberMin, block.numberMax));
      case SurveyBlockType.email:
        return _TextQuestion(
            key: ValueKey('text_$nodeId'),
            accent: accent,
            answer: answer,
            onAnswer: onAnswer,
            keyboard: TextInputType.emailAddress,
            singleLine: true,
            placeholder: 'you@example.com',
            validator: _validateEmail);
      case SurveyBlockType.date:
        return _TextQuestion(
            key: ValueKey('text_$nodeId'),
            accent: accent,
            answer: answer,
            onAnswer: onAnswer,
            keyboard: TextInputType.datetime,
            singleLine: true,
            placeholder: 'YYYY-MM-DD',
            maxWidth: 240,
            validator: _validateDate);
      case SurveyBlockType.welcome:
      case SurveyBlockType.textMedia:
      case SurveyBlockType.resultPage:
        return const SizedBox.shrink();
    }
  }
}

// ── star rating: 5 rounded tiles, fill-up style ────────────────────────────────

class _StarRatingQuestion extends StatelessWidget {
  final int range;
  final Color accent;
  final SurveyAnswer? answer;
  final ValueChanged<SurveyAnswer> onAnswer;

  const _StarRatingQuestion({
    required this.range,
    required this.accent,
    required this.answer,
    required this.onAnswer,
  });

  @override
  Widget build(BuildContext context) {
    final selected = int.tryParse(answer?.values.firstOrNull ?? '') ?? 0;
    return Row(
      children: [
        for (var i = 1; i <= range; i++) ...[
          if (i > 1) const SizedBox(width: 10),
          GestureDetector(
            onTap: () => onAnswer(SurveyAnswer(values: ['$i'])),
            child: Container(
              width: 40,
              height: 40,
              decoration: BoxDecoration(
                color: i <= selected ? accent.withValues(alpha: 0.12) : SurveyTokens.surfaceSunken,
                borderRadius: BorderRadius.circular(8),
              ),
              alignment: Alignment.center,
              child: Icon(
                Icons.star,
                size: 22,
                color: i <= selected ? accent : SurveyTokens.textTertiary,
              ),
            ),
          ),
        ],
      ],
    );
  }
}

// ── NPS: 11 square tiles, single-select ────────────────────────────────────────

NpsStyle _defaultNps() => const NpsStyle(
      shape: 'square',
      borderRadius: 8,
      borderWidth: 1,
      borderColorHex: '#E4E6EB',
      backgroundColorHex: '#F4F5F8',
      selectedTile: NpsTileStyle.defaultStyle,
      textStyle: ElementStyle(
          sizePx: 13,
          weight: SurveyFontWeight.semibold,
          align: SurveyTextAlign.center,
          colorHex: '#1A1D24'),
      scaleColors: NpsStyle.defaultScale,
      tierEmojis: NpsStyle.defaultTiers,
      selectedBgColorHex: '#FFFFFF',
      faces: [],
      showFaceLabels: true,
    );

/// Sentiment band colour for an NPS score (detractors ≤6, passives 7–8, promoters ≥9).
Color _npsBandColor(NpsStyle style, int score) {
  final hex = score <= 6
      ? style.scaleColors.detractors
      : score <= 8
          ? style.scaleColors.passives
          : style.scaleColors.promoters;
  return surveyColorOrNull(hex) ?? Colors.grey;
}

class _NpsQuestion extends StatelessWidget {
  final Color accent;
  final NpsStyle? style;
  final SurveyAnswer? answer;
  final ValueChanged<SurveyAnswer> onAnswer;

  const _NpsQuestion({
    required this.accent,
    required this.style,
    required this.answer,
    required this.onAnswer,
  });

  @override
  Widget build(BuildContext context) {
    final nps = style ?? _defaultNps();
    final selected = int.tryParse(answer?.values.firstOrNull ?? '');
    final sel = nps.selectedTile;
    final baseRadius = nps.isCircle ? 999.0 : nps.borderRadius;
    final selRadius = sel.isCircle ? 999.0 : sel.borderRadius;
    final baseBg = surveyColorOrNull(nps.backgroundColorHex) ?? Colors.transparent;
    final baseBorder = surveyColorOrNull(nps.borderColorHex) ?? SurveyTokens.border;
    final textColor = surveyColorOrNull(nps.textStyle.colorHex) ?? SurveyTokens.textPrimary;
    final textWeight = surveyFontWeight(nps.textStyle.weight);
    final textSize = nps.textStyle.sizePx > 0 ? nps.textStyle.sizePx : 13.0;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: [
            for (var i = 0; i <= 10; i++) ...[
              if (i > 0) const SizedBox(width: 5),
              Expanded(
                child: _npsTile(
                  i: i,
                  isOn: selected == i,
                  band: _npsBandColor(nps, i),
                  sel: sel,
                  nps: nps,
                  baseBg: baseBg,
                  baseBorder: baseBorder,
                  baseRadius: baseRadius,
                  selRadius: selRadius,
                  textColor: textColor,
                  textWeight: textWeight,
                  textSize: textSize,
                ),
              ),
            ],
          ],
        ),
        const SizedBox(height: 6),
        Row(
          children: const [
            Text('Not likely', style: TextStyle(color: SurveyTokens.textTertiary, fontSize: 11)),
            Spacer(),
            Text('Extremely likely',
                style: TextStyle(color: SurveyTokens.textTertiary, fontSize: 11)),
          ],
        ),
      ],
    );
  }

  Widget _npsTile({
    required int i,
    required bool isOn,
    required Color band,
    required NpsTileStyle sel,
    required NpsStyle nps,
    required Color baseBg,
    required Color baseBorder,
    required double baseRadius,
    required double selRadius,
    required Color textColor,
    required FontWeight textWeight,
    required double textSize,
  }) {
    // Selected tile takes its own style; empty colours fall back to the
    // sentiment band so the default look is preserved.
    final fill = isOn ? (surveyColorOrNull(sel.backgroundColorHex) ?? band) : baseBg;
    final borderColor = isOn ? (surveyColorOrNull(sel.borderColorHex) ?? band) : baseBorder;
    final borderWidth = isOn ? sel.borderWidth : nps.borderWidth;
    final radius = isOn ? selRadius : baseRadius;
    return GestureDetector(
      onTap: () => onAnswer(SurveyAnswer(values: ['$i'])),
      child: AspectRatio(
        aspectRatio: 1,
        child: Container(
          decoration: BoxDecoration(
            color: fill,
            borderRadius: BorderRadius.circular(radius),
            border: Border.all(color: borderColor, width: borderWidth),
          ),
          alignment: Alignment.center,
          child: Text(
            '$i',
            style: TextStyle(
              color: isOn ? Colors.white : textColor,
              fontWeight: textWeight,
              fontSize: textSize,
            ),
          ),
        ),
      ),
    );
  }
}

// ── NPS face scale: emoji / smiley rounded-square tiles, single-select ─────────

class _NpsFaceQuestion extends StatelessWidget {
  final Color accent;
  final NpsStyle? style;
  final int faceSize;
  final SurveyAnswer? answer;
  final ValueChanged<SurveyAnswer> onAnswer;

  const _NpsFaceQuestion({
    required this.accent,
    required this.style,
    required this.faceSize,
    required this.answer,
    required this.onAnswer,
  });

  @override
  Widget build(BuildContext context) {
    final nps = style ?? _defaultNps();
    final faces = nps.faces;
    final sel = nps.selectedTile;
    final baseRadius = nps.isCircle ? 999.0 : nps.borderRadius;
    final selRadius = sel.isCircle ? 999.0 : sel.borderRadius;
    final baseBg = surveyColorOrNull(nps.backgroundColorHex) ?? SurveyTokens.surfaceSunken;
    final baseBorder = surveyColorOrNull(nps.borderColorHex) ?? SurveyTokens.border;
    final labelColor = surveyColorOrNull(nps.textStyle.colorHex) ?? SurveyTokens.textPrimary;
    final labelWeight = surveyFontWeight(nps.textStyle.weight);
    final labelSize = nps.textStyle.sizePx > 0 ? nps.textStyle.sizePx : 13.0;
    final selectedValue = int.tryParse(answer?.values.firstOrNull ?? '');
    return Row(
      mainAxisAlignment: MainAxisAlignment.center,
      children: [
        for (var index = 0; index < faces.length; index++) ...[
          if (index > 0) const SizedBox(width: 10),
          _faceTile(
            face: faces[index],
            value: index + 1,
            isOn: selectedValue == index + 1,
            nps: nps,
            sel: sel,
            baseBg: baseBg,
            baseBorder: baseBorder,
            baseRadius: baseRadius,
            selRadius: selRadius,
            labelColor: labelColor,
            labelWeight: labelWeight,
            labelSize: labelSize,
          ),
        ],
      ],
    );
  }

  Widget _faceTile({
    required NpsFace face,
    required int value,
    required bool isOn,
    required NpsStyle nps,
    required NpsTileStyle sel,
    required Color baseBg,
    required Color baseBorder,
    required double baseRadius,
    required double selRadius,
    required Color labelColor,
    required FontWeight labelWeight,
    required double labelSize,
  }) {
    final fill = isOn ? (surveyColorOrNull(sel.backgroundColorHex) ?? accent.withValues(alpha: 0.12)) : baseBg;
    final borderColor = isOn ? (surveyColorOrNull(sel.borderColorHex) ?? accent) : baseBorder;
    final borderWidth = isOn ? sel.borderWidth : nps.borderWidth;
    final radius = isOn ? selRadius : baseRadius;
    return Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        GestureDetector(
          onTap: () => onAnswer(SurveyAnswer(values: ['$value'])),
          child: AnimatedScale(
            scale: isOn ? 1.1 : 1.0,
            duration: const Duration(milliseconds: 120),
            child: Container(
              width: 56,
              height: 56,
              decoration: BoxDecoration(
                color: fill,
                borderRadius: BorderRadius.circular(radius),
                border: Border.all(color: borderColor, width: borderWidth),
              ),
              alignment: Alignment.center,
              child: Text(face.emoji, style: TextStyle(fontSize: faceSize.toDouble())),
            ),
          ),
        ),
        if (nps.showFaceLabels && face.label.isNotEmpty) ...[
          const SizedBox(height: 6),
          Text(
            face.label,
            style: TextStyle(
              color: isOn ? accent : labelColor,
              fontWeight: labelWeight,
              fontSize: labelSize,
            ),
          ),
        ],
      ],
    );
  }
}

// ── reaction: large emoji circles ──────────────────────────────────────────────

class _ReactionQuestion extends StatelessWidget {
  final SurveyBlock block;
  final Color accent;
  final SurveyAnswer? answer;
  final ValueChanged<SurveyAnswer> onAnswer;

  const _ReactionQuestion({
    required this.block,
    required this.accent,
    required this.answer,
    required this.onAnswer,
  });

  @override
  Widget build(BuildContext context) {
    final selectedId = answer?.values.firstOrNull;
    return Row(
      mainAxisAlignment: MainAxisAlignment.center,
      children: [
        for (var i = 0; i < block.options.length; i++) ...[
          if (i > 0) const SizedBox(width: 10),
          _reactionTile(block.options[i], selectedId == block.options[i].id),
        ],
      ],
    );
  }

  Widget _reactionTile(SurveyOption option, bool isOn) => GestureDetector(
        onTap: () => onAnswer(SurveyAnswer(values: [option.id])),
        child: Container(
          width: 64,
          height: 64,
          decoration: BoxDecoration(
            color: isOn ? accent.withValues(alpha: 0.14) : SurveyTokens.surfaceSunken,
            shape: BoxShape.circle,
            border: Border.all(
              color: isOn ? accent : SurveyTokens.border,
              width: isOn ? 2 : 1.5,
            ),
          ),
          alignment: Alignment.center,
          child: Text(option.label, style: const TextStyle(fontSize: 32)),
        ),
      );
}

// ── this-or-that: two gradient cards ──────────────────────────────────────────

class _ThisOrThatQuestion extends StatelessWidget {
  final SurveyBlock block;
  final Color accent;
  final SurveyAnswer? answer;
  final ValueChanged<SurveyAnswer> onAnswer;

  const _ThisOrThatQuestion({
    required this.block,
    required this.accent,
    required this.answer,
    required this.onAnswer,
  });

  static const _gradients = [
    [Color(0xFFFF9966), Color(0xFFFF5E62)],
    [Color(0xFF6B6CFF), Color(0xFF4945FF)],
  ];

  @override
  Widget build(BuildContext context) {
    final options = block.options.take(2).toList();
    final selectedId = answer?.values.firstOrNull;
    return Row(
      children: [
        for (var index = 0; index < options.length; index++) ...[
          if (index > 0) const SizedBox(width: 12),
          Expanded(child: _card(options[index], index, selectedId == options[index].id)),
        ],
      ],
    );
  }

  Widget _card(SurveyOption option, int index, bool isOn) {
    final gradient = _gradients[index % _gradients.length];
    return GestureDetector(
      onTap: () => onAnswer(SurveyAnswer(values: [option.id])),
      child: AspectRatio(
        aspectRatio: 1,
        child: Container(
          decoration: BoxDecoration(
            gradient: LinearGradient(colors: gradient),
            borderRadius: BorderRadius.circular(14),
            border: Border.all(
              color: isOn ? accent : Colors.transparent,
              width: isOn ? 3 : 0,
            ),
          ),
          padding: const EdgeInsets.all(14),
          alignment: Alignment.bottomLeft,
          child: Text(
            option.label,
            style: const TextStyle(color: Colors.white, fontWeight: FontWeight.bold, fontSize: 16),
          ),
        ),
      ),
    );
  }
}

// ── tier list: S/A/B/C rows + chip rail (tap to cycle tier) ────────────────────

class _TierListQuestion extends StatefulWidget {
  final SurveyBlock block;
  final Color accent;
  final SurveyAnswer? answer;
  final ValueChanged<SurveyAnswer> onAnswer;

  const _TierListQuestion({
    required this.block,
    required this.accent,
    required this.answer,
    required this.onAnswer,
    super.key,
  });

  @override
  State<_TierListQuestion> createState() => _TierListQuestionState();
}

class _TierListQuestionState extends State<_TierListQuestion> {
  // Encoding: answer values are "tier:optionId" pairs; default tier is "-".
  static const _tiers = [
    ('S', Color(0xFFFF5E62)),
    ('A', Color(0xFFFFA351)),
    ('B', Color(0xFF5BC678)),
    ('C', Color(0xFF5089E0)),
  ];

  final Map<String, String> _placements = {};

  @override
  void initState() {
    super.initState();
    for (final pair in widget.answer?.values ?? const <String>[]) {
      final parts = pair.split(':');
      if (parts.length >= 2) _placements[parts.sublist(1).join(':')] = parts[0];
    }
  }

  void _emit() {
    final tierLabels = _tiers.map((t) => t.$1).toSet();
    final list = _placements.entries
        .where((e) => tierLabels.contains(e.value))
        .map((e) => '${e.value}:${e.key}')
        .toList();
    widget.onAnswer(SurveyAnswer(values: list));
  }

  void _cycle(String optionId) {
    final ordered = ['-', ..._tiers.map((t) => t.$1)];
    final current = _placements[optionId] ?? '-';
    final next = ordered[(ordered.indexOf(current) + 1) % ordered.length];
    setState(() => _placements[optionId] = next);
    _emit();
  }

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        for (final (label, color) in _tiers) ...[
          Padding(
            padding: const EdgeInsets.only(bottom: 6),
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Container(
                  width: 40,
                  height: 40,
                  decoration: BoxDecoration(color: color, borderRadius: BorderRadius.circular(6)),
                  alignment: Alignment.center,
                  child: Text(label,
                      style: const TextStyle(
                          color: Colors.white, fontSize: 18, fontWeight: FontWeight.w800)),
                ),
                const SizedBox(width: 6),
                Expanded(
                  child: Container(
                    constraints: const BoxConstraints(minHeight: 44),
                    decoration: BoxDecoration(
                      color: SurveyTokens.surfaceSunken,
                      borderRadius: BorderRadius.circular(6),
                      border: Border.all(color: SurveyTokens.border),
                    ),
                    padding: const EdgeInsets.all(6),
                    child: _TierChips(
                      items: widget.block.options.where((o) => _placements[o.id] == label).toList(),
                      onTap: _cycle,
                    ),
                  ),
                ),
              ],
            ),
          ),
        ],
        const SizedBox(height: 2),
        Container(
          width: double.infinity,
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(6),
            border: Border.all(color: SurveyTokens.border),
          ),
          padding: const EdgeInsets.all(10),
          child: _TierChips(
            items: widget.block.options.where((o) => (_placements[o.id] ?? '-') == '-').toList(),
            onTap: _cycle,
            placeholder: 'Tap a chip to assign a tier',
          ),
        ),
      ],
    );
  }
}

class _TierChips extends StatelessWidget {
  final List<SurveyOption> items;
  final ValueChanged<String> onTap;
  final String? placeholder;

  const _TierChips({required this.items, required this.onTap, this.placeholder});

  @override
  Widget build(BuildContext context) {
    if (items.isEmpty) {
      if (placeholder != null) {
        return Text(placeholder!,
            style: const TextStyle(color: SurveyTokens.textTertiary, fontSize: 11));
      }
      return const SizedBox.shrink();
    }
    return Wrap(
      spacing: 6,
      runSpacing: 6,
      children: [
        for (final opt in items)
          GestureDetector(
            onTap: () => onTap(opt.id),
            child: Container(
              decoration: BoxDecoration(
                color: SurveyTokens.surface,
                borderRadius: BorderRadius.circular(4),
                border: Border.all(color: SurveyTokens.border),
              ),
              padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
              child: Text(opt.label,
                  style: const TextStyle(fontSize: 12, color: SurveyTokens.textPrimary)),
            ),
          ),
      ],
    );
  }
}

// ── single/multi/upvote: marker-on-left card rows with layout support ──────────

class _ChoiceCardQuestion extends StatefulWidget {
  final SurveyBlock block;
  final Color accent;
  final SurveyAnswer? answer;
  final ValueChanged<SurveyAnswer> onAnswer;

  const _ChoiceCardQuestion({
    required this.block,
    required this.accent,
    required this.answer,
    required this.onAnswer,
    super.key,
  });

  @override
  State<_ChoiceCardQuestion> createState() => _ChoiceCardQuestionState();
}

class _ChoiceCardQuestionState extends State<_ChoiceCardQuestion> {
  final Map<String, bool> _selected = {};
  late final TextEditingController _otherText;

  bool get _multi => widget.block.type.isMultiSelect;
  bool get _otherSelected => _selected[otherChoiceId] == true;

  @override
  void initState() {
    super.initState();
    for (final v in widget.answer?.values ?? const <String>[]) {
      _selected[v] = true;
    }
    _otherText = TextEditingController(text: widget.answer?.comment ?? '');
  }

  @override
  void dispose() {
    _otherText.dispose();
    super.dispose();
  }

  void _emit() {
    final ids = _selected.entries.where((e) => e.value).map((e) => e.key).toList();
    widget.onAnswer(SurveyAnswer(
      values: ids,
      comment: _otherSelected ? _otherText.text : null,
    ));
  }

  void _toggle(String id) {
    setState(() {
      if (_multi) {
        _selected[id] = !(_selected[id] ?? false);
      } else {
        for (final k in _selected.keys.toList()) {
          _selected[k] = false;
        }
        _selected[id] = true;
      }
    });
    _emit();
  }

  @override
  Widget build(BuildContext context) {
    final options = [
      ...widget.block.options,
      if (widget.block.allowOther) const SurveyOption(id: otherChoiceId, label: 'Other…'),
    ];

    Widget card(SurveyOption option, {required bool wide}) => _ChoiceCardRow(
          option: option,
          selected: _selected[option.id] == true,
          multi: _multi,
          accent: widget.accent,
          optionStyle: widget.block.optionStyle,
          showMedia: widget.block.showAnswerMedia,
          showDescription: widget.block.showAnswerDescriptions,
          onTap: () => _toggle(option.id),
        );

    Widget layout;
    switch (widget.block.answerLayout) {
      case AnswerLayout.row:
        layout = Wrap(
          spacing: 8,
          runSpacing: 8,
          children: [
            for (final option in options)
              ConstrainedBox(
                constraints: const BoxConstraints(minWidth: 150),
                child: IntrinsicWidth(child: card(option, wide: true)),
              ),
          ],
        );
      case AnswerLayout.grid:
        layout = Column(
          children: [
            for (var i = 0; i < options.length; i += 2) ...[
              if (i > 0) const SizedBox(height: 8),
              Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Expanded(child: card(options[i], wide: true)),
                  const SizedBox(width: 8),
                  Expanded(
                    child: i + 1 < options.length
                        ? card(options[i + 1], wide: true)
                        : const SizedBox.shrink(),
                  ),
                ],
              ),
            ],
          ],
        );
      case AnswerLayout.column:
        layout = Column(
          children: [
            for (var i = 0; i < options.length; i++) ...[
              if (i > 0) const SizedBox(height: 8),
              card(options[i], wide: true),
            ],
          ],
        );
    }

    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        layout,
        if (widget.block.allowOther && _otherSelected) ...[
          const SizedBox(height: 8),
          TextField(
            controller: _otherText,
            onChanged: (_) => _emit(),
            decoration: const InputDecoration(
              hintText: 'Please specify…',
              border: OutlineInputBorder(),
            ),
          ),
        ],
      ],
    );
  }
}

class _ChoiceCardRow extends StatelessWidget {
  final SurveyOption option;
  final bool selected;
  final bool multi;
  final Color accent;
  final ElementStyle? optionStyle;
  final bool showMedia;
  final bool showDescription;
  final VoidCallback onTap;

  const _ChoiceCardRow({
    required this.option,
    required this.selected,
    required this.multi,
    required this.accent,
    required this.optionStyle,
    required this.showMedia,
    required this.showDescription,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    final markerShape = multi ? BoxShape.rectangle : BoxShape.circle;
    final markerRadius = multi ? BorderRadius.circular(4) : null;
    return GestureDetector(
      onTap: onTap,
      child: Container(
        decoration: BoxDecoration(
          color: selected ? accent.withValues(alpha: 0.08) : SurveyTokens.surface,
          borderRadius: BorderRadius.circular(10),
          border: Border.all(
            color: selected ? accent : SurveyTokens.border,
            width: 1.5,
          ),
        ),
        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          mainAxisSize: MainAxisSize.min,
          children: [
            Row(
              children: [
                Container(
                  width: 20,
                  height: 20,
                  decoration: BoxDecoration(
                    color: selected ? accent : Colors.transparent,
                    shape: markerShape,
                    borderRadius: markerRadius,
                    border: Border.all(
                      color: selected ? accent : SurveyTokens.borderStrong,
                      width: 1.5,
                    ),
                  ),
                ),
                const SizedBox(width: 12),
                if (showMedia && option.media?.hasUrl == true) ...[
                  ClipRRect(
                    borderRadius: BorderRadius.circular(4),
                    child: CachedNetworkImage(
                      imageUrl: option.media!.url,
                      width: 36,
                      height: 36,
                      fit: BoxFit.cover,
                      errorWidget: (_, __, ___) => const SizedBox(width: 36, height: 36),
                    ),
                  ),
                  const SizedBox(width: 12),
                ],
                Expanded(
                  child: Text(
                    option.label,
                    style: optionStyle?.toTextStyle(optionDefaults) ?? optionDefaults.toStyle(),
                  ),
                ),
              ],
            ),
            if (showDescription && (option.description?.isNotEmpty ?? false))
              Padding(
                padding: const EdgeInsets.only(left: 32, top: 6),
                child: Text(
                  option.description!,
                  style: const TextStyle(color: SurveyTokens.textSecondary, fontSize: 12),
                ),
              ),
          ],
        ),
      ),
    );
  }
}

// ── text inputs ────────────────────────────────────────────────────────────────

/// Validation result. A non-null [error] blocks the value from being emitted
/// (so the survey can't advance) and surfaces the message under the field.
class _InputValidation {
  final String? error;
  const _InputValidation(this.error);
}

class _TextQuestion extends StatefulWidget {
  final Color accent;
  final SurveyAnswer? answer;
  final ValueChanged<SurveyAnswer> onAnswer;
  final TextInputType keyboard;
  final bool singleLine;
  final String? placeholder;
  final double minHeight;
  final double maxWidth;
  final _InputValidation Function(String)? validator;

  const _TextQuestion({
    required this.accent,
    required this.answer,
    required this.onAnswer,
    required this.keyboard,
    required this.singleLine,
    this.placeholder,
    this.minHeight = 0,
    this.maxWidth = 0,
    this.validator,
    super.key,
  });

  @override
  State<_TextQuestion> createState() => _TextQuestionState();
}

class _TextQuestionState extends State<_TextQuestion> {
  late final TextEditingController _controller;
  String? _liveError;

  @override
  void initState() {
    super.initState();
    _controller = TextEditingController(text: widget.answer?.values.firstOrNull ?? '');
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  void _handle(String newText) {
    final trimmed = newText.trim();
    if (trimmed.isEmpty) {
      setState(() => _liveError = null);
      widget.onAnswer(const SurveyAnswer(values: []));
      return;
    }
    final validation = widget.validator?.call(trimmed);
    setState(() => _liveError = validation?.error);
    if (validation == null || validation.error == null) {
      widget.onAnswer(SurveyAnswer(values: [trimmed]));
    } else {
      // Reject invalid input: clear the answer so canAdvance stays false.
      widget.onAnswer(const SurveyAnswer(values: []));
    }
  }

  @override
  Widget build(BuildContext context) {
    Widget field = TextField(
      controller: _controller,
      onChanged: _handle,
      keyboardType: widget.keyboard,
      maxLines: widget.singleLine ? 1 : null,
      minLines: widget.singleLine ? 1 : 3,
      decoration: InputDecoration(
        hintText: widget.placeholder,
        hintStyle: const TextStyle(color: SurveyTokens.textTertiary),
        border: const OutlineInputBorder(),
        errorText: _liveError,
      ),
    );
    if (widget.maxWidth > 0) {
      field = Align(
        alignment: Alignment.centerLeft,
        child: ConstrainedBox(
          constraints: BoxConstraints(maxWidth: widget.maxWidth),
          child: field,
        ),
      );
    }
    if (widget.minHeight > 0) {
      field = ConstrainedBox(
        constraints: BoxConstraints(minHeight: widget.minHeight),
        child: field,
      );
    }
    return field;
  }
}

// ── validators ─────────────────────────────────────────────────────────────────

final _emailRegex = RegExp(r'^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$');
final _dateRegex = RegExp(r'^(\d{4})-(\d{2})-(\d{2}) ?$');

_InputValidation _validateNumber(String input, double? min, double? max) {
  final n = double.tryParse(input);
  if (n == null) return const _InputValidation('Enter a valid number');
  if (min != null && n < min) return _InputValidation('Must be at least ${_formatBound(min)}');
  if (max != null && n > max) return _InputValidation('Must be at most ${_formatBound(max)}');
  if (min != null && max != null && min > max) {
    return const _InputValidation('Invalid range configured');
  }
  return const _InputValidation(null);
}

/// Trim trailing `.0` so whole-number bounds display as "5" instead of "5.0".
String _formatBound(double v) =>
    v == v.truncateToDouble() ? v.toInt().toString() : v.toString();

_InputValidation _validateEmail(String input) => _emailRegex.hasMatch(input)
    ? const _InputValidation(null)
    : const _InputValidation('Enter a valid email address');

_InputValidation _validateDate(String input) {
  final match = _dateRegex.firstMatch(input);
  if (match == null) return const _InputValidation('Use format YYYY-MM-DD');
  final year = int.parse(match.group(1)!);
  final month = int.parse(match.group(2)!);
  final day = int.parse(match.group(3)!);
  if (month < 1 || month > 12) return const _InputValidation('Month must be 01–12');
  final maxDay = switch (month) {
    1 || 3 || 5 || 7 || 8 || 10 || 12 => 31,
    4 || 6 || 9 || 11 => 30,
    2 => ((year % 4 == 0 && year % 100 != 0) || year % 400 == 0) ? 29 : 28,
    _ => 31,
  };
  if (day < 1 || day > maxDay) return _InputValidation('Day must be 01–$maxDay');
  return const _InputValidation(null);
}
