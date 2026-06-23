import 'package:flutter/widgets.dart';

import '../engage_fonts.dart';

/// A guide action button, rendered inside showcaseview's tooltip action row via
/// `TooltipActionButton.button`.
///
/// A port of React Native's `ActionButton` (`DigiaProvider.tsx`): a primary
/// style fills with [primaryBg]; ghost/secondary are text-only. Using a custom
/// widget (rather than showcaseview's default action button) keeps the button
/// look identical to RN while still letting showcaseview own the tooltip frame,
/// arrow and positioning. `minHeight 32, minWidth 60, radius 8, paddingH 12`.
class GuideActionButton extends StatelessWidget {
  final String label;
  final bool isPrimary;
  final Color primaryBg;
  final Color primaryText;
  final Color ghostText;
  final VoidCallback onTap;

  const GuideActionButton({
    required this.label,
    required this.isPrimary,
    required this.primaryBg,
    required this.primaryText,
    required this.ghostText,
    required this.onTap,
    super.key,
  });

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      behavior: HitTestBehavior.opaque,
      child: Container(
        constraints: const BoxConstraints(minHeight: 32, minWidth: 60),
        padding: const EdgeInsets.symmetric(horizontal: 12),
        alignment: Alignment.center,
        decoration: BoxDecoration(
          color: isPrimary ? primaryBg : const Color(0x00000000),
          borderRadius: BorderRadius.circular(8),
        ),
        child: Text(
          label,
          style: TextStyle(
            color: isPrimary ? primaryText : ghostText,
            fontSize: 13,
            fontWeight: FontWeight.w600,
            fontFamily: EngageFonts.fontFamily,
          ),
        ),
      ),
    );
  }
}
