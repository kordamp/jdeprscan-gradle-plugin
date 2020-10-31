/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * Copyright 2018-2020 Andres Almiray.
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
package org.kordamp.gradle.plugin.jdeprscan.tasks

import groovy.transform.CompileStatic
import org.gradle.api.DefaultTask
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import org.kordamp.gradle.property.BooleanState
import org.kordamp.gradle.property.IntegerState
import org.kordamp.gradle.property.ListState
import org.kordamp.gradle.property.SimpleBooleanState
import org.kordamp.gradle.property.SimpleIntegerState
import org.kordamp.gradle.property.SimpleListState
import org.kordamp.gradle.property.SimpleStringState
import org.kordamp.gradle.property.StringState
import org.zeroturnaround.exec.ProcessExecutor

/**
 * @author Andres Almiray
 */
@CompileStatic
class JDeprscanReportTask extends DefaultTask {
    private final IntegerState release
    private final BooleanState forRemoval
    private final BooleanState verbose
    private final BooleanState consoleOutput
    private final StringState javaHome
    private final ListState configurations
    private final ListState sourceSets

    private Object reportDir

    JDeprscanReportTask() {
        forRemoval = SimpleBooleanState.of(this, 'jdeprscan.for.removal', false)
        verbose = SimpleBooleanState.of(this, 'jdeprscan.verbose', false)
        consoleOutput = SimpleBooleanState.of(this, 'jdeprscan.console.output', true)
        javaHome = SimpleStringState.of(this, 'jdeprscan.java.home', System.getProperty('java.home'))

        configurations = SimpleListState.of(this, 'jdeprscan.configurations', ['runtime'])
        sourceSets = SimpleListState.of(this, 'jdeprscan.sourcesets', ['main'])

        release = SimpleIntegerState.of(this, 'jdeprscan.release', 9)
    }

    @Option(option = 'jdeprscan-verbose', description = 'Enables additional message output during processing')
    void setVerbose(boolean value) { verbose.property.set(value) }

    @Option(option = 'jdeprscan-for-removal', description = 'Limits scanning to APIs deprecated for removal')
    void setForRemoval(boolean value) { forRemoval.property.set(value) }

    @Option(option = 'jdeprscan-console-output', description = 'Print out report to console')
    void setConsoleOutput(boolean value) { consoleOutput.property.set(value) }

    @Option(option = 'jdeprscan-java-home', description = 'The JDK home to use')
    void setJavaHome(String value) { javaHome.property.set(value) }

    @Option(option = 'jdeprscan-configurations', description = 'Configurations to be analyzed')
    void setConfigurations(String value) { configurations.property.set(value.split(',').toList()) }

    @Option(option = 'jdeprscan-sourcesets', description = 'SourceSets to be analyzed')
    void setSourceSets(String value) { sourceSets.property.set(value.split(',').toList()) }

    @Option(option = 'jdeprscan-release', description = 'Specifies the Java SE release that provides the set of deprecated APIs for scanning')
    void setRelease(String value) { release.property.set(Integer.valueOf(value)) }

    @Internal
    Property<Boolean> getVerbose() { verbose.property }

    @Input
    Provider<Boolean> getResolvedVerbose() { verbose.provider }

    @Internal
    Property<Boolean> getForRemoval() { forRemoval.property }

    @Input
    Provider<Boolean> getResolvedForRemoval() { forRemoval.provider }

    @Internal
    Property<Boolean> getConsoleOutput() { consoleOutput.property }

    @Input
    Provider<Boolean> getResolvedConsoleOutput() { consoleOutput.provider }

    @Internal
    Property<String> getJavaHome() { javaHome.property }

    @Input
    Provider<String> getResolvedJavaHome() { javaHome.provider }

    @Internal
    ListProperty<String> getConfigurations() { configurations.property }

    @Input
    @Optional
    Provider<List<String>> getResolvedConfigurations() { configurations.provider }

    @Internal
    ListProperty<String> getSourceSets() { sourceSets.property }

    @Input
    @Optional
    Provider<List<String>> getResolvedSourceSets() { sourceSets.provider }

    @Internal
    Property<Integer> getRelease() { release.property }

    @Input
    Provider<Integer> getResolvedRelease() { release.provider }

    @TaskAction
    void evaluate() {
        File javaHomeDir = new File(resolvedJavaHome.get())
        File javaBindDir = new File(javaHomeDir, 'bin')

        JavaPluginConvention convention = project.convention.getPlugin(JavaPluginConvention)

        final List<String> baseCmd = [new File(javaBindDir, 'jdeprscan').absolutePath]
        baseCmd << '--class-path'
        baseCmd << resolvedConfigurations.getOrElse(['runtimeClasspath'])
            .collect { c -> project.configurations[c].files }.flatten().unique().join(File.pathSeparator) +
            File.pathSeparator +
            resolvedSourceSets.getOrElse(['main'])
                .collect { s -> convention.sourceSets.findByName(s).output.asPath }.flatten().unique().join(File.pathSeparator)

        if (resolvedForRemoval.get() && resolvedRelease.get() > 8) baseCmd << '--for-removal'
        baseCmd << '--release'
        baseCmd << resolvedRelease.get().toString()
        if (resolvedVerbose.get()) baseCmd << '-v'

        List<String> outputs = []
        resolvedSourceSets.getOrElse(['main']).each { sourceSetName ->
            convention.sourceSets.findByName(sourceSetName).output.files.each { File file ->
                if (!file.exists()) {
                    return // skip
                }

                outputs << JDeprscanReportTask.runOn(baseCmd, file.absolutePath)
            }
        }

        if (resolvedConsoleOutput.get()) println outputs.join('\n')

        File parentFile = getReportsDir()
        if (!parentFile.exists()) parentFile.mkdirs()
        File logFile = new File(parentFile, 'jdeprscan-report.txt')

        logFile.withPrintWriter { w -> outputs.each { f -> w.println(f) } }
    }

    @OutputDirectory
    File getReportsDir() {
        if (this.reportDir == null) {
            File reportsDir = new File(project.buildDir, 'reports')
            this.reportDir = new File(reportsDir, 'jdeprscan')
        }
        project.file(this.reportDir)
    }

    void setReportsDir(File f) {
        this.reportDir = f
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
