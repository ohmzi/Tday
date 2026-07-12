import Foundation

// Hand-mirrored Codable structs for the generated guide artifact
// (ios-swiftUI/Tday/Resources/Guide/guide.<locale>.json), following the same
// convention as ApiModels.swift. The artifact is produced by
// `./gradlew :shared:exportGuideContent` from the shared Kotlin GuideCatalog, so
// these structs must track that schema — GuideContentContractTests guards it.

struct GuideArtifact: Codable {
    let currentVersion: String
    let ui: [String: String]
    let sections: [GuideSectionDTO]
    let topics: [GuideTopicDTO]

    static let empty = GuideArtifact(currentVersion: "", ui: [:], sections: [], topics: [])
}

struct GuideSectionDTO: Codable, Hashable {
    let id: String
    let title: String
    let order: Int
}

struct GuideDeepLinkDTO: Codable, Hashable {
    let web: String?
    let android: String?
    let ios: String?
}

struct GuideBlockDTO: Codable, Hashable {
    let type: String
    let texts: [String]
}

struct GuideTopicDTO: Codable, Hashable, Identifiable {
    let id: String
    let section: String
    let sectionOrder: Int
    let icon: String
    let platforms: [String]
    let badge: String?
    let serverOnly: Bool
    let sinceVersion: String?
    let deepLink: GuideDeepLinkDTO?
    let helpAnchors: [String]
    let title: String
    let summary: String
    let body: [GuideBlockDTO]
    // Pre-normalized (by the shared Kotlin GuideSearch) so the query-side port
    // is the only normalization iOS performs — guaranteeing search parity.
    let searchTitle: String
    let searchKeywords: String
    let searchBody: String
}
