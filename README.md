# Vantage Gradle Plugin

This plugin adds support for generating Vantage 'version' files in json format and publishing them to a [Vantage](https://github.com/yodle/vantage) server.  It also enables printing warnings about dependencies with known issues, and optionally gating the build if those issues are severe enough.  It is avaiable through Maven Central as `com.yodle:vantage-gradle:<version>`.

#Tasks and Configuration Options

Vantage-gradle offers three tasks which are configured either directly or via the vantage extension.  None of the tasks are integrated into the build process by default, so you will need to add dependencies on them from the appropriate tasks (likely the `build` task) if you want to integrate them into your build.

##The Vantage Extension

* moduleName (default: `project.name`) - The name of the component.  If groupName is also specified it will be concatenated with modleName as `groupName:moduleName`
* groupName (default: `project.group`) - Optionally allows setting a group name to be prepended to the component name
* version (default: `project.version`) - The version of the component
* server (default: localhost:8080) - The location of the vantage server to report to.
* includeProjectDependencyVersions (default: true) - In cases where the same gradle project contains both an application and a library with separate publishing schedules, it may be misleading to report that the app depends on version `x.y.z` of the library as there may be unpublished changes to the library.  If this is false, project dependencies will report as `latest` rather than their configured version.

## vantageGenerate

This task generates a json file compatible with the PUT /api/v1/components/{component}/versions/{version} endpoint of Vantage.  It outputs the file to `{buildDir}/reports/vantage`.  Currently it has no configuration options beyond those provided by the Vantage extension.

## vantageCheck

This task publishes the version report generated by vantageGenerate to Vantage using the dryRun option in order to retrieve the issues that affect dependencies of the current project.  It then compares the level of the returned issues to acceptable thresholds, failing the build if there are issues of sufficient criticality.  Regardless of whether it fails the build, this task will also print out a report summary of all known issues affecting this project's dependencies.

Currently, issue thresholds can be set to:

 * DEPRECATION
 * MINOR
 * MAJOR
 * CRITICAL
 * IGNORED (This is not a real Vantage issue level.  It can be used to ignore a specific dependency or allow for printing the report without ever failing the build)

 Configuration Options:

 * strictMode (default: true) - Fail the build on a failure to successfully contact Vantage.  If false, the task will still fail the build if it successfully contacts Vantage and determines that there are fatal issues, but not if there is a general issue contacting Vantage.
 * defaultThreshold (default: CRITICAL) - If any dependencies are affected by an issue of this severity, fail the build.
 * componentLevel(component, level) - Set an override threshold for a specific component (in `group:module` format).  This can be used to treat issues affecting certain dependencies either more or less strictly than the default threshold would.

## vantagePublish

This task publishes the version generated by vantageGenerate to Vantage.  Unlike vantageCheck, the version will be persisted.

* publishEnabled (default: true) - whether to attempt to publish to vantage.  This is programatically configurable so that it can be statically set as a dependency of the `build` task and dynamically enabled only under certain scenarios (e.g. from the master branch on a CI server).

# Sample build.gradle
This sample build.gradle contains an example configuration of the vantage-gradle plugin.  It assumes you have already applied the vantage-gradle plugin via your favorite means of applying plugins and have all your normal configuration options like dependencies already configured.  This sample does not integrate `vantagePublish` into the build steps.  You would need to manually invoke that task as part of your build/deploy process.  

```
//This is the one option you will always need to configure.  All other configuration below is shown for example purposes
vantage.server = '<your vantage server>:<your vantage servers port>'

//The vantage extension will pick this up automatically.  To override it, set vantage.groupName
group = 'com.yodle'

//For whatever reason, we want to report this project to vantage under this name instead of the gradle project's name
vantage.moduleName = 'some-module'

//We want to check our dependencies any time we're running tests
test.dependsOn vantageCheck

//For this particular sample project, we care so much about this dependency that we want the build to fail if it is affected by even a minor issue
vantageCheck.componentLevel 'org.sample:sample-library', 'MINOR'
```

# Compatibility

The plugin requires Java 8.  Gradle versions prior to 2.8 are unsupported and untested (due to limitations in Gradle's testing functionality prior to 2.8), however in practice the plugin may work with any 2.x version.

# Building
To build this plugin, clone the project and run the following in the main project dir:

    ./gradlew build
