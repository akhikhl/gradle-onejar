/*
 * gradle-onejar
 *
 * Copyright (c) 2014  Andrey Hihlovskiy.
 *
 * See the file "license.txt" for copying and usage permission.
 */
package org.akhikhl.gradle.onejar

class OneJarPluginExtension {

  class Manifest {
    def attributes = [:]
    def attributes(map) {
      attributes << map
    }
  }

  def mainJar
  String mainClass
  def manifest = new Manifest()
  private boolean defaultProducts = true
  List products = [[:]]
  boolean archiveProducts = false
  List additionalProductFiles = []
  List beforeProductGeneration = []
  List excludeProductFile = []
  List launchParameters = []
  List onProductGeneration = []
  String jvmMinMemory
  String jvmMaxMemory

  def additionalProductFiles(newValue) {
    if(newValue instanceof Collection)
      additionalProductFiles.addAll newValue
    else
      additionalProductFiles.add newValue
  }

  def beforeProductGeneration(newValue) {
    beforeProductGeneration.add newValue
  }

  def excludeProductFile(Closure newValue) {
    excludeProductFile.add newValue
  }

  def launchParameter(String newValue) {
    launchParameters.add newValue
  }

  def mainJar(newValue) {
    mainJar = newValue
  }

  def manifest(Closure closure) {
    closure.delegate = manifest
    closure()
  }

  def onProductGeneration(newValue) {
    onProductGeneration.add newValue
  }

  def product(String productName) {
    product( [ name: productName ] )
  }

  def product(Map productSpec) {
    if(defaultProducts) {
      products = []
      defaultProducts = false
    }
    products.add productSpec
  }
}
