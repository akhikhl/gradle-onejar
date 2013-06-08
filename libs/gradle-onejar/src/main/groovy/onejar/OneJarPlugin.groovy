package onejar

import org.gradle.api.*
import org.gradle.api.plugins.*
import org.gradle.api.tasks.*

class OneJarPlugin implements Plugin<Project> {

  void apply(final Project project) {

    // the project supposed to be "java" or "groovy" already

    def onejarExt = project.ext.properties.onejar ?: []

    if(onejarExt.mainClass)
      project.jar {
        manifest { attributes "Main-Class": onejarExt.mainClass }
      }
    else
      project.logger.warn("Main class is not specified")

    def flavors = onejarExt.flavors ?: ["default"]

    def findFileInFlavors = { file ->
      flavors.find { flavor ->
        flavor = flavor instanceof String ? [ name: flavor ] : flavor
        project.configurations.findByName(flavor.name)?.find { it == file }
      } }

    project.configurations { onejar }

    project.dependencies { onejar "com.simontuffs:one-jar-ant-task:0.97" }

    flavors.each { flavor ->
      flavor = flavor instanceof String ? [ name: flavor ] : flavor

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
            main(jar: project.tasks.jar.archivePath.toString())
            manifest {
              if(onejarExt.manifest)
                onejarExt.manifest.each { key, value ->
                  attribute(name: key, value: value)
                }
              if(!onejarExt.manifest.containsKey("Built-By"))
                attribute(name: "Built-By", value: System.getProperty("user.name"))
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
      }
    }
  }
}
