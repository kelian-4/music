import SwiftUI

#if os(iOS)
import UIKit
import MediaPlayer
import AVFAudio

@MainActor
@Observable
final class SystemVolume {
    static let shared = SystemVolume()

    var level: Double = Double(AVAudioSession.sharedInstance().outputVolume)

    @ObservationIgnored private var observation: NSKeyValueObservation?
    @ObservationIgnored fileprivate let volumeView = MPVolumeView(frame: CGRect(x: -2000, y: -2000, width: 1, height: 1))

    private init() {
        observation = AVAudioSession.sharedInstance().observe(\.outputVolume, options: [.new]) { [weak self] _, change in
            guard let value = change.newValue else { return }
            Task { @MainActor in self?.level = Double(value) }
        }
    }

    func refresh() {
        level = Double(AVAudioSession.sharedInstance().outputVolume)
    }

    func set(_ value: Double) {
        let clamped = min(max(value, 0), 1)
        level = clamped
        let slider = volumeView.subviews.compactMap { $0 as? UISlider }.first
        slider?.setValue(Float(clamped), animated: false)
        slider?.sendActions(for: .touchUpInside)
    }
}

struct SystemVolumeHost: UIViewRepresentable {
    func makeUIView(context: Context) -> UIView {
        let host = UIView(frame: .zero)
        host.isUserInteractionEnabled = false
        host.alpha = 0.01
        let view = SystemVolume.shared.volumeView
        view.removeFromSuperview()
        host.addSubview(view)
        return host
    }

    func updateUIView(_ uiView: UIView, context: Context) {}
}
#endif
