import SwiftUI

struct ConnectionBanner: View {
    private let network = NetworkQuality.shared
    @State private var tick = 0

    private var state: (icon: String, text: LocalizedStringKey)? {
        if !network.connected { return ("wifi.slash", "No connection. Showing saved content.") }
        if network.slow { return ("tortoise.fill", "Slow connection. Loading may take longer.") }
        return nil
    }

    var body: some View {
        VStack {
            if let state {
                Label(state.text, systemImage: state.icon)
                    .font(.footnote.weight(.semibold))
                    .padding(.horizontal, 14)
                    .padding(.vertical, 8)
                    .background(.regularMaterial, in: Capsule())
                    .overlay(Capsule().strokeBorder(.white.opacity(0.12)))
                    .shadow(color: .black.opacity(0.18), radius: 10, y: 4)
                    .padding(.top, 6)
                    .transition(.move(edge: .top).combined(with: .opacity))
                    .id(state.icon)
            }
            Spacer(minLength: 0)
        }
        .animation(.spring(response: 0.42, dampingFraction: 0.82), value: network.connected)
        .animation(.spring(response: 0.42, dampingFraction: 0.82), value: network.slow)
        .allowsHitTesting(false)
        .task {
            while !Task.isCancelled {
                try? await Task.sleep(for: .seconds(10))
                network.expireSlow()
            }
        }
    }
}
