package app.amisles.hanime.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.amisles.hanime.core.ui.R
import app.amisles.hanime.domain.model.SiteProbeResult

@Composable
internal fun WelcomePage() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.onboarding_welcome_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.onboarding_welcome_body),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
internal fun BasicsPage() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.onboarding_basics_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(28.dp))
            BasicsStep(icon = Icons.Default.Home, textRes = R.string.onboarding_basics_tabs)
            Spacer(modifier = Modifier.height(14.dp))
            BasicsStep(icon = Icons.Default.Menu, textRes = R.string.onboarding_basics_drawer)
            Spacer(modifier = Modifier.height(14.dp))
            BasicsStep(icon = Icons.Default.TouchApp, textRes = R.string.onboarding_basics_long_press)
        }
    }
}

@Composable
private fun BasicsStep(icon: ImageVector, textRes: Int) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(26.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = stringResource(textRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
internal fun ConnectivityPage(
    probeState: ProbeUiState,
    onRetry: () -> Unit,
    onNavigate: (String) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.onboarding_connectivity_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(32.dp))
            when (val state = probeState) {
                ProbeUiState.Running -> ProbeRunningRow()
                is ProbeUiState.Done -> when (val result = state.result) {
                    is SiteProbeResult.Reachable -> ProbeReachable(latencyMs = result.latencyMs)
                    is SiteProbeResult.Unreachable -> ProbeUnreachable(
                        reason = result.reason,
                        onRetry = onRetry,
                        onNavigate = onNavigate
                    )
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = stringResource(R.string.onboarding_privacy_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
    }
}

@Composable
private fun ProbeRunningRow() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(
            modifier = Modifier.size(22.dp),
            strokeWidth = 2.5.dp
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = stringResource(R.string.onboarding_probe_running),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ProbeReachable(latencyMs: Long) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(44.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.onboarding_probe_ok, latencyMs),
            style = MaterialTheme.typography.titleMedium
        )
    }
}

@Composable
private fun ProbeUnreachable(
    reason: SiteProbeResult.Reason,
    onRetry: () -> Unit,
    onNavigate: (String) -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = Icons.Default.Error,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(44.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.onboarding_probe_fail),
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(probeReasonRes(reason)),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(20.dp))
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            FilledTonalButton(onClick = { onNavigate("settings") }) {
                Text(text = stringResource(R.string.onboarding_action_mirror))
            }
            FilledTonalButton(onClick = { onNavigate("diagnostics") }) {
                Text(text = stringResource(R.string.onboarding_action_diagnostics))
            }
            TextButton(onClick = onRetry) {
                Text(text = stringResource(R.string.onboarding_action_retry))
            }
        }
    }
}

private fun probeReasonRes(reason: SiteProbeResult.Reason): Int = when (reason) {
    SiteProbeResult.Reason.INVALID_URL -> R.string.onboarding_probe_fail_reason_url
    SiteProbeResult.Reason.DNS -> R.string.onboarding_probe_fail_reason_dns
    SiteProbeResult.Reason.TIMEOUT -> R.string.onboarding_probe_fail_reason_timeout
    SiteProbeResult.Reason.IO -> R.string.onboarding_probe_fail_reason_io
}
