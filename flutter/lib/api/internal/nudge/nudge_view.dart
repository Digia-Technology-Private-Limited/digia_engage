import 'package:flutter/material.dart';

import 'nudge_box_decorator.dart';
import 'nudge_content.dart';
import 'nudge_node_renderer.dart';

/// Renders a nudge's content column with plain Flutter widgets — deliberately
/// NOT the DUI engine (no registry/RenderPayload/ResourceProvider/ActionExecutor).
///
/// This widget owns one responsibility: laying out the column (cross/main axis +
/// spacing). Each child's *content* is produced by an injected
/// [NudgeNodeRendererRegistry] (Strategy), and its *box envelope* by an injected
/// [NudgeBoxDecorator] (Decorator). Both are constructor-injected (DIP), so a
/// host can swap a renderer or the framing without touching this class.
class NudgeView extends StatelessWidget {
  final NudgeColumn content;
  final NudgeNodeRendererRegistry renderers;
  final NudgeBoxDecorator decorator;

  NudgeView({
    required this.content,
    NudgeNodeRendererRegistry? renderers,
    this.decorator = const NudgeBoxDecorator(),
    super.key,
  }) : renderers = renderers ?? _sharedRenderers;

  /// Built once and shared — the default strategy set is stateless.
  static final NudgeNodeRendererRegistry _sharedRenderers = NudgeNodeRendererRegistry();

  @override
  Widget build(BuildContext context) {
    final children = <Widget>[];
    for (var i = 0; i < content.children.length; i++) {
      if (i > 0 && content.spacing > 0) {
        children.add(SizedBox(height: content.spacing));
      }
      final node = content.children[i];
      children.add(decorator.decorate(node.box, renderers.render(node, context)));
    }

    return Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: content.crossAxisAlignment,
      mainAxisAlignment: content.mainAxisAlignment,
      children: children,
    );
  }
}
