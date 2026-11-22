import Foundation
import UserNotifications
#if os(iOS)
import BackgroundTasks
#endif

@MainActor
final class NewReleaseNotifier {
    static let shared = NewReleaseNotifier()
    static let taskIdentifier = "app.bitchord.newreleases"

    private static let enabledKey = "new_release_notifications"
    private static let seenKey = "new_release_seen"
    private static let lastCheckKey = "new_release_last_check"
    private static let minimumInterval: TimeInterval = 6 * 3600
    private static let maxArtistsPerCheck = 80

    private var running = false

    var enabled: Bool { UserDefaults.standard.bool(forKey: Self.enabledKey) }

    func setEnabled(_ value: Bool) async -> Bool {
        if value {
            let granted = (try? await UNUserNotificationCenter.current()
                .requestAuthorization(options: [.alert, .sound, .badge])) ?? false
            guard granted else {
                UserDefaults.standard.set(false, forKey: Self.enabledKey)
                return false
            }
            UserDefaults.standard.set(true, forKey: Self.enabledKey)
            scheduleBackgroundRefresh()
            await check(force: true)
            return true
        }
        UserDefaults.standard.set(false, forKey: Self.enabledKey)
        #if os(iOS)
        BGTaskScheduler.shared.cancel(taskRequestWithIdentifier: Self.taskIdentifier)
        #endif
        return false
    }

    func checkIfDue() async {
        guard enabled else { return }
        let last = UserDefaults.standard.double(forKey: Self.lastCheckKey)
        guard Date().timeIntervalSince1970 - last >= Self.minimumInterval else { return }
        await check(force: false)
    }

    func scheduleBackgroundRefresh() {
        #if os(iOS)
        guard enabled else { return }
        let request = BGAppRefreshTaskRequest(identifier: Self.taskIdentifier)
        request.earliestBeginDate = Date(timeIntervalSinceNow: Self.minimumInterval)
        try? BGTaskScheduler.shared.submit(request)
        #endif
    }

    #if os(iOS)
    nonisolated static func registerBackgroundTask() {
        BGTaskScheduler.shared.register(forTaskWithIdentifier: taskIdentifier, using: nil) { task in
            guard let refresh = task as? BGAppRefreshTask else {
                task.setTaskCompleted(success: false)
                return
            }
            let work = Task { @MainActor in
                NewReleaseNotifier.shared.scheduleBackgroundRefresh()
                await NewReleaseNotifier.shared.check(force: false)
                refresh.setTaskCompleted(success: !Task.isCancelled)
            }
            refresh.expirationHandler = { work.cancel() }
        }
    }
    #endif

    private func check(force: Bool) async {
        guard enabled, !running else { return }
        if NetworkQuality.shared.metered { return }
        running = true
        defer { running = false }

        guard let shelves = try? await InnertubeFeed.shared.library(force: true),
              let shelf = libraryShelf(titled: "Artists", in: shelves) else { return }
        let artists = shelf.items.filter { $0.browseId != nil }
        guard !artists.isEmpty else { return }

        var seen = NewReleasePolicy.forgetUnfollowed(
            NewReleasePolicy.decode(UserDefaults.standard.string(forKey: Self.seenKey)),
            followed: artists.compactMap(\.browseId)
        )
        var findings: [(artist: String, releases: [ReleaseRef])] = []

        for artist in artists.prefix(Self.maxArtistsPerCheck) {
            if Task.isCancelled { break }
            guard let browseId = artist.browseId,
                  let page = try? await InnertubeDetail.shared.browseArtist(browseId: browseId, force: true) else { continue }
            let current = page.sections.flatMap(\.items).compactMap { card -> ReleaseRef? in
                guard let id = card.browseId, NewReleasePolicy.isRelease(id) else { return nil }
                return ReleaseRef(id: id, title: card.title)
            }
            let result = NewReleasePolicy.diff(seen: seen[browseId], current: current)
            seen[browseId] = result.seenAfter
            findings.append((artist.title, result.fresh))
        }

        UserDefaults.standard.set(NewReleasePolicy.encode(seen), forKey: Self.seenKey)
        UserDefaults.standard.set(Date().timeIntervalSince1970, forKey: Self.lastCheckKey)

        for finding in NewReleasePolicy.toAnnounce(findings) {
            await post(artist: finding.artist, releases: finding.releases)
        }
    }

    private func post(artist: String, releases: [ReleaseRef]) async {
        let content = UNMutableNotificationContent()
        content.title = artist
        if releases.count == 1, let only = releases.first {
            content.body = String(format: String(localized: "New release: %@"), only.title)
        } else {
            content.body = String(format: String(localized: "%lld new releases"), releases.count)
        }
        content.sound = .default
        let request = UNNotificationRequest(
            identifier: "new-release-\(UUID().uuidString)", content: content, trigger: nil
        )
        try? await UNUserNotificationCenter.current().add(request)
    }
}
