package io.github.nerushkolya.locator.feature.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.map.MapEvent
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.map.rememberMapState
import org.maplibre.compose.style.BaseStyle
import org.maplibre.spatialk.geojson.Position

/** Default camera: Maidan Nezalezhnosti, Kyiv (clarification of 2026-11-23). */
object DefaultCamera {
    const val LATITUDE = 50.4501
    const val LONGITUDE = 30.5234
    const val ZOOM = 11.0
}

/** The native map. Logo and attribution come from the library's default controls and stay visible. */
@Composable
fun LocatorMap(styleUrl: String, onStyleFailed: () -> Unit) {
    key(styleUrl) {
        val mapState = rememberMapState(
            baseStyle = BaseStyle.Uri(styleUrl),
            initialCameraPosition = CameraPosition(
                center = Position(latitude = DefaultCamera.LATITUDE, longitude = DefaultCamera.LONGITUDE),
                zoom = DefaultCamera.ZOOM,
            ),
        )
        LaunchedEffect(mapState) {
            mapState.events.collect { event ->
                if (event is MapEvent.StyleLoadFailed) onStyleFailed()
            }
        }
        MaplibreMap(state = mapState)
    }
}

