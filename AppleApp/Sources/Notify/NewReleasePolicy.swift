import Foundation

struct ReleaseRef: Equatable {
    let id: String
    let title: String
}

struct ReleaseDiff: Equatable {
    let fresh: [ReleaseRef]
    let seenAfter: [String]
}

enum NewReleasePolicy {
    static let maxSeenPerArtist = 300
    static let maxNotificationsPerCheck = 6
    private static let releasePrefix = "MPRE"

    static func isRelease(_ browseId: String?) -> Bool {
        browseId?.hasPrefix(releasePrefix) == true
    }

    static func diff(seen: [String]?, current: [ReleaseRef]) -> ReleaseDiff {
        var distinct: [ReleaseRef] = []
        var ids = Set<String>()
        for ref in current where ids.insert(ref.id).inserted { distinct.append(ref) }
        guard let seen else {
            return ReleaseDiff(fresh: [], seenAfter: trimmed(distinct.map(\.id)))
        }
        let known = Set(seen)
        let fresh = distinct.filter { !known.contains($0.id) }
        var after = distinct.map(\.id)
        let current = Set(after)
        for id in seen where !current.contains(id) { after.append(id) }
        return ReleaseDiff(fresh: fresh, seenAfter: trimmed(after))
    }

    private static func trimmed(_ ids: [String]) -> [String] {
        ids.count <= maxSeenPerArtist ? ids : Array(ids.prefix(maxSeenPerArtist))
    }

    static func forgetUnfollowed(_ seen: [String: [String]], followed: [String]) -> [String: [String]] {
        if followed.isEmpty { return seen }
        let keep = Set(followed)
        return seen.filter { keep.contains($0.key) }
    }

    static func toAnnounce<T>(_ findings: [(artist: T, releases: [ReleaseRef])]) -> [(artist: T, releases: [ReleaseRef])] {
        Array(findings.filter { !$0.releases.isEmpty }.prefix(maxNotificationsPerCheck))
    }

    static func encode(_ seen: [String: [String]]) -> String {
        guard let data = try? JSONEncoder().encode(seen), let text = String(data: data, encoding: .utf8) else { return "" }
        return text
    }

    static func decode(_ stored: String?) -> [String: [String]] {
        guard let stored, !stored.isEmpty, let data = stored.data(using: .utf8) else { return [:] }
        return (try? JSONDecoder().decode([String: [String]].self, from: data)) ?? [:]
    }
}
