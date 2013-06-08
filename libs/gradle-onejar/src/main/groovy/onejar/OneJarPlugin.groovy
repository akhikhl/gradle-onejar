package onejar

import org.gradle.api.*
import org.gradle.api.plugins.*
import org.gradle.api.tasks.*

class OneJarPlugin implements Plugin<Project> {

  void apply(final Project project) {

    // the project supposed to be "java" or "groovy" already

    def onejarExt = project.ext.has("onejar") ? project.ext.onejar : []

    if(onejarExt.mainClass)
      project.jar {
        manifest { attributes "Main-Class": onejarExt.mainClass }
      }
    else
      project.logger.warn("Main class is not specified")

    def flavors = onejarExt.flavors ? onejarExt.flavors : ["default"]

    project.configurations { onejar }

    project.dependencies { onejar "com.simontuffs:one-jar-ant-task:0.97" }

    flavors.each { flavor ->
      def buildTaskName = "oneJarBuild"
      if(flavor != "default")
        buildTaskName += "_" + flavor
      project.task(buildTaskName) {
        inputs.dir "${project.buildDir}/libs"
        def outputDir = "${project.buildDir}/output"
        if(flavor != "default")
          outputDir += "-" + flavor
        outputs.dir outputDir
        doLast {
          ant.taskdef(name: 'onejar', classname: "com.simontuffs.onejar.ant.OneJarTask", classpath: project.configurations.onejar.asPath)
          def baseName = "${project.name}-${project.version}"
          if(flavor != "default")
            baseName += "-" + flavor
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
              project.configurations.runtime.each { File file ->
                boolean foundInFlavors = flavors.find { flavor2 ->
                  return project.configurations.findByName(flavor2) && project.configurations[flavor2].find { it == file }
                }
                if(!foundInFlavors)
                  fileset(file: file)
              }
              if(project.configurations.findByName(flavor))
                project.configurations[flavor].each { File file ->
                  fileset(file: file)
                }
            }
          }
          File launchScriptFile = new File("${outputDir}/${baseName}.sh")
          launchScriptFile.text = "#!/bin/bash\njava -jar ${baseName}.jar \"\$@\""
          launchScriptFile.setExecutable(true)
          launchScriptFile = new File("${outputDir}/${baseName}.bat")
          launchScriptFile.text = "@java -jar ${baseName}.jar %*"
          project.logger.info "Created one-jar: " + destFile
        }
      }
      project.tasks[buildTaskName].dependsOn "assemble", "check"

      project.tasks.build.dependsOn buildTaskName
    }
  }
}
