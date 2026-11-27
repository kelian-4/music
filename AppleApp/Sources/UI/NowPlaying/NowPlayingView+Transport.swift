import SwiftUI
import AmberShared
import UniformTypeIdentifiers
import AVKit
import CoreImage
import CoreImage.CIFilterBuiltins
#if os(iOS)
import UIKit
import AVFAudio
#else
import AppKit
#endif

// Position, volume and transport controls, the More menu, and nerd stats.
extension NowPlayingView {
    var positionControls: some View { positionControls(showsStatus: true) }

    /// The scrubber with its timestamps. `showsStatus` puts the playback-source
    /// caption ("YouTube · Opus") in the timestamp row, centred between the two
    /// times, instead of on a line of its own above the deck.
    func positionControls(showsStatus: Bool) -> some View {
        PositionReadout { position in
        VStack(spacing: 6) {
            ThinSlider(
                value: position,
                maximum: controller.duration,
                mixing: controller.smartMixInProgress,
                transitionWindow: controller.smartTransitionWindow.flatMap { w in
                    w.end > w.start ? w.start...w.end : nil
                },
                onEditingChanged: { controller.seek(to: $0) },
                onDraggingChanged: { dragging in
                    lyricsScrubbing = dragging
                    if !dragging { lyricsControlActivity += 1 }
                }
            )
            .tint(.white)

            HStack(spacing: 8) {
                // Equal-width time columns keep the caption on the true centre.
                Text(Self.timestamp(position))
                    .frame(minWidth: 48, alignment: .leading)
                Spacer(minLength: 0)
                if showsStatus, showsPlaybackStatusLine, let status = songStatusLine {
                    Button { showPipeline = true } label: { Text(status) }
                        .buttonStyle(.plain)
                        .font(.caption2.weight(.medium))
                        .foregroundStyle(.white.opacity(0.6))
                        .lineLimit(1)
                        .truncationMode(.tail)
                        .accessibilityLabel(status)
                    Spacer(minLength: 0)
                }
                Text(remainingLabel(at: position))
                    .frame(minWidth: 48, alignment: .trailing)
            }
            .font(.caption.monospacedDigit().weight(.medium))
            .foregroundStyle(.white.opacity(0.7))
            .shadow(color: .black.opacity(0.4), radius: 3, y: 1)
        }
        }
    }

    private func remainingLabel(at position: Double) -> String {
        guard controller.duration > 0 else { return "-0:00" }
        return "-\(Self.timestamp(max(0, controller.duration - position)))"
    }

    /// The iOS volume row: upstream `VolumeRow` in the hairline capsule style
    /// of `ThinSlider`, bound to the controller's volume with the system
    /// speaker icons either side. The hardware buttons route through the same
    /// system volume this writes, so the two never disagree.
    var deckVolumeRow: some View { volumeRow(hugsGlyphs: false) }

    /// `hugsGlyphs` pins the speakers to the row's outer edges, so its ends are
    /// the speakers' own rather than their 16pt frames'.
    var volumeLevel: Double {
        #if os(iOS)
        SystemVolume.shared.level
        #else
        controller.volume
        #endif
    }

    func setVolumeLevel(_ value: Double) {
        #if os(iOS)
        SystemVolume.shared.set(value)
        #else
        controller.volume = value
        #endif
    }

    func volumeRow(hugsGlyphs: Bool) -> some View {
        HStack(spacing: 10) {
            Image(systemName: volumeLevel == 0 ? "speaker.slash.fill" : "speaker.fill")
                .scaledFont(size: 12, weight: .semibold)
                .foregroundStyle(.white.opacity(0.7))
                .frame(width: 16, height: 16, alignment: hugsGlyphs ? .leading : .center)
                .playerGlyph()
            ThinSlider(
                value: volumeLevel,
                maximum: 1,
                label: "Volume",
                onEditingChanged: { setVolumeLevel($0) },
                onDraggingChanged: { volumeDragging = $0 },
                onLiveChange: { setVolumeLevel($0) },
                valueText: { "\(Int($0 * 100)) %" },
                incrementStep: 0.1,
                decrementStep: 0.1
            )
            .tint(.white)
            Image(systemName: "speaker.wave.3.fill")
                .scaledFont(size: 12, weight: .semibold)
                .foregroundStyle(.white.opacity(0.7))
                .frame(width: 16, height: 16, alignment: hugsGlyphs ? .trailing : .center)
                .playerGlyph()
        }
        #if os(iOS)
        .background(SystemVolumeHost().frame(width: 1, height: 1))
        .onAppear {
            SystemVolume.shared.refresh()
            controller.volume = 1
        }
        #endif
        .accessibilityElement()
        .accessibilityLabel("Volume")
        .accessibilityValue("\(Int(volumeLevel * 100)) percent")
        .accessibilityAdjustableAction { direction in
            switch direction {
            case .increment: setVolumeLevel(min(1, volumeLevel + 0.1))
            case .decrement: setVolumeLevel(max(0, volumeLevel - 0.1))
            default: break
            }
        }
    }

    /// Music's order: shuffle · previous · play-in-circle · next · repeat.
    /// Play sits on a white disc so it never disappears into a bright wash.
    /// The transport row.
    ///
    /// Every control carries an explicit `accessibilityLabel` as well as a
    /// `.help()`. The help text is macOS-only, so on iOS these were unlabelled
    /// template images and VoiceOver announced the asset name — "bch shuffle,
    /// button" — rather than what the button does. The labels are the same
    /// strings, so the two platforms say the same thing.
    /// Transport: skip, play, skip — and nothing else.
    ///
    /// Shuffle and repeat are not here. They live in the queue-modes pill
    /// (`queueModesPill`), which is their single home on every platform and
    /// pane; repeating them beside the playback keys put the same two switches
    /// on screen twice with nothing saying they are the same switch.
    var playerTransport: some View {
        HStack(spacing: 14) {
            Button {
                Haptics.play(.skipPrevious)
                controller.previous()
            } label: {
                Image(.bchPrevious)
                    .resizable()
                    .scaledToFit()
                    .frame(width: 53, height: 53)
                    .scaleEffect(y: 0.85)
                    .playerGlyph()
            }
            .buttonStyle(PressScaleStyle())
            .disabled(!controller.canPlayPrevious)
            .help("Previous")
            .accessibilityLabel("Previous track")

            Button {
                Haptics.play(controller.isPlaying ? .pause : .resume)
                controller.togglePlayPause()
            } label: {
                ZStack {
                    if controller.isBuffering {
                        ProgressView()
                            .controlSize(.regular)
                            .tint(.white)
                            .frame(width: 38, height: 38)
                    } else {
                        Image(controller.isPlaying ? .bchTransportPause : .bchTransportPlay)
                            .resizable()
                            .scaledToFit()
                            .frame(width: 74, height: 74)
                            .playerGlyph()
                            .id(controller.isPlaying)
                            .transition(.scale(scale: 0.6).combined(with: .opacity))
                    }
                }
                .frame(width: 92, height: 92)
                .contentShape(Rectangle())
                .animation(.spring(response: 0.34, dampingFraction: 0.66), value: controller.isPlaying)
            }
            .buttonStyle(PressScaleStyle())
            .disabled(controller.current == nil && !controller.isBuffering)
            .help(controller.isPlaying ? "Pause" : "Play")
            .accessibilityLabel(controller.isBuffering ? "Buffering" : (controller.isPlaying ? "Pause" : "Play"))

            Button {
                Haptics.play(.skipNext)
                controller.next()
            } label: {
                Image(.bchNext)
                    .resizable()
                    .scaledToFit()
                    .frame(width: 53, height: 53)
                    .scaleEffect(y: 0.85)
                    .playerGlyph()
            }
            .buttonStyle(.plain)
            .disabled(!controller.canPlayNext)
            .help("Next")
            .accessibilityLabel("Next track")
        }
        .foregroundStyle(.white)
    }

    var repeatLabel: String {
        switch controller.repeatMode {
        case .one: "Repeat one"
        case .all: "Repeat all"
        case .off: "Repeat off"
        }
    }

    var moreMenu: some View {
        Menu {
            if let current = controller.current {
                SongActionButtons(entry: current, showSleepTimer: true, showDebugLog: false)
            }
            Divider()
            Menu {
                #if os(iOS)
                Toggle(isOn: Binding(
                    get: { controller.mixWithOtherAudio },
                    set: { controller.setMixWithOtherAudio($0) }
                )) {
                    Label("Mix with Other Apps", systemImage: "waveform")
                }
                #endif
                Toggle(isOn: $showMusicVideos) {
                    Label("Music Videos", systemImage: "play.rectangle")
                }
                Button(party.inParty ? "People Listening" : "Listen Together", systemImage: "person.2") {
                    if party.inParty {
                        appModel.partyMembersPresented = true
                    } else {
                        appModel.listenTogetherPresented = true
                    }
                }
                if !controller.displayedLyrics.isEmpty {
                    Button("Share Lyrics", systemImage: "quote.bubble") { sharePicking = true }
                    Button("Lyrics Offset…", systemImage: "slider.horizontal.3") { showLyricsOffset = true }
                }
                if let current = controller.current {
                    Button("Track Log…", systemImage: "doc.text") { toast.showTrackLog(current) }
                }
            } label: {
                Label("More Options", systemImage: "ellipsis.circle")
            }
        } label: {
            Image(systemName: "ellipsis")
                .scaledFont(size: 15, weight: .semibold)
                .foregroundStyle(.white)
                .frame(width: 34, height: 34)
                .background(.white.opacity(0.18), in: Circle())
                .contentShape(Circle())
        }
        .buttonStyle(.plain)
        .help("More")
    }

    static func timestamp(_ seconds: Double) -> String {
        guard seconds.isFinite, seconds >= 0 else { return "0:00" }
        let total = Int(seconds.rounded())
        let mins = total / 60
        let secs = total % 60
        let hours = mins / 60
        return hours > 0
            ? String(format: "%d:%02d:%02d", hours, mins % 60, secs)
            : String(format: "%d:%02d", mins, secs)
    }

    func nerdLine(_ nerd: NerdStatsRec) -> String {
        var parts = [nerd.codec]
        if nerd.kbps > 0 { parts.append("\(nerd.kbps) kbps") }
        if nerd.bitDepth > 0 { parts.append("\(nerd.bitDepth)-bit") }
        if nerd.sampleRate > 0 { parts.append("\(nerd.sampleRate) Hz") }
        if nerd.channels > 0 { parts.append("\(nerd.channels) ch") }
        if let source = controller.playbackSourceName { parts.append(source) }
        if controller.racingLossless { parts.append("Upgrading Quality") }
        if let gain = nerd.loudnessGainDb, gain != 0 {
            parts.append(String(format: "%+.1f dB", gain))
        }
        if let tier = controller.analysisTier, !tier.isEmpty { parts.append(tier) }
        if let sources = controller.analysisSources, !sources.isEmpty { parts.append(sources) }
        if let conf = controller.analysisConfidence { parts.append(String(format: "%.0f%% mix", conf * 100)) }
        if controller.smartMixInProgress { parts.append("Automix") }
        // Only when the transition actually cued the track somewhere other than
        // the top — which is now the minority of transitions, so a non-nil value
        // is the interesting one.
        if let cue = controller.automixCueSeconds, cue > 0.05 {
            parts.append(String(format: "in at %@", QueueEntry.formatDuration(cue)))
        }
        return parts.joined(separator: " · ")
    }
}

struct PositionReadout<Content: View>: View {
    @Environment(PlaybackController.self) private var controller
    private let content: (Double) -> Content

    init(@ViewBuilder content: @escaping (Double) -> Content) {
        self.content = content
    }

    var body: some View {
        content(controller.position)
    }
}
