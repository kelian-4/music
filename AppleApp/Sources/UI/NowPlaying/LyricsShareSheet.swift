import SwiftUI
import AmberShared

#if os(iOS)
import UIKit
import Photos

struct LyricsShareSheet: View {
    let lines: [LyricLineDto]
    let originalLines: [LyricLineDto]?
    let translator: LyricsTranslator?
    let title: String
    let artist: String
    let artworkData: Data?
    let artworkUrl: String?
    let songURL: URL?
    let initialSelection: Int?

    @Environment(\.dismiss) private var dismiss
    @State private var selection: Set<Int> = []
    @State private var shareImage: Image?
    @State private var shareData: Data?
    @State private var storyFormat = false
    @State private var saveMessage: String?

    private let limit = 250

    private var characterCount: Int {
        selection.reduce(0) { $0 + text(at: $1).count }
    }

    private func text(at index: Int) -> String {
        if let originalLines, originalLines.indices.contains(index) { return originalLines[index].text }
        return lines.indices.contains(index) ? lines[index].text : ""
    }

    private var tint: Color {
        ArtworkPalette.pageTint(from: artworkData, seed: title.hashValue, dark: true).accent
    }

    var body: some View {
        VStack(spacing: 0) {
            header
            ScrollView {
                LazyVStack(spacing: 10) {
                    ForEach(Array(lines.enumerated()), id: \.offset) { index, line in
                        if !line.text.trimmingCharacters(in: .whitespaces).isEmpty {
                            lineCard(index: index, text: line.text)
                        }
                    }
                }
                .padding(.horizontal, 20)
                .padding(.vertical, 8)
            }
            .mask(
                LinearGradient(
                    stops: [
                        .init(color: .clear, location: 0),
                        .init(color: .black, location: 0.03),
                        .init(color: .black, location: 0.94),
                        .init(color: .clear, location: 1)
                    ],
                    startPoint: .top, endPoint: .bottom
                )
            )
            actions
        }
        .background(Color(white: 0.11).ignoresSafeArea())
        .preferredColorScheme(.dark)
        .presentationDetents([.large])
        .presentationDragIndicator(.hidden)
        .task {
            if let initialSelection, selection.isEmpty, lines.indices.contains(initialSelection) {
                selection = [initialSelection]
            }
        }
        .task(id: cardKey) { render() }
        .alert(saveMessage ?? "", isPresented: Binding(
            get: { saveMessage != nil },
            set: { if !$0 { saveMessage = nil } }
        )) {
            Button("OK", role: .cancel) {}
        }
    }

    private var header: some View {
        HStack(spacing: 14) {
            ArtworkView(url: artworkUrl, data: artworkData, side: 64)
                .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
            VStack(alignment: .leading, spacing: 2) {
                Text(selection.count == 1 ? "1 line selected" : "\(selection.count) lines selected")
                    .font(.title3.weight(.bold))
                    .foregroundStyle(.white)
                Text("\(characterCount) of \(limit) characters")
                    .font(.subheadline)
                    .foregroundStyle(.white.opacity(0.6))
            }
            Spacer(minLength: 0)
            Button { dismiss() } label: {
                Image(systemName: "xmark")
                    .font(.system(size: 17, weight: .bold))
                    .foregroundStyle(.white)
                    .frame(width: 44, height: 44)
                    .background(.white.opacity(0.16), in: Circle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Close")
        }
        .padding(.horizontal, 20)
        .padding(.top, 22)
        .padding(.bottom, 12)
    }

    private func lineCard(index: Int, text: String) -> some View {
        let picked = selection.contains(index)
        return Text(text)
            .font(.system(size: 26, weight: .bold))
            .foregroundStyle(picked ? Color.white : Color.white.opacity(0.7))
            .multilineTextAlignment(.leading)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 16)
            .padding(.vertical, 14)
            .background(
                RoundedRectangle(cornerRadius: 22, style: .continuous)
                    .fill(picked ? tint.opacity(0.85) : Color.white.opacity(0.14))
            )
            .contentShape(RoundedRectangle(cornerRadius: 22, style: .continuous))
            .onTapGesture { toggle(index) }
            .accessibilityAddTraits(picked ? [.isSelected, .isButton] : .isButton)
    }

    private var actions: some View {
        VStack(spacing: 14) {
            Divider().overlay(Color.white.opacity(0.15))
            HStack(alignment: .top, spacing: 22) {
                if let shareImage {
                    ShareLink(
                        item: shareImage,
                        preview: SharePreview(title.isEmpty ? "Lyrics" : "\(title) — lyrics", image: shareImage)
                    ) {
                        actionLabel("square.and.arrow.up", "Share Lyrics")
                    }
                    Button(action: saveToPhotos) {
                        actionLabel("square.and.arrow.down", "Save to Photos")
                    }
                    .buttonStyle(.plain)
                    Button { storyFormat.toggle() } label: {
                        actionLabel("aspectratio", storyFormat ? "Story · 9:16" : "Card")
                    }
                    .buttonStyle(.plain)
                } else {
                    Text(selection.isEmpty ? "Pick lines to share" : "")
                        .font(.subheadline)
                        .foregroundStyle(.white.opacity(0.6))
                        .frame(maxWidth: .infinity, minHeight: 70)
                }
                if let songURL {
                    ShareLink(item: songURL) {
                        actionLabel("square.and.arrow.up.on.square", "Share Song")
                    }
                }
                Spacer(minLength: 0)
            }
            .padding(.horizontal, 20)
        }
        .padding(.bottom, 24)
    }

    private func actionLabel(_ symbol: String, _ label: LocalizedStringKey) -> some View {
        VStack(spacing: 8) {
            Image(systemName: symbol)
                .font(.system(size: 26, weight: .regular))
                .foregroundStyle(.white)
                .frame(width: 64, height: 64)
                .background(.white.opacity(0.16), in: Circle())
            Text(label)
                .font(.footnote)
                .foregroundStyle(.white)
                .multilineTextAlignment(.center)
                .lineLimit(2)
                .frame(width: 76)
        }
    }

    private func toggle(_ index: Int) {
        if selection.contains(index) {
            selection.remove(index)
        } else if selection.isEmpty || characterCount + text(at: index).count <= limit {
            selection.insert(index)
        }
        UISelectionFeedbackGenerator().selectionChanged()
    }

    private struct CardKey: Equatable {
        var selection: Set<Int>
        var translated: Bool
        var story: Bool
    }

    private var cardKey: CardKey {
        CardKey(selection: selection, translated: translator?.translatedLines != nil, story: storyFormat)
    }

    private var pickedCardLines: [LyricShareCard.Line] {
        let subLines = translator?.translatedLines
        let subbed = subLines?.count == lines.count
        var out: [LyricShareCard.Line] = []
        var previous = -1
        for index in selection.sorted() {
            guard lines.indices.contains(index) else { continue }
            if previous >= 0, index > previous + 1 {
                out.append(LyricShareCard.Line(text: "", isGap: true))
            }
            let original = text(at: index)
            let sub = subbed ? subLines?[index].text : nil
            out.append(LyricShareCard.Line(
                text: original,
                subText: sub.flatMap {
                    let trimmed = $0.trimmingCharacters(in: .whitespacesAndNewlines)
                    return trimmed.isEmpty || $0 == original ? nil : $0
                }
            ))
            previous = index
        }
        return out
    }

    private func render() {
        guard !selection.isEmpty else {
            shareImage = nil
            shareData = nil
            return
        }
        let card = LyricShareCard(
            lines: pickedCardLines,
            title: title,
            artist: artist,
            tint: ArtworkPalette.pageTint(from: artworkData, seed: title.hashValue, dark: true),
            artwork: artworkData,
            artworkUrl: artworkUrl,
            storyFormat: storyFormat
        )
        let renderer = ImageRenderer(content: card)
        renderer.scale = 2
        if let image = renderer.uiImage {
            shareImage = Image(uiImage: image)
            shareData = image.pngData()
        } else {
            shareImage = nil
            shareData = nil
        }
    }

    private func saveToPhotos() {
        guard let shareData else { return }
        Task { @MainActor in
            let status = await PHPhotoLibrary.requestAuthorization(for: .addOnly)
            guard status == .authorized else {
                saveMessage = "Allow Amber to add photos in Settings to save this card."
                return
            }
            do {
                try await PHPhotoLibrary.shared().performChanges {
                    PHAssetCreationRequest.forAsset().addResource(with: .photo, data: shareData, options: nil)
                }
                saveMessage = "Saved to Photos."
            } catch {
                saveMessage = "Could not save the card. Please try again."
            }
        }
    }
}
#endif
