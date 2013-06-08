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

    project.configurations { onejar }

    project.dependencies { onejar "com.simontuffs:one-jar-ant-task:0.97" }

    project.task("onejarCopyDependencies", type: Copy) {
      from project.configurations.runtime
      into "${project.buildDir}/onejarDependencies"
    }
    project.tasks.onejarCopyDependencies.dependsOn "assemble", "check"

    project.task("onejarBuild") {
      inputs.dir "${project.buildDir}/libs"
      inputs.dir "${project.buildDir}/onejarDependencies"
      def outputDir = "${project.buildDir}/output"
      outputs.dir outputDir
      doLast {
        ant.taskdef(name: 'onejar', classname: "com.simontuffs.onejar.ant.OneJarTask", classpath: project.configurations.onejar.asPath)
        def destFile = "${outputDir}/${project.name}-${project.version}.jar"
        new File("${project.buildDir}/onejarDependencies").mkdirs() // this folder might not exist
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
            fileset(dir: "${project.buildDir}/onejarDependencies")
          }
        }
        File launchScriptFile = new File("${outputDir}/${project.name}-${project.version}.sh")
        launchScriptFile.text = "#!/bin/bash\njava -jar ${project.name}-${project.version}.jar \"\$@\""
        launchScriptFile.setExecutable(true)
        launchScriptFile = new File("${outputDir}/${project.name}-${project.version}.bat")
        launchScriptFile.text = "@java -jar ${project.name}-${project.version}.jar %*"
        project.logger.info "Created one-jar: " + destFile
      }
    }
    project.tasks.onejarBuild.dependsOn "onejarCopyDependencies"

    project.tasks.build.dependsOn "onejarBuild"
  }
}
