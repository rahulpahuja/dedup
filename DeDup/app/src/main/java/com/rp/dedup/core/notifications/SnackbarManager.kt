package com.rp.dedup.core.notifications

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Enhanced Manager for showing Snackbars in a Jetpack Compose environment.
 * Requires a [SnackbarHostState] and a [CoroutineScope] to function.
 */
class SnackbarManager(
    private val snackbarHostState: SnackbarHostState,
    private val scope: CoroutineScope
) {

    /**
     * Custom visuals implementation to carry styling data to the UI.
     * This allows passing background colors and icons through the [SnackbarHostState].
     */
    data class CustomSnackbarVisuals(
        override val message: String,
        override val actionLabel: String? = null,
        override val duration: SnackbarDuration = SnackbarDuration.Short,
        override val withDismissAction: Boolean = false,
        val icon: ImageVector? = null,
        val backgroundColor: Color = Color.Unspecified,
        val contentColor: Color = Color.Unspecified
    ) : SnackbarVisuals

    /**
     * Shows a simple snackbar.
     */
    fun showMessage(message: String, duration: SnackbarDuration = SnackbarDuration.Short) {
        scope.launch {
            snackbarHostState.showSnackbar(
                message = message,
                duration = duration
            )
        }
    }

    /**
     * Shows a snackbar with an action.
     *
     * @param message The message to display
     * @param actionLabel The label for the action button
     * @param duration Duration of the snackbar
     * @param onAction Callback executed if the user clicks the action button
     * @param onDismissed Callback executed if the snackbar is dismissed
     */
    fun showWithAction(
        message: String,
        actionLabel: String,
        duration: SnackbarDuration = SnackbarDuration.Short,
        onAction: () -> Unit = {},
        onDismissed: () -> Unit = {}
    ) {
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = message,
                actionLabel = actionLabel,
                duration = duration
            )
            when (result) {
                SnackbarResult.ActionPerformed -> onAction()
                SnackbarResult.Dismissed -> onDismissed()
            }
        }
    }

    /**
     * Shows a highly customized snackbar with specific colors and an icon.
     * 
     * @param message The message to display
     * @param icon Optional [ImageVector] to show alongside the text
     * @param actionLabel Optional label for the action button
     * @param backgroundColor Custom background color
     * @param contentColor Custom color for text and icon
     * @param duration Duration of the snackbar
     * @param onAction Callback executed if the user clicks the action button
     */
    fun showCustom(
        message: String,
        icon: ImageVector? = null,
        actionLabel: String? = null,
        backgroundColor: Color = Color.Unspecified,
        contentColor: Color = Color.Unspecified,
        duration: SnackbarDuration = SnackbarDuration.Short,
        onAction: () -> Unit = {}
    ) {
        scope.launch {
            val visuals = CustomSnackbarVisuals(
                message = message,
                actionLabel = actionLabel,
                duration = duration,
                icon = icon,
                backgroundColor = backgroundColor,
                contentColor = contentColor
            )
            val result = snackbarHostState.showSnackbar(visuals)
            if (result == SnackbarResult.ActionPerformed) {
                onAction()
            }
        }
    }
}
