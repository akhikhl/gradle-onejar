package org.akhikhl.gradle.onejar

import org.gradle.api.Project
import org.gradle.api.tasks.bundling.*

class ProductConfigurator {

  private final Project project
  private final Map product
  private final String platform
  private final String arch
  private final String language
  private final String productTaskSuffix
  private final String outputBaseDir
  private final String productSuffix
  private final String outputDir
  private final String baseName
  private final String destFile
  private final File mainJar
  private final List launchers
  private final String versionFileName
  private final List explodedResources = []

  ProductConfigurator(Project project, Map product) {
    this.project = project
    this.product = product
    platform = product.platform ?: 'any'
    arch = product.arch ?: 'any'
    language = product.language ?: 'en'
    productTaskSuffix = (product.name == 'default' ? '' : '_' + (product.suffix ?: product.name))
    outputBaseDir = "${project.buildDir}/output"
    productSuffix = (product.name == 'default' ? '' : '-' + (product.suffix ?: product.name))
    outputDir = "${outputBaseDir}/${project.name}-${project.version}${productSuffix}"
    baseName = "${project.name}"
    destFile = "${outputDir}/${baseName}.jar"
    mainJar = ProjectUtils.getMainJar(project)
    if(product.launchers)
      launchers = product.launchers
    else if(product.launcher)
      launchers = [product.launcher]
    else if(product.platform == 'windows')
      launchers = ['windows']
    else
      launchers = ['shell']
    versionFileName = "${outputDir}/VERSION"
    if(platform == 'windows' || launchers.contains('windows'))
      versionFileName += '.txt'
    if(product.explodedResource)
      explodedResources.add product.explodedResource
    if(product.explodedResources)
      explodedResources.addAll product.explodedResources
  }

  private void configureCopyExplodedResourcesTask() {
    if(!explodedResources)
      return
    project.task("copyExplodedResources${productTaskSuffix}") { task ->

      for(def explodedResource in explodedResources) {
        def f = project.file(explodedResource)
        if(f.exists() && f.isDirectory()) {
          inputs.dir f
          outputs.dir "$outputDir/$explodedResource"
        }
        else if(f.exists() && f.isFile()) {
          inputs.file f
          outputs.file "$outputDir/$explodedResource"
        }
      }

      doLast {
        for(def explodedResource in explodedResources) {
          logger.warn 'Copying exploded resource from {} into {}', explodedResource, "$outputDir/$explodedResource"
          project.copy {
            from explodedResource
            into "$outputDir/$explodedResource"
          }
        }
      }
    }
  }

  private void configureProductArchiveTask() {
    if(!project.onejar.archiveProducts)
      return

    def archiveType = launchers.contains('windows') ? Zip : Tar

    project.task("productArchive${productTaskSuffix}", type: archiveType) { task ->
      from new File(outputDir)
      into "${project.name}"
      destinationDir = new File(outputBaseDir)
      classifier = productSuffix
      if(archiveType == Tar) {
        extension = '.tar.gz'
        compression = Compression.GZIP
      }
      task.doLast {
        ant.checksum file: it.archivePath
      }
      task.dependsOn "productBuild${productTaskSuffix}"
      project.tasks.build.dependsOn task
    }
  }

  private void configureProductBuildTask() {

    project.task("productBuild${productTaskSuffix}") { task ->

      inputs.dir "${project.buildDir}/libs"
      inputs.files project.configurations.runtime.files

      def productConfig = project.configurations.findByName("product_${product.name}")
      if(productConfig)
        inputs.files productConfig.files

      outputs.file destFile

      if(launchers.contains('shell'))
        outputs.file(new File("${outputDir}/${baseName}.sh"))

      if(launchers.contains('windows'))
        outputs.file(new File("${outputDir}/${baseName}.bat"))

      outputs.file versionFileName

      doLast {
        Set addedFiles = new HashSet()

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
              if(!excludeFile(file) && !addedFiles.contains(file)) {
                fileset(file: file)
                addedFiles.add(file)
              }
            }
            productConfig?.each { file ->
              if(!excludeProductFile(file) && !addedFiles.contains(file)) {
                fileset(file: file)
                addedFiles.add(file)
              }
            }
            project.onejar.additionalProductFiles.each { obj ->
              if(obj instanceof Closure)
                obj = obj(product)
              obj.each { file ->
                if(!excludeFile(file) && !addedFiles.contains(file)) {
                  fileset(file: file)
                  addedFiles.add(file)
                }
              }
            }
          }
        }

        generateLauncherFiles()
        generateVersionFile()

        project.onejar.onProductGeneration.each { obj ->
          if(obj instanceof Closure)
            obj(product, outputDir)
        }

        project.logger.info 'Created one-jar: {}', destFile
      } // doLast

      task.dependsOn project.tasks.assemble, project.tasks.check

      if(explodedResources)
        task.dependsOn "copyExplodedResources${productTaskSuffix}"

      project.tasks.build.dependsOn task
    }
  }

  void configureProduct() {
    configureCopyExplodedResourcesTask()
    configureProductBuildTask()
    configureProductArchiveTask()
  }

  private boolean excludeFile(File file) {
    if(file.absolutePath == mainJar.absolutePath)
      return true
    if(findFileInProducts(file))
      return true
    project.onejar.excludeProductFile.find {
      (it instanceof Closure ? it(file) : it) == file
    }
  }

  private boolean excludeProductFile(File file) {
    if(file.absolutePath == mainJar.absolutePath)
      return true
    project.onejar.excludeProductFile.find {
      (it instanceof Closure ? it(file) : it) == file
    }
  }

  private boolean findFileInProducts(File file) {
    project.configurations.find { config ->
      config.name.startsWith('product_') && config.find { it == file }
    }
  }

  private void generateLauncherFiles() {

    def launchParameters = project.onejar.launchParameters.join(' ')

    if(launchers.contains('shell')) {
      def launchScriptFile = new File("${outputDir}/${baseName}.sh")
      launchScriptFile.text = '''#!/bin/bash
SOURCE="${BASH_SOURCE[0]}"
while [ -h "$SOURCE" ]; do # resolve $SOURCE until the file is no longer a symlink
DIR="$( cd -P "$( dirname "$SOURCE" )" && pwd )"
SOURCE="$(readlink "$SOURCE")"
[[ $SOURCE != /* ]] && SOURCE="$DIR/$SOURCE" # if $SOURCE was a relative symlink, we need to resolve it relative to the path where the symlink file was located
done
DIR="$( cd -P "$( dirname "$SOURCE" )" && pwd )"
java -Dfile.encoding=UTF8 -Xms512m -Xmx1024m -jar ${DIR}/''' + baseName + '.jar ' + launchParameters + ' "$@"'
      launchScriptFile.setExecutable(true)
    }

    if(launchers.contains('windows')) {
      def launchScriptFile = new File("${outputDir}/${baseName}.bat")
      launchScriptFile.text = "@java -Dfile.encoding=UTF8 -Xms512m -Xmx1024m -jar %~dp0\\${baseName}.jar $launchParameters %*"
    }
  }

  private void generateVersionFile() {
    new File(versionFileName).text = """\
product: ${project.name}
version: ${project.version}
platform: $platform
architecture: $arch
language: $language
"""
  }
}

