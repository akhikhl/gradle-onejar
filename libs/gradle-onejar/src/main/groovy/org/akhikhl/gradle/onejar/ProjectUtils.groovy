/*
 * gradle-onejar
 *
 * Copyright (c) 2014  Andrey Hihlovskiy.
 *
 * See the file "license.txt" for copying and usage permission.
 */
package org.akhikhl.gradle.onejar

import org.gradle.api.Project

class ProjectUtils {

  public static String getMainClass(Project project) {
    if(project.onejar.mainClass)
      return project.onejar.mainClass
    if(project.ext.has('mainClass'))
      return project.ext.mainClass
    return project.tasks.jar.manifest.attributes.'Main-Class'
  }

  public static File getMainJar(Project project) {
    def mainJar = project.onejar.mainJar
    if(mainJar == null)
      mainJar = project.tasks.jar.archivePath
    else if(mainJar instanceof Closure)
      mainJar = mainJar()
    if(!(mainJar instanceof File))
      mainJar = new File(mainJar)
    return mainJar
  }
}

