import Foundation

enum CreditRole: Int, CaseIterable, Sendable {
    case performer, vocals, instrument, composer, lyricist, writer, arranger, producer, mix, engineer, recording, mastering
}

struct Credit: Hashable, Sendable {
    let name: String
    let role: CreditRole
    let detail: String?
}

struct TrackCredits: Sendable {
    let recordingId: String
    let credits: [Credit]
    var isEmpty: Bool { credits.isEmpty }
}

enum CreditsParser {
    static let minSearchScore = 90
    static let durationToleranceMs = 5_000

    private static let roles: [String: CreditRole] = [
        "performer": .performer,
        "vocal": .vocals,
        "instrument": .instrument,
        "composer": .composer,
        "lyricist": .lyricist,
        "writer": .writer,
        "arranger": .arranger,
        "orchestrator": .arranger,
        "producer": .producer,
        "mix": .mix,
        "engineer": .engineer,
        "sound": .engineer,
        "recording": .recording,
        "mastering": .mastering,
    ]

    static func bestRecordingId(searchBody: Data, durationMs: Int?) -> String? {
        guard let root = try? JSONSerialization.jsonObject(with: searchBody) as? [String: Any],
              let recordings = root["recordings"] as? [[String: Any]] else { return nil }
        for hit in recordings {
            let score = (hit["score"] as? Int) ?? 0
            guard score >= minSearchScore else { continue }
            if let durationMs, let length = hit["length"] as? Int, abs(length - durationMs) > durationToleranceMs {
                continue
            }
            if let id = hit["id"] as? String { return id }
        }
        return nil
    }

    static func parseCredits(lookupBody: Data) -> TrackCredits? {
        guard let root = try? JSONSerialization.jsonObject(with: lookupBody) as? [String: Any],
              let id = root["id"] as? String else { return nil }
        var found: [Credit] = []
        var seen = Set<Credit>()
        func collect(_ relations: Any?) {
            guard let list = relations as? [[String: Any]] else { return }
            for relation in list {
                guard let type = (relation["type"] as? String)?.lowercased(),
                      let role = roles[type],
                      let name = ((relation["artist"] as? [String: Any])?["name"] as? String)?
                        .trimmingCharacters(in: .whitespacesAndNewlines),
                      !name.isEmpty else { continue }
                var detail: String?
                if role == .instrument || role == .vocals || role == .performer,
                   let attributes = relation["attributes"] as? [String] {
                    let joined = attributes.filter { !$0.trimmingCharacters(in: .whitespaces).isEmpty }.joined(separator: ", ")
                    detail = joined.isEmpty ? nil : joined
                }
                let credit = Credit(name: name, role: role, detail: detail)
                if seen.insert(credit).inserted { found.append(credit) }
            }
        }
        collect(root["relations"])
        for relation in (root["relations"] as? [[String: Any]]) ?? [] {
            collect((relation["work"] as? [String: Any])?["relations"])
        }
        let sorted = found.enumerated()
            .sorted { ($0.element.role.rawValue, $0.offset) < ($1.element.role.rawValue, $1.offset) }
            .map(\.element)
        return TrackCredits(recordingId: id, credits: sorted)
    }

    static func searchQuery(title: String, artist: String) -> String {
        func escaped(_ value: String) -> String {
            value.replacingOccurrences(of: "\\", with: "\\\\").replacingOccurrences(of: "\"", with: "\\\"")
        }
        return "recording:\"\(escaped(title))\" AND artist:\"\(escaped(artist))\""
    }
}

actor MusicBrainz {
    static let shared = MusicBrainz()

    private static let base = "https://musicbrainz.org/ws/2"
    private static let agent = "BitChord (https://github.com/kelian-4/music)"
    private static let gap: TimeInterval = 1.1
    private static let cacheSize = 64

    private var lastCall = Date.distantPast
    private var cache: [String: TrackCredits?] = [:]
    private var order: [String] = []

    func credits(title: String, artist: String, durationMs: Int?) async throws -> TrackCredits? {
        let key = "\(title.lowercased())|\(artist.lowercased())"
        if let hit = cache[key] { return hit }

        var search = URLComponents(string: "\(Self.base)/recording")!
        search.queryItems = [
            URLQueryItem(name: "query", value: CreditsParser.searchQuery(title: title, artist: artist)),
            URLQueryItem(name: "fmt", value: "json"),
            URLQueryItem(name: "limit", value: "5"),
        ]
        let searchBody = try await fetch(search.url!)
        var result: TrackCredits?
        if let id = CreditsParser.bestRecordingId(searchBody: searchBody, durationMs: durationMs) {
            var lookup = URLComponents(string: "\(Self.base)/recording/\(id)")!
            lookup.queryItems = [
                URLQueryItem(name: "inc", value: "artist-rels+work-rels+work-level-rels"),
                URLQueryItem(name: "fmt", value: "json"),
            ]
            let body = try await fetch(lookup.url!)
            if let parsed = CreditsParser.parseCredits(lookupBody: body), !parsed.isEmpty { result = parsed }
        }
        remember(key, result)
        return result
    }

    private func remember(_ key: String, _ value: TrackCredits?) {
        cache[key] = .some(value)
        order.removeAll { $0 == key }
        order.append(key)
        while order.count > Self.cacheSize {
            cache[order.removeFirst()] = nil
        }
    }

    private func fetch(_ url: URL) async throws -> Data {
        let wait = lastCall.addingTimeInterval(Self.gap).timeIntervalSinceNow
        if wait > 0 { try await Task.sleep(nanoseconds: UInt64(wait * 1_000_000_000)) }
        defer { lastCall = Date() }
        var request = URLRequest(url: url)
        request.setValue(Self.agent, forHTTPHeaderField: "User-Agent")
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        let (data, response) = try await URLSession.shared.data(for: request)
        guard let http = response as? HTTPURLResponse, (200..<300).contains(http.statusCode) else {
            throw URLError(.badServerResponse)
        }
        return data
    }
}
