package onejar

import org.gradle.api.*
import org.gradle.api.plugins.*
import org.gradle.api.tasks.*

class OneJarPlugin implements Plugin<Project> {

  static def isCollection(obj) {
    return [Collection, Object[]].any { it.isAssignableFrom(obj.getClass()) }
  }

  void apply(final Project project) {

    // the project supposed to be "java" or "groovy" already

    project.extensions.create("onejar", OneJarPluginExtension)

    project.configurations { onejar }

    project.dependencies { onejar "com.simontuffs:one-jar-ant-task:0.97" }

    project.afterEvaluate {

      def findFileInFlavors = { file ->
        project.onejar.flavors.find { flavor ->
          project.configurations.findByName(flavor.name)?.find { it == file }
        } }

      project.onejar.flavors.each { flavor ->

        def buildTaskName = "oneJarBuild"
        if(flavor.name != "default")
          buildTaskName += "_" + flavor.name

        project.task(buildTaskName) { task ->

          def outputDir = "${project.buildDir}/output"
          if(flavor.name != "default")
            outputDir += "-" + flavor.name

          inputs.dir "${project.buildDir}/libs"
          outputs.dir outputDir

          doLast {
            ant.taskdef(name: 'onejar', classname: "com.simontuffs.onejar.ant.OneJarTask", classpath: project.configurations.onejar.asPath)

            def baseName = "${project.name}-${project.version}"
            if(flavor != "default")
              baseName += "-" + flavor.name
            def destFile = "${outputDir}/${baseName}.jar"

            ant.onejar(destFile: destFile) {
              main jar: project.tasks.jar.archivePath.toString()
              manifest {
                project.onejar.manifest.attributes.each { key, value ->
                  attribute name: key, value: value
                }
                if(!project.onejar.manifest.attributes.containsKey("Built-By"))
                  attribute name: "Built-By", value: System.getProperty("user.name")
              }
              lib {
                project.configurations.runtime.each { file ->
                  if(!findFileInFlavors(file))
                    fileset(file: file)
                }
                project.configurations.findByName(flavor.name)?.each { file ->
                  fileset(file: file)
                }
              }
            }

            def launchers
            if(flavor.launchers)
              launchers = flavor.launchers
            else if(flavor.launcher)
              launchers = [flavor.launcher]
            else
              launchers = ["shell"]

            if(launchers.contains("shell")) {
              def launchScriptFile = new File("${outputDir}/${baseName}.sh")
              launchScriptFile.text = "#!/bin/bash\njava -jar ${baseName}.jar \"\$@\""
              launchScriptFile.setExecutable(true)
            }

            if(launchers.contains("windows")) {
              def launchScriptFile = new File("${outputDir}/${baseName}.bat")
              launchScriptFile.text = "@java -jar ${baseName}.jar %*"
            }

            project.logger.info "Created one-jar: " + destFile
          } // doLast

          dependsOn "assemble", "check"

          project.tasks.build.dependsOn task
        } // task
      } // project.onejar.flavors.each
    } // project.afterEvaluate
  } // apply
}
