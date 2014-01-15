gradle-onejar
=============

Gradle plugin for generating single jar from java/groovy application.

**Content of this document**

* [Usage](#usage)
* [Introduced tasks](#supported-tasks)

#Usage:

Add the following to "build.gradle" of your web-application:

```groovy
apply from: 'https://raw.github.com/akhikhl/gradle-onejar/master/pluginScripts/gradle-onejar.plugin'
```

then do "gradle build" from the command-line.

Alternatively, you can download the script from https://raw.github.com/akhikhl/gradle-onejar/master/pluginScripts/gradle-onejar.plugin
to the project folder and include it like this:

```groovy
apply from: 'gradle-onejar.plugin'
```

or feel free copying (and modifying) the declarations from this script to your "build.gradle".

#Tasks

gradle-onejar inserts the following tasks into java/groovy application lifecycle:

![task diagram](https://raw.github.com/akhikhl/gradle-onejar/master/doc/task_diagram.png "Gradle-onejar tasks")

###prepareOneJar

**Syntax:**

```shell
gradle prepareOneJar
```

**Effect:**


