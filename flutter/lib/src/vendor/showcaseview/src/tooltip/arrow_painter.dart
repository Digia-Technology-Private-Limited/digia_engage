/*
 * Copyright (c) 2021 Simform Solutions
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
part of 'tooltip.dart';

class ShowcaseArrow extends StatelessWidget {
  const ShowcaseArrow({
    super.key,
    required this.strokeColor,
    this.borderColor,
    this.borderWidth = 0,
    this.arrowSize = Constants.arrowHeight,
  });

  final Color strokeColor;

  final Color? borderColor;
  final double borderWidth;
  final double arrowSize;

  @override
  Widget build(BuildContext context) {
    final width = arrowSize * 2;
    final height = arrowSize;
    return CustomPaint(
      painter: _ArrowPainter(
        strokeColor: strokeColor,
        // DIGIA
        borderColor: borderColor,
        borderWidth: borderWidth,
        arrowWidth: width,
        arrowHeight: height,
      ),
      size: Size(width, height),
    );
  }
}

class _ArrowPainter extends CustomPainter {
  _ArrowPainter({
    this.strokeColor = Colors.black,
    this.strokeWidth = Constants.arrowStrokeWidth,
    this.paintingStyle = PaintingStyle.fill,
    // DIGIA
    this.borderColor,
    this.borderWidth = 0,
    this.arrowWidth = Constants.arrowWidth,
    this.arrowHeight = Constants.arrowHeight,
  })  : _paint = Paint()
          ..color = strokeColor
          ..strokeWidth = strokeWidth
          ..style = paintingStyle,
        _path = Path()
          ..moveTo(0, arrowHeight + borderWidth)
          ..lineTo(arrowWidth * 0.5, 0)
          ..lineTo(arrowWidth, arrowHeight + borderWidth)
          ..lineTo(0, arrowHeight + borderWidth),
        // DIGIA: border paint + an OPEN path tracing only the two slanted edges
        // (not the base, which meets the bubble) — mirrors RN's arrow border.
        // The slanted edges run down to the plunged base so they connect into
        // the bubble's border instead of stopping short of it.
        _borderPaint = (borderColor != null && borderWidth > 0)
            ? (Paint()
              ..color = borderColor
              ..strokeWidth = borderWidth
              ..style = PaintingStyle.stroke
              ..strokeJoin = StrokeJoin.miter)
            : null,
        _borderPath = Path()
          ..moveTo(0, arrowHeight + borderWidth)
          ..lineTo(arrowWidth * 0.5, 0)
          ..lineTo(arrowWidth, arrowHeight + borderWidth);

  final Color strokeColor;
  final PaintingStyle paintingStyle;
  final double strokeWidth;
  // DIGIA
  final Color? borderColor;
  final double borderWidth;
  final double arrowWidth;
  final double arrowHeight;
  final Paint _paint;
  final Path _path;
  // DIGIA
  final Paint? _borderPaint;
  final Path _borderPath;

  @override
  void paint(Canvas canvas, Size size) {
    canvas.drawPath(_path, _paint);
    // DIGIA: draw the arrow border (if any) over the fill.
    final borderPaint = _borderPaint;
    if (borderPaint != null) canvas.drawPath(_borderPath, borderPaint);
  }

  @override
  bool shouldRepaint(covariant _ArrowPainter oldDelegate) {
    return oldDelegate.strokeColor != strokeColor ||
        oldDelegate.paintingStyle != paintingStyle ||
        oldDelegate.strokeWidth != strokeWidth ||
        // DIGIA
        oldDelegate.borderColor != borderColor ||
        oldDelegate.borderWidth != borderWidth ||
        oldDelegate.arrowWidth != arrowWidth ||
        oldDelegate.arrowHeight != arrowHeight;
  }
}
