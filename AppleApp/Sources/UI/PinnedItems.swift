import SwiftUI
import AmberShared

struct PinnedItem: Decodable, Identifiable, Hashable {
    let kind: String
    let id: String
    let title: String
    let subtitle: String?
    let thumbnailUrl: String?

    enum CodingKeys: String, CodingKey { case kind, id, title, subtitle, thumbnailUrl }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        kind = try c.decode(String.self, forKey: .kind)
        id = try c.decode(String.self, forKey: .id)
        title = try c.decodeIfPresent(String.self, forKey: .title) ?? ""
        subtitle = try c.decodeIfPresent(String.self, forKey: .subtitle)
        thumbnailUrl = try c.decodeIfPresent(String.self, forKey: .thumbnailUrl)
    }

    var card: ShelfCard {
        kind == "SONG"
            ? ShelfCard(title: title, subtitle: subtitle, thumbnailUrl: thumbnailUrl, videoId: id, browseId: nil)
            : ShelfCard(title: title, subtitle: subtitle, thumbnailUrl: thumbnailUrl, videoId: nil, browseId: id)
    }
}

enum PinnedItems {
    static let storageKey = "pinned_items"

    static func all() -> [PinnedItem] {
        let raw = PlatformSettings.shared.getString(key: storageKey, default: "")
        guard let data = raw.data(using: .utf8),
              let items = try? JSONDecoder().decode([PinnedItem].self, from: data) else { return [] }
        return items
    }

    static func cards() -> [ShelfCard] {
        all().filter { !$0.title.isEmpty }.map(\.card)
    }

    static func kind(of card: ShelfCard) -> String? {
        if let videoId = card.videoId, !videoId.isEmpty { return "SONG" }
        guard let browseId = card.browseId, !browseId.isEmpty else { return nil }
        if browseId.hasPrefix("UC") { return "ARTIST" }
        if browseId.hasPrefix("VL") || browseId.hasPrefix("PL") { return "PLAYLIST" }
        return "ALBUM"
    }

    static func identifier(of card: ShelfCard) -> String? {
        if let videoId = card.videoId, !videoId.isEmpty { return videoId }
        guard let browseId = card.browseId, !browseId.isEmpty else { return nil }
        return browseId
    }

    static func isPinned(_ card: ShelfCard) -> Bool {
        guard let kind = kind(of: card), let id = identifier(of: card) else { return false }
        return all().contains { $0.kind == kind && $0.id == id }
    }

    @discardableResult
    static func toggle(_ card: ShelfCard) -> Bool {
        guard let kind = kind(of: card), let id = identifier(of: card) else { return true }
        let change = AppSettings.shared.togglePinnedItem(
            kind: kind,
            id: id,
            title: card.title,
            subtitle: card.subtitle ?? "",
            thumbnailUrl: card.thumbnailUrl
        )
        return change != "LIMIT"
    }
}

struct PinMenuButton: View {
    let card: ShelfCard
    @Environment(AppModel.self) private var appModel

    var body: some View {
        if PinnedItems.kind(of: card) != nil {
            Button(PinnedItems.isPinned(card) ? String(localized: "Unpin") : String(localized: "Pin")) {
                if !PinnedItems.toggle(card) {
                    appModel.pinLimitAlert = true
                }
            }
        }
    }
}
