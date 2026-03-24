import Foundation

enum ActionProcessorFactory {
    static func processorType(for action: DigiaActionModel) -> ActionType {
        action.actionType
    }
}
