import SwiftUI

struct DiagnosticReportsView: View {
    @Environment(PlaybackController.self) private var controller
    @State private var report: URL?
    @State private var message: String?
    @State private var probeLines: [String] = []
    @State private var probing = false
    var body: some View {
        Form {
            Section {
                Text("Recent technical events stay on this device. Reports exclude credentials and private paths. Audio capture is available separately in Audio Pipeline.")
                Button("Save Diagnostic Report") {
                    do { report = try PlaybackDebugLog.shared.saveReport(snapshot: controller.diagnosticSnapshot) }
                    catch { message = error.localizedDescription }
                }
                if let report { ShareLink("Share Diagnostic Report", item: report) }
                if let message { Text(FriendlyError.display(message)).foregroundStyle(.red) }
            }
            Section {
                Button(probing ? "Testing…" : "Test YouTube Music Connection") {
                    Task { await runProbe() }
                }
                .disabled(probing)
                ForEach(probeLines, id: \.self) { line in
                    Text(verbatim: line).font(.caption.monospaced()).textSelection(.enabled)
                }
            }
            Section {
                Button("Clear Diagnostic History", role: .destructive) {
                    PlaybackDebugLog.shared.clearHistory(); report = nil; message = "Diagnostic history cleared."
                }
            }
        }.navigationTitle("Diagnostic Reports")
    }

    private func runProbe() async {
        probing = true
        probeLines = []
        defer { probing = false }
        func timed(_ name: String, _ work: () async throws -> String) async {
            let start = Date()
            do {
                let detail = try await work()
                probeLines.append("\(name): OK \(String(format: "%.1f", Date().timeIntervalSince(start))) s \(detail)")
            } catch {
                probeLines.append("\(name): \(String(format: "%.1f", Date().timeIntervalSince(start))) s \(String(describing: error).prefix(300))")
            }
        }
        await timed("search") { "\(try await InnertubeSearch.shared.search("test", scope: "songs").count) results" }
        await timed("browse") { "\(try await InnertubeDetail.shared.browse(browseId: "FEmusic_new_releases_albums", force: true).title)" }
        await timed("home") { "\(try await InnertubeFeed.shared.home().shelves.count) shelves" }
    }
}
