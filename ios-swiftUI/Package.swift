// swift-tools-version: 5.10

import PackageDescription

let package = Package(
    name: "TdayIOSSupport",
    defaultLocalization: "en",
    platforms: [
        .iOS(.v17),
    ],
    products: [
        .library(
            name: "TdayCore",
            targets: ["TdayCore"]
        ),
    ],
    dependencies: [
        .package(url: "https://github.com/getsentry/sentry-cocoa", from: "8.45.0"),
    ],
    targets: [
        .target(
            name: "TdayCore",
            dependencies: [
                .product(name: "Sentry", package: "sentry-cocoa"),
            ],
            path: "Tday",
            exclude: [
                "Info.plist",
                "TdayApp.swift",
            ]
        ),
    ]
)
