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

class ProductConfigurer {

  private static final Logger log = LoggerFactory.getLogger(ProductConfigurer)

  private final Map options
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
  private final List launchers
  private final String versionFileName
  private final List explodedResources = []

  ProductConfigurer(Map options, Project project, Map product) {
    this.options = options
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
    if(productTaskSuffix)
      productTaskSuffix = '_' + productTaskSuffix

    String productConfigName = [ 'product', product.configBaseName ?: product.name ?: '', product.suffix, platform, arch, language ].findResults { it ?: null }.join('_')
    log.debug 'product config: {}', productConfigName
    productConfig = project.configurations.findByName(productConfigName)

    providedConfig = project.configurations.findByName('provided')

    if(product.launchers)
      launchers = product.launchers
    else if(product.launcher)
      launchers = [product.launcher]
    else if(product.platform == 'windows')
      launchers = ['windows']
    else
      launchers = ['shell']
    versionFileName = "${outputDir}/VERSION.txt"
    if(product.explodedResource)
      explodedResources.add product.explodedResource
    if(product.explodedResources)
      explodedResources.addAll product.explodedResources
  }

  private void configureCopyExplodedResourcesTask() {
    if(!explodedResources)
      return
    project.task("copyExplodedResources${productTaskSuffix}", group: 'onejar') { task ->

      description = 'Copies exploded resources to product directory'

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
          logger.debug 'Copying exploded resource from {} into {}', explodedResource, "$outputDir/$explodedResource"
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

    project.task("archiveProduct${productTaskSuffix}", type: archiveType, group: 'onejar') {

      description = "Compresses the product to ${archiveType == Tar ? 'tar.gz' : 'zip'} file"

      archiveName = productQualifiedFileName + (archiveType == Tar ? '.tar.gz' : '.zip')
      destinationDir = new File(outputBaseDir)
      if(archiveType == Tar)
        compression = Compression.GZIP

      from new File(outputDir), { into productBaseFileName }

      doLast {
        project.logger.debug 'Created archive: {}', archivePath
        ant.checksum file: archivePath
      }
      dependsOn "buildProduct${productTaskSuffix}"
      project.tasks.build.dependsOn it
    }
  }

  private void configureProductBuildTask() {

    project.task("buildProduct${productTaskSuffix}", group: 'onejar') { task ->

      description = 'Builds the product'

      inputs.dir "${project.buildDir}/libs"
      inputs.files { getRuntimeConfiguration().files }

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
        def mainJar = ProjectUtils.getMainJar(project)

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
            if(!options.suppressRuntimeConfiguration)
              getRuntimeConfiguration().each { file ->
                if(!excludeFile(file, mainJar) && !addedFiles.contains(file)) {
                  fileset(file: file)
                  addedFiles.add(file)
                }
              }
            productConfig?.each { file ->
              if(!excludeProductFile(file, mainJar) && !addedFiles.contains(file)) {
                fileset(file: file)
                addedFiles.add(file)
              }
            }
            project.onejar.additionalProductFiles.each { obj ->
              if(obj instanceof Closure)
                obj = obj(product)
              obj.each { file ->
                if(!excludeFile(file, mainJar) && !addedFiles.contains(file)) {
                  fileset(file: file)
                  addedFiles.add(file)
                }
              }
            }
          }
        }

        generateLauncherFiles()
        generateVersionFile(product)

        project.onejar.onProductGeneration.each { obj ->
          if(obj instanceof Closure)
            obj(product, outputDir)
        }

        project.logger.debug 'Created one-jar: {}', destFile
      } // doLast

      task.dependsOn project.tasks.assemble, project.tasks.check

      if(explodedResources)
        task.dependsOn "copyExplodedResources${productTaskSuffix}"

      if(!options.suppressBuildTaskDependency)
        project.tasks.build.dependsOn task
    }
  }

  void configureProduct() {
    configureCopyExplodedResourcesTask()
    configureProductBuildTask()
    configureProductArchiveTask()
  }

  private boolean excludeFile(File file, mainJar) {
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

  private boolean excludeProductFile(File file, mainJar) {
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

  private void generateVersionFile(Map product) {
    Properties props = new Properties()
    props.setProperty('product', productBaseFileName)
    props.setProperty('version', project.version)
    if(platform)
      props.setProperty('platform', platform)
    if(arch)
      props.setProperty('architecture', arch)
    if(language)
      props.setProperty('language', language)
      
    project.onejar.productInfo.each { key, value ->
      if(value instanceof Closure)
        value = value()
      props.setProperty(key, value)
    }
    
    product.productInfo?.each { key, value ->
      if(value instanceof Closure)
        value = value()
      props.setProperty(key, value)
    }
    
    new File(versionFileName).withOutputStream {
      props.store(it, null)
    }
  }

  protected getRuntimeConfiguration() {
    project.configurations.getByName(options.runtimeConfiguration ?: 'runtime')
  }
}
