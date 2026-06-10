package com.ohmz.tday.compose.feature.todos

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.HapticFeedbackConstantsCompat
import androidx.core.view.ViewCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ohmz.tday.compose.R
import com.ohmz.tday.compose.core.data.list.ListShareRepository
import com.ohmz.tday.compose.core.data.list.ShareListKind
import com.ohmz.tday.compose.core.model.ListMemberDto
import com.ohmz.tday.compose.core.model.UserSearchResultDto
import com.ohmz.tday.compose.ui.component.TdayModalBottomSheet
import com.ohmz.tday.compose.ui.component.TdaySheetCard
import com.ohmz.tday.compose.ui.component.TdaySheetDefaults
import com.ohmz.tday.compose.ui.component.TdaySheetHeader
import com.ohmz.tday.compose.ui.component.TdaySheetSectionTitle
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ManageMembersViewModel @Inject constructor(
    private val shareRepository: ListShareRepository,
) : ViewModel() {
    data class UiState(
        val isLoading: Boolean = true,
        val owner: ListMemberDto? = null,
        val members: List<ListMemberDto> = emptyList(),
        val searchQuery: String = "",
        val searchResults: List<UserSearchResultDto> = emptyList(),
        val errorMessage: String? = null,
        val isWorking: Boolean = false,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var kind: ShareListKind = ShareListKind.SCHEDULED
    private var listId: String = ""
    private var searchJob: Job? = null

    fun start(kind: ShareListKind, listId: String) {
        this.kind = kind
        this.listId = listId
        _uiState.value = UiState()
        refresh()
    }

    private fun refresh() {
        viewModelScope.launch {
            runCatching { shareRepository.fetchMembers(kind, listId) }
                .onSuccess { response ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            owner = response.owner,
                            members = response.members
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = error.message) }
                }
        }
    }

    fun onSearchQueryChange(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        searchJob?.cancel()
        if (query.trim().length < 2) {
            _uiState.update { it.copy(searchResults = emptyList()) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            runCatching { shareRepository.searchUsers(query) }
                .onSuccess { users ->
                    val memberIds = buildSet {
                        _uiState.value.owner?.let { add(it.userId) }
                        _uiState.value.members.forEach { add(it.userId) }
                    }
                    _uiState.update { state ->
                        state.copy(searchResults = users.filterNot { it.id in memberIds })
                    }
                }
        }
    }

    fun addMember(username: String) {
        runMembershipAction {
            shareRepository.addMember(kind, listId, username, role = "EDITOR")
            _uiState.update { it.copy(searchQuery = "", searchResults = emptyList()) }
        }
    }

    fun updateRole(userId: String, role: String) {
        runMembershipAction { shareRepository.updateMemberRole(kind, listId, userId, role) }
    }

    fun removeMember(userId: String) {
        runMembershipAction { shareRepository.removeMember(kind, listId, userId) }
    }

    fun leave(onLeft: () -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isWorking = true, errorMessage = null) }
            runCatching { shareRepository.leaveList(kind, listId) }
                .onSuccess {
                    _uiState.update { it.copy(isWorking = false) }
                    onLeft()
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isWorking = false, errorMessage = error.message) }
                }
        }
    }

    private fun runMembershipAction(action: suspend () -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isWorking = true, errorMessage = null) }
            runCatching { action() }
                .onSuccess {
                    _uiState.update { it.copy(isWorking = false) }
                    refresh()
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isWorking = false, errorMessage = error.message) }
                }
        }
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 250L
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageMembersSheet(
    listId: String,
    listName: String,
    kind: ShareListKind,
    myRole: String,
    onShareAsText: () -> Unit,
    onDismiss: () -> Unit,
    onLeftList: () -> Unit,
    viewModel: ManageMembersViewModel = hiltViewModel(),
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val uiState by viewModel.uiState.collectAsState()
    val isOwner = myRole.equals("OWNER", ignoreCase = true)
    val colorScheme = MaterialTheme.colorScheme

    LaunchedEffect(listId, kind) {
        viewModel.start(kind, listId)
    }

    TdayModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            TdaySheetHeader(
                title = stringResource(R.string.members_title),
                leftIcon = ImageVector.vectorResource(R.drawable.ic_lucide_x),
                leftContentDescription = stringResource(R.string.action_close),
                onLeftClick = onDismiss,
                showConfirmAction = false,
            )

            TdaySheetSectionTitle(text = listName)
            TdaySheetCard {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    if (uiState.isLoading) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 18.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(28.dp))
                        }
                    } else {
                        uiState.owner?.let { owner ->
                            MemberRow(
                                member = owner,
                                isOwnerRow = true,
                                canManage = false,
                                onRoleChange = {},
                                onRemove = {},
                            )
                        }
                        uiState.members.forEach { member ->
                            MemberRow(
                                member = member,
                                isOwnerRow = false,
                                canManage = isOwner && !uiState.isWorking,
                                onRoleChange = { role ->
                                    viewModel.updateRole(
                                        member.userId,
                                        role
                                    )
                                },
                                onRemove = { viewModel.removeMember(member.userId) },
                            )
                        }
                    }
                }
            }

            uiState.errorMessage?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colorScheme.error,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }

            if (isOwner) {
                TdaySheetSectionTitle(text = stringResource(R.string.members_add_section))
                TdaySheetCard {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        BasicTextField(
                            value = uiState.searchQuery,
                            onValueChange = viewModel::onSearchQueryChange,
                            singleLine = true,
                            textStyle = MaterialTheme.typography.titleMedium.copy(
                                color = colorScheme.onSurface,
                                fontWeight = FontWeight.Bold,
                            ),
                            cursorBrush = SolidColor(colorScheme.onSurface),
                            modifier = Modifier.fillMaxWidth(),
                            decorationBox = { innerTextField ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            TdaySheetDefaults.controlSurfaceColor(),
                                            RoundedCornerShape(16.dp),
                                        )
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                ) {
                                    if (uiState.searchQuery.isBlank()) {
                                        Text(
                                            text = stringResource(R.string.members_search_placeholder),
                                            style = MaterialTheme.typography.titleMedium,
                                            color = colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                    innerTextField()
                                }
                            },
                        )

                        if (uiState.searchQuery.trim().length >= 2) {
                            if (uiState.searchResults.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.members_no_results),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 4.dp),
                                )
                            } else {
                                uiState.searchResults.forEach { user ->
                                    SearchResultRow(
                                        user = user,
                                        enabled = !uiState.isWorking,
                                        onAdd = { viewModel.addMember(user.username) },
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                MembersSheetActionButton(
                    icon = ImageVector.vectorResource(R.drawable.ic_lucide_share_2),
                    label = stringResource(R.string.share_list_action),
                    tint = colorScheme.onSurface,
                    border = colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                    container = TdaySheetDefaults.controlSurfaceColor(),
                    onClick = {
                        onDismiss()
                        onShareAsText()
                    },
                )
                MembersSheetActionButton(
                    icon = ImageVector.vectorResource(R.drawable.ic_lucide_x),
                    label = stringResource(R.string.members_leave_list),
                    tint = colorScheme.error,
                    border = colorScheme.error.copy(alpha = 0.45f),
                    container = colorScheme.error.copy(
                        alpha = if (TdaySheetDefaults.isDarkTheme()) 0.14f else 0.04f,
                    ),
                    onClick = { viewModel.leave(onLeftList) },
                )
            }

            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun MemberRow(
    member: ListMemberDto,
    isOwnerRow: Boolean,
    canManage: Boolean,
    onRoleChange: (String) -> Unit,
    onRemove: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(colorScheme.primary.copy(alpha = 0.15f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = (member.name?.trim().takeUnless { it.isNullOrEmpty() } ?: member.username)
                    .take(1)
                    .uppercase(),
                style = MaterialTheme.typography.titleMedium,
                color = colorScheme.primary,
                fontWeight = FontWeight.ExtraBold,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = member.name?.trim().takeUnless { it.isNullOrEmpty() } ?: member.username,
                style = MaterialTheme.typography.titleMedium,
                color = colorScheme.onSurface,
                fontWeight = FontWeight.ExtraBold,
            )
            Text(
                text = "@${member.username}",
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
            )
        }
        if (isOwnerRow) {
            RolePill(text = stringResource(R.string.share_role_owner), selected = true)
        } else if (canManage) {
            val isEditor = member.role.equals("EDITOR", ignoreCase = true)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                RolePill(
                    text = stringResource(R.string.share_role_editor),
                    selected = isEditor,
                    onClick = { if (!isEditor) onRoleChange("EDITOR") },
                )
                RolePill(
                    text = stringResource(R.string.share_role_viewer),
                    selected = !isEditor,
                    onClick = { if (isEditor) onRoleChange("VIEWER") },
                )
            }
            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = ImageVector.vectorResource(R.drawable.ic_lucide_x),
                    contentDescription = stringResource(R.string.members_remove),
                    tint = colorScheme.error,
                    modifier = Modifier.size(18.dp),
                )
            }
        } else {
            RolePill(
                text = if (member.role.equals("EDITOR", ignoreCase = true)) {
                    stringResource(R.string.share_role_editor)
                } else {
                    stringResource(R.string.share_role_viewer)
                },
                selected = false,
            )
        }
    }
}

@Composable
private fun RolePill(
    text: String,
    selected: Boolean,
    onClick: (() -> Unit)? = null,
) {
    val colorScheme = MaterialTheme.colorScheme
    Card(
        onClick = { onClick?.invoke() },
        enabled = onClick != null,
        shape = RoundedCornerShape(50),
        border = BorderStroke(
            width = 1.5.dp,
            color = if (selected) {
                colorScheme.primary.copy(alpha = 0.55f)
            } else {
                colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
            },
        ),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                colorScheme.primary.copy(alpha = 0.15f)
            } else {
                TdaySheetDefaults.controlSurfaceColor()
            },
            disabledContainerColor = if (selected) {
                colorScheme.primary.copy(alpha = 0.15f)
            } else {
                TdaySheetDefaults.controlSurfaceColor()
            },
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) colorScheme.primary else colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
}

@Composable
private fun SearchResultRow(
    user: UserSearchResultDto,
    enabled: Boolean,
    onAdd: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val view = LocalView.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = user.name?.trim().takeUnless { it.isNullOrEmpty() } ?: user.username,
                style = MaterialTheme.typography.titleMedium,
                color = colorScheme.onSurface,
                fontWeight = FontWeight.ExtraBold,
            )
            Text(
                text = "@${user.username}",
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
            )
        }
        Card(
            onClick = {
                ViewCompat.performHapticFeedback(view, HapticFeedbackConstantsCompat.CLOCK_TICK)
                onAdd()
            },
            enabled = enabled,
            shape = RoundedCornerShape(50),
            colors = CardDefaults.cardColors(
                containerColor = colorScheme.primary.copy(alpha = 0.15f),
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = ImageVector.vectorResource(R.drawable.ic_lucide_plus),
                    contentDescription = null,
                    tint = colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = stringResource(R.string.members_add_action),
                    style = MaterialTheme.typography.labelLarge,
                    color = colorScheme.primary,
                    fontWeight = FontWeight.ExtraBold,
                )
            }
        }
    }
}

@Composable
private fun MembersSheetActionButton(
    icon: ImageVector,
    label: String,
    tint: androidx.compose.ui.graphics.Color,
    border: androidx.compose.ui.graphics.Color,
    container: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
) {
    val view = LocalView.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        label = "membersSheetActionButtonScale",
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        onClick = {
            ViewCompat.performHapticFeedback(view, HapticFeedbackConstantsCompat.CLOCK_TICK)
            onClick()
        },
        interactionSource = interactionSource,
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.5.dp, border),
        colors = CardDefaults.cardColors(containerColor = container),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp, pressedElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = tint,
                fontWeight = FontWeight.ExtraBold,
            )
        }
    }
}
