import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.psi.KtPsiFactory
import java.io.File

fun main(args: Array<String>) {
    val disposable = Disposer.newDisposable()
    var failures = 0
    var files = 0
    try {
        val environment = KotlinCoreEnvironment.createForProduction(disposable, CompilerConfiguration(), EnvironmentConfigFiles.JVM_CONFIG_FILES)
        val factory = KtPsiFactory(environment.project, false)
        File(args[0]).walkTopDown().filter { it.isFile && (it.extension == "kt" || it.extension == "kts") && "build" !in it.toPath().map { p -> p.toString() } && "test-output" !in it.path }.forEach { file ->
            val psi = factory.createFile(file.name, file.readText())
            val errors = PsiTreeUtil.collectElementsOfType(psi, PsiErrorElement::class.java)
            errors.forEach { error ->
                val line = file.readText().take(error.textOffset).count { it == '\n' } + 1
                println("SYNTAX ERROR ${file.path}:$line ${error.errorDescription}")
                failures++
            }
            files++
        }
    } finally { Disposer.dispose(disposable) }
    println("Kotlin syntax: $files files, $failures parse errors. Android API types are NOT checked.")
    check(failures == 0)
}
