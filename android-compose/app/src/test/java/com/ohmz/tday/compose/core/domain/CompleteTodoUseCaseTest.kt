package com.ohmz.tday.compose.core.domain

import com.ohmz.tday.compose.core.data.todo.TodoRepository
import com.ohmz.tday.compose.core.model.TodoItem
import com.ohmz.tday.compose.core.notification.TaskReminderScheduler
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.Runs
import kotlinx.coroutines.test.runTest
import org.junit.Assert.fail
import org.junit.Test
import java.time.Instant

class CompleteTodoUseCaseTest {

    private val todoRepository = mockk<TodoRepository>()
    private val reminderScheduler = mockk<TaskReminderScheduler>()
    private val useCase = CompleteTodoUseCase(todoRepository, reminderScheduler)

    @Test
    fun `invoke completes todo and reschedules reminders`() = runTest {
        val todo = makeTodo()
        coEvery { todoRepository.completeTodo(todo) } just Runs
        every { reminderScheduler.rescheduleAll() } just Runs

        useCase(todo)

        coVerify(exactly = 1) { todoRepository.completeTodo(todo) }
        coVerify(exactly = 1) { reminderScheduler.rescheduleAll() }
    }

    @Test
    fun `invoke propagates exception from completeTodo`() = runTest {
        val todo = makeTodo()
        coEvery { todoRepository.completeTodo(todo) } throws RuntimeException("network error")

        try {
            useCase(todo)
            fail("Expected exception")
        } catch (e: RuntimeException) {
            // expected
        }

        coVerify(exactly = 0) { reminderScheduler.rescheduleAll() }
    }

    @Test
    fun `invoke tolerates reschedule failure`() = runTest {
        val todo = makeTodo()
        coEvery { todoRepository.completeTodo(todo) } just Runs
        every { reminderScheduler.rescheduleAll() } throws RuntimeException("alarm fail")

        useCase(todo)

        coVerify(exactly = 1) { todoRepository.completeTodo(todo) }
    }

    private fun makeTodo() = TodoItem(
        id = "t1",
        canonicalId = "t1",
        title = "Test",
        description = null,
        priority = "Low",
        dtstart = Instant.now(),
        due = Instant.now().plusSeconds(3600),
        rrule = null,
        instanceDate = null,
        pinned = false,
        completed = false,
        listId = null,
    )
}
