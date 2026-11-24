import Foundation

enum AppGroup {
    static let id: String = {
        if let groups = Bundle.main.object(forInfoDictionaryKey: "ALTAppGroups") as? [String], let first = groups.first {
            return first
        }
        return "group.app.amber.Amber"
    }()
}
