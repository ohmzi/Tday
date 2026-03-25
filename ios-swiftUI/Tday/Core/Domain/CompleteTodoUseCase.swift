import Foundation

struct CompleteTodoUseCase {
    private let todoRepository: TodoRepository

    init(todoRepository: TodoRepository) {
        self.todoRepository = todoRepository
    }

    func callAsFunction(_ todo: TodoItem) async throws {
        try await todoRepository.completeTodo(todo)
    }
}
