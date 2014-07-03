/*
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
package org.jruyi.osgi.gradle.plugin

import aQute.bnd.osgi.Builder
import org.gradle.api.Plugin
import org.gradle.api.Project

class OsgiBndPlugin implements Plugin<Project> {

	@Override
	public void apply(Project p) {

		p.configure(p) { project ->

			project.plugins.apply 'java'

			jar {
				deleteAllActions()
				doLast {
					Builder builder = new Builder()
					try {
						builder.properties = Helper.getProperties(project)
						Helper.handleBundleSymbolicName(builder, project)
						builder.bundleVersion = Helper.getVersion(project)

						File[] files = Helper.getClasspath(project)
						builder.setClasspath files as File[]

						builder.sourcepath = Helper.getSources(project)

						files = Helper.handleIncludeResource(builder, project)
						builder.addClasspath files as Collection<File>

						builder.base = project.projectDir

						builder.build().write(Helper.check(archivePath))
					} finally {
						builder.close()
					}
				}
			}
		}
	}


}
