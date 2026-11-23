package io.github.nerushkolya.locator.feature.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nerushkolya.locator.core.model.ServerStatus
import io.github.nerushkolya.locator.core.model.UnreachableReason
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel

/** Entry point of the map destination; the real map is drawn in the map slot. */
@Composable
fun MapRoute(modifier: Modifier = Modifier, viewModel: MapViewModel = hiltViewModel()) {
    val state = viewModel.state.collectAsStateWithLifecycle().value
    MapScreen(
        state = state,
        onRefresh = viewModel::refresh,
        modifier = modifier,
        mapSlot = { styleUrl -> LocatorMap(styleUrl = styleUrl, onStyleFailed = viewModel::onMapStyleFailed) },
    )
}

/**
 * Stateless screen. The map slot receives the style URL; screenshot tests pass a placeholder because
 * the native map does not render on the JVM.
 */
@Composable
fun MapScreen(
    state: MapUiState,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    mapSlot: @Composable (styleUrl: String) -> Unit,
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.safeDrawingPadding()) {
            StatusHeader(state = state, onRefresh = onRefresh)
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                when (val map = state.map) {
                    MapSource.Loading -> LoadingMap()
                    is MapSource.Ready -> mapSlot(map.styleUrl)
                    MapSource.Unavailable -> MapUnavailable()
                }
            }
        }
    }
}

@Composable
private fun StatusHeader(state: MapUiState, onRefresh: () -> Unit) {
    val status = state.status
    val (icon, color) = when (status) {
        null -> Icons.Filled.Info to MaterialTheme.colorScheme.onSurfaceVariant
        ServerStatus.Reachable -> Icons.Filled.CheckCircle to MaterialTheme.colorScheme.primary
        is ServerStatus.NotReady -> Icons.Filled.Warning to MaterialTheme.colorScheme.tertiary
        is ServerStatus.Unreachable -> Icons.Filled.Warning to MaterialTheme.colorScheme.error
    }
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(start = 16.dp, top = 8.dp, end = 4.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatusIcon(icon, color)
            Column(Modifier.weight(1f)) {
                Text(
                    text = statusTitle(status),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                statusDetail(status)?.let {
                    Text(text = it, style = MaterialTheme.typography.bodyMedium)
                }
                Text(
                    text = stringResource(R.string.map_address, state.address.baseUrl),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onRefresh, enabled = !state.refreshing, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.map_refresh))
            }
        }
    }
}

@Composable
private fun StatusIcon(icon: ImageVector, tint: androidx.compose.ui.graphics.Color) {
    // The text next to the icon carries the meaning; the icon only reinforces it (never colour alone).
    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(28.dp))
}

@Composable
private fun statusTitle(status: ServerStatus?): String = when (status) {
    null -> stringResource(R.string.map_status_checking)
    ServerStatus.Reachable -> stringResource(R.string.map_status_reachable)
    is ServerStatus.NotReady -> stringResource(R.string.map_status_not_ready)
    is ServerStatus.Unreachable -> stringResource(R.string.map_status_unreachable)
}

@Composable
private fun statusDetail(status: ServerStatus?): String? = when (status) {
    is ServerStatus.NotReady -> stringResource(R.string.map_server_status_detail, status.status)
    is ServerStatus.Unreachable -> stringResource(
        when (status.reason) {
            UnreachableReason.TIMEOUT -> R.string.map_reason_timeout
            UnreachableReason.CONNECTION_REFUSED -> R.string.map_reason_connection_refused
            UnreachableReason.UNEXPECTED_RESPONSE -> R.string.map_reason_unexpected_response
        },
    )
    else -> null
}

@Composable
private fun LoadingMap() {
    val description = stringResource(R.string.map_loading)
    CircularProgressIndicator(Modifier.semantics { contentDescription = description })
}

@Composable
private fun MapUnavailable() {
    Column(
        Modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Filled.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(R.string.map_unavailable), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.map_unavailable_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Stand-in for the native map in screenshot tests. */
@Composable
fun MapPlaceholder(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(stringResource(R.string.map_placeholder), style = MaterialTheme.typography.headlineMedium)
    }
}

