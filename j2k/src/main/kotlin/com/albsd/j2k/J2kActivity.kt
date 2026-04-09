package com.albsd.j2k

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiManager
import org.jetbrains.kotlin.j2k.ConverterSettings
import org.jetbrains.kotlin.j2k.J2kConverterExtension
import java.io.File

class J2kActivity : ProjectActivity {

    companion object {
        const val SOURCE_DIR_PROP = "j2k.sourceDir"
        const val OUTPUT_DIR_PROP = "j2k.outputDir"
    }

    override suspend fun execute(project: Project) {
        val sourceDirPath = System.getProperty(SOURCE_DIR_PROP)
        val outputDirPath = System.getProperty(OUTPUT_DIR_PROP) ?: "converted-kotlin"

        if (sourceDirPath == null) {
            System.err.println("[j2k] ERROR: -D$SOURCE_DIR_PROP not set — aborting.")
            exit(1)
            return
        }

        val sourceDir = File(sourceDirPath)
        val outputDir = File(outputDirPath)

        if (!sourceDir.exists()) {
            System.err.println("[j2k] ERROR: source dir not found: $sourceDirPath")
            exit(1)
            return
        }

        println("[j2k] Source : $sourceDirPath")
        println("[j2k] Output : $outputDirPath")

        try {
            val stats = convert(project, sourceDir, outputDir)
            println("[j2k] ─────────────────────────────────────")
            println("[j2k] Converted : ${stats.converted}")
            println("[j2k] Skipped   : ${stats.skipped}")
            println("[j2k] Failed    : ${stats.failed}")
            println("[j2k] ─────────────────────────────────────")
            exit(0)
        } catch (e: Exception) {
            System.err.println("[j2k] FATAL: ${e.message}")
            e.printStackTrace()
            exit(1)
        }
    }

    private data class Stats(
        var converted: Int = 0,
        var skipped: Int = 0,
        var failed: Int = 0
    )

    private fun convert(project: Project, sourceDir: File, outputDir: File): Stats {
        outputDir.mkdirs()

        val javaFiles = sourceDir.walkTopDown()
            .filter { it.isFile && it.extension == "java" }
            .toList()

        println("[j2k] Found ${javaFiles.size} Java file(s) under ${sourceDir.absolutePath}")

        val psiManager = PsiManager.getInstance(project)

        LocalFileSystem.getInstance().refresh(true)

        val extension = J2kConverterExtension.extension(J2kConverterExtension.Kind.K1_NEW)

        val converter = extension.createJavaToKotlinConverter(
            project = project,
            targetModule = null,
            settings = ConverterSettings.defaultSettings
        )

        val stats = Stats()
        for (javaFile in javaFiles) {
            val virtualFile = LocalFileSystem.getInstance()
                .refreshAndFindFileByPath(javaFile.absolutePath)

            if (virtualFile == null) {
                System.err.println("[j2k] SKIP (vfs not found): ${javaFile.name}")
                stats.skipped++
                continue
            }

            val psiFile = ApplicationManager.getApplication().runReadAction<PsiJavaFile?> {
                psiManager.findFile(virtualFile) as? PsiJavaFile
            }

            if (psiFile == null) {
                System.err.println("[j2k] SKIP (not a PsiJavaFile): ${javaFile.name}")
                stats.skipped++
                continue
            }

            // plugins/kotlin/j2k/k1.new.post-processing/src/org/jetbrains/kotlin/idea/j2k/post/processing/NewJ2kConverterExtension.kt
            val kotlinSource = try {
                converter.elementsToKotlin(listOf(psiFile))
                    .results
                    .firstOrNull()
                    ?.text
            } catch (e: Exception) {
                System.err.println("[j2k] ERROR converting ${javaFile.name}: ${e.message}")
                null
            }

            if (kotlinSource == null) {
                stats.failed++
                continue
            }

            val outFile = File(outputDir, "${javaFile.nameWithoutExtension}.kt")
            outFile.writeText(kotlinSource)
            println("[j2k] OK  ${javaFile.name} → ${outFile.name}")
            stats.converted++
        }

        return stats
    }

    private fun exit(code: Int) {
        ApplicationManager.getApplication().exit(false, true, code != 0)
    }
}
