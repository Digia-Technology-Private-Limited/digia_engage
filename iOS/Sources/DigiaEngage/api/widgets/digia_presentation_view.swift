import SwiftUI

@MainActor
struct DigiaPresentationView: View {
    let presentation: DigiaViewPresentation

    var body: some View {
        if DigiaRuntime.shared.appConfigStore.component(presentation.viewID) != nil {
            DUIFactory.shared.createComponent(presentation.viewID)
        } else if DigiaRuntime.shared.appConfigStore.page(presentation.viewID) != nil {
            DUIFactory.shared.createPage(presentation.viewID)
        } else {
            VStack(alignment: .leading, spacing: 12) {
                if let title = presentation.title {
                    Text(title).font(.headline)
                }
                if let text = presentation.text {
                    Text(text)
                }
            }
            .padding(16)
        }
    }
}
