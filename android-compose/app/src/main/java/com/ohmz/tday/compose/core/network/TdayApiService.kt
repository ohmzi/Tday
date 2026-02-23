package com.ohmz.tday.compose.core.network

import com.ohmz.tday.compose.core.model.ChangePasswordRequest
import com.ohmz.tday.compose.core.model.AppSettingsResponse
import com.ohmz.tday.compose.core.model.AdminSettingsResponse
import com.ohmz.tday.compose.core.model.CompletedTodosResponse
import com.ohmz.tday.compose.core.model.CreateNoteRequest
import com.ohmz.tday.compose.core.model.CreateListRequest
import com.ohmz.tday.compose.core.model.CreateListResponse
import com.ohmz.tday.compose.core.model.CreateTodoRequest
import com.ohmz.tday.compose.core.model.CreateTodoResponse
import com.ohmz.tday.compose.core.model.CsrfResponse
import com.ohmz.tday.compose.core.model.DeleteCompletedTodoRequest
import com.ohmz.tday.compose.core.model.DeleteListRequest
import com.ohmz.tday.compose.core.model.DeleteTodoRequest
import com.ohmz.tday.compose.core.model.MessageResponse
import com.ohmz.tday.compose.core.model.MobileProbeResponse
import com.ohmz.tday.compose.core.model.NotesResponse
import com.ohmz.tday.compose.core.model.PreferencesDto
import com.ohmz.tday.compose.core.model.PreferencesResponse
import com.ohmz.tday.compose.core.model.ListsResponse
import com.ohmz.tday.compose.core.model.RegisterRequest
import com.ohmz.tday.compose.core.model.RegisterResponse
import com.ohmz.tday.compose.core.model.ReorderItemRequest
import com.ohmz.tday.compose.core.model.TodoCompleteRequest
import com.ohmz.tday.compose.core.model.TodoInstanceUpdateRequest
import com.ohmz.tday.compose.core.model.TodoPrioritizeRequest
import com.ohmz.tday.compose.core.model.TodoSummaryRequest
import com.ohmz.tday.compose.core.model.TodoSummaryResponse
import com.ohmz.tday.compose.core.model.TodoUncompleteRequest
import com.ohmz.tday.compose.core.model.TodosResponse
import com.ohmz.tday.compose.core.model.UpdateAdminSettingsRequest
import com.ohmz.tday.compose.core.model.UpdateCompletedTodoRequest
import com.ohmz.tday.compose.core.model.UpdateListRequest
import com.ohmz.tday.compose.core.model.UpdateTodoRequest
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
import retrofit2.http.HTTP
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

    @GET("/api/app-settings")
    suspend fun getAppSettings(): Response<AppSettingsResponse>

    @GET("/api/admin/settings")
    suspend fun getAdminSettings(): Response<AdminSettingsResponse>

    @PATCH("/api/admin/settings")
    suspend fun patchAdminSettings(
        @Body payload: UpdateAdminSettingsRequest,
    ): Response<AdminSettingsResponse>

    @POST("/api/todo/summary")
    suspend fun summarizeTodos(
        @Body payload: TodoSummaryRequest,
    ): Response<TodoSummaryResponse>

    @POST("/api/todo")
    suspend fun createTodo(
        @Body payload: CreateTodoRequest,
    ): Response<CreateTodoResponse>

    @PATCH("/api/todo")
    suspend fun patchTodoByBody(
        @Body payload: UpdateTodoRequest,
    ): Response<MessageResponse>

    @HTTP(method = "DELETE", path = "/api/todo", hasBody = true)
    suspend fun deleteTodoByBody(
        @Body payload: DeleteTodoRequest,
    ): Response<MessageResponse>

    @PATCH("/api/todo/complete")
    suspend fun completeTodoByBody(
        @Body payload: TodoCompleteRequest,
    ): Response<MessageResponse>

    @PATCH("/api/todo/uncomplete")
    suspend fun uncompleteTodoByBody(
        @Body payload: TodoUncompleteRequest,
    ): Response<MessageResponse>

    @PATCH("/api/todo/prioritize")
    suspend fun prioritizeTodoByBody(
        @Body payload: TodoPrioritizeRequest,
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

    @PATCH("/api/todo/instance")
    suspend fun patchTodoInstanceByBody(
        @Body payload: TodoInstanceUpdateRequest,
    ): Response<MessageResponse>

    @HTTP(method = "DELETE", path = "/api/todo/instance", hasBody = true)
    suspend fun deleteTodoInstanceByBody(
        @Body payload: DeleteTodoRequest,
    ): Response<MessageResponse>

    @GET("/api/completedTodo")
    suspend fun getCompletedTodos(): Response<CompletedTodosResponse>

    @PATCH("/api/completedTodo")
    suspend fun patchCompletedTodoByBody(
        @Body payload: UpdateCompletedTodoRequest,
    ): Response<MessageResponse>

    @HTTP(method = "DELETE", path = "/api/completedTodo", hasBody = true)
    suspend fun deleteCompletedTodoByBody(
        @Body payload: DeleteCompletedTodoRequest,
    ): Response<MessageResponse>

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

    @PATCH("/api/list")
    suspend fun patchListByBody(
        @Body payload: UpdateListRequest,
    ): Response<MessageResponse>

    @HTTP(method = "DELETE", path = "/api/list", hasBody = true)
    suspend fun deleteListByBody(
        @Body payload: DeleteListRequest,
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
