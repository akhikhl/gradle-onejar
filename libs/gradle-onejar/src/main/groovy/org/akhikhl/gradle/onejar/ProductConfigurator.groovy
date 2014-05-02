/*
 * gradle-onejar
 *
 * Copyright (c) 2014  Andrey Hihlovskiy.
 *
 * See the file "license.txt" for copying and usage permission.
 */
package org.akhikhl.gradle.onejar

import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.tasks.bundling.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class ProductConfigurator {

  private static final Logger log = LoggerFactory.getLogger(ProductConfigurator)

  private final Project project
  private final Map product
  private final String platform
  private final String arch
  private final String language
  private final String productBaseFileName
  private final String productQualifiedFileName
  private final String outputBaseDir
  private final String outputDir
  private final String destFile
  private final String productTaskSuffix
  private final Configuration productConfig
  private final Configuration providedConfig
  private final File mainJar
  private final List launchers
  private final String versionFileName
  private final List explodedResources = []

  ProductConfigurator(Project project, Map product) {
    this.project = project
    this.product = product
    platform = product.platform
    arch = product.arch
    language = product.language

    productBaseFileName = product.name ?: project.name

    String productFileSuffix
    if(product.fileSuffix)
      productFileSuffix = product.fileSuffix
    else
      productFileSuffix = [ product.suffix, platform, arch, language ].findResults { it ?: null }.join('-')

    productQualifiedFileName = [productBaseFileName, project.version, productFileSuffix].findResults { it ?: null }.join('-')

    outputBaseDir = "${project.buildDir}/output"
    outputDir = "${outputBaseDir}/${productQualifiedFileName}"
    log.debug 'outputDir={}', outputDir

    destFile = "${outputDir}/${productBaseFileName}.jar"

    productTaskSuffix = [ product.name, product.suffix, platform, arch, language ].findResults { it ?: null }.join('_')

    String productConfigName = [ 'product', product.configBaseName ?: product.name ?: '', product.suffix, platform, arch, language ].findResults { it ?: null }.join('_')
    log.debug 'product config: {}', productConfigName
    productConfig = project.configurations.findByName(productConfigName)

    providedConfig = project.configurations.findByName('provided')

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
    project.task("copyExplodedResources_${productTaskSuffix}") { task ->

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

    project.task("archive_${productTaskSuffix}", type: archiveType) {

      archiveName = productQualifiedFileName + (archiveType == Tar ? '.tar.gz' : '.zip')
      destinationDir = new File(outputBaseDir)
      if(archiveType == Tar)
        compression = Compression.GZIP

      from new File(outputDir), { into productBaseFileName }

      doLast {
        project.logger.warn 'Created archive: {}', archivePath
        ant.checksum file: archivePath
      }
      dependsOn "build_${productTaskSuffix}"
      project.tasks.build.dependsOn it
    }
  }

  private void configureProductBuildTask() {

    project.task("build_${productTaskSuffix}") { task ->

      inputs.dir "${project.buildDir}/libs"
      inputs.files project.configurations.runtime.files

      if(productConfig)
        inputs.files productConfig.files

      outputs.file destFile

      if(launchers.contains('shell'))
        outputs.file(new File("${outputDir}/${productBaseFileName}.sh"))

      if(launchers.contains('windows'))
        outputs.file(new File("${outputDir}/${productBaseFileName}.bat"))

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

        project.logger.warn 'Created one-jar: {}', destFile
      } // doLast

      task.dependsOn project.tasks.assemble, project.tasks.check

      if(explodedResources)
        task.dependsOn "copyExplodedResources_${productTaskSuffix}"

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
    if(productConfig?.find { it == file })
      return true
    if(providedConfig?.find { it == file })
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

  private void generateLauncherFiles() {

    def addCommonParams = { params ->

      if(project.onejar.jvmMinMemory)
        params.add('-Xms' + project.onejar.jvmMinMemory)

      if(project.onejar.jvmMaxMemory)
        params.add('-Xmx' + project.onejar.jvmMaxMemory)

      params.add('-Dfile.encoding=UTF8')

      if(language)
        params.add('-Duser.language=' + language)
    }

    if(launchers.contains('shell')) {

      def params = []
      addCommonParams(params)
      params.add('-jar ${DIR}/' + productBaseFileName + '.jar')
      params.addAll(project.onejar.launchParameters)
      params.add('"$@"')
      params = params.join(' ')

      def launchScriptFile = new File("${outputDir}/${productBaseFileName}.sh")
      launchScriptFile.text = '''#!/bin/bash
SOURCE="${BASH_SOURCE[0]}"
while [ -h "$SOURCE" ]; do # resolve $SOURCE until the file is no longer a symlink
DIR="$( cd -P "$( dirname "$SOURCE" )" && pwd )"
SOURCE="$(readlink "$SOURCE")"
[[ $SOURCE != /* ]] && SOURCE="$DIR/$SOURCE" # if $SOURCE was a relative symlink, we need to resolve it relative to the path where the symlink file was located
done
DIR="$( cd -P "$( dirname "$SOURCE" )" && pwd )"
java ''' + params
      launchScriptFile.setExecutable(true)
    }

    if(launchers.contains('windows')) {

      def params = []
      addCommonParams(params)
      params.add('-jar %~dp0\\' + productBaseFileName + '.jar')
      params.addAll(project.onejar.launchParameters)
      params.add('%*')
      params = params.join(' ')

      def launchScriptFile = new File("${outputDir}/${productBaseFileName}.bat")
      launchScriptFile.text = '@java ' + params
    }
  }

  private void generateVersionFile() {
    new File(versionFileName).text = """\
product: ${productBaseFileName}
version: ${project.version}
platform: ${platform ?: 'any'}
architecture: ${arch ?: 'any'}
language: ${language ?: 'any'}
"""
  }
}
