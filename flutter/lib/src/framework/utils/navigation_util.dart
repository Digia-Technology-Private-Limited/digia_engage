import 'package:flutter/material.dart';

Future<T?> presentDialog<T>({
  required BuildContext context,
  required WidgetBuilder builder,
  bool barrierDismissible = true,
  Color? barrierColor,
  bool useSafeArea = true,
  bool useRootNavigator = false,
  RouteSettings? routeSettings,
  GlobalKey<NavigatorState>? navigatorKey,
}) {
  return showDialog(
      context: navigatorKey?.currentContext ?? context,
      useSafeArea: useSafeArea,
      useRootNavigator: useRootNavigator,
      barrierDismissible: barrierDismissible,
      barrierColor: barrierColor,
      routeSettings: routeSettings,
      builder: (context) {
        return Dialog(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [builder(context)],
          ),
        );
      });
}

Future<T?> presentBottomSheet<T>({
  required BuildContext context,
  required WidgetBuilder builder,
  double scrollControlDisabledMaxHeightRatio = 9.0 / 16.0,
  Color? backgroundColor,
  Color? barrierColor,
  BoxBorder? border,
  bool useSafeArea = true,
  BorderRadius? borderRadius,
  GlobalKey<NavigatorState>? navigatorKey,
}) {
  return showModalBottomSheet<T>(
    context: navigatorKey?.currentContext ?? context,
    backgroundColor: backgroundColor,
    barrierColor: barrierColor,
    useSafeArea: useSafeArea,
    useRootNavigator: true,
    isScrollControlled: true,
    builder: (innerContext) {
      final maxSheetHeight = MediaQuery.sizeOf(innerContext).height *
          scrollControlDisabledMaxHeightRatio;
      return Container(
        width: double.infinity,
        decoration: BoxDecoration(
          color: backgroundColor,
          borderRadius: borderRadius,
        ),
        foregroundDecoration: BoxDecoration(
          border: border,
          borderRadius: borderRadius,
        ),
        clipBehavior: Clip.hardEdge,
        child: SafeArea(
          bottom: useSafeArea,
          child: Padding(
            padding: EdgeInsets.only(
              bottom: MediaQuery.viewInsetsOf(innerContext).bottom,
            ),
            child: ConstrainedBox(
              constraints: BoxConstraints(maxHeight: maxSheetHeight),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [builder(innerContext)],
              ),
            ),
          ),
        ),
      );
    },
  );
}

abstract class NavigatorHelper {
  static Future<T?> push<T extends Object?>(BuildContext context,
      GlobalKey<NavigatorState>? navigatorKey, Route<T> newRoute,
      {RoutePredicate? removeRoutesUntilPredicate}) {
    if (removeRoutesUntilPredicate == null) {
      final push =
          navigatorKey?.currentState?.push ?? Navigator.of(context).push;
      return push(newRoute);
    }

    final pushAndRemoveUntil = navigatorKey?.currentState?.pushAndRemoveUntil ??
        Navigator.of(context).pushAndRemoveUntil;
    return pushAndRemoveUntil(newRoute, removeRoutesUntilPredicate);
  }
}
