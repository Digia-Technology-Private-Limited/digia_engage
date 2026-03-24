import DigiaEngage
import SwiftUI

@main
struct DigiaEngageSampleApp: App {
    init() {
        Task {
            try await Digia.initialize(
                DigiaConfig(
                    apiKey: "698b1b7979d23afa242dcc7d",
                    logLevel: .verbose,
                    flavor: .debug()
                )
            )
        }
    }

    var body: some Scene {
        WindowGroup {
            DigiaHost {
                DigiaInitialRouteScreen()
            }
        }
     }
}
