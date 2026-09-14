package com.ohmz.tday.compose.feature.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ohmz.tday.compose.ui.component.TdaySheetDefaults
import com.ohmz.tday.compose.ui.theme.TdayDimens

// What this screen draws that the scale has no rung for. The card's corner is not among them: the
// panel inside it is the one the login dialog embeds, and drawing the dialog's own corner on a full
// screen is what makes it feel at home there, so it takes `TdaySheetDefaults.DialogShape`.

// The inset that keeps the card off the screen edges. The two halves are one margin and are named
// as a pair even though the vertical one matches `Spacing3xl`, so no later edit moves one across a
// step and leaves the other behind.
private val ScreenInsetHorizontal = 20.dp
private val ScreenInsetVertical = 24.dp

/** How wide the card is allowed to grow before the text starts running. Narrower than
 *  `TdaySheetDefaults.MaxContentWidth` because this holds one column of fields, not a sheet. */
private val ResetCardMaxWidth = 440.dp

/** Lifts the card off the background it is drawn on. Between `BottomSheetTonalElevationDark` and
 *  `FabElevation`, where the scale has no step. */
private val ResetCardElevation = 12.dp

/** The panel's inset inside the card. Its own value, not the screen inset above it, which is why
 *  the two 20s are named apart. */
private val PanelPadding = 20.dp

// Standalone reset screen (reached from Settings). Wraps the same ForgotPasswordPanel the
// login dialog embeds, in its own card so it feels at home on a full screen.
@Composable
fun ForgotPasswordScreen(
    onBackToLogin: () -> Unit,
    onResetComplete: () -> Unit,
    viewModel: ForgotPasswordViewModel = hiltViewModel(),
) {
    val colorScheme = MaterialTheme.colorScheme

    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = ScreenInsetHorizontal, vertical = ScreenInsetVertical),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = ResetCardMaxWidth),
            shape = TdaySheetDefaults.DialogShape,
            colors = CardDefaults.cardColors(containerColor = colorScheme.background),
            elevation = CardDefaults.cardElevation(defaultElevation = ResetCardElevation),
            border = BorderStroke(TdayDimens.BorderWidth, colorScheme.onSurface.copy(alpha = 0.08f)),
        ) {
            ForgotPasswordPanel(
                initialUsername = "",
                onBackToLogin = onBackToLogin,
                onResetComplete = { onResetComplete() },
                modifier = Modifier.padding(PanelPadding),
                viewModel = viewModel,
            )
        }
    }
}
