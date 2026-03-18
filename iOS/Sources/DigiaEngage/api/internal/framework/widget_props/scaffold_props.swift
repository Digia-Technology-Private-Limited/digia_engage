import Foundation

struct ScaffoldProps: Codable, Equatable, Sendable {
    let scaffoldBackgroundColor: ExprOr<String>?
    let enableSafeArea: ExprOr<Bool>?
    let resizeToAvoidBottomInset: ExprOr<Bool>?
    let body: String?
    let appBar: String?
    let drawer: String?
    let endDrawer: String?
    let bottomNavigationBar: String?
    let persistentFooterButtons: [String]?
}
