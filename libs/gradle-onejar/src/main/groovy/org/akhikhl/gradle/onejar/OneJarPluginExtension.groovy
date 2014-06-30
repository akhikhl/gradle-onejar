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
  List afterEvaluate = []
  List beforeProductGeneration = []
  List excludeProductFile = []
  List launchParameters = []
  List onProductGeneration = []
  String jvmMinMemory
  String jvmMaxMemory
  Map productInfo = [:]

  void additionalProductFiles(newValue) {
    if(newValue instanceof Collection)
      additionalProductFiles.addAll newValue
    else
      additionalProductFiles.add newValue
  }

  void afterEvaluate(newValue) {
    afterEvaluate.add newValue
  }

  void beforeProductGeneration(newValue) {
    beforeProductGeneration.add newValue
  }

  void excludeProductFile(Closure newValue) {
    excludeProductFile.add newValue
  }

  void launchParameter(String newValue) {
    launchParameters.add newValue
  }

  void mainJar(newValue) {
    mainJar = newValue
  }

  void manifest(Closure closure) {
    closure.delegate = manifest
    closure()
  }

  void onProductGeneration(newValue) {
    onProductGeneration.add newValue
  }

  void product(String productName) {
    product( [ name: productName ] )
  }

  void product(Map productSpec) {
    if(defaultProducts) {
      products = []
      defaultProducts = false
    }
    products.add productSpec
  }
  
  void productInfo(Map m) {
    productInfo << m
  }
  
  void productInfo(String key, String value) {
    productInfo[key] = value
  }
}
