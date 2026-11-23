import SwiftUI

extension SettingsView {
    var newReleasesBinding: Binding<Bool> {
        Binding(
            get: { newReleaseNotifications },
            set: { value in
                newReleaseNotifications = value
                Task {
                    let result = await NewReleaseNotifier.shared.setEnabled(value)
                    newReleaseNotifications = result
                }
            }
        )
    }

    func buildNewReleaseRows(_ rows: inout SettingsRows) {
        rows.row("New Release Notifications", "notifications", "releases", "artists", "followed", "alerts") {
            SettingsToggleLine(
                glyph: .update,
                title: "New Release Notifications",
                subtitle: "Get notified when an artist you follow releases something new. Checked on Wi-Fi only",
                isOn: newReleasesBinding
            )
        }
    }
}
