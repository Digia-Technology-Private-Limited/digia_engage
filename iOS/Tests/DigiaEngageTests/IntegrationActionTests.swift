import Foundation
@testable import DigiaEngage
import Testing

@MainActor
@Suite("Integration Actions")
struct IntegrationActionTests {
    @Test("postMessage publishes payload to Digia public listener")
    func postMessagePublishes() async throws {
        DigiaRuntime.shared.resetForTesting()
        let received = ScopeValueBox()
        let token = Digia.onMessage("checkout") { payload in
            received.value = payload
        }

        let action = PostMessageAction(
            disableActionIf: nil,
            data: ["name": .string("checkout"), "payload": .object(["id": .string("A1")])]
        )
        try await PostMessageProcessor().execute(action: action, context: context())

        #expect(received.value == .object(["id": .string("A1")]))
        Digia.removeMessageListener("checkout", token: token)
    }

    @Test("share action stores the share request")
    func shareActionStoresRequest() async throws {
        DigiaRuntime.shared.resetForTesting()
        try await ShareContentProcessor().execute(
            action: ShareContentAction(disableActionIf: nil, data: ["message": .string("Shared from Action"), "subject": .string("TEST")]),
            context: context()
        )
        #expect(DigiaRuntime.shared.lastShareRequest?.message == "Shared from Action")
        #expect(DigiaRuntime.shared.lastShareRequest?.subject == "TEST")
    }

    @Test("callRestApi executes nested onSuccess action flow")
    func callRestApiExecutesOnSuccessFlow() async throws {
        let fixtureDir = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        try FileManager.default.createDirectory(at: fixtureDir, withIntermediateDirectories: true)
        let responseURL = fixtureDir.appendingPathComponent("test.json")
        try Data(#"{"hello":"world"}"#.utf8).write(to: responseURL)
        defer { try? FileManager.default.removeItem(at: fixtureDir) }

        DigiaRuntime.shared.resetForTesting()
        let configPath = try makeTempConfigFile("""
        {
          "appSettings": { "initialRoute": "home" },
          "pages": { "home": { "uid": "home" } },
          "rest": {
            "resources": {
              "api_1": {
                "id": "api_1",
                "url": "\(responseURL.absoluteString)",
                "method": "GET"
              }
            }
          },
          "theme": { "colors": { "light": {} } }
        }
        """)
        Digia.initialize(
            DigiaConfig(
                apiKey: "rest_test",
                flavor: .release(initStrategy: .localFirst, appConfigPath: configPath, functionsPath: "unused")
            )
        )

        try await CallRestApiProcessor().execute(
            action: CallRestApiAction(
                disableActionIf: nil,
                data: [
                    "dataSource": .object(["id": .string("api_1")]),
                    "onSuccess": .object([
                        "steps": .array([
                            .object([
                                "type": .string("Action.showToast"),
                                "data": .object(["message": .string("${response.body.hello}")])
                            ])
                        ])
                    ])
                ]
            ),
            context: context(appConfig: DigiaRuntime.shared.appConfigStore)
        )

        #expect(DigiaRuntime.shared.controller.activeToast?.message == "world")
    }

    private func context(appConfig: AppConfigStore = AppConfigStore()) -> ActionProcessorContext {
        ActionProcessorContext(
            appConfig: appConfig,
            widgetHierarchy: [],
            currentEntityId: nil
        )
    }
}

private final class ScopeValueBox: @unchecked Sendable {
    var value: ScopeValue?
}
