// swift-tools-version: 5.10

import PackageDescription

let package = Package(
    name: "TdayIOS",
    defaultLocalization: "en",
    platforms: [
        .iOS(.v17),
    ],
    products: [
        .executable(
            name: "Tday",
            targets: ["Tday"]
        ),
    ],
    dependencies: [
        .package(url: "https://github.com/getsentry/sentry-cocoa", from: "8.45.0"),
    ],
    targets: [
        .executableTarget(
            name: "Tday",
            dependencies: [
                .product(name: "Sentry", package: "sentry-cocoa"),
            ],
            path: "Tday",
            exclude: [
                "Info.plist",
            ]
        ),
    ]
)
