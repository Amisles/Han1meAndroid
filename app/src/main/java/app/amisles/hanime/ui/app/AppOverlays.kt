package app.amisles.hanime.ui.app

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.amisles.hanime.core.common.AppFeatures
import app.amisles.hanime.core.common.util.CrashLogger
import app.amisles.hanime.core.common.util.CrashRecord
import app.amisles.hanime.core.ui.R
import app.amisles.hanime.domain.remote.CloudflareChallenge
import app.amisles.hanime.domain.repository.CrashSettings
import app.amisles.hanime.domain.repository.UpdateRepository
import app.amisles.hanime.feature.settings.UpdateAvailableDialog
import app.amisles.hanime.feature.settings.network.CloudflareChallengeDialog
import app.amisles.hanime.ui.onboarding.OnboardingHost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
internal fun CloudflareChallengeHost() {
    val challenge by CloudflareChallenge.request.collectAsStateWithLifecycle()
    challenge?.let { pending ->
        key(pending.id) {
            CloudflareChallengeDialog(
                host = pending.host,
                onDismiss = { CloudflareChallenge.dismiss() },
                onSolved = { CloudflareChallenge.markSolved() }
            )
        }
    }
}

@Composable
internal fun CrashNoticeHost(crashSettings: CrashSettings, onOpenLogs: () -> Unit) {
    var crashNotice by remember { mutableStateOf<CrashRecord?>(null) }
    LaunchedEffect(Unit) {
        crashNotice = withContext(Dispatchers.IO) {
            CrashLogger.takeUnseen(crashSettings.acknowledgedCrashFile)
        }
    }
    crashNotice?.let { record ->
        AlertDialog(
            onDismissRequest = {
                crashSettings.setAcknowledgedCrashFile(record.file.name)
                crashNotice = null
            },
            title = { Text(stringResource(R.string.crash_notice_title)) },
            text = {
                Text(
                    text = stringResource(R.string.crash_notice_body),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    crashSettings.setAcknowledgedCrashFile(record.file.name)
                    crashNotice = null
                    onOpenLogs()
                }) {
                    Text(stringResource(R.string.crash_notice_view))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    crashSettings.setAcknowledgedCrashFile(record.file.name)
                    crashNotice = null
                }) {
                    Text(
                        text = stringResource(R.string.common_close),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }
}

@Composable
internal fun OnboardingGate(
    done: Boolean,
    currentRoute: String,
    deepLinkLaunch: Boolean,
    onDone: () -> Unit,
    onNavigate: (String) -> Unit
) {
    val onboardingEnabled = AppFeatures.ONBOARDING_ENABLED
    var ready by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(done, currentRoute, deepLinkLaunch) {
        if (!onboardingEnabled || done || deepLinkLaunch || currentRoute != "home") return@LaunchedEffect
        delay(1_200L)
        ready = true
    }
    if (onboardingEnabled && ready && !done && currentRoute == "home") {
        OnboardingHost(onNavigate = onNavigate, onComplete = onDone)
    }
}

@Composable
internal fun UpdatePromptHost(
    updateRepository: UpdateRepository,
    onboardingDone: Boolean,
    onOpenUpdate: () -> Unit
) {
    val availableUpdate by updateRepository.availableUpdate.collectAsStateWithLifecycle()
    val autoCheckUpdate by updateRepository.autoCheckEnabledFlow.collectAsStateWithLifecycle()
    val currentSkippedVersion by updateRepository.skippedVersionFlow.collectAsStateWithLifecycle()
    var dismissedVersion by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(autoCheckUpdate) {
        if (autoCheckUpdate) {
            runCatching { updateRepository.checkForUpdate(force = false) }
        }
    }

    var promptReady by remember { mutableStateOf(false) }
    LaunchedEffect(availableUpdate) {
        if (availableUpdate != null && !promptReady) {
            delay(1_200L)
            promptReady = true
        }
    }

    val pending = availableUpdate
    if (promptReady && onboardingDone && pending != null &&
        pending.versionName != currentSkippedVersion &&
        pending.versionName != dismissedVersion
    ) {
        UpdateAvailableDialog(
            update = pending,
            onUpdate = {
                dismissedVersion = pending.versionName
                onOpenUpdate()
            },
            onSkipVersion = {
                updateRepository.skipVersion(pending.versionName)
                dismissedVersion = pending.versionName
            },
            onDismiss = { dismissedVersion = pending.versionName }
        )
    }
}
