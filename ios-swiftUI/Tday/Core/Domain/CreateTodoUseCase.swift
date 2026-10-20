import Foundation

struct CreateTodoUseCase {
    private let todoRepository: TodoRepository

    init(todoRepository: TodoRepository) {
        self.todoRepository = todoRepository
    }

    func callAsFunction(_ payload: CreateTaskPayload) async throws {
        try await todoRepository.createTodo(payload: payload)
    }
}
