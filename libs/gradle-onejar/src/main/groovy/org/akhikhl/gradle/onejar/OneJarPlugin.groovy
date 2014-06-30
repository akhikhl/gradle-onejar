/*
 * gradle-onejar
 *
 * Copyright (c) 2014  Andrey Hihlovskiy.
 *
 * See the file "license.txt" for copying and usage permission.
 */
package org.akhikhl.gradle.onejar

import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Gradle plugin for onejar generation
 */
class OneJarPlugin implements Plugin<Project> {

  @Override
  void apply(Project project) {
    new OneJarConfigurer([:], project).apply()
  }

  void apply(Map options, Project project) {
    new OneJarConfigurer(options, project).apply()
  }
}
