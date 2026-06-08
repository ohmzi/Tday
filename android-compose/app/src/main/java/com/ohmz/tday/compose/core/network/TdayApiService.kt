package com.ohmz.tday.compose.core.network

import com.ohmz.tday.compose.core.model.AppSettingsResponse
import com.ohmz.tday.compose.core.model.ChangePasswordRequest
import com.ohmz.tday.compose.core.model.CompletedFloatersResponse
import com.ohmz.tday.compose.core.model.CompletedTodosResponse
import com.ohmz.tday.compose.core.model.CreateFloaterListRequest
import com.ohmz.tday.compose.core.model.CreateFloaterListResponse
import com.ohmz.tday.compose.core.model.CreateFloaterRequest
import com.ohmz.tday.compose.core.model.CreateFloaterResponse
import com.ohmz.tday.compose.core.model.CreateListRequest
import com.ohmz.tday.compose.core.model.CreateListResponse
import com.ohmz.tday.compose.core.model.CreateTodoRequest
import com.ohmz.tday.compose.core.model.CreateTodoResponse
import com.ohmz.tday.compose.core.model.CredentialKeyResponse
import com.ohmz.tday.compose.core.model.CredentialsCallbackRequest
import com.ohmz.tday.compose.core.model.CsrfResponse
import com.ohmz.tday.compose.core.model.DeleteCompletedFloaterRequest
import com.ohmz.tday.compose.core.model.DeleteCompletedTodoRequest
import com.ohmz.tday.compose.core.model.DeleteFloaterListRequest
import com.ohmz.tday.compose.core.model.DeleteFloaterListResponse
import com.ohmz.tday.compose.core.model.DeleteFloaterRequest
import com.ohmz.tday.compose.core.model.DeleteListRequest
import com.ohmz.tday.compose.core.model.DeleteListResponse
import com.ohmz.tday.compose.core.model.DeleteTodoRequest
import com.ohmz.tday.compose.core.model.FloaterCompleteRequest
import com.ohmz.tday.compose.core.model.FloaterListDetailResponse
import com.ohmz.tday.compose.core.model.FloaterListsResponse
import com.ohmz.tday.compose.core.model.FloaterPrioritizeRequest
import com.ohmz.tday.compose.core.model.FloaterReorderRequest
import com.ohmz.tday.compose.core.model.FloaterUncompleteRequest
import com.ohmz.tday.compose.core.model.FloatersResponse
import com.ohmz.tday.compose.core.model.ListDetailResponse
import com.ohmz.tday.compose.core.model.ListsResponse
import com.ohmz.tday.compose.core.model.MessageResponse
import com.ohmz.tday.compose.core.model.MobileProbeResponse
import com.ohmz.tday.compose.core.model.PreferencesDto
import com.ohmz.tday.compose.core.model.PreferencesResponse
import com.ohmz.tday.compose.core.model.RegisterRequest
import com.ohmz.tday.compose.core.model.RegisterResponse
import com.ohmz.tday.compose.core.model.ReorderItemRequest
import com.ohmz.tday.compose.core.model.RequestAdminResetRequest
import com.ohmz.tday.compose.core.model.SecurityQuestionStatusResponse
import com.ohmz.tday.compose.core.model.SecurityQuestionsResponse
import com.ohmz.tday.compose.core.model.SelfServiceResetRequest
import com.ohmz.tday.compose.core.model.SetSecurityQuestionsRequest
import com.ohmz.tday.compose.core.model.TodoCompleteRequest
import com.ohmz.tday.compose.core.model.TodoInstanceDeleteRequest
import com.ohmz.tday.compose.core.model.TodoInstanceUpdateRequest
import com.ohmz.tday.compose.core.model.TodoPrioritizeRequest
import com.ohmz.tday.compose.core.model.TodoSummaryRequest
import com.ohmz.tday.compose.core.model.TodoSummaryResponse
import com.ohmz.tday.compose.core.model.TodoTitleNlpRequest
import com.ohmz.tday.compose.core.model.TodoTitleNlpResponse
import com.ohmz.tday.compose.core.model.TodoUncompleteRequest
import com.ohmz.tday.compose.core.model.TodosResponse
import com.ohmz.tday.compose.core.model.UpdateCompletedFloaterRequest
import com.ohmz.tday.compose.core.model.UpdateCompletedTodoRequest
import com.ohmz.tday.compose.core.model.UpdateFloaterListRequest
import com.ohmz.tday.compose.core.model.UpdateFloaterRequest
import com.ohmz.tday.compose.core.model.UpdateListRequest
import com.ohmz.tday.compose.core.model.UpdateProfileRequest
import com.ohmz.tday.compose.core.model.UpdateTodoRequest
import com.ohmz.tday.compose.core.model.UserResponse
import com.ohmz.tday.compose.core.model.VerifySecurityAnswersRequest
import com.ohmz.tday.compose.core.model.VerifySecurityAnswersResponse
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.HTTP
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

    @GET("/api/mobile/probe")
    suspend fun probeConfiguredServer(): Response<MobileProbeResponse>

    @GET("/api/auth/csrf")
    suspend fun getCsrfToken(): Response<CsrfResponse>

    @GET("/api/auth/credentials-key")
    suspend fun getCredentialKey(): Response<CredentialKeyResponse>

    @POST("/api/auth/callback/credentials")
    suspend fun signInWithCredentials(
        @Body payload: CredentialsCallbackRequest,
    ): Response<JsonElement>

    @GET("/api/auth/session")
    suspend fun getSession(): Response<JsonElement>

    @POST("/api/auth/logout")
    suspend fun signOut(): Response<MessageResponse>

    @POST("/api/auth/register")
    suspend fun register(
        @Body payload: RegisterRequest,
    ): Response<RegisterResponse>

    @GET("/api/auth/security-questions/all")
    suspend fun getAllSecurityQuestions(): Response<SecurityQuestionsResponse>

    @GET("/api/auth/security-questions")
    suspend fun getSecurityQuestionsForUsername(
        @Query("username") username: String,
    ): Response<SecurityQuestionsResponse>

    @POST("/api/auth/reset-password")
    suspend fun resetPassword(
        @Body payload: SelfServiceResetRequest,
    ): Response<MessageResponse>

    @POST("/api/auth/verify-security-answers")
    suspend fun verifySecurityAnswers(
        @Body payload: VerifySecurityAnswersRequest,
    ): Response<VerifySecurityAnswersResponse>

    @POST("/api/auth/request-admin-reset")
    suspend fun requestAdminReset(
        @Body payload: RequestAdminResetRequest,
    ): Response<MessageResponse>

    @GET("/api/user/security-questions")
    suspend fun getUserSecurityQuestionStatus(): Response<SecurityQuestionStatusResponse>

    @POST("/api/user/security-questions")
    suspend fun setUserSecurityQuestions(
        @Body payload: SetSecurityQuestionsRequest,
    ): Response<MessageResponse>

    @GET("/api/todo")
    suspend fun getTodos(
        @Query("start") start: Long? = null,
        @Query("end") end: Long? = null,
        @Query("timeline") timeline: Boolean? = null,
        @Query("recurringFutureDays") recurringFutureDays: Int? = null,
    ): Response<TodosResponse>

    @GET("/api/app-settings")
    suspend fun getAppSettings(): Response<AppSettingsResponse>

    @POST("/api/todo/summary")
    suspend fun summarizeTodos(
        @Body payload: TodoSummaryRequest,
    ): Response<TodoSummaryResponse>

    @POST("/api/todo/nlp")
    suspend fun parseTodoTitleNlp(
        @Body payload: TodoTitleNlpRequest,
    ): Response<TodoTitleNlpResponse>

    @POST("/api/todo")
    suspend fun createTodo(
        @Body payload: CreateTodoRequest,
    ): Response<CreateTodoResponse>

    @GET("/api/floater")
    suspend fun getFloaters(): Response<FloatersResponse>

    @POST("/api/floater")
    suspend fun createFloater(
        @Body payload: CreateFloaterRequest,
    ): Response<CreateFloaterResponse>

    @PATCH("/api/floater")
    suspend fun patchFloaterByBody(
        @Body payload: UpdateFloaterRequest,
    ): Response<MessageResponse>

    @HTTP(method = "DELETE", path = "/api/floater", hasBody = true)
    suspend fun deleteFloaterByBody(
        @Body payload: DeleteFloaterRequest,
    ): Response<MessageResponse>

    @PATCH("/api/floater/complete")
    suspend fun completeFloaterByBody(
        @Body payload: FloaterCompleteRequest,
    ): Response<MessageResponse>

    @PATCH("/api/floater/uncomplete")
    suspend fun uncompleteFloaterByBody(
        @Body payload: FloaterUncompleteRequest,
    ): Response<MessageResponse>

    @PATCH("/api/floater/prioritize")
    suspend fun prioritizeFloaterByBody(
        @Body payload: FloaterPrioritizeRequest,
    ): Response<MessageResponse>

    @PATCH("/api/floater/reorder")
    suspend fun reorderFloater(
        @Body payload: FloaterReorderRequest,
    ): Response<MessageResponse>

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
        @Body payload: TodoInstanceDeleteRequest,
    ): Response<MessageResponse>

    @GET("/api/completedTodo")
    suspend fun getCompletedTodos(): Response<CompletedTodosResponse>

    @GET("/api/completedFloater")
    suspend fun getCompletedFloaters(): Response<CompletedFloatersResponse>

    @PATCH("/api/completedTodo")
    suspend fun patchCompletedTodoByBody(
        @Body payload: UpdateCompletedTodoRequest,
    ): Response<MessageResponse>

    @HTTP(method = "DELETE", path = "/api/completedTodo", hasBody = true)
    suspend fun deleteCompletedTodoByBody(
        @Body payload: DeleteCompletedTodoRequest,
    ): Response<MessageResponse>

    @PATCH("/api/completedFloater")
    suspend fun patchCompletedFloaterByBody(
        @Body payload: UpdateCompletedFloaterRequest,
    ): Response<MessageResponse>

    @HTTP(method = "DELETE", path = "/api/completedFloater", hasBody = true)
    suspend fun deleteCompletedFloaterByBody(
        @Body payload: DeleteCompletedFloaterRequest,
    ): Response<MessageResponse>

    @GET("/api/list")
    suspend fun getLists(): Response<ListsResponse>

    @GET("/api/list/{id}")
    suspend fun getListTodos(
        @Path("id") listId: String,
        @Query("start") start: Long,
        @Query("end") end: Long,
    ): Response<ListDetailResponse>

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
    ): Response<DeleteListResponse>

    @GET("/api/floaterList")
    suspend fun getFloaterLists(): Response<FloaterListsResponse>

    @GET("/api/floaterList/{id}")
    suspend fun getFloaterListTodos(
        @Path("id") listId: String,
    ): Response<FloaterListDetailResponse>

    @POST("/api/floaterList")
    suspend fun createFloaterList(
        @Body payload: CreateFloaterListRequest,
    ): Response<CreateFloaterListResponse>

    @PATCH("/api/floaterList")
    suspend fun patchFloaterListByBody(
        @Body payload: UpdateFloaterListRequest,
    ): Response<MessageResponse>

    @HTTP(method = "DELETE", path = "/api/floaterList", hasBody = true)
    suspend fun deleteFloaterListByBody(
        @Body payload: DeleteFloaterListRequest,
    ): Response<DeleteFloaterListResponse>

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
