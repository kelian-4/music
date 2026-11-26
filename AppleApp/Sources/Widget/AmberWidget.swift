import Foundation
import SwiftUI
import WidgetKit
import AppIntents
import CoreImage
import CoreImage.CIFilterBuiltins

// The §9 widget, snapshot-driven per the parity contract: ready-to-play
// state with no position, prev/next availability dimmed not removed, dark
// gradient placeholder when there is no art, single-entry timeline pushed by
// WidgetStatePublisher. The extension never links native-core and never
// touches the network. Tapping opens the player (deep link §9).

private let groupDefaults = UserDefaults(suiteName: AppGroup.id)

struct MediaWidgetEntry: TimelineEntry {
    let date: Date
    let title: String
    let artist: String
    let playing: Bool
    let canNext: Bool
    let canPrevious: Bool
    let artwork: Data?
    let hasSession: Bool

    /// Upstream's last-played fallback: the widget is never blank after a
    /// first play, so an empty snapshot renders the placeholder, not nothing.
    static let empty = MediaWidgetEntry(
        date: .now, title: "Amber", artist: "Nothing queued",
        playing: false, canNext: false, canPrevious: false,
        artwork: nil, hasSession: false
    )
}

struct MediaWidgetProvider: TimelineProvider {
    func placeholder(in context: Context) -> MediaWidgetEntry { snapshot() }

    func getSnapshot(in context: Context, completion: @escaping (MediaWidgetEntry) -> Void) {
        completion(snapshot())
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<MediaWidgetEntry>) -> Void) {
        // Single entry: freshness is push-driven by WidgetStatePublisher's
        // reloadTimelines, never polled (spec §9).
        completion(Timeline(entries: [snapshot()], policy: .never))
    }

    private func snapshot() -> MediaWidgetEntry {
        guard AppGroup.available else {
            return MediaWidgetEntry(
                date: .now, title: "Amber", artist: "Groupe d'app indisponible",
                playing: false, canNext: false, canPrevious: false,
                artwork: nil, hasSession: false
            )
        }
        guard let defaults = groupDefaults, let title = defaults.string(forKey: "widget.title") else {
            return .empty
        }
        return MediaWidgetEntry(
            date: .now,
            title: title,
            artist: defaults.string(forKey: "widget.artist") ?? "",
            playing: defaults.bool(forKey: "widget.playing"),
            canNext: defaults.bool(forKey: "widget.canNext"),
            canPrevious: defaults.bool(forKey: "widget.canPrevious"),
            artwork: defaults.string(forKey: "widget.artworkPath").flatMap { try? Data(contentsOf: URL(fileURLWithPath: $0)) },
            hasSession: true
        )
    }
}

struct WidgetTint {
    var background: Color
    var backgroundDeep: Color
    var accent: Color

    static let neutral = WidgetTint(
        background: Color(red: 0.22, green: 0.22, blue: 0.24),
        backgroundDeep: Color(red: 0.12, green: 0.12, blue: 0.14),
        accent: Color(white: 0.78)
    )

    static func from(_ data: Data?) -> WidgetTint {
        guard let data, let image = CIImage(data: data) else { return .neutral }
        let filter = CIFilter.areaAverage()
        filter.inputImage = image
        filter.extent = image.extent
        guard let output = filter.outputImage else { return .neutral }
        var pixel = [UInt8](repeating: 0, count: 4)
        CIContext().render(
            output, toBitmap: &pixel, rowBytes: 4,
            bounds: CGRect(x: 0, y: 0, width: 1, height: 1),
            format: .RGBA8, colorSpace: CGColorSpaceCreateDeviceRGB()
        )
        let r = Double(pixel[0]) / 255, g = Double(pixel[1]) / 255, b = Double(pixel[2]) / 255
        let maxC = max(r, g, b), minC = min(r, g, b)
        let delta = maxC - minC
        var hue = 0.0
        if delta > 0.0001 {
            if maxC == r { hue = ((g - b) / delta).truncatingRemainder(dividingBy: 6) }
            else if maxC == g { hue = (b - r) / delta + 2 }
            else { hue = (r - g) / delta + 4 }
            hue /= 6
            if hue < 0 { hue += 1 }
        }
        let saturation = maxC == 0 ? 0 : delta / maxC
        let tunedSaturation = min(1, max(saturation * 1.15, saturation > 0.08 ? 0.35 : 0))
        return WidgetTint(
            background: Color(hue: hue, saturation: tunedSaturation, brightness: 0.52),
            backgroundDeep: Color(hue: hue, saturation: min(1, tunedSaturation + 0.05), brightness: 0.36),
            accent: Color(hue: hue, saturation: tunedSaturation * 0.55, brightness: 0.92)
        )
    }
}

struct MediaWidgetView: View {
    var entry: MediaWidgetEntry
    @Environment(\.widgetFamily) private var family

    private var tint: WidgetTint {
        entry.hasSession ? WidgetTint.from(entry.artwork) : .neutral
    }

    var body: some View {
        content
            .widgetURL(URL(string: "amber://open-player"))
            .containerBackground(for: .widget) {
                LinearGradient(
                    colors: [tint.background, tint.backgroundDeep],
                    startPoint: .topLeading, endPoint: .bottomTrailing
                )
            }
    }

    @ViewBuilder
    private var content: some View {
        if entry.hasSession {
            if family == .systemSmall {
                smallContent
            } else {
                mediumContent
            }
        } else {
            VStack(spacing: 6) {
                Image(systemName: "music.note")
                    .foregroundStyle(.white.opacity(0.7))
                Text("Amber")
                    .font(.callout.weight(.semibold))
                    .foregroundStyle(.white)
                Text(entry.artist)
                    .font(.caption2)
                    .foregroundStyle(.white.opacity(0.7))
                    .multilineTextAlignment(.center)
            }
        }
    }

    private var mediumContent: some View {
        HStack(spacing: 16) {
            artwork
                .aspectRatio(1, contentMode: .fit)
                .clipShape(.rect(cornerRadius: 14, style: .continuous))
                .shadow(color: .black.opacity(0.25), radius: 6, y: 2)
            VStack(alignment: .leading, spacing: 2) {
                Spacer(minLength: 0)
                Text("NOW PLAYING")
                    .font(.system(size: 11, weight: .semibold))
                    .tracking(0.8)
                    .foregroundStyle(tint.accent)
                Text(entry.title)
                    .font(.system(size: 17, weight: .bold))
                    .foregroundStyle(.white)
                    .lineLimit(1)
                Text(entry.artist)
                    .font(.system(size: 17, weight: .medium))
                    .foregroundStyle(tint.accent)
                    .lineLimit(1)
                playPill
                    .padding(.top, 8)
                Spacer(minLength: 0)
            }
            Spacer(minLength: 0)
        }
        .overlay(alignment: .topTrailing) {
            Image(systemName: "music.note")
                .font(.system(size: 20, weight: .semibold))
                .foregroundStyle(.white)
        }
    }

    private var smallContent: some View {
        ZStack(alignment: .bottomLeading) {
            artwork
                .aspectRatio(1, contentMode: .fill)
                .clipShape(.rect(cornerRadius: 12, style: .continuous))
            playPill
                .padding(6)
        }
    }

    private var playPill: some View {
        Button(intent: WidgetTransportIntent(command: "toggle")) {
            HStack(spacing: 6) {
                Image(systemName: entry.playing ? "pause.fill" : "play.fill")
                    .font(.system(size: 14, weight: .bold))
                if family != .systemSmall {
                    Text(entry.playing ? "Pause" : "Play")
                        .font(.system(size: 17, weight: .semibold))
                }
            }
            .foregroundStyle(.white)
            .padding(.horizontal, family == .systemSmall ? 10 : 14)
            .padding(.vertical, 8)
            .background(.white.opacity(0.22), in: Capsule())
        }
        .buttonStyle(.plain)
    }

    @ViewBuilder
    private var artwork: some View {
        if let data = entry.artwork, let image = platformImage(data) {
            Image(platform: image)
                .resizable()
                .scaledToFill()
        } else {
            ZStack {
                LinearGradient(
                    colors: [Color(red: 0.12, green: 0.10, blue: 0.16),
                             Color(red: 0.05, green: 0.05, blue: 0.08)],
                    startPoint: .top, endPoint: .bottom
                )
                Image(systemName: "music.note")
                    .foregroundStyle(.white.opacity(0.55))
            }
        }
    }
}

#if os(macOS)
private func platformImage(_ data: Data) -> NSImage? { NSImage(data: data) }
private extension Image {
    init(platform image: NSImage) { self.init(nsImage: image) }
}
#else
private func platformImage(_ data: Data) -> UIImage? { UIImage(data: data) }
private extension Image {
    init(platform image: UIImage) { self.init(uiImage: image) }
}
#endif

struct AmberMediaWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "AmberMediaWidget", provider: MediaWidgetProvider()) { entry in
            MediaWidgetView(entry: entry)
        }
        .configurationDisplayName("Amber")
        .description("Now-playing state, transport controls, and a tap back into the player.")
        // systemSmall ~= upstream square, systemMedium ~= upstream wide (spec §9).
        .supportedFamilies([.systemSmall, .systemMedium])
    }
}

@main
struct AmberWidgetBundle: WidgetBundle {
    var body: some Widget {
        AmberMediaWidget()
    }
}

struct WidgetTransportIntent: AppIntent {
    static var title: LocalizedStringResource = "Amber Transport"
    static var isDiscoverable = false

    @Parameter(title: "Command")
    var command: String

    init() { command = "toggle" }
    init(command: String) { self.command = command }

    func perform() async throws -> some IntentResult {
        UserDefaults(suiteName: AppGroup.id)?.set(command, forKey: "widget.command")
        return .result()
    }
}
