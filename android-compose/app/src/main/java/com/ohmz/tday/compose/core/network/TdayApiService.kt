package com.ohmz.tday.compose.core.network

import com.ohmz.tday.compose.core.model.ChangePasswordRequest
import com.ohmz.tday.compose.core.model.CompletedTodosResponse
import com.ohmz.tday.compose.core.model.CreateNoteRequest
import com.ohmz.tday.compose.core.model.CreateListRequest
import com.ohmz.tday.compose.core.model.CreateListResponse
import com.ohmz.tday.compose.core.model.CreateTodoRequest
import com.ohmz.tday.compose.core.model.CreateTodoResponse
import com.ohmz.tday.compose.core.model.CsrfResponse
import com.ohmz.tday.compose.core.model.MessageResponse
import com.ohmz.tday.compose.core.model.MobileProbeResponse
import com.ohmz.tday.compose.core.model.NotesResponse
import com.ohmz.tday.compose.core.model.PreferencesDto
import com.ohmz.tday.compose.core.model.PreferencesResponse
import com.ohmz.tday.compose.core.model.ListsResponse
import com.ohmz.tday.compose.core.model.RegisterRequest
import com.ohmz.tday.compose.core.model.RegisterResponse
import com.ohmz.tday.compose.core.model.ReorderItemRequest
import com.ohmz.tday.compose.core.model.TodoInstanceRequest
import com.ohmz.tday.compose.core.model.TodosResponse
import com.ohmz.tday.compose.core.model.UpdateProfileRequest
import com.ohmz.tday.compose.core.model.UserResponse
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.FieldMap
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Url

interface TdayApiService {
    @GET
    suspend fun probeServer(
        @Url probeUrl: String,
        @Header("X-Tday-No-Rewrite") noRewrite: String = "1",
    ): Response<MobileProbeResponse>

    @GET("/api/auth/csrf")
    suspend fun getCsrfToken(): Response<CsrfResponse>

    @FormUrlEncoded
    @POST("/api/auth/callback/credentials")
    suspend fun signInWithCredentials(
        @Header("X-Auth-Return-Redirect") returnRedirect: String = "1",
        @FieldMap payload: Map<String, String>,
    ): Response<JsonElement>

    @GET("/api/auth/session")
    suspend fun getSession(): Response<JsonElement>

    @FormUrlEncoded
    @POST("/api/auth/signout")
    suspend fun signOut(
        @Header("X-Auth-Return-Redirect") returnRedirect: String = "1",
        @FieldMap payload: Map<String, String>,
    ): Response<JsonElement>

    @POST("/api/auth/register")
    suspend fun register(
        @Body payload: RegisterRequest,
    ): Response<RegisterResponse>

    @GET("/api/todo")
    suspend fun getTodos(
        @Query("start") start: Long? = null,
        @Query("end") end: Long? = null,
        @Query("timeline") timeline: Boolean? = null,
        @Query("recurringFutureDays") recurringFutureDays: Int? = null,
    ): Response<TodosResponse>

    @POST("/api/todo")
    suspend fun createTodo(
        @Body payload: CreateTodoRequest,
    ): Response<CreateTodoResponse>

    @PATCH("/api/todo/{id}")
    suspend fun patchTodo(
        @Path("id") todoId: String,
        @Body payload: JsonObject,
    ): Response<MessageResponse>

    @DELETE("/api/todo/{id}")
    suspend fun deleteTodo(
        @Path("id") todoId: String,
    ): Response<MessageResponse>

    @PATCH("/api/todo/{id}/complete")
    suspend fun completeTodo(
        @Path("id") todoId: String,
        @Body payload: JsonObject,
    ): Response<MessageResponse>

    @PATCH("/api/todo/{id}/uncomplete")
    suspend fun uncompleteTodo(
        @Path("id") todoId: String,
        @Body payload: JsonObject,
    ): Response<MessageResponse>

    @PATCH("/api/todo/reorder")
    suspend fun reorderTodos(
        @Body payload: List<ReorderItemRequest>,
    ): Response<MessageResponse>

    @GET("/api/todo/overdue")
    suspend fun getOverdueTodos(
        @Query("start") start: Long,
        @Query("end") end: Long,
    ): Response<TodosResponse>

    @PATCH("/api/todo/instance/{id}")
    suspend fun patchTodoInstance(
        @Path("id") todoId: String,
        @Body payload: JsonObject,
    ): Response<MessageResponse>

    @DELETE("/api/todo/instance/{id}")
    suspend fun deleteTodoInstance(
        @Path("id") todoId: String,
        @Query("instanceDate") instanceDate: Long,
    ): Response<MessageResponse>

    @PATCH("/api/todo/instance/{id}/complete")
    suspend fun completeTodoInstance(
        @Path("id") todoId: String,
        @Body payload: TodoInstanceRequest,
    ): Response<MessageResponse>

    @PATCH("/api/todo/instance/{id}/uncomplete")
    suspend fun uncompleteTodoInstance(
        @Path("id") todoId: String,
        @Body payload: TodoInstanceRequest,
    ): Response<MessageResponse>

    @PATCH("/api/todo/instance/{id}/prioritize")
    suspend fun prioritizeTodoInstance(
        @Path("id") todoId: String,
        @Query("priority") priority: String,
        @Query("instanceDate") instanceDate: Long,
    ): Response<MessageResponse>

    @GET("/api/completedTodo")
    suspend fun getCompletedTodos(): Response<CompletedTodosResponse>

    @GET("/api/note")
    suspend fun getNotes(): Response<NotesResponse>

    @POST("/api/note")
    suspend fun createNote(
        @Body payload: CreateNoteRequest,
    ): Response<MessageResponse>

    @PATCH("/api/note/{id}")
    suspend fun patchNote(
        @Path("id") noteId: String,
        @Body payload: JsonObject,
    ): Response<MessageResponse>

    @DELETE("/api/note/{id}")
    suspend fun deleteNote(
        @Path("id") noteId: String,
    ): Response<MessageResponse>

    @GET("/api/list")
    suspend fun getLists(): Response<ListsResponse>

    @GET("/api/list/{id}")
    suspend fun getListTodos(
        @Path("id") listId: String,
        @Query("start") start: Long,
        @Query("end") end: Long,
    ): Response<TodosResponse>

    @POST("/api/list")
    suspend fun createList(
        @Body payload: CreateListRequest,
    ): Response<CreateListResponse>

    @PATCH("/api/list/{id}")
    suspend fun patchList(
        @Path("id") listId: String,
        @Body payload: JsonObject,
    ): Response<MessageResponse>

    @DELETE("/api/list/{id}")
    suspend fun deleteList(
        @Path("id") listId: String,
    ): Response<MessageResponse>

    @GET("/api/preferences")
    suspend fun getPreferences(): Response<PreferencesResponse>

    @PATCH("/api/preferences")
    suspend fun patchPreferences(
        @Body payload: PreferencesDto,
    ): Response<PreferencesResponse>

    @GET("/api/user")
    suspend fun getUserDetails(): Response<UserResponse>

    @PATCH("/api/user/profile")
    suspend fun patchUserProfile(
        @Body payload: UpdateProfileRequest,
    ): Response<JsonObject>

    @POST("/api/user/change-password")
    suspend fun changePassword(
        @Body payload: ChangePasswordRequest,
    ): Response<MessageResponse>

    @GET("/api/timezone")
    suspend fun syncTimezone(
        @Header("X-User-Timezone") timezone: String,
    ): Response<JsonObject>
}
