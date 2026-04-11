package com.albsd.j2k

import com.intellij.ide.AppLifecycleListener
import com.intellij.ide.GeneralSettings
import com.intellij.ide.impl.OpenProjectTask
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiManager
import org.jetbrains.kotlin.j2k.ConverterSettings
import org.jetbrains.kotlin.j2k.J2kConverterExtension
import java.io.File
import java.nio.file.Path

class J2kAppListener : AppLifecycleListener {

    override fun appStarted() {
        System.err.println("[j2k] appStarted() fired")
        System.err.println("[j2k] j2k.projectDir=${System.getProperty("j2k.projectDir")}")
        System.err.println("[j2k] j2k.sourceDir=${System.getProperty("j2k.sourceDir")}")
        System.err.println("[j2k] j2k.outputDir=${System.getProperty("j2k.outputDir")}")

        val projectDir = System.getProperty("j2k.projectDir") ?: run {
            System.err.println("[j2k] j2k.projectDir not set — skipping")
            return
        }
        val sourceDirPath = System.getProperty("j2k.sourceDir") ?: run {
            System.err.println("[j2k] ERROR: j2k.sourceDir not set — aborting")
            exit(1)
            return
        }
        val outputDirPath = System.getProperty("j2k.outputDir") ?: "converted-kotlin"

        val projectPath = Path.of(projectDir)
        if (!projectPath.toFile().exists()) {
            System.err.println("[j2k] ERROR: project dir not found: $projectDir")
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

        System.err.println("[j2k] Opening project: $projectDir")
        GeneralSettings.getInstance().confirmOpenNewProject = GeneralSettings.OPEN_PROJECT_SAME_WINDOW

        ApplicationManager.getApplication().executeOnPooledThread {
            val project = ProjectManagerEx.getInstanceEx()
                .openProject(projectPath, OpenProjectTask {
                    runConfigurators = false
                })

            if (project == null) {
                System.err.println("[j2k] ERROR: openProject() returned null for $projectDir")
                exit(1)
                return@executeOnPooledThread
            }

            System.err.println("[j2k] Project opened: ${project.name}")
            System.err.println("[j2k] Waiting for smart mode...")

            DumbService.getInstance(project).runWhenSmart {
                ApplicationManager.getApplication().executeOnPooledThread {
                    try {
                        val stats = convert(project, sourceDir, outputDir)
                        System.err.println("[j2k] -------------------------------------")
                        System.err.println("[j2k] Converted : ${stats.converted}")
                        System.err.println("[j2k] Skipped   : ${stats.skipped}")
                        System.err.println("[j2k] Failed    : ${stats.failed}")
                        System.err.println("[j2k] -------------------------------------")
                        exit(0)
                    } catch (e: Exception) {
                        System.err.println("[j2k] FATAL: ${e.message}")
                        e.printStackTrace()
                        exit(1)
                    }
                }
            }
        }
    }

    private data class Stats(
        var converted: Int = 0,
        var skipped: Int = 0,
        var failed: Int = 0
    )

    private fun convert(project: com.intellij.openapi.project.Project, sourceDir: File, outputDir: File): Stats {
        outputDir.mkdirs()

        val javaFiles = sourceDir.walkTopDown()
            .filter { it.isFile && it.extension == "java" }
            .toList()

        System.err.println("[j2k] Found ${javaFiles.size} Java file(s) under ${sourceDir.absolutePath}")

        val psiManager = PsiManager.getInstance(project)
        LocalFileSystem.getInstance().refresh(true)

        J2kConverterExtension.EP_NAME.extensionList.forEachIndexed { i, ext ->
            System.err.println("[j2k] Available extension[$i]: ${ext::class.qualifiedName}")
        }

        val extension = J2kConverterExtension.EP_NAME.extensionList.firstOrNull() ?: run {
            System.err.println("[j2k] ERROR: no J2kConverterExtension found")
            throw IllegalStateException("No J2kConverterExtension available")
        }
        System.err.println("[j2k] Using extension: ${extension::class.qualifiedName}")

        val converter = extension.createJavaToKotlinConverter(
            project = project,
            targetModule = null,
            settings = ConverterSettings.defaultSettings
        )
        System.err.println("[j2k] Using converter: ${converter::class.qualifiedName}")

        val stats = Stats()
        for (javaFile in javaFiles) {
            val virtualFile = LocalFileSystem.getInstance()
                .refreshAndFindFileByPath(javaFile.absolutePath)

            if (virtualFile == null) {
                System.err.println("[j2k] SKIP (vfs not found): ${javaFile.name}")
                stats.skipped++
                continue
            }

            val psiFile = DumbService.getInstance(project).runReadActionInSmartMode<PsiJavaFile?> {
                psiManager.findFile(virtualFile) as? PsiJavaFile
            }

            if (psiFile == null) {
                System.err.println("[j2k] SKIP (not a PsiJavaFile): ${javaFile.name}")
                stats.skipped++
                continue
            }

            val kotlinSource = try {
                DumbService.getInstance(project).runReadActionInSmartMode<String?> {
                    converter.elementsToKotlin(listOf(psiFile))
                        .results
                        .firstOrNull()
                        ?.text
                }
            } catch (e: Exception) {
                System.err.println("[j2k] ERROR converting ${javaFile.name}: ${e.message}")
                null
            }

            if (kotlinSource == null) {
                stats.failed++
                continue
            }

            val relativePath = javaFile.relativeTo(sourceDir)
            val outFile = File(outputDir, relativePath.path.removeSuffix(".java") + ".kt")
            outFile.parentFile.mkdirs()
            outFile.writeText(kotlinSource)
            System.err.println("[j2k] OK  ${javaFile.name} -> ${outFile.name}")
            stats.converted++
        }

        return stats
    }

    private fun exit(code: Int) {
        ApplicationManager.getApplication().exit(true, false, true)
        Runtime.getRuntime().halt(code)
    }
}
