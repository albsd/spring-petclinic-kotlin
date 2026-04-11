package com.albsd.j2k

import com.intellij.ide.AppLifecycleListener
import com.intellij.ide.impl.OpenProjectTask
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.ex.ProjectManagerEx
import java.nio.file.Path

class J2kAppListener : AppLifecycleListener {

    override fun appStarted() {
        println("[j2k] appStarted() fired, j2k.projectDir=${System.getProperty("j2k.projectDir")}")
        val projectDir = System.getProperty("j2k.projectDir") ?: return
        val path = Path.of(projectDir)

        if (!path.toFile().exists()) {
            System.err.println("[j2k] ERROR: project dir not found: $projectDir")
            return
        }

        println("[j2k] Opening project: $projectDir")

        ApplicationManager.getApplication().invokeLater {
            ProjectManagerEx.getInstanceEx()
                .openProject(path, OpenProjectTask { runConfigurators = false })
        }
    }
}
