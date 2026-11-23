import com.android.build.api.dsl.CommonExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.getByType

class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
            extensions.getByType<CommonExtension>().buildFeatures.compose = true
            val bom = dependencies.platform(lib("androidx-compose-bom"))
            dependencies.add("implementation", bom)
            dependencies.add("testImplementation", bom)
            listOf(
                "androidx-compose-ui",
                "androidx-compose-material3",
                "androidx-compose-ui-tooling-preview",
            ).forEach { dependencies.add("implementation", lib(it)) }
            dependencies.add("debugImplementation", lib("androidx-compose-ui-tooling"))
            dependencies.add("debugImplementation", lib("androidx-compose-ui-test-manifest"))
            dependencies.add("testImplementation", lib("androidx-compose-ui-test-junit4"))
        }
    }
}
