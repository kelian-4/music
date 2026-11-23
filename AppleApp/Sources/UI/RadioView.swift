import SwiftUI
import BitChordShared

struct RadioView: View {
    @Environment(PlaybackController.self) private var controller
    @Environment(AuthController.self) private var auth
    @State private var songs: [YouTubeSong] = []
    @State private var loading = false

    private var stations: [QueueEntry] {
        var seen = Set<String>()
        var result: [QueueEntry] = []
        let fromHistory = songs.map {
            QueueEntry(
                id: $0.videoId, title: $0.title, artist: $0.artist,
                source: "yt:\($0.videoId)", thumbnailUrl: $0.thumbnailUrl,
                durationText: $0.durationText, albumName: $0.albumName,
                artworkData: nil, isLocal: false
            )
        }
        for entry in fromHistory + controller.queue where entry.videoId != nil {
            let key = entry.artist.lowercased()
            if !key.isEmpty, seen.insert(key).inserted { result.append(entry) }
            if result.count >= 24 { break }
        }
        return result
    }

    var body: some View {
        NavigationStack {
            Group {
                if stations.isEmpty {
                    if loading {
                        ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity)
                    } else {
                        ContentUnavailableView(
                            "No stations yet",
                            systemImage: "dot.radiowaves.left.and.right",
                            description: Text("Play some music and your stations will appear here.")
                        )
                    }
                } else {
                    List {
                        Section("Stations for you") {
                            ForEach(stations, id: \.id) { entry in
                                Button {
                                    controller.playRadio(entry, context: entry.artist)
                                } label: {
                                    HStack(spacing: 12) {
                                        Image(systemName: "dot.radiowaves.left.and.right")
                                            .font(.title3)
                                            .frame(width: 44, height: 44)
                                            .background(.quaternary, in: RoundedRectangle(cornerRadius: 8))
                                        VStack(alignment: .leading, spacing: 2) {
                                            Text(String(format: String(localized: "%@ Radio"), entry.artist))
                                                .font(.body.weight(.semibold))
                                            Text("Based on what you play")
                                                .font(.footnote)
                                                .foregroundStyle(.secondary)
                                        }
                                        Spacer()
                                    }
                                }
                                .buttonStyle(.plain)
                            }
                        }
                    }
                    .listStyle(.plain)
                }
            }
            .navigationTitle("Radio")
            .task(id: auth.signedIn) {
                guard auth.signedIn else { songs = []; return }
                loading = true
                songs = (try? await InnertubeFeed.shared.history(force: false)) ?? []
                loading = false
            }
        }
    }
}
