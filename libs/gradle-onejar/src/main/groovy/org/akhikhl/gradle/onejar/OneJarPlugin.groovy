/*
 * gradle-onejar
 *
 * Copyright (c) 2014  Andrey Hihlovskiy.
 *
 * See the file "license.txt" for copying and usage permission.
 */
package org.akhikhl.gradle.onejar

import org.gradle.api.*
import org.gradle.api.plugins.*
import org.gradle.api.tasks.*
import org.gradle.api.tasks.bundling.*


/**
 * Gradle plugin for onejar generation
 */
class OneJarPlugin implements Plugin<Project> {

  private static final onejarAntTaskFileName = 'one-jar-ant-task-0.97.jar'

  void apply(Project project) {

    project.apply plugin: 'java'

    project.extensions.create('onejar', OneJarPluginExtension)

    project.task 'run', type: JavaExec
    project.task 'debug', type: JavaExec

    if(!project.configurations.findByName('provided'))
      project.configurations {
        provided
        compile.extendsFrom provided
      }

    project.afterEvaluate {

      configureRunTask(project)
      configureDebugTask(project)
      configureJarTask(project)
      configureOnejarAntTask(project)

      project.onejar.beforeProductGeneration.each { obj ->
        if(obj instanceof Closure)
          obj()
      }

      for(def product in project.onejar.products)
        new ProductConfigurator(project, product).configureProduct()

    } // project.afterEvaluate
  } // apply

  private void configureDebugTask(Project project) {
    project.tasks.debug {
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

  private void configureJarTask(Project project) {
    String mainClass = ProjectUtils.getMainClass(project)
    if(project.tasks.jar.manifest.attributes.'Main-Class' != mainClass)
      project.jar {
        manifest { attributes 'Main-Class': mainClass }
      }
  }

  private void configureOnejarAntTask(Project project) {
    File onejarAntTaskFile = new File(project.buildDir, onejarAntTaskFileName)
    if(!onejarAntTaskFile.exists()) {
      onejarAntTaskFile.parentFile.mkdirs()
      onejarAntTaskFile.withOutputStream { os ->
        os << OneJarPlugin.class.getResourceAsStream("/$onejarAntTaskFileName")
      }
    }
    project.ant.taskdef(name: 'onejar', classname: 'com.simontuffs.onejar.ant.OneJarTask', classpath: onejarAntTaskFile.absolutePath)
  }

  private void configureRunTask(Project project) {
    project.tasks.run {
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
