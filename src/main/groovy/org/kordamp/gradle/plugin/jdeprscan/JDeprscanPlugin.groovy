/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * Copyright 2018-2023 Andres Almiray.
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
package org.kordamp.gradle.plugin.jdeprscan

import groovy.transform.CompileStatic
import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.plugins.BasePlugin
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.tasks.TaskProvider
import org.kordamp.gradle.plugin.jdeprscan.tasks.JDeprscanReportTask

/**
 * @author Andres Almiray
 */
@CompileStatic
class JDeprscanPlugin implements Plugin<Project> {
    void apply(Project project) {
        project.gradle.sharedServices
            .registerIfAbsent('jdeprscan-banner', Banner, { spec -> })
            .get().display(project)

        project.plugins.apply(JavaPlugin)

        TaskProvider<JDeprscanReportTask> report = project.tasks.register('jdeprscanReport', JDeprscanReportTask,
            new Action<JDeprscanReportTask>() {
                @Override
                void execute(JDeprscanReportTask t) {
                    t.dependsOn(project.tasks.named('classes'))
                    t.group = BasePlugin.BUILD_GROUP
                    t.description = 'Generate a jdeprscan report on project classes and dependencies'
                    t.javaPluginConvention.set(project.convention.getPlugin(JavaPluginConvention))
                    t.reportDir.convention(project.layout.buildDirectory.dir('reports/jdeprscan'))
                    t.projectConfigurations = project.configurations
                }
            })

        project.tasks.named('check').configure(new Action<Task>() {
            @Override
            void execute(Task t) {
                t.dependsOn(report)
            }
        })
    }
}
