import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * Android Lint's HardcodedText only looks at XML. This task finds user-facing string literals in
 * Compose code (`Text("…")`, `text = "…"`, `contentDescription = "…"`) so that every string stays in `values/`.
 */
abstract class VerifyNoHardcodedTextTask : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sources: ConfigurableFileCollection

    @TaskAction
    fun verify() {
        val pattern = Regex("""(\bText\(\s*(text\s*=\s*)?|\btext\s*=\s*|\bcontentDescription\s*=\s*|\blabel\s*=\s*)"[^"]""")
        val problems = sources.files.filter { it.extension == "kt" && !it.path.contains("""\test\""") }.flatMap { file ->
            file.readLines().mapIndexedNotNull { index, line ->
                if (pattern.containsMatchIn(line)) "${file.name}:${index + 1}: ${line.trim()}" else null
            }
        }
        if (problems.isNotEmpty()) {
            throw GradleException("Hard-coded user-facing text, use string resources:\n" + problems.joinToString("\n") { " - $it" })
        }
    }
}

class HardcodedTextCheckConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            val task = tasks.register("verifyNoHardcodedText", VerifyNoHardcodedTextTask::class.java) {
                group = "verification"
                sources.from(fileTree("src/main") { include("**/*.kt") })
            }
            tasks.matching { it.name == "check" }.configureEach { dependsOn(task) }
        }
    }
}
