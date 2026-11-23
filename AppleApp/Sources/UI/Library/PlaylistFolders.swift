import SwiftUI
import AmberShared

struct PlaylistFolderItem: Decodable, Identifiable, Hashable {
    let id: String
    let name: String
    let playlistIds: [String]

    enum CodingKeys: String, CodingKey { case id, name, playlistIds }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decode(String.self, forKey: .id)
        name = try c.decode(String.self, forKey: .name)
        playlistIds = try c.decodeIfPresent([String].self, forKey: .playlistIds) ?? []
    }
}

enum PlaylistFolders {
    static let storageKey = "playlist_folders"

    static func all() -> [PlaylistFolderItem] {
        let raw = PlatformSettings.shared.getString(key: storageKey, default: "")
        guard let data = raw.data(using: .utf8),
              let folders = try? JSONDecoder().decode([PlaylistFolderItem].self, from: data) else { return [] }
        return folders
    }

    static func folderId(of playlistId: String) -> String? {
        all().first { $0.playlistIds.contains(playlistId) }?.id
    }

    static func visible(_ items: [ShelfCard], selectedId: String?) -> [ShelfCard] {
        guard let selectedId, let folder = all().first(where: { $0.id == selectedId }) else { return items }
        let ids = Set(folder.playlistIds)
        return items.filter { card in card.browseId.map { ids.contains($0) } ?? false }
    }
}

struct PlaylistFolderBar: View {
    @Binding var selectedId: String?
    @AppStorage(PlaylistFolders.storageKey) private var revision = ""
    @State private var creating = false
    @State private var renaming = false
    @State private var deleting = false
    @State private var refused = false
    @State private var name = ""

    var body: some View {
        let _ = revision
        let folders = PlaylistFolders.all()
        let selected = folders.first { $0.id == selectedId }
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                chip(String(localized: "All"), isOn: selected == nil) { selectedId = nil }
                ForEach(folders) { folder in
                    chip(folder.name, isOn: folder.id == selected?.id) { selectedId = folder.id }
                }
                Button {
                    name = ""
                    creating = true
                } label: {
                    Label(String(localized: "New Folder"), systemImage: "folder.badge.plus")
                        .font(.subheadline)
                }
                .buttonStyle(.bordered)
                .controlSize(.small)
                if selected != nil {
                    Button(String(localized: "Rename Folder")) {
                        name = selected?.name ?? ""
                        renaming = true
                    }
                    .font(.subheadline)
                    Button(String(localized: "Delete Folder"), role: .destructive) { deleting = true }
                        .font(.subheadline)
                }
            }
        }
        .alert(String(localized: "New Folder"), isPresented: $creating) {
            TextField(String(localized: "Folder Name"), text: $name)
            Button(String(localized: "Create")) {
                if !AppSettings.shared.createPlaylistFolder(name: name) { refused = true }
            }
            Button(String(localized: "Cancel"), role: .cancel) {}
        }
        .alert(String(localized: "Rename Folder"), isPresented: $renaming) {
            TextField(String(localized: "Folder Name"), text: $name)
            Button(String(localized: "Save")) {
                if let id = selected?.id, !AppSettings.shared.renamePlaylistFolder(id: id, name: name) {
                    refused = true
                }
            }
            Button(String(localized: "Cancel"), role: .cancel) {}
        }
        .alert(String(localized: "Delete Folder"), isPresented: $deleting) {
            Button(String(localized: "Delete Folder"), role: .destructive) {
                if let id = selected?.id {
                    selectedId = nil
                    AppSettings.shared.deletePlaylistFolder(id: id)
                }
            }
            Button(String(localized: "Cancel"), role: .cancel) {}
        } message: {
            Text("The playlists in this folder are kept.")
        }
        .alert(String(localized: "Pick a name that is not empty and not already used"), isPresented: $refused) {
            Button("OK", role: .cancel) {}
        }
    }

    private func chip(_ title: String, isOn: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(title)
                .font(.subheadline.weight(isOn ? .semibold : .regular))
                .padding(.horizontal, 12)
                .padding(.vertical, 6)
                .background(isOn ? AnyShapeStyle(.tint) : AnyShapeStyle(.thinMaterial), in: Capsule())
                .foregroundStyle(isOn ? Color.white : Color.primary)
        }
        .buttonStyle(.plain)
    }
}

struct PlaylistFolderMenu: View {
    let browseId: String
    @AppStorage(PlaylistFolders.storageKey) private var revision = ""

    var body: some View {
        let _ = revision
        let folders = PlaylistFolders.all()
        if !folders.isEmpty, browseId.hasPrefix("VL") || browseId.hasPrefix("PL") {
            let current = PlaylistFolders.folderId(of: browseId)
            Menu(String(localized: "Move to Folder")) {
                Button(String(localized: "No Folder")) {
                    AppSettings.shared.movePlaylistToFolder(playlistId: browseId, folderId: nil)
                }
                ForEach(folders) { folder in
                    Button {
                        AppSettings.shared.movePlaylistToFolder(playlistId: browseId, folderId: folder.id)
                    } label: {
                        if folder.id == current {
                            Label(folder.name, systemImage: "checkmark")
                        } else {
                            Text(folder.name)
                        }
                    }
                }
            }
        }
    }
}
