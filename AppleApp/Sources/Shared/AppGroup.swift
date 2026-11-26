import Foundation

enum AppGroup {
    static let id: String = {
        var candidates: [String] = []
        if let groups = Bundle.main.object(forInfoDictionaryKey: "ALTAppGroups") as? [String] {
            candidates += groups
        }
        let bundleId = Bundle.main.bundleIdentifier ?? ""
        let host = bundleId.split(separator: ".").filter { $0 != "widget" }.joined(separator: ".")
        if !host.isEmpty { candidates.append("group." + host) }
        candidates.append("group.app.amber.Amber")
        for candidate in candidates
        where FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: candidate) != nil {
            return candidate
        }
        return candidates.first ?? "group.app.amber.Amber"
    }()

    static var available: Bool {
        FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: id) != nil
    }
}
