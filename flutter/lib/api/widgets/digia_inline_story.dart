import 'package:cached_network_image/cached_network_image.dart';
import 'package:flutter/material.dart';
import 'package:video_player/video_player.dart';

import '../internal/action/engage_action_context.dart';
import '../internal/campaign/inline_story_config.dart';
import '../internal/variable_scope.dart';
import 'story/digia_story_overlay.dart';

/// Renders an [InlineStoryConfig] as a horizontally scrolling row of story
/// cards. Tapping a card opens the full-screen story viewer at that index.
///
/// Mirrors the Android `DigiaInlineStory`: card sizing, spacing, and corner
/// radius come straight from the server-delivered `card` config, and each card
/// shows the first frame (image) or a poster (video) of its story item.
class DigiaInlineStory extends StatelessWidget {
  final InlineStoryConfig config;

  const DigiaInlineStory({
    super.key,
    required this.config,
  });

  @override
  Widget build(BuildContext context) {
    // The trigger's variables come from the [VariableScopeProvider] DigiaSlot
    // places above this widget; forward the scope to the (route-pushed) viewer
    // so its CTA copy resolves against the same variables.
    final scope = VariableScopeProvider.of(context);
    final card = config.card;
    final spacing = card.spacing.toDouble();
    final cardHeight = card.height.toDouble();
    final cardWidth = card.width;
    final radius = BorderRadius.circular(card.borderRadius);

    return SizedBox(
      height: cardHeight,
      child: ListView.separated(
        scrollDirection: Axis.horizontal,
        padding: EdgeInsets.symmetric(horizontal: spacing),
        itemCount: config.items.length,
        separatorBuilder: (_, __) => SizedBox(width: spacing),
        itemBuilder: (context, index) {
          final item = config.items[index];
          return GestureDetector(
            behavior: HitTestBehavior.opaque,
            onTap: () => openStoryOverlay(
              context: context,
              config: config,
              initialIndex: index,
              scope: scope,
              actionContext: const EngageActionContext(
                campaignId: '',
                campaignKey: '',
                surface: EngageSurface.inline,
              ),
            ),
            child: ClipRRect(
              borderRadius: radius,
              child: SizedBox(
                width: cardWidth,
                height: cardHeight,
                child: _StoryCardThumbnail(item: item),
              ),
            ),
          );
        },
      ),
    );
  }
}

/// The poster shown on a story card: a cached image, or a muted looping video
/// preview for video items (mirrors the Android `DigiaInlineStory` cards).
class _StoryCardThumbnail extends StatelessWidget {
  final StoryItemConfig item;

  const _StoryCardThumbnail({required this.item});

  @override
  Widget build(BuildContext context) {
    if (item.isVideo) return _VideoThumbnail(url: item.url);
    return CachedNetworkImage(
      imageUrl: item.url,
      fit: BoxFit.cover,
      placeholder: (_, __) => const ColoredBox(color: Color(0xFF1A1A1A)),
      errorWidget: (_, __, ___) => const ColoredBox(color: Color(0xFF2A2A2A)),
    );
  }
}

/// A muted, looping video preview used as a story card thumbnail. Manages its
/// own [VideoPlayerController] and is disposed when the card scrolls off-screen.
class _VideoThumbnail extends StatefulWidget {
  final String url;

  const _VideoThumbnail({required this.url});

  @override
  State<_VideoThumbnail> createState() => _VideoThumbnailState();
}

class _VideoThumbnailState extends State<_VideoThumbnail> {
  VideoPlayerController? _controller;

  @override
  void initState() {
    super.initState();
    final controller = VideoPlayerController.networkUrl(Uri.parse(widget.url));
    _controller = controller;
    controller.initialize().then((_) {
      if (!mounted || _controller != controller) return;
      controller
        ..setVolume(0)
        ..setLooping(true)
        ..play();
      setState(() {});
    }).catchError((_) {/* leave the dark placeholder in place */});
  }

  @override
  void dispose() {
    _controller?.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final controller = _controller;
    if (controller == null || !controller.value.isInitialized) {
      return const ColoredBox(color: Color(0xFF1A1A1A));
    }
    return FittedBox(
      fit: BoxFit.cover,
      clipBehavior: Clip.hardEdge,
      child: SizedBox(
        width: controller.value.size.width,
        height: controller.value.size.height,
        child: VideoPlayer(controller),
      ),
    );
  }
}
