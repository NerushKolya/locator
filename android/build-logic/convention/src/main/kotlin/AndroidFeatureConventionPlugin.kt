import io.github.takahirom.roborazzi.RoborazziExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("locator.android.library")
            pluginManager.apply("locator.android.compose")
            pluginManager.apply("locator.hilt")
            pluginManager.apply("locator.hardcoded-text-check")
            pluginManager.apply("io.github.takahirom.roborazzi")
            // Baselines are committed next to the tests.
            extensions.configure<RoborazziExtension> {
                outputDir.set(layout.projectDirectory.dir("src/test/screenshots"))
            }
            dependencies.add("implementation", project(":core:designsystem"))
            dependencies.add("implementation", project(":core:model"))
            dependencies.add("implementation", project(":core:common"))
            dependencies.add("implementation", lib("androidx-lifecycle-viewmodel-compose"))
            dependencies.add("implementation", lib("androidx-lifecycle-runtime-compose"))
            dependencies.add("implementation", lib("androidx-hilt-navigation-compose"))
            dependencies.add("implementation", lib("kotlinx-coroutines-android"))
            dependencies.add("testImplementation", project(":core:testing"))
            dependencies.add("testImplementation", lib("robolectric"))
            dependencies.add("testImplementation", lib("roborazzi"))
            dependencies.add("testImplementation", lib("roborazzi-compose"))
            dependencies.add("testImplementation", lib("roborazzi-junit-rule"))
        }
    }
}
