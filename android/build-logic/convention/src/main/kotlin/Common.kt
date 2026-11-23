import com.android.build.api.dsl.CommonExtension
import com.android.build.api.dsl.Lint
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType

internal const val COMPILE_SDK = 37
internal const val COMPILE_SDK_MINOR = 1
internal const val MIN_SDK = 29
internal const val TARGET_SDK = 36

internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun Project.lib(alias: String) = libs.findLibrary(alias).get()

internal fun Project.configureAndroid(extension: CommonExtension) {
    extension.apply {
        compileSdk { version = release(COMPILE_SDK) { minorApiLevel = COMPILE_SDK_MINOR } }
        defaultConfig.minSdk = MIN_SDK
        compileOptions.sourceCompatibility = JavaVersion.VERSION_17
        compileOptions.targetCompatibility = JavaVersion.VERSION_17
        testOptions.unitTests.isIncludeAndroidResources = true
    }
}

internal fun Lint.configureLint() {
    // Hard-coded user-facing text is a build error (FR-016).
    error.add("HardcodedText")
    abortOnError = true
}

internal fun Project.addTestDependencies() {
    dependencies.add("testImplementation", lib("junit"))
    dependencies.add("testImplementation", lib("kotlinx-coroutines-test"))
    dependencies.add("testImplementation", lib("turbine"))
}
