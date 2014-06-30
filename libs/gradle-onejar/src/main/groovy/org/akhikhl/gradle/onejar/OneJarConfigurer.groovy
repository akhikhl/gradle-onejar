/*
 * gradle-onejar
 *
 * Copyright (c) 2014  Andrey Hihlovskiy.
 *
 * See the file "license.txt" for copying and usage permission.
 */
package org.akhikhl.gradle.onejar

import org.gradle.api.Project
import org.gradle.api.tasks.JavaExec

/**
 *
 * @author akhikhl
 */
class OneJarConfigurer {

  private static final onejarAntTaskFileName = 'one-jar-ant-task-0.97.jar'

  protected final Map options
  protected final Project project
  protected final String runTaskName
  protected final String debugTaskName

  OneJarConfigurer(Map options, Project project) {
    this.options = options
    this.project = project
    runTaskName = options.runTaskName ?: 'run'
    debugTaskName = options.debugTaskName ?: 'debug'
  }

  void apply() {

    project.apply plugin: 'java'

    project.extensions.create('onejar', OneJarPluginExtension)

    project.task(runTaskName, type: JavaExec, group: 'onejar', description: 'Runs the application with product configuration')
    project.task(debugTaskName, type: JavaExec, group: 'onejar', description: 'Runs the application with product configuration in debug mode')

    if(!project.configurations.findByName('provided'))
      project.configurations {
        provided
        compile.extendsFrom provided
      }

    project.afterEvaluate {

      configureRunTask()
      configureDebugTask()
      configureJarTask()
      configureOnejarAntTask()

      project.onejar.beforeProductGeneration.each { obj ->
        if(obj instanceof Closure)
          obj()
      }

      for(def product in project.onejar.products)
        new ProductConfigurer(options, project, product).configureProduct()

    } // project.afterEvaluate
  }

  private void configureDebugTask() {
    project.tasks[debugTaskName].configure {
      dependsOn project.tasks.classes
      main = ProjectUtils.getMainClass(project)
      classpath = project.sourceSets.main.runtimeClasspath
      if(project.ext.has('programArgs'))
        args project.ext.programArgs
      if(project.ext.has('args'))
        args project.ext.args
      if(project.ext.has('jvmArgs'))
        jvmArgs project.ext.jvmArgs
      debug = true
    }
  }

  private void configureJarTask() {
    String mainClass = ProjectUtils.getMainClass(project)
    if(mainClass && project.tasks.jar.manifest.attributes.'Main-Class' != mainClass)
      project.jar {
        manifest { attributes 'Main-Class': mainClass }
      }
  }

  private void configureOnejarAntTask() {
    File onejarAntTaskFile = new File(project.buildDir, onejarAntTaskFileName)
    if(!onejarAntTaskFile.exists()) {
      onejarAntTaskFile.parentFile.mkdirs()
      onejarAntTaskFile.withOutputStream { os ->
        os << OneJarPlugin.class.getResourceAsStream("/$onejarAntTaskFileName")
      }
    }
    project.ant.taskdef(name: 'onejar', classname: 'com.simontuffs.onejar.ant.OneJarTask', classpath: onejarAntTaskFile.absolutePath)
  }

  private void configureRunTask() {
    project.tasks[runTaskName].configure {
      dependsOn project.tasks.classes
      main = ProjectUtils.getMainClass(project)
      classpath = project.sourceSets.main.runtimeClasspath
      if(project.ext.has('programArgs'))
        args project.ext.programArgs
      if(project.ext.has('args'))
        args project.ext.args
      if(project.ext.has('jvmArgs'))
        jvmArgs project.ext.jvmArgs
    }
  }
}
