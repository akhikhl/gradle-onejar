package onejar

import org.gradle.api.*
import org.gradle.api.plugins.*
import org.gradle.api.tasks.*
import org.gradle.api.tasks.bundling.*


/**
 * Gradle plugin for onejar generation
 * @author akhikhl
 *
 */
class OneJarPlugin implements Plugin<Project> {

  void apply(final Project project) {

    // the project supposed to be "java" or "groovy" already

    project.extensions.create('onejar', OneJarPluginExtension)

    project.configurations { onejar }

    project.dependencies { onejar 'com.simontuffs:one-jar-ant-task:0.97' }

    project.task('prepareOneJar') { dependsOn project.tasks.assemble, project.tasks.check }

    project.afterEvaluate {

      String outputBaseDir = "${project.buildDir}/output"

      def mainJar = project.onejar.mainJar
      if(mainJar == null)
        mainJar = project.tasks.jar.archivePath
      else if(mainJar instanceof Closure)
        mainJar = mainJar()

      if(mainJar instanceof String)
        mainJar = new File(mainJar)

      project.onejar.beforeProductGeneration.each { obj ->
        if(obj instanceof Closure)
          obj()
      }

      def findFileInProducts = { file ->
        project.onejar.products.find { product ->
          project.configurations.findByName(product.name)?.find { it == file }
        } }

      def excludeProductFile = { file ->
        project.onejar.excludeProductFile.find {
          it instanceof Closure ? it(file) : it == file
        }
      }

      project.onejar.products.each { product ->

        def platform = product.platform ?: 'any'
        def arch = product.arch ?: 'any'
        def language = product.language ?: 'en'

        def suffix = ''
        if(product.name != 'default')
          suffix = product.suffix ?: product.name

        def launchers
        if(product.launchers)
          launchers = product.launchers
        else if(product.launcher)
          launchers = [product.launcher]
        else if(product.platform == 'windows')
          launchers = ['windows']
        else
          launchers = ['shell']

        def outputDir = "${outputBaseDir}/${project.name}-${project.version}"
        if(suffix)
          outputDir += '-' + suffix

        def buildTaskName = 'oneJarBuild'
        if(product.name != 'default')
          buildTaskName += '_' + product.name

        project.task(buildTaskName) { task ->

          inputs.dir "${project.buildDir}/libs"
          inputs.files project.configurations.runtime.files

          if(project.configurations.findByName(product.name))
            inputs.files project.configurations.findByName(product.name).files

          outputs.dir outputDir

          doLast {
            ant.taskdef(name: 'onejar', classname: 'com.simontuffs.onejar.ant.OneJarTask', classpath: project.configurations.onejar.asPath)

            def baseName = "${project.name}"
            def destFile = "${outputDir}/${baseName}.jar"

            ant.onejar(destFile: destFile) {
              main jar: mainJar
              manifest {
                project.onejar.manifest.attributes.each { key, value ->
                  attribute name: key, value: value
                }
                if(!project.onejar.manifest.attributes.containsKey('Built-By'))
                  attribute name: 'Built-By', value: System.getProperty('user.name')
              }
              lib {
                project.configurations.runtime.each { file ->
                  if(file.absolutePath != mainJar.absolutePath && !findFileInProducts(file) && !excludeProductFile(file))
                    fileset(file: file)
                }
                project.configurations.findByName(product.name)?.each { file ->
                  if(file.absolutePath != mainJar.absolutePath && !excludeProductFile(file))
                    fileset(file: file)
                }
                project.onejar.additionalProductFiles.each { obj ->
                  if(obj instanceof Closure)
                    obj = obj(product)
                  obj.each { file ->
                    if(file.absolutePath != mainJar.absolutePath && !findFileInProducts(file) && !project.configurations.runtime.find { it == file } && !excludeProductFile(file))
                      fileset(file: file)
                  }
                }
              }
            }

            def launchParameters = project.onejar.launchParameters.join(' ')

            if(launchers.contains('shell')) {
              def launchScriptFile = new File("${outputDir}/${baseName}.sh")
              launchScriptFile.text = "#!/bin/bash\njava -jar ${baseName}.jar $launchParameters \"\$@\""
              launchScriptFile.setExecutable(true)
            }

            if(launchers.contains('windows')) {
              def launchScriptFile = new File("${outputDir}/${baseName}.bat")
              launchScriptFile.text = "@java -jar ${baseName}.jar $launchParameters %*"
            }

            def versionFileName = "${outputDir}/VERSION"
            if(platform == 'windows' || launchers.contains('windows'))
              versionFileName += '.txt'
            new File(versionFileName).text = """\
product: ${project.name}
version: ${project.version}
platform: $platform
architecture: $arch
language: $language
"""
            project.onejar.onProductGeneration.each { obj ->
              if(obj instanceof Closure)
                obj(product, outputDir)
            }

            project.logger.info 'Created one-jar: {}', destFile
          } // doLast

          task.dependsOn project.tasks.prepareOneJar
          project.tasks.build.dependsOn task
        } // build task

        if(project.onejar.archiveProducts) {
          def archiveTaskName = 'oneJarArchiveProduct'
          if(product.name != 'default')
            archiveTaskName += '_' + product.name

          def archiveType = launchers.contains('windows') ? Zip : Tar

          project.task(archiveTaskName, type: archiveType) { task ->
            from new File(outputDir)
            into "${project.name}"
            destinationDir = new File(outputBaseDir)
            classifier = suffix
            if(archiveType == Tar) {
              extension = '.tar.gz'
              compression = Compression.GZIP
            }
            task.doLast {
              ant.checksum file: it.archivePath
            }
            task.dependsOn buildTaskName
            project.tasks.build.dependsOn task
          }
        }

      } // project.onejar.products.each
    } // project.afterEvaluate
  } // apply
}
