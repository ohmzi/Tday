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
    targets: [
        .executableTarget(
            name: "Tday",
            path: "Tday",
            exclude: [
                "Info.plist",
            ]
        ),
    ]
)
