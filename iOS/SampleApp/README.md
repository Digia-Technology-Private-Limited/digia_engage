# DigiaEngageSample

## Prereqs

- Xcode (SwiftPM support)
- iOS 16+ iPhone simulator (or device)

## Run

- Open `DigiaEngageSample.xcodeproj` in Xcode
- Select scheme `DigiaEngageSample`
- Select an iPhone simulator (iOS 16+)
- Run

## Configuration

The sample initializes DigiaEngage in `debug` flavor in `DigiaEngageSampleApp.swift`:

- `apiKey`: update to your own key if needed
- `flavor: .debug()`: controls how AppConfig is fetched (endpoints/behavior are flavor-driven)

## Troubleshooting

- If SwiftPM dependency resolution fails, ensure you can fetch the `digia_expr_swift` dependency referenced by the package graph (your environment may require GitHub access/credentials).
- If the app boots but shows no UI/content, verify network connectivity and that AppConfig can be fetched for your `apiKey` + `debug` flavor.