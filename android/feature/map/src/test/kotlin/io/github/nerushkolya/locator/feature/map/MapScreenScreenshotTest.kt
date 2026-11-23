package io.github.nerushkolya.locator.feature.map

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.nerushkolya.locator.core.designsystem.theme.AppTheme
import io.github.nerushkolya.locator.core.model.ServerAddress
import io.github.nerushkolya.locator.core.model.ServerStatus
import io.github.nerushkolya.locator.core.model.UnreachableReason
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.ParameterizedRobolectricTestRunner.Parameters
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Screenshots of the three screen states in light and dark, English and Ukrainian, plus 200 % font.
 * The native map does not render on the JVM, so the map slot shows a placeholder.
 * Baselines: `src/test/screenshots/`, recorded with `scripts/test-android.ps1 -RecordScreenshots`.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class MapScreenScreenshotTest(
    private val locale: String,
    private val dark: Boolean,
    private val fontScale: Float,
) {
    @get:Rule
    val composeRule = createComposeRule()

    private val address = ServerAddress("http://10.0.2.2:8080", configurable = true)

    private val reachable = MapUiState(
        address = address,
        status = ServerStatus.Reachable,
        map = MapSource.Ready("https://tiles.example.org/style"),
        refreshing = false,
    )
    private val notReachable = MapUiState(
        address = address,
        status = ServerStatus.Unreachable(address.baseUrl, UnreachableReason.CONNECTION_REFUSED),
        map = MapSource.Unavailable,
        refreshing = false,
    )
    private val mapUnavailable = reachable.copy(map = MapSource.Unavailable)

    private val suffix get() = "$locale-${if (dark) "dark" else "light"}-${(fontScale * 100).toInt()}"

    @Test
    fun reachable() = capture("reachable", reachable)

    @Test
    fun notReachable() = capture("not-reachable", notReachable)

    @Test
    fun mapUnavailable() = capture("map-unavailable", mapUnavailable)

    private fun capture(name: String, state: MapUiState) {
        RuntimeEnvironment.setQualifiers("+$locale" + if (dark) "-night" else "-notnight")
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                AppTheme(darkTheme = dark) {
                    MapScreen(state = state, onRefresh = {}, mapSlot = { MapPlaceholder() })
                }
            }
        }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/map-$name-$suffix.png")
    }

    companion object {
        @JvmStatic
        @Parameters(name = "{0} dark={1} font={2}")
        fun parameters(): List<Array<Any>> = buildList {
            for (locale in listOf("en", "uk")) {
                for (dark in listOf(false, true)) add(arrayOf<Any>(locale, dark, 1f))
            }
            add(arrayOf<Any>("en", false, 2f))
            add(arrayOf<Any>("uk", false, 2f))
        }
    }
}
