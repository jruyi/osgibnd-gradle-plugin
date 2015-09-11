## Gradle OSGiBnd Plugin

Gradle OSGiBnd Plugin uses the [BND](http://www.aqute.biz/Bnd/Bnd) tool to generate OSGi bundles.

### Usage
To use this plugin, add the following to your build script.

```groovy
buildscript {
	repositories {
		maven {
			url "https://plugins.gradle.org/m2/"
		}
	}
	dependencies {
		classpath "gradle.plugin.org.jruyi.gradle:osgibnd-gradle-plugin:0.4.0"
	}
}

apply plugin: 'org.jruyi.osgibnd'
```

Or for gradle 2.1+

```groovy
plugins {
	id "org.jruyi.osgibnd" version "0.4.0"
}
```

### Implicitly Applied Plugins

Applies the Java plugin.

### Tasks

This plugin does not add any tasks.

Add bnd instructions to the jar manifest to change the plugin behavior.

##### Example

```groovy
jar {
	manifest {
		attributes (
				'Export-Package': 'org.jruyi.example.*',
				'-dsannotations': '*',
		)
	}
}
```

Please visit [BND website] (http://www.aqute.biz/Bnd/Format) for a complete list of instructions and their format

### Default Behavior

* \<Bundle-SymbolicName\> is computed in the same way as the shared [Maven2OsgiConverter](http://svn.apache.org/repos/asf/maven/shared/trunk/maven-osgi/src/main/java/org/apache/maven/shared/osgi/DefaultMaven2OsgiConverter.java) does.
Basically, the symbolic name is generated as project.group + '.' + project.jar.baseName except the follow cases.
  * If Bundle-SymbolicName is provided in the manifest then that value is used.
  * If project.group has only one section (no dots), then the first package name with classes is used. eg. commons-logging:commons-logging -> org.apache.commons.logging
  * If project.group ends with project.jar.baseName then project.group is used. eg. org.jruyi.gradle:gradle -> org.jruyi.gradle
  * If project.jar.baseName starts with last section of project.group that portion is removed. eg. org.jruyi:jruyi-gradle -> org.jruyi.gradle

* \<Bundle-Version\> is assumed to be project.version but is normalized to the OSGi version format of 'MAJOR.MINOR.MICRO.QUALIFIER', for example '0.1-SNAPSHOT' would become '0.1.0.SNAPSHOT'.

* \<Bundle-Name\> is set to project property 'title' if Bundle-Name is not provided in manifest. If property title is not provided, then Bundle-Name is set to project.name

* \<Bundle-Description\> is set to project.description if provided

* \<Bundle-Vendor\> is set to project property 'organizationName' if Bundle-Description is not provided in manifest and property organizationName is provided

* \<Bundle-DocURL\> is set to project property 'organizationUrl' if Bundle-DocURL is not provided in manifest and property organizationUrl is provided

* \<Bundle-License\> is set to project property 'licenseUrl' if Bundle-License is not provided in manifest and property licenseUrl is provided

## License

OSGiBnd Gradle Plugin is licensed under the [Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.html).
