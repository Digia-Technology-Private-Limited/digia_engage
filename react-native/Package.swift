// swift-tools-version:5.9
// Package.swift — Swift Package Manager integration for @digia/engage-react-native.
//
// This file is used when the host app integrates React Native via Swift Package
// Manager (RN 0.74+).  It pulls the Digia Engage iOS SDK directly from the
// published Swift Package:
//   https://swiftpackageindex.com/Digia-Technology-Private-Limited/digia_engage_iOS
//
// CocoaPods users do NOT use this file; see DigiaEngageReactNative.podspec instead.

import PackageDescription

let package = Package(
    name: "DigiaEngageReactNative",
    platforms: [.iOS(.v16)],
    products: [
        .library(
            name: "DigiaEngageReactNative",
            targets: ["DigiaEngageReactNative"]
        ),
    ],
    dependencies: [
        // Digia Engage iOS SDK — Swift Package Index
        // https://swiftpackageindex.com/Digia-Technology-Private-Limited/digia_engage_iOS
        .package(
            url: "https://github.com/Digia-Technology-Private-Limited/digia_engage_iOS.git",
            exact: "1.0.0-beta.1"
        ),
    ],
    targets: [
        .target(
            name: "DigiaEngageReactNative",
            dependencies: [
                .product(name: "DigiaEngage", package: "digia_engage_iOS"),
            ],
            path: "ios",
            publicHeadersPath: "."
        ),
    ]
)
