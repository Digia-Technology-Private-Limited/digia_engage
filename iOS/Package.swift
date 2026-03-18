// swift-tools-version: 6.0
import PackageDescription

let package = Package(
    name: "DigiaEngage",
    platforms: [
        .iOS(.v16),
    ],
    products: [
        .library(
            name: "DigiaEngage",
            targets: ["DigiaEngage"]
        ),
    ],
    dependencies: [
        .package(url: "git@github.com:Digia-Technology-Private-Limited/digia_expr_swift.git", branch: "main"),
        .package(url: "https://github.com/SDWebImage/SDWebImageSwiftUI.git", from: "3.1.3"),
        .package(url: "https://github.com/SDWebImage/SDWebImageSVGCoder.git", from: "1.7.1"),
        .package(url: "https://github.com/airbnb/lottie-spm.git", from: "4.5.0"),
        .package(url: "https://github.com/AndreaMiotto/PartialSheet.git", from: "3.1.1"),
        .package(url: "https://github.com/elai950/AlertToast.git", from: "1.3.9"),
        .package(url: "https://github.com/nalexn/ViewInspector.git", from: "0.10.3"),
    ],
    targets: [
        .target(
            name: "DigiaEngage",
            dependencies: [
                .product(name: "DigiaExpr", package: "digia_expr_swift"),
                .product(name: "SDWebImageSwiftUI", package: "SDWebImageSwiftUI"),
                .product(name: "SDWebImageSVGCoder", package: "SDWebImageSVGCoder"),
                .product(name: "Lottie", package: "lottie-spm"),
                .product(name: "PartialSheet", package: "PartialSheet", condition: .when(platforms: [.iOS])),
                .product(name: "AlertToast", package: "AlertToast", condition: .when(platforms: [.iOS])),
            ],
            resources: [
                .process("Resources")
            ]
        ),
        .testTarget(
            name: "DigiaEngageTests",
            dependencies: [
                "DigiaEngage",
                .product(name: "ViewInspector", package: "ViewInspector"),
            ],
            resources: [
                .process("Fixtures")
            ]
        ),
    ]
)
