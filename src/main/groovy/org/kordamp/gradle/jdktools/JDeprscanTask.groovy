/*
 * Copyright 2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.kordamp.gradle.jdktools

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.zeroturnaround.exec.ProcessExecutor

/**
 * @author Andres Almiray
 */
class JDeprscanTask extends DefaultTask {
    @Input boolean forRemoval = false
    @Input int release = 9
    @Input boolean verbose = false
    @Input boolean consoleOutput = true
    @Input @Optional String javaHome
    @InputDirectory @Optional File reportsDir
    @Input @Optional List<String> configurations = ['runtime']
    @Input @Optional List<String> sourceSets = ['main']

    @TaskAction
    void evaluate() {
        File javaHomeDir = new File(javaHome ?: System.getProperty('java.home'))
        File javaBindDir = new File(javaHomeDir, 'bin')

        if (!configurations) configurations = ['runtime']
        if (!sourceSets) sourceSets = ['main']


        final List<String> baseCmd = [new File(javaBindDir, 'jdeprscan').absolutePath]
        baseCmd << '--class-path'
        baseCmd << configurations.collect { c -> project.configurations[c].files }.flatten().unique().join(File.pathSeparator) +
            File.pathSeparator +
            sourceSets.collect { s -> project.sourceSets[s].output.asPath }.flatten().unique().join(File.pathSeparator)

        if (forRemoval && release > 8) baseCmd << '--for-removal'
        baseCmd << '--release'
        baseCmd << release.toString()
        if (verbose) baseCmd << '-v'

        List<String> outputs = []
        sourceSets.each { sourceSetName ->
            project.sourceSets[sourceSetName].output.files.each { File file ->
                if (!file.exists()) {
                    return // skip
                }

                outputs << JDeprscanTask.runOn(baseCmd, file.absolutePath)
            }
        }

        if (consoleOutput) println outputs.join('\n')

        File parentFile = reportsDir ?: project.file("${project.buildDir}/reports/jdeprscan")
        if (!parentFile.exists()) parentFile.mkdirs()
        File logFile = new File(parentFile, 'jdeprscan-report.txt')

        logFile.withPrintWriter { w -> outputs.each { f -> w.println(f) } }
    }

    private static String runOn(List<String> baseCmd, String path) {
        List<String> cmd = []
        cmd.addAll(baseCmd)
        cmd.add(path)

        ByteArrayOutputStream out = new ByteArrayOutputStream()
        new ProcessExecutor(cmd).redirectOutput(out).execute().getExitValue()
        return out.toString().trim()
    }
}
