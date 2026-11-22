import Foundation

enum DownloadRetryPolicy {
    static let maxAttempts = 6
    private static let baseSeconds: Double = 5
    private static let factor: Double = 3
    private static let maxSeconds: Double = 600

    static func backoff(afterAttempts attempts: Int) -> TimeInterval {
        guard attempts > 0 else { return 0 }
        var wait = baseSeconds
        for _ in 1..<max(attempts, 1) { wait = min(wait * factor, maxSeconds) }
        return min(wait, maxSeconds)
    }

    static func shouldRetry(afterAttempts attempts: Int) -> Bool { attempts < maxAttempts }

    static func nextWake(retryDates: [Date?], now: Date = Date()) -> TimeInterval? {
        retryDates.compactMap { $0 }.filter { $0 > now }.min().map { $0.timeIntervalSince(now) }
    }
}
