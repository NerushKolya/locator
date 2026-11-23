import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/** FR-019: the store artifact has no `internal` classes and no location, notification or foreground-service permission. */
abstract class VerifyStoreArtifactTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val mappingFile: RegularFileProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val manifests: ConfigurableFileCollection

    @TaskAction
    fun verify() {
        val problems = mutableListOf<String>()
        // R8 renames classes in the APK, so the original names are read from its mapping file.
        val internal = "io.github.nerushkolya.locator.internal."
        if (mappingFile.get().asFile.readLines().any { it.startsWith(internal) }) {
            problems += "store artifact contains classes of package ${internal.trimEnd('.')}"
        }
        val forbidden = Regex("""android:name="android\.permission\.(\w*LOCATION\w*|POST_NOTIFICATIONS|FOREGROUND_SERVICE\w*)"""")
        manifests.files.forEach { file ->
            forbidden.findAll(file.readText()).forEach {
                problems += "${file.parentFile.parentFile.name}: forbidden permission ${it.groupValues[1]}"
            }
        }
        if (problems.isNotEmpty()) {
            throw GradleException("Store artifact check failed:\n" + problems.joinToString("\n") { " - $it" })
        }
    }
}

class StoreArtifactCheckConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            tasks.register("verifyStoreArtifact", VerifyStoreArtifactTask::class.java) {
                group = "verification"
                description = "Fails when the store release contains internal classes or forbidden permissions."
                dependsOn("assembleStoreRelease")
                val variants = listOf("InternalDebug", "InternalRelease", "StoreDebug", "StoreRelease")
                variants.forEach { dependsOn("process${it}Manifest") }
                mappingFile.set(layout.buildDirectory.file("outputs/mapping/storeRelease/mapping.txt"))
                manifests.from(variants.map {
                    layout.buildDirectory.file(
                        "intermediates/merged_manifests/${it.replaceFirstChar(Char::lowercase)}/process${it}Manifest/AndroidManifest.xml",
                    )
                })
            }
        }
    }
}
