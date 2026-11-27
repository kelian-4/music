import Foundation

final class PageCache: @unchecked Sendable {
    static let shared = PageCache()
    private struct Entry { let value: any Sendable; let saved: Date }
    private let lock = NSLock()
    private var entries: [String: Entry] = [:]
    private let capacity = 80

    func get(_ key: String, maxAge: TimeInterval) -> (any Sendable)? {
        lock.lock(); defer { lock.unlock() }
        guard let entry = entries[key], Date().timeIntervalSince(entry.saved) <= maxAge else { return nil }
        return entry.value
    }

    func put(_ key: String, _ value: any Sendable) {
        lock.lock(); defer { lock.unlock() }
        entries[key] = Entry(value: value, saved: Date())
        if entries.count > capacity, let oldest = entries.min(by: { $0.value.saved < $1.value.saved })?.key {
            entries.removeValue(forKey: oldest)
        }
    }

    func clear() {
        lock.lock(); defer { lock.unlock() }
        entries.removeAll()
    }
}

/// Pages stay for a short while so a page just visited reopens at once; on a
/// failed fetch the last copy is shown rather than an error.
@MainActor
enum PageRequests {
    static let freshSeconds: TimeInterval = 180
    static let staleSeconds: TimeInterval = 6 * 3600

    static func load<T: Sendable>(
        _ name: String, force: Bool = false,
        network: @escaping @Sendable () async throws -> T
    ) async throws -> T {
        let context = PageSession.capture()
        let key = context.partition + ":\(context.generation):" + name + ":" + String(reflecting: T.self)
        let volatile = name.hasPrefix("library") || name == "history"
        if !force, !volatile, let hit = PageCache.shared.get(key, maxAge: freshSeconds) as? T {
            return hit
        }
        let started = Date()
        do {
            let value: T = try await PageRequestCoordinator.shared.fetch(name, context: context,
                currentGeneration: { PageSession.generation() }, network: network)
            guard PageSession.generation() == context.generation else { throw CancellationError() }
            PageCache.shared.put(key, value)
            NetworkQuality.shared.noteLatency(Date().timeIntervalSince(started), failed: false)
            Task { await LaunchReadiness.shared.contentAppeared() }
            return value
        } catch is CancellationError {
            throw CancellationError()
        } catch {
            NetworkQuality.shared.noteLatency(Date().timeIntervalSince(started), failed: true)
            if let stale = PageCache.shared.get(key, maxAge: staleSeconds) as? T { return stale }
            throw error
        }
    }
}
