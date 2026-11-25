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

// Credits, the action row, and the output/party and queue-mode pills.
extension NowPlayingView {
    /// Title and artist, with the like and the overflow menu beside them.
    var creditsRow: some View {
        // Upstream's portrait credits: title and artist at the same 20pt, the
        // title bold and white, the artist medium at 55% white, each crawling
        // when it overflows (the artist a beat behind a crawling title), and
        // the star and the menu beside them as two 34pt glyph discs.
        let current = controller.current
        let artistColor = Color.white.opacity(0.55)
        return HStack(alignment: .center, spacing: 0) {
            VStack(alignment: .leading, spacing: 2) {
                HStack(spacing: 6) {
                    if current?.isExplicit == true {
                        ExplicitMark(color: artistColor)
                    }
                    Text(current?.title ?? "Nothing playing")
                        .font(.system(size: 20, weight: .bold))
                        .tracking(-0.2)
                        .foregroundStyle(.white)
                        .lineLimit(1)
                        .frame(maxWidth: .infinity, alignment: .leading)
                    // Only a track YouTube gave an album id for leads anywhere.
                    .contentShape(Rectangle())
                    .onTapGesture {
                        guard let albumId = current?.albumId else { return }
                        appModel.openPage(browseId: albumId, title: current?.albumName ?? "Album")
                    }
                    .accessibilityAddTraits(current?.albumId != nil ? .isButton : [])
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                ArtistCreditText(
                    credit: current?.artist ?? "",
                    artistId: current?.artistId,
                    font: .system(size: 20, weight: .medium),
                    color: artistColor
                )
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .id(current?.id)
            .transition(.opacity)
            .animation(.easeInOut(duration: 0.3), value: current?.id)
            if auth.signedIn, current?.videoId != nil {
                GlassCircleButton(
                    system: controller.isLiked ? "star.fill" : "star",
                    selected: controller.isLiked,
                    label: controller.isLiked ? "Remove from Liked" : "Like"
                ) {
                    Haptics.play(.tap)
                    controller.toggleLike()
                }
                .help(controller.isLiked ? "Remove from Liked Music" : "Like")
                .padding(.trailing, 8)
            }
            moreMenu
        }
        .padding(.horizontal, 24)
    }

    /// Below-volume pill, pane-reactive on every layout that carries one:
    /// the queue-modes pill (shuffle · repeat · AutoPlay · AutoMix) in the
    /// queue pane, output/party everywhere else. One definition so the
    /// portrait deck, compact landscape and desktop columns cannot disagree
    /// about which pill a pane shows — the desktop column used to hardcode
    /// the output pill, which is why pane toggles never changed anything on
    /// iPad landscape or macOS.
    var actionRowPill: some View {
        Group {
            outputPartyPill
        }
        .transition(.opacity.combined(with: .scale(scale: 0.94)))
    }

    /// Which of the three panes is showing.
    ///
    /// The artwork toggle is here rather than implied by the artwork's size: a
    /// control that only exists while a panel is closed cannot be used to open
    /// the first one, and one that only exists while a panel is open cannot be
    /// used to leave it.
    /// Lyrics and Up Next stay at the two ends. The middle pill follows the
    /// pane through `actionRowPill`, the same definition every layout uses.
    var playerActionRow: some View {
        VStack(spacing: 10) {
            GeometryReader { geometry in
                let edgeInset = actionRowEdgeInset(width: geometry.size.width)
                HStack {
                    actionGlyph(.bchLyrics, selected: pane == .lyrics, label: "Lyrics") {
                        Haptics.play(.expand)
                        pane = pane == .lyrics ? .main : .lyrics
                    }
                    Spacer(minLength: 0)
                    // The queue pane swaps the output/party pill for the
                    // queue-modes pill (shuffle · repeat · AutoPlay · AutoMix).
                    // This is the pill's only home, and it is per platform and
                    // pane alike: iPad used to be excluded here, which is why its
                    // queue pane showed output controls instead of these.
                    actionRowPill
                    Spacer(minLength: 0)
                    actionGlyph(.bchQueue, selected: pane == .queue, label: "Up Next") {
                        Haptics.play(.expand)
                        pane = pane == .queue ? .main : .queue
                    }
                }
                .padding(.horizontal, edgeInset)
            }
            .frame(height: 44)

            if !outputCaption.isEmpty {
                Text(outputCaption)
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(.white.opacity(0.62))
                    .lineLimit(1)
                    .accessibilityLabel(outputCaption)
            }
        }
        .frame(maxWidth: .infinity)
    }

    /// Where the action row's two 44pt glyph buttons start in from either edge.
    func actionRowEdgeInset(width: CGFloat) -> CGFloat {
        max(0, (width - (44 * 2 + 64)) / 4)
    }

    /// Where the Lyrics and Up Next glyphs' ink starts in from either edge:
    /// the button inset, the 26pt image's place in its 44pt button, and the
    /// 2 of 24 units each drawing leaves clear at its outer side.
    func actionGlyphInkInset(width: CGFloat) -> CGFloat {
        actionRowEdgeInset(width: width) + (44 - 26) / 2 + 26 * 2 / 24
    }

    func actionGlyph(
        _ image: ImageResource,
        selected: Bool,
        label: String,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            Image(image)
                .resizable()
                .scaledToFit()
                .frame(width: 26, height: 26)
                .foregroundStyle(.white.opacity(selected ? 1 : 0.75))
                .frame(width: 44, height: 44)
                .background(.white.opacity(selected ? 0.28 : 0), in: Circle())
                .contentShape(Circle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(label)
        .accessibilityAddTraits(selected ? [.isButton, .isSelected] : .isButton)
    }

    private var outputPartyPill: some View {
        Button {
            Haptics.play(.tap)
            showOutputDevice = true
        } label: {
            Image(systemName: "airplayaudio")
                .scaledFont(size: 24, weight: .regular)
                .foregroundStyle(.white.opacity(0.88))
                .frame(width: 64, height: 44)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel("Audio output")
    }

    /// The phone queue keeps its established shuffle/repeat/AutoPlay pill.
    /// iPad and desktop show these modes with the queue itself instead.
    var queueModesPill: some View {
        HStack(spacing: 0) {
            Button {
                Haptics.play(controller.shuffleEnabled ? .toggleOff : .toggleOn)
                controller.toggleShuffle()
            } label: {
                Image(.bchShuffle)
                    .resizable().scaledToFit()
                    .frame(width: 22, height: 22)
                    .foregroundStyle(.white.opacity(controller.shuffleEnabled ? 1 : 0.75))
                    .frame(width: 52, height: 44)
                    .background(.white.opacity(controller.shuffleEnabled ? 0.14 : 0))
            }
            .buttonStyle(.plain)
            .accessibilityLabel(controller.shuffleEnabled ? "Shuffle on" : "Shuffle off")

            pillDivider

            Button {
                Haptics.play(.select)
                controller.cycleRepeat()
            } label: {
                Group {
                    if controller.repeatMode == .one {
                        Text("1").scaledFont(size: 19, weight: .bold)
                    } else {
                        Image(.bchRepeat).resizable().scaledToFit().frame(width: 22, height: 22)
                    }
                }
                .foregroundStyle(.white.opacity(controller.repeatMode == .off ? 0.75 : 1))
                .frame(width: 52, height: 44)
                .background(.white.opacity(controller.repeatMode == .off ? 0 : 0.14))
            }
            .buttonStyle(.plain)
            .accessibilityLabel(repeatLabel)

            pillDivider

            Button {
                Haptics.play(controller.autoplayEnabled ? .toggleOff : .toggleOn)
                controller.toggleAutoplay()
            } label: {
                Image(.bchInfinity)
                    .resizable().scaledToFit()
                    .frame(width: 22, height: 22)
                    .foregroundStyle(.white.opacity(controller.autoplayEnabled ? 1 : 0.75))
                    .frame(width: 52, height: 44)
                    .background(.white.opacity(controller.autoplayEnabled ? 0.14 : 0))
            }
            .buttonStyle(.plain)
            .accessibilityLabel(controller.autoplayEnabled ? "AutoPlay on" : "AutoPlay off")

            pillDivider

            // AutoMix is the fourth and last slot, at the right end of the pill.
            Button {
                Haptics.play(controller.automixEnabled ? .toggleOff : .toggleOn)
                controller.toggleAutomix()
            } label: {
                Image(.bchVinylRecord)
                    .resizable().scaledToFit()
                    .frame(width: 22, height: 22)
                    .foregroundStyle(.white.opacity(controller.automixEnabled ? 1 : 0.75))
                    .frame(width: 52, height: 44)
                    .background(.white.opacity(controller.automixEnabled ? 0.14 : 0))
            }
            .buttonStyle(.plain)
            .accessibilityLabel(controller.automixEnabled ? "AutoMix on" : "AutoMix off")
            .help(controller.automixEnabled
                ? "AutoMix blends the end of one track into the next"
                : "AutoMix off — tracks change at the end")
        }
        .background(.white.opacity(0.12))
        .clipShape(Capsule())
        .accessibilityElement(children: .contain)
    }

    private var pillDivider: some View {
        Rectangle().fill(.white.opacity(0.20)).frame(width: 1, height: 44)
    }

    var outputCaption: String { "" }
}
