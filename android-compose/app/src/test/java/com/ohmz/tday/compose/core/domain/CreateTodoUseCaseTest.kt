package com.ohmz.tday.compose.core.domain

import com.ohmz.tday.compose.core.data.todo.TodoRepository
import com.ohmz.tday.compose.core.model.CreateTaskPayload
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

class CreateTodoUseCaseTest {

    private val todoRepository = mockk<TodoRepository>()
    private val reminderScheduler = mockk<TaskReminderScheduler>()
    private val useCase = CreateTodoUseCase(todoRepository, reminderScheduler)

    @Test
    fun `invoke creates todo and reschedules reminders`() = runTest {
        val payload = makePayload()
        coEvery { todoRepository.createTodo(payload) } just Runs
        every { reminderScheduler.rescheduleAll() } just Runs

        useCase(payload)

        coVerify(exactly = 1) { todoRepository.createTodo(payload) }
        coVerify(exactly = 1) { reminderScheduler.rescheduleAll() }
    }

    @Test
    fun `invoke propagates exception from createTodo`() = runTest {
        val payload = makePayload()
        coEvery { todoRepository.createTodo(payload) } throws RuntimeException("network error")

        try {
            useCase(payload)
            fail("Expected exception")
        } catch (e: RuntimeException) {
            // expected
        }

        coVerify(exactly = 0) { reminderScheduler.rescheduleAll() }
    }

    @Test
    fun `invoke tolerates reschedule failure`() = runTest {
        val payload = makePayload()
        coEvery { todoRepository.createTodo(payload) } just Runs
        every { reminderScheduler.rescheduleAll() } throws RuntimeException("alarm fail")

        useCase(payload)

        coVerify(exactly = 1) { todoRepository.createTodo(payload) }
    }

    private fun makePayload() = CreateTaskPayload(
        title = "New task",
        priority = "Medium",
        dtstart = Instant.now(),
        due = Instant.now().plusSeconds(3600),
    )
}
