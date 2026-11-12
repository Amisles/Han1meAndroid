package app.amisles.hanime.core.common.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

data class AppSnackbarEvent(
    val text: String,
    val actionLabel: String? = null,
    val onAction: (() -> Unit)? = null
)

object AppSnackbar {
    private val _events = Channel<AppSnackbarEvent>(Channel.UNLIMITED)
    val events: Flow<AppSnackbarEvent> = _events.receiveAsFlow()

    val undoScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun show(text: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
        _events.trySend(AppSnackbarEvent(text, actionLabel, onAction))
    }
}
