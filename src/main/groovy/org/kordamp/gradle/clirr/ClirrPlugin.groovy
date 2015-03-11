/*
 * Copyright 2008-2015 the original author or authors.
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
package org.kordamp.gradle.clirr

import org.gradle.BuildAdapter
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.invocation.Gradle
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.ReportingBasePlugin
import org.gradle.api.reporting.ReportingExtension

class ClirrPlugin implements Plugin<Project> {
    private static final String CLIRR = 'clirr'

    private ClirrPluginExtension extension

    @Override
    void apply(Project project) {
        project.plugins.apply(ReportingBasePlugin)
        project.plugins.apply(JavaPlugin)
        ClirrPluginExtension extension = createExtension(project)
        addClirrTask(project, extension)
        registerBuildListener(project, extension)
    }

    private static void registerBuildListener(
        final Project project, final ClirrPluginExtension extension) {
        project.gradle.addBuildListener(new BuildAdapter() {
            @Override
            void projectsEvaluated(Gradle gradle) {
                if (!extension.enabled) {
                    // do not evaluate baseline if disabled
                    return
                }

                // temporary change the group of the current project  otherwise
                // the latest version will always override the baseline
                String projectGroup = project.group
                try {
                    project.group = projectGroup + '.clirr'
                    Configuration detached = project.configurations.detachedConfiguration(
                        project.dependencies.create(project.clirr.baseline)
                    )
                    detached.transitive = true
                    detached.resolve()
                    extension.baseFiles = detached.files
                } finally {
                    project.group = projectGroup
                }
            }
        })
    }

    ClirrPluginExtension createExtension(Project project) {
        extension = project.extensions.create(CLIRR, ClirrPluginExtension)
        extension.reportsDir = project.extensions.getByType(ReportingExtension).file(CLIRR)
        extension
    }

    void addClirrTask(Project project, ClirrPluginExtension extension) {
        ClirrTask task = project.task(CLIRR,
            type: ClirrTask,
            group: 'Verification',
            description: 'Determines the binary compatibility of the current codebase against a previous release') {
            newFiles = project.tasks['jar'].outputs.files
            newClasspath = project.configurations['compile']
        }

        project.tasks.getByName('check').dependsOn(task)
    }
}