import SwiftUI

struct CreditsSheet: View {
    let entry: QueueEntry
    @Environment(\.dismiss) private var dismiss

    private enum Phase {
        case loading, failed, none
        case found(TrackCredits)
    }

    @State private var phase: Phase = .loading

    var body: some View {
        NavigationStack {
            List {
                Section {
                    Text(entry.title).font(.headline)
                    Text(entry.artist).foregroundStyle(.secondary)
                }
                switch phase {
                case .loading:
                    Section { ProgressView().frame(maxWidth: .infinity) }
                case .failed:
                    Section { Text("Could not load the credits. Try again later.") }
                case .none:
                    Section { Text("No credits found for this song.") }
                case .found(let credits):
                    ForEach(CreditRole.allCases, id: \.self) { role in
                        let people = credits.credits.filter { $0.role == role }
                        if !people.isEmpty {
                            Section(Self.label(role)) {
                                ForEach(people, id: \.self) { credit in
                                    Text(credit.detail.map { "\(credit.name) (\($0))" } ?? credit.name)
                                }
                            }
                        }
                    }
                }
                Section {
                    Text("Credits from MusicBrainz").font(.footnote).foregroundStyle(.secondary)
                }
            }
            .navigationTitle("Credits")
            #if os(iOS)
            .navigationBarTitleDisplayMode(.inline)
            #endif
            .toolbar {
                ToolbarItem(placement: .confirmationAction) { Button("Done") { dismiss() } }
            }
        }
        .task(id: entry.id) {
            phase = .loading
            let duration = entry.durationSeconds > 0 ? Int(entry.durationSeconds * 1000) : nil
            do {
                if let credits = try await MusicBrainz.shared.credits(
                    title: entry.title, artist: entry.artist, durationMs: duration
                ) {
                    phase = .found(credits)
                } else {
                    phase = .none
                }
            } catch {
                phase = .failed
            }
        }
    }

    private static func label(_ role: CreditRole) -> LocalizedStringKey {
        switch role {
        case .performer: "Performers"
        case .vocals: "Vocals"
        case .instrument: "Instruments"
        case .composer: "Composed by"
        case .lyricist: "Lyrics by"
        case .writer: "Written by"
        case .arranger: "Arranged by"
        case .producer: "Produced by"
        case .mix: "Mixed by"
        case .engineer: "Engineered by"
        case .recording: "Recorded by"
        case .mastering: "Mastered by"
        }
    }
}
