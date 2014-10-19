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
package org.jruyi.gradle.osgi.plugin

import aQute.bnd.osgi.Builder
import aQute.bnd.osgi.Constants
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.file.FileTree

import java.util.regex.Matcher
import java.util.regex.Pattern

class Helper {

	static Map<String, ?> getProperties(Project project) {
		Map<String, ?> properties = new LinkedHashMap(project.jar.manifest.effectiveManifest.attributes)
		Map<String, ?> projProps = project.properties
		handleBundleName(properties, projProps, project.name)
		handleBundleDesc(properties, project)
		handleBundleDocUrl(properties, projProps)
		handleBundleVendor(properties, projProps)
		handleBundleLicense(properties, projProps)
		properties
	}

	static File check(File file) {
		File parentFile = file.parentFile
		if (!parentFile.exists())
			parentFile.mkdirs()
		file
	}

	static File[] getClasspath(Project project) {
		project.configurations.runtime.files
	}

	static File[] getSources(Project project) {
		project.sourceSets.main.allSource.srcDirs.findAll {
			it.exists()
		}
	}

	static File[] handleIncludeResource(Builder builder, Project project) {
		def output = project.sourceSets.main.output
		File[] files = [output.classesDir, output.resourcesDir].findAll {
			it.exists()
		}
		String resources = builder.getProperty(Constants.INCLUDERESOURCE)
		if (resources == null || resources.empty) {
			resources = files.join(',')
			builder.setProperty(Constants.INCLUDERESOURCE, resources)
		} else if (resources.contains('{gradle-resources}')) {
			resources = resources.replace('{gradle-resources}', files.join(','))
			builder.setProperty(Constants.INCLUDERESOURCE, resources);
		}

		files
	}

	static String getVersion(Project project) {
		String version = project.version
		if (version == null || version.empty)
			throw new GradleException("Project version is not specified")

		Pattern osgiVersion = Pattern.compile('[0-9]+(\\.[0-9]+(\\.[0-9]+(\\.[0-9A-Za-z_-]+)?)?)?')
		if (osgiVersion.matcher(version).matches())
			return version

		// check for dated snapshot versions with only major or major and minor
		Pattern datedSnapshot = Pattern.compile('([0-9])(\\.([0-9]))?(\\.([0-9]))?\\-([0-9]{8}\\.[0-9]{6}\\-[0-9]*)')
		Matcher m = datedSnapshot.matcher(version)
		if (m.matches()) {
			String major = m.group(1)
			String minor = (m.group(3) != null) ? m.group(3) : '0'
			String service = (m.group(5) != null) ? m.group(5) : '0'
			String qualifier = m.group(6).replaceAll('-', '_').replaceAll('\\.', '_')
			version = "${major}.${minor}.${service}.${qualifier}"
		}

		// else transform first - to . and others to _
		version = version.replaceFirst('-', '\\.')
		version = version.replaceAll('-', '_')
		m = osgiVersion.matcher(version)
		if (m.matches())
			return version

		// remove dots in the middle of the qualifier
		Pattern dotsInQualifier = Pattern.compile(
				'([0-9])(\\.[0-9])?\\.([0-9A-Za-z_-]+)\\.([0-9A-Za-z_-]+)')
		m = dotsInQualifier.matcher(version)
		if (m.matches()) {
			String s1 = m.group(1)
			String s2 = m.group(2)
			String s3 = m.group(3)
			String s4 = m.group(4)

			Pattern onlyNumbers = Pattern.compile('[0-9]+')
			Matcher qualifierMatcher = onlyNumbers.matcher(s3)

			// if last portion before dot is only numbers then it's not in the middle of the
			// qualifier
			if (!qualifierMatcher.matches())
				version = "${s1}${s2}.${s3}_${s4}"
		}

		// convert
		// 1.string   -> 1.0.0.string
		// 1.2.string -> 1.2.0.string
		// 1          -> 1.0.0
		// 1.1        -> 1.1.0
		Pattern needToFillZeros = Pattern.compile("([0-9])(\\.([0-9]))?(\\.([0-9A-Za-z_-]+))?")
		m = needToFillZeros.matcher(version)
		if (m.matches()) {
			String major = m.group(1)
			String minor = m.group(3)
			String service = null
			String qualifier = m.group(5)

			// if there's no qualifier just fill with 0s
			if (qualifier == null)
				version = Helper.version(major, minor, service, qualifier)
			else {
				// if last portion is only numbers then it's not a qualifier
				Pattern onlyNumbers = Pattern.compile('[0-9]+')
				Matcher qualifierMatcher = onlyNumbers.matcher(qualifier)
				if (qualifierMatcher.matches()) {
					if (minor == null)
						minor = qualifier
					else
						service = qualifier
					version = Helper.version(major, minor, service, null)
				} else
					version = Helper.version(major, minor, service, qualifier)
			}
		}

		m = osgiVersion.matcher(version)
		// if still its not OSGi version then add everything as qualifier
		if (!m.matches()) {
			String qualifier = version.replaceAll('\\.', '_')
			version = "0.0.0.${qualifier}"
		}

		version
	}

	static void handleBundleSymbolicName(Builder builder, Project project) {

		String bundleSymbolicName = builder.getProperty(Constants.BUNDLE_SYMBOLICNAME)
		if (bundleSymbolicName != null && !bundleSymbolicName.empty) {
			builder.bundleSymbolicName = bundleSymbolicName
			return
		}

		String group = project.getGroup().toString()
		String archiveBaseName = project.jar.baseName
		if (archiveBaseName == null || archiveBaseName.empty) {
			archiveBaseName = project.archivesBaseName
			if (archiveBaseName == null || archiveBaseName.empty)
				archiveBaseName = project.name
		}

		if (archiveBaseName.startsWith(group)) {
			builder.bundleSymbolicName = archiveBaseName
			return
		}

		int i = group.lastIndexOf('.')
		if (i < 0) {
			String groupIdFromPackage = getGroupIdFromPackage(project.fileTree(project.sourceSets.main.output.classesDir))
			if (groupIdFromPackage != null) {
				builder.bundleSymbolicName = groupIdFromPackage
				return
			}
		}

		String lastSection = group.substring(++i)
		if (archiveBaseName.equals(lastSection)) {
			builder.bundleSymbolicName = group
			return
		}

		if (archiveBaseName.startsWith(lastSection)) {
			String name = archiveBaseName.substring(lastSection.length())
			if (Character.isLetterOrDigit(name.charAt(0)))
				builder.bundleSymbolicName = Helper.bundleSymbolicName(group, name)
			else
				builder.bundleSymbolicName = Helper.bundleSymbolicName(group, name.substring(1))
			return
		}
		builder.bundleSymbolicName = bundleSymbolicName(group, archiveBaseName)
	}

	private static String bundleSymbolicName(String groupId, String archiveName) {
		return "${groupId}.${archiveName}"
	}

	private static String version(String major, String minor, String service, String qualifier) {
		StringBuilder sb = new StringBuilder()
		sb.append(major != null ? major : '0')
		sb.append('.')
		sb.append(minor != null ? minor : '0')
		sb.append('.')
		sb.append(service != null ? service : '0')
		if (qualifier != null) {
			sb.append('.')
			sb.append(qualifier)
		}
		return sb.toString()
	}

	private static handleBundleDesc(Map<String, Object> properties, Project project) {
		String desc = properties[Constants.BUNDLE_DESCRIPTION];
		if (desc == null || desc.empty) {
			desc = project.description
			if (desc != null && !desc.empty)
				properties[Constants.BUNDLE_DESCRIPTION] = desc
		}
	}

	private static handleBundleName(Map<String, Object> properties, Map<String, ?> projProps, String projName) {
		String name = properties[Constants.BUNDLE_NAME]
		if (name == null || name.empty) {
			name = projProps['title']
			if (name != null && !name.empty)
				properties[Constants.BUNDLE_NAME] = name
			else
				properties[Constants.BUNDLE_NAME] = projName
		}
	}

	private static handleBundleVendor(Map<String, Object> properties, Map<String, ?> projProps) {
		String vendor = properties[Constants.BUNDLE_VENDOR]
		if (vendor == null || vendor.empty) {
			vendor = projProps['organizationName']
			if (vendor != null && !vendor.empty)
				properties[Constants.BUNDLE_VENDOR] = vendor
		}
	}

	private static handleBundleDocUrl(Map<String, Object> properties, Map<String, ?> projProps) {
		String docUrl = properties[Constants.BUNDLE_DOCURL]
		if (docUrl == null || docUrl.empty) {
			docUrl = projProps['organizationUrl']
			if (docUrl != null && !docUrl.empty)
				properties[Constants.BUNDLE_DOCURL] = docUrl
		}
	}

	private static handleBundleLicense(Map<String, Object> properties, Map<String, ?> projProps) {
		String licenseUrl = properties[Constants.BUNDLE_LICENSE]
		if (licenseUrl == null || licenseUrl.empty) {
			licenseUrl = projProps['licenseUrl']
			if (licenseUrl != null && !licenseUrl.empty)
				properties[Constants.BUNDLE_LICENSE] = licenseUrl
		}
	}

	private static String getGroupIdFromPackage(FileTree classes) {

		Set<String> packageNames = new HashSet<>()
		classes.each { File file ->
			if (file.name.endsWith(".class")) {
				String packageName = file.parent
				if (parent != null)
					packageNames.add(parent)
			}
		}

		String fileSeperator = "\\" + File.separator
		// find the top package
		String[] groupIdSections = null
		packageNames.each { String packageName ->
			String[] packageNameSections = packageName.split(fileSeperator)
			if (groupIdSections == null) // first candidate
				groupIdSections = packageNameSections
			else { // if ( packageNameSections.length < groupIdSections.length )
				/*
				 * find the common portion of current package and previous selected groupId
				 */
				int i
				for (i = 0; (i < packageNameSections.length) && (i < groupIdSections.length); ++i) {
					if (!packageNameSections[i].equals(groupIdSections[i]))
						break
				}
				if (i < groupIdSections.length) {
					groupIdSections = new String[i]
					System.arraycopy(packageNameSections, 0, groupIdSections, 0, i)
				}
			}
		}

		// only one section as id doesn't seem enough, so ignore it
		if ((groupIdSections == null) || (groupIdSections.length < 2))
			return null

		groupIdSections.join('.');
	}
}
